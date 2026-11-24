package sn.portailedr.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.CompteUtilisateur;

/**
 * Contenu des pages Accueil, Connexion et Mon profil, et de l'en-tete selon le role.
 * L'en-tete et les blocs par role sont decides cote serveur (sec:authorize) :
 * un bloc qui ne concerne pas le role n'est pas envoye du tout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PagesTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private PasswordEncoder encodeur;

    private CompteUtilisateur notaire;
    private CompteUtilisateur conservateur;
    private CompteUtilisateur administrateur;

    @BeforeEach
    void comptes() {
        Bureau dakarPlateau = bureaux.findByNom("Bureau de Dakar-Plateau").orElseThrow();
        notaire = compte("Me Mariama Fall", "m.fall@test.sn", "+221 33 000 01 01", Role.NOTAIRE, null);
        conservateur = compte("Moussa Diop", "m.diop@test.sn", "+221 33 000 00 01", Role.CONSERVATEUR, dakarPlateau);
        administrateur = compte("Aïssatou Ba", "a.ba@test.sn", "+221 33 000 00 00", Role.ADMINISTRATEUR, null);
    }

    private CompteUtilisateur compte(String nom, String email, String telephone, Role role, Bureau bureau) {
        Utilisateur utilisateur = utilisateurs.save(
                new Utilisateur(nom, email, telephone, encodeur.encode("mot-de-passe"), role, bureau));
        return new CompteUtilisateur(utilisateur);
    }

    // ---------------------------------------------------------------- accueil et connexion

    @Test
    void accueilAnonymeAfficheLeBoutonConnexionEtLesServices() {
        assertThat(mvc.get().uri("/")).hasStatusOk().bodyText()
                .contains("Vos démarches,", "Nouvelle demande", "Suivre une demande", "Annuaire")
                .contains("href=\"/connexion\"", "href=\"/demandes/nouvelle\"", "href=\"/suivi\"", "href=\"/annuaire\"")
                .doesNotContain("class=\"account\"");
    }

    @Test
    void aucunePageNeChargeLeScriptDuPrototypeNiLesProfilsDeDemonstration() {
        for (String url : new String[] {"/", "/connexion"}) {
            assertThat(mvc.get().uri(url)).bodyText()
                    .doesNotContain("proto.js")
                    .doesNotContain("profils de démonstration")
                    .doesNotContain("data-for=")
                    .doesNotContain("data-auth=");
        }
        assertThat(mvc.get().uri("/profil").with(user(notaire))).bodyText()
                .doesNotContain("proto.js")
                .doesNotContain("data-for=");
    }

    @Test
    void pageDeConnexionRappelleQueLesComptesSontCreesParLAdministrateur() {
        assertThat(mvc.get().uri("/connexion")).hasStatusOk().bodyText()
                .contains("Espace réservé aux notaires, aux conservateurs et aux administrateurs.")
                .contains("Les comptes sont créés par l'administrateur de la plateforme.")
                .contains("action=\"/connexion\"");
    }

    // ---------------------------------------------------------------- en-tete selon le role

    @Test
    void enTeteDUnUtilisateurConnecteAfficheSonNomEtLaDeconnexion() {
        assertThat(mvc.get().uri("/").with(user(notaire))).bodyText()
                .contains("Me Mariama Fall", ">MF<", "Notaire")
                .contains("action=\"/deconnexion\"")
                .doesNotContain("connexion-btn");
    }

    @Test
    void menuDuNotaire() {
        assertThat(mvc.get().uri("/").with(user(notaire))).bodyText()
                .contains("href=\"/demandes/nouvelle\"")
                .doesNotContain("href=\"/conservateur/demandes\"")
                .doesNotContain("href=\"/admin\"");
    }

    @Test
    void menuDuConservateur() {
        assertThat(mvc.get().uri("/profil").with(user(conservateur))).bodyText()
                .contains("href=\"/conservateur/demandes\"")
                .doesNotContain("href=\"/admin\"");
    }

    @Test
    void menuDeLAdministrateur() {
        assertThat(mvc.get().uri("/profil").with(user(administrateur))).bodyText()
                .contains("href=\"/admin\"")
                .doesNotContain("href=\"/conservateur/demandes\"");
    }

    // ---------------------------------------------------------------- mon profil

    @Test
    void profilDuConservateurAfficheSesInformationsEtSonBureau() {
        assertThat(mvc.get().uri("/profil").with(user(conservateur))).hasStatusOk().bodyText()
                .contains("Moussa Diop", "m.diop@test.sn", "+221 33 000 00 01", "Conservateur")
                .contains("Bureau de Dakar-Plateau")
                .doesNotContain("Mes dernières demandes");
    }

    @Test
    void profilDuNotaireAfficheAucunBureauEtSesDemandes() {
        assertThat(mvc.get().uri("/profil").with(user(notaire))).hasStatusOk().bodyText()
                .contains("Me Mariama Fall", "m.fall@test.sn", "Notaire")
                .contains("Bureau de rattachement", "Aucun")
                .contains("Mes dernières demandes", "Aucune demande pour le moment.");
    }

    @Test
    void profilDeLAdministrateur() {
        assertThat(mvc.get().uri("/profil").with(user(administrateur))).hasStatusOk().bodyText()
                .contains("Aïssatou Ba", "a.ba@test.sn", "Administrateur")
                .doesNotContain("Mes dernières demandes");
    }
}
