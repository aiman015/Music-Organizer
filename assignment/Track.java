package assignment;

import java.time.LocalDate;

/**
 * One music track. Stores the details that the GUI shows plus the extra
 * fields needed by the textbook exercises (playCount: 4.35, rating: 4.37).
 */
public class Track {
    private String title;
    private String artist;
    private String duration;   // "m:ss"
    private String genre;
    private String filePath;
    private final LocalDate addedOn;

    private int playCount;     // Exercise 4.35
    private int rating;        // Exercise 4.37 (0 = not rated, 1..5)

    public Track(String title, String artist, String duration, String genre) {
        this(title, artist, duration, genre, "");
    }

    public Track(String title, String artist, String duration, String genre, String filePath) {
        this(title, artist, duration, genre, filePath, LocalDate.now());
    }

    /** Full constructor: also lets the caller say when the song was added (shown in the "Added On" column). */
    public Track(String title, String artist, String duration, String genre, String filePath, LocalDate addedOn) {
        this.title = clean(title, "Untitled");
        this.artist = clean(artist, "Unknown artist");
        this.duration = clean(duration, "0:00");
        this.genre = clean(genre, "Other");
        this.filePath = filePath == null ? "" : filePath;
        this.addedOn = addedOn == null ? LocalDate.now() : addedOn;
    }

    private String clean(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s.trim();
    }

    public String getTitle()      { return title; }
    public String getArtist()     { return artist; }
    public String getDuration()   { return duration; }
    public String getGenre()      { return genre; }
    public String getFilePath()   { return filePath; }
    public LocalDate getAddedOn() { return addedOn; }

    public void setTitle(String s)    { title = clean(s, "Untitled"); }
    public void setArtist(String s)   { artist = clean(s, "Unknown artist"); }
    public void setDuration(String s) { duration = clean(s, "0:00"); }
    public void setGenre(String s)    { genre = clean(s, "Other"); }
    public void setFilePath(String s) { filePath = s == null ? "" : s; }

    // ---- Exercise 4.35: play count -------------------------------------
    public int getPlayCount()        { return playCount; }
    public void resetPlayCount()     { playCount = 0; }
    public void incrementPlayCount() { playCount++; }

    // ---- Exercise 4.37: extra field (rating) with accessor + mutator ----
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = Math.max(0, Math.min(5, rating)); }

    /** Length of the track in seconds, parsed from the "m:ss" duration text. */
    public int getDurationSeconds() {
        try {
            String[] p = duration.split(":");
            if (p.length == 2) return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (NumberFormatException ignored) { }
        return 0;
    }

    /** A one-line description; the Track is responsible for formatting itself. */
    public String getDetails() {
        return artist + " - " + title + " (" + duration + ", " + genre + ")"
                + ", played " + playCount + " time(s)"
                + (rating > 0 ? ", rating " + rating + "/5" : "");
    }

    @Override
    public String toString() { return title + " - " + artist; }
}
