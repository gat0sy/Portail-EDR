package sn.portailedr.service;

import java.util.List;

/**
 * Le formulaire de demande est refuse. Toutes les erreurs sont donnees en une fois,
 * chacune avec le champ concerne, pour etre affichees sous les champs.
 */
public class DemandeInvalideException extends RuntimeException {

    public record ErreurChamp(String champ, String message) {
    }

    private final List<ErreurChamp> erreurs;

    public DemandeInvalideException(List<ErreurChamp> erreurs) {
        super("Demande invalide : " + erreurs);
        this.erreurs = List.copyOf(erreurs);
    }

    public List<ErreurChamp> getErreurs() {
        return erreurs;
    }
}
