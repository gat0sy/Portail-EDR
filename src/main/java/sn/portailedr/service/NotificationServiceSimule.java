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
    public void notifierRejet(Demande demande) {
        enregistrer(TypeNotification.REJET, demande, "demande rejetée");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifierDisponibilite(Demande demande) {
        enregistrer(TypeNotification.DISPONIBILITE, demande, "résultat disponible, frais à régler");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifierPaiement(Demande demande) {
        enregistrer(TypeNotification.PAIEMENT, demande, "paiement confirmé");
    }

    /** SMS pour un particulier, courriel pour un notaire (canal du cahier des charges). */
    private void enregistrer(TypeNotification type, Demande demande, String objet) {
        notifications.save(new Notification(type, horloge.instant(), demande));
        JOURNAL.info("{} simulé : {} pour la demande {} (aucun envoi réel)",
                demande.getUtilisateur() == null ? "SMS" : "Courriel", objet, demande.getReference());
    }
}
