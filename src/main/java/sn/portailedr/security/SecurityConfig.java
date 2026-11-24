package sn.portailedr.security;

import java.util.Map;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.ExceptionMappingAuthenticationFailureHandler;

import sn.portailedr.repository.UtilisateurRepository;

/**
 * Regles d'acces et connexion par formulaire.
 * Refus par defaut : une URL sans regle explicite est refusee (anyRequest().denyAll()).
 * CSRF reste active (valeur par defaut de Spring Security) et la session est cote serveur.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, UtilisateurRepository utilisateurs) throws Exception {
        http
            // un compte desactive ou modifie perd sa session des la requete suivante (voir CompteActifFilter) ;
            // le filtre n'est pas un bean, pour que Spring Boot ne l'enregistre pas une seconde fois
            .addFilterBefore(new CompteActifFilter(utilisateurs), AuthorizationFilter.class)
            .authorizeHttpRequests(acces -> acces
                // feuilles de style et autres fichiers statiques
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                // pages publiques ("/suivi/**" couvre aussi "/suivi")
                .requestMatchers("/", "/connexion", "/annuaire", "/suivi/**", "/demandes/nouvelle/**").permitAll()
                // page d'erreur de Spring Boot et sonde de sante
                .requestMatchers("/error", "/actuator/health").permitAll()
                .requestMatchers("/profil").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMINISTRATEUR")
                .requestMatchers("/conservateur/**").hasRole("CONSERVATEUR")
                .anyRequest().denyAll())
            .formLogin(connexion -> connexion
                .loginPage("/connexion")
                .loginProcessingUrl("/connexion")
                .defaultSuccessUrl("/profil")
                .failureHandler(echecDeConnexion())
                .permitAll())
            .logout(deconnexion -> deconnexion
                .logoutUrl("/deconnexion")
                .logoutSuccessUrl("/")
                .deleteCookies("JSESSIONID"));
        return http.build();
    }

    /**
     * Compte desactive : /connexion?desactive. Tout autre echec (adresse inconnue,
     * mauvais mot de passe) : /connexion?erreur, avec le meme message dans les deux cas.
     */
    private ExceptionMappingAuthenticationFailureHandler echecDeConnexion() {
        ExceptionMappingAuthenticationFailureHandler gestionnaire = new ExceptionMappingAuthenticationFailureHandler();
        gestionnaire.setDefaultFailureUrl("/connexion?erreur");
        gestionnaire.setExceptionMappings(Map.of(DisabledException.class.getName(), "/connexion?desactive"));
        return gestionnaire;
    }

    /**
     * Par defaut, Spring Security verifie si le compte est actif AVANT le mot de passe :
     * on pourrait alors savoir qu'un compte desactive existe sans connaitre son mot de passe.
     * Ici le statut du compte n'est verifie qu'APRES un mot de passe correct.
     */
    @Bean
    DaoAuthenticationProvider fournisseurAuthentification(UtilisateurDetailsService comptes, PasswordEncoder encodeur) {
        DaoAuthenticationProvider fournisseur = new DaoAuthenticationProvider(comptes);
        fournisseur.setPasswordEncoder(encodeur);
        fournisseur.setPreAuthenticationChecks(compte -> { });
        fournisseur.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return fournisseur;
    }

    @Bean
    PasswordEncoder encodeurMotDePasse() {
        return new BCryptPasswordEncoder();
    }
}
