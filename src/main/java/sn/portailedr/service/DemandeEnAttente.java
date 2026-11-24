package sn.portailedr.service;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import sn.portailedr.domaine.TypeDemande;

/**
 * Demande verifiee mais pas encore enregistree : elle attend le code SMS du particulier.
 * Gardee dans la session ; ses pieces sont dans la zone temporaire. Rien en base.
 *
 * @param numeroTitreFoncier numero normalise (12405/NGA), null pour une demande sur une personne
 * @param idBureau           bureau deduit du livre foncier, ou choisi pour une personne
 * @param declarantCni       numero declare, jamais verifie ; null pour un notaire
 */
public record DemandeEnAttente(TypeDemande type, String numeroTitreFoncier, String personneConcernee,
        Long idBureau, String nomBureau, String declarantNom, String declarantTelephone, String declarantCni,
        List<PieceEnAttente> pieces) implements Serializable {

    public DemandeEnAttente {
        pieces = List.copyOf(pieces);
    }

    /** Supprime les fichiers temporaires qui restent (aucun si la demande a ete enregistree). */
    public void supprimerFichiers() {
        for (PieceEnAttente piece : pieces) {
            try {
                Files.deleteIfExists(Path.of(piece.fichierTemporaire()));
            } catch (IOException erreur) {
                // le nettoyage au demarrage (StockagePieces) le retirera
            }
        }
    }

    /** Pour revenir au formulaire depuis l'etape SMS : les champs sont gardes, pas les fichiers. */
    public DemandeFormulaire versFormulaire() {
        DemandeFormulaire formulaire = new DemandeFormulaire();
        formulaire.setType(type);
        formulaire.setNumeroTitreFoncier(numeroTitreFoncier);
        formulaire.setPersonneConcernee(personneConcernee);
        formulaire.setIdBureau(idBureau);
        formulaire.setDeclarantNom(declarantNom);
        formulaire.setDeclarantTelephone(declarantTelephone);
        formulaire.setDeclarantCni(declarantCni);
        return formulaire;
    }
}
