package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Code de suivi EDR-<annee>-<6 caracteres> : aleatoire, alphabet sans caracteres ambigus, unique. */
class GenerateurReferenceTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);

    private final GenerateurReference generateur = new GenerateurReference(HORLOGE);

    @Test
    void formatEtAlphabet() {
        for (int i = 0; i < 2000; i++) {
            String reference = generateur.nouvelle(dejaPrise -> false);
            // meme expression que la contrainte ck_demande_reference
            assertThat(reference).matches("EDR-2026-[2-9A-HJKMNP-Z]{6}");
            assertThat(reference.substring(9)).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void lAlphabetCompteTrenteEtUnCaracteres() {
        assertThat(GenerateurReference.ALPHABET).hasSize(31).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void lesReferencesNeSeSuiventPas() {
        Set<String> tirees = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tirees.add(generateur.nouvelle(dejaPrise -> false));
        }
        // 31^6 = 887 503 681 possibilites : 1000 tirages identiques seraient le signe d'un defaut
        assertThat(tirees).hasSizeGreaterThan(995);
    }

    @Test
    void uneCollisionProvoqueUnNouveauTirage() {
        List<String> proposees = new ArrayList<>();

        String reference = generateur.nouvelle(candidate -> {
            proposees.add(candidate);
            return proposees.size() == 1; // la premiere est "deja prise"
        });

        assertThat(proposees).hasSize(2);
        assertThat(reference).isEqualTo(proposees.get(1));
    }

    @Test
    void desCollisionsSansFinSontUneErreur() {
        assertThatIllegalStateException().isThrownBy(() -> generateur.nouvelle(candidate -> true));
    }
}
