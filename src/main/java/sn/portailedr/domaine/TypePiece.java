package sn.portailedr.domaine;

public enum TypePiece {
    CNI_RECTO("Carte nationale d'identité, recto"),
    CNI_VERSO("Carte nationale d'identité, verso"),
    COPIE_TITRE_FONCIER("Copie du titre foncier");

    /** Libelle affiche dans les pages, repris du prototype. */
    private final String libelle;

    TypePiece(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
