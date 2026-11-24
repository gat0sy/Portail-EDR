package sn.portailedr.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Creation d'un compte : en plus du profil, l'administrateur fixe un mot de passe provisoire. */
public class NouveauProfilFormulaire extends ProfilFormulaire {

    @NotNull(message = MotDePasseFormulaire.MESSAGE_LONGUEUR)
    @Size(min = MotDePasseFormulaire.LONGUEUR_MINIMALE, message = MotDePasseFormulaire.MESSAGE_LONGUEUR)
    private String motDePasse;

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }
}
