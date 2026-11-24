package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

/** Lecture des resultats : seul un nom de fichier du dossier "resultats" est accepte, jamais un chemin. */
class StockageResultatsTest {

    @TempDir
    Path dossier;

    private StockageResultats stockage() {
        return new StockageResultats(new FichiersProprietes(dossier, DataSize.ofMegabytes(5)));
    }

    @Test
    void creeLeDossierAuDemarrage() {
        stockage();

        assertThat(dossier.resolve("resultats")).isDirectory();
    }

    @Test
    void trouveUnFichierDuDossier() throws Exception {
        StockageResultats stockage = stockage();
        Files.writeString(dossier.resolve("resultats").resolve("abc.pdf"), "%PDF-");

        assertThat(stockage.fichier("abc.pdf")).isEqualTo(dossier.resolve("resultats").resolve("abc.pdf").toAbsolutePath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../pieces/cni.jpg", "../../etc/passwd", "/etc/passwd", "sous/dossier.pdf", "..", ""})
    void refuseToutCheminQuiSortDuDossier(String nom) {
        StockageResultats stockage = stockage();

        assertThatIllegalArgumentException().isThrownBy(() -> stockage.fichier(nom));
    }

    @ParameterizedTest
    @CsvSource({"resultat.pdf, application/pdf", "scan.PDF, application/pdf", "photo.jpg, image/jpeg",
        "photo.png, image/png", "inconnu.bin, application/octet-stream"})
    void typeDuContenuSelonLExtensionStockee(String nom, String type) {
        assertThat(StockageResultats.typeDeContenu(nom)).isEqualTo(type);
    }
}
