package sn.portailedr.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import sn.portailedr.service.AnnuaireService;

/** Annuaire public des bureaux de conservation. Les notaires n'y figurent pas. */
@Controller
public class AnnuaireController {

    private final AnnuaireService annuaireService;
    private final String lienNotaires;

    /** @param lienNotaires lien vers la liste officielle des notaires ; vide : texte seul, sans lien */
    public AnnuaireController(AnnuaireService annuaireService,
            @Value("${portail.annuaire.lien-notaires:}") String lienNotaires) {
        this.annuaireService = annuaireService;
        this.lienNotaires = lienNotaires;
    }

    @GetMapping("/annuaire")
    public String annuaire(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("bureaux", annuaireService.bureaux(q));
        model.addAttribute("recherche", q == null ? "" : q.trim());
        model.addAttribute("lienNotaires", lienNotaires.isBlank() ? null : lienNotaires.trim());
        return "annuaire";
    }
}
