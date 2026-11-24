package sn.portailedr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Regles d'acces par URL : refus par defaut, routes publiques, routes reservees a un role.
 * Certaines pages autorisees n'existent pas encore (elles arrivent dans la tranche 7) :
 * pour elles on verifie seulement que la securite laisse passer la requete (ni 401, ni 403,
 * ni redirection vers la connexion). La reponse est alors un 404 de l'application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorizationRulesTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    private CompteUtilisateur notaire;
    private CompteUtilisateur conservateur;
    private CompteUtilisateur administrateur;

    @BeforeEach
    void comptes() {
        ComptesDeTest comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        notaire = new CompteUtilisateur(comptes.notaire());
        conservateur = new CompteUtilisateur(comptes.conservateur());
        administrateur = new CompteUtilisateur(comptes.administrateur());
    }

    private CompteUtilisateur compte(Role role) {
        return switch (role) {
            case NOTAIRE -> notaire;
            case CONSERVATEUR -> conservateur;
            case ADMINISTRATEUR -> administrateur;
        };
    }

    private static void redirigeVersConnexion(MvcTestResult resultat) {
        assertThat(resultat).hasStatus3xxRedirection();
        assertThat(resultat.getMvcResult().getResponse().getRedirectedUrl()).endsWith("/connexion");
    }

    /** La securite laisse passer : ni 401, ni 403, ni redirection vers la page de connexion. */
    private static void autorise(MvcTestResult resultat) {
        int statut = resultat.getMvcResult().getResponse().getStatus();
        assertThat(statut).isNotIn(401, 403);
        String redirection = resultat.getMvcResult().getResponse().getRedirectedUrl();
        assertThat(redirection == null || !redirection.endsWith("/connexion")).isTrue();
    }

    // ---------------------------------------------------------------- visiteur anonyme

    @ParameterizedTest
    @ValueSource(strings = {"/profil", "/admin", "/admin/bureaux", "/admin/profils", "/conservateur/demandes",
        "/une-route-sans-regle"})
    void anonymeRedirigeVersLaConnexionSurUneRouteProtegee(String url) {
        redirigeVersConnexion(mvc.get().uri(url).exchange());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/connexion", "/annuaire", "/css/base.css"})
    void anonymeAccedeAuxPagesPubliquesExistantes(String url) {
        assertThat(mvc.get().uri(url)).hasStatusOk();
    }

    /** Tranche 6 : la saisie du code existe ; sans verification SMS, une demande renvoie a la saisie. */
    @Test
    void anonymeAccedeAuSuivi() {
        assertThat(mvc.get().uri("/suivi")).hasStatusOk();
        assertThat(mvc.get().uri("/suivi/EDR-2026-K7P4QX")).hasRedirectedUrl("/suivi");
    }

    /** Tranche 5 : le formulaire de depot existe. Sans demande en attente, l'etape SMS renvoie au formulaire. */
    @Test
    void anonymeAccedeALaNouvelleDemande() {
        assertThat(mvc.get().uri("/demandes/nouvelle")).hasStatusOk();
        assertThat(mvc.get().uri("/demandes/nouvelle/verification")).hasRedirectedUrl("/demandes/nouvelle");
    }

    // ---------------------------------------------------------------- chaque role sur ses pages

    @ParameterizedTest
    @EnumSource(Role.class)
    void chaqueRoleAccedeASonProfil(Role role) {
        assertThat(mvc.get().uri("/profil").with(user(compte(role)))).hasStatusOk();
    }

    @Test
    void administrateurAccedeAuxPagesDAdministration() {
        assertThat(mvc.get().uri("/admin").with(user(administrateur))).hasStatusOk();
        assertThat(mvc.get().uri("/admin/bureaux").with(user(administrateur))).hasStatusOk();
        assertThat(mvc.get().uri("/admin/profils").with(user(administrateur))).hasStatusOk();
    }

    @Test
    void conservateurAccedeAuxDemandesDuBureau() {
        autorise(mvc.get().uri("/conservateur/demandes").with(user(conservateur)).exchange());
    }

    @Test
    void notaireAccedeALaNouvelleDemande() {
        assertThat(mvc.get().uri("/demandes/nouvelle").with(user(notaire))).hasStatusOk();
    }

    // ---------------------------------------------------------------- chaque role refuse ailleurs

    static Stream<Arguments> pagesInterdites() {
        return Stream.of(
                arguments(Role.NOTAIRE, "/admin"),
                arguments(Role.NOTAIRE, "/admin/profils"),
                arguments(Role.NOTAIRE, "/conservateur/demandes"),
                arguments(Role.CONSERVATEUR, "/admin"),
                arguments(Role.CONSERVATEUR, "/admin/bureaux"),
                arguments(Role.CONSERVATEUR, "/demandes/nouvelle"),
                arguments(Role.CONSERVATEUR, "/suivi"),
                arguments(Role.ADMINISTRATEUR, "/conservateur/demandes"),
                arguments(Role.ADMINISTRATEUR, "/demandes/nouvelle"),
                arguments(Role.ADMINISTRATEUR, "/suivi"));
    }

    @ParameterizedTest
    @MethodSource("pagesInterdites")
    void roleRefuseSurLesPagesDesAutresRoles(Role role, String url) {
        assertThat(mvc.get().uri(url).with(user(compte(role)))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void refusParDefautSurUneRouteSansRegle(Role role) {
        assertThat(mvc.get().uri("/une-route-sans-regle").with(user(compte(role)))).hasStatus(HttpStatus.FORBIDDEN);
    }
}
