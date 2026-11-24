package sn.portailedr.service;

import java.util.List;

/**
 * Page "Demande soumise" (demande-soumise.html). Les pages ne recoivent jamais l'entite Demande.
 *
 * @param notaire            demande deposee par un notaire (libelles et liens differents)
 * @param numeroTitreFoncier numero normalise, ou null pour une demande sur une personne
 * @param pieces             libelles des pieces transmises
 */
public record ConfirmationVue(String reference, boolean notaire, String objet, String numeroTitreFoncier,
        String personneConcernee, String bureau, String dateSoumission, List<String> pieces) {
}
