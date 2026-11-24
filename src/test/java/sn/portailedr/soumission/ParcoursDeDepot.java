package sn.portailedr.soumission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.portailedr.web.SoumissionEnCours;

/**
 * Les etapes du depot, telles qu'un navigateur les envoie. Chaque visiteur a sa propre
 * session (MockHttpSession) : c'est elle qui garde la demande en attente du code SMS.
 */
public final class ParcoursDeDepot {

    public static final String NUMERO_NGA = "12.405/NGA";

    private final MockMvcTester mvc;

    public ParcoursDeDepot(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    /** Champs d'une demande de particulier sur un bien, tous valides. */
    public static Map<String, String> champsBien(String numero) {
        Map<String, String> champs = champsDeclarant();
        champs.put("type", "BIEN");
        champs.put("numeroTitreFoncier", numero);
        return champs;
    }

    public static Map<String, String> champsPersonne(Long idBureau) {
        Map<String, String> champs = champsDeclarant();
        champs.put("type", "PERSONNE");
        champs.put("personneConcernee", "Moustapha Sarr");
        champs.put("idBureau", idBureau == null ? "" : idBureau.toString());
        return champs;
    }

    private static Map<String, String> champsDeclarant() {
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put("declarantNom", "Awa Ndiaye");
        champs.put("declarantTelephone", "+221 77 000 00 00");
        champs.put("declarantCni", "1 234 1990 01234");
        return champs;
    }

    /** POST du formulaire par un visiteur (utilisateur null) ou par un compte connecte. */
    public MvcTestResult formulaire(MockHttpSession session, UserDetails utilisateur, Map<String, String> champs,
            MockMultipartFile... fichiers) {
        MockMvcTester.MockMultipartMvcRequestBuilder requete = mvc.post().uri("/demandes/nouvelle").multipart();
        for (MockMultipartFile fichier : fichiers) {
            requete.file(fichier);
        }
        champs.forEach(requete::param);
        requete.with(csrf()).session(session);
        if (utilisateur != null) {
            requete.with(user(utilisateur));
        }
        return requete.exchange();
    }

    /** Demande de particulier sur un bien, avec les trois pieces requises. */
    public MvcTestResult bienParticulier(MockHttpSession session, String numero) {
        return formulaire(session, null, champsBien(numero),
                FichiersDeTest.jpg("cniRecto"), FichiersDeTest.png("cniVerso"), FichiersDeTest.pdf("copieTitreFoncier"));
    }

    public MvcTestResult code(MockHttpSession session, String code) {
        return mvc.post().uri("/demandes/nouvelle/verification").with(csrf()).session(session)
                .param("code", code).exchange();
    }

    public MvcTestResult renvoi(MockHttpSession session) {
        return mvc.post().uri("/demandes/nouvelle/verification/renvoi").with(csrf()).session(session).exchange();
    }

    public static SoumissionEnCours enCours(MockHttpSession session) {
        return (SoumissionEnCours) session.getAttribute(SoumissionEnCours.ATTRIBUT);
    }

    /** Le code "envoye par SMS" : le faux service ne fait que le journaliser, le test le lit dans la session. */
    public static String codeEnvoye(MockHttpSession session) {
        return enCours(session).verification().getCode();
    }

    /** Parcours complet d'un particulier ; renvoie la reference de la demande creee. */
    public String deposerParticulier(MockHttpSession session) {
        assertThat(bienParticulier(session, NUMERO_NGA)).hasRedirectedUrl("/demandes/nouvelle/verification");
        return reference(code(session, codeEnvoye(session)));
    }

    /** Demande d'un notaire sur une personne (aucune piece) ; renvoie la reference. */
    public String deposerNotaire(MockHttpSession session, UserDetails notaire, Long idBureau) {
        return reference(formulaire(session, notaire, champsPersonne(idBureau)));
    }

    /** Reference lue dans la redirection vers /demandes/{reference}/confirmation. */
    public static String reference(MvcTestResult resultat) {
        assertThat(resultat).hasStatus3xxRedirection();
        String url = resultat.getMvcResult().getResponse().getRedirectedUrl();
        assertThat(url).matches("/demandes/EDR-\\d{4}-[2-9A-HJKMNP-Z]{6}/confirmation");
        return url.substring("/demandes/".length(), url.length() - "/confirmation".length());
    }
}
