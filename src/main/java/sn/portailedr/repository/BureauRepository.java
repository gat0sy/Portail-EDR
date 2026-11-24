package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Bureau;

public interface BureauRepository extends JpaRepository<Bureau, Long> {
}
