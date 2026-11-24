package sn.portailedr.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du suivi (proprietes portail.suivi.*). Choix du projet, pas des regles legales.
 *
 * @param inactivite          une verification reussie reste valable tant que la reference est consultee
 *                            au moins une fois par periode de cette duree
 * @param fenetreLimite       duree pendant laquelle les envois et les essais d'une reference sont comptes,
 *                            toutes sessions confondues ; ensuite la reference repart de zero
 * @param dureeTelechargement le resultat se telecharge pendant cette duree apres le paiement
 */
@ConfigurationProperties("portail.suivi")
public record SuiviProprietes(Duration inactivite, Duration fenetreLimite, Duration dureeTelechargement) {
}
