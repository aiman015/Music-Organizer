MusicOrganizer_RealSound  (SEN-311 Assignment 2 - Collections and Iterators)

HOW TO RUN IN BLUEJ
  1. Unzip. In BlueJ: Project > Open Project... and choose this folder (it is already a BlueJ project).
  2. Click Compile.  Right-click assignment.Main > void main(String[] args) > OK  -> the GUI opens.
  3. Exercise evidence: right-click assignment.ExerciseDemo > main. Or create "new MusicOrganizer()" on the
     object bench and call checkIndex, validIndex, listAllTracks, listMatching ... from the right-click menu.
  4. 4.1 / 4.22 / 4.23 / 4.17: run assignment.BlueJExercisesDemo > main (plain-Java version of the BlueJ-only steps).
  Needs only the standard Java library (Java 8 or newer). Playback is REAL sound (see REAL SOUND below).

REAL SOUND  (new in this version, standard Java Sound only - no extra jars)
  assignment.AudioEngine (new) is driven by MusicPlayer:
   - A song with an audio file (WAV / AIFF / AU) plays that file. MP3 / OGG / M4A need an extra Java Sound
     plug-in that standard Java does not contain, so those fall back to the melody below.
   - A song without a file (all the built-in demo songs) plays a generated melody (chords, bass, tune, drums),
     different for every title and genre, so every song makes audible sound.
   - Play / pause / next / previous / seek bar / volume slider / mute all act on the real audio.
   - Add Song / Edit Song dialog has a new "AUDIO FILE" row with a Browse button; choosing a WAV fills in
     the real duration automatically.
   - No sound device (e.g. a server): the program still runs, silently, with a clock.
  Changed files: MusicPlayer (uses the engine), MusicPanel (volume, seek, sync, file field), Track unchanged.

PACKAGES
  assignment : Track, MusicOrganizer, MusicPlayer, AudioEngine, TrackReader, Main, ExerciseDemo
  gui        : only TWO files
                 MusicApp.java    - the window (JFrame)
                 MusicPanel.java  - the whole dashboard: layout, behaviour, song table and the helper
                                    classes (Theme, Ui, Icons, Art, Button, Dialogs ...) as nested classes

GUI (matches the supplied mockup)
  Header : logo, live search (title / artist / genre), bell, settings, avatar, minimise / maximise / close,
           drag the header to move the window.
  Sidebar: Home, My Music, Add Music, Remove Music, Playlists, Search Music, Statistics, Settings.
  Banner : "Welcome Back!" with equaliser bars and headphones artwork.
  Toolbar: Add Song, Remove Selected, Search Songs, genre filter, sort, refresh (all functional).
  Table  : select-all + per-row checkboxes, #, cover, Title, Artist, Duration, Genre, Added On,
           Play / Edit / Delete icons on every row (double-click a row also plays it).
  Right  : Now Playing, Collection Statistics and Genre Distribution (pie + legend);
           all numbers are calculated from the real song list.
  Bottom : fixed player with cover, title, artist, shuffle / previous / play-pause / next / repeat,
           volume slider and queue button. Now Playing and the bottom player always show the same song.
  Settings (sidebar / gear icon) opens the EXERCISE LAB: buttons that run checkIndex, validIndex, listTrack,
           listAllTracks, listAllTracksWithPosition, listMatching, playTrack, resetPlayCount, setRating,
           removeByTitle and playSamplesByArtist on the live collection and show their console output,
           so every code exercise can be demonstrated (and screenshotted) inside the GUI.
  The window keeps the mockup's proportions and scales to any window / screen size.
  Playback is real audio (see REAL SOUND below); the progress bar follows the audio and songs advance when they end.

EXERCISES THAT REQUIRED CODE CHANGES  (class / method)
  4.14  MusicOrganizer.checkIndex(int)
  4.15  MusicOrganizer.validIndex(int)
  4.16  MusicOrganizer.getTrack, listTrack, removeTrackAt now use validIndex
  4.17  (observation only) MusicPlayer.play / MusicOrganizer.playTrack - non-existing files are not decoded in this version
  4.18  MusicOrganizer.listAllTracks()  (header: public void listAllTracks(), no parameters)
  4.20  MusicOrganizer.listAllTracks()  (for-each loop)
  4.21  run listAllTracks() from the object bench / ExerciseDemo
  4.24  MusicOrganizer.listAllTracksWithPosition()
  4.25  MusicOrganizer.listMatching(String)
  4.26  MusicOrganizer.listMatching(String)  (boolean "found" flag, prints a message if nothing matches)
  4.27  MusicOrganizer.playSamplesByArtist(String) + MusicPlayer.playAndWait(Track)
  4.28  header only:  for(Track track : tracks)  - see MusicOrganizer.listAllTracks()
  4.35  Track.playCount, getPlayCount(), resetPlayCount(), incrementPlayCount()
  4.36  MusicOrganizer.playTrack / playNext / playPrevious / playSamplesByArtist increment the count
  4.37  Track.rating + getRating()/setRating(); Track.getDetails(); MusicOrganizer.setRating(int,int)
  4.38  MusicPlayer.play(Track) stops the current track first
  4.39  MusicOrganizer.removeByTitle(String)  (Iterator.remove)
  Also: MusicOrganizer.removeTracks(Collection) and removeByGenre(String) use Iterator.remove().
  Exercises 4.1 - 4.13 and 4.19 / 4.22 / 4.23 / 4.29 are written answers (no code change needed).

PLAIN-JAVA REPLACEMENT FOR BLUEJ-ONLY EXERCISES (4.1, 4.17, 4.22, 4.23)
  assignment.BlueJExercisesDemo  -> run it; real output saved in bluej_exercises_output.txt
