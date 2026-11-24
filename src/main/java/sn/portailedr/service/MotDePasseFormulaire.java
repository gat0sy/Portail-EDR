package sn.portailedr.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reinitialisation du mot de passe par l'administrateur : il fixe un mot de passe provisoire. */
public class MotDePasseFormulaire {

    /** Choix du projet (pas une regle de la DGID), a presenter comme tel dans le memoire. */
    static final int LONGUEUR_MINIMALE = 8;
    static final String MESSAGE_LONGUEUR = "Le mot de passe provisoire doit comporter au moins 8 caractères.";

    @NotNull(message = MESSAGE_LONGUEUR)
    @Size(min = LONGUEUR_MINIMALE, message = MESSAGE_LONGUEUR)
    private String nouveauMotDePasse;

    public String getNouveauMotDePasse() {
        return nouveauMotDePasse;
    }

    public void setNouveauMotDePasse(String nouveauMotDePasse) {
        this.nouveauMotDePasse = nouveauMotDePasse;
    }
}
