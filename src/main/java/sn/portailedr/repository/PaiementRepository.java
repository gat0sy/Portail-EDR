package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Paiement;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
}
