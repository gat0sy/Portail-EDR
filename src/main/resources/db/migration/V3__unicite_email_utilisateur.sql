-- L'adresse e-mail sert d'identifiant de connexion : elle doit etre unique.
-- L'index porte sur lower(email) pour que Fall@exemple.sn et fall@exemple.sn
-- ne puissent pas designer deux comptes differents.
CREATE UNIQUE INDEX uq_utilisateur_email ON utilisateur (lower(email));
