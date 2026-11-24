package sn.portailedr.domaine;

import java.util.Set;

/**
 * Statuts du cycle de vie (figure 2.3). Libelle et classe CSS du badge viennent du prototype.
 * <pre>
 * SOUMISE --conforme--> EN_COURS --resultat publie--> DISPONIBLE --paiement--> PAYEE
 *    +--non conforme--> REJETEE                           +--delai depasse--> EXPIREE
 * </pre>
 * REJETEE, PAYEE et EXPIREE sont finaux.
 */
public enum StatutDemande {
    SOUMISE("Soumise", "b-sou"),
    EN_COURS("En cours", "b-enc"),
    REJETEE("Rejetée", "b-rej"),
    DISPONIBLE("Disponible", "b-dis"),
    PAYEE("Payée", "b-pay"),
    EXPIREE("Expirée", "b-exp");

    private final String libelle;
    private final String badge;

    StatutDemande(String libelle, String badge) {
        this.libelle = libelle;
        this.badge = badge;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Le seul endroit qui dit quels changements de statut sont permis (utilise par Demande.changerStatut). */
    public boolean peutPasserA(StatutDemande cible) {
        return switch (this) {
            case SOUMISE -> Set.of(EN_COURS, REJETEE).contains(cible);
            case EN_COURS -> cible == DISPONIBLE;
            case DISPONIBLE -> Set.of(PAYEE, EXPIREE).contains(cible);
            case REJETEE, PAYEE, EXPIREE -> false;
        };
    }

    /** Classe CSS du badge de statut (base.css). */
    public String getBadge() {
        return badge;
    }
}
