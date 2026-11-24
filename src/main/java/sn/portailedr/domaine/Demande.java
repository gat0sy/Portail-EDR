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

/**
 * Demande d'etat des droits reels. Les regles d'integrite (objet de la demande,
 * CNI du particulier, telephone verifie, format de la reference) sont aussi
 * verifiees par les contraintes CHECK de la table demande.
 */
@Entity
@Table(name = "demande")
public class Demande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_demande")
    private Long id;

    /** Code de suivi aleatoire, jamais sequentiel. */
    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDemande type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDemande statut;

    /** Demande sur un bien : numero normalise, par exemple 12405/NGA. */
    private String numeroTitreFoncier;

    /** Demande sur une personne. */
    private String personneConcernee;

    /** Valeur declaree, jamais verifiee. Nulle pour la demande d'un notaire. */
    private String declarantCni;

    @Column(nullable = false)
    private String declarantNom;

    @Column(nullable = false)
    private String declarantTelephone;

    @Column(nullable = false)
    private boolean telephoneVerifie;

    @Column(nullable = false)
    private Instant dateSoumission;

    private Instant dateLimitePaiement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bureau", nullable = false)
    private Bureau bureau;

    /** Le notaire qui a soumis la demande, nul pour un particulier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utilisateur")
    private Utilisateur utilisateur;

    /** Rattache quand les frais sont determines. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tarif")
    private Tarif tarif;

    public Demande() {
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public TypeDemande getType() {
        return type;
    }

    public void setType(TypeDemande type) {
        this.type = type;
    }

    public StatutDemande getStatut() {
        return statut;
    }

    /** Statut initial, a la creation (SOUMISE). Ensuite, passer par {@link #changerStatut}. */
    public void setStatut(StatutDemande statut) {
        this.statut = statut;
    }

    /**
     * Transition du cycle de vie. Refuse tout changement que la figure 2.3 ne permet pas :
     * les services verifient le statut avant (avec un message en francais), ceci est le filet.
     *
     * @throws IllegalStateException si la transition n'est pas permise
     */
    public void changerStatut(StatutDemande cible) {
        if (!statut.peutPasserA(cible)) {
            throw new IllegalStateException("Transition refusee : " + statut + " vers " + cible);
        }
        this.statut = cible;
    }

    public String getNumeroTitreFoncier() {
        return numeroTitreFoncier;
    }

    public void setNumeroTitreFoncier(String numeroTitreFoncier) {
        this.numeroTitreFoncier = numeroTitreFoncier;
    }

    public String getPersonneConcernee() {
        return personneConcernee;
    }

    public void setPersonneConcernee(String personneConcernee) {
        this.personneConcernee = personneConcernee;
    }

    public String getDeclarantCni() {
        return declarantCni;
    }

    public void setDeclarantCni(String declarantCni) {
        this.declarantCni = declarantCni;
    }

    public String getDeclarantNom() {
        return declarantNom;
    }

    public void setDeclarantNom(String declarantNom) {
        this.declarantNom = declarantNom;
    }

    public String getDeclarantTelephone() {
        return declarantTelephone;
    }

    public void setDeclarantTelephone(String declarantTelephone) {
        this.declarantTelephone = declarantTelephone;
    }

    public boolean isTelephoneVerifie() {
        return telephoneVerifie;
    }

    public void setTelephoneVerifie(boolean telephoneVerifie) {
        this.telephoneVerifie = telephoneVerifie;
    }

    public Instant getDateSoumission() {
        return dateSoumission;
    }

    public void setDateSoumission(Instant dateSoumission) {
        this.dateSoumission = dateSoumission;
    }

    public Instant getDateLimitePaiement() {
        return dateLimitePaiement;
    }

    public void setDateLimitePaiement(Instant dateLimitePaiement) {
        this.dateLimitePaiement = dateLimitePaiement;
    }

    public Bureau getBureau() {
        return bureau;
    }

    public void setBureau(Bureau bureau) {
        this.bureau = bureau;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public Tarif getTarif() {
        return tarif;
    }

    public void setTarif(Tarif tarif) {
        this.tarif = tarif;
    }
}
