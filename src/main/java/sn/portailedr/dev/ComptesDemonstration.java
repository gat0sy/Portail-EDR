package sn.portailedr.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Bureau;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.BureauRepository;
import sn.portailedr.repository.UtilisateurRepository;

/**
 * DEVELOPPEMENT UNIQUEMENT (profil "dev") : cree au demarrage un compte FICTIF par role,
 * repris des maquettes du prototype, s'il n'existe pas deja. Ces personnes et ces adresses
 * (domaine exemple.sn) n'existent pas. Ce composant n'est jamais cree hors du profil dev,
 * donc jamais en production.
 */
@Component
@Profile("dev")
public class ComptesDemonstration implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ComptesDemonstration.class);

    private final UtilisateurRepository utilisateurs;
    private final BureauRepository bureaux;
    private final PasswordEncoder encodeur;
    private final String motDePasse;

    public ComptesDemonstration(UtilisateurRepository utilisateurs, BureauRepository bureaux, PasswordEncoder encodeur,
            @Value("${portail.demo.mot-de-passe}") String motDePasse) {
        this.utilisateurs = utilisateurs;
        this.bureaux = bureaux;
        this.encodeur = encodeur;
        this.motDePasse = motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Bureau dakarPlateau = bureaux.findByNom("Bureau de Dakar-Plateau").orElseThrow();
        creerSiAbsent("Me Mariama Fall", "m.fall@exemple.sn", "+221 33 000 01 01", Role.NOTAIRE, null);
        creerSiAbsent("Moussa Diop", "m.diop@exemple.sn", "+221 33 000 00 01", Role.CONSERVATEUR, dakarPlateau);
        creerSiAbsent("Aïssatou Ba", "a.ba@exemple.sn", "+221 33 000 00 00", Role.ADMINISTRATEUR, null);
    }

    private void creerSiAbsent(String nom, String email, String telephone, Role role, Bureau bureau) {
        if (utilisateurs.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        utilisateurs.save(new Utilisateur(nom, email, telephone, encodeur.encode(motDePasse), role, bureau));
        LOG.info("Compte de demonstration (fictif) cree : {} ({})", email, role.getLibelle());
    }
}
