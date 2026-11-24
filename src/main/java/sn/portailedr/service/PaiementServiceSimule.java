package sn.portailedr.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Demande;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.domaine.Paiement;
import sn.portailedr.domaine.StatutPaiement;
import sn.portailedr.repository.PaiementRepository;

/** Paiement simule : aucun argent reel n'est debite, le paiement est toujours accepte. */
@Service
public class PaiementServiceSimule implements PaiementService {

    private static final Logger JOURNAL = LoggerFactory.getLogger(PaiementServiceSimule.class);

    private final PaiementRepository paiements;
    private final Clock horloge;

    public PaiementServiceSimule(PaiementRepository paiements, Clock horloge) {
        this.paiements = paiements;
        this.horloge = horloge;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Paiement payer(Demande demande, int montant, ModePaiement mode) {
        Paiement paiement = paiements.save(new Paiement(montant, mode, StatutPaiement.INITIE, horloge.instant(), demande));
        // un vrai prestataire serait appele ici ; la simulation accepte toujours
        paiement.setStatut(StatutPaiement.CONFIRME);
        paiement.setDatePaiement(horloge.instant());
        JOURNAL.info("Paiement simulé : {} FCFA par {} pour {} (aucun débit réel)", montant, mode, demande.getReference());
        return paiement;
    }
}
