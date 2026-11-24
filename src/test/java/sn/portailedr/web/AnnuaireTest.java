package sn.portailedr.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/** Annuaire public : les bureaux et leurs livres fonciers, jamais les donnees des comptes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AnnuaireTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    @Test
    void lAnnuaireEstPublicEtListeLesBureauxAvecLeursLivresFonciers() {
        assertThat(mvc.get().uri("/annuaire")).hasStatusOk().bodyText()
                .contains("Conservations foncières · 6")
                .contains("Bureau de Dakar-Plateau", "Bureau de Grand Dakar", "Bureau de Ngor-Almadies",
                        "Bureau de Pikine", "Bureau de Rufisque", "Bureau de Thiès")
                .contains("+221 33 000 00 04", ">DP<", ">GW<", ">NGA<");
    }

    @Test
    void lAnnuaireNAfficheAucunNotaireNiAucunCompte() {
        utilisateurs.save(new Utilisateur("Me Awa Ndiaye", "a.ndiaye@etude.sn", "+221 77 123 45 67",
                encodeur.encode("x"), Role.NOTAIRE, null));
        utilisateurs.save(new Utilisateur("Coumba Ndour", "c.ndour@conservation.sn", "+221 77 765 43 21",
                encodeur.encode("x"), Role.CONSERVATEUR, bureaux.findByNom("Bureau de Pikine").orElseThrow()));

        assertThat(mvc.get().uri("/annuaire")).bodyText()
                .doesNotContain("Awa Ndiaye", "a.ndiaye@etude.sn", "+221 77 123 45 67")
                .doesNotContain("Coumba Ndour", "c.ndour@conservation.sn", "+221 77 765 43 21");
    }

    @Test
    void sansLienConfigureLeBlocNotairesEstUnTexteSansLien() {
        assertThat(mvc.get().uri("/annuaire")).bodyText()
                .contains("Chambre des notaires du Sénégal")
                .doesNotContain("class=\"lien-notaires\"");
    }

    @Test
    void laRechercheFiltreParNomVilleOuCode() {
        assertThat(mvc.get().uri("/annuaire?q=gw")).bodyText()
                .contains("Bureau de Pikine")
                .doesNotContain("Bureau de Thiès");
        assertThat(mvc.get().uri("/annuaire?q=rufisque")).bodyText()
                .contains("Bureau de Rufisque")
                .doesNotContain("Bureau de Pikine");
    }

    @Test
    void uneRechercheSansResultatLeDit() {
        assertThat(mvc.get().uri("/annuaire?q=kolda")).bodyText().contains("Aucun bureau ne correspond à « kolda ».");
    }

    @Test
    void unBureauAjouteParLAdministrateurApparaitDansLAnnuaire() {
        bureaux.save(new sn.portailedr.domaine.Bureau("Bureau de Kaolack", "Kaolack", "+221 33 000 00 07"));

        assertThat(mvc.get().uri("/annuaire")).bodyText().contains("Bureau de Kaolack", "Conservations foncières · 7");
    }
}
