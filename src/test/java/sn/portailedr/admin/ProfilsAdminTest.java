package sn.portailedr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;

/** Comptes : creation, modification, affectation, mot de passe provisoire, desactivation, et le journal. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProfilsAdminTest {

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

    private ComptesDeTest comptes;
    private Utilisateur admin;
    private CompteUtilisateur compteAdmin;

    @BeforeEach
    void init() {
        comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        admin = comptes.administrateur();
        compteAdmin = new CompteUtilisateur(admin);
    }

    private List<String> journal() {
        return jdbc.sql("SELECT action FROM journal WHERE id_utilisateur = ? AND id_demande IS NULL ORDER BY id_journal")
                .param(admin.getId()).query(String.class).list();
    }

    private long idBureau(String nom) {
        return bureaux.findByNom(nom).orElseThrow().getId();
    }

    private MvcTestResult creer(String nom, String email, String role, String idBureau, String motDePasse) {
        return mvc.post().uri("/admin/profils").with(user(compteAdmin)).with(csrf())
                .param("nom", nom).param("email", email).param("telephone", "+221 77 000 00 11")
                .param("role", role).param("idBureau", idBureau).param("motDePasse", motDePasse)
                .exchange();
    }

    private static String redirection(MvcTestResult resultat) {
        return resultat.getMvcResult().getResponse().getRedirectedUrl();
    }

    // ---------------------------------------------------------------- page

    @Test
    void laPageListeLesComptesSansJamaisAfficherLesEmpreintes() {
        comptes.notaire();
        comptes.conservateur();

        assertThat(mvc.get().uri("/admin/profils").with(user(compteAdmin))).hasStatusOk().bodyText()
                .contains("Me Test Notaire", "notaire@test.sn", "Test Conservateur", "Bureau de Dakar-Plateau")
                .contains("Créer un profil")
                .doesNotContain("$2a$");
    }

    @Test
    void laPageFiltreParRole() {
        comptes.notaire();
        comptes.conservateur();

        assertThat(mvc.get().uri("/admin/profils?role=NOTAIRE").with(user(compteAdmin))).bodyText()
                .contains("Me Test Notaire")
                .doesNotContain("Test Conservateur");
    }

    @Test
    void lAdministrateurNeVoitPasDeBoutonPourSeDesactiver() {
        assertThat(mvc.get().uri("/admin/profils").with(user(compteAdmin))).bodyText()
                .doesNotContain("action=\"/admin/profils/" + admin.getId() + "/desactivation\"");
    }

    // ---------------------------------------------------------------- creation

    @Test
    void creerUnNotaire() {
        MvcTestResult resultat = creer("Me Awa Ndiaye", "a.ndiaye@test.sn", "NOTAIRE", "", "provisoire-2026");

        assertThat(redirection(resultat)).isEqualTo("/admin/profils");
        Utilisateur cree = utilisateurs.findByEmailIgnoreCase("a.ndiaye@test.sn").orElseThrow();
        assertThat(cree.getRole()).isEqualTo(Role.NOTAIRE);
        assertThat(cree.isActif()).isTrue();
        assertThat(cree.getBureau()).isNull();
        assertThat(cree.getMotDePasse()).startsWith("$2").isNotEqualTo("provisoire-2026");
        assertThat(encodeur.matches("provisoire-2026", cree.getMotDePasse())).isTrue();
        assertThat(journal()).containsExactly("Création du profil Me Awa Ndiaye (Notaire)");
    }

    @Test
    void leNouveauCompteSeConnecteAvecSonMotDePasseProvisoire() {
        creer("Me Awa Ndiaye", "a.ndiaye@test.sn", "NOTAIRE", "", "provisoire-2026");

        assertThat(mvc.perform(formLogin("/connexion").user("a.ndiaye@test.sn").password("provisoire-2026")))
                .matches(authenticated().withRoles("NOTAIRE"));
    }

    @Test
    void creerUnConservateurAffecteAUnBureau() {
        creer("Coumba Ndour", "c.ndour@test.sn", "CONSERVATEUR", String.valueOf(idBureau("Bureau de Pikine")), "provisoire-2026");

        Utilisateur cree = utilisateurs.findByEmailIgnoreCase("c.ndour@test.sn").orElseThrow();
        assertThat(cree.getBureau().getNom()).isEqualTo("Bureau de Pikine");
        assertThat(journal()).containsExactly("Création du profil Coumba Ndour (Conservateur, Bureau de Pikine)");
    }

    @Test
    void unConservateurSansBureauEstRefuse() {
        MvcTestResult resultat = creer("Coumba Ndour", "c.ndour@test.sn", "CONSERVATEUR", "", "provisoire-2026");

        assertThat(resultat).hasStatusOk().bodyText().contains("Un conservateur doit être affecté à un bureau.");
        assertThat(utilisateurs.findByEmailIgnoreCase("c.ndour@test.sn")).isEmpty();
        assertThat(journal()).isEmpty();
    }

    @Test
    void uneAdresseDejaUtiliseeEstRefuseeSansTenirCompteDeLaCasse() {
        comptes.notaire();

        MvcTestResult resultat = creer("Autre", "Notaire@TEST.sn", "NOTAIRE", "", "provisoire-2026");

        assertThat(resultat).hasStatusOk().bodyText()
                .contains("Cette adresse e-mail est déjà utilisée par un autre compte.");
        assertThat(journal()).isEmpty();
    }

    @Test
    void lesChampsObligatoiresEtLeMotDePasseSontVerifies() {
        MvcTestResult resultat = mvc.post().uri("/admin/profils").with(user(compteAdmin)).with(csrf())
                .param("nom", "").param("email", "pas-une-adresse").param("telephone", "")
                .param("role", "").param("idBureau", "").param("motDePasse", "court")
                .exchange();

        assertThat(resultat).hasStatusOk().bodyText()
                .contains("Le nom est obligatoire.", "Adresse e-mail invalide.", "Le téléphone est obligatoire.",
                        "Choisissez un rôle.", "Le mot de passe provisoire doit comporter au moins 8 caractères.");
        assertThat(journal()).isEmpty();
    }

    // ---------------------------------------------------------------- modification et affectation

    @Test
    void laPageAfficheLeFormulaireDeModificationDUnCompte() {
        Utilisateur notaire = comptes.notaire();

        assertThat(mvc.get().uri("/admin/profils?id={id}", notaire.getId()).with(user(compteAdmin))).bodyText()
                .contains("Modifier le profil", "value=\"Me Test Notaire\"", "Réinitialiser le mot de passe");
    }

    @Test
    void affecterUnConservateurAUnAutreBureau() {
        Utilisateur conservateur = comptes.conservateur();

        MvcTestResult resultat = mvc.post().uri("/admin/profils/{id}", conservateur.getId())
                .with(user(compteAdmin)).with(csrf())
                .param("nom", "Test Conservateur").param("email", "conservateur@test.sn")
                .param("telephone", "+221 77 000 00 00").param("role", "CONSERVATEUR")
                .param("idBureau", String.valueOf(idBureau("Bureau de Pikine")))
                .exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/profils");
        assertThat(utilisateurs.findById(conservateur.getId()).orElseThrow().getBureau().getNom())
                .isEqualTo("Bureau de Pikine");
        assertThat(journal()).containsExactly("Affectation de Test Conservateur au Bureau de Pikine");
    }

    @Test
    void modifierLeNomDUnCompte() {
        Utilisateur notaire = comptes.notaire();

        mvc.post().uri("/admin/profils/{id}", notaire.getId()).with(user(compteAdmin)).with(csrf())
                .param("nom", "Me Test Notaire Fall").param("email", "notaire@test.sn")
                .param("telephone", "+221 77 000 00 00").param("role", "NOTAIRE").param("idBureau", "")
                .exchange();

        assertThat(utilisateurs.findById(notaire.getId()).orElseThrow().getNom()).isEqualTo("Me Test Notaire Fall");
        assertThat(journal()).containsExactly("Modification du profil Me Test Notaire Fall");
    }

    @Test
    void retirerLeBureauDUnConservateurEstRefuse() {
        Utilisateur conservateur = comptes.conservateur();

        assertThat(mvc.post().uri("/admin/profils/{id}", conservateur.getId()).with(user(compteAdmin)).with(csrf())
                .param("nom", "Test Conservateur").param("email", "conservateur@test.sn")
                .param("telephone", "+221 77 000 00 00").param("role", "CONSERVATEUR").param("idBureau", ""))
                .hasStatusOk().bodyText().contains("Un conservateur doit être affecté à un bureau.");
        assertThat(utilisateurs.findById(conservateur.getId()).orElseThrow().getBureau()).isNotNull();
        assertThat(journal()).isEmpty();
    }

    // ---------------------------------------------------------------- mot de passe

    @Test
    void reinitialiserLeMotDePasse() {
        Utilisateur notaire = comptes.notaire();

        MvcTestResult resultat = mvc.post().uri("/admin/profils/{id}/mot-de-passe", notaire.getId())
                .with(user(compteAdmin)).with(csrf()).param("nouveauMotDePasse", "nouveau-provisoire").exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/profils");
        assertThat(mvc.perform(formLogin("/connexion").user("notaire@test.sn").password("nouveau-provisoire")))
                .matches(authenticated());
        assertThat(mvc.perform(formLogin("/connexion").user("notaire@test.sn").password(ComptesDeTest.MOT_DE_PASSE)))
                .matches(unauthenticated());
        assertThat(journal()).containsExactly("Réinitialisation du mot de passe de Me Test Notaire");
    }

    @Test
    void unMotDePasseProvisoireTropCourtEstRefuse() {
        Utilisateur notaire = comptes.notaire();

        assertThat(mvc.post().uri("/admin/profils/{id}/mot-de-passe", notaire.getId())
                .with(user(compteAdmin)).with(csrf()).param("nouveauMotDePasse", "court"))
                .hasStatusOk().bodyText().contains("Le mot de passe provisoire doit comporter au moins 8 caractères.");
        assertThat(encodeur.matches(ComptesDeTest.MOT_DE_PASSE,
                utilisateurs.findById(notaire.getId()).orElseThrow().getMotDePasse())).isTrue();
        assertThat(journal()).isEmpty();
    }

    // ---------------------------------------------------------------- desactivation

    @Test
    void desactiverPuisReactiverUnCompte() {
        Utilisateur notaire = comptes.notaire();

        mvc.post().uri("/admin/profils/{id}/desactivation", notaire.getId()).with(user(compteAdmin)).with(csrf()).exchange();
        assertThat(utilisateurs.findById(notaire.getId()).orElseThrow().isActif()).isFalse();

        mvc.post().uri("/admin/profils/{id}/reactivation", notaire.getId()).with(user(compteAdmin)).with(csrf()).exchange();
        assertThat(utilisateurs.findById(notaire.getId()).orElseThrow().isActif()).isTrue();

        assertThat(journal()).containsExactly(
                "Désactivation du compte de Me Test Notaire",
                "Réactivation du compte de Me Test Notaire");
    }

    @Test
    void unAdministrateurNePeutPasDesactiverSonPropreCompte() {
        MvcTestResult resultat = mvc.post().uri("/admin/profils/{id}/desactivation", admin.getId())
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(resultat).flash().containsEntry("erreur", "Vous ne pouvez pas désactiver votre propre compte.");
        assertThat(utilisateurs.findById(admin.getId()).orElseThrow().isActif()).isTrue();
        assertThat(journal()).isEmpty();
    }
}
