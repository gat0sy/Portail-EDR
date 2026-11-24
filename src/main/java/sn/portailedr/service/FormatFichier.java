package sn.portailedr.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formats acceptes pour les pieces, reconnus a leurs premiers octets ("nombres magiques"),
 * pas a l'extension ni au type annonces par le navigateur, qui se falsifient facilement.
 */
public enum FormatFichier {

    JPG("jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    PDF("pdf", new byte[] {'%', 'P', 'D', 'F', '-'});

    /** Nombre d'octets a lire pour reconnaitre tous les formats. */
    static final int TAILLE_ENTETE = 8;

    private final String extension;
    private final byte[] signature;

    FormatFichier(String extension, byte[] signature) {
        this.extension = extension;
        this.signature = signature;
    }

    /** Extension du fichier stocke, deduite du contenu. */
    public String extension() {
        return extension;
    }

    static Optional<FormatFichier> reconnaitre(byte[] entete) {
        return Arrays.stream(values())
                .filter(format -> entete.length >= format.signature.length
                        && Arrays.equals(entete, 0, format.signature.length, format.signature, 0, format.signature.length))
                .findFirst();
    }
}
