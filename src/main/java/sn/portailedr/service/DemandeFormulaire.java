package sn.portailedr.service;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.portailedr.domaine.TypeDemande;

/**
 * Formulaire "Nouvelle demande" (m1.html). Les champs obligatoires selon l'objet
 * (bien ou personne) ou selon le demandeur (particulier ou notaire) sont verifies par
 * SoumissionService, qui renvoie toutes les erreurs d'un coup.
 * Les champs du declarant et la CNI ne sont lus que pour un particulier.
 */
public class DemandeFormulaire {

    @NotNull(message = "Choisissez l'objet de la demande.")
    private TypeDemande type;

    @Size(max = 40, message = "40 caractères au maximum.")
    private String numeroTitreFoncier;

    @Size(max = 200, message = "200 caractères au maximum.")
    private String personneConcernee;

    private Long idBureau;

    @Size(max = 200, message = "200 caractères au maximum.")
    private String declarantNom;

    @Size(max = 30, message = "30 caractères au maximum.")
    private String declarantTelephone;

    @Size(max = 40, message = "40 caractères au maximum.")
    private String declarantCni;

    private MultipartFile cniRecto;
    private MultipartFile cniVerso;
    private MultipartFile copieTitreFoncier;

    /** Formulaire vide, "Sur un bien" coche comme dans le prototype. */
    public static DemandeFormulaire nouveau() {
        DemandeFormulaire formulaire = new DemandeFormulaire();
        formulaire.setType(TypeDemande.BIEN);
        return formulaire;
    }

    public TypeDemande getType() {
        return type;
    }

    public void setType(TypeDemande type) {
        this.type = type;
    }

    public String getNumeroTitreFoncier() {
        return numeroTitreFoncier;
    }

    public void setNumeroTitreFoncier(String numeroTitreFoncier) {
        this.numeroTitreFoncier = numeroTitreFoncier;
    }

    public String getPersonneConcernee() {
        return personneConcernee;
    }

    public void setPersonneConcernee(String personneConcernee) {
        this.personneConcernee = personneConcernee;
    }

    public Long getIdBureau() {
        return idBureau;
    }

    public void setIdBureau(Long idBureau) {
        this.idBureau = idBureau;
    }

    public String getDeclarantNom() {
        return declarantNom;
    }

    public void setDeclarantNom(String declarantNom) {
        this.declarantNom = declarantNom;
    }

    public String getDeclarantTelephone() {
        return declarantTelephone;
    }

    public void setDeclarantTelephone(String declarantTelephone) {
        this.declarantTelephone = declarantTelephone;
    }

    public String getDeclarantCni() {
        return declarantCni;
    }

    public void setDeclarantCni(String declarantCni) {
        this.declarantCni = declarantCni;
    }

    public MultipartFile getCniRecto() {
        return cniRecto;
    }

    public void setCniRecto(MultipartFile cniRecto) {
        this.cniRecto = cniRecto;
    }

    public MultipartFile getCniVerso() {
        return cniVerso;
    }

    public void setCniVerso(MultipartFile cniVerso) {
        this.cniVerso = cniVerso;
    }

    public MultipartFile getCopieTitreFoncier() {
        return copieTitreFoncier;
    }

    public void setCopieTitreFoncier(MultipartFile copieTitreFoncier) {
        this.copieTitreFoncier = copieTitreFoncier;
    }
}
