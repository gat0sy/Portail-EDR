package sn.portailedr.conservateur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.suivi.ParcoursDeSuivi;

/**
 * Decisions du conservateur : conformite et rejet (SOUMISE seulement), publication (EN_COURS seulement).
 * Chacune en une transaction, avec une ligne de journal ; refusee sans rien ecrire si le statut ne convient pas.
 */
class DecisionsTest extends ConservateurTestBase {

    private static final byte[] PDF_SIGNE = "%PDF-1.7\netat des droits reels signe\n".getBytes(StandardCharsets.US_ASCII);

    private String url(Demande demande, String action) {
        return "/conservateur/demandes/" + demande.getReference() + "/" + action;
    }

    private MvcTestResult decider(Demande demande, String action) {
        return mvc.post().uri(url(demande, action)).with(user(conservateur)).with(csrf()).exchange();
    }

    private MvcTestResult publier(Demande demande, String typeResultat, MockMultipartFile fichier) {
        var requete = mvc.post().uri(url(demande, "publication")).multipart();
        if (fichier != null) {
            requete.file(fichier);
        }
        if (typeResultat != null) {
            requete.param("typeResultat", typeResultat);
        }
        return requete.with(user(conservateur)).with(csrf()).exchange();
    }

    private static MockMultipartFile pdf(byte[] contenu) {
        return new MockMultipartFile("fichier", "resultat-signe.pdf", "application/pdf", contenu);
    }

    private long fichiersDans(String dossier) throws Exception {
        Path chemin = fichiers.dossier().resolve(dossier);
        if (!Files.isDirectory(chemin)) {
            return 0;
        }
        try (var liste = Files.list(chemin)) {
            return liste.count();
        }
    }

    /** Une decision refusee ne laisse aucune trace : ni statut change, ni journal, ni notification, ni resultat. */
    private void rienNAChange(Demande demande, StatutDemande statutAttendu) {
        assertThat(statut(demande)).isEqualTo(statutAttendu.name());
        assertThat(lignes("journal", demande)).isZero();
        assertThat(lignes("notification", demande)).isZero();
        assertThat(lignes("resultat", demande)).isZero();
    }

    // ---------------------------------------------------------------- conformite

    @Test
    void declarerConforme() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);

        assertThat(decider(demande, "conformite")).hasRedirectedUrl("/conservateur/demandes/" + demande.getReference())
                .flash().containsEntry("message", "Dossier déclaré conforme : la demande est en cours.");

        assertThat(statut(demande)).isEqualTo("EN_COURS");
        assertThat(lignes("notification", demande)).isZero();
        assertThat(jdbc.sql("SELECT action FROM journal WHERE id_demande = ? AND id_utilisateur = ?")
                .param(demande.getId()).param(compteConservateur.getId()).query(String.class).list())
                .containsExactly("Dossier déclaré conforme");
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = "SOUMISE", mode = EnumSource.Mode.EXCLUDE)
    void conformiteRefuseeHorsDUneDemandeSoumise(StatutDemande statutDemande) {
        Demande demande = donnees.particulier(statutDemande);

        assertThat(decider(demande, "conformite")).flash().containsEntry("erreur",
                "Seule une demande soumise peut être déclarée conforme. Statut actuel : « "
                        + statutDemande.getLibelle() + " ».");

        rienNAChange(demande, statutDemande);
    }

    // ---------------------------------------------------------------- rejet

    @Test
    void rejeter() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);

        assertThat(decider(demande, "rejet")).flash().containsEntry("message", "Demande rejetée. Le demandeur est notifié.");

        assertThat(statut(demande)).isEqualTo("REJETEE");
        assertThat(jdbc.sql("SELECT type FROM notification WHERE id_demande = ?").param(demande.getId())
                .query(String.class).list()).containsExactly("REJET");
        assertThat(jdbc.sql("SELECT action FROM journal WHERE id_demande = ? AND id_utilisateur = ?")
                .param(demande.getId()).param(compteConservateur.getId()).query(String.class).list())
                .containsExactly("Rejet de la demande : dossier non conforme");
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = "SOUMISE", mode = EnumSource.Mode.EXCLUDE)
    void rejetRefuseApresLaVerificationDeConformite(StatutDemande statutDemande) {
        Demande demande = donnees.particulier(statutDemande);

        assertThat(decider(demande, "rejet")).flash().containsEntry("erreur",
                "Seule une demande soumise peut être rejetée. Statut actuel : « " + statutDemande.getLibelle() + " ».");

        rienNAChange(demande, statutDemande);
    }

    // ---------------------------------------------------------------- publication

    @Test
    void publierLeResultat() throws Exception {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);
        long temporairesAvant = fichiersDans("temporaire");

        assertThat(publier(demande, "ETAT_DROITS_REELS", pdf(PDF_SIGNE))).flash()
                .containsEntry("message", "Résultat publié : la demande est disponible et le demandeur est notifié.");

        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
        Map<String, Object> ligne = jdbc.sql("""
                SELECT t.type_demande, t.montant, d.date_limite_paiement
                FROM demande d JOIN tarif t ON t.id_tarif = d.id_tarif WHERE d.id_demande = ?""")
                .param(demande.getId()).query().singleRow();
        assertThat(ligne).containsEntry("type_demande", "BIEN").containsEntry("montant", 1000);
        OffsetDateTime limite = ((java.sql.Timestamp) ligne.get("date_limite_paiement")).toInstant().atOffset(ZoneOffset.UTC);
        assertThat(limite).isCloseTo(OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));

        Map<String, Object> resultat = jdbc.sql("SELECT type, fichier FROM resultat WHERE id_demande = ?")
                .param(demande.getId()).query().singleRow();
        assertThat(resultat).containsEntry("type", "ETAT_DROITS_REELS");
        String nom = (String) resultat.get("fichier");
        assertThat(nom).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(Files.readAllBytes(fichiers.dossier().resolve("resultats").resolve(nom))).isEqualTo(PDF_SIGNE);
        assertThat(fichiersDans("temporaire")).isEqualTo(temporairesAvant);

        assertThat(jdbc.sql("SELECT type FROM notification WHERE id_demande = ?").param(demande.getId())
                .query(String.class).list()).containsExactly("DISPONIBILITE");
        assertThat(jdbc.sql("SELECT action FROM journal WHERE id_demande = ? AND id_utilisateur = ?")
                .param(demande.getId()).param(compteConservateur.getId()).query(String.class).list())
                .containsExactly("Publication du résultat : État des droits réels, frais de 1 000 FCFA");
    }

    @Test
    void publierUneRequisitionNegativeSurUnePersonne() {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);
        demande.setType(TypeDemande.PERSONNE);
        demande.setNumeroTitreFoncier(null);
        demande.setPersonneConcernee("Moustapha Sarr");

        publier(demande, "REQUISITION_NEGATIVE", pdf(PDF_SIGNE));

        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
        assertThat(jdbc.sql("SELECT t.type_demande FROM demande d JOIN tarif t ON t.id_tarif = d.id_tarif "
                + "WHERE d.id_demande = ?").param(demande.getId()).query(String.class).single()).isEqualTo("PERSONNE");
        assertThat(jdbc.sql("SELECT type FROM resultat WHERE id_demande = ?").param(demande.getId())
                .query(String.class).single()).isEqualTo("REQUISITION_NEGATIVE");
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = "EN_COURS", mode = EnumSource.Mode.EXCLUDE)
    void publicationRefuseeHorsDUneDemandeEnCours(StatutDemande statutDemande) throws Exception {
        Demande demande = donnees.particulier(statutDemande);
        long resultatsAvant = fichiersDans("resultats");

        assertThat(publier(demande, "ETAT_DROITS_REELS", pdf(PDF_SIGNE))).flash().containsEntry("erreur",
                "Le résultat ne peut être publié que pour une demande en cours. Statut actuel : « "
                        + statutDemande.getLibelle() + " ».");

        rienNAChange(demande, statutDemande);
        assertThat(fichiersDans("resultats")).isEqualTo(resultatsAvant);
    }

    @Test
    void publicationRefuseeSiLeFichierNEstPasUnPdf() throws Exception {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);
        long temporairesAvant = fichiersDans("temporaire");
        long resultatsAvant = fichiersDans("resultats");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

        assertThat(publier(demande, "ETAT_DROITS_REELS", new MockMultipartFile("fichier", "resultat.pdf",
                "application/pdf", jpg))).flash().containsEntry("erreur", "Format non accepté : PDF uniquement.");
        assertThat(publier(demande, "ETAT_DROITS_REELS", new MockMultipartFile("fichier", "resultat.pdf",
                "application/pdf", "pas un pdf".getBytes(StandardCharsets.UTF_8)))).flash()
                .containsEntry("erreur", "Format non accepté : PDF uniquement.");

        rienNAChange(demande, StatutDemande.EN_COURS);
        assertThat(fichiersDans("temporaire")).isEqualTo(temporairesAvant);
        assertThat(fichiersDans("resultats")).isEqualTo(resultatsAvant);
    }

    @Test
    void publicationRefuseeAuDelaDeCinqMo() {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);
        byte[] gros = Arrays.copyOf(PDF_SIGNE, 5 * 1024 * 1024 + 1);

        assertThat(publier(demande, "ETAT_DROITS_REELS", pdf(gros))).flash()
                .containsEntry("erreur", "Fichier trop volumineux : 5 Mo maximum.");

        rienNAChange(demande, StatutDemande.EN_COURS);
    }

    @Test
    void publicationRefuseeSansFichierNiType() {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);

        assertThat(publier(demande, "ETAT_DROITS_REELS", null)).flash()
                .containsEntry("erreur", "Ajoutez le document du résultat, signé, au format PDF.");
        assertThat(publier(demande, null, pdf(PDF_SIGNE))).flash()
                .containsEntry("erreur", "Choisissez le type de résultat.");
        assertThat(publier(demande, "AUTRE", pdf(PDF_SIGNE))).flash()
                .containsEntry("erreur", "Choisissez le type de résultat.");

        rienNAChange(demande, StatutDemande.EN_COURS);
    }

    // ---------------------------------------------------------------- de bout en bout avec la tranche 6

    @Test
    void apresPublicationLeParticulierPaieEtTelecharge() throws Exception {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        decider(demande, "conformite");
        publier(demande, "ETAT_DROITS_REELS", pdf(PDF_SIGNE));

        ParcoursDeSuivi suivi = new ParcoursDeSuivi(mvc, verifications);
        MockHttpSession visiteur = new MockHttpSession();
        suivi.ouvrir(visiteur, demande.getReference());
        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).session(visiteur)).hasStatusOk().bodyText()
                .contains("1 000 FCFA", "Disponible · paiement requis");

        assertThat(suivi.payer(visiteur, demande.getReference(), "WAVE")).flash()
                .containsEntry("message", "Paiement simulé confirmé. Vous pouvez télécharger le résultat.");
        assertThat(statut(demande)).isEqualTo("PAYEE");

        var reponse = mvc.get().uri("/suivi/" + demande.getReference() + "/resultat").session(visiteur).exchange()
                .getResponse();
        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(reponse.getContentType()).isEqualTo("application/pdf");
        assertThat(reponse.getContentAsByteArray()).isEqualTo(PDF_SIGNE);

        assertThat(journal(demande)).containsExactly("Dossier déclaré conforme",
                "Publication du résultat : État des droits réels, frais de 1 000 FCFA",
                "Vérification du numéro de téléphone pour le suivi", "Paiement simulé de 1 000 FCFA par Wave",
                "Téléchargement du résultat");
    }
}
