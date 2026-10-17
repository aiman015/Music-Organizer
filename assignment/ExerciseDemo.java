package assignment;

/** Console evidence for the textbook exercises (run it and paste the output into the answer document). */
public class ExerciseDemo {
    public static void main(String[] args) {
        System.out.println("== 4.14/4.15 on an EMPTY collection");
        MusicOrganizer empty = new MusicOrganizer();
        empty.checkIndex(0);
        System.out.println("validIndex(0) on empty = " + empty.validIndex(0));

        MusicOrganizer o = new MusicOrganizer();
        o.getPlayer().setSampleMillis(200);
        o.addTrack(new Track("Night Changes", "One Direction", "3:46", "Pop"));
        o.addTrack(new Track("Perfect", "Ed Sheeran", "4:23", "Pop"));
        o.addTrack(new Track("Believer", "Imagine Dragons", "3:24", "Rock"));
        o.addTrack(new Track("Counting Stars", "OneRepublic", "4:17", "Rock"));
        o.addTrack(new Track("Someone You Loved", "Lewis Capaldi", "3:02", "Pop"));
        o.addTrack(new Track("Shape of You", "Ed Sheeran", "3:53", "Pop"));

        System.out.println("== 4.14 checkIndex(2) then checkIndex(9)");
        o.checkIndex(2);
        o.checkIndex(9);
        System.out.println("== 4.15 validIndex(0)=" + o.validIndex(0) + ", validIndex(6)=" + o.validIndex(6) + ", validIndex(-1)=" + o.validIndex(-1));
        System.out.println("== 4.16 listTrack(1) and listTrack(50)");
        o.listTrack(1);
        o.listTrack(50);
        System.out.println("== 4.20/4.21 listAllTracks()");
        o.listAllTracks();
        System.out.println("== 4.24 listAllTracksWithPosition()");
        o.listAllTracksWithPosition();
        System.out.println("== 4.25 listMatching(\"Sheeran\")");
        o.listMatching("Sheeran");
        System.out.println("== 4.26 listMatching(\"zzz\")");
        o.listMatching("zzz");
        System.out.println("== 4.27 playSamplesByArtist(\"Ed Sheeran\")");
        long t0 = System.currentTimeMillis();
        o.playSamplesByArtist("Ed Sheeran");
        System.out.println("returned after " + (System.currentTimeMillis() - t0) + " ms: two samples of 200 ms were played one after the other");
        System.out.println("play counts after 4.27: Perfect=" + o.getTrack(1).getPlayCount() + ", Shape of You=" + o.getTrack(5).getPlayCount());
        System.out.println("== 4.35/4.36 playTrack increments the count");
        o.playTrack(o.getTrack(0));
        o.playTrack(o.getTrack(0));
        System.out.println("Night Changes play count = " + o.getTrack(0).getPlayCount());
        o.getTrack(0).resetPlayCount();
        System.out.println("after resetPlayCount() = " + o.getTrack(0).getPlayCount());
        System.out.println("== 4.37 rating field");
        o.setRating(2, 5);
        o.listTrack(2);
        o.setRating(2, 9);
        System.out.println("setRating(2, 9)  -> rating = " + o.getTrack(2).getRating() + " (limited to 5)");
        o.setRating(2, -3);
        System.out.println("setRating(2, -3) -> rating = " + o.getTrack(2).getRating() + " (limited to 0)");
        o.setRating(2, 5);
        System.out.println("== 4.38 starting a second track stops the first");
        o.playTrack(o.getTrack(0));
        o.playTrack(o.getTrack(3));
        System.out.println("current = " + o.getPlayer().getCurrentTrack() + ", playing = " + o.getPlayer().isPlaying());
        try { Thread.sleep(1500); } catch (InterruptedException e) { }
        int alive = 0;
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            if (th.getName().equals("audio-engine") && th.isAlive()) alive++;
        }
        System.out.println("audio threads running: " + alive + " (only the newest track is playing)");
        System.out.println("== 4.39 removeByTitle(\"Stars\") using an Iterator");
        System.out.println("removed " + o.removeByTitle("Stars") + " track(s); " + o.getNumberOfTracks() + " left");
        o.listAllTracks();
        System.out.println("== 4.39 why an Iterator is needed");
        java.util.ArrayList<String> titles = new java.util.ArrayList<>(java.util.Arrays.asList("Love Song", "Love Story", "Perfect", "Love Me"));
        try {
            for (String s : titles) { if (s.contains("Love")) titles.remove(s); }
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("for-each + remove(): ConcurrentModificationException");
        }
        titles = new java.util.ArrayList<>(java.util.Arrays.asList("Love Song", "Love Story", "Perfect", "Love Me"));
        for (int i = 0; i < titles.size(); i++) { if (titles.get(i).contains("Love")) titles.remove(i); }
        System.out.println("index loop + remove(i) left: " + titles + "  (Love Story was skipped)");
        titles = new java.util.ArrayList<>(java.util.Arrays.asList("Love Song", "Love Story", "Perfect", "Love Me"));
        java.util.Iterator<String> it = titles.iterator();
        while (it.hasNext()) { if (it.next().contains("Love")) it.remove(); }
        System.out.println("Iterator + remove() left: " + titles);
        System.out.println("== 4.17 real playback in the project (MusicPlayer + AudioEngine)");
        java.io.File[] wavs = new java.io.File("music").listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
        MusicOrganizer rp = new MusicOrganizer();
        if (wavs != null && wavs.length > 0) {
            String path = "music/" + wavs[0].getName();
            Track existing = new Track("Existing file", "Test", "0:00", "Other", path);
            System.out.println("canPlayFile(\"" + path + "\") = " + AudioEngine.canPlayFile(path)
                    + "  (" + AudioEngine.probeSeconds(path) + " s long)");
            rp.addTrack(existing);
            rp.playTrack(existing);
            pause(1500);
            AudioEngine eng = rp.getPlayer().getEngine();
            System.out.println("after 1.5 s: playing = " + rp.getPlayer().isPlaying() + ", position = "
                    + String.format("%.1f", eng.getPositionSeconds()) + " s of " + String.format("%.0f", eng.getLengthSeconds())
                    + " s, sound device = " + eng.hasDevice()
                    + (eng.hasDevice() ? "   -> the audio file is playing through the speakers"
                                       : "   -> no sound device: the position still advances (silent clock)"));
            rp.stopPlaying();
        } else {
            System.out.println("(no .wav file found in the music folder)");
        }
        String missing = "music/DoesNotExist.wav";
        Track none = new Track("Missing file", "Test", "3:00", "Other", missing);
        System.out.println("canPlayFile(\"" + missing + "\") = " + AudioEngine.canPlayFile(missing));
        rp.addTrack(none);
        rp.playTrack(none);
        pause(500);
        System.out.println("after 0.5 s: playing = " + rp.getPlayer().isPlaying()
                + "   -> no crash: a generated melody is played instead of the missing file");
        rp.stopPlaying();
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}