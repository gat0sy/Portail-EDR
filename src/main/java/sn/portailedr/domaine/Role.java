package sn.portailedr.domaine;

public enum Role {
    NOTAIRE("Notaire"),
    CONSERVATEUR("Conservateur"),
    ADMINISTRATEUR("Administrateur");

    /** Libelle affiche dans les pages. */
    private final String libelle;

    Role(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
