package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * La ligne de journal fait partie de la transaction de l'action : si l'action est annulee,
 * la ligne l'est aussi. Ce test n'est volontairement PAS @Transactional : il observe ce qui
 * reste vraiment en base apres l'annulation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JournalTransactionTest {

    @Autowired
    private JournalService journalService;

    @Autowired
    private BureauAdministrationService bureauService;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    private long lignesDeJournal() {
        return jdbc.sql("SELECT count(*) FROM journal").query(Long.class).single();
    }

    @Test
    void uneActionAnnuleeApresLEcritureDuJournalNeLaisseAucuneLigne() {
        long avant = lignesDeJournal();

        assertThatThrownBy(() -> transaction.executeWithoutResult(statut -> {
            Utilisateur admin = utilisateurs.save(new Utilisateur("Admin annule", "annule@test.sn", "+221 77 000 00 00",
                    "x", Role.ADMINISTRATEUR, null));
            journalService.journaliser("Action annulee", admin, null);
            throw new RegleMetierException("Regle violee apres l'ecriture du journal");
        })).isInstanceOf(RegleMetierException.class);

        assertThat(lignesDeJournal()).isEqualTo(avant);
        assertThat(utilisateurs.findByEmailIgnoreCase("annule@test.sn")).isEmpty();
    }

    @Test
    void uneActionRefuseeParLeServiceNeLaissePasDeLigne() {
        long avant = lignesDeJournal();
        long thies = bureaux.findByNom("Bureau de Thiès").orElseThrow().getId();
        transaction.executeWithoutResult(statut -> utilisateurs.save(new Utilisateur("Admin refuse", "refuse@test.sn",
                "+221 77 000 00 00", "x", Role.ADMINISTRATEUR, null)));
        try {
            assertThatThrownBy(() -> bureauService.supprimerBureau(thies, "refuse@test.sn"))
                    .isInstanceOf(RegleMetierException.class);

            assertThat(lignesDeJournal()).isEqualTo(avant);
            assertThat(bureaux.findById(thies)).isPresent();
        } finally {
            // ce test a enregistre un compte hors transaction de test : on le retire
            transaction.executeWithoutResult(statut -> utilisateurs.delete(
                    utilisateurs.findByEmailIgnoreCase("refuse@test.sn").orElseThrow()));
        }
    }

    @Test
    void leJournalNePeutPasEtreEcritHorsDUneTransaction() {
        // Propagation.MANDATORY : une ligne de journal appartient toujours a l'action qui l'ecrit
        assertThatThrownBy(() -> journalService.journaliser("Hors transaction", null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
