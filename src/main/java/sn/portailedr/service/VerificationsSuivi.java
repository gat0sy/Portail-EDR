package sn.portailedr.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * Etat de la verification SMS du suivi, PAR REFERENCE, partage par toutes les sessions.
 * Un attaquant qui ouvre des sessions neuves retrouve le meme etat : memes envois, memes essais.
 * Les limites sont celles du depot (portail.sms.*) ; elles sont comptees pendant
 * portail.suivi.fenetre-limite (1 heure), puis la reference repart de zero.
 *
 * Limites connues, acceptees pour ce projet :
 * <ul>
 *   <li>en memoire : un redemarrage remet tous les compteurs a zero ;</li>
 *   <li>une seule instance de l'application : deux instances auraient chacune leurs compteurs ;</li>
 *   <li>l'etat est commun au demandeur et a un attaquant : en epuisant les essais, un attaquant
 *       peut bloquer le suivi d'une reference pendant au plus une heure.</li>
 * </ul>
 * Les references inconnues ont aussi une entree (avec un code jamais envoye) : leur comportement
 * est le meme que celui d'une reference existante. Les entrees perimees sont retirees a chaque appel.
 */
@Component
public class VerificationsSuivi {

    private record Entree(VerificationSms verification, Instant debut) {
    }

    private final SmsProprietes sms;
    private final SuiviProprietes suivi;
    private final Map<String, Entree> entrees = new HashMap<>();

    public VerificationsSuivi(SmsProprietes sms, SuiviProprietes suivi) {
        this.sms = sms;
        this.suivi = suivi;
    }

    /**
     * Applique une action a l'etat de la reference (cree s'il n'existe pas), sous verrou :
     * deux requetes simultanees sur la meme reference ne comptent pas un essai en moins.
     */
    public synchronized <T> T utiliser(String reference, Instant maintenant, Function<VerificationSms, T> action) {
        purger(maintenant);
        Entree entree = entrees.computeIfAbsent(reference,
                cle -> new Entree(new VerificationSms(null, sms), maintenant));
        return action.apply(entree.verification());
    }

    /** Apres un code valide : la prochaine visite repart avec un nouveau code. */
    public synchronized void oublier(String reference) {
        entrees.remove(reference);
    }

    /** Nombre d'entrees gardees (pour les tests de purge). */
    public synchronized int taille() {
        return entrees.size();
    }

    private void purger(Instant maintenant) {
        entrees.values().removeIf(entree -> maintenant.isAfter(entree.debut().plus(suivi.fenetreLimite())));
    }
}
