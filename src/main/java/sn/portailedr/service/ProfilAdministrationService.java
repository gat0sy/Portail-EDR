package sn.portailedr.service;

import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Comptes des notaires, conservateurs et administrateurs, geres par l'administrateur.
 * Pas d'inscription libre ni de mot de passe oublie : l'administrateur fixe un mot de passe provisoire.
 */
@Service
public class ProfilAdministrationService {

    private final UtilisateurRepository utilisateurs;
    private final BureauRepository bureaux;
    private final PasswordEncoder encodeur;
    private final JournalService journal;

    public ProfilAdministrationService(UtilisateurRepository utilisateurs, BureauRepository bureaux,
            PasswordEncoder encodeur, JournalService journal) {
        this.utilisateurs = utilisateurs;
        this.bureaux = bureaux;
        this.encodeur = encodeur;
        this.journal = journal;
    }

    // ---------------------------------------------------------------- lecture

    /** @param filtre role a afficher, ou null pour tous les comptes */
    @Transactional(readOnly = true)
    public List<ProfilLigne> profils(Role filtre, String emailActeur) {
        List<Utilisateur> liste = filtre == null
                ? utilisateurs.findAllByOrderByNomAsc()
                : utilisateurs.findAllByRoleOrderByNomAsc(filtre);
        return liste.stream()
                .map(compte -> new ProfilLigne(compte.getId(), compte.getNom(), compte.getEmail(), compte.getRole(),
                        compte.getBureau() == null ? null : compte.getBureau().getNom(), compte.isActif(),
                        compte.getEmail().equalsIgnoreCase(emailActeur)))
                .toList();
    }

    @Transactional(readOnly = true)
    public long nombre(Role role) {
        return role == null ? utilisateurs.count() : utilisateurs.countByRole(role);
    }

    @Transactional(readOnly = true)
    public ProfilFormulaire formulaire(Long idCompte) {
        Utilisateur compte = trouver(idCompte);
        ProfilFormulaire formulaire = new ProfilFormulaire();
        formulaire.setNom(compte.getNom());
        formulaire.setEmail(compte.getEmail());
        formulaire.setTelephone(compte.getTelephone());
        formulaire.setRole(compte.getRole());
        formulaire.setIdBureau(compte.getBureau() == null ? null : compte.getBureau().getId());
        return formulaire;
    }

    // ---------------------------------------------------------------- actions

    @Transactional
    public void creer(NouveauProfilFormulaire formulaire, String emailActeur) {
        verifierEmailLibre(formulaire.getEmail(), null);
        Bureau bureau = bureauDuFormulaire(formulaire);
        Utilisateur compte = utilisateurs.save(new Utilisateur(formulaire.getNom().trim(), formulaire.getEmail().trim(),
                formulaire.getTelephone().trim(), encodeur.encode(formulaire.getMotDePasse()), formulaire.getRole(), bureau));
        String precision = bureau == null
                ? compte.getRole().getLibelle()
                : compte.getRole().getLibelle() + ", " + bureau.getNom();
        journal.journaliser("Création du profil " + compte.getNom() + " (" + precision + ")", acteur(emailActeur), null);
    }

    /**
     * Une ligne de journal pour la modification des informations, une autre pour un changement
     * d'affectation (bureau). Si rien ne change, rien n'est journalise.
     */
    @Transactional
    public void modifier(Long idCompte, ProfilFormulaire formulaire, String emailActeur) {
        Utilisateur compte = trouver(idCompte);
        Utilisateur administrateur = acteur(emailActeur);
        if (compte.getRole() != formulaire.getRole()) {
            if (administrateur.getId().equals(idCompte)) {
                throw new RegleMetierException("Vous ne pouvez pas modifier votre propre rôle.", "role");
            }
            if (estDernierAdministrateurActif(compte)) {
                throw new RegleMetierException("Impossible de retirer le rôle du dernier administrateur actif.", "role");
            }
        }
        verifierEmailLibre(formulaire.getEmail(), idCompte);
        Bureau bureau = bureauDuFormulaire(formulaire);

        boolean informationsModifiees = !compte.getNom().equals(formulaire.getNom().trim())
                || !compte.getEmail().equals(formulaire.getEmail().trim())
                || !compte.getTelephone().equals(formulaire.getTelephone().trim())
                || compte.getRole() != formulaire.getRole();
        boolean affectationModifiee = !Objects.equals(idDe(compte.getBureau()), idDe(bureau));

        compte.setNom(formulaire.getNom().trim());
        compte.setEmail(formulaire.getEmail().trim());
        compte.setTelephone(formulaire.getTelephone().trim());
        compte.setRole(formulaire.getRole());
        compte.setBureau(bureau);

        if (informationsModifiees) {
            journal.journaliser("Modification du profil " + compte.getNom(), administrateur, null);
        }
        if (affectationModifiee) {
            journal.journaliser(bureau == null
                    ? "Retrait de l'affectation de " + compte.getNom()
                    : "Affectation de " + compte.getNom() + " au " + bureau.getNom(), administrateur, null);
        }
    }

    @Transactional
    public void reinitialiserMotDePasse(Long idCompte, MotDePasseFormulaire formulaire, String emailActeur) {
        Utilisateur compte = trouver(idCompte);
        compte.setMotDePasse(encodeur.encode(formulaire.getNouveauMotDePasse()));
        journal.journaliser("Réinitialisation du mot de passe de " + compte.getNom(), acteur(emailActeur), null);
    }

    /**
     * Un administrateur ne peut pas desactiver son propre compte (il perdrait l'acces sur-le-champ),
     * ni le dernier administrateur actif (plus personne ne pourrait administrer la plateforme).
     */
    @Transactional
    public void desactiver(Long idCompte, String emailActeur) {
        Utilisateur administrateur = acteur(emailActeur);
        if (administrateur.getId().equals(idCompte)) {
            throw new RegleMetierException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        Utilisateur compte = trouver(idCompte);
        if (estDernierAdministrateurActif(compte)) {
            throw new RegleMetierException("Impossible de désactiver le dernier administrateur actif.");
        }
        compte.setActif(false);
        journal.journaliser("Désactivation du compte de " + compte.getNom(), administrateur, null);
    }

    @Transactional
    public void reactiver(Long idCompte, String emailActeur) {
        Utilisateur compte = trouver(idCompte);
        compte.setActif(true);
        journal.journaliser("Réactivation du compte de " + compte.getNom(), acteur(emailActeur), null);
    }

    // ---------------------------------------------------------------- regles

    /** Il doit toujours rester au moins un administrateur actif. */
    private boolean estDernierAdministrateurActif(Utilisateur compte) {
        return compte.getRole() == Role.ADMINISTRATEUR && compte.isActif()
                && utilisateurs.countByRoleAndActifTrue(Role.ADMINISTRATEUR) <= 1;
    }

    private void verifierEmailLibre(String email, Long idCompteModifie) {
        utilisateurs.findByEmailIgnoreCase(email.trim())
                .filter(existant -> !existant.getId().equals(idCompteModifie))
                .ifPresent(existant -> {
                    throw new RegleMetierException("Cette adresse e-mail est déjà utilisée par un autre compte.", "email");
                });
    }

    /** Un conservateur ne voit que les demandes de son bureau : il en a forcement un. */
    private Bureau bureauDuFormulaire(ProfilFormulaire formulaire) {
        if (formulaire.getIdBureau() == null) {
            if (formulaire.getRole() == Role.CONSERVATEUR) {
                throw new RegleMetierException("Un conservateur doit être affecté à un bureau.", "idBureau");
            }
            return null;
        }
        return bureaux.findById(formulaire.getIdBureau())
                .orElseThrow(() -> new RegleMetierException("Bureau introuvable.", "idBureau"));
    }

    private Utilisateur trouver(Long idCompte) {
        return utilisateurs.findById(idCompte).orElseThrow(() -> new RegleMetierException("Compte introuvable."));
    }

    private Utilisateur acteur(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Administrateur connecte introuvable"));
    }

    private static Long idDe(Bureau bureau) {
        return bureau == null ? null : bureau.getId();
    }
}
