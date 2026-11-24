package sn.portailedr.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.repository.UtilisateurRepository;

/** Charge le compte qui se connecte depuis la table UTILISATEUR, par son adresse e-mail. */
@Service
public class UtilisateurDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurs;

    public UtilisateurDetailsService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .map(CompteUtilisateur::new)
                // Spring Security transforme cette exception en "identifiants incorrects",
                // sans dire si l'adresse existe
                .orElseThrow(() -> new UsernameNotFoundException("Aucun compte pour cette adresse"));
    }
}
