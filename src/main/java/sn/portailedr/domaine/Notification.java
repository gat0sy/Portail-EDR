package sn.portailedr.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Trace d'un message envoye au demandeur (SMS ou courriel simule). */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notification")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNotification type;

    @Column(nullable = false)
    private Instant dateEnvoi;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demande", nullable = false)
    private Demande demande;

    protected Notification() {
    }

    public Notification(TypeNotification type, Instant dateEnvoi, Demande demande) {
        this.type = type;
        this.dateEnvoi = dateEnvoi;
        this.demande = demande;
    }

    public Long getId() {
        return id;
    }

    public TypeNotification getType() {
        return type;
    }

    public Instant getDateEnvoi() {
        return dateEnvoi;
    }

    public Demande getDemande() {
        return demande;
    }
}
