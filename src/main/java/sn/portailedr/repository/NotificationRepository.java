package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
