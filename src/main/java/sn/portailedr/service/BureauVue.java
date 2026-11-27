package sn.portailedr.service;

import java.util.List;

/**
 * Page "Demandes du bureau" (m3.html) : la liste, ses filtres et, si une demande est choisie, son detail.
 *
 * @param filtre filtre actif : SOUMISE, EN_COURS, DISPONIBLE, CLOTUREE, ou null pour toutes
 * @param detail demande choisie, ou null
 */
public record BureauVue(String bureau, String filtre, List<Filtre> filtres, List<Ligne> lignes, Detail detail) {

    /** Un filtre de statut, avec le nombre de demandes du bureau qu'il retient. */
    public record Filtre(String code, String libelle, long nombre) {
    }

    public record Ligne(String reference, String objet, String demandeur, String dateSoumission, String statut,
            String badge) {
    }

    /**
     * @param telephone  "Verifie par SMS" pour un particulier, sinon le numero du compte du notaire
     * @param cni        "numero (declare, non verifie)" pour un particulier, null pour un notaire
     * @param tarif      frais de la grille pour le type de la demande, affiches avant la publication
     * @param resultat   "Etat des droits reels, publie le ...", ou null
     */
    public record Detail(String reference, String statut, String badge, String objet, String numeroTitreFoncier,
            String personneConcernee, String demandeur, String telephone, String cni, String dateSoumission,
            List<Piece> pieces, boolean peutStatuer, boolean peutPublier, String tarif, String resultat,
            String dateLimitePaiement) {
    }

    /** @param image photo (JPG ou PNG) plutot que document (PDF) : icone du prototype */
    public record Piece(Long id, String libelle, String nomFichier, String taille, boolean image) {
    }
}
