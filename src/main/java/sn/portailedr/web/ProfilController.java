package sn.portailedr.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sn.portailedr.service.ProfilService;

@Controller
public class ProfilController {

    private final ProfilService profilService;

    public ProfilController(ProfilService profilService) {
        this.profilService = profilService;
    }

    /** Principal.getName() est l'adresse e-mail du compte connecte. */
    @GetMapping("/profil")
    public String profil(Principal principal, Model model) {
        model.addAttribute("profil", profilService.profil(principal.getName()));
        return "profil";
    }
}
