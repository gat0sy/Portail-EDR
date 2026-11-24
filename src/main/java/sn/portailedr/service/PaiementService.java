package sn.portailedr.service;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.domaine.Paiement;

/**
 * Prestataire de paiement (Wave, Orange Money, carte bancaire) : systeme externe.
 * La seule implementation est simulee (PaiementServiceSimule) ; un vrai prestataire la
 * remplacerait sans changer le code metier (SuiviService).
 */
public interface PaiementService {

    /**
     * Enregistre le paiement (INITIE), le soumet au prestataire, puis le passe a CONFIRME ou ECHEC.
     * Appele dans la transaction du paiement : si le paiement n'est pas CONFIRME, l'appelant
     * annule tout, ligne du paiement comprise.
     */
    Paiement payer(Demande demande, int montant, ModePaiement mode);
}
