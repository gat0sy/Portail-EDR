package sn.portailedr.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.Journal;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.JournalRepository;

@Service
public class JournalService {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.FRENCH).withZone(ZoneId.of("Africa/Dakar"));

    private final JournalRepository journal;

    public JournalService(JournalRepository journal) {
        this.journal = journal;
    }

    /**
     * Ecrit une ligne de journal DANS la transaction de l'action qui l'appelle.
     * MANDATORY : sans transaction en cours, l'appel est refuse. Ainsi la ligne et l'action
     * sont enregistrees ensemble, ou annulees ensemble.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void journaliser(String action, Utilisateur acteur, Demande demande) {
        journal.save(new Journal(action, Instant.now(), acteur, demande));
    }

    @Transactional(readOnly = true)
    public List<JournalLigne> dernieresOperations() {
        return journal.findTop10ByOrderByDateActionDescIdDesc().stream()
                .map(ligne -> new JournalLigne(
                        FORMAT_DATE.format(ligne.getDateAction()),
                        ligne.getUtilisateur() == null ? "Aucun" : ligne.getUtilisateur().getNom(),
                        ligne.getAction(),
                        ligne.getDemande() == null ? "Aucune" : ligne.getDemande().getReference()))
                .toList();
    }
}
