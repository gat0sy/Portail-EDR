package sn.portailedr.service;

/**
 * Une regle metier refuse l'action. Le message, en francais, est affiche tel quel.
 * C'est une RuntimeException : la transaction du service est annulee, journal compris.
 *
 * @param champ champ du formulaire concerne, ou null si le refus porte sur toute l'action
 */
public class RegleMetierException extends RuntimeException {

    private final String champ;

    public RegleMetierException(String message) {
        this(message, null);
    }

    public RegleMetierException(String message, String champ) {
        super(message);
        this.champ = champ;
    }

    public String getChamp() {
        return champ;
    }
}
