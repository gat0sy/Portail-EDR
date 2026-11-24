package sn.portailedr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByDemandeIdOrderByDateEnvoiDescIdDesc(Long idDemande);
}
