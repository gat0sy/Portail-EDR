package sn.portailedr.service;

/**
 * La demande (ou la piece) n'existe pas, ou n'appartient pas au bureau du conservateur.
 * Les deux cas donnent la meme reponse (404) : un conservateur ne peut pas savoir si une
 * demande d'un autre bureau existe.
 */
public class DemandeIntrouvableException extends RuntimeException {

    public DemandeIntrouvableException() {
        super("Demande introuvable pour ce bureau");
    }
}
