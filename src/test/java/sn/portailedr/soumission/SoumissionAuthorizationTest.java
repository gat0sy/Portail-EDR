package sn.portailedr.soumission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Qui peut deposer une demande, et qui peut lire sa page de confirmation.
 * Deposer : un visiteur (particulier) ou un notaire. Conservateur et administrateur : 403.
 * Confirmation : la session qui a depose la demande, ou le notaire auquel elle appartient.
 * Tous les autres recoivent un 404, que la reference existe ou non : la page ne revele pas
 * qu'une reference existe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SoumissionAuthorizationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    private ParcoursDeDepot parcours;
    private CompteUtilisateur notaire;
    private CompteUtilisateur autreNotaire;
    private CompteUtilisateur conservateur;
    private CompteUtilisateur administrateur;
    private Long idPikine;

    @BeforeEach
    void init() {
        parcours = new ParcoursDeDepot(mvc);
        ComptesDeTest comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        notaire = new CompteUtilisateur(comptes.notaire());
        autreNotaire = new CompteUtilisateur(utilisateurs.save(new Utilisateur("Me Autre Notaire", "autre.notaire@test.sn",
                "+221 77 000 00 02", encodeur.encode(ComptesDeTest.MOT_DE_PASSE), Role.NOTAIRE, null)));
        conservateur = new CompteUtilisateur(comptes.conservateur());
        administrateur = new CompteUtilisateur(comptes.administrateur());
        idPikine = bureaux.findByNom("Bureau de Pikine").orElseThrow().getId();
    }

    private CompteUtilisateur agent(Role role) {
        return role == Role.CONSERVATEUR ? conservateur : administrateur;
    }

    // ---------------------------------------------------------------- deposer

    @ParameterizedTest
    @ValueSource(strings = {"CONSERVATEUR", "ADMINISTRATEUR"})
    void conservateurEtAdministrateurNePeuventPasOuvrirLeFormulaire(Role role) {
        assertThat(mvc.get().uri("/demandes/nouvelle").with(user(agent(role)))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/demandes/nouvelle/verification").with(user(agent(role))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSERVATEUR", "ADMINISTRATEUR"})
    void conservateurEtAdministrateurNePeuventPasDeposer(Role role) {
        MockHttpSession session = new MockHttpSession();
        assertThat(parcours.formulaire(session, agent(role), ParcoursDeDepot.champsPersonne(idPikine)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/demandes/nouvelle/verification").with(user(agent(role))).with(csrf())
                .param("code", "123456")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/demandes/nouvelle/verification/renvoi").with(user(agent(role))).with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void visiteurEtNotaireOuvrentLeFormulaire() {
        assertThat(mvc.get().uri("/demandes/nouvelle")).hasStatusOk();
        assertThat(mvc.get().uri("/demandes/nouvelle").with(user(notaire))).hasStatusOk();
    }

    // ---------------------------------------------------------------- confirmation d'un particulier

    @Test
    void laSessionQuiADeposeLitLaConfirmation() {
        MockHttpSession session = new MockHttpSession();
        String reference = parcours.deposerParticulier(session);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(session)).hasStatusOk()
                .bodyText().contains(reference);
    }

    @Test
    void uneAutreSessionNeLitPasLaConfirmationDUnParticulier() {
        String reference = parcours.deposerParticulier(new MockHttpSession());

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(new MockHttpSession()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(notaire)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- confirmation d'un notaire

    @Test
    void leNotaireLitLaConfirmationDeSaDemandeDepuisUneAutreSession() {
        String reference = parcours.deposerNotaire(new MockHttpSession(), notaire, idPikine);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(notaire))
                .session(new MockHttpSession())).hasStatusOk().bodyText().contains(reference);
    }

    @Test
    void unAutreNotaireNeLitPasLaConfirmation() {
        String reference = parcours.deposerNotaire(new MockHttpSession(), notaire, idPikine);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(autreNotaire)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unVisiteurNeLitPasLaConfirmationDUnNotaire() {
        String reference = parcours.deposerNotaire(new MockHttpSession(), notaire, idPikine);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(new MockHttpSession()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONSERVATEUR", "ADMINISTRATEUR"})
    void conservateurEtAdministrateurNeLisentPasLaConfirmation(Role role) {
        String reference = parcours.deposerNotaire(new MockHttpSession(), notaire, idPikine);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(agent(role))))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void referenceInconnueOuMalFormee() {
        assertThat(mvc.get().uri("/demandes/EDR-2026-ZZZZZZ/confirmation")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/demandes/n-importe-quoi/confirmation")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
