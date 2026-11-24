package sn.portailedr.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import sn.portailedr.service.RegleMetierException;

/**
 * Filet commun des pages d'administration : un refus de regle metier que le controleur
 * n'a pas traite lui-meme ne donne jamais une erreur 500. L'utilisateur revient sur la page
 * d'administration concernee, avec le message en francais (meme affichage que les autres refus).
 * La transaction du service a deja ete annulee, journal compris.
 */
@ControllerAdvice(assignableTypes = {AdminController.class, BureauxAdminController.class, ProfilsAdminController.class})
public class AdministrationExceptionHandler {

    @ExceptionHandler(RegleMetierException.class)
    public String regleMetierRefusee(RegleMetierException refus, HttpServletRequest requete) {
        // message transmis a la page suivante, comme les attributs "flash" des controleurs
        RequestContextUtils.getOutputFlashMap(requete).put("erreur", refus.getMessage());
        return "redirect:" + pageDAdministration(requete.getRequestURI());
    }

    private static String pageDAdministration(String chemin) {
        if (chemin.startsWith("/admin/bureaux")) {
            return "/admin/bureaux";
        }
        if (chemin.startsWith("/admin/profils")) {
            return "/admin/profils";
        }
        return "/admin";
    }
}
