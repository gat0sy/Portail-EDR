package sn.portailedr.web;

import java.io.Serializable;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import sn.portailedr.service.DemandeEnAttente;
import sn.portailedr.service.VerificationSms;

/**
 * Ce que la session d'un particulier garde entre le formulaire et le code SMS :
 * la demande verifiee (pas encore en base) et l'etat de la verification.
 *
 * Nettoyage des fichiers abandonnes : quand cet objet quitte la session, ses fichiers
 * temporaires sont supprimes (HttpSessionBindingListener, mecanisme standard des servlets).
 * Cela arrive quand la session expire ou se ferme (deconnexion), quand un nouveau formulaire
 * remplace la demande en attente, ou quand la verification echoue definitivement. Apres un code
 * valide, les fichiers ont deja ete deplaces : il ne reste rien a supprimer.
 */
public record SoumissionEnCours(DemandeEnAttente demande, VerificationSms verification)
        implements Serializable, HttpSessionBindingListener {

    /** Nom de l'attribut de session. */
    public static final String ATTRIBUT = "soumissionEnCours";

    @Override
    public void valueUnbound(HttpSessionBindingEvent evenement) {
        demande.supprimerFichiers();
    }
}
