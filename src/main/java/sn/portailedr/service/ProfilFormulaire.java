package sn.portailedr.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import sn.portailedr.domaine.Role;

/** Formulaire de modification d'un compte (et base du formulaire de creation). */
public class ProfilFormulaire {

    @NotBlank(message = "Le nom est obligatoire.")
    private String nom;

    @NotBlank(message = "L'adresse e-mail est obligatoire.")
    @Email(message = "Adresse e-mail invalide.")
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire.")
    private String telephone;

    @NotNull(message = "Choisissez un rôle.")
    private Role role;

    /** Obligatoire pour un conservateur : verifie par le service. */
    private Long idBureau;

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getIdBureau() {
        return idBureau;
    }

    public void setIdBureau(Long idBureau) {
        this.idBureau = idBureau;
    }
}
