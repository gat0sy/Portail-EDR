package sn.portailedr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.LivreFoncier;
import sn.portailedr.domaine.Tarif;
import sn.portailedr.domaine.TypeDemande;

/** Donnees initiales de la migration V2 (valeurs de demonstration). */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SeedDataTest {

    @Autowired
    private BureauRepository bureaux;

    @Autowired
    private LivreFoncierRepository livresFonciers;

    @Autowired
    private TarifRepository tarifs;

    @Test
    void lesSixBureauxDuPrototypeSontCrees() {
        assertThat(bureaux.findAll())
                .extracting(Bureau::getNom, Bureau::getAdresse)
                .containsExactlyInAnyOrder(
                        tuple("Bureau de Dakar-Plateau", "Dakar"),
                        tuple("Bureau de Grand Dakar", "Dakar"),
                        tuple("Bureau de Ngor-Almadies", "Dakar"),
                        tuple("Bureau de Pikine", "Pikine"),
                        tuple("Bureau de Rufisque", "Rufisque"),
                        tuple("Bureau de Thiès", "Thiès"));
    }

    @Test
    void chaqueLivreFoncierProvisoireEstTenuParLeBonBureau() {
        Map<String, String> bureauParCode = livresFonciers.findAll().stream()
                .collect(Collectors.toMap(LivreFoncier::getCode, livre -> livre.getBureau().getNom()));

        assertThat(bureauParCode).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DK", "Bureau de Dakar-Plateau",
                "GR", "Bureau de Grand Dakar",
                "NGA", "Bureau de Ngor-Almadies",
                "DP", "Bureau de Pikine",
                "GW", "Bureau de Pikine",
                "R", "Bureau de Rufisque",
                "TH", "Bureau de Thiès"));
    }

    @Test
    void lAncienCodeDgNEstPasCree() {
        assertThat(livresFonciers.findByCode("DG")).isEmpty();
    }

    @Test
    void laGrilleTarifaireFictiveEstDe1000FcfaParType() {
        assertThat(tarifs.findAll())
                .extracting(Tarif::getTypeDemande, Tarif::getMontant)
                .containsExactlyInAnyOrder(
                        tuple(TypeDemande.BIEN, 1000),
                        tuple(TypeDemande.PERSONNE, 1000));
    }
}
