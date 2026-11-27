package sn.portailedr.conservateur;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.TypePiece;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.PaiementRepository;
import sn.portailedr.repository.PieceJointeRepository;
import sn.portailedr.repository.ResultatRepository;
import sn.portailedr.repository.TarifRepository;
import sn.portailedr.repository.UtilisateurRepository;
import sn.portailedr.security.ComptesDeTest;
import sn.portailedr.security.CompteUtilisateur;
import sn.portailedr.service.FichiersProprietes;
import sn.portailedr.service.GenerateurReference;
import sn.portailedr.service.VerificationsSuivi;
import sn.portailedr.suivi.DemandesDeTest;

/**
 * Configuration commune des tests de l'espace conservateur.
 * Les demandes de DemandesDeTest sont au Bureau de Dakar-Plateau, celui du conservateur de ComptesDeTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
abstract class ConservateurTestBase {

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
    protected PieceJointeRepository pieces;

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
    protected ComptesDeTest comptes;
    protected Utilisateur compteConservateur;
    protected CompteUtilisateur conservateur;

    @BeforeEach
    void initialiserConservateur() {
        donnees = new DemandesDeTest(demandes, bureaux, tarifs, resultats, paiements, references, fichiers);
        comptes = new ComptesDeTest(utilisateurs, bureaux, encodeur);
        compteConservateur = comptes.conservateur();
        conservateur = new CompteUtilisateur(compteConservateur);
    }

    /** Conservateur d'un AUTRE bureau (Pikine). */
    protected CompteUtilisateur conservateurDePikine() {
        return new CompteUtilisateur(utilisateurs.save(new Utilisateur("Conservateur Pikine", "pikine@test.sn",
                "+221 33 000 00 09", encodeur.encode(ComptesDeTest.MOT_DE_PASSE), Role.CONSERVATEUR,
                bureaux.findByNom("Bureau de Pikine").orElseThrow())));
    }

    /** Piece jointe enregistree et son fichier dans le dossier "pieces". */
    protected PieceJointe piece(Demande demande, TypePiece type, String extension, byte[] contenu) {
        String nom = UUID.randomUUID() + "." + extension;
        try {
            Path dossier = fichiers.dossier().resolve("pieces");
            Files.createDirectories(dossier);
            Files.write(dossier.resolve(nom), contenu);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
        return pieces.save(new PieceJointe(type, "origine." + extension, nom, demande));
    }

    /** Les lectures SQL voient la base : on y ecrit d'abord ce que JPA garde en memoire. */
    protected String statut(Demande demande) {
        entityManager.flush();
        return jdbc.sql("SELECT statut FROM demande WHERE id_demande = ?").param(demande.getId())
                .query(String.class).single();
    }

    protected long lignes(String table, Demande demande) {
        entityManager.flush();
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE id_demande = ?").param(demande.getId())
                .query(Long.class).single();
    }

    protected java.util.List<String> journal(Demande demande) {
        entityManager.flush();
        return jdbc.sql("SELECT action FROM journal WHERE id_demande = ? ORDER BY id_journal").param(demande.getId())
                .query(String.class).list();
    }
}
