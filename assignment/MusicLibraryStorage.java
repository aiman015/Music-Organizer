package assignment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Saves the music collection beside the BlueJ project in music-library.txt. */
public final class MusicLibraryStorage {
    private static final Path FILE = Paths.get("music-library.txt");
    private MusicLibraryStorage() { }

    public static boolean exists() { return Files.isRegularFile(FILE); }

    public static List<Track> load() throws IOException {
        List<Track> tracks = new ArrayList<Track>();
        if (!Files.exists(FILE)) return tracks;
        try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                try {
                    String[] p = line.split("\\t", -1);
                    if (p.length < 7) continue;
                    String title = decode(p[0]);
                    String artist = decode(p[1]);
                    String duration = decode(p[2]);
                    String genre = decode(p[3]);
                    String path = decode(p[4]);
                    LocalDate added = LocalDate.parse(decode(p[5]));
                    Track track = new Track(title, artist, duration, genre, path, added);
                    track.setRating(Integer.parseInt(p[6]));
                    tracks.add(track);
                } catch (RuntimeException badLine) {
                    System.err.println("Skipping invalid song entry in music-library.txt");
                }
            }
        }
        return tracks;
    }

    public static void save(List<Track> tracks) throws IOException {
        Path absolute = FILE.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(absolute, StandardCharsets.UTF_8)) {
            for (Track t : tracks) {
                writer.write(encode(t.getTitle())); writer.write('\t');
                writer.write(encode(t.getArtist())); writer.write('\t');
                writer.write(encode(t.getDuration())); writer.write('\t');
                writer.write(encode(t.getGenre())); writer.write('\t');
                writer.write(encode(t.getFilePath())); writer.write('\t');
                writer.write(encode(t.getAddedOn().toString())); writer.write('\t');
                writer.write(Integer.toString(t.getRating()));
                writer.newLine();
            }
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
