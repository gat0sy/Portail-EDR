package sn.portailedr.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Journal;

public interface JournalRepository extends JpaRepository<Journal, Long> {
}
