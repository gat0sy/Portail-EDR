package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.security.CompteUtilisateur;
import sn.portailedr.soumission.ParcoursDeDepot;

/** Page de suivi (m2.html) selon le statut, et liens vers elle. */
class SuiviPageTest extends SuiviTestBase {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).withZone(ZoneId.of("Africa/Dakar"));

    /** Page lue par un visiteur verifie. L'apostrophe echappee par Thymeleaf (&#39;) est remise en clair. */
    private AbstractStringAssert<?> page(Demande demande) {
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());
        var reponse = mvc.get().uri("/suivi/" + demande.getReference()).session(session).exchange();
        assertThat(reponse).hasStatusOk();
        try {
            return assertThat(reponse.getResponse().getContentAsString().replace("&#39;", "'"));
        } catch (java.io.UnsupportedEncodingException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    // ---------------------------------------------------------------- statuts sans paiement ni telechargement

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = {"SOUMISE", "EN_COURS", "REJETEE", "EXPIREE"})
    void niPaiementNiTelechargementAvantDisponible(StatutDemande statut) {
        Demande demande = donnees.particulier(statut);

        page(demande).contains(demande.getReference(), statut.getLibelle(), "Bureau de Dakar-Plateau", "4521/DK")
                .doesNotContain("name=\"modePaiement\"", "/resultat\"", "Télécharger le résultat");
    }

    @Test
    void textesDesStatuts() {
        page(donnees.particulier(StatutDemande.SOUMISE)).contains("qui vérifie la conformité du dossier");
        page(donnees.particulier(StatutDemande.EN_COURS)).contains("Le dossier a été déclaré conforme");
        page(donnees.particulier(StatutDemande.REJETEE)).contains("La demande a été rejetée",
                "déposer une nouvelle demande");
        page(donnees.particulier(StatutDemande.EXPIREE)).contains("Le délai de paiement est dépassé",
                "le résultat n'est plus accessible");
    }

    // ---------------------------------------------------------------- disponible

    @Test
    void disponibleAfficheLesFraisLeDelaiEtLePaiementSimule() {
        Instant limite = Instant.now().plus(Duration.ofDays(7));
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE), limite);

        page(demande).contains("Disponible · paiement requis", "1 000 FCFA", "À régler avant le " + DATE.format(limite))
                .contains("name=\"modePaiement\"", "value=\"WAVE\"", "value=\"ORANGE_MONEY\"", "value=\"CARTE_BANCAIRE\"")
                .contains("Paiement simulé", "aucun montant réel n'est débité")
                .contains("Téléchargeable après paiement")
                .doesNotContain("/resultat\"");
    }

    @Test
    void disponibleDontLeDelaiEstPasseNeProposePlusLePaiement() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE),
                Instant.now().minus(Duration.ofHours(1)));

        page(demande).contains("Le délai de paiement est dépassé").doesNotContain("name=\"modePaiement\"");
    }

    // ---------------------------------------------------------------- payee

    @Test
    void payeeAfficheLePaiementEtLeTelechargement() {
        Instant paiement = Instant.now().minus(Duration.ofDays(2));
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), paiement);

        page(demande).contains("Payée", "Paiement simulé confirmé", "1 000 FCFA réglés par Wave le " + DATE.format(paiement))
                .contains("href=\"/suivi/" + demande.getReference() + "/resultat\"")
                .contains("Téléchargeable jusqu'au " + DATE.format(paiement.plus(Duration.ofDays(15))))
                .contains("État des droits réels")
                .doesNotContain("name=\"modePaiement\"");
    }

    @Test
    void payeeApresLaFenetreNeProposePlusLeTelechargement() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE),
                Instant.now().minus(Duration.ofDays(16)));

        page(demande).contains("Payée", "Le délai de téléchargement est dépassé", "nouvelle demande")
                .doesNotContain("/resultat\"");
    }

    @Test
    void lHistoriqueAfficheLesNotificationsDeLaDemande() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));

        // le code SMS du suivi est une notification de la demande
        page(demande).contains("SMS · Code de vérification");
    }

    // ---------------------------------------------------------------- notaire et liens

    @Test
    void leNotaireVoitLeLienDeRetourASonProfil() {
        Utilisateur compte = comptes.notaire();
        Demande demande = donnees.disponible(donnees.notaire(compte, StatutDemande.SOUMISE));

        assertThat(mvc.get().uri("/suivi/" + demande.getReference()).with(user(new CompteUtilisateur(compte))))
                .hasStatusOk().bodyText().contains("Retour à mon profil", "1 000 FCFA")
                .doesNotContain("Saisir un autre code de suivi");
    }

    @Test
    void leProfilDuNotaireMeneAuSuiviDeSesDemandes() {
        Utilisateur compte = comptes.notaire();
        Demande demande = donnees.notaire(compte, StatutDemande.EN_COURS);

        assertThat(mvc.get().uri("/profil").with(user(new CompteUtilisateur(compte)))).hasStatusOk().bodyText()
                .contains("href=\"/suivi/" + demande.getReference() + "\"");
    }

    @Test
    void laConfirmationDuParticulierMeneALaSaisieDuCode() {
        MockHttpSession session = new MockHttpSession();
        String reference = new ParcoursDeDepot(mvc).deposerParticulier(session);

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").session(session)).hasStatusOk()
                .bodyText().contains("Suivre cette demande", "href=\"/suivi?reference=" + reference + "\"");
        assertThat(mvc.get().uri("/suivi?reference=" + reference)).hasStatusOk().bodyText()
                .contains("value=\"" + reference + "\"");
    }

    @Test
    void laConfirmationDuNotaireMeneDirectementAuSuivi() {
        Utilisateur compte = comptes.notaire();
        CompteUtilisateur notaire = new CompteUtilisateur(compte);
        String reference = new ParcoursDeDepot(mvc).deposerNotaire(new MockHttpSession(), notaire,
                bureaux.findByNom("Bureau de Pikine").orElseThrow().getId());

        assertThat(mvc.get().uri("/demandes/" + reference + "/confirmation").with(user(notaire))).hasStatusOk()
                .bodyText().contains("Suivre cette demande", "href=\"/suivi/" + reference + "\"");
    }
}
