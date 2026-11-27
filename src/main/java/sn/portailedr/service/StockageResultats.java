package sn.portailedr.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Fichiers des resultats (etat des droits reels ou requisition negative), dans le dossier
 * "resultats", a cote de "pieces", hors de tout dossier servi par le web.
 * Le conservateur depose le resultat a la publication (tranche 7) ; le demandeur le telecharge (tranche 6).
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
        return NomDeFichier.dans(dossier, nom);
    }

    /**
     * Range un resultat depose (deja dans la zone temporaire, sous un nom genere) dans le dossier
     * des resultats, sous le meme nom. Appele apres l'ecriture des lignes en base.
     */
    public void deposer(Path temporaire) {
        try {
            Files.move(temporaire, dossier.resolve(temporaire.getFileName()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
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
