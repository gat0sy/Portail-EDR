package sn.portailedr.service;

import java.nio.file.Path;

/**
 * Fichier du resultat pret a etre envoye.
 *
 * @param nomTelechargement nom propose au navigateur : la reference et l'extension (EDR-2026-K7P4QX.pdf)
 * @param typeDeContenu     deduit de l'extension du fichier stocke
 */
public record FichierResultat(Path chemin, String nomTelechargement, String typeDeContenu) {
}
