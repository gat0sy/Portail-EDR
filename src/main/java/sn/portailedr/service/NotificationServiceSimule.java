package sn.portailedr.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.Notification;
import sn.portailedr.domaine.TypeNotification;
import sn.portailedr.repository.NotificationRepository;

/**
 * Notifications simulees : aucun SMS ni courriel reel n'est envoye. Chaque message lie a une
 * demande est enregistre dans NOTIFICATION, dans la transaction de l'action qui l'envoie.
 * Les codes de verification n'apparaissent qu'au niveau DEBUG, active seulement par le profil dev
 * (application-dev.properties) : ils ne figurent pas dans le journal d'un autre environnement.
 */
@Service
public class NotificationServiceSimule implements NotificationService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(NotificationServiceSimule.class);

    private final NotificationRepository notifications;
    private final Clock horloge;

    public NotificationServiceSimule(NotificationRepository notifications, Clock horloge) {
        this.notifications = notifications;
        this.horloge = horloge;
    }

    @Override
    public void envoyerCodeVerification(String telephone, String code) {
        JOURNAL.info("SMS simulé : code de vérification envoyé (aucun SMS réel)");
        JOURNAL.debug("SMS simulé pour {} : votre code de vérification Portail EDR est {}", telephone, code);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void envoyerCodeSuivi(Demande demande, String code) {
        notifications.save(new Notification(TypeNotification.CODE_VERIFICATION, horloge.instant(), demande));
        JOURNAL.info("SMS simulé : code de vérification du suivi envoyé (aucun SMS réel)");
        JOURNAL.debug("SMS simulé pour {} : votre code pour suivre la demande {} est {}",
                demande.getDeclarantTelephone(), demande.getReference(), code);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifierPaiement(Demande demande) {
        notifications.save(new Notification(TypeNotification.PAIEMENT, horloge.instant(), demande));
        JOURNAL.info("{} simulé : paiement confirmé pour la demande {} (aucun envoi réel)",
                demande.getUtilisateur() == null ? "SMS" : "Courriel", demande.getReference());
    }
}
