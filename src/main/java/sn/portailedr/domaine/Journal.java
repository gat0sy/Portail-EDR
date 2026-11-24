package sn.portailedr.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ligne du journal. Rattachee a l'utilisateur authentifie qui agit, ou a la demande
 * quand l'action vient d'un particulier (au moins l'un des deux, ck_journal_rattachement).
 */
@Entity
@Table(name = "journal")
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_journal")
    private Long id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private Instant dateAction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_demande")
    private Demande demande;

    protected Journal() {
    }

    public Journal(String action, Instant dateAction, Utilisateur utilisateur, Demande demande) {
        this.action = action;
        this.dateAction = dateAction;
        this.utilisateur = utilisateur;
        this.demande = demande;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public Instant getDateAction() {
        return dateAction;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public Demande getDemande() {
        return demande;
    }
}
