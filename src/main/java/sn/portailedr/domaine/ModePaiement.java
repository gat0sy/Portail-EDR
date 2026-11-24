package sn.portailedr.domaine;

public enum ModePaiement {
    WAVE("Wave"),
    ORANGE_MONEY("Orange Money"),
    CARTE_BANCAIRE("Carte bancaire");

    /** Libelle affiche dans les pages, repris du prototype. */
    private final String libelle;

    ModePaiement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
