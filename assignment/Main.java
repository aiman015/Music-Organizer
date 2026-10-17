package assignment;

import gui.MusicApp;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.SwingUtilities;

/** BlueJ entry point: run main(String[] args). */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MusicOrganizer organizer = new MusicOrganizer();
            try {
                if (MusicLibraryStorage.exists()) {
                    List<Track> saved = MusicLibraryStorage.load();
                    for (Track track : saved) organizer.addTrack(track);
                } else {
                    addStarterSongs(organizer);
                    MusicLibraryStorage.save(organizer.getTracks());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                addStarterSongs(organizer);
            }

            // Auto-play the first song only if a real audio file path is saved.
            // The starter examples have empty paths, so choose a real audio file when editing a song.
            Track firstPlayable = null;
            for (Track track : organizer.getTracks()) {
                if (track.getFilePath() != null && !track.getFilePath().trim().isEmpty()) {
                    firstPlayable = track;
                    break;
                }
            }
            if (firstPlayable != null) organizer.playTrack(firstPlayable);
            new MusicApp(organizer, 0).setVisible(true);
        });
    }

    private static void addStarterSongs(MusicOrganizer organizer) {
        organizer.addTrack(new Track("Night Changes", "One Direction", "3:46", "Pop", "", LocalDate.parse("2025-05-10")));
        organizer.addTrack(new Track("Perfect", "Ed Sheeran", "4:23", "Pop", "", LocalDate.parse("2025-05-11")));
        organizer.addTrack(new Track("Believer", "Imagine Dragons", "3:24", "Rock", "", LocalDate.parse("2025-05-12")));
        organizer.addTrack(new Track("Counting Stars", "OneRepublic", "4:17", "Rock", "", LocalDate.parse("2025-05-13")));
        organizer.addTrack(new Track("Someone You Loved", "Lewis Capaldi", "3:02", "Pop", "", LocalDate.parse("2025-05-14")));
        organizer.addTrack(new Track("Shape of You", "Ed Sheeran", "3:53", "Pop", "", LocalDate.parse("2025-05-15")));
    }
}
