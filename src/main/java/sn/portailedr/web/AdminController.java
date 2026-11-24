package sn.portailedr.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sn.portailedr.service.TableauDeBordService;

/** Tableau de bord de l'administrateur. L'acces a /admin/** est reserve par SecurityConfig. */
@Controller
public class AdminController {

    private final TableauDeBordService tableauDeBordService;

    public AdminController(TableauDeBordService tableauDeBordService) {
        this.tableauDeBordService = tableauDeBordService;
    }

    @GetMapping("/admin")
    public String tableauDeBord(Model model) {
        model.addAttribute("tableau", tableauDeBordService.tableauDeBord());
        return "admin";
    }
}
