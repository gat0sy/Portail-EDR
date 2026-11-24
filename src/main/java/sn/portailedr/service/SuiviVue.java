package sn.portailedr.service;

import java.util.List;

/**
 * Page "Suivi de la demande" (m2.html). Les pages ne recoivent jamais les entites.
 *
 * @param etapes              frise Soumise / En cours / Disponible / Payee ; vide pour REJETEE et EXPIREE
 * @param paiementEtResultat  vrai pour DISPONIBLE et PAYEE : cartes "Paiement" et "Resultat"
 * @param paiementPossible    DISPONIBLE et date limite non atteinte : le formulaire de paiement s'affiche
 * @param paiementConfirme    "1 000 FCFA regles par Wave le ...", ou null
 * @param telechargementOuvert PAYEE et dans la fenetre de telechargement
 */
public record SuiviVue(String reference, String objet, String numeroTitreFoncier, String personneConcernee,
        String bureau, String dateSoumission, String statut, String badge, String explication,
        List<Etape> etapes, boolean paiementEtResultat, boolean paiementPossible, String montant,
        String dateLimitePaiement, String paiementConfirme, String typeResultat, boolean telechargementOuvert,
        String telechargementJusquau, long joursTelechargement, List<LigneHistorique> historique) {

    /** Une etape de la frise ; classe CSS du prototype : done, cur, et last pour la derniere. */
    public record Etape(String libelle, String date, String classe) {
    }

    /** Un message envoye au demandeur : "SMS · Paiement confirme", date. */
    public record LigneHistorique(String libelle, String date) {
    }
}
