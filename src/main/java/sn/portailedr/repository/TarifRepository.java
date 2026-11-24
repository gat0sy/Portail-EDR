package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Tarif;

public interface TarifRepository extends JpaRepository<Tarif, Long> {
}
