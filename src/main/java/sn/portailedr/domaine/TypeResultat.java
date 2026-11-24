package sn.portailedr.domaine;

public enum TypeResultat {
    ETAT_DROITS_REELS("État des droits réels"),
    REQUISITION_NEGATIVE("Réquisition négative");

    private final String libelle;

    TypeResultat(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
