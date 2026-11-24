package sn.portailedr.web;

import org.springframework.validation.BindingResult;

import sn.portailedr.service.RegleMetierException;

/** Affiche le refus d'une regle metier sur le formulaire : sous le champ concerne, ou en tete du formulaire. */
final class ErreursFormulaire {

    private ErreursFormulaire() {
    }

    static void signaler(BindingResult resultat, RegleMetierException refus) {
        if (refus.getChamp() == null) {
            resultat.reject("regle", refus.getMessage());
        } else {
            resultat.rejectValue(refus.getChamp(), "regle", refus.getMessage());
        }
    }
}
