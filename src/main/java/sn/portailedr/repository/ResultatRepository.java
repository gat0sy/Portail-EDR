package sn.portailedr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Resultat;

public interface ResultatRepository extends JpaRepository<Resultat, Long> {

    /** 0..1 resultat par demande (uq_resultat_demande). */
    Optional<Resultat> findByDemandeId(Long idDemande);
}
