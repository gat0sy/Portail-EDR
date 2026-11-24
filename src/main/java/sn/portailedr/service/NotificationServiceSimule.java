package sn.portailedr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notifications simulees : aucun SMS ni courriel reel n'est envoye.
 * Le code de verification n'apparait qu'au niveau DEBUG, active seulement par le profil dev
 * (application-dev.properties) : il ne figure pas dans le journal d'un autre environnement.
 */
@Service
public class NotificationServiceSimule implements NotificationService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(NotificationServiceSimule.class);

    @Override
    public void envoyerCodeVerification(String telephone, String code) {
        JOURNAL.info("SMS simulé : code de vérification envoyé (aucun SMS réel)");
        JOURNAL.debug("SMS simulé pour {} : votre code de vérification Portail EDR est {}", telephone, code);
    }
}
