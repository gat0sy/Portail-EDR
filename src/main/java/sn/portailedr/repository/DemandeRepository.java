package sn.portailedr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.StatutDemande;

public interface DemandeRepository extends JpaRepository<Demande, Long> {

    long countByStatut(StatutDemande statut);

    long countByBureauIdAndStatut(Long idBureau, StatutDemande statut);

    boolean existsByBureauId(Long idBureau);

    boolean existsByReference(String reference);

    Optional<Demande> findByReference(String reference);

    /**
     * Lit la demande en la verrouillant (SELECT ... FOR UPDATE) jusqu'a la fin de la transaction :
     * deux paiements simultanes de la meme demande passent l'un apres l'autre, et le second
     * voit le statut PAYEE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Demande> findWithLockByReference(String reference);

    /** Demandes d'un bureau, de la plus recente a la plus ancienne (espace conservateur). */
    List<Demande> findByBureauIdOrderByDateSoumissionDescIdDesc(Long idBureau);

    /** Dernieres demandes d'un notaire, de la plus recente a la plus ancienne. */
    List<Demande> findTop5ByUtilisateurIdOrderByDateSoumissionDescIdDesc(Long idUtilisateur);
}
