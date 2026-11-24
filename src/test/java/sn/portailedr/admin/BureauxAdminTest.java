package sn.portailedr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.OffsetDateTime;
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
import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.LivreFoncierRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;

/** Bureaux et livres fonciers : creation, modification, suppression, et la ligne de journal de chaque action. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BureauxAdminTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private LivreFoncierRepository livresFonciers;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private PasswordEncoder encodeur;

    @Autowired
    private JdbcClient jdbc;

    private Utilisateur admin;
    private CompteUtilisateur compteAdmin;

    @BeforeEach
    void init() {
        admin = new ComptesDeTest(utilisateurs, bureaux, encodeur).administrateur();
        compteAdmin = new CompteUtilisateur(admin);
    }

    private List<String> journal() {
        return jdbc.sql("SELECT action FROM journal WHERE id_utilisateur = ? AND id_demande IS NULL ORDER BY id_journal")
                .param(admin.getId()).query(String.class).list();
    }

    private Bureau bureauVide() {
        return bureaux.save(new Bureau("Bureau de Kaolack", "Kaolack", "+221 33 000 00 07"));
    }

    private static String redirection(MvcTestResult resultat) {
        return resultat.getMvcResult().getResponse().getRedirectedUrl();
    }

    // ---------------------------------------------------------------- page

    @Test
    void laPageListeLesBureauxEtLesLivresFonciers() {
        assertThat(mvc.get().uri("/admin/bureaux").with(user(compteAdmin))).hasStatusOk().bodyText()
                .contains("Bureaux enregistrés · 6", "Livres fonciers · 7")
                .contains("Bureau de Pikine", "Dagoudane Pikine", "Guédiawaye")
                .contains("Ajouter un bureau", "Ajouter un livre foncier");
    }

    @Test
    void laPageAfficheLeFormulaireDeModificationDUnBureau() {
        long id = bureaux.findByNom("Bureau de Rufisque").orElseThrow().getId();

        assertThat(mvc.get().uri("/admin/bureaux?id={id}", id).with(user(compteAdmin))).hasStatusOk().bodyText()
                .contains("Modifier le bureau", "value=\"Bureau de Rufisque\"", "action=\"/admin/bureaux/" + id + "\"");
    }

    // ---------------------------------------------------------------- bureau

    @Test
    void creerUnBureau() {
        MvcTestResult resultat = mvc.post().uri("/admin/bureaux").with(user(compteAdmin)).with(csrf())
                .param("nom", "Bureau de Kaolack").param("adresse", "Kaolack").param("telephone", "+221 33 000 00 07")
                .exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/bureaux");
        assertThat(bureaux.findByNom("Bureau de Kaolack")).isPresent();
        assertThat(journal()).containsExactly("Création du bureau Bureau de Kaolack");
    }

    @Test
    void unBureauIncompletReafficheLeFormulaireAvecDesMessagesEnFrancais() {
        MvcTestResult resultat = mvc.post().uri("/admin/bureaux").with(user(compteAdmin)).with(csrf())
                .param("nom", " ").param("adresse", "").param("telephone", "").exchange();

        assertThat(resultat).hasStatusOk().bodyText()
                // l'apostrophe est echappee en &#39; dans le HTML ; le navigateur affiche "L'adresse est obligatoire."
                .contains("Le nom du bureau est obligatoire.", "L&#39;adresse est obligatoire.", "Le téléphone est obligatoire.");
        assertThat(bureaux.count()).isEqualTo(6);
        assertThat(journal()).isEmpty();
    }

    @Test
    void modifierUnBureau() {
        Bureau bureau = bureauVide();

        mvc.post().uri("/admin/bureaux/{id}", bureau.getId()).with(user(compteAdmin)).with(csrf())
                .param("nom", "Bureau de Kaolack-Ville").param("adresse", "Kaolack, centre").param("telephone", "+221 33 000 00 08")
                .exchange();

        Bureau relu = bureaux.findById(bureau.getId()).orElseThrow();
        assertThat(relu.getNom()).isEqualTo("Bureau de Kaolack-Ville");
        assertThat(relu.getAdresse()).isEqualTo("Kaolack, centre");
        assertThat(journal()).containsExactly("Modification du bureau Bureau de Kaolack-Ville");
    }

    @Test
    void supprimerUnBureauSansLivreNiDemande() {
        Bureau bureau = bureauVide();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/{id}/suppression", bureau.getId())
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/bureaux");
        assertThat(bureaux.findById(bureau.getId())).isEmpty();
        assertThat(journal()).containsExactly("Suppression du bureau Bureau de Kaolack");
    }

    @Test
    void supprimerUnBureauQuiTientDesLivresFonciersEstRefuse() {
        long thies = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/{id}/suppression", thies)
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(resultat).flash().containsEntry("erreur",
                "Suppression impossible : ce bureau tient encore des livres fonciers.");
        assertThat(bureaux.findById(thies)).isPresent();
        assertThat(journal()).isEmpty();
    }

    @Test
    void supprimerUnBureauQuiADesDemandesEstRefuse() {
        Bureau bureau = bureauVide();
        jdbc.sql("""
                INSERT INTO demande (reference, type, statut, numero_titre_foncier, declarant_cni, declarant_nom,
                    declarant_telephone, telephone_verifie, date_soumission, id_bureau)
                VALUES ('EDR-2026-K7P4QX', 'BIEN', 'SOUMISE', '12405/KL', '1751199900123', 'Awa Diop',
                    '+221 77 000 00 00', true, ?, ?)""")
                .params(OffsetDateTime.now(), bureau.getId()).update();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/{id}/suppression", bureau.getId())
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(resultat).flash().containsEntry("erreur",
                "Suppression impossible : des demandes sont rattachées à ce bureau.");
        assertThat(bureaux.findById(bureau.getId())).isPresent();
        assertThat(journal()).isEmpty();
    }

    @Test
    void supprimerUnBureauAuquelUnConservateurEstAffecteEstRefuse() {
        Bureau bureau = bureauVide();
        utilisateurs.save(new Utilisateur("Coumba Ndour", "c.ndour@test.sn", "+221 33 000 00 10", "x",
                Role.CONSERVATEUR, bureau));

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/{id}/suppression", bureau.getId())
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(resultat).flash().containsEntry("erreur",
                "Suppression impossible : des conservateurs sont encore affectés à ce bureau.");
        assertThat(bureaux.findById(bureau.getId())).isPresent();
        assertThat(journal()).isEmpty();
    }

    @Test
    void leMessageDeRefusEstAfficheSurLaPage() {
        assertThat(mvc.get().uri("/admin/bureaux").with(user(compteAdmin))
                .flashAttr("erreur", "Suppression impossible : ce bureau tient encore des livres fonciers."))
                .bodyText().contains("Suppression impossible : ce bureau tient encore des livres fonciers.");
    }

    // ---------------------------------------------------------------- livre foncier

    @Test
    void ajouterUnLivreFoncierMetLeCodeEnMajuscules() {
        long thies = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/livres").with(user(compteAdmin)).with(csrf())
                .param("code", " tb ").param("nom", "Tivaouane").param("idBureau", String.valueOf(thies)).exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/bureaux");
        var livre = livresFonciers.findByCode("TB").orElseThrow();
        assertThat(livre.getBureau().getNom()).isEqualTo("Bureau de Thiès");
        assertThat(journal()).containsExactly("Ajout du livre foncier TB au Bureau de Thiès");
    }

    @Test
    void unCodeDejaUtiliseEstRefuseSansTenirCompteDeLaCasse() {
        long thies = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/livres").with(user(compteAdmin)).with(csrf())
                .param("code", "nga").param("nom", "Doublon").param("idBureau", String.valueOf(thies)).exchange();

        assertThat(resultat).hasStatusOk().bodyText()
                .contains("Le code NGA est déjà utilisé par le Bureau de Ngor-Almadies.");
        assertThat(livresFonciers.findByCode("NGA").orElseThrow().getBureau().getNom()).isEqualTo("Bureau de Ngor-Almadies");
        assertThat(journal()).isEmpty();
    }

    @Test
    void unCodeMalFormeEstRefuse() {
        long thies = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();

        for (String code : new String[] {"N1", "ABCDE", "N-A", ""}) {
            assertThat(mvc.post().uri("/admin/bureaux/livres").with(user(compteAdmin)).with(csrf())
                    .param("code", code).param("nom", "Livre").param("idBureau", String.valueOf(thies)))
                    .hasStatusOk().bodyText().contains("Le code doit comporter 1 à 4 lettres.");
        }
        assertThat(livresFonciers.count()).isEqualTo(7);
        assertThat(journal()).isEmpty();
    }

    @Test
    void unLivreSansBureauEstRefuse() {
        assertThat(mvc.post().uri("/admin/bureaux/livres").with(user(compteAdmin)).with(csrf())
                .param("code", "TB").param("nom", "Tivaouane").param("idBureau", "999999"))
                .hasStatusOk().bodyText().contains("Choisissez le bureau qui tient ce livre.");
        assertThat(livresFonciers.findByCode("TB")).isEmpty();
        assertThat(journal()).isEmpty();
    }

    @Test
    void supprimerUnLivreFoncier() {
        long idLivre = livresFonciers.findByCode("TH").orElseThrow().getId();

        MvcTestResult resultat = mvc.post().uri("/admin/bureaux/livres/{id}/suppression", idLivre)
                .with(user(compteAdmin)).with(csrf()).exchange();

        assertThat(redirection(resultat)).isEqualTo("/admin/bureaux");
        assertThat(livresFonciers.findByCode("TH")).isEmpty();
        assertThat(journal()).containsExactly("Suppression du livre foncier TH du Bureau de Thiès");
    }
}
