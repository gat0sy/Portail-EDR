package sn.portailedr.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import sn.portailedr.service.BureauAdministrationService;
import sn.portailedr.service.BureauFormulaire;
import sn.portailedr.service.LivreFormulaire;
import sn.portailedr.service.RegleMetierException;

/**
 * Page Bureaux de conservation (admin-bureaux.html du prototype).
 * Apres une action reussie : redirection vers la page (POST-redirection-GET) avec un message.
 * Apres une erreur de saisie : la page est affichee de nouveau avec les messages sous les champs.
 */
@Controller
@RequestMapping("/admin/bureaux")
public class BureauxAdminController {

    private static final String PAGE = "redirect:/admin/bureaux";

    private final BureauAdministrationService service;

    public BureauxAdminController(BureauAdministrationService service) {
        this.service = service;
    }

    /** @param id bureau a modifier ; absent : formulaire de creation */
    @GetMapping
    public String page(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            try {
                model.addAttribute("bureau", service.formulaire(id));
            } catch (RegleMetierException refus) {
                model.addAttribute("erreur", refus.getMessage());
                id = null;
            }
        }
        return afficher(model, id);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("bureau") BureauFormulaire formulaire, BindingResult resultat,
            Principal administrateur, Model model, RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            return afficher(model, null);
        }
        service.creerBureau(formulaire, administrateur.getName());
        redirection.addFlashAttribute("message", "Bureau enregistré.");
        return PAGE;
    }

    @PostMapping("/{id}")
    public String modifier(@PathVariable Long id, @Valid @ModelAttribute("bureau") BureauFormulaire formulaire,
            BindingResult resultat, Principal administrateur, Model model, RedirectAttributes redirection) {
        if (!resultat.hasErrors()) {
            try {
                service.modifierBureau(id, formulaire, administrateur.getName());
                redirection.addFlashAttribute("message", "Bureau enregistré.");
                return PAGE;
            } catch (RegleMetierException refus) {
                ErreursFormulaire.signaler(resultat, refus);
            }
        }
        return afficher(model, id);
    }

    @PostMapping("/{id}/suppression")
    public String supprimer(@PathVariable Long id, Principal administrateur, RedirectAttributes redirection) {
        try {
            service.supprimerBureau(id, administrateur.getName());
            redirection.addFlashAttribute("message", "Bureau supprimé.");
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return PAGE;
    }

    /** Le bureau est choisi dans le formulaire (liste deroulante), d'ou /livres et non /{id}/livres. */
    @PostMapping("/livres")
    public String ajouterLivre(@Valid @ModelAttribute("livre") LivreFormulaire formulaire, BindingResult resultat,
            Principal administrateur, Model model, RedirectAttributes redirection) {
        if (!resultat.hasErrors()) {
            try {
                service.ajouterLivre(formulaire, administrateur.getName());
                redirection.addFlashAttribute("message", "Livre foncier enregistré.");
                return PAGE;
            } catch (RegleMetierException refus) {
                ErreursFormulaire.signaler(resultat, refus);
            }
        }
        return afficher(model, null);
    }

    @PostMapping("/livres/{id}/suppression")
    public String supprimerLivre(@PathVariable Long id, Principal administrateur, RedirectAttributes redirection) {
        try {
            service.supprimerLivre(id, administrateur.getName());
            redirection.addFlashAttribute("message", "Livre foncier supprimé.");
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return PAGE;
    }

    private String afficher(Model model, Long idModifie) {
        if (!model.containsAttribute("bureau")) {
            model.addAttribute("bureau", new BureauFormulaire());
        }
        if (!model.containsAttribute("livre")) {
            model.addAttribute("livre", new LivreFormulaire());
        }
        model.addAttribute("idModifie", idModifie);
        model.addAttribute("bureaux", service.bureaux());
        model.addAttribute("livres", service.livresFonciers());
        model.addAttribute("choixBureaux", service.choixBureaux());
        return "admin-bureaux";
    }
}
