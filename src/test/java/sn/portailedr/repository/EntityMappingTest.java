package sn.portailedr.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.PieceJointe;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.domaine.TypePiece;

/**
 * Les entites s'enregistrent et se relisent sur le schema cree par Flyway.
 * (Au demarrage, ddl-auto=validate verifie deja que chaque entite correspond a sa table.)
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class EntityMappingTest {

    @Autowired
    private DemandeRepository demandes;

    @Autowired
    private PieceJointeRepository piecesJointes;

    @Autowired
    private LivreFoncierRepository livresFonciers;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void demandeDUnParticulierEtSesPiecesSontEnregistreesPuisRelues() {
        Demande demande = new Demande();
        demande.setReference("EDR-2026-K7P4QX");
        demande.setType(TypeDemande.BIEN);
        demande.setStatut(StatutDemande.SOUMISE);
        demande.setNumeroTitreFoncier("12405/NGA");
        demande.setDeclarantCni("1751199900123");
        demande.setDeclarantNom("Awa Diop");
        demande.setDeclarantTelephone("+221 77 000 00 00");
        demande.setTelephoneVerifie(true);
        demande.setDateSoumission(Instant.now().truncatedTo(ChronoUnit.MICROS));
        demande.setBureau(livresFonciers.findByCode("NGA").orElseThrow().getBureau());
        demandes.save(demande);
        piecesJointes.save(new PieceJointe(TypePiece.CNI_RECTO, "recto.jpg", "pieces/a1.jpg", demande));
        piecesJointes.save(new PieceJointe(TypePiece.COPIE_TITRE_FONCIER, "titre.pdf", "pieces/a2.pdf", demande));
        entityManager.flush();
        entityManager.clear();

        Demande relue = demandes.findById(demande.getId()).orElseThrow();

        assertThat(relue.getReference()).isEqualTo("EDR-2026-K7P4QX");
        assertThat(relue.getStatut()).isEqualTo(StatutDemande.SOUMISE);
        assertThat(relue.getDateSoumission()).isEqualTo(demande.getDateSoumission());
        assertThat(relue.getBureau().getNom()).isEqualTo("Bureau de Ngor-Almadies");
        assertThat(relue.getUtilisateur()).isNull();
        assertThat(piecesJointes.findAll())
                .extracting(PieceJointe::getType)
                .containsExactlyInAnyOrder(TypePiece.CNI_RECTO, TypePiece.COPIE_TITRE_FONCIER);
        // les enumerations sont stockees par leur nom, en texte
        assertThat(jdbc.sql("SELECT statut FROM demande WHERE id_demande = ?").param(relue.getId())
                .query(String.class).single()).isEqualTo("SOUMISE");
    }
}
