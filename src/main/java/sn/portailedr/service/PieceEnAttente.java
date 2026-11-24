package sn.portailedr.service;

import java.io.Serializable;

import sn.portailedr.domaine.TypePiece;

/**
 * Piece deposee, en attente dans la zone temporaire privee jusqu'a l'enregistrement de la demande.
 *
 * @param nomOrigine        nom du fichier sur le poste du demandeur, pour l'affichage seulement
 * @param fichierTemporaire chemin absolu du fichier temporaire, sous un nom genere
 */
public record PieceEnAttente(TypePiece type, String nomOrigine, String fichierTemporaire) implements Serializable {
}
