package sn.portailedr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.LivreFoncier;

public interface LivreFoncierRepository extends JpaRepository<LivreFoncier, Long> {

    /** Le code est unique et stocke en majuscules (NGA, TH, R...). */
    Optional<LivreFoncier> findByCode(String code);
}
