package sn.portailedr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/** Connexion par formulaire sur /connexion, comptes inactifs, CSRF et deconnexion. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginTest {

    private static final String MESSAGE_IDENTIFIANTS = "Adresse e-mail ou mot de passe incorrect.";
    private static final String MESSAGE_COMPTE_DESACTIVE = "Ce compte est désactivé.";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    private ComptesDeTest comptes;

    @BeforeEach
    void init() {
        comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
    }

    private MvcTestResult seConnecter(String email, String motDePasse) {
        return mvc.perform(formLogin("/connexion").user(email).password(motDePasse));
    }

    private static String redirection(MvcTestResult resultat) {
        return resultat.getMvcResult().getResponse().getRedirectedUrl();
    }

    // ---------------------------------------------------------------- connexion

    @Test
    void connexionReussieMeneAuProfil() {
        comptes.notaire();

        MvcTestResult resultat = seConnecter("notaire@test.sn", ComptesDeTest.MOT_DE_PASSE);

        assertThat(resultat).matches(authenticated().withRoles("NOTAIRE"));
        assertThat(redirection(resultat)).isEqualTo("/profil");
    }

    @Test
    void lAdresseEMailEstInsensibleALaCasse() {
        comptes.conservateur();

        assertThat(seConnecter("Conservateur@TEST.sn", ComptesDeTest.MOT_DE_PASSE))
                .matches(authenticated().withRoles("CONSERVATEUR"));
    }

    @Test
    void mauvaisMotDePasseRefuseAvecUnMessageEnFrancais() {
        comptes.notaire();

        MvcTestResult resultat = seConnecter("notaire@test.sn", "mauvais-mot-de-passe");

        assertThat(resultat).matches(unauthenticated());
        assertThat(redirection(resultat)).isEqualTo("/connexion?erreur");
        assertThat(mvc.get().uri("/connexion?erreur")).bodyText().contains(MESSAGE_IDENTIFIANTS);
    }

    @Test
    void adresseInconnueRefuseeAvecLeMemeMessage() {
        MvcTestResult resultat = seConnecter("personne@test.sn", ComptesDeTest.MOT_DE_PASSE);

        assertThat(resultat).matches(unauthenticated());
        assertThat(redirection(resultat)).isEqualTo("/connexion?erreur");
    }

    @Test
    void pageDeConnexionSansErreurNAfficheAucunMessage() {
        assertThat(mvc.get().uri("/connexion")).bodyText()
                .doesNotContain(MESSAGE_IDENTIFIANTS)
                .doesNotContain(MESSAGE_COMPTE_DESACTIVE);
    }

    // ---------------------------------------------------------------- compte inactif

    @Test
    void compteInactifNePeutPasSeConnecter() {
        comptes.notaireInactif();

        MvcTestResult resultat = seConnecter("inactif@test.sn", ComptesDeTest.MOT_DE_PASSE);

        assertThat(resultat).matches(unauthenticated());
        assertThat(redirection(resultat)).isEqualTo("/connexion?desactive");
        assertThat(mvc.get().uri("/connexion?desactive")).bodyText().contains(MESSAGE_COMPTE_DESACTIVE);
    }

    @Test
    void compteInactifAvecUnMauvaisMotDePasseNeReveleRien() {
        // le statut du compte n'est verifie qu'apres le mot de passe :
        // sans le bon mot de passe, on ne peut pas savoir que le compte existe et est desactive
        comptes.notaireInactif();

        assertThat(redirection(seConnecter("inactif@test.sn", "mauvais-mot-de-passe"))).isEqualTo("/connexion?erreur");
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void connexionSansJetonCsrfRefusee() {
        comptes.notaire();

        MvcTestResult resultat = mvc.post().uri("/connexion")
                .param("username", "notaire@test.sn")
                .param("password", ComptesDeTest.MOT_DE_PASSE)
                .exchange();

        assertThat(resultat).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(resultat).matches(unauthenticated());
    }

    @Test
    void leFormulaireDeConnexionContientLeJetonCsrf() {
        assertThat(mvc.get().uri("/connexion")).bodyText().contains("name=\"_csrf\"");
    }

    // ---------------------------------------------------------------- deconnexion

    private MockHttpSession sessionConnectee() throws Exception {
        comptes.notaire();
        return (MockHttpSession) mockMvc.perform(formLogin("/connexion").user("notaire@test.sn")
                .password(ComptesDeTest.MOT_DE_PASSE)).andReturn().getRequest().getSession(false);
    }

    @Test
    void deconnexionInvalideLaSession() throws Exception {
        MockHttpSession session = sessionConnectee();
        assertThat(mvc.get().uri("/profil").session(session)).hasStatusOk();

        MvcTestResult resultat = mvc.post().uri("/deconnexion").session(session).with(csrf()).exchange();

        assertThat(redirection(resultat)).isEqualTo("/");
        assertThat(session.isInvalid()).isTrue();
        // l'ancienne session ne donne plus acces au profil
        MvcTestResult apres = mvc.get().uri("/profil").session(session).exchange();
        assertThat(redirection(apres)).endsWith("/connexion");
    }

    @Test
    void deconnexionSansJetonCsrfRefusee() throws Exception {
        MockHttpSession session = sessionConnectee();

        assertThat(mvc.post().uri("/deconnexion").session(session)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(session.isInvalid()).isFalse();
        assertThat(mvc.get().uri("/profil").session(session)).hasStatusOk();
    }
}
