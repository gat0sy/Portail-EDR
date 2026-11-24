package sn.portailedr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /** L'adresse e-mail est l'identifiant de connexion, unique sans tenir compte de la casse. */
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
}
