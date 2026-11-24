package sn.portailedr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.portailedr.domaine.Journal;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    /** Les dernieres operations, de la plus recente a la plus ancienne. */
    List<Journal> findTop10ByOrderByDateActionDescIdDesc();
}
