package sn.portailedr.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Formulaire d'ajout d'un livre foncier a un bureau. */
public class LivreFormulaire {

    /** 1 a 4 lettres, espaces autour toleres ; le service le met en majuscules. */
    @Pattern(regexp = "\\s*[A-Za-z]{1,4}\\s*", message = "Le code doit comporter 1 à 4 lettres.")
    private String code = "";

    @NotBlank(message = "Le nom du livre est obligatoire.")
    private String nom;

    private Long idBureau;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Long getIdBureau() {
        return idBureau;
    }

    public void setIdBureau(Long idBureau) {
        this.idBureau = idBureau;
    }
}
