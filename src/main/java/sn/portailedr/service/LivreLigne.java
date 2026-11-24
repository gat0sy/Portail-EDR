package sn.portailedr.service;

/** Un livre foncier dans le tableau de la page d'administration des bureaux. */
public record LivreLigne(Long id, String code, String nom, String bureau) {
}
