package sn.portailedr.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.domaine.TypePiece;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.LivreFoncierRepository;
import sn.portailedr.repository.PieceJointeRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.service.DemandeInvalideException.ErreurChamp;
import sn.portailedr.service.VerificationSms.Resultat;

/**
 * Depot d'une demande d'etat des droits reels.
 * <ul>
 *   <li>Particulier : {@link #preparer} verifie le formulaire et met les pieces en zone temporaire,
 *       {@link #demarrerVerification} envoie le code SMS, {@link #validerCode} enregistre la demande.
 *       Jusqu'au code valide, rien n'est ecrit en base.</li>
 *   <li>Notaire : {@link #soumettreNotaire} verifie et enregistre en une fois, sans CNI ni SMS.</li>
 * </ul>
 */
@Service
public class SoumissionService {

    private static final List<FormatFichier> IMAGES = List.of(FormatFichier.JPG, FormatFichier.PNG);
    private static final List<FormatFichier> PDF_OU_IMAGES = List.of(FormatFichier.PDF, FormatFichier.JPG, FormatFichier.PNG);
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final DateTimeFormatter DATE_LONGUE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(DAKAR);
    private static final DateTimeFormatter DATE_COURTE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH).withZone(DAKAR);

    private final DemandeRepository demandes;
    private final PieceJointeRepository piecesJointes;
    private final LivreFoncierRepository livres;
    private final BureauRepository bureaux;
    private final UtilisateurRepository utilisateurs;
    private final JournalService journal;
    private final NotificationService notifications;
    private final StockagePieces stockage;
    private final GenerateurReference references;
    private final SmsProprietes sms;
    private final FichiersProprietes fichiers;
    private final Clock horloge;
    private final SecureRandom hasard = new SecureRandom();

    public SoumissionService(DemandeRepository demandes, PieceJointeRepository piecesJointes,
            LivreFoncierRepository livres, BureauRepository bureaux, UtilisateurRepository utilisateurs,
            JournalService journal, NotificationService notifications, StockagePieces stockage,
            GenerateurReference references, SmsProprietes sms, FichiersProprietes fichiers, Clock horloge) {
        this.demandes = demandes;
        this.piecesJointes = piecesJointes;
        this.livres = livres;
        this.bureaux = bureaux;
        this.utilisateurs = utilisateurs;
        this.journal = journal;
        this.notifications = notifications;
        this.stockage = stockage;
        this.references = references;
        this.sms = sms;
        this.fichiers = fichiers;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- formulaire

    /** Bureaux proposes pour une demande sur une personne. */
    @Transactional(readOnly = true)
    public List<BureauChoix> bureaux() {
        return bureaux.findAll(Sort.by("nom")).stream()
                .map(bureau -> new BureauChoix(bureau.getId(), bureau.getNom()))
                .toList();
    }

    /**
     * Verifie tout le formulaire (objet, bureau, declarant, pieces) et renvoie toutes les erreurs
     * d'un coup. Si tout est valide, copie les pieces dans la zone temporaire. N'ecrit rien en base.
     *
     * @param particulier vrai pour un visiteur : declarant et CNI obligatoires ; faux pour un notaire,
     *                    dont les champs declarant et les photos de CNI sont ignores
     * @throws DemandeInvalideException si au moins un champ est refuse ; aucun fichier n'est alors garde
     */
    @Transactional(readOnly = true)
    public DemandeEnAttente preparer(DemandeFormulaire formulaire, boolean particulier) {
        List<ErreurChamp> erreurs = new ArrayList<>();

        // objet de la demande et bureau competent
        String numero = null;
        String personne = null;
        Bureau bureau = null;
        if (formulaire.getType() == TypeDemande.BIEN) {
            if (vide(formulaire.getNumeroTitreFoncier())) {
                erreurs.add(new ErreurChamp("numeroTitreFoncier", "Indiquez le numéro du titre foncier."));
            } else {
                Optional<NumeroTitreFoncier> lu = NumeroTitreFoncier.lire(formulaire.getNumeroTitreFoncier());
                if (lu.isEmpty()) {
                    erreurs.add(new ErreurChamp("numeroTitreFoncier",
                            "Numéro de titre foncier invalide. Format attendu : 12.405/NGA"));
                } else {
                    numero = lu.get().normalise();
                    bureau = livres.findByCode(lu.get().code()).map(livre -> livre.getBureau()).orElse(null);
                    if (bureau == null) {
                        erreurs.add(new ErreurChamp("numeroTitreFoncier", "Livre foncier inconnu. Si votre titre a été "
                                + "reporté dans un autre livre foncier, indiquez son numéro actuel."));
                    }
                }
            }
        } else if (formulaire.getType() == null) {
            erreurs.add(new ErreurChamp("type", "Choisissez l'objet de la demande."));
        } else {
            if (vide(formulaire.getPersonneConcernee())) {
                erreurs.add(new ErreurChamp("personneConcernee", "Indiquez le nom de la personne concernée."));
            } else {
                personne = formulaire.getPersonneConcernee().trim();
            }
            // le demandeur choisit le bureau ; le serveur verifie seulement qu'il existe
            if (formulaire.getIdBureau() == null) {
                erreurs.add(new ErreurChamp("idBureau", "Choisissez le bureau de conservation foncière."));
            } else {
                bureau = bureaux.findById(formulaire.getIdBureau()).orElse(null);
                if (bureau == null) {
                    erreurs.add(new ErreurChamp("idBureau", "Bureau introuvable."));
                }
            }
        }

        // declarant : saisi par le particulier ; pour un notaire, repris du compte a l'enregistrement
        if (particulier) {
            obligatoire(formulaire.getDeclarantNom(), "declarantNom", "Indiquez votre nom complet.", erreurs);
            obligatoire(formulaire.getDeclarantTelephone(), "declarantTelephone",
                    "Indiquez votre numéro de téléphone.", erreurs);
            obligatoire(formulaire.getDeclarantCni(), "declarantCni",
                    "Indiquez le numéro de votre carte nationale d'identité.", erreurs);
        }

        // pieces : CNI pour un particulier, copie du titre pour un bien
        List<PieceVerifiee> aGarder = new ArrayList<>();
        if (particulier) {
            verifierPiece(formulaire.getCniRecto(), TypePiece.CNI_RECTO, "cniRecto",
                    "Ajoutez le recto de votre carte nationale d'identité.", IMAGES, erreurs, aGarder);
            verifierPiece(formulaire.getCniVerso(), TypePiece.CNI_VERSO, "cniVerso",
                    "Ajoutez le verso de votre carte nationale d'identité.", IMAGES, erreurs, aGarder);
        }
        if (formulaire.getType() == TypeDemande.BIEN) {
            verifierPiece(formulaire.getCopieTitreFoncier(), TypePiece.COPIE_TITRE_FONCIER, "copieTitreFoncier",
                    "Ajoutez la copie du titre foncier.", PDF_OU_IMAGES, erreurs, aGarder);
        }

        if (!erreurs.isEmpty()) {
            throw new DemandeInvalideException(erreurs);
        }

        // tout est valide : alors seulement, les fichiers sont copies dans la zone temporaire
        List<PieceEnAttente> pieces = aGarder.stream()
                .map(piece -> new PieceEnAttente(piece.type(), nomOrigine(piece.fichier()),
                        stockage.enregistrerTemporaire(piece.fichier(), piece.format()).toString()))
                .toList();
        return new DemandeEnAttente(formulaire.getType(), numero, personne, bureau.getId(), bureau.getNom(),
                particulier ? formulaire.getDeclarantNom().trim() : null,
                particulier ? formulaire.getDeclarantTelephone().trim() : null,
                particulier ? formulaire.getDeclarantCni().trim() : null, pieces);
    }

    private record PieceVerifiee(TypePiece type, MultipartFile fichier, FormatFichier format) {
    }

    private void verifierPiece(MultipartFile fichier, TypePiece type, String champ, String messageAbsente,
            List<FormatFichier> acceptes, List<ErreurChamp> erreurs, List<PieceVerifiee> aGarder) {
        if (fichier == null || fichier.isEmpty()) {
            erreurs.add(new ErreurChamp(champ, messageAbsente));
            return;
        }
        if (fichier.getSize() > fichiers.tailleMax().toBytes()) {
            erreurs.add(new ErreurChamp(champ,
                    "Fichier trop volumineux : " + fichiers.tailleMax().toMegabytes() + " Mo maximum."));
            return;
        }
        Optional<FormatFichier> format = stockage.reconnaitre(fichier).filter(acceptes::contains);
        if (format.isEmpty()) {
            erreurs.add(new ErreurChamp(champ, acceptes.equals(IMAGES)
                    ? "Format non accepté : JPG ou PNG uniquement."
                    : "Format non accepté : PDF, JPG ou PNG uniquement."));
            return;
        }
        aGarder.add(new PieceVerifiee(type, fichier, format.get()));
    }

    // ---------------------------------------------------------------- code SMS (particulier)

    /** Premier envoi du code, juste apres un formulaire valide. */
    public VerificationSms demarrerVerification(DemandeEnAttente demande) {
        VerificationSms verification = new VerificationSms(demande.declarantTelephone(), sms);
        envoyer(verification);
        return verification;
    }

    /** @throws VerificationRefuseeException si le delai de renvoi n'est pas ecoule ou si les envois sont epuises */
    public void renvoyerCode(VerificationSms verification) {
        if (verification.envoisRestants() <= 0) {
            throw new VerificationRefuseeException("Vous avez atteint le nombre maximal d'envois de code.",
                    verification.estEpuisee(horloge.instant()));
        }
        long attente = verification.secondesAvantRenvoi(horloge.instant());
        if (attente > 0) {
            throw new VerificationRefuseeException(
                    "Vous pourrez demander un nouveau code dans " + attente + " s.", false);
        }
        envoyer(verification);
    }

    private void envoyer(VerificationSms verification) {
        String code = tirerCode();
        verification.enregistrerEnvoi(code, horloge.instant());
        notifications.envoyerCodeVerification(verification.getTelephone(), code);
    }

    private String tirerCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < sms.longueurCode(); i++) {
            code.append(hasard.nextInt(10));
        }
        return code.toString();
    }

    /**
     * Verifie le code ; s'il est bon, enregistre la demande (statut SOUMISE, telephone verifie).
     *
     * @return la reference (code de suivi) de la demande creee
     * @throws VerificationRefuseeException code faux, expire ou bloque ; definitive si plus rien n'est possible
     */
    @Transactional
    public String validerCode(DemandeEnAttente demande, VerificationSms verification, String code) {
        Instant maintenant = horloge.instant();
        Resultat resultat = verification.verifier(code, maintenant);
        if (resultat != Resultat.VALIDE) {
            throw refus(resultat, verification, maintenant);
        }
        Demande enregistree = enregistrer(demande, null, true);
        journal.journaliser("Vérification du numéro de téléphone par SMS", null, enregistree);
        journal.journaliser("Soumission de la demande", null, enregistree);
        return enregistree.getReference();
    }

    private VerificationRefuseeException refus(Resultat resultat, VerificationSms verification, Instant maintenant) {
        if (verification.estEpuisee(maintenant)) {
            return new VerificationRefuseeException("La vérification de votre numéro a échoué et aucun nouveau code "
                    + "ne peut être envoyé. Votre demande n'a pas été enregistrée : vous pouvez la recommencer.", true);
        }
        return switch (resultat) {
            case INCORRECT -> new VerificationRefuseeException("Code incorrect. Il vous reste "
                    + verification.tentativesRestantes()
                    + (verification.tentativesRestantes() > 1 ? " essais." : " essai."), false);
            case EXPIRE -> new VerificationRefuseeException("Ce code a expiré. Demandez un nouveau code.", false);
            default -> new VerificationRefuseeException("Trop d'essais incorrects. Demandez un nouveau code.", false);
        };
    }

    // ---------------------------------------------------------------- notaire

    /**
     * Demande d'un notaire connecte : pas de CNI ni de code SMS ; nom et telephone du declarant
     * sont ceux du compte ; la demande est rattachee au compte.
     *
     * @return la reference de la demande creee
     */
    @Transactional
    public String soumettreNotaire(DemandeFormulaire formulaire, String emailNotaire) {
        Utilisateur notaire = utilisateurs.findByEmailIgnoreCase(emailNotaire)
                .filter(compte -> compte.getRole() == Role.NOTAIRE)
                .orElseThrow(() -> new IllegalStateException("Notaire connecte introuvable"));
        DemandeEnAttente demande = preparer(formulaire, false);
        try {
            Demande enregistree = enregistrer(demande, notaire, false);
            journal.journaliser("Soumission de la demande", notaire, enregistree);
            return enregistree.getReference();
        } finally {
            demande.supprimerFichiers();
        }
    }

    // ---------------------------------------------------------------- enregistrement

    /**
     * Ecrit la demande et ses pieces, puis deplace les fichiers de la zone temporaire vers les pieces.
     * Les lignes d'abord, les fichiers ensuite : une contrainte refusee annule tout avant qu'un
     * fichier ne bouge.
     */
    private Demande enregistrer(DemandeEnAttente enAttente, Utilisateur notaire, boolean telephoneVerifie) {
        Demande demande = new Demande();
        demande.setReference(references.nouvelle(demandes::existsByReference));
        demande.setType(enAttente.type());
        demande.setStatut(StatutDemande.SOUMISE);
        demande.setNumeroTitreFoncier(enAttente.numeroTitreFoncier());
        demande.setPersonneConcernee(enAttente.personneConcernee());
        demande.setBureau(bureaux.getReferenceById(enAttente.idBureau()));
        if (notaire == null) {
            demande.setDeclarantNom(enAttente.declarantNom());
            demande.setDeclarantTelephone(enAttente.declarantTelephone());
            demande.setDeclarantCni(enAttente.declarantCni());
        } else {
            demande.setUtilisateur(notaire);
            demande.setDeclarantNom(notaire.getNom());
            demande.setDeclarantTelephone(notaire.getTelephone());
        }
        demande.setTelephoneVerifie(telephoneVerifie);
        demande.setDateSoumission(horloge.instant());
        demandes.save(demande);

        for (PieceEnAttente piece : enAttente.pieces()) {
            piecesJointes.save(new PieceJointe(piece.type(), piece.nomOrigine(),
                    stockage.nomDefinitif(piece.fichierTemporaire()), demande));
        }
        piecesJointes.flush();
        enAttente.pieces().forEach(piece -> stockage.deplacer(piece.fichierTemporaire()));
        return demande;
    }

    // ---------------------------------------------------------------- lecture

    /**
     * Page de confirmation, lisible par la session qui a depose la demande ou par le notaire
     * auquel elle appartient. Vide dans tous les autres cas, y compris une reference inconnue :
     * le controleur repond alors 404, sans dire si la reference existe.
     *
     * @param referencesDeLaSession references deposees depuis la session courante
     * @param emailConnecte         adresse du compte connecte, ou null pour un visiteur
     */
    @Transactional(readOnly = true)
    public Optional<ConfirmationVue> confirmation(String reference, Set<String> referencesDeLaSession,
            String emailConnecte) {
        return demandes.findByReference(reference)
                .filter(demande -> referencesDeLaSession.contains(demande.getReference())
                        || (demande.getUtilisateur() != null && emailConnecte != null
                                && demande.getUtilisateur().getEmail().equalsIgnoreCase(emailConnecte)))
                .map(demande -> new ConfirmationVue(demande.getReference(), demande.getUtilisateur() != null,
                        demande.getType().getLibelle(), demande.getNumeroTitreFoncier(), demande.getPersonneConcernee(),
                        demande.getBureau().getNom(), DATE_LONGUE.format(demande.getDateSoumission()),
                        piecesJointes.findByDemandeIdOrderById(demande.getId()).stream()
                                .map(piece -> piece.getType().getLibelle()).toList()));
    }

    /** "Mes dernieres demandes" du profil : les 5 plus recentes du notaire, vide pour les autres comptes. */
    @Transactional(readOnly = true)
    public List<DemandeLigne> dernieresDemandes(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .filter(compte -> compte.getRole() == Role.NOTAIRE)
                .map(compte -> demandes.findTop5ByUtilisateurIdOrderByDateSoumissionDescIdDesc(compte.getId()))
                .orElse(List.of()).stream()
                .map(demande -> new DemandeLigne(demande.getReference(),
                        demande.getType() == TypeDemande.BIEN ? "Bien · " + demande.getNumeroTitreFoncier() : "Personne",
                        DATE_COURTE.format(demande.getDateSoumission()), demande.getStatut().getLibelle(),
                        demande.getStatut().getBadge()))
                .toList();
    }

    // ---------------------------------------------------------------- outils

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static void obligatoire(String valeur, String champ, String message, List<ErreurChamp> erreurs) {
        if (vide(valeur)) {
            erreurs.add(new ErreurChamp(champ, message));
        }
    }

    /** Nom d'origine, pour l'affichage seulement : sans chemin, et jamais vide. */
    private static String nomOrigine(MultipartFile fichier) {
        String nom = fichier.getOriginalFilename();
        if (nom == null || nom.isBlank()) {
            return "piece";
        }
        String sansChemin = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
        return sansChemin.length() > 200 ? sansChemin.substring(0, 200) : sansChemin;
    }
}
