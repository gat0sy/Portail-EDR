package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Paiement simule : seulement si la demande est DISPONIBLE et avant sa date limite.
 * Paiement CONFIRME, statut PAYEE, notification PAIEMENT et ligne de journal, ensemble.
 */
class PaiementTest extends SuiviTestBase {

    private MockHttpSession ouverte(Demande demande) {
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());
        return session;
    }

    private long paiementsDe(Demande demande) {
        return compter("SELECT count(*) FROM paiement WHERE id_demande = ?", demande.getId());
    }

    @Test
    void paiementAccepte() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = ouverte(demande);

        assertThat(parcours.payer(session, demande.getReference(), "ORANGE_MONEY"))
                .hasRedirectedUrl("/suivi/" + demande.getReference()).flash()
                .containsEntry("message", "Paiement simulé confirmé. Vous pouvez télécharger le résultat.");

        assertThat(statut(demande)).isEqualTo("PAYEE");
        Map<String, Object> paiement = jdbc.sql(
                "SELECT montant, mode_paiement, statut, date_paiement FROM paiement WHERE id_demande = ?")
                .param(demande.getId()).query().singleRow();
        assertThat(paiement).containsEntry("montant", 1000).containsEntry("mode_paiement", "ORANGE_MONEY")
                .containsEntry("statut", "CONFIRME");
        assertThat(paiement.get("date_paiement")).isNotNull();
        assertThat(notifications(demande)).containsExactly("CODE_VERIFICATION", "PAIEMENT");
        assertThat(journal(demande)).containsExactly("Vérification du numéro de téléphone pour le suivi",
                "Paiement simulé de 1 000 FCFA par Orange Money");
    }

    @Test
    void leNotaireProprietairePaieSansCodeSms() {
        Utilisateur compte = comptes.notaire();
        Demande demande = donnees.disponible(donnees.notaire(compte, StatutDemande.SOUMISE));

        assertThat(mvc.post().uri("/suivi/" + demande.getReference() + "/paiement").with(user(new CompteUtilisateur(compte)))
                .with(csrf()).param("modePaiement", "CARTE_BANCAIRE")).hasRedirectedUrl("/suivi/" + demande.getReference());

        assertThat(statut(demande)).isEqualTo("PAYEE");
        assertThat(compter("SELECT count(*) FROM journal WHERE id_demande = ? AND id_utilisateur = ?",
                demande.getId(), compte.getId())).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = StatutDemande.class, names = {"SOUMISE", "EN_COURS", "REJETEE", "EXPIREE"})
    void refuseSiLaDemandeNEstPasDisponible(StatutDemande statutDemande) {
        Demande demande = donnees.particulier(statutDemande);
        MockHttpSession session = ouverte(demande);

        assertThat(parcours.payer(session, demande.getReference(), "WAVE"))
                .hasRedirectedUrl("/suivi/" + demande.getReference()).flash()
                .containsEntry("erreur", "Cette demande ne peut pas être payée : son statut est « "
                        + statutDemande.getLibelle() + " ».");

        assertThat(statut(demande)).isEqualTo(statutDemande.name());
        assertThat(paiementsDe(demande)).isZero();
    }

    @Test
    void refuseApresLaDateLimite() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE),
                Instant.now().minus(Duration.ofMinutes(1)));
        MockHttpSession session = ouverte(demande);

        assertThat(parcours.payer(session, demande.getReference(), "WAVE")).flash()
                .containsEntry("erreur", "Le délai de paiement est dépassé : la demande ne peut plus être réglée.");

        // le passage a EXPIREE est le travail du traitement planifie (tranche 8)
        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
        assertThat(paiementsDe(demande)).isZero();
        assertThat(notifications(demande)).doesNotContain("PAIEMENT");
    }

    @Test
    void unSecondPaiementEstRefuse() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = ouverte(demande);
        parcours.payer(session, demande.getReference(), "WAVE");

        assertThat(parcours.payer(session, demande.getReference(), "WAVE")).flash()
                .containsEntry("erreur", "Cette demande est déjà payée.");

        assertThat(paiementsDe(demande)).isEqualTo(1);
        assertThat(notifications(demande)).containsOnlyOnce("PAIEMENT");
    }

    @Test
    void unMoyenDePaiementEstObligatoire() {
        Demande demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE));
        MockHttpSession session = ouverte(demande);

        assertThat(mvc.post().uri("/suivi/" + demande.getReference() + "/paiement").with(csrf()).session(session))
                .flash().containsEntry("erreur", "Choisissez un moyen de paiement.");
        assertThat(parcours.payer(session, demande.getReference(), "CHEQUE")).flash()
                .containsEntry("erreur", "Choisissez un moyen de paiement.");

        assertThat(statut(demande)).isEqualTo("DISPONIBLE");
    }
}
