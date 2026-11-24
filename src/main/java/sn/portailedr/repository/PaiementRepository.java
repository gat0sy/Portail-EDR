package sn.portailedr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Paiement;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    /** 0..1 paiement par demande (uq_paiement_demande). */
    Optional<Paiement> findByDemandeId(Long idDemande);
}
