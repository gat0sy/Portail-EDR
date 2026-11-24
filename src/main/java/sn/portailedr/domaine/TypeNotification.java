package sn.portailedr.domaine;

public enum TypeNotification {
    CODE_VERIFICATION("Code de vérification"),
    REJET("Demande rejetée"),
    DISPONIBILITE("Résultat disponible"),
    PAIEMENT("Paiement confirmé"),
    EXPIRATION("Demande expirée");

    /** Libelle de l'historique des messages (page de suivi, rubrique du prototype). */
    private final String libelle;

    TypeNotification(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
