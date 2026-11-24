package sn.portailedr.service;

/**
 * Le code SMS est refuse (faux, expire, trop d'essais) ou le renvoi n'est pas permis.
 *
 * @param definitive vrai quand plus aucun essai ni envoi n'est possible : la demande en attente
 *                   est abandonnee et le particulier doit la recommencer
 */
public class VerificationRefuseeException extends RegleMetierException {

    private final boolean definitive;

    public VerificationRefuseeException(String message, boolean definitive) {
        super(message);
        this.definitive = definitive;
    }

    public boolean isDefinitive() {
        return definitive;
    }
}
