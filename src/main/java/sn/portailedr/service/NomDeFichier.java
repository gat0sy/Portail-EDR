package sn.portailedr.service;

import java.nio.file.Path;

/**
 * Les fichiers stockes portent un nom genere (UUID et extension), jamais un chemin.
 * Avant de lire un fichier dont le nom vient de la base, on verifie que ce nom designe
 * bien un fichier directement dans le dossier attendu : ni "/", ni "\", ni "..".
 */
final class NomDeFichier {

    private NomDeFichier() {
    }

    /** @throws IllegalArgumentException si le nom contient un chemin ou sort du dossier */
    static Path dans(Path dossier, String nom) {
        if (nom == null || nom.isBlank() || nom.contains("/") || nom.contains("\\") || nom.equals("..")) {
            throw new IllegalArgumentException("Nom de fichier refuse : " + nom);
        }
        Path fichier = dossier.resolve(nom).normalize();
        if (!dossier.equals(fichier.getParent())) {
            throw new IllegalArgumentException("Nom de fichier refuse : " + nom);
        }
        return fichier;
    }
}
