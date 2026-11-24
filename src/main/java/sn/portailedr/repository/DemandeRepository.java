package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Demande;

public interface DemandeRepository extends JpaRepository<Demande, Long> {
}
