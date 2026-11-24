package sn.portailedr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.PieceJointe;

public interface PieceJointeRepository extends JpaRepository<PieceJointe, Long> {

    List<PieceJointe> findByDemandeIdOrderById(Long idDemande);
}
