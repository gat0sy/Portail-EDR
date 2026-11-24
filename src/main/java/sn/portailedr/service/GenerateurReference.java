package sn.portailedr.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * Code de suivi EDR-<annee>-<6 caracteres>, tire au hasard avec SecureRandom : il ne se devine pas
 * et ne suit aucun ordre. L'alphabet exclut 0, O, 1, I et L, faciles a confondre a la lecture.
 * Meme forme que la contrainte ck_demande_reference ; l'unicite est garantie par uq_demande_reference.
 */
@Component
public class GenerateurReference {

    /** 31 caracteres : 2 a 9, puis A a Z sans I, L ni O. */
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private static final int LONGUEUR = 6;
    private static final int ESSAIS_MAX = 10;
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private final SecureRandom hasard = new SecureRandom();
    private final Clock horloge;

    public GenerateurReference(Clock horloge) {
        this.horloge = horloge;
    }

    /**
     * @param dejaPrise vrai si la reference existe deja en base : on tire alors une autre reference.
     *                  Avec 31^6 (pres de 900 millions) possibilites par an, une collision est rare ;
     *                  dix collisions de suite signalent un defaut, pas un hasard.
     */
    public String nouvelle(Predicate<String> dejaPrise) {
        for (int essai = 0; essai < ESSAIS_MAX; essai++) {
            String reference = tirer();
            if (!dejaPrise.test(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Impossible de tirer une reference libre");
    }

    private String tirer() {
        StringBuilder reference = new StringBuilder("EDR-").append(Year.now(horloge.withZone(DAKAR))).append('-');
        for (int i = 0; i < LONGUEUR; i++) {
            reference.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
