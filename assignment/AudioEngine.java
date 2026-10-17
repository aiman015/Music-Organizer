package assignment;

import java.io.File;
import javax.sound.sampled.*;

/**
 * Real audio output using only the standard javax.sound.sampled API.
 *
 *  - A track that has an audio file (WAV / AIFF / AU, and MP3 / OGG / M4A if a Java Sound
 *    service provider for them is on the class path) is decoded and played.
 *  - A track without a playable file is played as a generated melody (see {@link SynthSource}),
 *    so every song in the collection makes real, audible sound.
 *
 * If the computer has no sound device the engine keeps working silently with a clock, so the
 * rest of the program (and the exercises) behave exactly the same.
 */
public class AudioEngine {
    /** Output format used for everything: 44.1 kHz, 16-bit, stereo, little endian. */
    public static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 2, true, false);
    static final int FRAME_BYTES = 4;

    /** Anything that can hand out PCM frames at a given position. */
    interface Source {
        long totalFrames();
        /** Fill buf with 'frames' stereo frames starting at frame 'start'. Returns frames written. */
        int read(byte[] buf, long start, int frames);
    }

    /** Source backed by a fully decoded PCM byte array. */
    static final class PcmSource implements Source {
        private final byte[] data;
        PcmSource(byte[] data) { this.data = data; }
        public long totalFrames() { return data.length / FRAME_BYTES; }
        public int read(byte[] buf, long start, int frames) {
            long from = start * FRAME_BYTES;
            int n = (int) Math.min((long) frames * FRAME_BYTES, Math.max(0, data.length - from));
            if (n > 0) System.arraycopy(data, (int) from, buf, 0, n);
            return n / FRAME_BYTES;
        }
    }

    private final Object lock = new Object();
    private Thread worker;
    private volatile int generation;              // bumped for every new track / stop so old threads end
    private Source source;
    private volatile long position;      // in frames
    private volatile boolean playing, finished, hasDevice = true;
    private volatile double volume = 0.65;
    private volatile String lastError = "";

    public boolean isFinished()  { return finished; }
    public boolean hasDevice()   { return hasDevice; }
    public String  getLastError(){ return lastError; }
    public void setVolume(double v) { volume = Math.max(0, Math.min(1, v)); }
    public double getVolume()    { return volume; }
    public double getPositionSeconds() { return position / (double) FORMAT.getFrameRate(); }
    public double getLengthSeconds() {
        Source s = source;
        return s == null ? 0 : s.totalFrames() / (double) FORMAT.getFrameRate();
    }

    // ------------------------------------------------------------------ loading

    /** Decodes a file to our PCM format, or returns null when Java Sound cannot read it. */
    static Source loadFile(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(f)) {
            AudioFormat base = raw.getFormat();
            AudioInputStream pcm = raw;
            if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || base.getSampleSizeInBits() != 16) {
                AudioFormat mid = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                        base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
                pcm = AudioSystem.getAudioInputStream(mid, raw);
            }
            AudioInputStream out = AudioSystem.getAudioInputStream(FORMAT, pcm);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[1 << 16];
            int n;
            long cap = 600L * 1024 * 1024;
            while ((n = out.read(b)) > 0) {
                bos.write(b, 0, n);
                if (bos.size() > cap) break;
            }
            return new PcmSource(bos.toByteArray());
        } catch (Exception | Error e) {
            return null;
        }
    }

    /** Length in seconds of an audio file, or -1 if it cannot be read. */
    public static int probeSeconds(String path) {
        if (path == null || path.trim().isEmpty()) return -1;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(path))) {
            long frames = in.getFrameLength();
            float rate = in.getFormat().getFrameRate();
            if (frames > 0 && rate > 0) return Math.max(1, Math.round(frames / rate));
        } catch (Exception | Error ignored) { }
        return -1;
    }

    /** True when the file is something we can really decode. */
    public static boolean canPlayFile(String path) { return probeSeconds(path) > 0 || loadFile(path) != null; }

    // ------------------------------------------------------------------ control

    /** Start a new track from the beginning. */
    public void start(Track t) {
        synchronized (lock) {
            generation++;
            finished = false;
            position = 0;
            Source s = loadFile(t.getFilePath());
            if (s == null) {
                int secs = Math.max(5, t.getDurationSeconds());
                s = new SynthSource(t.getTitle() + "|" + t.getArtist(), t.getGenre(), secs);
            }
            source = s;
            playing = true;
            final int gen = generation;
            worker = new Thread(() -> run(gen), "audio-engine");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void pause()  { playing = false; }
    public void resume() { if (source != null && !finished) playing = true; }

    /** Stop and forget the track. */
    public void stop() {
        synchronized (lock) {
            generation++;
            playing = false;
            finished = false;
            source = null;
            position = 0;
        }
    }

    public void seekSeconds(double sec) {
        Source s = source;
        if (s == null) return;
        long fr = (long) (sec * FORMAT.getFrameRate());
        position = Math.max(0, Math.min(s.totalFrames() - 1, fr));
        finished = false;
    }

    // ------------------------------------------------------------------ playback thread

    private void run(int gen) {
        Source s = source;
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, (int) (FORMAT.getFrameRate() * FRAME_BYTES * 0.25));
            line.start();
            hasDevice = true;
        } catch (Exception | Error e) {
            hasDevice = false;          // no speakers: keep time silently
            lastError = String.valueOf(e.getMessage());
            line = null;
        }
        final int chunk = 2205;         // 50 ms
        byte[] buf = new byte[chunk * FRAME_BYTES];
        long lastNano = System.nanoTime();
        try {
            while (gen == generation) {
                if (!playing) {
                    if (line != null && line.isRunning()) { line.stop(); line.flush(); }
                    Thread.sleep(25);
                    lastNano = System.nanoTime();
                    continue;
                }
                if (line != null && !line.isRunning()) line.start();
                long pos = position;
                if (pos >= s.totalFrames()) { finished = true; playing = false; break; }
                int got = s.read(buf, pos, chunk);
                if (got <= 0) { finished = true; playing = false; break; }
                applyVolume(buf, got);
                if (line != null) {
                    line.write(buf, 0, got * FRAME_BYTES);
                } else {
                    long due = (long) (got * 1_000_000_000L / FORMAT.getFrameRate());
                    long wait = due - (System.nanoTime() - lastNano);
                    if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    lastNano = System.nanoTime();
                }
                if (gen == generation && position == pos) position = pos + got;
            }
            if (line != null && gen == generation && finished) line.drain();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            if (line != null) { line.stop(); line.close(); }
        }
    }

    private void applyVolume(byte[] buf, int frames) {
        double g = volume * volume;     // perceptual curve
        for (int i = 0; i < frames * 2; i++) {
            int lo = buf[2 * i] & 0xff, hi = buf[2 * i + 1];
            int v = (int) (((hi << 8) | lo) * g);
            buf[2 * i] = (byte) v;
            buf[2 * i + 1] = (byte) (v >> 8);
        }
    }

    // ------------------------------------------------------------------ generated music

    /**
     * A small stateless synthesizer: every sample is a pure function of the time, so seeking is free.
     * Chords, bass, a repeating melody phrase and (for pop / rock / dance styles) drums. Each song
     * title gives its own key, tempo and tune, so different songs sound different but are repeatable.
     */
    static final class SynthSource implements Source {
        private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
        private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
        private static final int[][] PROG_MAJOR = {{0, 4, 5, 3}, {0, 5, 3, 4}, {0, 3, 4, 4}, {5, 3, 0, 4}};
        private static final int[][] PROG_MINOR = {{0, 5, 2, 6}, {0, 3, 6, 4}, {0, 6, 5, 4}, {0, 2, 5, 6}};

        private final long seed;
        private final int[] scale;
        private final int[] prog;
        private final int root;           // MIDI note of the tonic (below middle C range)
        private final double beat;        // seconds per beat
        private final boolean drums, snare;
        private final int totalFrames;
        private final double melodyAmp, padAmp;

        SynthSource(String key, String genre, int seconds) {
            this.seed = mix(key.toLowerCase().hashCode() * 0x9E3779B97F4A7C15L + 12345);
            String g = genre == null ? "" : genre.toLowerCase();
            boolean minor = g.contains("rock") || g.contains("metal") || g.contains("hip") || g.contains("rap")
                    || (g.contains("other") && (seed & 1) == 0) || g.contains("jazz") && (seed & 2) == 0;
            if (g.contains("pop") || g.contains("country") || g.contains("classic")) minor = (seed & 7) == 0;
            this.scale = minor ? MINOR : MAJOR;
            int[][] progs = minor ? PROG_MINOR : PROG_MAJOR;
            this.prog = progs[(int) ((seed >>> 8) & 3)];
            this.root = 48 + (int) ((seed >>> 12) % 12);
            int bpm;
            if (g.contains("rock")) bpm = 112 + (int) ((seed >>> 20) % 24);
            else if (g.contains("pop")) bpm = 96 + (int) ((seed >>> 20) % 24);
            else if (g.contains("classic") || g.contains("jazz")) bpm = 72 + (int) ((seed >>> 20) % 20);
            else bpm = 90 + (int) ((seed >>> 20) % 30);
            this.beat = 60.0 / bpm;
            this.drums = !(g.contains("classic"));
            this.snare = g.contains("rock") || g.contains("pop") || g.contains("hip") || g.contains("rap");
            this.melodyAmp = 0.30;
            this.padAmp = g.contains("classic") ? 0.16 : 0.11;
            this.totalFrames = seconds * 44100;
        }

        public long totalFrames() { return totalFrames; }

        public int read(byte[] buf, long start, int frames) {
            int n = (int) Math.min(frames, totalFrames - start);
            if (n < 0) n = 0;
            for (int i = 0; i < n; i++) {
                long fi = start + i;
                double t = fi / 44100.0;
                double l, r;
                double[] s = sample(t, fi);
                double fade = Math.min(1, t / 0.4) * Math.min(1, (totalFrames - fi) / 44100.0 / 1.5);
                l = Math.tanh(s[0] * fade * 1.3) * 0.85;
                r = Math.tanh(s[1] * fade * 1.3) * 0.85;
                int li = (int) (l * 32767), ri = (int) (r * 32767);
                buf[4 * i] = (byte) li; buf[4 * i + 1] = (byte) (li >> 8);
                buf[4 * i + 2] = (byte) ri; buf[4 * i + 3] = (byte) (ri >> 8);
            }
            return n;
        }

        private static double hz(double midi) { return 440.0 * Math.pow(2, (midi - 69) / 12.0); }

        private int degreeToMidi(int degree) {
            int oct = Math.floorDiv(degree, 7);
            return root + 12 + scale[Math.floorMod(degree, 7)] + 12 * oct;
        }

        private static long mix(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        private static double noise(long n) { return ((mix(n) >>> 11) / (double) (1L << 53)) * 2 - 1; }

        private double[] sample(double t, long fi) {
            double bt = t / beat;                 // time in beats
            int beatIdx = (int) Math.floor(bt);
            double inBeat = (bt - beatIdx) * beat; // seconds since beat start
            int bar = beatIdx / 4;
            int chordDeg = prog[bar % 4];

            // --- pad: triad of the current chord, soft, re-struck every bar
            double barT = (bt - bar * 4) * beat;
            double padEnv = Math.min(1, barT / 0.12) * (0.65 + 0.35 * Math.exp(-barT * 1.2));
            double pad = 0;
            for (int k = 0; k < 3; k++) {
                double f = hz(degreeToMidi(chordDeg + 2 * k) - 12);
                pad += Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t);
            }
            pad *= padAmp * padEnv / 3.0;

            // --- bass: root of chord on every beat (octave jumps on off-beats)
            double bassF = hz(degreeToMidi(chordDeg) - 24);
            double eighth = (bt * 2) % 1.0;
            int e8 = (int) Math.floor(bt * 2);
            if ((e8 & 1) == 1 && ((mix(seed + e8 / 2) & 3) != 0)) bassF *= 2;
            double bEnv = Math.exp(-eighth * 2.2) * Math.min(1, eighth * 50);
            double bass = (Math.sin(2 * Math.PI * bassF * t) + 0.3 * Math.sin(4 * Math.PI * bassF * t)) * 0.26 * bEnv;

            // --- melody: a 4-bar phrase that repeats, notes land on eighth/quarter grid
            int phrase = (beatIdx % 16) * 2 + (int) Math.floor(((bt - beatIdx) * 2));
            long h = mix(seed + 77 * phrase + 5);
            double mel = 0;
            boolean rest = (h & 7) < 2 && (phrase % 2 == 1);
            if (!rest) {
                int pos = phrase % 8;
                int cd = prog[(phrase / 8) % 4];
                int deg;
                if (pos == 0 || pos == 4) deg = cd + 7 + 2 * (int) ((h >>> 5) % 2);        // chord tone on strong beats
                else deg = cd + 7 + (int) ((h >>> 9) % 6) - 1;                               // scale tone
                double noteStart = (Math.floor(bt * 2)) / 2.0;
                double nT = (bt - noteStart) * beat;
                double f = hz(degreeToMidi(deg));
                double env = Math.exp(-nT * 3.2) * Math.min(1, nT * 400);
                double vib = 1 + 0.002 * Math.sin(2 * Math.PI * 5.5 * t);
                mel = (Math.sin(2 * Math.PI * f * vib * t) + 0.45 * Math.sin(4 * Math.PI * f * t)
                        + 0.2 * Math.sin(6 * Math.PI * f * t)) * env * melodyAmp / 1.4;
            }

            // --- drums
            double dr = 0;
            if (drums) {
                int bInBar = beatIdx % 4;
                if (bInBar == 0 || bInBar == 2) {
                    double kf = 45 + 90 * Math.exp(-inBeat * 30);
                    dr += Math.sin(2 * Math.PI * kf * inBeat * (1 + 0.0)) * Math.exp(-inBeat * 9) * 0.5;
                }
                if (snare && (bInBar == 1 || bInBar == 3)) dr += noise(fi) * Math.exp(-inBeat * 16) * 0.22;
                double hatT = (bt * 2 % 1.0) * beat / 2;
                dr += (noise(fi) - noise(fi - 1)) * 0.5 * Math.exp(-hatT * 60) * 0.12;
            }

            double centre = pad + bass + dr;
            double w = mel;
            double width = 0.12 * Math.sin(2 * Math.PI * 0.2 * t);   // gentle stereo movement of the melody
            return new double[]{centre + w * (1 - width), centre + w * (1 + width)};
        }
    }
}
