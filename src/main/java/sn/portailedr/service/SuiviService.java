package sn.portailedr.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.domaine.Paiement;
import sn.portailedr.domaine.Resultat;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.StatutPaiement;
import sn.portailedr.domaine.TypeNotification;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.NotificationRepository;
import sn.portailedr.repository.PaiementRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.service.SuiviVue.Etape;
import sn.portailedr.service.SuiviVue.LigneHistorique;

/**
 * Suivi d'une demande, paiement simule et telechargement du resultat.
 * Acces (regles 3 et 4) : le visiteur qui a passe le code SMS pour CETTE reference dans sa session,
 * ou le notaire auquel la demande appartient, sans code SMS.
 */
@Service
public class SuiviService {

    /** Ce que le controleur doit faire d'une requete sur une reference. */
    public enum Acces { AUTORISE, VERIFICATION_REQUISE, INTROUVABLE }

    /** Meme forme que ck_demande_reference. */
    private static final Pattern FORMAT_REFERENCE = Pattern.compile("^EDR-[0-9]{4}-[2-9A-HJKMNP-Z]{6}$");
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_LONGUE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(DAKAR);
    private static final DateTimeFormatter DATE_COURTE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH).withZone(DAKAR);
    private static final List<StatutDemande> FRISE =
            List.of(StatutDemande.SOUMISE, StatutDemande.EN_COURS, StatutDemande.DISPONIBLE, StatutDemande.PAYEE);
    private static final List<String> ETAPES = List.of("Soumise", "En cours de traitement", "Disponible", "Payée");

    private final DemandeRepository demandes;
    private final ResultatRepository resultats;
    private final PaiementRepository paiements;
    private final NotificationRepository notificationsEnvoyees;
    private final UtilisateurRepository utilisateurs;
    private final JournalService journal;
    private final NotificationService notifications;
    private final PaiementService paiement;
    private final VerificationsSuivi verifications;
    private final StockageResultats stockage;
    private final SmsProprietes sms;
    private final SuiviProprietes suivi;
    private final Clock horloge;
    private final SecureRandom hasard = new SecureRandom();

    public SuiviService(DemandeRepository demandes, ResultatRepository resultats, PaiementRepository paiements,
            NotificationRepository notificationsEnvoyees, UtilisateurRepository utilisateurs, JournalService journal,
            NotificationService notifications, PaiementService paiement, VerificationsSuivi verifications,
            StockageResultats stockage, SmsProprietes sms, SuiviProprietes suivi, Clock horloge) {
        this.demandes = demandes;
        this.resultats = resultats;
        this.paiements = paiements;
        this.notificationsEnvoyees = notificationsEnvoyees;
        this.utilisateurs = utilisateurs;
        this.journal = journal;
        this.notifications = notifications;
        this.paiement = paiement;
        this.verifications = verifications;
        this.stockage = stockage;
        this.sms = sms;
        this.suivi = suivi;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- code de suivi et code SMS

    /** Reference saisie, en majuscules et sans espaces autour ; vide si elle n'a pas la forme EDR-2026-K7P4QX. */
    public static Optional<String> lireReference(String saisie) {
        if (saisie == null) {
            return Optional.empty();
        }
        String reference = saisie.trim().toUpperCase(Locale.ROOT);
        return FORMAT_REFERENCE.matcher(reference).matches() ? Optional.of(reference) : Optional.empty();
    }

    /**
     * Envoie un code pour la reference si les limites le permettent. Le comportement est le meme
     * que la reference existe ou non : seule une reference existante recoit vraiment un SMS
     * (et une ligne NOTIFICATION).
     *
     * @return le refus (delai de renvoi, envois epuises), ou vide si un code est parti
     */
    @Transactional
    public Optional<String> demanderCode(String reference) {
        Instant maintenant = horloge.instant();
        Optional<Demande> demande = demandes.findByReference(reference);
        return verifications.utiliser(reference, maintenant, verification -> {
            if (verification.envoisRestants() <= 0) {
                return Optional.of("Vous avez atteint le nombre maximal d'envois de code. Réessayez plus tard.");
            }
            long attente = verification.secondesAvantRenvoi(maintenant);
            if (attente > 0) {
                return Optional.of("Vous pourrez demander un nouveau code dans " + attente + " s.");
            }
            String code = tirerCode();
            verification.enregistrerEnvoi(code, maintenant);
            demande.ifPresent(trouvee -> notifications.envoyerCodeSuivi(trouvee, code));
            return Optional.empty();
        });
    }

    /**
     * Verifie le code SMS. S'il est bon, la reference est ouverte dans la session (et seulement elle).
     *
     * @throws VerificationRefuseeException code faux, expire ou bloque ; memes messages pour une reference inconnue
     */
    @Transactional
    public void verifierCode(String reference, String code, AccesSuivi accesSession) {
        Instant maintenant = horloge.instant();
        VerificationSms.Resultat resultat = verifications.utiliser(reference, maintenant, verification -> {
            VerificationSms.Resultat lu = verification.verifier(code, maintenant);
            if (lu != VerificationSms.Resultat.VALIDE && verification.estEpuisee(maintenant)) {
                throw new VerificationRefuseeException("Trop d'essais pour ce code de suivi. Réessayez plus tard.", true);
            }
            if (lu == VerificationSms.Resultat.INCORRECT) {
                throw new VerificationRefuseeException("Code incorrect. Il vous reste " + verification.tentativesRestantes()
                        + (verification.tentativesRestantes() > 1 ? " essais." : " essai."), false);
            }
            return lu;
        });
        switch (resultat) {
            case EXPIRE -> throw new VerificationRefuseeException("Ce code a expiré. Demandez un nouveau code.", false);
            case TENTATIVES_EPUISEES ->
                throw new VerificationRefuseeException("Trop d'essais incorrects. Demandez un nouveau code.", false);
            default -> {
                verifications.oublier(reference);
                accesSession.ouvrir(reference, maintenant);
                demandes.findByReference(reference).ifPresent(demande ->
                        journal.journaliser("Vérification du numéro de téléphone pour le suivi", null, demande));
            }
        }
    }

    private String tirerCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < sms.longueurCode(); i++) {
            code.append(hasard.nextInt(10));
        }
        return code.toString();
    }

    // ---------------------------------------------------------------- acces

    /**
     * Regles 3 et 4 : un notaire connecte ne lit que ses demandes (sinon INTROUVABLE, donc 404) ;
     * un visiteur doit avoir passe le code SMS pour cette reference, il y a moins de 30 minutes
     * d'inactivite (sinon VERIFICATION_REQUISE : retour a /suivi). La consultation repousse la fermeture.
     *
     * @param emailConnecte compte connecte (seul un notaire arrive ici, voir SecurityConfig), ou null
     */
    @Transactional(readOnly = true)
    public Acces acces(String reference, AccesSuivi accesSession, String emailConnecte) {
        Optional<Demande> demande = demandes.findByReference(reference);
        if (emailConnecte != null) {
            boolean proprietaire = demande.map(Demande::getUtilisateur)
                    .filter(notaire -> notaire.getEmail().equalsIgnoreCase(emailConnecte)).isPresent();
            return proprietaire ? Acces.AUTORISE : Acces.INTROUVABLE;
        }
        boolean verifiee = accesSession != null && accesSession.estOuvert(reference, horloge.instant(), suivi.inactivite());
        return verifiee && demande.isPresent() ? Acces.AUTORISE : Acces.VERIFICATION_REQUISE;
    }

    // ---------------------------------------------------------------- page de suivi

    /** A appeler apres {@link #acces} : la demande existe. */
    @Transactional(readOnly = true)
    public SuiviVue vue(String reference) {
        Instant maintenant = horloge.instant();
        Demande demande = demandes.findByReference(reference).orElseThrow();
        StatutDemande statut = demande.getStatut();
        Optional<Resultat> resultat = resultats.findByDemandeId(demande.getId());
        Optional<Paiement> paye = paiements.findByDemandeId(demande.getId())
                .filter(trouve -> trouve.getStatut() == StatutPaiement.CONFIRME);

        boolean disponible = statut == StatutDemande.DISPONIBLE;
        boolean delaiPaiementDepasse = disponible
                && (demande.getDateLimitePaiement() == null || !maintenant.isBefore(demande.getDateLimitePaiement()));
        Instant finTelechargement = paye.map(trouve -> trouve.getDatePaiement().plus(suivi.dureeTelechargement()))
                .orElse(null);
        boolean telechargementOuvert = statut == StatutDemande.PAYEE && finTelechargement != null
                && !maintenant.isAfter(finTelechargement);

        return new SuiviVue(demande.getReference(), demande.getType().getLibelle(), demande.getNumeroTitreFoncier(),
                demande.getPersonneConcernee(), demande.getBureau().getNom(),
                DATE_LONGUE.format(demande.getDateSoumission()),
                statut.getLibelle(), statut.getBadge(),
                explication(statut, delaiPaiementDepasse, telechargementOuvert),
                frise(demande, resultat, paye),
                disponible || statut == StatutDemande.PAYEE,
                disponible && !delaiPaiementDepasse,
                demande.getTarif() == null ? null : montant(demande.getTarif().getMontant()),
                demande.getDateLimitePaiement() == null ? null : DATE_LONGUE.format(demande.getDateLimitePaiement()),
                paye.map(trouve -> montant(trouve.getMontant()) + " réglés par " + trouve.getModePaiement().getLibelle()
                        + " le " + DATE_LONGUE.format(trouve.getDatePaiement())).orElse(null),
                resultat.map(trouve -> trouve.getType().getLibelle()).orElse(null),
                telechargementOuvert,
                finTelechargement == null ? null : DATE_LONGUE.format(finTelechargement),
                suivi.dureeTelechargement().toDays(),
                historique(demande));
    }

    private static String explication(StatutDemande statut, boolean delaiPaiementDepasse, boolean telechargementOuvert) {
        return switch (statut) {
            case SOUMISE -> "Votre demande a été transmise au conservateur du bureau compétent, qui vérifie la "
                    + "conformité du dossier.";
            case EN_COURS -> "Le dossier a été déclaré conforme. Le conservateur effectue la recherche dans les "
                    + "registres fonciers.";
            case REJETEE -> "La demande a été rejetée : le dossier n'a pas été jugé conforme. Vous pouvez déposer une "
                    + "nouvelle demande.";
            case DISPONIBLE -> delaiPaiementDepasse
                    ? "Le délai de paiement est dépassé : la demande va expirer et le résultat ne sera plus accessible."
                    : "Le résultat est disponible. Il est téléchargeable après le paiement des frais.";
            case PAYEE -> telechargementOuvert
                    ? "Le paiement est confirmé : le résultat est téléchargeable."
                    : "Le délai de téléchargement est dépassé. Pour obtenir un état des droits réels à jour, "
                            + "déposez une nouvelle demande.";
            case EXPIREE -> "Le délai de paiement est dépassé : la demande a expiré et le résultat n'est plus "
                    + "accessible. Vous pouvez déposer une nouvelle demande.";
        };
    }

    /** Frise du prototype. La date du passage EN_COURS n'est pas une colonne : elle n'est pas affichee. */
    private static List<Etape> frise(Demande demande, Optional<Resultat> resultat, Optional<Paiement> paye) {
        int courant = FRISE.indexOf(demande.getStatut());
        if (courant < 0) {
            return List.of();
        }
        List<String> dates = new ArrayList<>();
        dates.add(DATE_COURTE.format(demande.getDateSoumission()));
        dates.add(null);
        dates.add(resultat.map(trouve -> DATE_COURTE.format(trouve.getDatePublication())).orElse(null));
        dates.add(paye.map(trouve -> DATE_COURTE.format(trouve.getDatePaiement())).orElse(null));
        List<Etape> etapes = new ArrayList<>();
        for (int i = 0; i < FRISE.size(); i++) {
            String classe = i < courant ? "done" : i == courant ? "cur" : "";
            if (i == FRISE.size() - 1) {
                classe = (classe + " last").trim();
            }
            etapes.add(new Etape(ETAPES.get(i), i <= courant ? dates.get(i) : null, classe));
        }
        return etapes;
    }

    private List<LigneHistorique> historique(Demande demande) {
        String canal = demande.getUtilisateur() == null ? "SMS" : "Courriel";
        return notificationsEnvoyees.findByDemandeIdOrderByDateEnvoiDescIdDesc(demande.getId()).stream()
                .map(envoyee -> new LigneHistorique(
                        (envoyee.getType() == TypeNotification.CODE_VERIFICATION ? "SMS" : canal) + " · "
                                + envoyee.getType().getLibelle(),
                        DATE_COURTE.format(envoyee.getDateEnvoi())))
                .toList();
    }

    /** 1000 donne "1 000 FCFA". */
    static String montant(int francs) {
        return String.format(Locale.ROOT, "%,d", francs).replace(',', ' ') + " FCFA";
    }

    // ---------------------------------------------------------------- paiement

    /**
     * Paiement simule, en UNE transaction : paiement CONFIRME, statut PAYEE, notification PAIEMENT
     * et ligne de journal. Tout est annule si le prestataire ne confirme pas.
     * Le delai est verifie ici, pas seulement par le traitement planifie de la tranche 8.
     *
     * @param emailNotaire notaire proprietaire connecte, ou null pour un visiteur verifie
     * @throws RegleMetierException demande non payable, delai depasse, moyen absent, paiement en echec
     */
    @Transactional
    public void payer(String reference, ModePaiement mode, String emailNotaire) {
        Instant maintenant = horloge.instant();
        Demande demande = demandes.findWithLockByReference(reference).orElseThrow();
        if (demande.getStatut() == StatutDemande.PAYEE) {
            throw new RegleMetierException("Cette demande est déjà payée.");
        }
        if (demande.getStatut() != StatutDemande.DISPONIBLE) {
            throw new RegleMetierException("Cette demande ne peut pas être payée : son statut est « "
                    + demande.getStatut().getLibelle() + " ».");
        }
        if (demande.getDateLimitePaiement() == null || !maintenant.isBefore(demande.getDateLimitePaiement())) {
            throw new RegleMetierException("Le délai de paiement est dépassé : la demande ne peut plus être réglée.");
        }
        if (mode == null) {
            throw new RegleMetierException("Choisissez un moyen de paiement.");
        }
        if (demande.getTarif() == null) {
            throw new IllegalStateException("Demande disponible sans tarif : " + reference);
        }
        int montant = demande.getTarif().getMontant();

        Paiement effectue = paiement.payer(demande, montant, mode);
        if (effectue.getStatut() != StatutPaiement.CONFIRME) {
            throw new RegleMetierException(
                    "Le paiement simulé n'a pas abouti. La demande reste à régler : vous pouvez réessayer.");
        }
        demande.changerStatut(StatutDemande.PAYEE);
        notifications.notifierPaiement(demande);
        journal.journaliser("Paiement simulé de " + montant(montant) + " par " + mode.getLibelle(),
                acteur(emailNotaire), demande);
    }

    // ---------------------------------------------------------------- telechargement

    /**
     * Resultat d'une demande PAYEE, pendant la fenetre de telechargement (15 jours apres le paiement).
     * Apres, la demande reste PAYEE et le fichier reste au bureau ; seul le telechargement est refuse.
     *
     * @throws RegleMetierException pas encore payee, ou fenetre depassee
     */
    @Transactional
    public FichierResultat telecharger(String reference, String emailNotaire) {
        Instant maintenant = horloge.instant();
        Demande demande = demandes.findByReference(reference).orElseThrow();
        if (demande.getStatut() != StatutDemande.PAYEE) {
            throw new RegleMetierException("Le résultat n'est téléchargeable qu'après le paiement.");
        }
        Paiement paye = paiements.findByDemandeId(demande.getId())
                .filter(trouve -> trouve.getStatut() == StatutPaiement.CONFIRME)
                .orElseThrow(() -> new IllegalStateException("Demande payee sans paiement confirme : " + reference));
        if (maintenant.isAfter(paye.getDatePaiement().plus(suivi.dureeTelechargement()))) {
            throw new RegleMetierException("Le délai de téléchargement de " + suivi.dureeTelechargement().toDays()
                    + " jours est dépassé. Pour obtenir un état des droits réels à jour, déposez une nouvelle demande.");
        }
        Resultat resultat = resultats.findByDemandeId(demande.getId())
                .orElseThrow(() -> new IllegalStateException("Demande payee sans resultat : " + reference));
        Path fichier = stockage.fichier(resultat.getFichier());
        if (!Files.isRegularFile(fichier)) {
            throw new IllegalStateException("Fichier du resultat introuvable pour " + reference);
        }
        journal.journaliser("Téléchargement du résultat", acteur(emailNotaire), demande);
        String extension = StockageResultats.extension(resultat.getFichier());
        return new FichierResultat(fichier, reference + (extension.isEmpty() ? "" : "." + extension),
                StockageResultats.typeDeContenu(resultat.getFichier()));
    }

    private Utilisateur acteur(String email) {
        return email == null ? null : utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }
}
