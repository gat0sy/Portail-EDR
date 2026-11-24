package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.security.CompteUtilisateur;
import sn.portailedr.service.AccesSuivi;

/**
 * Qui lit le suivi d'une demande, la paie et telecharge son resultat :
 * le visiteur qui a passe le code SMS pour CETTE reference dans sa session (30 min d'inactivite
 * au plus), ou le notaire auquel la demande appartient. Les autres : retour a /suivi,
 * ou 404 pour un notaire qui n'en est pas proprietaire. Conservateur et administrateur : 403.
 */
class SuiviAuthorizationTest extends SuiviTestBase {

    private static final String SUIVI = "/suivi";

    private void sansAcces(MockHttpSession session, Demande demande) {
        String reference = demande.getReference();
        assertThat(mvc.get().uri("/suivi/" + reference).session(session)).hasRedirectedUrl(SUIVI);
        assertThat(parcours.payer(session, reference, "WAVE")).hasRedirectedUrl(SUIVI);
        assertThat(mvc.get().uri("/suivi/" + reference + "/resultat").session(session)).hasRedirectedUrl(SUIVI);
    }

    // ---------------------------------------------------------------- sans verification

    @Test
    void sansVerificationNiPageNiPaiementNiResultat() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());

        sansAcces(new MockHttpSession(), demande);
    }

    @Test
    void unPaiementSansVerificationNePaieRien() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));

        parcours.payer(new MockHttpSession(), demande.getReference(), "WAVE");

        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
        assertThat(compter("SELECT count(*) FROM paiement WHERE id_demande = ?", demande.getId())).isZero();
    }

    @Test
    void avoirDemandeUnCodeSansLeSaisirNOuvreRien() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = new MockHttpSession();

        parcours.codeDeSuivi(session, demande.getReference());

        sansAcces(session, demande);
    }

    // ---------------------------------------------------------------- A n'ouvre pas B

    @Test
    void laVerificationDeANOuvrePasB() {
        Demande a = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());
        Demande b = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());
        MockHttpSession session = new MockHttpSession();

        parcours.ouvrir(session, a.getReference());

        assertThat(mvc.get().uri("/suivi/" + a.getReference()).session(session)).hasStatusOk();
        sansAcces(session, b);
    }

    @Test
    void laVerificationNeSuitPasLaSessionDansUneAutre() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        parcours.ouvrir(new MockHttpSession(), demande.getReference());

        sansAcces(new MockHttpSession(), demande);
    }

    // ---------------------------------------------------------------- 30 minutes d'inactivite

    @Test
    void trenteMinutesDInactiviteFermentLeSuivi() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());

        // derniere activite il y a 31 minutes
        AccesSuivi acces = (AccesSuivi) session.getAttribute(AccesSuivi.ATTRIBUT);
        acces.ouvrir(demande.getReference(), Instant.now().minus(Duration.ofMinutes(31)));

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).session(session)).hasRedirectedUrl(SUIVI);
        // et l'acces ne revient pas tout seul
        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).session(session)).hasRedirectedUrl(SUIVI);
    }

    @Test
    void chaqueConsultationRepousseLaFinDuSuivi() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());
        AccesSuivi acces = (AccesSuivi) session.getAttribute(AccesSuivi.ATTRIBUT);
        acces.ouvrir(demande.getReference(), Instant.now().minus(Duration.ofMinutes(29)));

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).session(session)).hasStatusOk();
    }

    // ---------------------------------------------------------------- notaires

    @Test
    void leNotaireProprietaireLitSansCodeSms() {
        Utilisateur compte = comptes.notaire();
        Demande demande = donnees.payee(donnees.notaire(compte, StatutDemande.SOUMISE), Instant.now());
        CompteUtilisateur notaire = new CompteUtilisateur(compte);

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).with(user(notaire))).hasStatusOk();
        assertThat(mvc.get().uri("/suivi/" + demande.getReference() + "/resultat").with(user(notaire))).hasStatusOk();
    }

    @Test
    void unAutreNotaireRecoitUn404() {
        Demande demande = donnees.disponible(donnees.notaire(comptes.notaire(), StatutDemande.SOUMISE));
        CompteUtilisateur autre = autreNotaire();

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).with(user(autre))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/suivi/" + demande.getReference() + "/paiement").with(user(autre)).with(csrf())
                .param("modePaiement", "WAVE")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/suivi/" + demande.getReference() + "/resultat").with(user(autre)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
    }

    @Test
    void unNotaireNeLitPasLaDemandeDUnParticulier() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).with(user(new CompteUtilisateur(comptes.notaire()))))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unNotaireSurUneReferenceInconnueRecoitUn404() {
        assertThat(mvc.get().uri("/suivi/EDR-2026-ZZZZZZ").with(user(new CompteUtilisateur(comptes.notaire()))))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- autres roles

    @ParameterizedTest
    @ValueSource(strings = {"CONSERVATEUR", "ADMINISTRATEUR"})
    void conservateurEtAdministrateurNOntPasAccesAuSuivi(Role role) {
        CompteUtilisateur agent = new CompteUtilisateur(
                role == Role.CONSERVATEUR ? comptes.conservateur() : comptes.administrateur());
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));

        assertThat(mvc.get().uri("/suivi").with(user(agent))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).with(user(agent))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/suivi/" + demande.getReference() + "/paiement").with(user(agent)).with(csrf())
                .param("modePaiement", "WAVE")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void visiteurEtNotaireOuvrentLaSaisieDuCode() {
        assertThat(mvc.get().uri("/suivi")).hasStatusOk();
        assertThat(mvc.get().uri("/suivi").with(user(new CompteUtilisateur(comptes.notaire())))).hasStatusOk();
    }
}
