package sn.portailedr.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.LivreFoncier;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.LivreFoncierRepository;

/** Annuaire public : lit seulement BUREAU et LIVRE_FONCIER, jamais les comptes. */
@Service
public class AnnuaireService {

    private final BureauRepository bureaux;
    private final LivreFoncierRepository livresFonciers;

    public AnnuaireService(BureauRepository bureaux, LivreFoncierRepository livresFonciers) {
        this.bureaux = bureaux;
        this.livresFonciers = livresFonciers;
    }

    /**
     * @param recherche texte cherche dans le nom, l'adresse et les codes des livres fonciers,
     *                  sans tenir compte de la casse ni des accents ; vide ou null : tous les bureaux
     */
    @Transactional(readOnly = true)
    public List<AnnuaireBureau> bureaux(String recherche) {
        Map<Long, List<String>> codesParBureau = livresFonciers.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.groupingBy(livre -> livre.getBureau().getId(),
                        Collectors.mapping(LivreFoncier::getCode, Collectors.toList())));
        String cherche = recherche == null ? "" : simplifier(recherche);
        return bureaux.findAll(Sort.by("nom")).stream()
                .map(bureau -> new AnnuaireBureau(bureau.getNom(), bureau.getAdresse(), bureau.getTelephone(),
                        codesParBureau.getOrDefault(bureau.getId(), List.of())))
                .filter(bureau -> cherche.isEmpty()
                        || simplifier(bureau.nom()).contains(cherche)
                        || simplifier(bureau.adresse()).contains(cherche)
                        || bureau.codes().stream().anyMatch(code -> simplifier(code).equals(cherche)))
                .toList();
    }

    /** "Thiès " devient "thies" : minuscules, sans accents, sans espaces autour. */
    private static String simplifier(String texte) {
        return Normalizer.normalize(texte.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
