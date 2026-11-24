package sn.portailedr.service;

import sn.portailedr.domaine.Demande;

/**
 * Envoi des messages au demandeur (SMS pour un particulier, courriel pour un notaire).
 * Systeme externe : l'application n'en connait que cette interface. La seule implementation
 * est simulee (NotificationServiceSimule) ; un vrai fournisseur la remplacerait sans toucher
 * au code metier. Les messages de rejet, disponibilite et expiration arrivent avec les tranches 7 et 8.
 */
public interface NotificationService {

    /**
     * Code de verification du telephone d'un particulier qui depose une demande. Aucune ligne
     * NOTIFICATION n'est enregistree : la demande n'existe pas encore et notification.id_demande
     * est obligatoire.
     */
    void envoyerCodeVerification(String telephone, String code);

    /** Code de verification pour le suivi : SMS au numero declare, et ligne NOTIFICATION de la demande. */
    void envoyerCodeSuivi(Demande demande, String code);

    /** Confirmation du paiement : ligne NOTIFICATION de la demande. */
    void notifierPaiement(Demande demande);
}
