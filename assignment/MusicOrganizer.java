package assignment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the user's music collection in an ArrayList and offers the operations
 * from Chapter 4 of "Objects First with Java" (index checks, for-each listing,
 * searching, playing and iterator based removal).
 */
public class MusicOrganizer {
    private final ArrayList<Track> tracks = new ArrayList<>();
    private final MusicPlayer player = new MusicPlayer();

    public MusicPlayer getPlayer() { return player; }

    // ------------------------------------------------------------------
    // Basic collection operations (Part 1 / Part 2)
    // ------------------------------------------------------------------
    public void addTrack(Track t) { if (t != null) tracks.add(t); }

    public int getNumberOfTracks() { return tracks.size(); }

    /** A copy of the list, so the GUI can never change the real collection by accident. */
    public List<Track> getTracks() { return new ArrayList<>(tracks); }

    /** Exercise 4.14: print an error if the index is not valid, otherwise print nothing. */
    public void checkIndex(int index) {
        if (index < 0 || index >= tracks.size()) {
            if (tracks.isEmpty()) {
                System.out.println("Invalid index " + index + ". The collection is empty, so there is no valid index.");
            } else {
                System.out.println("Invalid index " + index + ". Valid indices are 0 to " + (tracks.size() - 1) + ".");
            }
        }
    }

    /** Exercise 4.15: true when index is in the range 0 .. size()-1. */
    public boolean validIndex(int index) {
        return index >= 0 && index < tracks.size();
    }

    /** Exercise 4.16: uses validIndex instead of the boolean expression. */
    public Track getTrack(int index) {
        if (validIndex(index)) return tracks.get(index);
        return null;
    }

    public void listTrack(int index) {
        if (validIndex(index)) System.out.println(tracks.get(index).getDetails());
    }

    public Track removeTrackAt(int index) {
        if (validIndex(index)) return tracks.remove(index);
        return null;
    }

    public boolean removeTrack(Track t) { return tracks.remove(t); }

    public void clear() { tracks.clear(); }

    // ------------------------------------------------------------------
    // Processing the whole collection (Part 3)
    // ------------------------------------------------------------------
    /** Exercise 4.20 / 4.21: for-each loop. */
    public void listAllTracks() {
        for (Track track : tracks) {
            System.out.println(track.getDetails());
        }
    }

    /** Exercise 4.24: for-each loop with our own position variable. */
    public void listAllTracksWithPosition() {
        int position = 0;
        for (Track track : tracks) {
            System.out.println(position + ": " + track.getDetails());
            position++;
        }
    }

    /** Exercise 4.25 / 4.26: list tracks whose title or artist contains the text; say so if none. */
    public void listMatching(String searchString) {
        boolean found = false;
        for (Track track : tracks) {
            if (track.getTitle().contains(searchString) || track.getArtist().contains(searchString)) {
                System.out.println(track.getDetails());
                found = true;
            }
        }
        if (!found) System.out.println("No tracks match \"" + searchString + "\".");
    }

    /** Exercise 4.27: play a sample of every track by one artist, one after the other. */
    public void playSamplesByArtist(String artist) {
        for (Track track : tracks) {
            if (track.getArtist().equalsIgnoreCase(artist)) {
                track.incrementPlayCount();
                player.playAndWait(track);
            }
        }
    }

    // ------------------------------------------------------------------
    // Playing (Exercises 4.35 - 4.38)
    // ------------------------------------------------------------------
    /** Exercise 4.36: playing a track increments its play count. 4.38: the player stops the old one. */
    public void playTrack(Track t) {
        if (t == null) return;
        t.incrementPlayCount();
        player.play(t);
    }

    public Track playNext() {
        Track t = player.next(tracks);
        if (t != null) t.incrementPlayCount();
        return t;
    }

    public Track playPrevious() {
        Track t = player.previous(tracks);
        if (t != null) t.incrementPlayCount();
        return t;
    }

    public void stopPlaying() { player.stop(); }

    /** Exercise 4.37: change the rating of the track at the given index. */
    public void setRating(int index, int rating) {
        if (validIndex(index)) tracks.get(index).setRating(rating);
    }

    // ------------------------------------------------------------------
    // Searching and statistics used by the GUI
    // ------------------------------------------------------------------
    public List<Track> search(String query, String genre) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String g = genre == null ? "All Genres" : genre;
        ArrayList<Track> out = new ArrayList<>();
        for (Track t : tracks) {
            boolean text = q.isEmpty()
                    || t.getTitle().toLowerCase(Locale.ROOT).contains(q)
                    || t.getArtist().toLowerCase(Locale.ROOT).contains(q)
                    || t.getGenre().toLowerCase(Locale.ROOT).contains(q);
            if (text && (g.equals("All Genres") || t.getGenre().equalsIgnoreCase(g))) out.add(t);
        }
        return out;
    }

    public int getUniqueArtistCount() {
        Set<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : tracks) s.add(t.getArtist());
        return s.size();
    }

    public int getUniqueGenreCount() {
        Set<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : tracks) s.add(t.getGenre());
        return s.size();
    }

    public String getTotalDuration() {
        int n = 0;
        for (Track t : tracks) n += t.getDurationSeconds();
        return n / 60 + ":" + String.format("%02d", n % 60);
    }

    // ------------------------------------------------------------------
    // Removing while iterating (Part 4): only Iterator.remove() is safe
    // ------------------------------------------------------------------
    /** Exercise 4.39: remove every track whose title contains the given text. */
    public int removeByTitle(String text) {
        int removed = 0;
        Iterator<Track> it = tracks.iterator();
        while (it.hasNext()) {
            Track t = it.next();
            if (t.getTitle().contains(text)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public int removeByGenre(String genre) {
        int removed = 0;
        Iterator<Track> it = tracks.iterator();
        while (it.hasNext()) {
            if (it.next().getGenre().equalsIgnoreCase(genre)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Removes all given tracks in a single pass (used by "Remove Selected"). */
    public int removeTracks(Collection<Track> toRemove) {
        int removed = 0;
        Iterator<Track> it = tracks.iterator();
        while (it.hasNext()) {
            if (toRemove.contains(it.next())) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public List<Track> tracksUsingIterator() {
        ArrayList<Track> out = new ArrayList<>();
        Iterator<Track> it = tracks.iterator();
        while (it.hasNext()) out.add(it.next());
        return out;
    }
}
