package sn.portailedr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.service.ProfilAdministrationService;
import sn.portailedr.service.ProfilFormulaire;
import sn.portailedr.service.RegleMetierException;

/**
 * Le dernier administrateur actif ne peut etre ni desactive ni prive de son role,
 * sinon plus personne ne pourrait administrer la plateforme.
 *
 * Teste au niveau du service : par HTTP, ce cas ne peut pas se produire. La personne qui agit
 * est elle-meme un administrateur actif (sinon CompteActifFilter la deconnecte) et elle ne peut
 * ni se desactiver ni changer son propre role : il reste donc toujours un autre administrateur actif.
 * Ici, l'auteur de l'action est un administrateur DESACTIVE, pour que la cible soit le dernier actif.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DernierAdministrateurTest {

    @Autowired
    private ProfilAdministrationService service;

    @Autowired
    private UtilisateurRepository utilisateurs;

    @Autowired
    private JdbcClient jdbc;

    private Utilisateur dernierActif;
    private Utilisateur auteurDesactive;

    @BeforeEach
    void init() {
        dernierActif = utilisateurs.save(new Utilisateur("Aïssatou Ba", "a.ba@test.sn", "+221 33 000 00 00", "x",
                Role.ADMINISTRATEUR, null));
        auteurDesactive = new Utilisateur("Ancien Administrateur", "ancien@test.sn", "+221 33 000 00 01", "x",
                Role.ADMINISTRATEUR, null);
        auteurDesactive.setActif(false);
        utilisateurs.save(auteurDesactive);
    }

    private long lignesDeJournal() {
        return jdbc.sql("SELECT count(*) FROM journal").query(Long.class).single();
    }

    private ProfilFormulaire formulaire(Role role) {
        ProfilFormulaire formulaire = new ProfilFormulaire();
        formulaire.setNom(dernierActif.getNom());
        formulaire.setEmail(dernierActif.getEmail());
        formulaire.setTelephone(dernierActif.getTelephone());
        formulaire.setRole(role);
        return formulaire;
    }

    @Test
    void leDernierAdministrateurActifNePeutPasEtreDesactive() {
        assertThatThrownBy(() -> service.desactiver(dernierActif.getId(), auteurDesactive.getEmail()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessage("Impossible de désactiver le dernier administrateur actif.");
        assertThat(utilisateurs.findById(dernierActif.getId()).orElseThrow().isActif()).isTrue();
        assertThat(lignesDeJournal()).isZero();
    }

    @Test
    void leDernierAdministrateurActifNePeutPasPerdreSonRole() {
        RegleMetierException refus = catchThrowableOfType(RegleMetierException.class,
                () -> service.modifier(dernierActif.getId(), formulaire(Role.NOTAIRE), auteurDesactive.getEmail()));

        assertThat(refus).hasMessage("Impossible de retirer le rôle du dernier administrateur actif.");
        assertThat(refus.getChamp()).isEqualTo("role");
        assertThat(utilisateurs.findById(dernierActif.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMINISTRATEUR);
        assertThat(lignesDeJournal()).isZero();
    }

    @Test
    void leDernierAdministrateurActifPeutEncoreModifierSesAutresInformations() {
        ProfilFormulaire formulaire = formulaire(Role.ADMINISTRATEUR);
        formulaire.setTelephone("+221 33 000 00 09");

        assertThatCode(() -> service.modifier(dernierActif.getId(), formulaire, auteurDesactive.getEmail()))
                .doesNotThrowAnyException();
        assertThat(utilisateurs.findById(dernierActif.getId()).orElseThrow().getTelephone()).isEqualTo("+221 33 000 00 09");
    }

    @Test
    void unAdministrateurDejaDesactiveNeComptePasCommeActif() {
        // l'auteur desactive n'est pas "actif" : desactiver le dernier actif reste refuse,
        // mais modifier le role d'un administrateur deja desactive est permis
        Utilisateur autreDesactive = new Utilisateur("Autre Ancien", "autre.ancien@test.sn", "+221 33 000 00 02", "x",
                Role.ADMINISTRATEUR, null);
        autreDesactive.setActif(false);
        utilisateurs.save(autreDesactive);
        ProfilFormulaire formulaire = new ProfilFormulaire();
        formulaire.setNom(autreDesactive.getNom());
        formulaire.setEmail(autreDesactive.getEmail());
        formulaire.setTelephone(autreDesactive.getTelephone());
        formulaire.setRole(Role.NOTAIRE);

        assertThatCode(() -> service.modifier(autreDesactive.getId(), formulaire, auteurDesactive.getEmail()))
                .doesNotThrowAnyException();
        assertThat(utilisateurs.findById(autreDesactive.getId()).orElseThrow().getRole()).isEqualTo(Role.NOTAIRE);
    }

    @Test
    void avecDeuxAdministrateursActifsLaDesactivationEstPermise() {
        Utilisateur second = utilisateurs.save(new Utilisateur("Second Administrateur", "second@test.sn",
                "+221 33 000 00 03", "x", Role.ADMINISTRATEUR, null));

        assertThatCode(() -> service.desactiver(dernierActif.getId(), second.getEmail())).doesNotThrowAnyException();
        assertThat(utilisateurs.findById(dernierActif.getId()).orElseThrow().isActif()).isFalse();
    }
}
