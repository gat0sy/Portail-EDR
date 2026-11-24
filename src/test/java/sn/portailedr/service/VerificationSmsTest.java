package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sn.portailedr.service.VerificationSms.Resultat;

/**
 * Regles du code SMS, sans Spring ni base : l'heure est passee en parametre,
 * ce qui permet de tester l'expiration et le delai de renvoi sans attendre.
 * Parametres du cahier des charges : 6 chiffres, 5 min, 3 essais, renvoi apres 60 s, 3 envois.
 */
class VerificationSmsTest {

    private static final SmsProprietes REGLES =
            new SmsProprietes(6, Duration.ofMinutes(5), 3, Duration.ofSeconds(60), 3);
    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");

    private VerificationSms verification;

    @BeforeEach
    void premierEnvoi() {
        verification = new VerificationSms("+221 77 000 00 00", REGLES);
        verification.enregistrerEnvoi("123456", T0);
    }

    // ---------------------------------------------------------------- expiration (5 minutes)

    @Test
    void leBonCodeDansLesCinqMinutesEstValide() {
        assertThat(verification.verifier("123456", T0.plus(Duration.ofMinutes(5)))).isEqualTo(Resultat.VALIDE);
    }

    @Test
    void leBonCodeApresCinqMinutesAExpire() {
        assertThat(verification.verifier("123456", T0.plus(Duration.ofMinutes(5)).plusSeconds(1)))
                .isEqualTo(Resultat.EXPIRE);
    }

    @Test
    void unCodeValideNeSertQuUneFois() {
        assertThat(verification.verifier("123456", T0.plusSeconds(10))).isEqualTo(Resultat.VALIDE);
        assertThat(verification.verifier("123456", T0.plusSeconds(11))).isNotEqualTo(Resultat.VALIDE);
    }

    // ---------------------------------------------------------------- 3 essais

    @Test
    void troisEssaisFauxBloquentLeCode() {
        assertThat(verification.verifier("000000", T0)).isEqualTo(Resultat.INCORRECT);
        assertThat(verification.tentativesRestantes()).isEqualTo(2);
        assertThat(verification.verifier("000000", T0)).isEqualTo(Resultat.INCORRECT);
        assertThat(verification.tentativesRestantes()).isEqualTo(1);
        assertThat(verification.verifier("000000", T0)).isEqualTo(Resultat.TENTATIVES_EPUISEES);
        assertThat(verification.tentativesRestantes()).isZero();

        // meme le bon code est maintenant refuse
        assertThat(verification.verifier("123456", T0)).isEqualTo(Resultat.TENTATIVES_EPUISEES);
    }

    @Test
    void unNouveauCodeRemetLesEssaisAZeroEtInvalideLAncien() {
        verification.verifier("000000", T0);
        verification.verifier("000000", T0);
        verification.verifier("000000", T0);

        verification.enregistrerEnvoi("654321", T0.plusSeconds(60));

        assertThat(verification.tentativesRestantes()).isEqualTo(3);
        assertThat(verification.verifier("123456", T0.plusSeconds(61))).isEqualTo(Resultat.INCORRECT);
        assertThat(verification.verifier("654321", T0.plusSeconds(62))).isEqualTo(Resultat.VALIDE);
    }

    @Test
    void unNouveauCodeRepartPourCinqMinutes() {
        verification.enregistrerEnvoi("654321", T0.plus(Duration.ofMinutes(4)));

        assertThat(verification.verifier("654321", T0.plus(Duration.ofMinutes(8)))).isEqualTo(Resultat.VALIDE);
    }

    // ---------------------------------------------------------------- renvoi apres 60 secondes

    @Test
    void leRenvoiNEstPossibleQuApresSoixanteSecondes() {
        assertThat(verification.secondesAvantRenvoi(T0)).isEqualTo(60);
        assertThat(verification.secondesAvantRenvoi(T0.plusSeconds(59))).isEqualTo(1);
        assertThat(verification.secondesAvantRenvoi(T0.plusSeconds(60))).isZero();
    }

    @Test
    void unRenvoiTropTotEstRefuse() {
        assertThatIllegalStateException().isThrownBy(() -> verification.enregistrerEnvoi("654321", T0.plusSeconds(59)));
        assertThat(verification.verifier("123456", T0.plusSeconds(59))).isEqualTo(Resultat.VALIDE);
    }

    // ---------------------------------------------------------------- 3 envois au maximum

    @Test
    void troisEnvoisAuMaximum() {
        assertThat(verification.envoisRestants()).isEqualTo(2);
        verification.enregistrerEnvoi("222222", T0.plusSeconds(60));
        verification.enregistrerEnvoi("333333", T0.plusSeconds(120));
        assertThat(verification.envoisRestants()).isZero();

        assertThatIllegalStateException().isThrownBy(() -> verification.enregistrerEnvoi("444444", T0.plusSeconds(600)));
        assertThat(verification.verifier("333333", T0.plusSeconds(130))).isEqualTo(Resultat.VALIDE);
    }

    @Test
    void laVerificationEstEpuiseeSansEnvoiNiEssaiRestant() {
        verification.enregistrerEnvoi("222222", T0.plusSeconds(60));
        verification.enregistrerEnvoi("333333", T0.plusSeconds(120));
        assertThat(verification.estEpuisee(T0.plusSeconds(121))).isFalse();

        verification.verifier("000000", T0.plusSeconds(121));
        verification.verifier("000000", T0.plusSeconds(122));
        verification.verifier("000000", T0.plusSeconds(123));

        assertThat(verification.estEpuisee(T0.plusSeconds(124))).isTrue();
    }

    @Test
    void laVerificationEstEpuiseeQuandLeDernierCodeExpire() {
        verification.enregistrerEnvoi("222222", T0.plusSeconds(60));
        verification.enregistrerEnvoi("333333", T0.plusSeconds(120));

        assertThat(verification.estEpuisee(T0.plusSeconds(120).plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(verification.estEpuisee(T0.plusSeconds(121).plus(Duration.ofMinutes(5)))).isTrue();
    }

    // ---------------------------------------------------------------- saisie

    @Test
    void lesEspacesAutourDuCodeSontIgnores() {
        assertThat(verification.verifier(" 123456 ", T0)).isEqualTo(Resultat.VALIDE);
    }

    @Test
    void uneSaisieVideCompteCommeUnEssai() {
        assertThat(verification.verifier(null, T0)).isEqualTo(Resultat.INCORRECT);
        assertThat(verification.verifier("", T0)).isEqualTo(Resultat.INCORRECT);
        assertThat(verification.tentativesRestantes()).isEqualTo(1);
    }
}
