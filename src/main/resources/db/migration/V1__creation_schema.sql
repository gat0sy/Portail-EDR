-- Schema du portail EDR : les dix tables du modele logique (figure 2.5).
-- Conventions :
--   * noms de tables et de colonnes en snake_case (idBureau -> id_bureau) ;
--   * enumerations stockees en TEXT avec une contrainte CHECK, pas de type ENUM natif ;
--   * chaque contrainte porte un nom explicite (pk_, fk_, uq_, ck_) pour etre reconnue
--     dans les messages d'erreur et dans les tests.

CREATE TABLE bureau (
    id_bureau  BIGINT GENERATED ALWAYS AS IDENTITY,
    nom        TEXT NOT NULL,
    adresse    TEXT NOT NULL,
    telephone  TEXT NOT NULL,
    CONSTRAINT pk_bureau PRIMARY KEY (id_bureau)
);

-- Livre foncier : registre dont le code termine le numero du titre foncier (12405/NGA).
-- Un bureau tient un ou plusieurs livres fonciers.
CREATE TABLE livre_foncier (
    id_livre_foncier  BIGINT GENERATED ALWAYS AS IDENTITY,
    code              TEXT NOT NULL,
    nom               TEXT NOT NULL,
    id_bureau         BIGINT NOT NULL,
    CONSTRAINT pk_livre_foncier PRIMARY KEY (id_livre_foncier),
    CONSTRAINT uq_livre_foncier_code UNIQUE (code),
    -- 1 a 4 lettres majuscules, comme dans le numero de titre normalise
    CONSTRAINT ck_livre_foncier_code CHECK (code ~ '^[A-Z]{1,4}$'),
    -- un bureau qui tient encore des livres fonciers ne peut pas etre supprime
    CONSTRAINT fk_livre_foncier_bureau FOREIGN KEY (id_bureau) REFERENCES bureau (id_bureau)
);

CREATE TABLE utilisateur (
    id_utilisateur  BIGINT GENERATED ALWAYS AS IDENTITY,
    nom             TEXT NOT NULL,
    email           TEXT NOT NULL,
    telephone       TEXT NOT NULL,
    mot_de_passe    TEXT NOT NULL,
    role            TEXT NOT NULL,
    actif           BOOLEAN NOT NULL,
    id_bureau       BIGINT,
    CONSTRAINT pk_utilisateur PRIMARY KEY (id_utilisateur),
    CONSTRAINT ck_utilisateur_role CHECK (role IN ('NOTAIRE', 'CONSERVATEUR', 'ADMINISTRATEUR')),
    -- un conservateur est toujours affecte a un bureau
    CONSTRAINT ck_utilisateur_conservateur_bureau CHECK (role <> 'CONSERVATEUR' OR id_bureau IS NOT NULL),
    CONSTRAINT fk_utilisateur_bureau FOREIGN KEY (id_bureau) REFERENCES bureau (id_bureau)
);

CREATE TABLE tarif (
    id_tarif      BIGINT GENERATED ALWAYS AS IDENTITY,
    type_demande  TEXT NOT NULL,
    montant       INTEGER NOT NULL,
    CONSTRAINT pk_tarif PRIMARY KEY (id_tarif),
    CONSTRAINT ck_tarif_type_demande CHECK (type_demande IN ('BIEN', 'PERSONNE')),
    -- montant en FCFA (pas de centimes)
    CONSTRAINT ck_tarif_montant CHECK (montant > 0)
);

CREATE TABLE demande (
    id_demande            BIGINT GENERATED ALWAYS AS IDENTITY,
    reference             TEXT NOT NULL,
    type                  TEXT NOT NULL,
    statut                TEXT NOT NULL,
    numero_titre_foncier  TEXT,
    personne_concernee    TEXT,
    declarant_cni         TEXT,
    declarant_nom         TEXT NOT NULL,
    declarant_telephone   TEXT NOT NULL,
    telephone_verifie     BOOLEAN NOT NULL,
    date_soumission       TIMESTAMPTZ NOT NULL,
    date_limite_paiement  TIMESTAMPTZ,
    id_bureau             BIGINT NOT NULL,
    id_utilisateur        BIGINT,
    id_tarif              BIGINT,
    CONSTRAINT pk_demande PRIMARY KEY (id_demande),
    -- code de suivi : aleatoire, unique, EDR-<annee>-<6 caracteres> pris dans un
    -- alphabet sans caracteres ambigus (ni 0, O, 1, I, L)
    CONSTRAINT uq_demande_reference UNIQUE (reference),
    CONSTRAINT ck_demande_reference CHECK (reference ~ '^EDR-[0-9]{4}-[2-9A-HJKMNP-Z]{6}$'),
    CONSTRAINT ck_demande_type CHECK (type IN ('BIEN', 'PERSONNE')),
    CONSTRAINT ck_demande_statut CHECK (statut IN ('SOUMISE', 'EN_COURS', 'REJETEE', 'DISPONIBLE', 'PAYEE', 'EXPIREE')),
    -- une demande sur un bien porte un numero de titre foncier
    CONSTRAINT ck_demande_bien_numero_titre CHECK (type <> 'BIEN' OR numero_titre_foncier IS NOT NULL),
    -- une demande sur une personne porte le nom de la personne concernee
    CONSTRAINT ck_demande_personne_concernee CHECK (type <> 'PERSONNE' OR personne_concernee IS NOT NULL),
    -- numero de titre stocke sous forme normalisee : chiffres sans separateur / code en majuscules
    CONSTRAINT ck_demande_numero_titre_foncier CHECK (numero_titre_foncier ~ '^[0-9]+/[A-Z]{1,4}$'),
    -- particulier (sans compte) : CNI declaree obligatoire ; notaire (avec compte) : pas de CNI
    CONSTRAINT ck_demande_declarant_cni CHECK (
        (id_utilisateur IS NULL AND declarant_cni IS NOT NULL)
        OR (id_utilisateur IS NOT NULL AND declarant_cni IS NULL)
    ),
    -- la demande d'un particulier n'est enregistree qu'apres validation du code SMS
    CONSTRAINT ck_demande_telephone_verifie CHECK (id_utilisateur IS NOT NULL OR telephone_verifie),
    CONSTRAINT fk_demande_bureau FOREIGN KEY (id_bureau) REFERENCES bureau (id_bureau),
    CONSTRAINT fk_demande_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_utilisateur),
    CONSTRAINT fk_demande_tarif FOREIGN KEY (id_tarif) REFERENCES tarif (id_tarif)
);

-- Composition : les pieces jointes sont supprimees avec leur demande.
CREATE TABLE piece_jointe (
    id_piece_jointe  BIGINT GENERATED ALWAYS AS IDENTITY,
    type             TEXT NOT NULL,
    nom_fichier      TEXT NOT NULL,
    chemin           TEXT NOT NULL,
    id_demande       BIGINT NOT NULL,
    CONSTRAINT pk_piece_jointe PRIMARY KEY (id_piece_jointe),
    CONSTRAINT ck_piece_jointe_type CHECK (type IN ('CNI_RECTO', 'CNI_VERSO', 'COPIE_TITRE_FONCIER')),
    CONSTRAINT fk_piece_jointe_demande FOREIGN KEY (id_demande) REFERENCES demande (id_demande) ON DELETE CASCADE
);

-- 0..1 resultat par demande
CREATE TABLE resultat (
    id_resultat       BIGINT GENERATED ALWAYS AS IDENTITY,
    type              TEXT NOT NULL,
    fichier           TEXT NOT NULL,
    date_publication  TIMESTAMPTZ NOT NULL,
    id_demande        BIGINT NOT NULL,
    CONSTRAINT pk_resultat PRIMARY KEY (id_resultat),
    CONSTRAINT uq_resultat_demande UNIQUE (id_demande),
    CONSTRAINT ck_resultat_type CHECK (type IN ('ETAT_DROITS_REELS', 'REQUISITION_NEGATIVE')),
    CONSTRAINT fk_resultat_demande FOREIGN KEY (id_demande) REFERENCES demande (id_demande)
);

-- 0..1 paiement par demande
CREATE TABLE paiement (
    id_paiement    BIGINT GENERATED ALWAYS AS IDENTITY,
    montant        INTEGER NOT NULL,
    mode_paiement  TEXT NOT NULL,
    statut         TEXT NOT NULL,
    date_paiement  TIMESTAMPTZ NOT NULL,
    id_demande     BIGINT NOT NULL,
    CONSTRAINT pk_paiement PRIMARY KEY (id_paiement),
    CONSTRAINT uq_paiement_demande UNIQUE (id_demande),
    CONSTRAINT ck_paiement_montant CHECK (montant > 0),
    CONSTRAINT ck_paiement_mode_paiement CHECK (mode_paiement IN ('WAVE', 'ORANGE_MONEY', 'CARTE_BANCAIRE')),
    CONSTRAINT ck_paiement_statut CHECK (statut IN ('INITIE', 'CONFIRME', 'ECHEC')),
    CONSTRAINT fk_paiement_demande FOREIGN KEY (id_demande) REFERENCES demande (id_demande)
);

CREATE TABLE notification (
    id_notification  BIGINT GENERATED ALWAYS AS IDENTITY,
    type             TEXT NOT NULL,
    date_envoi       TIMESTAMPTZ NOT NULL,
    id_demande       BIGINT NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id_notification),
    CONSTRAINT ck_notification_type CHECK (type IN ('CODE_VERIFICATION', 'REJET', 'DISPONIBILITE', 'PAIEMENT', 'EXPIRATION')),
    CONSTRAINT fk_notification_demande FOREIGN KEY (id_demande) REFERENCES demande (id_demande)
);

-- Une ligne de journal est rattachee a l'utilisateur authentifie qui agit,
-- ou a la demande quand l'action vient d'un particulier ou du traitement planifie.
CREATE TABLE journal (
    id_journal      BIGINT GENERATED ALWAYS AS IDENTITY,
    action          TEXT NOT NULL,
    date_action     TIMESTAMPTZ NOT NULL,
    id_utilisateur  BIGINT,
    id_demande      BIGINT,
    CONSTRAINT pk_journal PRIMARY KEY (id_journal),
    CONSTRAINT ck_journal_rattachement CHECK (id_utilisateur IS NOT NULL OR id_demande IS NOT NULL),
    CONSTRAINT fk_journal_utilisateur FOREIGN KEY (id_utilisateur) REFERENCES utilisateur (id_utilisateur),
    CONSTRAINT fk_journal_demande FOREIGN KEY (id_demande) REFERENCES demande (id_demande)
);

-- Index sur les cles etrangeres les plus parcourues (PostgreSQL ne les cree pas seul).
CREATE INDEX ix_demande_bureau ON demande (id_bureau);
CREATE INDEX ix_demande_utilisateur ON demande (id_utilisateur);
CREATE INDEX ix_piece_jointe_demande ON piece_jointe (id_demande);
CREATE INDEX ix_livre_foncier_bureau ON livre_foncier (id_bureau);
