package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;

public interface DemandeRepository extends JpaRepository<Demande, Long> {

    long countByStatut(StatutDemande statut);

    long countByBureauIdAndStatut(Long idBureau, StatutDemande statut);

    boolean existsByBureauId(Long idBureau);
}
