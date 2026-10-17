MUSIC ORGANIZER - SEN-311 Software Construction, Assignment 2  
Collections and Iterators (Objects First with Java, Chapter 4)  
Bahria University, Islamabad Campus

**Group members:** Aiman Farooq (Roll No. 09-131242-015)  
　　　　　　　　 Rohab Israr (Roll No. 09-131242-077)


1. HOW TO RUN (BlueJ)
---------------------
  1. Unzip the submission. In BlueJ choose Project > Open Project... and select
     the folder "MusicOrganizer" (it is already a BlueJ project).
  2. Click Compile.
  3. To open the application: right-click the class "Main" (package assignment)
     > void main(String[] args) > OK. The Music Organizer window opens.
  4. To see the exercise output in the Terminal window: right-click "ExerciseDemo"
     > void main(String[] args). For the BlueJ-only exercises (4.1, 4.2, 4.3,
     4.16 v1 version, 4.17, 4.22, 4.23) run "BlueJExercisesDemo" the same way.
  5. You can also test by hand: right-click MusicOrganizer > new MusicOrganizer(),
     then call checkIndex, validIndex, listAllTracks, listMatching and so on from
     the object's right-click menu.

  Requirements: Java 8 or newer and the standard Java library only. No extra jar
  files are needed.


2. PROJECT STRUCTURE
--------------------
  MusicOrganizer/
    assignment/                      (package: the assignment code)
      Track.java                     one song: title, artist, duration, genre,
                                     audio file, date added, play count, rating
      MusicOrganizer.java            the collection (ArrayList<Track>) and all the
                                     exercise methods
      MusicPlayer.java               play / stop / pause, next / previous
      AudioEngine.java               real sound using standard Java Sound
      MusicLibraryStorage.java       saves and loads the collection
      TrackReader.java               helper that recognises audio file types
      Main.java                      starts the application
      ExerciseDemo.java              console demo of the code exercises
      BlueJExercisesDemo.java        console demo of the BlueJ-only exercises
    gui/                             (package: the window, two files only)
      MusicApp.java                  the window (JFrame)
      MusicPanel.java                the whole dashboard: layout, behaviour and the
                                     helper classes, written as nested classes
    music-library.txt                the saved song list (created automatically)
    music/                           copies of audio files chosen with Add / Edit Song


3. EXERCISES THAT REQUIRED CODE CHANGES  (class / method)
----------------------------------------------------------
  Part 1 (4.1 - 4.6)    Observation and written answers only, no code change.
                        4.1 - 4.3 are demonstrated in BlueJExercisesDemo with the
                        textbook's version-1 code (String file names).
                        4.4 - 4.6 are field declarations (written answers).

  Part 2 (4.14 - 4.18)
    4.14  MusicOrganizer.checkIndex(int)
    4.15  MusicOrganizer.validIndex(int)
    4.16  MusicOrganizer.getTrack, listTrack and removeTrackAt now call
          validIndex (the v1 listFile / removeFile version is in
          BlueJExercisesDemo)
    4.17  Observation only: MusicPlayer.play(Track) and AudioEngine. Files that do
          not exist are not played. See BlueJExercisesDemo and ExerciseDemo.
    4.18  Written answer: the header is  public void listAllTracks()
          (no parameters, void return type). Implemented in 4.20.

  Part 3 (4.20 - 4.28)
    4.20  MusicOrganizer.listAllTracks()                  (for-each loop)
    4.21  Run listAllTracks() from the object bench or ExerciseDemo
    4.22  Code Pad experiment with ArrayList<String> (BlueJ); also traced in
          BlueJExercisesDemo
    4.23  Debugger trace of the for-each loop (BlueJ); also traced in
          BlueJExercisesDemo
    4.24  MusicOrganizer.listAllTracksWithPosition()
    4.25  MusicOrganizer.listMatching(String)
    4.26  MusicOrganizer.listMatching(String)   (boolean "found" flag, prints a
          message when nothing matches)
    4.27  MusicOrganizer.playSamplesByArtist(String) with
          MusicPlayer.playAndWait(Track)
    4.28  Written answer: for(Track track : tracks)

  Part 4 (4.35 - 4.39)
    4.35  Track: playCount field, getPlayCount(), resetPlayCount(),
          incrementPlayCount()
    4.36  MusicOrganizer.playTrack, playNext, playPrevious and
          playSamplesByArtist increase the play count
    4.37  Track: rating field with getRating() / setRating();
          Track.getDetails(); MusicOrganizer.setRating(int, int)
    4.38  MusicPlayer.play(Track) stops the current track before starting the
          new one
    4.39  MusicOrganizer.removeByTitle(String)   (Iterator and it.remove())

  Also written with an Iterator: MusicOrganizer.removeByGenre(String) and
  MusicOrganizer.removeTracks(Collection) (used by "Remove Selected").


4. REAL SOUND
-------------
  - A song with a WAV, AIFF or AU file plays that file. Other formats (MP3, OGG,
    M4A) cannot be decoded by standard Java, so they are not played.
  - A song without an audio file (the six starter songs) plays a generated
    melody, so every song makes a sound.
  - Add Song / Edit Song has an AUDIO FILE row with a Browse button. The chosen
    file is copied into the project's "music" folder and its length fills in the
    duration for WAV files.
  - Play, pause, next, previous, shuffle, repeat, the seek bar, the volume slider
    and mute all act on the audio.
  - If the computer has no sound device the program still runs, silently, using
    a clock.


5. THE APPLICATION (GUI)
------------------------
  Header   : logo, search (title / artist / genre), bell, settings gear, avatar,
             minimise / maximise / close. Drag the header to move the window.
             The bell lists live notifications (song added, updated or deleted)
             and shows a badge for unread ones. The avatar and the "..." icon on
             Now Playing are decoration only and do nothing.
  Sidebar  : Home, Add Music, Remove Music, Playlists (by genre), Search Music,
             Statistics, Settings.
  Toolbar  : Add Song, Remove Selected, genre filter, sort, refresh.
  Table    : checkboxes, number, cover, title, artist, duration, genre, date
             added, and play / edit / delete icons on each row. Double-click a
             row to play it.
  Right    : Now Playing, Collection Statistics and Genre Distribution. All
             numbers are calculated from the real song list.
  Bottom   : player bar with cover, title, artist, shuffle, previous, play/pause,
             next, repeat, volume and queue.
  Settings : opens the EXERCISE LAB. Its buttons run checkIndex, validIndex,
             listTrack, listAllTracks, listAllTracksWithPosition, listMatching,
             playTrack, resetPlayCount, setRating, removeByTitle and
             playSamplesByArtist on the live collection and show the console
             output, so each code exercise can be demonstrated inside the GUI.
  The collection is saved to music-library.txt and loaded again on the next start.
