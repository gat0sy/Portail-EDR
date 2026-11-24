package sn.portailedr.service;

/** Une ligne du journal telle qu'affichee au tableau de bord. */
public record JournalLigne(String date, String utilisateur, String action, String demande) {
}
