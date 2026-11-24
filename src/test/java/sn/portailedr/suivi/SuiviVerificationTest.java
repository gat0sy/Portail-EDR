package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static sn.portailedr.suivi.ParcoursDeSuivi.autreCode;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;

/**
 * Saisie du code de suivi puis du code SMS. La reponse ne dit jamais si une reference existe,
 * et les essais sont comptes PAR REFERENCE : une nouvelle session ne redonne pas d'essais.
 */
class SuiviVerificationTest extends SuiviTestBase {

    private static final String VERIFICATION = "/suivi/verification";
    private static final String MESSAGE_UNIFORME = "Si ce code existe, un SMS a été envoyé au numéro déclaré.";

    /** Reference bien formee, absente de la base. */
    private String referenceInconnue() {
        return references.nouvelle(demandes::existsByReference);
    }

    // ---------------------------------------------------------------- reponse uniforme

    @Test
    void memeReponsePourUneReferenceConnueEtUneInconnue() throws Exception {
        String connue = donnees.particulier(StatutDemande.SOUMISE).getReference();
        String inconnue = referenceInconnue();
        MockHttpSession sessionConnue = new MockHttpSession();
        MockHttpSession sessionInconnue = new MockHttpSession();

        assertThat(parcours.codeDeSuivi(sessionConnue, connue)).hasRedirectedUrl(VERIFICATION).flash()
                .containsEntry("message", MESSAGE_UNIFORME);
        assertThat(parcours.codeDeSuivi(sessionInconnue, inconnue)).hasRedirectedUrl(VERIFICATION).flash()
                .containsEntry("message", MESSAGE_UNIFORME);

        String pageConnue = sansJeton(mvc.get().uri(VERIFICATION).session(sessionConnue).exchange().getResponse()
                .getContentAsString().replace(connue, "REF"));
        String pageInconnue = sansJeton(mvc.get().uri(VERIFICATION).session(sessionInconnue).exchange().getResponse()
                .getContentAsString().replace(inconnue, "REF"));
        assertThat(pageConnue).isEqualTo(pageInconnue).doesNotContain("00 00");
    }

    @Test
    void memesMessagesDErreurPourUneReferenceConnueEtUneInconnue() {
        String connue = donnees.particulier(StatutDemande.SOUMISE).getReference();
        String inconnue = referenceInconnue();
        MockHttpSession sessionConnue = new MockHttpSession();
        MockHttpSession sessionInconnue = new MockHttpSession();
        parcours.codeDeSuivi(sessionConnue, connue);
        parcours.codeDeSuivi(sessionInconnue, inconnue);

        for (int essai = 0; essai < 4; essai++) {
            Object erreurConnue = parcours.codeSms(sessionConnue, autreCode(parcours.codeEnvoye(connue)))
                    .getMvcResult().getFlashMap().get("erreur");
            Object erreurInconnue = parcours.codeSms(sessionInconnue, autreCode(parcours.codeEnvoye(inconnue)))
                    .getMvcResult().getFlashMap().get("erreur");
            assertThat(erreurConnue).isNotNull().isEqualTo(erreurInconnue);
        }
        Object renvoiConnue = mvc.post().uri("/suivi/verification/renvoi").with(csrf()).session(sessionConnue)
                .exchange().getMvcResult().getFlashMap().get("erreur");
        Object renvoiInconnue = mvc.post().uri("/suivi/verification/renvoi").with(csrf()).session(sessionInconnue)
                .exchange().getMvcResult().getFlashMap().get("erreur");
        // le nombre de secondes restantes peut differer d'une seconde entre les deux appels
        assertThat(renvoiConnue).isNotNull();
        assertThat(renvoiConnue.toString().replaceAll("\\d+", "N"))
                .isEqualTo(renvoiInconnue.toString().replaceAll("\\d+", "N"));
    }

    /** Le jeton CSRF est propre a chaque session : on le retire avant de comparer deux pages. */
    private static String sansJeton(String page) {
        return page.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "");
    }

    @Test
    void uneReferenceMalFormeeEstRefuseeSurLeFormulaire() {
        MvcTestResult resultat = parcours.codeDeSuivi(new MockHttpSession(), "EDR-26-ABC");

        assertThat(resultat).hasStatusOk().hasViewName("suivi-code");
        assertThat(resultat).bodyText().contains("Code de suivi invalide. Format attendu : EDR-2026-K7P4QX");
    }

    @Test
    void laReferenceEstLueSansTenirCompteDeLaCasseNiDesEspaces() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = new MockHttpSession();

        parcours.codeDeSuivi(session, "  " + demande.getReference().toLowerCase() + " ");

        assertThat(parcours.codeSms(session, parcours.codeEnvoye(demande.getReference())))
                .hasRedirectedUrl("/suivi/" + demande.getReference());
    }

    // ---------------------------------------------------------------- notification et journal

    @Test
    void unCodeEnvoyeEstUneNotificationDeLaDemande() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);

        parcours.codeDeSuivi(new MockHttpSession(), demande.getReference());

        assertThat(notifications(demande)).containsExactly("CODE_VERIFICATION");
    }

    @Test
    void uneReferenceInconnueNEcritRien() {
        long notificationsAvant = compter("SELECT count(*) FROM notification");
        long journalAvant = compter("SELECT count(*) FROM journal");

        parcours.codeDeSuivi(new MockHttpSession(), referenceInconnue());

        assertThat(compter("SELECT count(*) FROM notification")).isEqualTo(notificationsAvant);
        assertThat(compter("SELECT count(*) FROM journal")).isEqualTo(journalAvant);
    }

    @Test
    void leBonCodeOuvreLaDemandeEtEstJournalise() {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);
        MockHttpSession session = new MockHttpSession();

        parcours.ouvrir(session, demande.getReference());

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).session(session)).hasStatusOk();
        assertThat(journal(demande)).containsExactly("Vérification du numéro de téléphone pour le suivi");
    }

    @Test
    void uneNouvelleDemandeDeCodeAvantSoixanteSecondesNEnvoiePasDeSms() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        parcours.codeDeSuivi(new MockHttpSession(), demande.getReference());
        String code = parcours.codeEnvoye(demande.getReference());

        assertThat(parcours.codeDeSuivi(new MockHttpSession(), demande.getReference())).flash()
                .containsEntry("message", MESSAGE_UNIFORME);

        assertThat(notifications(demande)).hasSize(1);
        assertThat(parcours.codeEnvoye(demande.getReference())).isEqualTo(code);
    }

    @Test
    void leRenvoiImmediatEstRefuse() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        MockHttpSession session = new MockHttpSession();
        parcours.codeDeSuivi(session, demande.getReference());

        assertThat((String) mvc.post().uri("/suivi/verification/renvoi").with(csrf()).session(session).exchange()
                .getMvcResult().getFlashMap().get("erreur")).startsWith("Vous pourrez demander un nouveau code dans");
        assertThat(notifications(demande)).hasSize(1);
    }

    @Test
    void laPageDeVerificationSansCodeDeSuiviRenvoieALaSaisie() {
        assertThat(mvc.get().uri(VERIFICATION)).hasRedirectedUrl("/suivi");
        assertThat(parcours.codeSms(new MockHttpSession(), "123456")).hasRedirectedUrl("/suivi");
    }

    // ---------------------------------------------------------------- limite par reference

    @Test
    void lesEssaisSontComptesParReferenceEtPasParSession() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        String reference = demande.getReference();
        MockHttpSession premiere = new MockHttpSession();
        parcours.codeDeSuivi(premiere, reference);
        String bon = parcours.codeEnvoye(reference);

        parcours.codeSms(premiere, autreCode(bon));
        parcours.codeSms(premiere, autreCode(bon));
        assertThat(parcours.codeSms(premiere, autreCode(bon))).flash()
                .containsEntry("erreur", "Trop d'essais incorrects. Demandez un nouveau code.");

        // l'attaquant repart d'une session neuve : les essais ne reviennent pas
        MockHttpSession seconde = new MockHttpSession();
        parcours.codeDeSuivi(seconde, reference);
        assertThat(parcours.codeSms(seconde, bon)).hasRedirectedUrl(VERIFICATION).flash()
                .containsEntry("erreur", "Trop d'essais incorrects. Demandez un nouveau code.");

        assertThat(mvc.get().uri("/suivi/" + reference).session(seconde)).hasRedirectedUrl("/suivi");
        assertThat(notifications(demande)).hasSize(1);
    }

    @Test
    void lesEssaisDUneSessionComptentPourUneAutre() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        String reference = demande.getReference();
        MockHttpSession premiere = new MockHttpSession();
        MockHttpSession seconde = new MockHttpSession();
        parcours.codeDeSuivi(premiere, reference);
        parcours.codeDeSuivi(seconde, reference);
        String faux = autreCode(parcours.codeEnvoye(reference));

        assertThat(parcours.codeSms(premiere, faux)).flash().containsEntry("erreur", "Code incorrect. Il vous reste 2 essais.");
        assertThat(parcours.codeSms(seconde, faux)).flash().containsEntry("erreur", "Code incorrect. Il vous reste 1 essai.");
    }

    @Test
    void apresUnSuccesLaReferencePeutEtreVerifieeANouveau() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        parcours.ouvrir(new MockHttpSession(), demande.getReference());

        // le lendemain, une autre visite : un nouveau code est envoye
        MockHttpSession autreVisite = new MockHttpSession();
        parcours.ouvrir(autreVisite, demande.getReference());

        assertThat(notifications(demande)).isEqualTo(List.of("CODE_VERIFICATION", "CODE_VERIFICATION"));
    }
}
