package sn.portailedr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.portailedr.domaine.Utilisateur;
import sn.portailedr.repository.UtilisateurRepository;

@Service
public class ProfilService {

    private final UtilisateurRepository utilisateurs;

    public ProfilService(UtilisateurRepository utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    /** Le bureau (association paresseuse) est lu ici, dans la transaction, pas dans la vue. */
    @Transactional(readOnly = true)
    public ProfilVue profil(String email) {
        Utilisateur utilisateur = utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Compte connecte introuvable"));
        String bureau = utilisateur.getBureau() == null ? null : utilisateur.getBureau().getNom();
        return new ProfilVue(utilisateur.getNom(), utilisateur.getEmail(), utilisateur.getTelephone(),
                utilisateur.getRole(), bureau);
    }
}
