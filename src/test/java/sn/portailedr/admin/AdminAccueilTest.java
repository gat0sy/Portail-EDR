package sn.portailedr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;

/** Tableau de bord : chiffres lus en base et dernieres lignes du journal. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminAccueilTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    @Test
    void leTableauDeBordAfficheLesChiffresEtLesDernieresOperations() {
        ComptesDeTest comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        CompteUtilisateur admin = new CompteUtilisateur(comptes.administrateur());
        comptes.notaire();
        comptes.conservateur();
        mvc.post().uri("/admin/bureaux").with(user(admin)).with(csrf())
                .param("nom", "Bureau de Kaolack").param("adresse", "Kaolack").param("telephone", "+221 33 000 00 07")
                .exchange();

        assertThat(mvc.get().uri("/admin").with(user(admin))).hasStatusOk().bodyText()
                .contains("<small>Bureaux de conservation</small><b>7</b>")
                .contains("<small>Conservateurs</small><b>1</b>")
                .contains("<small>Notaires</small><b>1</b>")
                .contains("<small>Demandes en cours</small><b>0</b>")
                .contains("Création du bureau Bureau de Kaolack", "Test Administrateur", "Aucune");
    }
}
