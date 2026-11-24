package sn.portailedr.suivi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.domaine.Paiement;
import sn.portailedr.domaine.Resultat;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.StatutPaiement;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.domaine.TypeResultat;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.PaiementRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.TarifRepository;
import sn.portailedr.service.FichiersProprietes;
import sn.portailedr.service.GenerateurReference;

/**
 * Demandes creees directement en base, a chaque statut, pour les tests du suivi.
 * La publication du resultat (tranche 7) n'existe pas encore : le test ecrit lui-meme
 * la ligne RESULTAT et le fichier dans le dossier "resultats".
 */
public final class DemandesDeTest {

    /** Contenu du faux resultat : un PDF minimal. */
    public static final byte[] CONTENU_RESULTAT = "%PDF-1.7\nresultat de test\n".getBytes(StandardCharsets.US_ASCII);

    private final DemandeRepository demandes;
    private final BureauRepository bureaux;
    private final TarifRepository tarifs;
    private final ResultatRepository resultats;
    private final PaiementRepository paiements;
    private final GenerateurReference references;
    private final FichiersProprietes fichiers;

    public DemandesDeTest(DemandeRepository demandes, BureauRepository bureaux, TarifRepository tarifs,
            ResultatRepository resultats, PaiementRepository paiements, GenerateurReference references,
            FichiersProprietes fichiers) {
        this.demandes = demandes;
        this.bureaux = bureaux;
        this.tarifs = tarifs;
        this.resultats = resultats;
        this.paiements = paiements;
        this.references = references;
        this.fichiers = fichiers;
    }

    /** Demande d'un particulier sur un bien, au statut donne (sans resultat ni paiement). */
    public Demande particulier(StatutDemande statut) {
        Demande demande = base(statut);
        demande.setDeclarantNom("Awa Ndiaye");
        demande.setDeclarantTelephone("+221 77 000 00 00");
        demande.setDeclarantCni("1 234 1990 01234");
        demande.setTelephoneVerifie(true);
        return demandes.save(demande);
    }

    /** Demande d'un notaire, au statut donne. */
    public Demande notaire(Utilisateur notaire, StatutDemande statut) {
        Demande demande = base(statut);
        demande.setUtilisateur(notaire);
        demande.setDeclarantNom(notaire.getNom());
        demande.setDeclarantTelephone(notaire.getTelephone());
        return demandes.save(demande);
    }

    private Demande base(StatutDemande statut) {
        Demande demande = new Demande();
        demande.setReference(references.nouvelle(demandes::existsByReference));
        demande.setType(TypeDemande.BIEN);
        demande.setNumeroTitreFoncier("4521/DK");
        demande.setStatut(statut);
        demande.setDateSoumission(Instant.now().minus(Duration.ofDays(8)));
        demande.setBureau(bureaux.findByNom("Bureau de Dakar-Plateau").orElseThrow());
        return demande;
    }

    /** Comme la publication de la tranche 7 : resultat, tarif, date limite, statut DISPONIBLE. */
    public Demande disponible(Demande demande, Instant dateLimitePaiement) {
        demande.setTarif(tarifs.findAll().stream().filter(tarif -> tarif.getTypeDemande() == demande.getType())
                .findFirst().orElseThrow());
        demande.setDateLimitePaiement(dateLimitePaiement);
        demande.setStatut(StatutDemande.DISPONIBLE);
        resultats.save(new Resultat(TypeResultat.ETAT_DROITS_REELS, ecrireFichierResultat(),
                Instant.now().minus(Duration.ofDays(1)), demande));
        return demandes.save(demande);
    }

    /** Disponible, delai de paiement dans 7 jours. */
    public Demande disponible(Demande demande) {
        return disponible(demande, Instant.now().plus(Duration.ofDays(7)));
    }

    /** Paiement confirme a la date donnee, statut PAYEE. */
    public Demande payee(Demande demande, Instant datePaiement) {
        disponible(demande);
        paiements.save(new Paiement(demande.getTarif().getMontant(), ModePaiement.WAVE, StatutPaiement.CONFIRME,
                datePaiement, demande));
        demande.setStatut(StatutDemande.PAYEE);
        return demandes.save(demande);
    }

    private String ecrireFichierResultat() {
        String nom = UUID.randomUUID() + ".pdf";
        try {
            Path dossier = fichiers.dossier().resolve("resultats");
            Files.createDirectories(dossier);
            Files.write(dossier.resolve(nom), CONTENU_RESULTAT);
            return nom;
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }
}
