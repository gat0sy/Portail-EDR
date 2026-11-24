package sn.portailedr.service;

import sn.portailedr.domaine.Role;

/**
 * Donnees affichees par la page Mon profil. Les pages ne recoivent jamais l'entite Utilisateur.
 *
 * @param bureau nom du bureau d'affectation, ou null si l'utilisateur n'en a pas
 */
public record ProfilVue(String nom, String email, String telephone, Role role, String bureau) {

    public String roleLibelle() {
        return role.getLibelle();
    }

    /** Libelle du prototype : un notaire est "rattache", un conservateur ou un administrateur est "affecte". */
    public String libelleBureau() {
        return role == Role.NOTAIRE ? "Bureau de rattachement" : "Bureau d'affectation";
    }
}
