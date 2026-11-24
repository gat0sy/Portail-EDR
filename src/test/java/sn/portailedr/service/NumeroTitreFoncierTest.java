package sn.portailedr.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Lecture du numero de titre foncier avec l'expression du cahier des charges, puis normalisation. */
class NumeroTitreFoncierTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "12.405/NGA;      12405/NGA; NGA",
        "12405/NGA;       12405/NGA; NGA",
        "12 405/NGA;      12405/NGA; NGA",
        "' 12.405 / nga '; 12405/NGA; NGA",
        "2082/R;          2082/R;    R",
        "1.234.567/DK;    1234567/DK; DK",
        "1234567/ABCD;    1234567/ABCD; ABCD"})
    void numerosValides(String saisi, String normalise, String code) {
        NumeroTitreFoncier numero = NumeroTitreFoncier.lire(saisi).orElseThrow();

        assertThat(numero.normalise()).isEqualTo(normalise);
        assertThat(numero.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12405", "12405/", "/NGA", "ABC/NGA", "12405/NGAXX", "12405-NGA", "12,405/NGA",
        "12.40/NGA", "12..405/NGA", "12405/N1", "12405/NGA/X"})
    void numerosInvalides(String saisi) {
        assertThat(NumeroTitreFoncier.lire(saisi)).isEmpty();
    }
}
