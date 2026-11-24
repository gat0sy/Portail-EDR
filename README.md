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

Tests (Docker doit tourner, une base PostgreSQL jetable est créée par Testcontainers) :

```bash
./mvnw verify
```
