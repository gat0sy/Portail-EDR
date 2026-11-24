package sn.portailedr.service;

/**
 * Une ligne de "Mes dernieres demandes" (profil du notaire).
 *
 * @param objet "Personne", ou "Bien · " suivi du numero du titre, comme dans le prototype
 * @param badge classe CSS du badge de statut
 */
public record DemandeLigne(String reference, String objet, String dateSoumission, String statut, String badge) {
}
