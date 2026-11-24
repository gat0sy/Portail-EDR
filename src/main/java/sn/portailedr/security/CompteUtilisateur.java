package sn.portailedr.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;

/**
 * Utilisateur connecte, garde dans la session par Spring Security.
 * Ce n'est pas l'entite : seules les informations utiles a l'en-tete sont copiees
 * (nom, role). Le nom d'utilisateur Spring Security est l'adresse e-mail,
 * le role devient l'autorite ROLE_NOTAIRE, ROLE_CONSERVATEUR ou ROLE_ADMINISTRATEUR.
 */
public class CompteUtilisateur extends User {

    private final String nom;
    private final Role role;

    public CompteUtilisateur(Utilisateur utilisateur) {
        super(utilisateur.getEmail(), utilisateur.getMotDePasse(), utilisateur.isActif(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name())));
        this.nom = utilisateur.getNom();
        this.role = utilisateur.getRole();
    }

    public String getNom() {
        return nom;
    }

    public Role getRole() {
        return role;
    }

    public String getRoleLibelle() {
        return role.getLibelle();
    }

    /** Initiales des deux derniers mots du nom : "Me Mariama Fall" donne "MF". */
    public String getInitiales() {
        String[] mots = nom.trim().split("\\s+");
        String initiales = mots.length == 1
                ? mots[0].substring(0, Math.min(2, mots[0].length()))
                : mots[mots.length - 2].substring(0, 1) + mots[mots.length - 1].substring(0, 1);
        return initiales.toUpperCase();
    }
}
