package sn.portailedr.service;

import jakarta.validation.constraints.NotBlank;

/** Formulaire de creation ou de modification d'un bureau. */
public class BureauFormulaire {

    @NotBlank(message = "Le nom du bureau est obligatoire.")
    private String nom;

    @NotBlank(message = "L'adresse est obligatoire.")
    private String adresse;

    @NotBlank(message = "Le téléphone est obligatoire.")
    private String telephone;

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }
}
