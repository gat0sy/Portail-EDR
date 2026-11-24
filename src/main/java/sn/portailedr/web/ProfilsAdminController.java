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
import sn.portailedr.domaine.Role;
import sn.portailedr.service.BureauAdministrationService;
import sn.portailedr.service.MotDePasseFormulaire;
import sn.portailedr.service.NouveauProfilFormulaire;
import sn.portailedr.service.ProfilAdministrationService;
import sn.portailedr.service.ProfilFormulaire;
import sn.portailedr.service.RegleMetierException;

/** Page Profils (admin-profils.html du prototype) : comptes, affectations, mots de passe provisoires. */
@Controller
@RequestMapping("/admin/profils")
public class ProfilsAdminController {

    private static final String PAGE = "redirect:/admin/profils";

    private final ProfilAdministrationService service;
    private final BureauAdministrationService bureauService;

    public ProfilsAdminController(ProfilAdministrationService service, BureauAdministrationService bureauService) {
        this.service = service;
        this.bureauService = bureauService;
    }

    /**
     * @param id   compte a modifier ; absent : formulaire de creation
     * @param role filtre de la liste ; absent : tous les comptes
     */
    @GetMapping
    public String page(@RequestParam(required = false) Long id, @RequestParam(required = false) Role role,
            Principal administrateur, Model model) {
        if (id != null) {
            try {
                model.addAttribute("profil", service.formulaire(id));
            } catch (RegleMetierException refus) {
                model.addAttribute("erreur", refus.getMessage());
                id = null;
            }
        }
        return afficher(model, id, role, administrateur);
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("nouveauProfil") NouveauProfilFormulaire formulaire,
            BindingResult resultat, Principal administrateur, Model model, RedirectAttributes redirection) {
        if (!resultat.hasErrors()) {
            try {
                service.creer(formulaire, administrateur.getName());
                redirection.addFlashAttribute("message", "Profil créé.");
                return PAGE;
            } catch (RegleMetierException refus) {
                ErreursFormulaire.signaler(resultat, refus);
            }
        }
        return afficher(model, null, null, administrateur);
    }

    @PostMapping("/{id}")
    public String modifier(@PathVariable Long id, @Valid @ModelAttribute("profil") ProfilFormulaire formulaire,
            BindingResult resultat, Principal administrateur, Model model, RedirectAttributes redirection) {
        if (!resultat.hasErrors()) {
            try {
                service.modifier(id, formulaire, administrateur.getName());
                redirection.addFlashAttribute("message", "Profil enregistré.");
                return PAGE;
            } catch (RegleMetierException refus) {
                ErreursFormulaire.signaler(resultat, refus);
            }
        }
        return afficher(model, id, null, administrateur);
    }

    @PostMapping("/{id}/mot-de-passe")
    public String reinitialiserMotDePasse(@PathVariable Long id,
            @Valid @ModelAttribute("motDePasse") MotDePasseFormulaire formulaire, BindingResult resultat,
            Principal administrateur, Model model, RedirectAttributes redirection) {
        if (resultat.hasErrors()) {
            model.addAttribute("profil", service.formulaire(id));
            return afficher(model, id, null, administrateur);
        }
        service.reinitialiserMotDePasse(id, formulaire, administrateur.getName());
        redirection.addFlashAttribute("message",
                "Mot de passe provisoire enregistré. Communiquez-le à l'utilisateur.");
        return PAGE;
    }

    @PostMapping("/{id}/desactivation")
    public String desactiver(@PathVariable Long id, Principal administrateur, RedirectAttributes redirection) {
        try {
            service.desactiver(id, administrateur.getName());
            redirection.addFlashAttribute("message", "Compte désactivé.");
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/{id}/reactivation")
    public String reactiver(@PathVariable Long id, Principal administrateur, RedirectAttributes redirection) {
        try {
            service.reactiver(id, administrateur.getName());
            redirection.addFlashAttribute("message", "Compte réactivé.");
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return PAGE;
    }

    private String afficher(Model model, Long idModifie, Role filtre, Principal administrateur) {
        if (!model.containsAttribute("nouveauProfil")) {
            model.addAttribute("nouveauProfil", new NouveauProfilFormulaire());
        }
        if (!model.containsAttribute("motDePasse")) {
            model.addAttribute("motDePasse", new MotDePasseFormulaire());
        }
        model.addAttribute("idModifie", idModifie);
        model.addAttribute("filtre", filtre);
        model.addAttribute("profils", service.profils(filtre, administrateur.getName()));
        model.addAttribute("nombreTotal", service.nombre(null));
        model.addAttribute("nombreNotaires", service.nombre(Role.NOTAIRE));
        model.addAttribute("nombreConservateurs", service.nombre(Role.CONSERVATEUR));
        model.addAttribute("nombreAdministrateurs", service.nombre(Role.ADMINISTRATEUR));
        model.addAttribute("roles", Role.values());
        model.addAttribute("choixBureaux", bureauService.choixBureaux());
        return "admin-profils";
    }
}
