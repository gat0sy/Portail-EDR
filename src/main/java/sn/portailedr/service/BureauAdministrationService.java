package sn.portailedr.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.LivreFoncier;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.DemandeRepository;
import sn.portailedr.repository.LivreFoncierRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * Bureaux de conservation et livres fonciers, geres par l'administrateur.
 * Chaque action est une transaction : la modification et sa ligne de journal sont
 * enregistrees ensemble, ou annulees ensemble si une regle refuse l'action.
 */
@Service
public class BureauAdministrationService {

    private final BureauRepository bureaux;
    private final LivreFoncierRepository livresFonciers;
    private final UtilisateurRepository utilisateurs;
    private final DemandeRepository demandes;
    private final JournalService journal;

    public BureauAdministrationService(BureauRepository bureaux, LivreFoncierRepository livresFonciers,
            UtilisateurRepository utilisateurs, DemandeRepository demandes, JournalService journal) {
        this.bureaux = bureaux;
        this.livresFonciers = livresFonciers;
        this.utilisateurs = utilisateurs;
        this.demandes = demandes;
        this.journal = journal;
    }

    // ---------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public List<BureauLigne> bureaux() {
        Map<Long, List<String>> codesParBureau = livresFonciers.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.groupingBy(livre -> livre.getBureau().getId(),
                        Collectors.mapping(LivreFoncier::getCode, Collectors.toList())));
        return bureaux.findAll(Sort.by("nom")).stream()
                .map(bureau -> new BureauLigne(bureau.getId(), bureau.getNom(), bureau.getAdresse(),
                        codesParBureau.getOrDefault(bureau.getId(), List.of()),
                        utilisateurs.countByBureauIdAndRole(bureau.getId(), Role.CONSERVATEUR),
                        demandes.countByBureauIdAndStatut(bureau.getId(), StatutDemande.EN_COURS)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LivreLigne> livresFonciers() {
        return livresFonciers.findAllByOrderByCodeAsc().stream()
                .map(livre -> new LivreLigne(livre.getId(), livre.getCode(), livre.getNom(), livre.getBureau().getNom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BureauChoix> choixBureaux() {
        return bureaux.findAll(Sort.by("nom")).stream()
                .map(bureau -> new BureauChoix(bureau.getId(), bureau.getNom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BureauFormulaire formulaire(Long idBureau) {
        Bureau bureau = trouverBureau(idBureau);
        BureauFormulaire formulaire = new BureauFormulaire();
        formulaire.setNom(bureau.getNom());
        formulaire.setAdresse(bureau.getAdresse());
        formulaire.setTelephone(bureau.getTelephone());
        return formulaire;
    }

    // ---------------------------------------------------------------- bureaux

    @Transactional
    public void creerBureau(BureauFormulaire formulaire, String emailActeur) {
        Bureau bureau = bureaux.save(new Bureau(formulaire.getNom().trim(), formulaire.getAdresse().trim(),
                formulaire.getTelephone().trim()));
        journal.journaliser("Création du bureau " + bureau.getNom(), acteur(emailActeur), null);
    }

    @Transactional
    public void modifierBureau(Long idBureau, BureauFormulaire formulaire, String emailActeur) {
        Bureau bureau = trouverBureau(idBureau);
        bureau.setNom(formulaire.getNom().trim());
        bureau.setAdresse(formulaire.getAdresse().trim());
        bureau.setTelephone(formulaire.getTelephone().trim());
        journal.journaliser("Modification du bureau " + bureau.getNom(), acteur(emailActeur), null);
    }

    /** Refuse tant que le bureau tient des livres fonciers, a des demandes ou des conservateurs affectes. */
    @Transactional
    public void supprimerBureau(Long idBureau, String emailActeur) {
        Bureau bureau = trouverBureau(idBureau);
        if (livresFonciers.existsByBureauId(idBureau)) {
            throw new RegleMetierException("Suppression impossible : ce bureau tient encore des livres fonciers.");
        }
        if (demandes.existsByBureauId(idBureau)) {
            throw new RegleMetierException("Suppression impossible : des demandes sont rattachées à ce bureau.");
        }
        if (utilisateurs.existsByBureauId(idBureau)) {
            throw new RegleMetierException("Suppression impossible : des conservateurs sont encore affectés à ce bureau.");
        }
        journal.journaliser("Suppression du bureau " + bureau.getNom(), acteur(emailActeur), null);
        bureaux.delete(bureau);
    }

    // ---------------------------------------------------------------- livres fonciers

    /** Le code est mis en majuscules ; il est unique tous bureaux confondus. */
    @Transactional
    public void ajouterLivre(LivreFormulaire formulaire, String emailActeur) {
        String code = formulaire.getCode().trim().toUpperCase(Locale.ROOT);
        Bureau bureau = formulaire.getIdBureau() == null ? null : bureaux.findById(formulaire.getIdBureau()).orElse(null);
        if (bureau == null) {
            throw new RegleMetierException("Choisissez le bureau qui tient ce livre.", "idBureau");
        }
        livresFonciers.findByCode(code).ifPresent(existant -> {
            throw new RegleMetierException(
                    "Le code " + code + " est déjà utilisé par le " + existant.getBureau().getNom() + ".", "code");
        });
        livresFonciers.save(new LivreFoncier(code, formulaire.getNom().trim(), bureau));
        journal.journaliser("Ajout du livre foncier " + code + " au " + bureau.getNom(), acteur(emailActeur), null);
    }

    @Transactional
    public void supprimerLivre(Long idLivre, String emailActeur) {
        LivreFoncier livre = livresFonciers.findById(idLivre)
                .orElseThrow(() -> new RegleMetierException("Livre foncier introuvable."));
        journal.journaliser("Suppression du livre foncier " + livre.getCode() + " du " + livre.getBureau().getNom(),
                acteur(emailActeur), null);
        livresFonciers.delete(livre);
    }

    // ---------------------------------------------------------------- outils

    private Bureau trouverBureau(Long idBureau) {
        return bureaux.findById(idBureau).orElseThrow(() -> new RegleMetierException("Bureau introuvable."));
    }

    private Utilisateur acteur(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Administrateur connecte introuvable"));
    }
}
