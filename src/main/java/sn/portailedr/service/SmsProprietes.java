package sn.portailedr.service;

import java.io.Serializable;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du code de verification SMS (proprietes portail.sms.*, valeurs dans application.properties).
 * Ce sont des choix du projet, pas des regles legales.
 *
 * @param longueurCode nombre de chiffres du code
 * @param validite     duree de validite d'un code apres son envoi
 * @param tentativesMax essais permis pour un meme code
 * @param delaiRenvoi  attente minimale entre deux envois
 * @param envoisMax    nombre d'envois au maximum pour une meme demande, premier envoi compris
 */
@ConfigurationProperties("portail.sms")
public record SmsProprietes(int longueurCode, Duration validite, int tentativesMax, Duration delaiRenvoi,
        int envoisMax) implements Serializable {
}
