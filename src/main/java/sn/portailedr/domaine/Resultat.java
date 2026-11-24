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

/** PDF signe depose par le conservateur. Au plus un resultat par demande. */
@Entity
@Table(name = "resultat")
public class Resultat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultat")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeResultat type;

    /** Chemin du fichier stocke. */
    @Column(nullable = false)
    private String fichier;

    @Column(nullable = false)
    private Instant datePublication;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demande", nullable = false, unique = true)
    private Demande demande;

    protected Resultat() {
    }

    public Resultat(TypeResultat type, String fichier, Instant datePublication, Demande demande) {
        this.type = type;
        this.fichier = fichier;
        this.datePublication = datePublication;
        this.demande = demande;
    }

    public Long getId() {
        return id;
    }

    public TypeResultat getType() {
        return type;
    }

    public String getFichier() {
        return fichier;
    }

    public Instant getDatePublication() {
        return datePublication;
    }

    public Demande getDemande() {
        return demande;
    }
}
