package sn.portailedr.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ligne de la grille tarifaire. Montant en FCFA. */
@Entity
@Table(name = "tarif")
public class Tarif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarif")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDemande typeDemande;

    @Column(nullable = false)
    private int montant;

    protected Tarif() {
    }

    public Tarif(TypeDemande typeDemande, int montant) {
        this.typeDemande = typeDemande;
        this.montant = montant;
    }

    public Long getId() {
        return id;
    }

    public TypeDemande getTypeDemande() {
        return typeDemande;
    }

    public int getMontant() {
        return montant;
    }

    public void setMontant(int montant) {
        this.montant = montant;
    }
}
