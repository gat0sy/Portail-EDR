package sn.portailedr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.UtilisateurRepository;

@Service
public class TableauDeBordService {

    private final BureauRepository bureaux;
    private final UtilisateurRepository utilisateurs;
    private final DemandeRepository demandes;
    private final JournalService journal;

    public TableauDeBordService(BureauRepository bureaux, UtilisateurRepository utilisateurs,
            DemandeRepository demandes, JournalService journal) {
        this.bureaux = bureaux;
        this.utilisateurs = utilisateurs;
        this.demandes = demandes;
        this.journal = journal;
    }

    /** "Demandes en cours" : les demandes au statut EN_COURS (badge "En cours" du prototype). */
    @Transactional(readOnly = true)
    public TableauDeBordVue tableauDeBord() {
        return new TableauDeBordVue(bureaux.count(), utilisateurs.countByRole(Role.CONSERVATEUR),
                utilisateurs.countByRole(Role.NOTAIRE), demandes.countByStatut(StatutDemande.EN_COURS),
                journal.dernieresOperations());
    }
}
