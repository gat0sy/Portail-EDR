package sn.portailedr.web;

import java.io.IOException;
import java.security.Principal;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import sn.portailedr.domaine.TypeResultat;
import sn.portailedr.service.ConservateurService;
import sn.portailedr.service.DemandeIntrouvableException;
import sn.portailedr.service.FichierPiece;
import sn.portailedr.service.RegleMetierException;

/**
 * Espace conservateur (m3.html) : demandes du bureau, detail, pieces, decisions.
 * /conservateur/** est reserve au role CONSERVATEUR (SecurityConfig) ; le bureau est verifie
 * par ConservateurService. Une demande d'un autre bureau donne un 404.
 */
@Controller
@RequestMapping("/conservateur/demandes")
public class ConservateurController {

    private final ConservateurService service;

    public ConservateurController(ConservateurService service) {
        this.service = service;
    }

    /** @param statut filtre : SOUMISE, EN_COURS, DISPONIBLE, CLOTUREE ; absent ou inconnu : toutes */
    @GetMapping
    public String liste(@RequestParam(required = false) String statut, Principal conservateur, Model model) {
        return afficher(model, service.bureau(conservateur.getName(), statut, null));
    }

    @GetMapping("/{reference}")
    public String detail(@PathVariable String reference, @RequestParam(required = false) String statut,
            Principal conservateur, Model model) {
        return afficher(model, service.bureau(conservateur.getName(), statut, reference));
    }

    private static String afficher(Model model, Object bureau) {
        model.addAttribute("bureau", bureau);
        model.addAttribute("typesResultat", TypeResultat.values());
        return "conservateur";
    }

    /**
     * Une photo s'affiche dans le navigateur (inline) ; un PDF est telecharge (attachment).
     * Type deduit de l'extension stockee, nosniff : le navigateur ne devine pas un autre type.
     */
    @GetMapping("/{reference}/pieces/{id}")
    public ResponseEntity<Resource> piece(@PathVariable String reference, @PathVariable Long id,
            Principal conservateur) {
        FichierPiece fichier = service.ouvrirPiece(conservateur.getName(), reference, id);
        ContentDisposition disposition = (fichier.enLigne() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fichier.nomTelechargement()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fichier.typeDeContenu()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(fichier.chemin()));
    }

    // ---------------------------------------------------------------- decisions

    @PostMapping("/{reference}/conformite")
    public String conformite(@PathVariable String reference, Principal conservateur, RedirectAttributes redirection) {
        return decider(reference, redirection, "Dossier déclaré conforme : la demande est en cours.",
                () -> service.declarerConforme(conservateur.getName(), reference));
    }

    @PostMapping("/{reference}/rejet")
    public String rejet(@PathVariable String reference, Principal conservateur, RedirectAttributes redirection) {
        return decider(reference, redirection, "Demande rejetée. Le demandeur est notifié.",
                () -> service.rejeter(conservateur.getName(), reference));
    }

    @PostMapping("/{reference}/publication")
    public String publication(@PathVariable String reference, @RequestParam(required = false) String typeResultat,
            @RequestParam(required = false) MultipartFile fichier, Principal conservateur,
            RedirectAttributes redirection) {
        return decider(reference, redirection,
                "Résultat publié : la demande est disponible et le demandeur est notifié.",
                () -> service.publier(conservateur.getName(), reference, typeResultat, fichier));
    }

    /** Refus metier : message en francais sur la page de la demande. La transaction est deja annulee. */
    private static String decider(String reference, RedirectAttributes redirection, String succes, Runnable decision) {
        try {
            decision.run();
            redirection.addFlashAttribute("message", succes);
        } catch (RegleMetierException refus) {
            redirection.addFlashAttribute("erreur", refus.getMessage());
        }
        return "redirect:/conservateur/demandes/" + reference;
    }

    /** Demande ou piece hors du bureau (ou inexistante) : la page d'erreur 404 habituelle. */
    @ExceptionHandler(DemandeIntrouvableException.class)
    public void introuvable(HttpServletResponse reponse) throws IOException {
        reponse.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
