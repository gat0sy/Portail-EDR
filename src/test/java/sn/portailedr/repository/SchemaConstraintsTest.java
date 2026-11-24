package sn.portailedr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import sn.portailedr.TestcontainersConfiguration;
import sn.portailedr.domaine.ModePaiement;
import sn.portailedr.domaine.Role;
import sn.portailedr.domaine.StatutDemande;
import sn.portailedr.domaine.StatutPaiement;
import sn.portailedr.domaine.TypeDemande;
import sn.portailedr.domaine.TypeNotification;
import sn.portailedr.domaine.TypePiece;
import sn.portailedr.domaine.TypeResultat;

/**
 * Contraintes de la base, testees en SQL direct (sans passer par JPA).
 * Chaque test part d'une ligne valide, change une seule colonne et verifie
 * le nom de la contrainte qui refuse la ligne. Chaque test est annule a la fin
 * (transaction de @DataJpaTest).
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SchemaConstraintsTest {

    @Autowired
    private JdbcClient jdbc;

    private long idBureau;

    @BeforeEach
    void bureauExistant() {
        idBureau = jdbc.sql("SELECT id_bureau FROM bureau ORDER BY id_bureau LIMIT 1").query(Long.class).single();
    }

    // ---------------------------------------------------------------- outils

    /** Insere une ligne et renvoie sa cle primaire. */
    private long inserer(String table, Map<String, Object> valeurs) {
        String colonnes = String.join(", ", valeurs.keySet());
        String parametres = ":" + String.join(", :", valeurs.keySet());
        return jdbc.sql("INSERT INTO " + table + " (" + colonnes + ") VALUES (" + parametres + ") RETURNING id_" + table)
                .params(valeurs)
                .query(Long.class)
                .single();
    }

    private static Map<String, Object> avec(Map<String, Object> base, Object... colonnesEtValeurs) {
        Map<String, Object> copie = new LinkedHashMap<>(base);
        for (int i = 0; i < colonnesEtValeurs.length; i += 2) {
            copie.put((String) colonnesEtValeurs[i], colonnesEtValeurs[i + 1]);
        }
        return copie;
    }

    private static void refuseePar(String contrainte, Runnable insertion) {
        assertThatThrownBy(insertion::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(contrainte);
    }

    /** Demande valide d'un particulier sur un bien. */
    private Map<String, Object> demandeParticulier() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("reference", "EDR-2026-K7P4QX");
        d.put("type", "BIEN");
        d.put("statut", "SOUMISE");
        d.put("numero_titre_foncier", "12405/NGA");
        d.put("personne_concernee", null);
        d.put("declarant_cni", "1751199900123");
        d.put("declarant_nom", "Awa Diop");
        d.put("declarant_telephone", "+221 77 000 00 00");
        d.put("telephone_verifie", true);
        d.put("date_soumission", OffsetDateTime.now());
        d.put("id_bureau", idBureau);
        d.put("id_utilisateur", null);
        return d;
    }

    private Map<String, Object> utilisateur(String role, Long bureau) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("nom", "Me Ndiaye");
        u.put("email", "ndiaye@example.sn");
        u.put("telephone", "+221 77 000 00 01");
        u.put("mot_de_passe", "{bcrypt}empreinte");
        u.put("role", role);
        u.put("actif", true);
        u.put("id_bureau", bureau);
        return u;
    }

    /** Demande valide d'un notaire sur une personne. */
    private Map<String, Object> demandeNotaire() {
        long notaire = inserer("utilisateur", utilisateur("NOTAIRE", null));
        return avec(demandeParticulier(),
                "reference", "EDR-2026-H4K7QM",
                "type", "PERSONNE",
                "numero_titre_foncier", null,
                "personne_concernee", "Moussa Fall",
                "declarant_cni", null,
                "telephone_verifie", false,
                "id_utilisateur", notaire);
    }

    private long demandeEnregistree() {
        return inserer("demande", demandeParticulier());
    }

    // ---------------------------------------------------------------- livre_foncier

    @Nested
    class LivreFoncier {

        private Map<String, Object> livre(String code) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("code", code);
            l.put("nom", "Livre de test");
            l.put("id_bureau", idBureau);
            return l;
        }

        @Test
        void codeValideAccepte() {
            assertThatCode(() -> inserer("livre_foncier", livre("DG"))).doesNotThrowAnyException();
        }

        @Test
        void codeDejaUtiliseRefuse() {
            // NGA est cree par la migration V2
            refuseePar("uq_livre_foncier_code", () -> inserer("livre_foncier", livre("NGA")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"nga", "Nga", "ABCDE", "", "N1", "N-A"})
        void codeMalFormeRefuse(String code) {
            refuseePar("ck_livre_foncier_code", () -> inserer("livre_foncier", livre(code)));
        }

        @Test
        void bureauTenantDesLivresNonSupprimable() {
            refuseePar("fk_livre_foncier_bureau",
                    () -> jdbc.sql("DELETE FROM bureau WHERE id_bureau = ?").param(idBureau).update());
        }
    }

    // ---------------------------------------------------------------- utilisateur

    @Nested
    class Utilisateur {

        @ParameterizedTest
        @EnumSource(Role.class)
        void chaqueRoleDeLEnumerationAccepte(Role role) {
            assertThatCode(() -> inserer("utilisateur", utilisateur(role.name(), idBureau))).doesNotThrowAnyException();
        }

        @Test
        void roleInconnuRefuse() {
            refuseePar("ck_utilisateur_role", () -> inserer("utilisateur", utilisateur("PARTICULIER", null)));
        }

        @Test
        void conservateurSansBureauRefuse() {
            refuseePar("ck_utilisateur_conservateur_bureau",
                    () -> inserer("utilisateur", utilisateur("CONSERVATEUR", null)));
        }

        @Test
        void notaireSansBureauAccepte() {
            assertThatCode(() -> inserer("utilisateur", utilisateur("NOTAIRE", null))).doesNotThrowAnyException();
        }

        @Test
        void adresseEMailDejaUtiliseeRefuseeSansTenirCompteDeLaCasse() {
            // l'adresse e-mail sert d'identifiant de connexion (V3)
            inserer("utilisateur", avec(utilisateur("NOTAIRE", null), "email", "ndiaye@example.sn"));
            refuseePar("uq_utilisateur_email",
                    () -> inserer("utilisateur", avec(utilisateur("NOTAIRE", null), "email", "NDiaye@Example.SN")));
        }
    }

    // ---------------------------------------------------------------- tarif

    @Nested
    class Tarif {

        private Map<String, Object> tarif(String type, int montant) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("type_demande", type);
            t.put("montant", montant);
            return t;
        }

        @ParameterizedTest
        @EnumSource(TypeDemande.class)
        void chaqueTypeDemandeAccepte(TypeDemande type) {
            assertThatCode(() -> inserer("tarif", tarif(type.name(), 1000))).doesNotThrowAnyException();
        }

        @Test
        void typeDemandeInconnuRefuse() {
            refuseePar("ck_tarif_type_demande", () -> inserer("tarif", tarif("SOCIETE", 1000)));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1000})
        void montantNulOuNegatifRefuse(int montant) {
            refuseePar("ck_tarif_montant", () -> inserer("tarif", tarif("BIEN", montant)));
        }
    }

    // ---------------------------------------------------------------- demande

    @Nested
    class Demande {

        @Test
        void demandeValideDUnParticulierSurUnBienAcceptee() {
            assertThatCode(() -> inserer("demande", demandeParticulier())).doesNotThrowAnyException();
        }

        @Test
        void demandeValideDUnNotaireSurUnePersonneAcceptee() {
            assertThatCode(() -> inserer("demande", demandeNotaire())).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(TypeDemande.class)
        void chaqueTypeAccepte(TypeDemande type) {
            Map<String, Object> d = avec(demandeParticulier(), "type", type.name(), "personne_concernee", "Moussa Fall");
            assertThatCode(() -> inserer("demande", d)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(StatutDemande.class)
        void chaqueStatutAccepte(StatutDemande statut) {
            assertThatCode(() -> inserer("demande", avec(demandeParticulier(), "statut", statut.name())))
                    .doesNotThrowAnyException();
        }

        @Test
        void typeInconnuRefuse() {
            refuseePar("ck_demande_type", () -> inserer("demande", avec(demandeParticulier(), "type", "SOCIETE")));
        }

        @Test
        void statutInconnuRefuse() {
            refuseePar("ck_demande_statut", () -> inserer("demande", avec(demandeParticulier(), "statut", "ANNULEE")));
        }

        @Test
        void referenceEnDoubleRefusee() {
            inserer("demande", demandeParticulier());
            refuseePar("uq_demande_reference", () -> inserer("demande", demandeParticulier()));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "EDR-2026-K7P4Q",   // 5 caracteres
            "EDR-2026-K7P4QXY", // 7 caracteres
            "EDR-2026-K7P4Q0",  // chiffre 0
            "EDR-2026-K7P4Q1",  // chiffre 1
            "EDR-2026-K7P4QO",  // lettre O
            "EDR-2026-K7P4QI",  // lettre I
            "EDR-2026-K7P4QL",  // lettre L
            "EDR-2026-k7p4qx",  // minuscules
            "EDR-26-K7P4QX",    // annee sur 2 chiffres
            "ABC-2026-K7P4QX"   // prefixe
        })
        void referenceMalFormeeRefusee(String reference) {
            refuseePar("ck_demande_reference",
                    () -> inserer("demande", avec(demandeParticulier(), "reference", reference)));
        }

        @Test
        void demandeSurUnBienSansNumeroDeTitreRefusee() {
            refuseePar("ck_demande_bien_numero_titre",
                    () -> inserer("demande", avec(demandeParticulier(), "numero_titre_foncier", null)));
        }

        @Test
        void demandeSurUnePersonneSansPersonneConcerneeRefusee() {
            refuseePar("ck_demande_personne_concernee", () -> inserer("demande", avec(demandeParticulier(),
                    "type", "PERSONNE", "numero_titre_foncier", null, "personne_concernee", null)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"12.405/NGA", "12 405/NGA", "12405/nga", "12405 / NGA", "12405", "/NGA", "12405/ABCDE"})
        void numeroDeTitreNonNormaliseRefuse(String numero) {
            refuseePar("ck_demande_numero_titre_foncier",
                    () -> inserer("demande", avec(demandeParticulier(), "numero_titre_foncier", numero)));
        }

        @Test
        void particulierSansCniRefuse() {
            refuseePar("ck_demande_declarant_cni",
                    () -> inserer("demande", avec(demandeParticulier(), "declarant_cni", null)));
        }

        @Test
        void notaireAvecCniRefuse() {
            refuseePar("ck_demande_declarant_cni",
                    () -> inserer("demande", avec(demandeNotaire(), "declarant_cni", "1751199900123")));
        }

        @Test
        void particulierSansTelephoneVerifieRefuse() {
            refuseePar("ck_demande_telephone_verifie",
                    () -> inserer("demande", avec(demandeParticulier(), "telephone_verifie", false)));
        }

        @Test
        void demandeSansBureauRefusee() {
            refuseePar("id_bureau", () -> inserer("demande", avec(demandeParticulier(), "id_bureau", null)));
        }

        @Test
        void bureauAyantDesDemandesNonSupprimable() {
            long bureauSansLivre = inserer("bureau", Map.of("nom", "Bureau de test", "adresse", "Dakar", "telephone", "+221 33 000 00 99"));
            inserer("demande", avec(demandeParticulier(), "id_bureau", bureauSansLivre));
            refuseePar("fk_demande_bureau",
                    () -> jdbc.sql("DELETE FROM bureau WHERE id_bureau = ?").param(bureauSansLivre).update());
        }
    }

    // ---------------------------------------------------------------- piece_jointe

    @Nested
    class PieceJointe {

        private Map<String, Object> piece(String type, long demande) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", type);
            p.put("nom_fichier", "cni.jpg");
            p.put("chemin", "pieces/3f2a9c.jpg");
            p.put("id_demande", demande);
            return p;
        }

        @ParameterizedTest
        @EnumSource(TypePiece.class)
        void chaqueTypeAccepte(TypePiece type) {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("piece_jointe", piece(type.name(), demande))).doesNotThrowAnyException();
        }

        @Test
        void typeInconnuRefuse() {
            long demande = demandeEnregistree();
            refuseePar("ck_piece_jointe_type", () -> inserer("piece_jointe", piece("PASSEPORT", demande)));
        }

        @Test
        void piecesSupprimeesAvecLaDemande() {
            long demande = demandeEnregistree();
            inserer("piece_jointe", piece("CNI_RECTO", demande));
            inserer("piece_jointe", piece("CNI_VERSO", demande));

            jdbc.sql("DELETE FROM demande WHERE id_demande = ?").param(demande).update();

            assertThat(jdbc.sql("SELECT count(*) FROM piece_jointe WHERE id_demande = ?").param(demande)
                    .query(Long.class).single()).isZero();
        }
    }

    // ---------------------------------------------------------------- resultat

    @Nested
    class Resultat {

        private Map<String, Object> resultat(String type, long demande) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type", type);
            r.put("fichier", "resultats/9b1e4d.pdf");
            r.put("date_publication", OffsetDateTime.now());
            r.put("id_demande", demande);
            return r;
        }

        @ParameterizedTest
        @EnumSource(TypeResultat.class)
        void chaqueTypeAccepte(TypeResultat type) {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("resultat", resultat(type.name(), demande))).doesNotThrowAnyException();
        }

        @Test
        void typeInconnuRefuse() {
            long demande = demandeEnregistree();
            refuseePar("ck_resultat_type", () -> inserer("resultat", resultat("CERTIFICAT", demande)));
        }

        @Test
        void secondResultatPourLaMemeDemandeRefuse() {
            long demande = demandeEnregistree();
            inserer("resultat", resultat("ETAT_DROITS_REELS", demande));
            refuseePar("uq_resultat_demande", () -> inserer("resultat", resultat("REQUISITION_NEGATIVE", demande)));
        }
    }

    // ---------------------------------------------------------------- paiement

    @Nested
    class Paiement {

        private Map<String, Object> paiement(long demande) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("montant", 1000);
            p.put("mode_paiement", "WAVE");
            p.put("statut", "CONFIRME");
            p.put("date_paiement", OffsetDateTime.now());
            p.put("id_demande", demande);
            return p;
        }

        @ParameterizedTest
        @EnumSource(ModePaiement.class)
        void chaqueModeAccepte(ModePaiement mode) {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("paiement", avec(paiement(demande), "mode_paiement", mode.name())))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(StatutPaiement.class)
        void chaqueStatutAccepte(StatutPaiement statut) {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("paiement", avec(paiement(demande), "statut", statut.name())))
                    .doesNotThrowAnyException();
        }

        @Test
        void modeInconnuRefuse() {
            long demande = demandeEnregistree();
            refuseePar("ck_paiement_mode_paiement",
                    () -> inserer("paiement", avec(paiement(demande), "mode_paiement", "ESPECES")));
        }

        @Test
        void statutInconnuRefuse() {
            long demande = demandeEnregistree();
            refuseePar("ck_paiement_statut", () -> inserer("paiement", avec(paiement(demande), "statut", "REMBOURSE")));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1000})
        void montantNulOuNegatifRefuse(int montant) {
            long demande = demandeEnregistree();
            refuseePar("ck_paiement_montant", () -> inserer("paiement", avec(paiement(demande), "montant", montant)));
        }

        @Test
        void secondPaiementPourLaMemeDemandeRefuse() {
            long demande = demandeEnregistree();
            inserer("paiement", paiement(demande));
            refuseePar("uq_paiement_demande", () -> inserer("paiement", paiement(demande)));
        }
    }

    // ---------------------------------------------------------------- notification

    @Nested
    class Notification {

        private Map<String, Object> notification(String type, long demande) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("type", type);
            n.put("date_envoi", OffsetDateTime.now());
            n.put("id_demande", demande);
            return n;
        }

        @ParameterizedTest
        @EnumSource(TypeNotification.class)
        void chaqueTypeAccepte(TypeNotification type) {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("notification", notification(type.name(), demande))).doesNotThrowAnyException();
        }

        @Test
        void typeInconnuRefuse() {
            long demande = demandeEnregistree();
            refuseePar("ck_notification_type", () -> inserer("notification", notification("RELANCE", demande)));
        }
    }

    // ---------------------------------------------------------------- journal

    @Nested
    class Journal {

        private Map<String, Object> journal(Long utilisateur, Long demande) {
            Map<String, Object> j = new LinkedHashMap<>();
            j.put("action", "Soumission de la demande");
            j.put("date_action", OffsetDateTime.now());
            j.put("id_utilisateur", utilisateur);
            j.put("id_demande", demande);
            return j;
        }

        @Test
        void ligneRattacheeAUneDemandeAcceptee() {
            long demande = demandeEnregistree();
            assertThatCode(() -> inserer("journal", journal(null, demande))).doesNotThrowAnyException();
        }

        @Test
        void ligneRattacheeAUnUtilisateurAcceptee() {
            long admin = inserer("utilisateur", utilisateur("ADMINISTRATEUR", null));
            assertThatCode(() -> inserer("journal", journal(admin, null))).doesNotThrowAnyException();
        }

        @Test
        void ligneSansUtilisateurNiDemandeRefusee() {
            refuseePar("ck_journal_rattachement", () -> inserer("journal", journal(null, null)));
        }
    }
}
