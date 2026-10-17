package assignment;

import java.util.List;
import java.util.Random;

/**
 * Playback controller. Keeps track of the current track and plays it for real through
 * {@link AudioEngine} (standard Java Sound): the track's own audio file when it has a
 * playable one, otherwise a generated melody.
 */
public class MusicPlayer {
    private Track current;
    private final AudioEngine engine = new AudioEngine();
    private boolean started;       // engine currently holds this track (playing or paused)
    private boolean playing, shuffle, repeat;
    private final Random random = new Random();
    /** How long playAndWait() "plays" a sample (the textbook player uses about 15 s). */
    private long sampleMillis = 1500;

    public Track getCurrentTrack() { return current; }
    public boolean isPlaying()     { return playing; }
    public boolean isShuffle()     { return shuffle; }
    public boolean isRepeat()      { return repeat; }
    public void setSampleMillis(long ms) { sampleMillis = Math.max(0, ms); }

    /** Exercise 4.38: starting a track first stops whatever is already playing. */
    public void play(Track t) {
        if (t == null) return;
        if (playing) stop();
        current = t;
        playing = true;
        started = true;
        engine.start(t);
    }

    public void stop()  { playing = false; started = false; engine.stop(); }
    public void pause() { playing = false; engine.pause(); }

    public void togglePlayPause() {
        if (current == null) return;
        if (playing) { pause(); return; }
        if (started) { playing = true; engine.resume(); }
        else { playing = true; started = true; engine.start(current); }
    }

    // ---- real audio helpers used by the GUI -------------------------------
    public AudioEngine getEngine()         { return engine; }
    public void setVolume(double v)        { engine.setVolume(v); }
    public void seekSeconds(double sec)    { if (current != null) engine.seekSeconds(sec); }
    public int  getPositionSeconds()       { return (int) engine.getPositionSeconds(); }
    /** True once the audio of the current track has played to its end. */
    public boolean hasFinishedTrack()      { return playing && engine.isFinished(); }
    public void toggleShuffle()   { shuffle = !shuffle; }
    public void toggleRepeat()    { repeat = !repeat; }

    /** Forget the current track (used when it is removed from the collection). */
    public void clear() { current = null; playing = false; started = false; engine.stop(); }

    /** Exercise 4.27: play a short sample and only return when it has finished. */
    public void playAndWait(Track t) {
        play(t);
        try { Thread.sleep(sampleMillis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        stop();
    }

    public Track next(List<Track> all) {
        if (all == null || all.isEmpty()) { clear(); return null; }
        int i = all.indexOf(current);
        int target;
        if (shuffle && all.size() > 1) {
            do { target = random.nextInt(all.size()); } while (target == i);
        } else {
            target = (i + 1) % all.size();
        }
        play(all.get(target));
        return current;
    }

    public Track previous(List<Track> all) {
        if (all == null || all.isEmpty()) { clear(); return null; }
        int i = all.indexOf(current);
        play(all.get((i - 1 + all.size()) % all.size()));
        return current;
    }
}
