package gui;

import assignment.MusicOrganizer;
import java.awt.*;
import javax.swing.JFrame;

/** The application window. It is undecorated because the design draws its own minimise / maximise / close buttons. */
public class MusicApp extends JFrame {
    public MusicApp(MusicOrganizer organizer, int startElapsedSeconds) {
        super("Music Organizer");
        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(MusicPanel.Theme.BG);
        setContentPane(new MusicPanel(organizer, startElapsedSeconds));

        // The design is 1536 x 1024; shrink it (keeping the proportions) when the screen is smaller.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double s = Math.min(1.0, Math.min(screen.width * 0.98 / MusicPanel.DW, screen.height * 0.98 / MusicPanel.DH));
        setSize((int) Math.round(MusicPanel.DW * s), (int) Math.round(MusicPanel.DH * s));
        setMinimumSize(new Dimension(768, 512));
        setLocationRelativeTo(null);
    }
}
