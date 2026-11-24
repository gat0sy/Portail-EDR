package sn.portailedr.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Etat de la verification du telephone d'un particulier, garde dans sa session (jamais en base).
 * Regles : un code expire apres "validite" ; 3 essais par code ; un nouveau code peut etre
 * demande "delaiRenvoi" apres le precedent, "envoisMax" fois au total. Un nouveau code
 * remplace l'ancien et remet les essais a zero.
 *
 * L'heure est toujours passee en parametre : les tests verifient l'expiration sans attendre.
 */
public class VerificationSms implements Serializable {

    public enum Resultat { VALIDE, INCORRECT, EXPIRE, TENTATIVES_EPUISEES }

    private final String telephone;
    private final SmsProprietes regles;

    private String code;
    private Instant dateEnvoi;
    private int envois;
    private int essaisFaux;

    public VerificationSms(String telephone, SmsProprietes regles) {
        this.telephone = telephone;
        this.regles = regles;
    }

    public String getTelephone() {
        return telephone;
    }

    /** Le code envoye. Garde cote serveur, jamais affiche ; lu par les tests a la place du SMS. */
    public String getCode() {
        return code;
    }

    /** @throws IllegalStateException si le delai de renvoi ou le nombre d'envois ne le permet pas */
    public void enregistrerEnvoi(String nouveauCode, Instant maintenant) {
        if (envoisRestants() == 0 || secondesAvantRenvoi(maintenant) > 0) {
            throw new IllegalStateException("Envoi d'un code non permis");
        }
        code = nouveauCode;
        dateEnvoi = maintenant;
        envois++;
        essaisFaux = 0;
    }

    public int envoisRestants() {
        return regles.envoisMax() - envois;
    }

    /** 0 si un nouveau code peut etre envoye maintenant (toujours 0 avant le premier envoi). */
    public long secondesAvantRenvoi(Instant maintenant) {
        if (dateEnvoi == null) {
            return 0;
        }
        long restant = Duration.between(maintenant, dateEnvoi.plus(regles.delaiRenvoi())).toSeconds();
        return Math.max(0, restant);
    }

    public int tentativesRestantes() {
        return regles.tentativesMax() - essaisFaux;
    }

    /** Une saisie vide ou mal formee compte comme un essai faux. Un code valide ne sert qu'une fois. */
    public Resultat verifier(String saisi, Instant maintenant) {
        if (code == null || tentativesRestantes() <= 0) {
            return Resultat.TENTATIVES_EPUISEES;
        }
        if (aExpire(maintenant)) {
            return Resultat.EXPIRE;
        }
        if (saisi != null && egaux(saisi.trim(), code)) {
            code = null;
            return Resultat.VALIDE;
        }
        essaisFaux++;
        return tentativesRestantes() == 0 ? Resultat.TENTATIVES_EPUISEES : Resultat.INCORRECT;
    }

    /** Plus rien n'est possible : plus d'envoi, et le dernier code est bloque ou a expire. */
    public boolean estEpuisee(Instant maintenant) {
        return envoisRestants() == 0 && (tentativesRestantes() <= 0 || aExpire(maintenant));
    }

    private boolean aExpire(Instant maintenant) {
        return maintenant.isAfter(dateEnvoi.plus(regles.validite()));
    }

    /** Comparaison en temps constant : la duree de reponse ne renseigne pas sur les chiffres justes. */
    private static boolean egaux(String saisi, String attendu) {
        return MessageDigest.isEqual(saisi.getBytes(StandardCharsets.UTF_8), attendu.getBytes(StandardCharsets.UTF_8));
    }
}
