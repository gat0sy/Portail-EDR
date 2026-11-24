package sn.portailedr;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** @ConfigurationPropertiesScan : les records *Proprietes (portail.sms, portail.fichiers) sont lus au demarrage. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PortailEdrApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortailEdrApplication.class, args);
    }

    /** Heure courante, injectee plutot que lue directement : un test peut la fixer. */
    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
