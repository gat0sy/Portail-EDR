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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Paiement simule des frais. Au plus un paiement par demande. Montant en FCFA. */
@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paiement")
    private Long id;

    @Column(nullable = false)
    private int montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModePaiement modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiement statut;

    @Column(nullable = false)
    private Instant datePaiement;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demande", nullable = false, unique = true)
    private Demande demande;

    protected Paiement() {
    }

    public Paiement(int montant, ModePaiement modePaiement, StatutPaiement statut, Instant datePaiement,
            Demande demande) {
        this.montant = montant;
        this.modePaiement = modePaiement;
        this.statut = statut;
        this.datePaiement = datePaiement;
        this.demande = demande;
    }

    public Long getId() {
        return id;
    }

    public int getMontant() {
        return montant;
    }

    public ModePaiement getModePaiement() {
        return modePaiement;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public void setStatut(StatutPaiement statut) {
        this.statut = statut;
    }

    public Instant getDatePaiement() {
        return datePaiement;
    }

    public void setDatePaiement(Instant datePaiement) {
        this.datePaiement = datePaiement;
    }

    public Demande getDemande() {
        return demande;
    }
}
