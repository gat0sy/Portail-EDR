package sn.portailedr.suivi;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.PaiementRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.TarifRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;
import sn.portailedr.service.FichiersProprietes;
import sn.portailedr.service.GenerateurReference;
import sn.portailedr.service.VerificationsSuivi;

/** Configuration commune des tests du suivi (un seul contexte Spring pour toutes ces classes). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
abstract class SuiviTestBase {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected DemandeRepository demandes;

    @Autowired
    protected UtilisateurRepository utilisateurs;

    @Autowired
    protected BureauRepository bureaux;

    @Autowired
    protected TarifRepository tarifs;

    @Autowired
    protected ResultatRepository resultats;

    @Autowired
    protected PaiementRepository paiements;

    @Autowired
    protected GenerateurReference references;

    @Autowired
    protected FichiersProprietes fichiers;

    @Autowired
    protected VerificationsSuivi verifications;

    @Autowired
    protected PasswordEncoder encodeur;

    @Autowired
    private EntityManager entityManager;

    protected DemandesDeTest donnees;
    protected ParcoursDeSuivi parcours;
    protected ComptesDeTest comptes;

    @BeforeEach
    void initialiserSuivi() {
        donnees = new DemandesDeTest(demandes, bureaux, tarifs, resultats, paiements, references, fichiers);
        parcours = new ParcoursDeSuivi(mvc, verifications);
        comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
    }

    protected CompteUtilisateur autreNotaire() {
        return new CompteUtilisateur(utilisateurs.save(new Utilisateur("Me Autre Notaire", "autre.notaire@test.sn",
                "+221 77 000 00 02", encodeur.encode(ComptesDeTest.MOT_DE_PASSE), Role.NOTAIRE, null)));
    }

    /**
     * Les lectures SQL ci-dessous voient la base, pas les entites modifiees en memoire : dans un test
     * @Transactional, rien n'est valide (commit) a la fin de la requete. On ecrit donc d'abord en base.
     */
    private void ecrireEnBase() {
        entityManager.flush();
    }

    protected String statut(Demande demande) {
        ecrireEnBase();
        return jdbc.sql("SELECT statut FROM demande WHERE id_demande = ?").param(demande.getId())
                .query(String.class).single();
    }

    protected long compter(String sql, Object... parametres) {
        ecrireEnBase();
        var requete = jdbc.sql(sql);
        for (Object parametre : parametres) {
            requete = requete.param(parametre);
        }
        return requete.query(Long.class).single();
    }

    protected List<String> journal(Demande demande) {
        ecrireEnBase();
        return jdbc.sql("SELECT action FROM journal WHERE id_demande = ? ORDER BY id_journal").param(demande.getId())
                .query(String.class).list();
    }

    protected List<String> notifications(Demande demande) {
        ecrireEnBase();
        return jdbc.sql("SELECT type FROM notification WHERE id_demande = ? ORDER BY id_notification")
                .param(demande.getId()).query(String.class).list();
    }
}
