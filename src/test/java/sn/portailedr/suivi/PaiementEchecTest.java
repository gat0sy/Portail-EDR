package sn.portailedr.suivi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.Paiement;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.StatutPaiement;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.PaiementRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.TarifRepository;
import sn.portailedr.service.FichiersProprietes;
import sn.portailedr.service.GenerateurReference;
import sn.portailedr.service.PaiementService;
import sn.portailedr.service.VerificationsSuivi;

/**
 * Un paiement qui echoue (le prestataire repond ECHEC) ne change rien : la demande reste DISPONIBLE,
 * sans paiement, notification ni journal. Le faux prestataire est remplace par un prestataire qui
 * refuse toujours (@Primary), sans toucher au code metier.
 *
 * Cette classe n'est PAS @Transactional : la transaction du service est la vraie, et son annulation
 * se verifie en base. Les donnees creees sont supprimees apres chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PaiementEchecTest.PrestataireQuiRefuse.class})
class PaiementEchecTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PrestataireQuiRefuse {

        /** Comme le vrai : enregistre le paiement INITIE, puis le prestataire repond ECHEC. */
        @Bean
        @Primary
        PaiementService paiementQuiEchoue(PaiementRepository paiements) {
            return (demande, montant, mode) -> {
                Paiement paiement = paiements.save(new Paiement(montant, mode, StatutPaiement.INITIE, Instant.now(), demande));
                paiement.setStatut(StatutPaiement.ECHEC);
                return paiement;
            };
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DemandeRepository demandes;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private TarifRepository tarifs;

    @Autowired
    private ResultatRepository resultats;

    @Autowired
    private PaiementRepository paiements;

    @Autowired
    private GenerateurReference references;

    @Autowired
    private FichiersProprietes fichiers;

    @Autowired
    private VerificationsSuivi verifications;

    private Demande demande;

    @AfterEach
    void nettoyer() {
        if (demande != null) {
            for (String table : new String[] {"journal", "notification", "paiement", "resultat", "piece_jointe", "demande"}) {
                jdbc.sql("DELETE FROM " + table + " WHERE id_demande = ?").param(demande.getId()).update();
            }
        }
    }

    @Test
    void unPaiementEnEchecEstAnnuleEtLaDemandeResteDisponible() {
        DemandesDeTest donnees = new DemandesDeTest(demandes, bureaux, tarifs, resultats, paiements, references, fichiers);
        demande = donnees.disponible(donnees.particulier(StatutDemande.SOUMISE), Instant.now().plus(Duration.ofDays(7)));
        ParcoursDeSuivi parcours = new ParcoursDeSuivi(mvc, verifications);
        MockHttpSession session = new MockHttpSession();
        parcours.ouvrir(session, demande.getReference());
        long journalAvant = jdbc.sql("SELECT count(*) FROM journal WHERE id_demande = ?").param(demande.getId())
                .query(Long.class).single();

        assertThat(mvc.post().uri("/suivi/" + demande.getReference() + "/paiement").with(csrf()).session(session)
                .param("modePaiement", "WAVE")).hasRedirectedUrl("/suivi/" + demande.getReference()).flash()
                .containsEntry("erreur", "Le paiement simulé n'a pas abouti. La demande reste à régler : vous pouvez réessayer.");

        assertThat(jdbc.sql("SELECT statut FROM demande WHERE id_demande = ?").param(demande.getId())
                .query(String.class).single()).isEqualTo("DISPONIBLE");
        // la ligne INITIE ecrite par le prestataire a ete annulee avec le reste
        assertThat(jdbc.sql("SELECT count(*) FROM paiement WHERE id_demande = ?").param(demande.getId())
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM notification WHERE id_demande = ? AND type = 'PAIEMENT'")
                .param(demande.getId()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM journal WHERE id_demande = ?").param(demande.getId())
                .query(Long.class).single()).isEqualTo(journalAvant);
    }
}
