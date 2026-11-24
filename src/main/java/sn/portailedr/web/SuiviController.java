package sn.portailedr.web;

import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.service.AccesSuivi;
import sn.portailedr.service.FichierResultat;
import sn.portailedr.service.RegleMetierException;
import sn.portailedr.service.SmsProprietes;
import sn.portailedr.service.SuiviService;
import sn.portailedr.service.SuiviService.Acces;
import sn.portailedr.service.VerificationRefuseeException;

/**
 * Suivi d'une demande (suivi-code.html, m2.html) : code de suivi, code SMS, page de suivi,
 * paiement simule et telechargement du resultat. Acces (SecurityConfig) : visiteur ou notaire ;
 * l'acces a UNE demande est decide par SuiviService.acces.
 * Etat garde en session : la reference qui attend son code SMS, et les references ouvertes (AccesSuivi).
 */
@Controller
@RequestMapping("/suivi")
public class SuiviController {

    static final String REFERENCE_EN_ATTENTE = "suiviReference";

    /** Meme message que la reference existe ou non : on ne peut pas sonder les references. */
    static final String MESSAGE_UNIFORME = "Si ce code existe, un SMS a été envoyé au numéro déclaré.";

    private static final String SAISIE = "redirect:/suivi";
    private static final String VERIFICATION = "redirect:/suivi/verification";

    private final SuiviService service;
    private final SmsProprietes sms;

    public SuiviController(SuiviService service, SmsProprietes sms) {
        this.service = service;
        this.sms = sms;
    }

    // ---------------------------------------------------------------- code de suivi

    /** @param reference pre-remplit le champ (lien "Suivre cette demande" de la confirmation) */
    @GetMapping
    public String saisie(@RequestParam(required = false) String reference, Model model) {
        model.addAttribute("reference", reference == null ? "" : reference);
        return "suivi-code";
    }

    @PostMapping
    public String demanderCode(@RequestParam(required = false) String reference, HttpSession session, Model model,
            RedirectAttributes redirection) {
        Optional<String> lue = SuiviService.lireReference(reference);
        if (lue.isEmpty()) {
            model.addAttribute("reference", reference == null ? "" : reference);
            model.addAttribute("erreur", "Code de suivi invalide. Format attendu : EDR-2026-K7P4QX");
            return "suivi-code";
        }
        // un refus (delai de renvoi, envois epuises) n'est pas affiche ici : la reponse reste la meme
        service.demanderCode(lue.get());
        session.setAttribute(REFERENCE_EN_ATTENTE, lue.get());
        redirection.addFlashAttribute("message", MESSAGE_UNIFORME);
        return VERIFICATION;
    }

    // ---------------------------------------------------------------- code SMS

    @GetMapping("/verification")
    public String verification(HttpSession session, Model model) {
        String reference = (String) session.getAttribute(REFERENCE_EN_ATTENTE);
        if (reference == null) {
            return SAISIE;
        }
        model.addAttribute("reference", reference);
        model.addAttribute("longueurCode", sms.longueurCode());
        return "suivi-verification";
    }

    @PostMapping("/verification")
    public String verifier(@RequestParam(required = false) String code, HttpSession session,
            RedirectAttributes redirection) {
        String reference = (String) session.getAttribute(REFERENCE_EN_ATTENTE);
        if (reference == null) {
            return SAISIE;
        }
        try {
            service.verifierCode(reference, code, accesSuivi(session));
            session.removeAttribute(REFERENCE_EN_ATTENTE);
            return "redirect:/suivi/" + reference;
        } catch (VerificationRefuseeException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
            if (refus.isDefinitive()) {
                session.removeAttribute(REFERENCE_EN_ATTENTE);
                return SAISIE;
            }
            return VERIFICATION;
        }
    }

    @PostMapping("/verification/renvoi")
    public String renvoyer(HttpSession session, RedirectAttributes redirection) {
        String reference = (String) session.getAttribute(REFERENCE_EN_ATTENTE);
        if (reference == null) {
            return SAISIE;
        }
        service.demanderCode(reference).ifPresentOrElse(
                refus -> redirection.addFlashAttribute("erreur", refus),
                () -> redirection.addFlashAttribute("message", MESSAGE_UNIFORME));
        return VERIFICATION;
    }

    // ---------------------------------------------------------------- page de suivi

    @GetMapping("/{reference}")
    public String page(@PathVariable String reference, Principal connecte, HttpSession session, Model model) {
        if (acces(reference, connecte, session) == Acces.VERIFICATION_REQUISE) {
            return SAISIE;
        }
        model.addAttribute("suivi", service.vue(reference));
        model.addAttribute("modes", ModePaiement.values());
        return "suivi";
    }

    @PostMapping("/{reference}/paiement")
    public String payer(@PathVariable String reference, @RequestParam(required = false) String modePaiement,
            Principal connecte, HttpSession session, RedirectAttributes redirection) {
        if (acces(reference, connecte, session) == Acces.VERIFICATION_REQUISE) {
            return SAISIE;
        }
        try {
            service.payer(reference, mode(modePaiement), connecte == null ? null : connecte.getName());
            redirection.addFlashAttribute("message", "Paiement simulé confirmé. Vous pouvez télécharger le résultat.");
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:/suivi/" + reference;
    }

    /** Valeur inconnue ou absente : null, que le service refuse avec un message. */
    private static ModePaiement mode(String saisi) {
        return Arrays.stream(ModePaiement.values()).filter(mode -> mode.name().equals(saisi)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- telechargement

    /**
     * Le fichier, en piece jointe (Content-Disposition: attachment), avec le type deduit de
     * l'extension stockee et nosniff (le navigateur ne devine pas un autre type).
     * Un refus renvoie a la page de suivi avec le message, comme les autres actions.
     */
    @GetMapping("/{reference}/resultat")
    public ResponseEntity<Resource> telecharger(@PathVariable String reference, Principal connecte,
            HttpSession session, HttpServletRequest requete, HttpServletResponse reponse) {
        if (acces(reference, connecte, session) == Acces.VERIFICATION_REQUISE) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/suivi")).build();
        }
        try {
            FichierResultat fichier = service.telecharger(reference, connecte == null ? null : connecte.getName());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fichier.typeDeContenu()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(fichier.nomTelechargement()).build().toString())
                    // Spring Security l'ajoute deja a toutes les reponses ; ecrit ici pour le rendre explicite
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new FileSystemResource(fichier.chemin()));
        } catch (RegleMetierException refus) {
            String page = "/suivi/" + reference;
            RequestContextUtils.getOutputFlashMap(requete).put("erreur", refus.getMessage());
            RequestContextUtils.saveOutputFlashMap(page, requete, reponse);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(page)).build();
        }
    }

    // ---------------------------------------------------------------- acces

    /** INTROUVABLE devient un 404 : un notaire ne sait pas si la demande d'un autre existe. */
    private Acces acces(String reference, Principal connecte, HttpSession session) {
        Acces acces = service.acces(reference, (AccesSuivi) session.getAttribute(AccesSuivi.ATTRIBUT),
                connecte == null ? null : connecte.getName());
        if (acces == Acces.INTROUVABLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return acces;
    }

    private static AccesSuivi accesSuivi(HttpSession session) {
        AccesSuivi acces = (AccesSuivi) session.getAttribute(AccesSuivi.ATTRIBUT);
        if (acces == null) {
            acces = new AccesSuivi();
            session.setAttribute(AccesSuivi.ATTRIBUT, acces);
        }
        return acces;
    }
}
