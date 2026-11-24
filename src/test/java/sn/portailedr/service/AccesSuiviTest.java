package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Une verification reussie ouvre UNE reference, jusqu'a 30 minutes sans consultation. */
class AccesSuiviTest {

    private static final Duration INACTIVITE = Duration.ofMinutes(30);
    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void ouvreSeulementLaReferenceVerifiee() {
        AccesSuivi acces = new AccesSuivi();
        acces.ouvrir("EDR-2026-AAAAAA", T0);

        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plusSeconds(1), INACTIVITE)).isTrue();
        assertThat(acces.estOuvert("EDR-2026-BBBBBB", T0.plusSeconds(1), INACTIVITE)).isFalse();
    }

    @Test
    void trenteMinutesSansConsultationFermentLAcces() {
        AccesSuivi acces = new AccesSuivi();
        acces.ouvrir("EDR-2026-AAAAAA", T0);

        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plus(INACTIVITE).plusSeconds(1), INACTIVITE)).isFalse();
        // ferme pour de bon : revenir dans les temps ne rouvre rien
        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plusSeconds(10), INACTIVITE)).isFalse();
    }

    @Test
    void chaqueConsultationRepousseLaFermeture() {
        AccesSuivi acces = new AccesSuivi();
        acces.ouvrir("EDR-2026-AAAAAA", T0);

        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plus(Duration.ofMinutes(25)), INACTIVITE)).isTrue();
        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plus(Duration.ofMinutes(50)), INACTIVITE)).isTrue();
        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plus(Duration.ofMinutes(81)), INACTIVITE)).isFalse();
    }

    @Test
    void exactementTrenteMinutesEstEncoreOuvert() {
        AccesSuivi acces = new AccesSuivi();
        acces.ouvrir("EDR-2026-AAAAAA", T0);

        assertThat(acces.estOuvert("EDR-2026-AAAAAA", T0.plus(INACTIVITE), INACTIVITE)).isTrue();
    }
}
