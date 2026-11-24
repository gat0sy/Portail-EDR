package sn.portailedr.domaine;

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
 * Registre dont le code termine le numero du titre foncier (12405/NGA).
 * Il est tenu par un bureau : c'est ce lien qui permet d'affecter une demande sur un bien.
 */
@Entity
@Table(name = "livre_foncier")
public class LivreFoncier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_livre_foncier")
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String nom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bureau", nullable = false)
    private Bureau bureau;

    protected LivreFoncier() {
    }

    public LivreFoncier(String code, String nom, Bureau bureau) {
        this.code = code;
        this.nom = nom;
        this.bureau = bureau;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Bureau getBureau() {
        return bureau;
    }

    public void setBureau(Bureau bureau) {
        this.bureau = bureau;
    }
}
