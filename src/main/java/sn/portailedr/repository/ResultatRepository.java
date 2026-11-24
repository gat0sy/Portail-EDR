package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Resultat;

public interface ResultatRepository extends JpaRepository<Resultat, Long> {
}
