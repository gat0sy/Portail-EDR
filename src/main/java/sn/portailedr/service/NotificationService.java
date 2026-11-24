package sn.portailedr.service;

/**
 * Envoi des messages au demandeur (SMS pour un particulier, courriel pour un notaire).
 * Systeme externe : l'application n'en connait que cette interface. La seule implementation
 * est simulee (NotificationServiceSimule) ; un vrai fournisseur la remplacerait sans toucher
 * au code metier. Les autres messages (rejet, disponibilite...) arrivent dans les tranches suivantes.
 */
public interface NotificationService {

    /**
     * Code de verification du telephone d'un particulier. Aucune ligne NOTIFICATION n'est
     * enregistree : la demande n'existe pas encore et notification.id_demande est obligatoire.
     */
    void envoyerCodeVerification(String telephone, String code);
}
