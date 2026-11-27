package sn.portailedr.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du paiement (proprietes portail.paiement.*). Choix du projet, pas une regle legale.
 *
 * @param delai duree laissee pour payer apres la publication du resultat (7 jours, valeur du prototype)
 */
@ConfigurationProperties("portail.paiement")
public record PaiementProprietes(Duration delai) {
}
