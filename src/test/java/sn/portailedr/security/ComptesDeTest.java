package sn.portailedr.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Cree de vrais comptes dans UTILISATEUR pour les tests de securite.
 * Les tests qui l'utilisent sont @Transactional : les comptes sont supprimes a la fin de chaque test.
 */
final class ComptesDeTest {

    static final String MOT_DE_PASSE = "mot-de-passe-de-test";

    private final UtilisateurRepository utilisateurs;
    private final BureauRepository bureaux;
    private final PasswordEncoder encodeur;

    ComptesDeTest(UtilisateurRepository utilisateurs, BureauRepository bureaux, PasswordEncoder encodeur) {
        this.utilisateurs = utilisateurs;
        this.bureaux = bureaux;
        this.encodeur = encodeur;
    }

    Utilisateur notaire() {
        return creer("Me Test Notaire", "notaire@test.sn", Role.NOTAIRE, null, true);
    }

    Utilisateur conservateur() {
        Bureau dakarPlateau = bureaux.findByNom("Bureau de Dakar-Plateau").orElseThrow();
        return creer("Test Conservateur", "conservateur@test.sn", Role.CONSERVATEUR, dakarPlateau, true);
    }

    Utilisateur administrateur() {
        return creer("Test Administrateur", "admin@test.sn", Role.ADMINISTRATEUR, null, true);
    }

    Utilisateur notaireInactif() {
        return creer("Me Test Inactif", "inactif@test.sn", Role.NOTAIRE, null, false);
    }

    private Utilisateur creer(String nom, String email, Role role, Bureau bureau, boolean actif) {
        Utilisateur utilisateur = new Utilisateur(nom, email, "+221 77 000 00 00", encodeur.encode(MOT_DE_PASSE), role, bureau);
        utilisateur.setActif(actif);
        return utilisateurs.save(utilisateur);
    }
}
