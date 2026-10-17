package assignment;

import java.io.File;
import java.util.ArrayList;

/**
 * Plain-Java replacement for the BlueJ-only exercises 4.1, 4.17, 4.22 and 4.23.
 * Run: java -cp bin assignment.BlueJExercisesDemo
 */
public class BlueJExercisesDemo {

    /** Exercise 4.1 / 4.2 / 4.3: Code 4.1 from the textbook (music-organizer-v1, String file names). */
    static class OrganizerV1 {
        private ArrayList<String> files = new ArrayList<String>();
        public void addFile(String filename) { files.add(filename); }
        public int getNumberOfFiles() { return files.size(); }
        public void listFile(int index) {
            if (index >= 0 && index < files.size()) System.out.println(files.get(index));
        }
        public void removeFile(int index) {
            if (index >= 0 && index < files.size()) files.remove(index);
        }
        /** Exercise 4.23 target: Code 4.3 listAllFiles (for-each). */
        public void listAllFiles() {
            for (String filename : files) {
                System.out.println(filename);
            }
        }
    }

    /** Exercise 4.16: the v1 methods rewritten to use validIndex. */
    static class OrganizerV1Valid {
        private ArrayList<String> files = new ArrayList<String>();
        public void addFile(String filename) { files.add(filename); }
        public int getNumberOfFiles() { return files.size(); }
        public boolean validIndex(int index) { return index >= 0 && index < files.size(); }
        public void listFile(int index) {
            if (validIndex(index)) System.out.println(files.get(index));
        }
        public void removeFile(int index) {
            if (validIndex(index)) files.remove(index);
        }
    }

    /** Exercise 4.17: v2 idea. A file is only "played" if it exists on disk (no mp3 decoder in plain Java). */
    static class OrganizerV2 {
        private ArrayList<String> files = new ArrayList<String>();
        private String nowPlaying;
        public void addFile(String filename) { files.add(filename); }
        public void startPlayingFile(int index) {
            String filename = files.get(index);
            File f = new File(filename);
            if (f.exists()) { nowPlaying = filename; System.out.println("Playing: " + filename); }
            else System.out.println("ERROR: cannot play, file not found: " + filename);
        }
        public void stopPlaying() { System.out.println("Stopped: " + nowPlaying); nowPlaying = null; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===== 4.1 =====");
        OrganizerV1 m = new OrganizerV1();
        m.addFile("MorningBlues.mp3");
        m.addFile("DontGo.mp3");
        m.addFile("MatchBoxBlues.mp3");
        System.out.println("stored 3, getNumberOfFiles() = " + m.getNumberOfFiles());
        System.out.print("listFile(0): "); m.listFile(0);
        System.out.print("listFile(1): "); m.listFile(1);
        System.out.print("listFile(2): "); m.listFile(2);
        System.out.println("listFile(3) (invalid, prints nothing):"); m.listFile(3);

        System.out.println("\n===== 4.2 =====");
        OrganizerV1 empty = new OrganizerV1();
        System.out.println("new organizer: getNumberOfFiles() = " + empty.getNumberOfFiles());
        System.out.println("calling removeFile(0) on the empty organizer ...");
        empty.removeFile(0);
        System.out.println("... no error, nothing happened");

        System.out.println("\n===== 4.3 =====");
        OrganizerV1 two = new OrganizerV1();
        two.addFile("MorningBlues.mp3");
        two.addFile("DontGo.mp3");
        System.out.print("listFile(0): "); two.listFile(0);
        System.out.print("listFile(1): "); two.listFile(1);
        two.removeFile(0);
        System.out.println("after removeFile(0): getNumberOfFiles() = " + two.getNumberOfFiles());
        System.out.print("listFile(0): "); two.listFile(0);
        System.out.println("listFile(1) (index 1 is no longer valid, prints nothing):"); two.listFile(1);

        System.out.println("\n===== 4.16 =====");
        OrganizerV1Valid valid = new OrganizerV1Valid();
        valid.addFile("MorningBlues.mp3"); valid.addFile("DontGo.mp3"); valid.addFile("MatchBoxBlues.mp3");
        System.out.print("listFile(1): "); valid.listFile(1);
        System.out.println("listFile(7) (invalid, prints nothing):"); valid.listFile(7);
        valid.removeFile(9);
        System.out.println("removeFile(9) (invalid): getNumberOfFiles() is still " + valid.getNumberOfFiles());
        valid.removeFile(0);
        System.out.println("removeFile(0) (valid): getNumberOfFiles() is now " + valid.getNumberOfFiles());

        System.out.println("\n===== 4.17 =====");
        File dir = new File("audio_demo"); dir.mkdirs();
        File real = new File(dir, "Real.mp3"); real.createNewFile();
        OrganizerV2 v2 = new OrganizerV2();
        v2.addFile("audio_demo/Real.mp3");
        v2.addFile("audio_demo/DoesNotExist.mp3");
        v2.startPlayingFile(0);
        v2.stopPlaying();
        v2.startPlayingFile(1);
        real.delete(); dir.delete();

        System.out.println("\n===== 4.22 (ArrayList<String> by hand, like the Code Pad) =====");
        ArrayList<String> all = new ArrayList<String>();
        System.out.println("new list: size() = " + all.size() + ", isEmpty() = " + all.isEmpty());
        all.add("one"); all.add("two"); all.add("three");
        System.out.println("after 3 add(): size() = " + all.size() + ", isEmpty() = " + all.isEmpty() + ", list = " + all);
        all.remove(1);
        System.out.println("after remove(1): size() = " + all.size() + ", list = " + all + ", get(1) = " + all.get(1));
        all.remove("one");
        System.out.println("after remove(\"one\"): size() = " + all.size() + ", list = " + all);
        all.remove(0);
        System.out.println("after remove(0): size() = " + all.size() + ", isEmpty() = " + all.isEmpty());

        System.out.println("\n===== 4.23 (manual trace of listAllFiles loop) =====");
        ArrayList<String> files = new ArrayList<String>();
        files.add("MorningBlues.mp3"); files.add("DontGo.mp3"); files.add("MatchBoxBlues.mp3");
        int pass = 0;
        for (String filename : files) {
            pass++;
            System.out.println("[iteration " + pass + "] filename = " + filename + "  -> println runs");
        }
        System.out.println("[loop exit] no elements left after " + pass + " iterations");
    }
}