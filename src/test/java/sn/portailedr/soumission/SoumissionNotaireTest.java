package sn.portailedr.soumission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static sn.portailedr.soumission.FichiersDeTest.jpg;
import static sn.portailedr.soumission.FichiersDeTest.pdf;
import static sn.portailedr.soumission.FichiersDeTest.png;
import static sn.portailedr.soumission.ParcoursDeDepot.champsBien;
import static sn.portailedr.soumission.ParcoursDeDepot.champsPersonne;
import static sn.portailedr.soumission.ParcoursDeDepot.enCours;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Depot d'un notaire connecte : meme formulaire, sans CNI ni code SMS.
 * Nom et telephone du declarant viennent du compte ; la demande est rattachee au compte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SoumissionNotaireTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    private ParcoursDeDepot parcours;
    private MockHttpSession session;
    private Utilisateur compte;
    private CompteUtilisateur notaire;

    @BeforeEach
    void init() {
        parcours = new ParcoursDeDepot(mvc);
        session = new MockHttpSession();
        compte = new ComptesDeTest(utilisateurs, bureaux, encodeur).notaire();
        notaire = new CompteUtilisateur(compte);
    }

    private Map<String, Object> demande(String reference) {
        return jdbc.sql("""
                SELECT declarant_nom, declarant_telephone, declarant_cni, id_utilisateur, telephone_verifie, statut
                FROM demande WHERE reference = ?""").param(reference).query().singleRow();
    }

    private long nombreDePieces(String reference) {
        return jdbc.sql("SELECT count(*) FROM piece_jointe p JOIN demande d ON d.id_demande = p.id_demande "
                + "WHERE d.reference = ?").param(reference).query(Long.class).single();
    }

    @Test
    void formulaireDuNotaireSansCniNiSms() {
        assertThat(mvc.get().uri("/demandes/nouvelle").with(user(notaire))).hasStatusOk().bodyText()
                .contains("Demandeur", "Me Test Notaire", "Soumettre la demande", "name=\"copieTitreFoncier\"")
                .doesNotContain("name=\"cniRecto\"", "name=\"cniVerso\"", "name=\"declarantCni\"")
                .doesNotContain("Vérification par SMS");
    }

    @Test
    void demandeSurUnBienEnregistreeSansCodeSms() {
        MvcTestResult resultat = parcours.formulaire(session, notaire, champsBien("2082/R"), pdf("copieTitreFoncier"));

        String reference = ParcoursDeDepot.reference(resultat);
        assertThat(enCours(session)).isNull();
        assertThat(demande(reference)).containsEntry("statut", "SOUMISE")
                .containsEntry("declarant_nom", "Me Test Notaire").containsEntry("declarant_telephone", "+221 77 000 00 00")
                .containsEntry("declarant_cni", null).containsEntry("id_utilisateur", compte.getId())
                .containsEntry("telephone_verifie", false);
        assertThat(nombreDePieces(reference)).isEqualTo(1);
    }

    @Test
    void lesChampsDuDeclarantEnvoyesSontIgnores() {
        Map<String, String> champs = champsBien("2082/R");
        champs.put("declarantNom", "Quelqu'un d'autre");
        champs.put("declarantTelephone", "+221 70 999 99 99");

        String reference = ParcoursDeDepot.reference(parcours.formulaire(session, notaire, champs,
                pdf("copieTitreFoncier"), jpg("cniRecto"), png("cniVerso")));

        assertThat(demande(reference)).containsEntry("declarant_nom", "Me Test Notaire")
                .containsEntry("declarant_telephone", "+221 77 000 00 00").containsEntry("declarant_cni", null);
        // aucune photo de CNI n'est gardee pour un notaire, meme envoyee
        assertThat(jdbc.sql("SELECT p.type FROM piece_jointe p JOIN demande d ON d.id_demande = p.id_demande "
                + "WHERE d.reference = ?").param(reference).query(String.class).list())
                .containsExactly("COPIE_TITRE_FONCIER");
    }

    @Test
    void demandeSurUnePersonneSansAucunePiece() {
        long pikine = bureaux.findByNom("Bureau de Pikine").orElseThrow().getId();

        String reference = parcours.deposerNotaire(session, notaire, pikine);

        assertThat(nombreDePieces(reference)).isZero();
    }

    @Test
    void laCopieDuTitreResteObligatoirePourUnBien() {
        long avant = jdbc.sql("SELECT count(*) FROM demande").query(Long.class).single();

        MvcTestResult resultat = parcours.formulaire(session, notaire, champsBien("2082/R"));

        assertThat(resultat).hasStatusOk().bodyText().contains("Ajoutez la copie du titre foncier.");
        assertThat(jdbc.sql("SELECT count(*) FROM demande").query(Long.class).single()).isEqualTo(avant);
    }

    @Test
    void leJournalEstRattacheAuNotaireEtALaDemande() {
        String reference = ParcoursDeDepot.reference(
                parcours.formulaire(session, notaire, champsBien("2082/R"), pdf("copieTitreFoncier")));

        assertThat(jdbc.sql("""
                SELECT j.action FROM journal j JOIN demande d ON d.id_demande = j.id_demande
                WHERE d.reference = ? AND j.id_utilisateur = ?""").param(reference).param(compte.getId())
                .query(String.class).list()).containsExactly("Soumission de la demande");
    }

    @Test
    void laConfirmationDuNotaire() {
        String reference = ParcoursDeDepot.reference(
                parcours.formulaire(session, notaire, champsBien("2082/R"), pdf("copieTitreFoncier")));

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(notaire))).hasStatusOk()
                .bodyText().contains("Référence de la demande", reference, "Bureau de Rufisque", "Copie du titre foncier")
                .contains("Retour à mon profil", "Mes dernières demandes")
                .doesNotContain("Votre code de suivi");
    }

    @Test
    void leProfilListeSesDernieresDemandes() {
        long pikine = bureaux.findByNom("Bureau de Pikine").orElseThrow().getId();
        String surUnBien = ParcoursDeDepot.reference(
                parcours.formulaire(session, notaire, champsBien("2082/R"), pdf("copieTitreFoncier")));
        String surUnePersonne = parcours.deposerNotaire(session, notaire, pikine);

        assertThat(mvc.get().uri("/profil").with(user(notaire))).hasStatusOk().bodyText()
                .contains("Mes dernières demandes", surUnBien, surUnePersonne)
                .contains("Bien · 2082/R", "Personne", "Soumise")
                .doesNotContain("Aucune demande pour le moment.")
                // tranche 6 : chaque demande mene a son suivi
                .contains("href=\"/suivi/" + surUnBien + "\"", "href=\"/suivi/" + surUnePersonne + "\"");
    }

    @Test
    void leProfilNeMontrePasLesDemandesDUnAutreNotaire() {
        long pikine = bureaux.findByNom("Bureau de Pikine").orElseThrow().getId();
        String reference = parcours.deposerNotaire(session, notaire, pikine);
        Utilisateur autre = utilisateurs.save(new Utilisateur("Me Autre", "autre@test.sn", "+221 77 000 00 03",
                encodeur.encode(ComptesDeTest.MOT_DE_PASSE), sn.portailedr.domaine.Role.NOTAIRE, null));

        assertThat(mvc.get().uri("/profil").with(user(new CompteUtilisateur(autre)))).hasStatusOk().bodyText()
                .doesNotContain(reference).contains("Aucune demande pour le moment.");
    }
}
