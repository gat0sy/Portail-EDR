package sn.portailedr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;

public interface DemandeRepository extends JpaRepository<Demande, Long> {

    long countByStatut(StatutDemande statut);

    long countByBureauIdAndStatut(Long idBureau, StatutDemande statut);

    boolean existsByBureauId(Long idBureau);

    boolean existsByReference(String reference);

    Optional<Demande> findByReference(String reference);

    /** Dernieres demandes d'un notaire, de la plus recente a la plus ancienne. */
    List<Demande> findTop5ByUtilisateurIdOrderByDateSoumissionDescIdDesc(Long idUtilisateur);
}
