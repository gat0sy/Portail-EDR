-- Donnees initiales. Toutes sont des valeurs de DEMONSTRATION, a verifier ou remplacer
-- par l'administrateur avant tout usage reel.

-- Bureaux : les six bureaux du prototype (annuaire.html).
-- Adresses reduites a la ville et numeros de telephone FICTIFS (+221 33 000 00 0x).
INSERT INTO bureau (nom, adresse, telephone) VALUES
    ('Bureau de Dakar-Plateau', 'Dakar',    '+221 33 000 00 01'),
    ('Bureau de Grand Dakar',   'Dakar',    '+221 33 000 00 02'),
    ('Bureau de Ngor-Almadies', 'Dakar',    '+221 33 000 00 03'),
    ('Bureau de Pikine',        'Pikine',   '+221 33 000 00 04'),
    ('Bureau de Rufisque',      'Rufisque', '+221 33 000 00 05'),
    ('Bureau de Thiès',         'Thiès',    '+221 33 000 00 06');

-- Livres fonciers : codes PROVISOIRES. Aucune table officielle complete des codes et des
-- bureaux n'a ete trouvee. DK et GR sont les moins certains. L'ancien code DG
-- (Dakar-Goree) n'est volontairement pas cree : on ne sait pas quel bureau le tient
-- aujourd'hui, l'administrateur pourra l'ajouter.
INSERT INTO livre_foncier (code, nom, id_bureau)
SELECT l.code, l.nom, b.id_bureau
FROM (VALUES
    ('DK',  'Dakar',            'Bureau de Dakar-Plateau'),
    ('GR',  'Grand Dakar',      'Bureau de Grand Dakar'),
    ('NGA', 'Ngor-Almadies',    'Bureau de Ngor-Almadies'),
    ('DP',  'Dagoudane Pikine', 'Bureau de Pikine'),
    ('GW',  'Guédiawaye',       'Bureau de Pikine'),
    ('R',   'Rufisque',         'Bureau de Rufisque'),
    ('TH',  'Thiès',            'Bureau de Thiès')
) AS l (code, nom, bureau)
JOIN bureau b ON b.nom = l.bureau;

-- Grille tarifaire : montants FICTIFS (1 000 FCFA, valeur du prototype).
-- La grille officielle n'a pas pu etre confirmee.
INSERT INTO tarif (type_demande, montant) VALUES
    ('BIEN',     1000),
    ('PERSONNE', 1000);
