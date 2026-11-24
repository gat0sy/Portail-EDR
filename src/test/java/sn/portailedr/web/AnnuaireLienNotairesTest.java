package sn.portailedr.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import sn.portailedr.TestcontainersConfiguration;

/** Avec la propriete portail.annuaire.lien-notaires renseignee, le bloc Notaires porte un lien. */
@SpringBootTest(properties = "portail.annuaire.lien-notaires=https://notaires.exemple.sn/annuaire")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnnuaireLienNotairesTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void leBlocNotairesPointeVersLeLienConfigure() {
        assertThat(mvc.get().uri("/annuaire")).bodyText()
                .contains("class=\"lien-notaires\"", "href=\"https://notaires.exemple.sn/annuaire\"");
    }
}
