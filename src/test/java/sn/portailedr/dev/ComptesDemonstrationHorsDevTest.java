package sn.portailedr.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.repository.UtilisateurRepository;

/** Sans le profil dev (cas de la production), aucun compte de demonstration n'existe. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ComptesDemonstrationHorsDevTest {

    @Autowired
    private ApplicationContext contexte;

    @Autowired
    private Environment environnement;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Test
    void leComposantNEstPasCreeHorsDuProfilDev() {
        assertThat(environnement.getActiveProfiles()).doesNotContain("dev");
        assertThat(contexte.getBeansOfType(ComptesDemonstration.class)).isEmpty();
    }

    @Test
    void aucunCompteDeDemonstrationEnBase() {
        assertThat(utilisateurs.findByEmailIgnoreCase("m.fall@exemple.sn")).isEmpty();
        assertThat(utilisateurs.findByEmailIgnoreCase("m.diop@exemple.sn")).isEmpty();
        assertThat(utilisateurs.findByEmailIgnoreCase("a.ba@exemple.sn")).isEmpty();
    }
}
