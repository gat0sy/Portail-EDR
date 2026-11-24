package sn.portailedr.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Affiche le formulaire. L'envoi (POST /connexion) est traite par Spring Security. */
@Controller
public class ConnexionController {

    @GetMapping("/connexion")
    public String connexion() {
        return "connexion";
    }
}
