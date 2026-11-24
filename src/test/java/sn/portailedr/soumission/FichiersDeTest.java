package sn.portailedr.soumission;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.mock.web.MockMultipartFile;

/**
 * Fichiers deposes dans les tests. Seuls les premiers octets ("nombres magiques") comptent
 * pour le serveur : le reste du contenu est du remplissage.
 */
public final class FichiersDeTest {

    private static final byte[] ENTETE_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] ENTETE_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] ENTETE_PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    /** 5 Mo, la limite par defaut de portail.fichiers.taille-max. */
    public static final int CINQ_MO = 5 * 1024 * 1024;

    private FichiersDeTest() {
    }

    public static MockMultipartFile jpg(String champ) {
        return fichier(champ, "photo-cni.jpg", "image/jpeg", ENTETE_JPG, 2048);
    }

    public static MockMultipartFile png(String champ) {
        return fichier(champ, "photo-cni.png", "image/png", ENTETE_PNG, 2048);
    }

    public static MockMultipartFile pdf(String champ) {
        return fichier(champ, "copie-titre.pdf", "application/pdf", ENTETE_PDF, 2048);
    }

    /** Extension et type annonces d'une image, mais le contenu est du texte. */
    public static MockMultipartFile imageDeguisee(String champ) {
        return new MockMultipartFile(champ, "photo-cni.jpg", "image/jpeg",
                "ceci n'est pas une image".getBytes(StandardCharsets.UTF_8));
    }

    /** Un vrai JPG, un octet au-dessus de la limite. */
    public static MockMultipartFile jpgTropGros(String champ) {
        return fichier(champ, "photo-cni.jpg", "image/jpeg", ENTETE_JPG, CINQ_MO + 1);
    }

    /** Exactement a la limite : accepte. */
    public static MockMultipartFile jpgALaLimite(String champ) {
        return fichier(champ, "photo-cni.jpg", "image/jpeg", ENTETE_JPG, CINQ_MO);
    }

    private static MockMultipartFile fichier(String champ, String nom, String type, byte[] entete, int taille) {
        byte[] contenu = Arrays.copyOf(entete, taille);
        return new MockMultipartFile(champ, nom, type, contenu);
    }
}
