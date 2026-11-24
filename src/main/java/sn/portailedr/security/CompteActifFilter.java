package sn.portailedr.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * A chaque requete d'un utilisateur connecte, relit son compte dans UTILISATEUR.
 * La session garde une copie du compte faite a la connexion ; sans ce filtre, un compte
 * desactive (ou dont le role a change) garderait l'acces jusqu'a la fin de sa session.
 *
 * - compte desactive : fin de la session, retour a /connexion?desactive ;
 * - compte introuvable (adresse modifiee) ou role modifie : fin de la session, retour a /connexion?modifie.
 *
 * Cout : une lecture par cle unique (adresse e-mail) par requete authentifiee.
 */
public class CompteActifFilter extends OncePerRequestFilter {

    private final UtilisateurRepository utilisateurs;
    private final SecurityContextLogoutHandler deconnexion = new SecurityContextLogoutHandler();

    public CompteActifFilter(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof CompteUtilisateur compte) {
            String motif = motifDeDeconnexion(compte);
            if (motif != null) {
                // invalide la session et vide le contexte de securite
                deconnexion.logout(requete, reponse, authentification);
                reponse.sendRedirect(requete.getContextPath() + "/connexion?" + motif);
                return;
            }
        }
        suite.doFilter(requete, reponse);
    }

    /** null si le compte en session correspond toujours au compte en base. */
    private String motifDeDeconnexion(CompteUtilisateur compte) {
        Optional<Utilisateur> enBase = utilisateurs.findByEmailIgnoreCase(compte.getUsername());
        if (enBase.isEmpty()) {
            return "modifie";
        }
        if (!enBase.get().isActif()) {
            return "desactive";
        }
        if (enBase.get().getRole() != compte.getRole()) {
            return "modifie";
        }
        return null;
    }
}
