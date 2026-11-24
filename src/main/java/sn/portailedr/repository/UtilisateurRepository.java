package sn.portailedr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /** L'adresse e-mail est l'identifiant de connexion, unique sans tenir compte de la casse. */
    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    List<Utilisateur> findAllByOrderByNomAsc();

    List<Utilisateur> findAllByRoleOrderByNomAsc(Role role);

    long countByRole(Role role);

    long countByRoleAndActifTrue(Role role);

    long countByBureauIdAndRole(Long idBureau, Role role);

    boolean existsByBureauId(Long idBureau);
}
