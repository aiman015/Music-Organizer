package assignment;
import java.io.File;import java.util.*;
public class TrackReader {private static final String[] EXT={".mp3",".wav",".aiff",".aif",".m4a",".ogg"};public List<File> readTracks(File dir){ArrayList<File>out=new ArrayList<>();if(dir==null||!dir.isDirectory())return out;File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.isFile()&&isAudioFile(f))out.add(f);return out;}public boolean isAudioFile(File f){if(f==null)return false;String n=f.getName().toLowerCase(Locale.ROOT);for(String e:EXT)if(n.endsWith(e))return true;return false;}}
