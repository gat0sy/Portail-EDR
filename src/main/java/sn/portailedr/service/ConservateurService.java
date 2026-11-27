package sn.portailedr.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.Resultat;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Tarif;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.domaine.TypeResultat;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.PieceJointeRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.TarifRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.service.BureauVue.Detail;
import sn.portailedr.service.BureauVue.Filtre;
import sn.portailedr.service.BureauVue.Ligne;
import sn.portailedr.service.BureauVue.Piece;

/**
 * Espace conservateur : les demandes de SON bureau (regle 2), les pieces, et les decisions
 * (regles 5 et 6). Chaque decision verrouille la demande, verifie son statut, passe par
 * Demande.changerStatut et ecrit une ligne de journal, le tout en une transaction.
 * Une demande d'un autre bureau est traitee comme inexistante (DemandeIntrouvableException, 404).
 */
@Service
public class ConservateurService {

    /** "Cloturees" du prototype : les statuts finaux. */
    private static final Set<StatutDemande> CLOTUREES = Set.of(StatutDemande.REJETEE, StatutDemande.PAYEE,
            StatutDemande.EXPIREE);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_LONGUE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(DAKAR);
    private static final DateTimeFormatter DATE_COURTE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH).withZone(DAKAR);

    private final DemandeRepository demandes;
    private final PieceJointeRepository pieces;
    private final ResultatRepository resultats;
    private final TarifRepository tarifs;
    private final UtilisateurRepository utilisateurs;
    private final JournalService journal;
    private final NotificationService notifications;
    private final StockagePieces stockagePieces;
    private final StockageResultats stockageResultats;
    private final FichiersProprietes fichiers;
    private final PaiementProprietes paiement;
    private final Clock horloge;

    public ConservateurService(DemandeRepository demandes, PieceJointeRepository pieces, ResultatRepository resultats,
            TarifRepository tarifs, UtilisateurRepository utilisateurs, JournalService journal,
            NotificationService notifications, StockagePieces stockagePieces, StockageResultats stockageResultats,
            FichiersProprietes fichiers, PaiementProprietes paiement, Clock horloge) {
        this.demandes = demandes;
        this.pieces = pieces;
        this.resultats = resultats;
        this.tarifs = tarifs;
        this.utilisateurs = utilisateurs;
        this.journal = journal;
        this.notifications = notifications;
        this.stockagePieces = stockagePieces;
        this.stockageResultats = stockageResultats;
        this.fichiers = fichiers;
        this.paiement = paiement;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- lecture

    /**
     * @param filtre    SOUMISE, EN_COURS, DISPONIBLE ou CLOTUREE ; toute autre valeur : toutes les demandes
     * @param reference demande a detailler, ou null
     * @throws DemandeIntrouvableException si la reference n'est pas une demande du bureau
     */
    @Transactional(readOnly = true)
    public BureauVue bureau(String emailConservateur, String filtre, String reference) {
        Utilisateur conservateur = conservateur(emailConservateur);
        List<Demande> toutes = demandes.findByBureauIdOrderByDateSoumissionDescIdDesc(conservateur.getBureau().getId());
        // List.of(...).contains(null) leve une exception : pas de filtre, toutes les demandes
        String actif = filtre != null && List.of("SOUMISE", "EN_COURS", "DISPONIBLE", "CLOTUREE").contains(filtre)
                ? filtre : null;

        List<Filtre> filtres = List.of(
                new Filtre(null, "Toutes", toutes.size()),
                new Filtre("SOUMISE", "Soumises", compter(toutes, "SOUMISE")),
                new Filtre("EN_COURS", "En cours", compter(toutes, "EN_COURS")),
                new Filtre("DISPONIBLE", "Disponibles", compter(toutes, "DISPONIBLE")),
                new Filtre("CLOTUREE", "Clôturées", compter(toutes, "CLOTUREE")));
        List<Ligne> lignes = toutes.stream().filter(demande -> retenue(demande, actif))
                .map(demande -> new Ligne(demande.getReference(),
                        demande.getType() == TypeDemande.BIEN ? "Bien" : "Personne", demandeur(demande),
                        DATE_COURTE.format(demande.getDateSoumission()), demande.getStatut().getLibelle(),
                        demande.getStatut().getBadge()))
                .toList();
        Detail detail = reference == null ? null : detail(duBureau(reference, conservateur, false));
        return new BureauVue(conservateur.getBureau().getNom(), actif, filtres, lignes, detail);
    }

    private static boolean retenue(Demande demande, String filtre) {
        if (filtre == null) {
            return true;
        }
        return "CLOTUREE".equals(filtre) ? CLOTUREES.contains(demande.getStatut())
                : demande.getStatut().name().equals(filtre);
    }

    private static long compter(List<Demande> toutes, String filtre) {
        return toutes.stream().filter(demande -> retenue(demande, filtre)).count();
    }

    /** "Awa Ndiaye" pour un particulier, "Me Mariama Fall, notaire" pour un notaire (comme le prototype). */
    private static String demandeur(Demande demande) {
        return demande.getUtilisateur() == null ? demande.getDeclarantNom() : demande.getDeclarantNom() + ", notaire";
    }

    private Detail detail(Demande demande) {
        boolean particulier = demande.getUtilisateur() == null;
        Optional<Resultat> resultat = resultats.findByDemandeId(demande.getId());
        List<Piece> liste = pieces.findByDemandeIdOrderById(demande.getId()).stream()
                .map(piece -> new Piece(piece.getId(), piece.getType().getLibelle(), piece.getNomFichier(),
                        taille(piece), !piece.getChemin().toLowerCase(Locale.ROOT).endsWith(".pdf")))
                .toList();
        String tarif = tarifs.findFirstByTypeDemandeOrderByIdAsc(demande.getType())
                .map(trouve -> SuiviService.montant(trouve.getMontant())).orElse(null);
        return new Detail(demande.getReference(), demande.getStatut().getLibelle(), demande.getStatut().getBadge(),
                demande.getType().getLibelle(), demande.getNumeroTitreFoncier(), demande.getPersonneConcernee(),
                particulier ? demande.getDeclarantNom() + " (particulier)" : demandeur(demande),
                particulier ? demande.getDeclarantTelephone() + " · Vérifié par SMS" : demande.getDeclarantTelephone(),
                particulier ? demande.getDeclarantCni() + " (déclaré, non vérifié)" : null,
                DATE_LONGUE.format(demande.getDateSoumission()), liste,
                demande.getStatut() == StatutDemande.SOUMISE, demande.getStatut() == StatutDemande.EN_COURS, tarif,
                resultat.map(trouve -> trouve.getType().getLibelle() + ", publié le "
                        + DATE_LONGUE.format(trouve.getDatePublication())).orElse(null),
                demande.getDateLimitePaiement() == null ? null : DATE_LONGUE.format(demande.getDateLimitePaiement()));
    }

    /** "1,2 Mo" ou "412 Ko", lu sur le disque ; vide si le fichier manque. */
    private String taille(PieceJointe piece) {
        try {
            long octets = Files.size(stockagePieces.fichier(piece.getChemin()));
            return octets >= 1024 * 1024
                    ? String.format(Locale.FRENCH, "%.1f Mo", octets / (1024.0 * 1024.0))
                    : Math.max(1, octets / 1024) + " Ko";
        } catch (IOException | IllegalArgumentException erreur) {
            return "";
        }
    }

    // ---------------------------------------------------------------- pieces

    /**
     * Ouvre une piece de la demande, pour le conservateur de son bureau ; journalise l'ouverture.
     *
     * @throws DemandeIntrouvableException autre bureau, piece d'une autre demande, ou fichier absent
     */
    @Transactional
    public FichierPiece ouvrirPiece(String emailConservateur, String reference, Long idPiece) {
        Utilisateur conservateur = conservateur(emailConservateur);
        Demande demande = duBureau(reference, conservateur, false);
        PieceJointe piece = pieces.findById(idPiece)
                .filter(trouvee -> trouvee.getDemande().getId().equals(demande.getId()))
                .orElseThrow(DemandeIntrouvableException::new);
        Path fichier;
        try {
            fichier = stockagePieces.fichier(piece.getChemin());
        } catch (IllegalArgumentException cheminRefuse) {
            throw new DemandeIntrouvableException();
        }
        if (!Files.isRegularFile(fichier)) {
            throw new DemandeIntrouvableException();
        }
        journal.journaliser("Consultation de la pièce « " + piece.getType().getLibelle() + " »", conservateur, demande);
        String extension = StockageResultats.extension(piece.getChemin());
        return new FichierPiece(fichier,
                reference + "-" + piece.getType().name().toLowerCase(Locale.ROOT).replace('_', '-') + "." + extension,
                StockageResultats.typeDeContenu(piece.getChemin()), !"pdf".equals(extension));
    }

    // ---------------------------------------------------------------- decisions

    /** SOUMISE vers EN_COURS : le dossier est conforme, la recherche commence. */
    @Transactional
    public void declarerConforme(String emailConservateur, String reference) {
        Utilisateur conservateur = conservateur(emailConservateur);
        Demande demande = duBureau(reference, conservateur, true);
        if (demande.getStatut() != StatutDemande.SOUMISE) {
            throw new RegleMetierException("Seule une demande soumise peut être déclarée conforme. Statut actuel : « "
                    + demande.getStatut().getLibelle() + " ».");
        }
        demande.changerStatut(StatutDemande.EN_COURS);
        journal.journaliser("Dossier déclaré conforme", conservateur, demande);
    }

    /** SOUMISE vers REJETEE (regle 5 : le rejet n'est possible qu'a la verification de conformite). */
    @Transactional
    public void rejeter(String emailConservateur, String reference) {
        Utilisateur conservateur = conservateur(emailConservateur);
        Demande demande = duBureau(reference, conservateur, true);
        if (demande.getStatut() != StatutDemande.SOUMISE) {
            throw new RegleMetierException("Seule une demande soumise peut être rejetée. Statut actuel : « "
                    + demande.getStatut().getLibelle() + " ».");
        }
        demande.changerStatut(StatutDemande.REJETEE);
        notifications.notifierRejet(demande);
        journal.journaliser("Rejet de la demande : dossier non conforme", conservateur, demande);
    }

    /**
     * EN_COURS vers DISPONIBLE. Le resultat est un PDF signe par le conservateur : l'application le stocke,
     * elle ne le produit jamais. Frais : le tarif du type de la demande ; delai : portail.paiement.delai.
     * Les lignes sont ecrites d'abord, le fichier est range ensuite : si une ecriture est refusee,
     * tout est annule et le fichier temporaire supprime.
     *
     * @param typeResultat ETAT_DROITS_REELS ou REQUISITION_NEGATIVE (texte du formulaire)
     */
    @Transactional
    public void publier(String emailConservateur, String reference, String typeResultat, MultipartFile fichier) {
        Utilisateur conservateur = conservateur(emailConservateur);
        Demande demande = duBureau(reference, conservateur, true);
        if (demande.getStatut() != StatutDemande.EN_COURS) {
            throw new RegleMetierException("Le résultat ne peut être publié que pour une demande en cours. "
                    + "Statut actuel : « " + demande.getStatut().getLibelle() + " ».");
        }
        TypeResultat type = Arrays.stream(TypeResultat.values())
                .filter(valeur -> valeur.name().equals(typeResultat)).findFirst()
                .orElseThrow(() -> new RegleMetierException("Choisissez le type de résultat."));
        if (fichier == null || fichier.isEmpty()) {
            throw new RegleMetierException("Ajoutez le document du résultat, signé, au format PDF.");
        }
        if (fichier.getSize() > fichiers.tailleMax().toBytes()) {
            throw new RegleMetierException("Fichier trop volumineux : " + fichiers.tailleMax().toMegabytes() + " Mo maximum.");
        }
        if (stockagePieces.reconnaitre(fichier).filter(format -> format == FormatFichier.PDF).isEmpty()) {
            throw new RegleMetierException("Format non accepté : PDF uniquement.");
        }
        Tarif tarif = tarifs.findFirstByTypeDemandeOrderByIdAsc(demande.getType())
                .orElseThrow(() -> new IllegalStateException("Aucun tarif pour " + demande.getType()));

        Instant maintenant = horloge.instant();
        Path temporaire = stockagePieces.enregistrerTemporaire(fichier, FormatFichier.PDF);
        try {
            resultats.save(new Resultat(type, temporaire.getFileName().toString(), maintenant, demande));
            demande.setTarif(tarif);
            demande.setDateLimitePaiement(maintenant.plus(paiement.delai()));
            demande.changerStatut(StatutDemande.DISPONIBLE);
            notifications.notifierDisponibilite(demande);
            journal.journaliser("Publication du résultat : " + type.getLibelle() + ", frais de "
                    + SuiviService.montant(tarif.getMontant()), conservateur, demande);
            demandes.flush();
            stockageResultats.deposer(temporaire);
        } finally {
            try {
                Files.deleteIfExists(temporaire);
            } catch (IOException erreur) {
                // le nettoyage au demarrage (StockagePieces) le retirera
            }
        }
    }

    // ---------------------------------------------------------------- outils

    private Utilisateur conservateur(String email) {
        Utilisateur conservateur = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Conservateur connecte introuvable"));
        if (conservateur.getBureau() == null) {
            throw new IllegalStateException("Conservateur sans bureau : " + email);
        }
        return conservateur;
    }

    /**
     * La demande, si elle est du bureau du conservateur. Pour une decision, la ligne est verrouillee
     * (SELECT ... FOR UPDATE) jusqu'a la fin de la transaction : deux conservateurs ne peuvent pas
     * statuer en meme temps, le second voit le statut laisse par le premier.
     */
    private Demande duBureau(String reference, Utilisateur conservateur, boolean verrouiller) {
        Optional<Demande> trouvee = verrouiller ? demandes.findWithLockByReference(reference)
                : demandes.findByReference(reference);
        return trouvee.filter(demande -> demande.getBureau().getId().equals(conservateur.getBureau().getId()))
                .orElseThrow(DemandeIntrouvableException::new);
    }
}
