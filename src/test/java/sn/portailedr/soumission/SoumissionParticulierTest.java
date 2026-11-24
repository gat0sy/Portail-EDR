package sn.portailedr.soumission;

import static org.assertj.core.api.Assertions.assertThat;
import static sn.portailedr.soumission.FichiersDeTest.imageDeguisee;
import static sn.portailedr.soumission.FichiersDeTest.jpg;
import static sn.portailedr.soumission.FichiersDeTest.jpgALaLimite;
import static sn.portailedr.soumission.FichiersDeTest.jpgTropGros;
import static sn.portailedr.soumission.FichiersDeTest.pdf;
import static sn.portailedr.soumission.FichiersDeTest.png;
import static sn.portailedr.soumission.ParcoursDeDepot.champsBien;
import static sn.portailedr.soumission.ParcoursDeDepot.champsPersonne;
import static sn.portailedr.soumission.ParcoursDeDepot.codeEnvoye;
import static sn.portailedr.soumission.ParcoursDeDepot.enCours;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.service.FichiersProprietes;
import sn.portailedr.service.PieceEnAttente;

/**
 * Depot d'un particulier : formulaire, pieces, code SMS, enregistrement.
 * Regle centrale : rien n'est ecrit en base avant un code valide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SoumissionParticulierTest {

    private static final String VERIFICATION = "/demandes/nouvelle/verification";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private FichiersProprietes fichiers;

    private ParcoursDeDepot parcours;
    private MockHttpSession session;

    @BeforeEach
    void init() {
        parcours = new ParcoursDeDepot(mvc);
        session = new MockHttpSession();
    }

    private long compter(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    /** Nombre de lignes des tables qu'un depot pourrait remplir. */
    private List<Long> lignesEnBase() {
        return List.of(compter("demande"), compter("piece_jointe"), compter("journal"), compter("notification"));
    }

    private long idBureau(String nom) {
        return bureaux.findByNom(nom).orElseThrow().getId();
    }

    private static void formulaireRefuse(MvcTestResult resultat, String message) {
        assertThat(resultat).hasStatusOk().hasViewName("demande-nouvelle");
        assertThat(resultat).bodyText().contains(html(message));
    }

    /** Thymeleaf echappe l'apostrophe des textes dynamiques : d'identite devient d&#39;identite. */
    private static String html(String texte) {
        return texte.replace("'", "&#39;");
    }

    // ---------------------------------------------------------------- formulaire

    @Test
    void formulaireDuVisiteur() {
        assertThat(mvc.get().uri("/demandes/nouvelle")).hasStatusOk().bodyText()
                .contains("Nouvelle demande d'état des droits réels", "Vérification par SMS")
                .contains("name=\"declarantCni\"", "name=\"cniRecto\"", "name=\"cniVerso\"", "name=\"copieTitreFoncier\"")
                .contains("Bureau de Pikine", "Bureau de Thiès")
                .contains("enctype=\"multipart/form-data\"");
    }

    // ---------------------------------------------------------------- rien avant le code

    @Test
    void rienNEstEcritEnBaseAvantLeCode() {
        List<Long> avant = lignesEnBase();

        assertThat(parcours.bienParticulier(session, "12.405/NGA")).hasRedirectedUrl(VERIFICATION);

        assertThat(lignesEnBase()).isEqualTo(avant);
        assertThat(enCours(session)).isNotNull();
        // les pieces attendent dans la zone temporaire privee
        Path temporaire = fichiers.dossier().resolve("temporaire").toAbsolutePath().normalize();
        assertThat(enCours(session).demande().pieces()).hasSize(3)
                .allSatisfy(piece -> {
                    assertThat(Path.of(piece.fichierTemporaire())).startsWith(temporaire).exists();
                });
    }

    @Test
    void unCodeFauxNEcritRienNonPlus() {
        parcours.bienParticulier(session, "12.405/NGA");
        List<Long> avant = lignesEnBase();

        assertThat(parcours.code(session, autreCode(codeEnvoye(session)))).hasRedirectedUrl(VERIFICATION)
                .flash().containsEntry("erreur", "Code incorrect. Il vous reste 2 essais.");

        assertThat(lignesEnBase()).isEqualTo(avant);
    }

    @Test
    void lePasDeVerificationSansDemandeEnAttenteRenvoieAuFormulaire() {
        assertThat(mvc.get().uri(VERIFICATION).session(session)).hasRedirectedUrl("/demandes/nouvelle");
        assertThat(parcours.code(session, "123456")).hasRedirectedUrl("/demandes/nouvelle");
    }

    @Test
    void laPageDeVerificationRappelleLeNumeroEtLeBureau() {
        parcours.bienParticulier(session, "12.405/NGA");

        assertThat(mvc.get().uri(VERIFICATION).session(session)).hasStatusOk().bodyText()
                .contains("+221 77 000 00 00", "Bureau de Ngor-Almadies", "12405/NGA")
                .contains("aucun SMS réel n'est envoyé")
                .doesNotContain(codeEnvoye(session));
    }

    // ---------------------------------------------------------------- code valide : la demande est creee

    @Test
    void codeValideCreeLaDemandeSoumise() {
        String reference = parcours.deposerParticulier(session);

        Map<String, Object> demande = jdbc.sql("""
                SELECT d.statut, d.type, d.numero_titre_foncier, d.declarant_nom, d.declarant_telephone,
                       d.declarant_cni, d.telephone_verifie, d.id_utilisateur, b.nom AS bureau
                FROM demande d JOIN bureau b ON b.id_bureau = d.id_bureau WHERE d.reference = ?""")
                .param(reference).query().singleRow();
        assertThat(demande).containsEntry("statut", "SOUMISE").containsEntry("type", "BIEN")
                .containsEntry("numero_titre_foncier", "12405/NGA").containsEntry("declarant_nom", "Awa Ndiaye")
                .containsEntry("declarant_telephone", "+221 77 000 00 00")
                .containsEntry("declarant_cni", "1 234 1990 01234").containsEntry("telephone_verifie", true)
                .containsEntry("bureau", "Bureau de Ngor-Almadies");
        assertThat(demande.get("id_utilisateur")).isNull();

        // le code est consomme : la session ne garde plus de demande en attente
        assertThat(enCours(session)).isNull();
    }

    @Test
    void lesPiecesSontEnregistreesAvecLeurType() {
        String reference = parcours.deposerParticulier(session);

        assertThat(jdbc.sql("""
                SELECT p.type FROM piece_jointe p JOIN demande d ON d.id_demande = p.id_demande
                WHERE d.reference = ? ORDER BY p.type""").param(reference).query(String.class).list())
                .containsExactly("CNI_RECTO", "CNI_VERSO", "COPIE_TITRE_FONCIER");
    }

    @Test
    void leJournalEstRattacheALaDemande() {
        String reference = parcours.deposerParticulier(session);

        assertThat(jdbc.sql("""
                SELECT j.action FROM journal j JOIN demande d ON d.id_demande = j.id_demande
                WHERE d.reference = ? AND j.id_utilisateur IS NULL ORDER BY j.id_journal""")
                .param(reference).query(String.class).list())
                .containsExactly("Vérification du numéro de téléphone par SMS", "Soumission de la demande");
    }

    @Test
    void laConfirmationAfficheReferenceBureauEtPieces() {
        String reference = parcours.deposerParticulier(session);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(session)).hasStatusOk()
                .bodyText().contains("Votre demande a bien été soumise", "Votre code de suivi", reference)
                .contains("Sur un bien", "12405/NGA", "Bureau de Ngor-Almadies")
                .contains("Pièces transmises", html("Carte nationale d'identité, recto"),
                        html("Carte nationale d'identité, verso"), "Copie du titre foncier")
                .doesNotContain("Mes dernières demandes");
    }

    // ---------------------------------------------------------------- fichiers : hors de tout dossier servi

    @Test
    void lesFichiersSontStockesSousUnNomGenereHorsDesDossiersServis() {
        String reference = parcours.deposerParticulier(session);
        Path pieces = fichiers.dossier().resolve("pieces").toAbsolutePath().normalize();

        List<String> chemins = jdbc.sql("""
                SELECT p.chemin FROM piece_jointe p JOIN demande d ON d.id_demande = p.id_demande
                WHERE d.reference = ?""").param(reference).query(String.class).list();

        assertThat(chemins).hasSize(3).allSatisfy(chemin -> {
            // nom genere (UUID + extension deduite du contenu), jamais le nom envoye par le navigateur
            assertThat(chemin).matches("[0-9a-f-]{36}\\.(jpg|png|pdf)").doesNotContain("photo-cni", "copie-titre");
            Path fichier = pieces.resolve(chemin);
            assertThat(fichier).exists();
            assertThat(fichier.toString()).doesNotContain("static", "public", "resources", "templates");
            // aucune URL ne sert ce fichier
            int statut = mvc.get().uri("/" + chemin).exchange().getResponse().getStatus();
            assertThat(statut).isNotEqualTo(200);
            statut = mvc.get().uri("/pieces/" + chemin).exchange().getResponse().getStatus();
            assertThat(statut).isNotEqualTo(200);
        });
        // la zone temporaire a ete videe des pieces de cette demande
        assertThat(enCours(session)).isNull();
    }

    @Test
    void leDossierDesFichiersNEstPasDansLesRessourcesDeLApplication() {
        Path dossier = fichiers.dossier().toAbsolutePath().normalize();
        assertThat(dossier.startsWith(Path.of("src").toAbsolutePath())).isFalse();
        assertThat(dossier.startsWith(Path.of("target", "classes").toAbsolutePath())).isFalse();
    }

    @Test
    void unNouveauFormulaireRemplaceLaDemandeEnAttenteEtSupprimeSesFichiers() {
        parcours.bienParticulier(session, "12.405/NGA");
        List<String> anciens = enCours(session).demande().pieces().stream().map(PieceEnAttente::fichierTemporaire)
                .toList();

        parcours.bienParticulier(session, "2082/R");

        assertThat(anciens).allSatisfy(fichier -> assertThat(Path.of(fichier)).doesNotExist());
        assertThat(enCours(session).demande().nomBureau()).isEqualTo("Bureau de Rufisque");
    }

    @Test
    void laFinDeLaSessionSupprimeLesFichiersEnAttente() {
        parcours.bienParticulier(session, "12.405/NGA");
        List<String> enAttente = enCours(session).demande().pieces().stream().map(PieceEnAttente::fichierTemporaire)
                .toList();
        assertThat(enAttente).allSatisfy(fichier -> assertThat(Path.of(fichier)).exists());

        session.invalidate();

        assertThat(enAttente).allSatisfy(fichier -> assertThat(Path.of(fichier)).doesNotExist());
    }

    // ---------------------------------------------------------------- bureau deduit du numero du titre

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "12.405/NGA;     12405/NGA; Bureau de Ngor-Almadies",
        "' 12 405 / nga '; 12405/NGA; Bureau de Ngor-Almadies",
        "2082/R;         2082/R;    Bureau de Rufisque",
        "2322/th;        2322/TH;   Bureau de Thiès",
        "1.234.567/DK;   1234567/DK; Bureau de Dakar-Plateau",
        "123/DP;         123/DP;    Bureau de Pikine",
        "456/GW;         456/GW;    Bureau de Pikine"})
    void leBureauEstDeduitDuLivreFoncier(String saisi, String normalise, String bureau) {
        assertThat(parcours.bienParticulier(session, saisi)).hasRedirectedUrl(VERIFICATION);

        assertThat(enCours(session).demande().numeroTitreFoncier()).isEqualTo(normalise);
        assertThat(enCours(session).demande().idBureau()).isEqualTo(idBureau(bureau));
        assertThat(enCours(session).demande().nomBureau()).isEqualTo(bureau);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12405", "12405/", "/NGA", "ABC/NGA", "12405/NGAXX", "12405-NGA", "12,405/NGA", "12.40/NGA"})
    void numeroMalFormeRefuse(String saisi) {
        List<Long> avant = lignesEnBase();

        formulaireRefuse(parcours.bienParticulier(session, saisi),
                "Numéro de titre foncier invalide. Format attendu : 12.405/NGA");

        assertThat(enCours(session)).isNull();
        assertThat(lignesEnBase()).isEqualTo(avant);
    }

    @Test
    void numeroAbsentRefuse() {
        formulaireRefuse(parcours.bienParticulier(session, "  "), "Indiquez le numéro du titre foncier.");
    }

    @Test
    void livreFoncierInconnuRefuse() {
        // DG (ancien livre de Dakar-Gorée) n'est volontairement pas dans les donnees initiales
        formulaireRefuse(parcours.bienParticulier(session, "24.714/DG"),
                "Livre foncier inconnu. Si votre titre a été reporté dans un autre livre foncier, indiquez son numéro actuel.");
        assertThat(enCours(session)).isNull();
    }

    // ---------------------------------------------------------------- demande sur une personne

    @Test
    void demandeSurUnePersonneAvecLeBureauChoisi() {
        long thies = idBureau("Bureau de Thiès");

        assertThat(parcours.formulaire(session, null, champsPersonne(thies), jpg("cniRecto"), jpg("cniVerso")))
                .hasRedirectedUrl(VERIFICATION);

        assertThat(enCours(session).demande().idBureau()).isEqualTo(thies);
        assertThat(enCours(session).demande().personneConcernee()).isEqualTo("Moustapha Sarr");
        assertThat(enCours(session).demande().numeroTitreFoncier()).isNull();
        // pas de copie du titre pour une personne, meme si un fichier est envoye
        assertThat(enCours(session).demande().pieces()).extracting(PieceEnAttente::type)
                .extracting(Enum::name).containsExactly("CNI_RECTO", "CNI_VERSO");
    }

    @Test
    void demandeSurUnePersonneEnregistree() {
        long thies = idBureau("Bureau de Thiès");
        parcours.formulaire(session, null, champsPersonne(thies), jpg("cniRecto"), jpg("cniVerso"),
                pdf("copieTitreFoncier"));

        String reference = ParcoursDeDepot.reference(parcours.code(session, codeEnvoye(session)));

        assertThat(jdbc.sql("SELECT personne_concernee, numero_titre_foncier, id_bureau FROM demande WHERE reference = ?")
                .param(reference).query().singleRow())
                .containsEntry("personne_concernee", "Moustapha Sarr").containsEntry("numero_titre_foncier", null)
                .containsEntry("id_bureau", thies);
        assertThat(jdbc.sql("SELECT count(*) FROM piece_jointe p JOIN demande d ON d.id_demande = p.id_demande "
                + "WHERE d.reference = ?").param(reference).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void unBureauQuiNExistePasEstRefuse() {
        formulaireRefuse(parcours.formulaire(session, null, champsPersonne(999_999L), jpg("cniRecto"), jpg("cniVerso")),
                "Bureau introuvable.");
        assertThat(enCours(session)).isNull();
    }

    @Test
    void personneSansNomNiBureauRefusee() {
        Map<String, String> champs = champsPersonne(null);
        champs.put("personneConcernee", " ");

        MvcTestResult resultat = parcours.formulaire(session, null, champs, jpg("cniRecto"), jpg("cniVerso"));

        formulaireRefuse(resultat, "Indiquez le nom de la personne concernée.");
        assertThat(resultat).bodyText().contains("Choisissez le bureau de conservation foncière.");
    }

    @Test
    void objetAbsentRefuse() {
        Map<String, String> champs = champsBien("12.405/NGA");
        champs.remove("type");

        formulaireRefuse(parcours.formulaire(session, null, champs, jpg("cniRecto"), jpg("cniVerso")),
                "Choisissez l'objet de la demande.");
    }

    @Test
    void informationsDuDeclarantObligatoires() {
        Map<String, String> champs = champsBien("12.405/NGA");
        champs.put("declarantNom", "");
        champs.put("declarantTelephone", " ");
        champs.remove("declarantCni");

        MvcTestResult resultat = parcours.formulaire(session, null, champs, jpg("cniRecto"), jpg("cniVerso"),
                pdf("copieTitreFoncier"));

        formulaireRefuse(resultat, "Indiquez votre nom complet.");
        assertThat(resultat).bodyText().contains("Indiquez votre numéro de téléphone.",
                html("Indiquez le numéro de votre carte nationale d'identité."));
        assertThat(enCours(session)).isNull();
    }

    @Test
    void laCniEstPresenteeCommeDeclareeJamaisCommeVerifiee() {
        assertThat(mvc.get().uri("/demandes/nouvelle")).bodyText()
                .doesNotContainIgnoringCase("CNI vérifiée").doesNotContainIgnoringCase("identité vérifiée");
    }

    // ---------------------------------------------------------------- pieces

    @Test
    void lesDeuxFacesDeLaCniSontObligatoires() {
        MvcTestResult resultat = parcours.formulaire(session, null, champsBien("12.405/NGA"), pdf("copieTitreFoncier"));

        formulaireRefuse(resultat, "Ajoutez le recto de votre carte nationale d'identité.");
        assertThat(resultat).bodyText().contains(html("Ajoutez le verso de votre carte nationale d'identité."));
    }

    @Test
    void laCopieDuTitreEstObligatoirePourUnBien() {
        formulaireRefuse(parcours.formulaire(session, null, champsBien("12.405/NGA"), jpg("cniRecto"), png("cniVerso")),
                "Ajoutez la copie du titre foncier.");
        assertThat(enCours(session)).isNull();
    }

    @Test
    void unFichierDontLeContenuNEstPasUneImageEstRefuse() {
        formulaireRefuse(parcours.formulaire(session, null, champsBien("12.405/NGA"), imageDeguisee("cniRecto"),
                png("cniVerso"), pdf("copieTitreFoncier")), "Format non accepté : JPG ou PNG uniquement.");
        assertThat(enCours(session)).isNull();
    }

    @Test
    void unPdfNEstPasAcceptePourLaCni() {
        formulaireRefuse(parcours.formulaire(session, null, champsBien("12.405/NGA"), pdf("cniRecto"),
                png("cniVerso"), pdf("copieTitreFoncier")), "Format non accepté : JPG ou PNG uniquement.");
    }

    @Test
    void laCopieDuTitrePeutEtreUnPdfOuUneImage() {
        assertThat(parcours.formulaire(session, null, champsBien("12.405/NGA"), jpg("cniRecto"), png("cniVerso"),
                jpg("copieTitreFoncier"))).hasRedirectedUrl(VERIFICATION);
    }

    @Test
    void laCopieDuTitreDeguiseeEstRefusee() {
        formulaireRefuse(parcours.formulaire(session, null, champsBien("12.405/NGA"), jpg("cniRecto"), png("cniVerso"),
                imageDeguisee("copieTitreFoncier")), "Format non accepté : PDF, JPG ou PNG uniquement.");
    }

    @Test
    void unFichierDePlusDeCinqMoEstRefuse() {
        formulaireRefuse(parcours.formulaire(session, null, champsBien("12.405/NGA"), jpgTropGros("cniRecto"),
                png("cniVerso"), pdf("copieTitreFoncier")), "Fichier trop volumineux : 5 Mo maximum.");
        assertThat(enCours(session)).isNull();
    }

    @Test
    void unFichierDeCinqMoExactementEstAccepte() {
        assertThat(parcours.formulaire(session, null, champsBien("12.405/NGA"), jpgALaLimite("cniRecto"),
                png("cniVerso"), pdf("copieTitreFoncier"))).hasRedirectedUrl(VERIFICATION);
    }

    @Test
    void unFormulaireRefuseNeLaissePasDeFichierTemporaire() throws Exception {
        Path temporaire = fichiers.dossier().resolve("temporaire");
        long avant = nombreDeFichiers(temporaire);

        parcours.formulaire(session, null, champsBien("24.714/DG"), jpg("cniRecto"), png("cniVerso"),
                pdf("copieTitreFoncier"));

        assertThat(nombreDeFichiers(temporaire)).isEqualTo(avant);
    }

    private static long nombreDeFichiers(Path dossier) throws Exception {
        if (!Files.isDirectory(dossier)) {
            return 0;
        }
        try (var fichiers = Files.list(dossier)) {
            return fichiers.count();
        }
    }

    // ---------------------------------------------------------------- code SMS, par HTTP

    @Test
    void troisCodesFauxPuisLeBonCodeEstRefuse() {
        parcours.bienParticulier(session, "12.405/NGA");
        String bon = codeEnvoye(session);
        String faux = autreCode(bon);

        assertThat(parcours.code(session, faux)).flash().containsEntry("erreur", "Code incorrect. Il vous reste 2 essais.");
        assertThat(parcours.code(session, faux)).flash().containsEntry("erreur", "Code incorrect. Il vous reste 1 essai.");
        assertThat(parcours.code(session, faux)).flash()
                .containsEntry("erreur", "Trop d'essais incorrects. Demandez un nouveau code.");
        long avant = compter("demande");

        assertThat(parcours.code(session, bon)).hasRedirectedUrl(VERIFICATION).flash()
                .containsEntry("erreur", "Trop d'essais incorrects. Demandez un nouveau code.");
        assertThat(compter("demande")).isEqualTo(avant);
    }

    @Test
    void leRenvoiImmediatEstRefuse() {
        parcours.bienParticulier(session, "12.405/NGA");
        String code = codeEnvoye(session);

        MvcTestResult resultat = parcours.renvoi(session);

        assertThat(resultat).hasRedirectedUrl(VERIFICATION);
        assertThat((String) resultat.getMvcResult().getFlashMap().get("erreur"))
                .startsWith("Vous pourrez demander un nouveau code dans");
        assertThat(codeEnvoye(session)).isEqualTo(code);
    }

    @Test
    void leCodeEnvoyeASixChiffres() {
        parcours.bienParticulier(session, "12.405/NGA");

        assertThat(codeEnvoye(session)).matches("\\d{6}");
    }

    @Test
    void unCodeQuiNEstPasFaitDeSixChiffresCompteCommeUnEssai() {
        parcours.bienParticulier(session, "12.405/NGA");

        assertThat(parcours.code(session, "abc")).flash()
                .containsEntry("erreur", "Code incorrect. Il vous reste 2 essais.");
    }

    @Test
    void retourAuFormulaireDepuisLaVerificationGardeLesChamps() {
        parcours.bienParticulier(session, "12.405/NGA");

        assertThat(mvc.get().uri("/demandes/nouvelle").session(session)).hasStatusOk().bodyText()
                .contains("value=\"12405/NGA\"", "value=\"Awa Ndiaye\"")
                .contains("Ajoutez à nouveau les pièces");
    }

    @Test
    void laConfirmationDUnAutreVisiteurEst404() {
        String reference = parcours.deposerParticulier(session);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(new MockHttpSession()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    /** Un code de 6 chiffres different du bon. */
    private static String autreCode(String bon) {
        return bon.equals("000000") ? "111111" : "000000";
    }
}
