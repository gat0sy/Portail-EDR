package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Telechargement du resultat : demande PAYEE, dans les 15 jours qui suivent le paiement,
 * par le visiteur verifie pour CETTE reference ou par le notaire proprietaire.
 */
class TelechargementTest extends SuiviTestBase {

    private MockHttpSession ouverte(Demande demande) {
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());
        return session;
    }

    private String resultat(Demande demande) {
        return "/suivi/" + demande.getReference() + "/resultat";
    }

    @Test
    void telechargementDansLaFenetre() throws Exception {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now().minus(Duration.ofDays(3)));
        MockHttpSession session = ouverte(demande);

        MockHttpServletResponse reponse = mvc.get().uri(resultat(demande)).session(session).exchange().getResponse();

        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(reponse.getContentType()).isEqualTo("application/pdf");
        assertThat(reponse.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"" + demande.getReference() + ".pdf\"");
        assertThat(reponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(reponse.getContentAsByteArray()).isEqualTo(DemandesDeTest.CONTENU_RESULTAT);
    }

    @Test
    void leTelechargementEstJournalise() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());
        MockHttpSession session = ouverte(demande);

        mvc.get().uri(resultat(demande)).session(session).exchange();

        assertThat(journal(demande)).containsExactly("Vérification du numéro de téléphone pour le suivi",
                "Téléchargement du résultat");
    }

    @Test
    void leNotaireProprietaireTelechargeEtLeJournalLeNomme() {
        Utilisateur compte = comptes.notaire();
        Demande demande = donnees.payee(donnees.notaire(compte, StatutDemande.SOUMISE), Instant.now());

        assertThat(mvc.get().uri(resultat(demande)).with(user(new CompteUtilisateur(compte)))).hasStatusOk();

        assertThat(compter("SELECT count(*) FROM journal WHERE id_demande = ? AND id_utilisateur = ? AND action = ?",
                demande.getId(), compte.getId(), "Téléchargement du résultat")).isEqualTo(1);
    }

    @Test
    void refuseAvantLePaiement() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = ouverte(demande);

        assertThat(mvc.get().uri(resultat(demande)).session(session)).hasRedirectedUrl("/suivi/" + demande.getReference())
                .flash().containsEntry("erreur", "Le résultat n'est téléchargeable qu'après le paiement.");
        assertThat(journal(demande)).doesNotContain("Téléchargement du résultat");
    }

    @Test
    void refuseApresLaFenetreEtLaDemandeRestePayee() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE),
                Instant.now().minus(Duration.ofDays(15)).minus(Duration.ofMinutes(1)));
        MockHttpSession session = ouverte(demande);

        assertThat(mvc.get().uri(resultat(demande)).session(session)).hasRedirectedUrl("/suivi/" + demande.getReference())
                .flash().containsEntry("erreur", "Le délai de téléchargement de 15 jours est dépassé. Pour obtenir un "
                        + "état des droits réels à jour, déposez une nouvelle demande.");
        assertThat(statut(demande)).isEqualTo("PAYEE");
        assertThat(journal(demande)).doesNotContain("Téléchargement du résultat");
    }

    @Test
    void accepteJusteAvantLaFinDeLaFenetre() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE),
                Instant.now().minus(Duration.ofDays(15)).plus(Duration.ofMinutes(5)));

        assertThat(mvc.get().uri(resultat(demande)).session(ouverte(demande))).hasStatusOk();
    }

    @Test
    void refusePourUneAutreReference() {
        Demande a = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());
        Demande b = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());
        MockHttpSession session = ouverte(a);

        assertThat(mvc.get().uri(resultat(b)).session(session)).hasRedirectedUrl("/suivi");
        assertThat(journal(b)).isEmpty();
    }

    @Test
    void refuseSansVerification() {
        Demande demande = donnees.payee(donnees.particulier(StatutDemande.SOUMISE), Instant.now());

        assertThat(mvc.get().uri(resultat(demande))).hasRedirectedUrl("/suivi");
    }
}
