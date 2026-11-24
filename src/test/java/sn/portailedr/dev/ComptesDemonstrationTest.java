package sn.portailedr.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Comptes de demonstration du profil dev. Le composant est appele directement
 * (sans le profil dev) pour que les comptes soient annules a la fin du test
 * et ne restent pas dans la base partagee par les autres tests.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ComptesDemonstrationTest {

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    private final PasswordEncoder encodeur = new BCryptPasswordEncoder();

    private ComptesDemonstration comptesDemonstration;

    @BeforeEach
    void init() {
        comptesDemonstration = new ComptesDemonstration(utilisateurs, bureaux, encodeur, "mot-de-passe-demo");
    }

    @Test
    void unCompteActifParRoleEstCree() {
        comptesDemonstration.run(null);

        assertThat(utilisateurs.findAll())
                .extracting(Utilisateur::getEmail, Utilisateur::getRole, Utilisateur::isActif)
                .containsExactlyInAnyOrder(
                        tuple("m.fall@exemple.sn", Role.NOTAIRE, true),
                        tuple("m.diop@exemple.sn", Role.CONSERVATEUR, true),
                        tuple("a.ba@exemple.sn", Role.ADMINISTRATEUR, true));
    }

    @Test
    void leConservateurEstAffecteAuBureauDeDakarPlateau() {
        comptesDemonstration.run(null);

        Utilisateur conservateur = utilisateurs.findByEmailIgnoreCase("m.diop@exemple.sn").orElseThrow();
        assertThat(conservateur.getBureau().getNom()).isEqualTo("Bureau de Dakar-Plateau");
    }

    @Test
    void leMotDePasseEstStockeEnEmpreinteBcrypt() {
        comptesDemonstration.run(null);

        String empreinte = utilisateurs.findByEmailIgnoreCase("a.ba@exemple.sn").orElseThrow().getMotDePasse();
        assertThat(empreinte).startsWith("$2").isNotEqualTo("mot-de-passe-demo");
        assertThat(encodeur.matches("mot-de-passe-demo", empreinte)).isTrue();
    }

    @Test
    void unSecondDemarrageNeCreePasDeDoublon() {
        comptesDemonstration.run(null);
        comptesDemonstration.run(null);

        assertThat(utilisateurs.count()).isEqualTo(3);
    }
}
