package sn.portailedr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * /admin/** est reserve a l'administrateur, en lecture (GET) comme en action (POST).
 * Les POST portent un jeton CSRF valide : le refus vient donc bien de la regle de role.
 * Aucun POST refuse ne doit laisser de trace (ni modification, ni ligne de journal).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAuthorizationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    @Autowired
    private JdbcClient jdbc;

    private CompteUtilisateur notaire;
    private CompteUtilisateur conservateur;
    private long idBureau;
    private long idLivre;
    private long idCompte;

    @BeforeEach
    void init() {
        ComptesDeTest comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        notaire = new CompteUtilisateur(comptes.notaire());
        conservateur = new CompteUtilisateur(comptes.conservateur());
        idCompte = utilisateurs.findByEmailIgnoreCase("notaire@test.sn").orElseThrow().getId();
        idBureau = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();
        idLivre = jdbc.sql("SELECT id_livre_foncier FROM livre_foncier WHERE code = 'TH'").query(Long.class).single();
    }

    /** Une requete POST de chaque action d'administration, avec des parametres valides. */
    private MvcTestResult post(String action, CompteUtilisateur compte) {
        var requete = switch (action) {
            case "creer-bureau" -> mvc.post().uri("/admin/bureaux")
                    .param("nom", "Bureau pirate").param("adresse", "Dakar").param("telephone", "+221 33 000 00 99");
            case "modifier-bureau" -> mvc.post().uri("/admin/bureaux/{id}", idBureau)
                    .param("nom", "Bureau pirate").param("adresse", "Dakar").param("telephone", "+221 33 000 00 99");
            case "supprimer-bureau" -> mvc.post().uri("/admin/bureaux/{id}/suppression", idBureau);
            case "ajouter-livre" -> mvc.post().uri("/admin/bureaux/livres")
                    .param("code", "ZZ").param("nom", "Livre pirate").param("idBureau", String.valueOf(idBureau));
            case "supprimer-livre" -> mvc.post().uri("/admin/bureaux/livres/{id}/suppression", idLivre);
            case "creer-profil" -> mvc.post().uri("/admin/profils")
                    .param("nom", "Pirate").param("email", "pirate@test.sn").param("telephone", "+221 77 000 00 99")
                    .param("role", "ADMINISTRATEUR").param("motDePasse", "mot-de-passe-pirate");
            case "modifier-profil" -> mvc.post().uri("/admin/profils/{id}", idCompte)
                    .param("nom", "Pirate").param("email", "notaire@test.sn").param("telephone", "+221 77 000 00 99")
                    .param("role", "ADMINISTRATEUR");
            case "mot-de-passe" -> mvc.post().uri("/admin/profils/{id}/mot-de-passe", idCompte)
                    .param("nouveauMotDePasse", "mot-de-passe-pirate");
            case "desactiver" -> mvc.post().uri("/admin/profils/{id}/desactivation", idCompte);
            case "reactiver" -> mvc.post().uri("/admin/profils/{id}/reactivation", idCompte);
            default -> throw new IllegalArgumentException(action);
        };
        requete = requete.with(csrf());
        if (compte != null) {
            requete = requete.with(user(compte));
        }
        return requete.exchange();
    }

    private void aucuneTrace() {
        assertThat(jdbc.sql("SELECT count(*) FROM journal").query(Long.class).single()).isZero();
        assertThat(bureaux.findByNom("Bureau pirate")).isEmpty();
        assertThat(utilisateurs.findByEmailIgnoreCase("pirate@test.sn")).isEmpty();
        assertThat(bureaux.findByNom("Bureau de Thiès")).isPresent();
        assertThat(jdbc.sql("SELECT count(*) FROM livre_foncier WHERE code IN ('TH', 'ZZ')").query(Long.class).single())
                .isEqualTo(1);
        var compte = utilisateurs.findById(idCompte).orElseThrow();
        assertThat(compte.isActif()).isTrue();
        assertThat(compte.getNom()).isEqualTo("Me Test Notaire");
        assertThat(encodeur.matches(ComptesDeTest.MOT_DE_PASSE, compte.getMotDePasse())).isTrue();
    }

    // ---------------------------------------------------------------- GET

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/bureaux", "/admin/profils", "/admin/bureaux?id=1", "/admin/profils?role=NOTAIRE"})
    void anonymeRedirigeVersLaConnexion(String url) {
        MvcTestResult resultat = mvc.get().uri(url).exchange();
        assertThat(resultat).hasStatus3xxRedirection();
        assertThat(resultat.getMvcResult().getResponse().getRedirectedUrl()).endsWith("/connexion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/bureaux", "/admin/profils"})
    void notaireEtConservateurRefusesEnLecture(String url) {
        assertThat(mvc.get().uri(url).with(user(notaire))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri(url).with(user(conservateur))).hasStatus(HttpStatus.FORBIDDEN);
    }

    // ---------------------------------------------------------------- POST

    @ParameterizedTest
    @ValueSource(strings = {"creer-bureau", "modifier-bureau", "supprimer-bureau", "ajouter-livre", "supprimer-livre",
        "creer-profil", "modifier-profil", "mot-de-passe", "desactiver", "reactiver"})
    void anonymeRedirigeVersLaConnexionSansRienModifier(String action) {
        MvcTestResult resultat = post(action, null);

        assertThat(resultat).hasStatus3xxRedirection();
        assertThat(resultat.getMvcResult().getResponse().getRedirectedUrl()).endsWith("/connexion");
        aucuneTrace();
    }

    @ParameterizedTest
    @ValueSource(strings = {"creer-bureau", "modifier-bureau", "supprimer-bureau", "ajouter-livre", "supprimer-livre",
        "creer-profil", "modifier-profil", "mot-de-passe", "desactiver", "reactiver"})
    void notaireRefuseSansRienModifier(String action) {
        assertThat(post(action, notaire)).hasStatus(HttpStatus.FORBIDDEN);
        aucuneTrace();
    }

    @ParameterizedTest
    @ValueSource(strings = {"creer-bureau", "modifier-bureau", "supprimer-bureau", "ajouter-livre", "supprimer-livre",
        "creer-profil", "modifier-profil", "mot-de-passe", "desactiver", "reactiver"})
    void conservateurRefuseSansRienModifier(String action) {
        assertThat(post(action, conservateur)).hasStatus(HttpStatus.FORBIDDEN);
        aucuneTrace();
    }
}
