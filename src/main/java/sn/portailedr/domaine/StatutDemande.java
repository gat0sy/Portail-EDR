package sn.portailedr.domaine;

/** Statuts du cycle de vie (figure 2.3). Libelle et classe CSS du badge viennent du prototype. */
public enum StatutDemande {
    SOUMISE("Soumise", "b-sou"),
    EN_COURS("En cours", "b-enc"),
    REJETEE("Rejetée", "b-rej"),
    DISPONIBLE("Disponible", "b-dis"),
    PAYEE("Payée", "b-pay"),
    EXPIREE("Expirée", "b-exp");

    private final String libelle;
    private final String badge;

    StatutDemande(String libelle, String badge) {
        this.libelle = libelle;
        this.badge = badge;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Classe CSS du badge de statut (base.css). */
    public String getBadge() {
        return badge;
    }
}
