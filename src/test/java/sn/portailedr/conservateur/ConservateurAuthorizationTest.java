package sn.portailedr.conservateur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.TypePiece;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Regle 2 : un conservateur ne lit et n'agit que sur les demandes de SON bureau.
 * Ailleurs, 404 (il ne sait pas si la demande existe). Notaire et administrateur : 403 ;
 * visiteur : renvoye a la connexion.
 */
class ConservateurAuthorizationTest extends ConservateurTestBase {

    private static final byte[] PDF = "%PDF-1.7\nresultat signe\n".getBytes(StandardCharsets.US_ASCII);

    private String detail(Demande demande) {
        return "/conservateur/demandes/" + demande.getReference();
    }

    // ---------------------------------------------------------------- autres roles

    @Test
    void visiteurRenvoyeALaConnexion() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);

        for (String url : new String[] {"/conservateur/demandes", detail(demande)}) {
            var resultat = mvc.get().uri(url).exchange();
            assertThat(resultat).hasStatus3xxRedirection();
            assertThat(resultat.getResponse().getRedirectedUrl()).endsWith("/connexion");
        }
        var conformite = mvc.post().uri(detail(demande) + "/conformite").with(csrf()).exchange();
        assertThat(conformite.getResponse().getRedirectedUrl()).endsWith("/connexion");
        assertThat(statut(demande)).isEqualTo("SOUMISE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOTAIRE", "ADMINISTRATEUR"})
    void notaireEtAdministrateurRefuses(String role) {
        UserDetails compte = new CompteUtilisateur("NOTAIRE".equals(role) ? comptes.notaire() : comptes.administrateur());
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe piece = piece(demande, TypePiece.CNI_RECTO, "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThat(mvc.get().uri("/conservateur/demandes").with(user(compte))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri(detail(demande)).with(user(compte))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri(detail(demande) + "/pieces/" + piece.getId()).with(user(compte)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri(detail(demande) + "/conformite").with(user(compte)).with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri(detail(demande) + "/rejet").with(user(compte)).with(csrf()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(statut(demande)).isEqualTo("SOUMISE");
    }

    // ---------------------------------------------------------------- conservateur d'un autre bureau

    @Test
    void laListeNeMontreQueLesDemandesDuBureau() {
        Demande duBureau = donnees.particulier(StatutDemande.SOUMISE);
        Demande autreBureau = donnees.particulier(StatutDemande.SOUMISE);
        autreBureau.setBureau(bureaux.findByNom("Bureau de Pikine").orElseThrow());

        assertThat(mvc.get().uri("/conservateur/demandes").with(user(conservateur))).hasStatusOk().bodyText()
                .contains(duBureau.getReference()).doesNotContain(autreBureau.getReference());
    }

    @Test
    void unConservateurDUnAutreBureauRecoitUn404Partout() {
        CompteUtilisateur pikine = conservateurDePikine();
        Demande soumise = donnees.particulier(StatutDemande.SOUMISE);
        Demande enCours = donnees.particulier(StatutDemande.EN_COURS);
        PieceJointe piece = piece(soumise, TypePiece.CNI_RECTO, "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThat(mvc.get().uri("/conservateur/demandes").with(user(pikine))).hasStatusOk().bodyText()
                .doesNotContain(soumise.getReference());
        assertThat(mvc.get().uri(detail(soumise)).with(user(pikine))).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri(detail(soumise) + "/pieces/" + piece.getId()).with(user(pikine)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri(detail(soumise) + "/conformite").with(user(pikine)).with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri(detail(soumise) + "/rejet").with(user(pikine)).with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri(detail(enCours) + "/publication").multipart()
                .file(new MockMultipartFile("fichier", "resultat.pdf", "application/pdf", PDF))
                .param("typeResultat", "ETAT_DROITS_REELS").with(user(pikine)).with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(statut(soumise)).isEqualTo("SOUMISE");
        assertThat(statut(enCours)).isEqualTo("EN_COURS");
        assertThat(journal(soumise)).isEmpty();
        assertThat(journal(enCours)).isEmpty();
        assertThat(lignes("resultat", enCours)).isZero();
    }

    @Test
    void uneReferenceInconnueEst404() {
        assertThat(mvc.get().uri("/conservateur/demandes/EDR-2026-ZZZZZZ").with(user(conservateur)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/conservateur/demandes/EDR-2026-ZZZZZZ/conformite").with(user(conservateur))
                .with(csrf())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------------------------------------------------------------- pieces

    @Test
    void unePieceDUneAutreDemandeEst404() {
        Demande a = donnees.particulier(StatutDemande.SOUMISE);
        Demande b = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe pieceDeB = piece(b, TypePiece.CNI_RECTO, "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThat(mvc.get().uri(detail(a) + "/pieces/" + pieceDeB.getId()).with(user(conservateur)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(journal(a)).isEmpty();
        assertThat(journal(b)).isEmpty();
    }

    @Test
    void unePieceInconnueEst404() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);

        assertThat(mvc.get().uri(detail(demande) + "/pieces/999999999").with(user(conservateur)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void leConservateurDuBureauOuvreLaDemandeEtSesPieces() {
        Demande demande = donnees.particulier(StatutDemande.SOUMISE);
        PieceJointe piece = piece(demande, TypePiece.CNI_RECTO, "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThat(mvc.get().uri(detail(demande)).with(user(conservateur))).hasStatusOk();
        assertThat(mvc.get().uri(detail(demande) + "/pieces/" + piece.getId()).with(user(conservateur))).hasStatusOk();
    }
}
