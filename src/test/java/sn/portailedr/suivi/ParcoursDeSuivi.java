package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.portailedr.service.VerificationSms;
import sn.portailedr.service.VerificationsSuivi;

/** Les etapes du suivi par un visiteur : code de suivi, code SMS. */
public final class ParcoursDeSuivi {

    private final MockMvcTester mvc;
    private final VerificationsSuivi verifications;

    public ParcoursDeSuivi(MockMvcTester mvc, VerificationsSuivi verifications) {
        this.mvc = mvc;
        this.verifications = verifications;
    }

    public MvcTestResult codeDeSuivi(MockHttpSession session, String reference) {
        return mvc.post().uri("/suivi").with(csrf()).session(session).param("reference", reference).exchange();
    }

    public MvcTestResult codeSms(MockHttpSession session, String code) {
        return mvc.post().uri("/suivi/verification").with(csrf()).session(session).param("code", code).exchange();
    }

    /** Le code "envoye" pour cette reference : le test le lit dans le registre, a la place du SMS. */
    public String codeEnvoye(String reference) {
        return verifications.utiliser(reference, Instant.now(), VerificationSms::getCode);
    }

    /** Parcours complet : la session peut ensuite lire cette demande. */
    public void ouvrir(MockHttpSession session, String reference) {
        assertThat(codeDeSuivi(session, reference)).hasRedirectedUrl("/suivi/verification");
        assertThat(codeSms(session, codeEnvoye(reference))).hasRedirectedUrl("/suivi/" + reference);
    }

    public MvcTestResult payer(MockHttpSession session, String reference, String mode) {
        return mvc.post().uri("/suivi/" + reference + "/paiement").with(csrf()).session(session)
                .param("modePaiement", mode).exchange();
    }

    /** Un code different du bon. */
    public static String autreCode(String bon) {
        return "000000".equals(bon) ? "111111" : "000000";
    }
}
