package sn.portailedr.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numero de titre foncier lu dans le formulaire, par exemple 12.405/NGA.
 * Le code apres la barre est celui du livre foncier ou le titre est inscrit :
 * c'est lui qui donne le bureau competent (table LIVRE_FONCIER).
 *
 * @param chiffres numero sans separateur (12405)
 * @param code     code du livre foncier, en majuscules (NGA)
 */
public record NumeroTitreFoncier(String chiffres, String code) {

    /** Expression du cahier des charges : chiffres groupes par 3 (espace ou point) ou non, barre, 1 a 4 lettres. */
    private static final Pattern FORMAT =
            Pattern.compile("^\\s*(\\d{1,3}(?:[ .]?\\d{3})*|\\d+)\\s*/\\s*([A-Za-z]{1,4})\\s*$");

    public static Optional<NumeroTitreFoncier> lire(String saisi) {
        if (saisi == null) {
            return Optional.empty();
        }
        Matcher lecture = FORMAT.matcher(saisi);
        if (!lecture.matches()) {
            return Optional.empty();
        }
        String chiffres = lecture.group(1).replaceAll("[ .]", "");
        return Optional.of(new NumeroTitreFoncier(chiffres, lecture.group(2).toUpperCase(Locale.ROOT)));
    }

    /** Forme enregistree : 12405/NGA (contrainte ck_demande_numero_titre_foncier). */
    public String normalise() {
        return chiffres + "/" + code;
    }
}
