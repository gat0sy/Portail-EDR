package sn.portailedr.service;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * References ouvertes dans la session d'un visiteur, apres un code SMS valide.
 * Une verification n'ouvre que SA reference. Elle se ferme apres une periode d'inactivite
 * (30 minutes par defaut) ou a la fin de la session. L'heure est passee en parametre.
 */
public class AccesSuivi implements Serializable {

    /** Nom de l'attribut de session. */
    public static final String ATTRIBUT = "accesSuivi";

    /** Reference vers l'heure de sa derniere consultation. */
    private final Map<String, Instant> ouvertes = new HashMap<>();

    /** Ouvre la reference (ou fixe sa derniere consultation, ce que font aussi les tests). */
    public void ouvrir(String reference, Instant maintenant) {
        ouvertes.put(reference, maintenant);
    }

    /** Vrai si la reference est ouverte ; la consultation repousse alors la fermeture. */
    public boolean estOuvert(String reference, Instant maintenant, Duration inactivite) {
        Instant derniere = ouvertes.get(reference);
        if (derniere == null) {
            return false;
        }
        if (maintenant.isAfter(derniere.plus(inactivite))) {
            ouvertes.remove(reference);
            return false;
        }
        ouvertes.put(reference, maintenant);
        return true;
    }
}
