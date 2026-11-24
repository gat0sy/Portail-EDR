package sn.portailedr;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL jetable pour les tests, meme version majeure que docker-compose.yml.
 * {@link ServiceConnection} fournit a Spring l'URL et les identifiants du conteneur,
 * qui remplacent alors les variables DATABASE_*.
 *
 * Le conteneur est statique : un seul conteneur pour toute l'execution des tests,
 * partage par tous les contextes Spring qui importent cette configuration
 * (@DataJpaTest et @SpringBootTest creent des contextes differents). Le second
 * contexte retrouve le conteneur deja demarre, et Flyway n'a plus rien a appliquer.
 * Consequence : un test qui enregistre des donnees sans les annuler les laisse
 * visibles pour les autres classes de test.
 *
 * Delai d'attente du demarrage : 105 s au lieu des 60 s par defaut (x 1,75), pour
 * les postes de developpement au disque lent ou PostgreSQL met plus d'une minute a s'initialiser.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withStartupTimeout(Duration.ofSeconds(105));

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
