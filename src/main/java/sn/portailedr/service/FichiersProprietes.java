package sn.portailedr.service;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Stockage des pieces deposees (proprietes portail.fichiers.*).
 *
 * @param dossier   dossier racine, hors de l'application et de tout dossier servi par le web
 *                  (variable d'environnement UPLOAD_DIR)
 * @param tailleMax taille maximale d'une piece
 */
@ConfigurationProperties("portail.fichiers")
public record FichiersProprietes(Path dossier, DataSize tailleMax) {
}
