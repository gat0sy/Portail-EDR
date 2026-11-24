package sn.portailedr.service;

import java.util.List;

/** Un bureau tel qu'affiche dans l'annuaire public. Aucune donnee de compte. */
public record AnnuaireBureau(String nom, String adresse, String telephone, List<String> codes) {
}
