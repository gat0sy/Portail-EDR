package sn.portailedr.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fichiers des pieces jointes, sur le disque, sous le dossier portail.fichiers.dossier :
 * <ul>
 *   <li>temporaire/ : pieces d'une demande qui attend encore le code SMS ;</li>
 *   <li>pieces/ : pieces des demandes enregistrees (PieceJointe.chemin = nom du fichier).</li>
 * </ul>
 * Ce dossier n'est pas dans l'application ni dans un dossier servi par le web : un fichier
 * ne sera lisible que par un controleur qui verifie l'acces (tranche 7). Les noms sont generes
 * (UUID) ; le nom envoye par le navigateur n'entre jamais dans un chemin.
 */
@Component
public class StockagePieces {

    private static final Logger JOURNAL = LoggerFactory.getLogger(StockagePieces.class);

    private final Path temporaire;
    private final Path pieces;

    public StockagePieces(FichiersProprietes proprietes) {
        Path racine = proprietes.dossier().toAbsolutePath().normalize();
        this.temporaire = racine.resolve("temporaire");
        this.pieces = racine.resolve("pieces");
        try {
            Files.createDirectories(temporaire);
            Files.createDirectories(pieces);
        } catch (IOException erreur) {
            throw new UncheckedIOException("Dossier des pieces inaccessible : " + racine, erreur);
        }
    }

    /** Format reconnu a partir des premiers octets du fichier, ou vide si aucun format accepte. */
    public Optional<FormatFichier> reconnaitre(MultipartFile fichier) {
        try (InputStream contenu = fichier.getInputStream()) {
            return FormatFichier.reconnaitre(contenu.readNBytes(FormatFichier.TAILLE_ENTETE));
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    /** Copie le fichier dans la zone temporaire sous un nom genere ; renvoie son chemin absolu. */
    public Path enregistrerTemporaire(MultipartFile fichier, FormatFichier format) {
        Path cible = temporaire.resolve(UUID.randomUUID() + "." + format.extension());
        try (InputStream contenu = fichier.getInputStream()) {
            Files.copy(contenu, cible);
            return cible;
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    /** Nom que portera la piece une fois la demande enregistree (meme UUID, meme extension). */
    public String nomDefinitif(String fichierTemporaire) {
        return Path.of(fichierTemporaire).getFileName().toString();
    }

    /** Deplace une piece de la zone temporaire vers les pieces des demandes enregistrees. */
    public void deplacer(String fichierTemporaire) {
        Path source = Path.of(fichierTemporaire);
        if (!source.toAbsolutePath().normalize().startsWith(temporaire)) {
            throw new IllegalArgumentException("Fichier hors de la zone temporaire : " + source);
        }
        try {
            Files.move(source, pieces.resolve(source.getFileName()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException erreur) {
            throw new UncheckedIOException(erreur);
        }
    }

    /**
     * Au demarrage, aucune session ne survit : les fichiers temporaires restants (arret brutal
     * du serveur, par exemple) n'appartiennent plus a personne et sont supprimes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void viderZoneTemporaire() {
        try (Stream<Path> restants = Files.list(temporaire)) {
            restants.forEach(fichier -> {
                try {
                    Files.deleteIfExists(fichier);
                } catch (IOException erreur) {
                    JOURNAL.warn("Fichier temporaire non supprime : {}", fichier.getFileName());
                }
            });
        } catch (IOException erreur) {
            JOURNAL.warn("Zone temporaire illisible : {}", temporaire);
        }
    }
}
