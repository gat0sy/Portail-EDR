package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Utilisateur;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
}
