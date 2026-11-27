package sn.portailedr.service;

import java.nio.file.Path;

/**
 * Piece jointe prete a etre envoyee au conservateur.
 *
 * @param enLigne vrai pour une photo (JPG, PNG) affichee dans le navigateur ; un PDF est telecharge
 */
public record FichierPiece(Path chemin, String nomTelechargement, String typeDeContenu, boolean enLigne) {
}
