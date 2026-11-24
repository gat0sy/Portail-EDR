package sn.portailedr.domaine;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

/** Piece justificative. Composition : supprimee avec sa demande (ON DELETE CASCADE). */
@Entity
@Table(name = "piece_jointe")
public class PieceJointe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_piece_jointe")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypePiece type;

    /** Nom d'origine du fichier, pour l'affichage uniquement. */
    @Column(nullable = false)
    private String nomFichier;

    /** Chemin du fichier stocke sous un nom genere, jamais fourni par l'utilisateur. */
    @Column(nullable = false)
    private String chemin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_demande", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Demande demande;

    protected PieceJointe() {
    }

    public PieceJointe(TypePiece type, String nomFichier, String chemin, Demande demande) {
        this.type = type;
        this.nomFichier = nomFichier;
        this.chemin = chemin;
        this.demande = demande;
    }

    public Long getId() {
        return id;
    }

    public TypePiece getType() {
        return type;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getChemin() {
        return chemin;
    }

    public Demande getDemande() {
        return demande;
    }
}
