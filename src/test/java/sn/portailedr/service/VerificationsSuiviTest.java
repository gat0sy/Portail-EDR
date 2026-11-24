package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import sn.portailedr.service.VerificationSms.Resultat;

/** Registre en memoire : un etat de verification PAR REFERENCE, partage par toutes les sessions. */
class VerificationsSuiviTest {

    private static final SmsProprietes SMS = new SmsProprietes(6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60), 3);
    private static final SuiviProprietes SUIVI =
            new SuiviProprietes(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofDays(15));
    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");

    private final VerificationsSuivi registre = new VerificationsSuivi(SMS, SUIVI);

    @Test
    void lamemeReferenceDonneLeMemeEtat() {
        registre.utiliser("EDR-2026-AAAAAA", T0, verification -> {
            verification.enregistrerEnvoi("123456", T0);
            return null;
        });

        assertThat(registre.utiliser("EDR-2026-AAAAAA", T0.plusSeconds(5), VerificationSms::getCode)).isEqualTo("123456");
        assertThat(registre.utiliser("EDR-2026-BBBBBB", T0.plusSeconds(5), VerificationSms::getCode)).isNull();
    }

    @Test
    void lesEssaisRestentAcquisPendantUneHeure() {
        registre.utiliser("EDR-2026-AAAAAA", T0, verification -> {
            verification.enregistrerEnvoi("123456", T0);
            verification.verifier("000000", T0);
            verification.verifier("000000", T0);
            return verification.verifier("000000", T0);
        });

        Resultat apres59Minutes = registre.utiliser("EDR-2026-AAAAAA", T0.plus(Duration.ofMinutes(59)),
                verification -> verification.verifier("123456", T0.plus(Duration.ofMinutes(59))));
        assertThat(apres59Minutes).isEqualTo(Resultat.TENTATIVES_EPUISEES);
    }

    @Test
    void apresUneHeureLaReferenceRepartDeZero() {
        registre.utiliser("EDR-2026-AAAAAA", T0, verification -> {
            verification.enregistrerEnvoi("123456", T0);
            return null;
        });

        Instant plusTard = T0.plus(Duration.ofHours(1)).plusSeconds(1);
        assertThat(registre.utiliser("EDR-2026-AAAAAA", plusTard, VerificationSms::envoisRestants)).isEqualTo(3);
        assertThat(registre.taille()).isEqualTo(1);
    }

    @Test
    void lesEntreesExpireesSontRetireesPourNePasRemplirLaMemoire() {
        for (int i = 0; i < 50; i++) {
            registre.utiliser("EDR-2026-AAAA" + (char) ('A' + i % 26) + (char) ('A' + i / 26), T0, v -> null);
        }
        assertThat(registre.taille()).isEqualTo(50);

        registre.utiliser("EDR-2026-ZZZZZZ", T0.plus(Duration.ofHours(2)), v -> null);

        assertThat(registre.taille()).isEqualTo(1);
    }

    @Test
    void oublierUneReferenceApresUnSucces() {
        registre.utiliser("EDR-2026-AAAAAA", T0, verification -> {
            verification.enregistrerEnvoi("123456", T0);
            return null;
        });

        registre.oublier("EDR-2026-AAAAAA");

        assertThat(registre.utiliser("EDR-2026-AAAAAA", T0.plusSeconds(1), VerificationSms::envoisRestants)).isEqualTo(3);
    }
}
