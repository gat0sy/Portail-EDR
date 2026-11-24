package sn.portailedr.service;

import java.util.List;

/** Un bureau dans le tableau de la page d'administration des bureaux. */
public record BureauLigne(Long id, String nom, String adresse, List<String> codes, long conservateurs,
        long demandesEnCours) {
}
