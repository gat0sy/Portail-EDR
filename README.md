# Portail EDR

Portail de demande et de délivrance de l'état des droits réels (Conservation foncière, DGID).
Le prototype statique se trouve dans `prototype/` et est publié sur GitHub Pages.

## Lancer en local

Prérequis : Java 25 (`JAVA_HOME` doit pointer dessus) et Docker.

```bash
cp .env.example .env        # puis choisir un mot de passe
docker compose up -d        # démarre PostgreSQL 18
./mvnw spring-boot:run      # démarre l'application sur http://localhost:8080
```

L'état de l'application est visible sur http://localhost:8080/actuator/health.

### Comptes de démonstration (profil dev)

Pour disposer d'un compte par rôle en local, démarrer avec le profil `dev` :

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Trois comptes **fictifs** sont alors créés s'ils n'existent pas (mot de passe : `demo-edr-2026`, défini dans `application-dev.properties`) :

| Rôle | Adresse e-mail |
|---|---|
| Notaire | m.fall@exemple.sn |
| Conservateur (Bureau de Dakar-Plateau) | m.diop@exemple.sn |
| Administrateur | a.ba@exemple.sn |

Ce profil ne doit jamais être activé en production : hors du profil `dev`, aucun compte de démonstration n'est créé.

Tests (Docker doit tourner, une base PostgreSQL jetable est créée par Testcontainers) :

```bash
./mvnw verify
```
