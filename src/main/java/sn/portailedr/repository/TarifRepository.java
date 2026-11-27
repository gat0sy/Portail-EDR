package sn.portailedr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Tarif;
import sn.portailedr.domaine.TypeDemande;

public interface TarifRepository extends JpaRepository<Tarif, Long> {

    /** Un tarif par type de demande dans la grille (montants fictifs, migration V2). */
    Optional<Tarif> findFirstByTypeDemandeOrderByIdAsc(TypeDemande typeDemande);
}
