package sn.portailedr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Une session deja ouverte suit les changements faits par l'administrateur :
 * un compte desactive (ou dont le role change) perd l'acces des la requete suivante,
 * sans attendre la fin de sa session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SessionOuverteTest {

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

    private Utilisateur notaire;
    private CompteUtilisateur administrateur;

    @BeforeEach
    void init() {
        ComptesDeTest comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        notaire = comptes.notaire();
        administrateur = new CompteUtilisateur(comptes.administrateur());
    }

    private MockHttpSession sessionDuNotaire() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/connexion").user("notaire@test.sn")
                .password(ComptesDeTest.MOT_DE_PASSE)).andReturn().getRequest().getSession(false);
        assertThat(mvc.get().uri("/profil").session(session)).hasStatusOk();
        return session;
    }

    private static String redirection(MvcTestResult resultat) {
        return resultat.getMvcResult().getResponse().getRedirectedUrl();
    }

    @Test
    void compteDesactiveParLAdministrateurPerdSaSessionOuverte() throws Exception {
        MockHttpSession session = sessionDuNotaire();

        mvc.post().uri("/admin/profils/{id}/desactivation", notaire.getId())
                .with(user(administrateur)).with(csrf()).exchange();

        MvcTestResult suivante = mvc.get().uri("/profil").session(session).exchange();
        assertThat(redirection(suivante)).isEqualTo("/connexion?desactive");
        assertThat(session.isInvalid()).isTrue();
        assertThat(redirection(mvc.get().uri("/profil").session(session).exchange())).endsWith("/connexion");
    }

    @Test
    void lAccueilPublicNeRendPasLaSessionNonPlus() throws Exception {
        // la verification porte sur toute requete authentifiee, pas seulement sur les pages protegees
        MockHttpSession session = sessionDuNotaire();
        notaire.setActif(false);

        assertThat(redirection(mvc.get().uri("/").session(session).exchange())).isEqualTo("/connexion?desactive");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void roleModifieParLAdministrateurObligeAUneNouvelleConnexion() throws Exception {
        // sinon l'ancien role resterait dans la session (un notaire promu, ou un administrateur retrogade)
        MockHttpSession session = sessionDuNotaire();

        mvc.post().uri("/admin/profils/{id}", notaire.getId())
                .param("nom", notaire.getNom()).param("email", notaire.getEmail())
                .param("telephone", notaire.getTelephone()).param("role", "ADMINISTRATEUR")
                .with(user(administrateur)).with(csrf()).exchange();

        assertThat(redirection(mvc.get().uri("/profil").session(session).exchange())).isEqualTo("/connexion?modifie");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void compteToujoursActifGardeSaSession() throws Exception {
        MockHttpSession session = sessionDuNotaire();

        assertThat(mvc.get().uri("/profil").session(session)).hasStatusOk();
        assertThat(mvc.get().uri("/").session(session)).hasStatusOk();
        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void lesMessagesDeReconnexionSontEnFrancais() {
        assertThat(mvc.get().uri("/connexion?desactive")).bodyText().contains("Ce compte est désactivé.");
        assertThat(mvc.get().uri("/connexion?modifie")).bodyText()
                .contains("Votre compte a été modifié par l'administrateur. Reconnectez-vous.");
    }
}
