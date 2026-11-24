package sn.portailedr.service;

import sn.portailedr.domaine.Role;

/** Un compte dans le tableau de la page Profils. Jamais l'empreinte du mot de passe. */
public record ProfilLigne(Long id, String nom, String email, Role role, String bureau, boolean actif, boolean soiMeme) {

    public String roleLibelle() {
        return role.getLibelle();
    }

    /** Couleurs de badge du prototype : b-dis administrateur, b-enc conservateur, b-sou notaire. */
    public String badgeRole() {
        return switch (role) {
            case ADMINISTRATEUR -> "b-dis";
            case CONSERVATEUR -> "b-enc";
            case NOTAIRE -> "b-sou";
        };
    }
}
