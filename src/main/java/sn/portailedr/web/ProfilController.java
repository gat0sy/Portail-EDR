package sn.portailedr.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sn.portailedr.service.ProfilService;
import sn.portailedr.service.SoumissionService;

@Controller
public class ProfilController {

    private final ProfilService profilService;
    private final SoumissionService soumissionService;

    public ProfilController(ProfilService profilService, SoumissionService soumissionService) {
        this.profilService = profilService;
        this.soumissionService = soumissionService;
    }

    /** Principal.getName() est l'adresse e-mail du compte connecte. */
    @GetMapping("/profil")
    public String profil(Principal principal, Model model) {
        model.addAttribute("profil", profilService.profil(principal.getName()));
        // vide pour un conservateur ou un administrateur ; la page ne l'affiche qu'a un notaire
        model.addAttribute("demandes", soumissionService.dernieresDemandes(principal.getName()));
        return "profil";
    }
}
