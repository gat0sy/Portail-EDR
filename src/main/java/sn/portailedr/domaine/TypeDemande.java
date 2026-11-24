package sn.portailedr.domaine;

public enum TypeDemande {
    BIEN("Sur un bien"),
    PERSONNE("Sur une personne");

    /** Libelle affiche dans les pages (rubrique "Objet"). */
    private final String libelle;

    TypeDemande(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
