package sn.portailedr.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Fichiers des resultats (etat des droits reels ou requisition negative), dans le dossier
 * "resultats", a cote de "pieces", hors de tout dossier servi par le web.
 * Ici, la lecture seulement ; l'ecriture arrive avec la publication (tranche 7).
 * Resultat.fichier contient un nom de fichier genere, jamais un chemin.
 */
@Component
public class StockageResultats {

    private final Path dossier;

    public StockageResultats(FichiersProprietes proprietes) {
        this.dossier = proprietes.dossier().toAbsolutePath().normalize().resolve("resultats");
        try {
            Files.createDirectories(dossier);
        } catch (IOException erreur) {
            throw new UncheckedIOException("Dossier des resultats inaccessible : " + dossier, erreur);
        }
    }

    /**
     * Chemin du fichier dans le dossier des resultats.
     *
     * @throws IllegalArgumentException si le nom contient un chemin ou sort du dossier
     */
    public Path fichier(String nom) {
        if (nom == null || nom.isBlank() || nom.contains("/") || nom.contains("\\") || nom.equals("..")) {
            throw new IllegalArgumentException("Nom de fichier de resultat refuse : " + nom);
        }
        Path fichier = dossier.resolve(nom).normalize();
        if (!fichier.getParent().equals(dossier)) {
            throw new IllegalArgumentException("Nom de fichier de resultat refuse : " + nom);
        }
        return fichier;
    }

    /** Type du contenu deduit de l'extension du fichier stocke (choisie par l'application, pas par l'utilisateur). */
    public static String typeDeContenu(String nom) {
        String minuscules = nom.toLowerCase(Locale.ROOT);
        if (minuscules.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (minuscules.endsWith(".jpg") || minuscules.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (minuscules.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    /** Extension du fichier stocke, sans le point ("pdf"), ou vide. */
    public static String extension(String nom) {
        int point = nom.lastIndexOf('.');
        return point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT);
    }
}
