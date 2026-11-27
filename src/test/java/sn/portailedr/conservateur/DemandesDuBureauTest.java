package sn.portailedr.conservateur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.TypePiece;

/** Liste du bureau (m3.html), filtre par statut, detail d'une demande et ouverture des pieces. */
class DemandesDuBureauTest extends ConservateurTestBase {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] PDF = "%PDF-1.7\ncopie du titre\n".getBytes(StandardCharsets.US_ASCII);

    private String page(String url) {
        try {
            var reponse = mvc.get().uri(url).with(user(conservateur)).exchange().getResponse();
            assertThat(reponse.getStatus()).isEqualTo(200);
            return reponse.getContentAsString().replace("&#39;", "'");
        } catch (java.io.UnsupportedEncodingException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    // ---------------------------------------------------------------- liste

    @Test
    void laListeEstTrieeDeLaPlusRecenteALaPlusAncienne() {
        Demande ancienne = donnees.particulier(StatutDemande.SOUMISE);
        ancienne.setDateSoumission(Instant.now().minus(Duration.ofDays(20)));
        Demande recente = donnees.particulier(StatutDemande.SOUMISE);
        recente.setDateSoumission(Instant.now().minus(Duration.ofDays(1)));

        String liste = page("/conservateur/demandes");

        assertThat(liste).contains("Demandes du bureau", "Bureau de Dakar-Plateau", "Awa Ndiaye");
        assertThat(liste.indexOf(recente.getReference())).isLessThan(liste.indexOf(ancienne.getReference()));
    }

    @Test
    void leFiltreParStatut() {
        Demande soumise = donnees.particulier(StatutDemande.SOUMISE);
        Demande enCours = donnees.particulier(StatutDemande.EN_COURS);
        Demande rejetee = donnees.particulier(StatutDemande.REJETEE);
        Demande payee = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());

        assertThat(page("/conservateur/demandes?statut=SOUMISE")).contains(soumise.getReference())
                .doesNotContain(enCours.getReference(), rejetee.getReference());
        assertThat(page("/conservateur/demandes?statut=EN_COURS")).contains(enCours.getReference())
                .doesNotContain(soumise.getReference());
        // Cloturees : les statuts finaux (rejetee, payee, expiree), comme dans le prototype
        assertThat(page("/conservateur/demandes?statut=CLOTUREE")).contains(rejetee.getReference(), payee.getReference())
                .doesNotContain(soumise.getReference(), enCours.getReference());
        assertThat(page("/conservateur/demandes")).contains(soumise.getReference(), enCours.getReference(),
                rejetee.getReference(), payee.getReference());
    }

    @Test
    void unFiltreInconnuAfficheTout() {
        Demande soumise = donnees.particulier(StatutDemande.SOUMISE);

        assertThat(page("/conservateur/demandes?statut=N_IMPORTE_QUOI")).contains(soumise.getReference());
    }

    @Test
    void leNotaireEstNommeCommeDemandeur() {
        Demande demande = donnees.notaire(comptes.notaire(), StatutDemande.SOUMISE);

        assertThat(page("/conservateur/demandes")).contains(demande.getReference(), "Me Test Notaire, notaire");
    }

    // ---------------------------------------------------------------- detail

    @Test
    void leDetailMontreLObjetLeDemandeurEtLesPieces() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe recto = piece(demande, TypePiece.CNI_RECTO, "jpg", JPG);
        PieceJointe copie = piece(demande, TypePiece.COPIE_TITRE_FONCIER, "pdf", PDF);

        String detail = page("/conservateur/demandes/" + demande.getReference());

        assertThat(detail).contains(demande.getReference(), "Sur un bien", "4521/DK", "Awa Ndiaye (particulier)")
                .contains("Vérifié par SMS", "1 234 1990 01234", "déclaré, non vérifié")
                .contains("Carte nationale d'identité, recto", "Copie du titre foncier")
                .contains("href=\"/conservateur/demandes/" + demande.getReference() + "/pieces/" + recto.getId() + "\"")
                .contains("href=\"/conservateur/demandes/" + demande.getReference() + "/pieces/" + copie.getId() + "\"")
                .contains("Déclarer le dossier conforme", "Rejeter la demande")
                .doesNotContain("Publier le résultat");
    }

    @Test
    void leDetailDUneDemandeEnCoursProposeLaPublication() {
        Demande demande = donnees.particulier(StatutDemande.EN_COURS);

        assertThat(page("/conservateur/demandes/" + demande.getReference()))
                .contains("Publier le résultat", "name=\"typeResultat\"", "value=\"ETAT_DROITS_REELS\"",
                        "value=\"REQUISITION_NEGATIVE\"", "name=\"fichier\"", "1 000 FCFA")
                .contains("enctype=\"multipart/form-data\"")
                .doesNotContain("Déclarer le dossier conforme");
    }

    @Test
    void uneDemandeDisponibleNeProposePlusDeDecision() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));

        assertThat(page("/conservateur/demandes/" + demande.getReference()))
                .contains("Disponible", "État des droits réels")
                .doesNotContain("Déclarer le dossier conforme", "Rejeter la demande", "name=\"fichier\"");
    }

    @Test
    void leDetailDUnNotaireNAffichePasDeCni() {
        Demande demande = donnees.notaire(comptes.notaire(), StatutDemande.SOUMISE);

        assertThat(page("/conservateur/demandes/" + demande.getReference()))
                .contains("Me Test Notaire, notaire").doesNotContain("Vérifié par SMS", "non vérifié");
    }

    // ---------------------------------------------------------------- ouvrir une piece

    @Test
    void ouvrirUnePhotoLaMontreDansLeNavigateur() throws Exception {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe recto = piece(demande, TypePiece.CNI_RECTO, "jpg", JPG);

        MockHttpServletResponse reponse = mvc.get().uri("/conservateur/demandes/" + demande.getReference()
                + "/pieces/" + recto.getId()).with(user(conservateur)).exchange().getResponse();

        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(reponse.getContentType()).isEqualTo("image/jpeg");
        assertThat(reponse.getHeader("Content-Disposition")).startsWith("inline");
        assertThat(reponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(reponse.getContentAsByteArray()).isEqualTo(JPG);
    }

    @Test
    void ouvrirUnPdfLeTelecharge() throws Exception {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe copie = piece(demande, TypePiece.COPIE_TITRE_FONCIER, "pdf", PDF);

        MockHttpServletResponse reponse = mvc.get().uri("/conservateur/demandes/" + demande.getReference()
                + "/pieces/" + copie.getId()).with(user(conservateur)).exchange().getResponse();

        assertThat(reponse.getContentType()).isEqualTo("application/pdf");
        assertThat(reponse.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"" + demande.getReference() + "-copie-titre-foncier.pdf\"");
        assertThat(reponse.getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void ouvrirUnePieceEstJournalise() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe recto = piece(demande, TypePiece.CNI_RECTO, "jpg", JPG);

        mvc.get().uri("/conservateur/demandes/" + demande.getReference() + "/pieces/" + recto.getId())
                .with(user(conservateur)).exchange();

        assertThat(jdbc.sql("SELECT action FROM journal WHERE id_demande = ? AND id_utilisateur = ?")
                .param(demande.getId()).param(compteConservateur.getId()).query(String.class).list())
                .containsExactly("Consultation de la pièce « Carte nationale d'identité, recto »");
    }

    @Test
    void unCheminEnregistreQuiSortDuDossierEstRefuse() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe piege = pieces.save(new PieceJointe(TypePiece.CNI_RECTO, "x.jpg", "../resultats/x.pdf", demande));

        assertThat(mvc.get().uri("/conservateur/demandes/" + demande.getReference() + "/pieces/" + piege.getId())
                .with(user(conservateur))).hasStatus(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
