package sn.portailedr.service;

import java.util.List;

/** Chiffres et dernieres operations du tableau de bord de l'administrateur. */
public record TableauDeBordVue(long bureaux, long conservateurs, long notaires, long demandesEnCours,
        List<JournalLigne> operations) {
}
