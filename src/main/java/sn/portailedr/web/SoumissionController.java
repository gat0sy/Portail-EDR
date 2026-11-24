package sn.portailedr.web;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import sn.portailedr.service.DemandeEnAttente;
import sn.portailedr.service.DemandeFormulaire;
import sn.portailedr.service.DemandeInvalideException;
import sn.portailedr.service.SmsProprietes;
import sn.portailedr.service.SoumissionService;
import sn.portailedr.service.VerificationRefuseeException;

/**
 * Nouvelle demande (m1.html) et page "Demande soumise" (demande-soumise.html).
 * Acces (SecurityConfig) : visiteur ou notaire. Un visiteur passe par le code SMS,
 * un notaire connecte non. Etat garde en session :
 * <ul>
 *   <li>{@link SoumissionEnCours#ATTRIBUT} : la demande d'un particulier qui attend son code ;</li>
 *   <li>{@link #REFERENCES} : les references deposees depuis cette session, seules confirmations
 *       lisibles par un visiteur.</li>
 * </ul>
 */
@Controller
public class SoumissionController {

    static final String REFERENCES = "referencesSoumises";

    private static final String FORMULAIRE = "redirect:/demandes/nouvelle";
    private static final String VERIFICATION = "redirect:/demandes/nouvelle/verification";

    private final SoumissionService service;
    private final SmsProprietes sms;

    public SoumissionController(SoumissionService service, SmsProprietes sms) {
        this.service = service;
        this.sms = sms;
    }

    // ---------------------------------------------------------------- formulaire

    /** Au retour depuis l'etape SMS, les champs saisis sont repris (pas les fichiers). */
    @GetMapping("/demandes/nouvelle")
    public String formulaire(Principal connecte, HttpSession session, Model model) {
        SoumissionEnCours enCours = connecte == null ? enCours(session) : null;
        model.addAttribute("demande", enCours == null ? DemandeFormulaire.nouveau() : enCours.demande().versFormulaire());
        model.addAttribute("retourDeVerification", enCours != null);
        return afficher(model);
    }

    @PostMapping("/demandes/nouvelle")
    public String soumettre(@Valid @ModelAttribute("demande") DemandeFormulaire formulaire, BindingResult resultat,
            Principal connecte, HttpSession session, Model model) {
        if (resultat.hasErrors()) {
            return afficher(model);
        }
        try {
            if (connecte != null) {
                // seul un notaire est connecte ici (SecurityConfig) : pas de code SMS
                String reference = service.soumettreNotaire(formulaire, connecte.getName());
                memoriser(session, reference);
                return "redirect:/demandes/" + reference + "/confirmation";
            }
            DemandeEnAttente demande = service.preparer(formulaire, true);
            // remplace une eventuelle demande precedente, dont les fichiers sont alors supprimes
            session.setAttribute(SoumissionEnCours.ATTRIBUT,
                    new SoumissionEnCours(demande, service.demarrerVerification(demande)));
            return VERIFICATION;
        } catch (DemandeInvalideException refus) {
            refus.getErreurs().forEach(erreur -> resultat.rejectValue(erreur.champ(), "regle", erreur.message()));
            return afficher(model);
        }
    }

    private String afficher(Model model) {
        model.addAttribute("bureaux", service.bureaux());
        return "demande-nouvelle";
    }

    // ---------------------------------------------------------------- code SMS (visiteur)

    @GetMapping("/demandes/nouvelle/verification")
    public String verification(HttpSession session, Model model) {
        SoumissionEnCours enCours = enCours(session);
        if (enCours == null) {
            return FORMULAIRE;
        }
        model.addAttribute("demande", enCours.demande());
        model.addAttribute("longueurCode", sms.longueurCode());
        return "demande-verification";
    }

    @PostMapping("/demandes/nouvelle/verification")
    public String valider(@RequestParam(required = false) String code, HttpSession session,
            RedirectAttributes redirection) {
        SoumissionEnCours enCours = enCours(session);
        if (enCours == null) {
            return FORMULAIRE;
        }
        try {
            String reference = service.validerCode(enCours.demande(), enCours.verification(), code);
            session.removeAttribute(SoumissionEnCours.ATTRIBUT);
            memoriser(session, reference);
            return "redirect:/demandes/" + reference + "/confirmation";
        } catch (VerificationRefuseeException refus) {
            return refuser(refus, session, redirection);
        }
    }

    @PostMapping("/demandes/nouvelle/verification/renvoi")
    public String renvoyer(HttpSession session, RedirectAttributes redirection) {
        SoumissionEnCours enCours = enCours(session);
        if (enCours == null) {
            return FORMULAIRE;
        }
        try {
            service.renvoyerCode(enCours.verification());
            redirection.addFlashAttribute("message", "Un nouveau code vous a été envoyé.");
            return VERIFICATION;
        } catch (VerificationRefuseeException refus) {
            return refuser(refus, session, redirection);
        }
    }

    /** Refus definitif : la demande en attente est abandonnee (ses fichiers sont supprimes). */
    private static String refuser(VerificationRefuseeException refus, HttpSession session,
            RedirectAttributes redirection) {
        redirection.addFlashAttribute("erreur", refus.getMessage());
        if (refus.isDefinitive()) {
            session.removeAttribute(SoumissionEnCours.ATTRIBUT);
            return FORMULAIRE;
        }
        return VERIFICATION;
    }

    // ---------------------------------------------------------------- confirmation

    /** 404 pour une reference inconnue comme pour une demande d'un autre : rien n'est revele. */
    @GetMapping("/demandes/{reference}/confirmation")
    public String confirmation(@PathVariable String reference, Principal connecte, HttpSession session, Model model) {
        model.addAttribute("confirmation", service
                .confirmation(reference, references(session), connecte == null ? null : connecte.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "demande-soumise";
    }

    // ---------------------------------------------------------------- session

    private static SoumissionEnCours enCours(HttpSession session) {
        return (SoumissionEnCours) session.getAttribute(SoumissionEnCours.ATTRIBUT);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> references(HttpSession session) {
        Set<String> references = (Set<String>) session.getAttribute(REFERENCES);
        return references == null ? Set.of() : references;
    }

    private static void memoriser(HttpSession session, String reference) {
        Set<String> references = new HashSet<>(references(session));
        references.add(reference);
        session.setAttribute(REFERENCES, references);
    }
}
