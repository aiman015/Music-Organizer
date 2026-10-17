package gui;

import assignment.MusicOrganizer;
import assignment.MusicPlayer;
import assignment.MusicLibraryStorage;
import java.io.IOException;
import assignment.Track;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The whole Music Organizer window (everything except the JFrame, which lives in MusicApp).
 *
 * Every piece is placed at a fixed position of a 1536 x 1024 design and scaled to the window,
 * so the dashboard keeps the proportions of the mock-up at any window size.
 *
 * Contents of this file, top to bottom:
 *   1. MusicPanel      - layout, state and behaviour (add / edit / remove / search / filter / sort / play)
 *   2. Cards           - header, sidebar, welcome banner, toolbar, collection card, right-hand cards, bottom player
 *   3. Table + widgets - TrackList (song table), Dropdown, ProgressPart, VolumePart, AvatarPart
 *   4. Helpers         - Theme, Ui, Glyph, Icons, Art, Part, Button, Dialogs (all static nested classes)
 */
public class MusicPanel extends JPanel {
    static final int DW = 1536, DH = 1024;
    static final String ALL = "All Genres";
    static final String[] SORTS = {"Title", "Artist", "Duration", "Genre", "Added On"};
    static final String[] PIE_GENRES = {"Pop", "Rock", "Hip Hop", "R&B"};
    static final Color[] PIE_COLORS = {new Color(0xC8102E), new Color(0xFF3B55), new Color(0xE58A1F), new Color(0x8E97A6)};
    static final Color OTHER_COLOR = new Color(0x4F5666);

    private final MusicOrganizer organizer;
    private final MusicPlayer player;
    private List<Track> shown = new ArrayList<>();
    private final Set<Track> checked = new LinkedHashSet<>();

    private final JTextField headerSearch = new JTextField();
    private final JTextField toolSearch = new JTextField();
    private final Rectangle headerFieldDesign = new Rectangle(586, 35, 500, 34);
    private final Rectangle toolFieldDesign = new Rectangle(648, 274, 174, 32);

    private final Button[] nav = new Button[7];
    private Dropdown genreBox, sortBox;
    private final javax.swing.Timer ticker;
    private int active = 0, elapsed, sortMode = -1;   // -1 = collection order
    private String genreFilter = ALL;
    private boolean syncing;
    private double volume = 0.65;
    private Track lastCurrent;
    private double f = 1;

    // live notifications (bell list + short pop-up message)
    private final List<String> notifications = new ArrayList<>();
    private int unread;
    private String toastTitle, toastMsg;
    private long toastUntil;

    public MusicPanel(MusicOrganizer organizer, int startElapsedSeconds) {
        this.organizer = organizer;
        this.player = organizer.getPlayer();
        this.elapsed = startElapsedSeconds;
        player.setVolume(volume);
        if (player.getCurrentTrack() != null && startElapsedSeconds > 0) player.seekSeconds(startElapsedSeconds);
        setLayout(null);
        setBackground(Theme.BG);
        setOpaque(true);

        buildCards();
        buildControls();
        buildSearchFields();

        lastCurrent = player.getCurrentTrack();
        refreshList();
        ticker = new javax.swing.Timer(250, e -> tick());
        ticker.start();
    }

    // =====================================================================
    // Stage layout: place every child by its design rectangle
    // =====================================================================
    @Override
    public void doLayout() {
        f = Math.min(getWidth() / (double) DW, getHeight() / (double) DH);
        double ox = (getWidth() - DW * f) / 2.0, oy = (getHeight() - DH * f) / 2.0;
        for (Component c : getComponents()) {
            if (c instanceof Part) {
                Rectangle r = ((Part) c).design;
                c.setBounds((int) Math.round(ox + r.x * f), (int) Math.round(oy + r.y * f),
                        (int) Math.round(r.width * f), (int) Math.round(r.height * f));
            }
        }
        place(headerSearch, headerFieldDesign, ox, oy, 16.5);
        place(toolSearch, toolFieldDesign, ox, oy, 14.5);
    }

    private void place(JTextField tf, Rectangle d, double ox, double oy, double fontSize) {
        tf.setBounds((int) Math.round(ox + d.x * f), (int) Math.round(oy + d.y * f),
                (int) Math.round(d.width * f), (int) Math.round(d.height * f));
        tf.setFont(Ui.font(false, fontSize * f));
    }

    /** Components added later sit on top, so cards are added before the controls. */
    private <T extends Part> T put(T p) { add(p, 0); return p; }

    private Window window() { return SwingUtilities.getWindowAncestor(this); }

    // =====================================================================
    // Cards (static decoration that reads live data from the organizer)
    // =====================================================================
    private void buildCards() {
        put(new HeaderPart());
        put(new SidebarPart());
        put(new BannerPart());
        put(new ToolbarPart());
        put(new CollectionPart());
        put(new NowPart());
        put(new StatsPart());
        put(new GenrePart());
        put(new BottomPart());
    }

    /** The dark rounded panel used by every card. */
    private static void card(Graphics2D g, double x, double y, double w, double h, double r) {
        Ui.roundRect(g, x, y, w, h, r, Theme.PANEL, Theme.BORDER, 1.4);
    }

    private abstract class Decor extends Part {
        Decor(int x, int y, int w, int h) { super(x, y, w, h); }
        @Override void paintPart(Graphics2D g, int w, int h) {
            g.translate(-design.x, -design.y);          // draw with absolute design coordinates
            paintGlobal(g);
        }
        abstract void paintGlobal(Graphics2D g);
    }

    private class HeaderPart extends Decor {
        private Point pressScreen, pressLoc;

        HeaderPart() {
            super(0, 0, DW, 93);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    pressScreen = e.getLocationOnScreen();
                    Window w = window();
                    pressLoc = w == null ? null : w.getLocation();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    Window w = window();
                    if (w instanceof Frame && (((Frame) w).getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) return;
                    if (w != null && pressLoc != null) {
                        Point now = e.getLocationOnScreen();
                        w.setLocation(pressLoc.x + now.x - pressScreen.x, pressLoc.y + now.y - pressScreen.y);
                    }
                }
                @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) toggleMaximize(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override void paintGlobal(Graphics2D g) {
            g.setColor(Theme.BG);
            g.fillRect(0, 0, DW, 93);
            g.setColor(new Color(0x151A22));
            g.fillRect(0, 92, DW, 1);
            // logo
            g.setPaint(Ui.redGradient(26, 20, 62, 62));
            g.fill(new Ellipse2D.Double(26, 20, 62, 62));
            Icons.NOTE.draw(g, 57, 51, 30, Color.WHITE);
            // brand
            Font bf = Ui.font(true, 28);
            Ui.text(g, "Music ", 104, 40, bf, Theme.TEXT);
            Ui.text(g, "Organizer", 104 + Ui.textW(g, "Music ", bf), 40, bf, Theme.RED_LIGHT);
            Font sf = Ui.font(false, 14);
            double x = 104;
            String[] words = {"Organize", "Play", "Enjoy"};
            for (int i = 0; i < words.length; i++) {
                Ui.text(g, words[i], x, 70, sf, Theme.MUTED);
                x += Ui.textW(g, words[i], sf) + 7;
                if (i < 2) {
                    g.setColor(Theme.RED);
                    g.fill(new Ellipse2D.Double(x, 68.3, 3.4, 3.4));
                    x += 3.4 + 7;
                }
            }
            // search pill
            Ui.roundRect(g, 526, 26, 588, 51, 25.5, Theme.PANEL, Theme.BORDER, 1.4);
            Icons.SEARCH.draw(g, 556, 51.5, 24, Theme.TEXT);
            if (headerSearch.getText().isEmpty()) {
                Ui.text(g, "Search by title, artist, or genre...", 588, 52, Ui.font(false, 16.5), Theme.MUTED);
            }
            if (unread > 0) {                       // bell badge
                g.setColor(Theme.RED);
                g.fill(new Ellipse2D.Double(1356, 36, 17, 17));
                Ui.textC(g, unread > 9 ? "9+" : String.valueOf(unread), 1364.5, 44.5, Ui.font(true, 11), Color.WHITE);
            }
        }
    }

    private class SidebarPart extends Decor {
        SidebarPart() { super(0, 93, 263, 804); }

        @Override void paintGlobal(Graphics2D g) {
            Shape shape = new RoundRectangle2D.Double(-30, 93, 293, 804, 18, 18);
            g.setPaint(Theme.SIDEBAR);
            g.fill(shape);
            Graphics2D c = (Graphics2D) g.create();
            c.clip(shape);
            waves(c);
            c.dispose();
            g.setColor(new Color(0x161B24));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Double(-30, 93.7, 292.3, 802.6, 18, 18));

            // caption
            equalizer(g, 131, 759);
            Font mf = Ui.font(false, 16);
            Ui.textC(g, "Good Music", 131, 794, mf, Theme.MUTED);
            Ui.textC(g, "Good Mood", 131, 817, mf, Theme.MUTED);
            g.setColor(Theme.RED);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Line2D.Double(68, 843, 108, 843));
            g.draw(new Line2D.Double(154, 843, 194, 843));
            equalizer(g, 131, 843);
        }

        private void equalizer(Graphics2D g, double cx, double cy) {
            double[] bars = {8, 15, 24, 15, 8};
            g.setColor(Theme.RED);
            for (int i = 0; i < bars.length; i++) {
                g.fill(new RoundRectangle2D.Double(cx - 10 + i * 4.8, cy - bars[i] / 2, 2.4, bars[i], 2, 2));
            }
        }

        private void waves(Graphics2D g) {
            fillBand(g, 520, 540, 684, 694, new Color(0xFF3B55), 90, new Color(0x7A0816), 130);
            fillBand(g, 534, 600, 694, 726, new Color(0xE50D22), 215, new Color(0x6E0715), 235);
            fillBand(g, 580, 660, 716, 770, new Color(0xFF2B3F), 150, new Color(0x45040D), 230);
            fillBand(g, 612, 700, 742, 800, new Color(0xB30B1C), 190, new Color(0x2B030A), 235);
            fillBand(g, 521, 524, 686, 690, new Color(0xFF8A96), 230, new Color(0xFF3B55), 40);
        }

        private void fillBand(Graphics2D g, double rt, double rb, double lt, double lb,
                              Color c1, int a1, Color c2, int a2) {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(263, rt);
            p.curveTo(205, rt + 28, 150, lt - 62, -2, lt);
            p.lineTo(-2, lb);
            p.curveTo(150, lb - 62, 205, rb + 28, 263, rb);
            p.closePath();
            g.setPaint(new GradientPaint(263, (float) rt, Ui.alpha(c1, a1), 0, (float) lb, Ui.alpha(c2, a2)));
            g.fill(p);
        }
    }

    /** "Welcome Back!" banner with red equaliser bars and the headphones illustration. */
    private class BannerPart extends Decor {
        BannerPart() { super(283, 107, 866, 139); }

        @Override void paintGlobal(Graphics2D g) {
            Shape shape = new RoundRectangle2D.Double(283, 107, 866, 139, 36, 36);
            g.setPaint(new GradientPaint(283, 107, new Color(0x0C1017), 1149, 107, new Color(0x180A10)));
            g.fill(shape);
            Graphics2D c = (Graphics2D) g.create();
            c.clip(shape);
            c.setPaint(new RadialGradientPaint(new Point2D.Double(1070, 190), 230, new float[]{0f, 1f},
                    new Color[]{new Color(229, 13, 34, 95), new Color(229, 13, 34, 0)}));
            c.fill(new Rectangle2D.Double(283, 107, 866, 139));
            waveform(c);
            headphones(c, 1046, 189, 1.05);
            c.dispose();
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Double(283.7, 107.7, 864.6, 137.6, 36, 36));

            Ui.text(g, "Welcome Back!", 307, 148, Ui.font(true, 32), Theme.TEXT);
            Font df = Ui.font(false, 16.5);
            Ui.text(g, "Manage your music collection, explore your favorites,", 307, 181, df, new Color(0xD7DBE2));
            Ui.text(g, "and keep your playlist organized.", 307, 204, df, new Color(0xD7DBE2));
        }

        private void waveform(Graphics2D g) {
            int n = 46;
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                double env = Math.pow(t, 1.25);
                double amp = 0.35 + 0.65 * Math.abs(Math.sin(i * 0.62) * Math.cos(i * 0.21 + 0.6));
                double h = 5 + 70 * env * amp;
                int a = (int) (45 + 205 * env);
                g.setColor(Ui.alpha(Theme.RED_LIGHT, Math.min(255, a)));
                double x = 745 + i * 4.55;
                g.fill(new RoundRectangle2D.Double(x, 182 - h / 2, 2.1, h, 2, 2));
            }
        }

        /** Red-and-black headphones drawn with Java2D shapes, centred on (cx, cy). */
        private void headphones(Graphics2D g0, double cx, double cy, double s) {
            Graphics2D g = (Graphics2D) g0.create();
            g.translate(cx, cy);
            g.scale(s, s);
            g.rotate(Math.toRadians(-7));
            // glow behind the cups
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 10), 100, new float[]{0f, 1f},
                    new Color[]{new Color(255, 40, 60, 95), new Color(255, 40, 60, 0)}));
            g.fill(new Ellipse2D.Double(-100, -90, 200, 200));
            // headband
            Arc2D band = new Arc2D.Double(-57, -56, 114, 128, 0, 180, Arc2D.OPEN);
            g.setStroke(new BasicStroke(13, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x0B0D12));
            g.draw(band);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setPaint(new GradientPaint(-60, 0, new Color(255, 59, 85, 60), 60, -60, new Color(255, 59, 85, 235)));
            g.draw(new Arc2D.Double(-63.5, -62.5, 127, 141, 8, 164, Arc2D.OPEN));
            g.setStroke(new BasicStroke(1.4f));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new Arc2D.Double(-53, -52, 106, 120, 20, 140, Arc2D.OPEN));
            // ear cups
            cup(g, -57, 20, 21, 33);
            cup(g, 57, 20, 21, 33);
            g.dispose();
        }

        private void cup(Graphics2D g, double cx, double cy, double rx, double ry) {
            g.setPaint(new GradientPaint((float) (cx - rx), (float) (cy - ry), new Color(0x2C313B), (float) (cx + rx), (float) (cy + ry), new Color(0x07080B)));
            g.fill(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
            g.setStroke(new BasicStroke(3f));
            g.setPaint(new GradientPaint((float) cx, (float) (cy - ry), new Color(0xFF4A5E), (float) cx, (float) (cy + ry), new Color(0x8A0A1A)));
            g.draw(new Ellipse2D.Double(cx - rx + 1.5, cy - ry + 1.5, rx * 2 - 3, ry * 2 - 3));
            g.setPaint(new GradientPaint((float) cx, (float) (cy - ry * 0.6), new Color(0x1B0D12), (float) cx, (float) (cy + ry * 0.6), new Color(0x0A0A0E)));
            g.fill(new Ellipse2D.Double(cx - rx * 0.62, cy - ry * 0.62, rx * 1.24, ry * 1.24));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (rx * 0.62), new float[]{0f, 1f},
                    new Color[]{new Color(255, 40, 60, 150), new Color(255, 40, 60, 0)}));
            g.fill(new Ellipse2D.Double(cx - rx * 0.62, cy - ry * 0.62, rx * 1.24, ry * 1.24));
        }
    }

    /** Dark bar behind the toolbar controls, plus the search-songs box. */
    private class ToolbarPart extends Decor {
        ToolbarPart() { super(283, 261, 866, 57); }

        @Override void paintGlobal(Graphics2D g) {
            card(g, 283, 261, 866, 57, 14);
            Ui.roundRect(g, 603, 269, 227, 41, 9, new Color(0x10151C), new Color(0x242B36), 1.2);
            Icons.SEARCH.draw(g, 624, 289.5, 19, Theme.TEXT);
            if (toolSearch.getText().isEmpty()) {
                Ui.text(g, "Search songs...", 650, 290, Ui.font(false, 14.5), Theme.MUTED);
            }
        }
    }

    private class CollectionPart extends Decor {
        CollectionPart() { super(283, 333, 866, 550); }

        @Override void paintGlobal(Graphics2D g) {
            card(g, 283, 333, 866, 550, 18);
            Icons.NOTE.draw(g, 316, 360, 30, Theme.RED_LIGHT);
            Ui.text(g, "My Music Collection", 351, 358, Ui.font(true, 18.5), Theme.TEXT);
            Ui.text(g, "Total Songs: " + organizer.getNumberOfTracks(), 351, 383, Ui.font(false, 14), Theme.MUTED);
        }
    }

    private class NowPart extends Decor {
        NowPart() { super(1167, 107, 351, 291); }

        @Override void paintGlobal(Graphics2D g) {
            card(g, 1167, 107, 351, 291, 18);
            Icons.EQUALIZER.draw(g, 1196, 131, 24, Theme.RED_LIGHT);
            Ui.text(g, "Now Playing", 1222, 131, Ui.font(true, 18), Theme.TEXT);
            Icons.MORE.draw(g, 1493, 131, 24, Theme.TEXT);
            Track t = player.getCurrentTrack();
            double cx = 1342.5;
            if (t != null) {
                Art.cover(g, Art.styleFor(t), cx - 64, 150, 128, 108, 10);
                Ui.textC(g, fit(g, t.getTitle(), Ui.font(true, 20), 320), cx, 280, Ui.font(true, 20), Theme.TEXT);
                Ui.textC(g, fit(g, t.getArtist(), Ui.font(false, 15), 320), cx, 302, Ui.font(false, 15), Theme.MUTED);
                Ui.text(g, Ui.mmss(elapsed), 1185, 341, Ui.font(false, 14), Theme.MUTED);
                Ui.textR(g, t.getDuration(), 1501, 341, Ui.font(false, 14), Theme.MUTED);
            } else {
                Ui.roundRect(g, cx - 64, 150, 128, 108, 10, Theme.ROW, Theme.BORDER, 1.2);
                Icons.NOTE.draw(g, cx, 204, 44, Theme.DIM);
                Ui.textC(g, "Nothing playing", cx, 280, Ui.font(true, 20), Theme.TEXT);
                Ui.textC(g, "Press play or pick a song", cx, 302, Ui.font(false, 15), Theme.MUTED);
                Ui.text(g, "0:00", 1185, 341, Ui.font(false, 14), Theme.MUTED);
                Ui.textR(g, "0:00", 1501, 341, Ui.font(false, 14), Theme.MUTED);
            }
        }
    }

    private class StatsPart extends Decor {
        StatsPart() { super(1167, 410, 351, 134); }

        @Override void paintGlobal(Graphics2D g) {
            g.translate(0, -182);                       // card sits right under Now Playing
            card(g, 1167, 592, 351, 134, 18);
            Icons.BARS.draw(g, 1196, 613, 22, Theme.TEXT);
            Ui.text(g, "Collection Statistics", 1222, 613, Ui.font(true, 18), Theme.TEXT);
            g.setColor(Theme.BORDER);
            g.fill(new Rectangle2D.Double(1342.5, 630, 1.4, 86));
            g.fill(new Rectangle2D.Double(1185, 671, 317, 1.4));
            stat(g, Icons.NOTE, 1198, 650, "Total Songs", String.valueOf(organizer.getNumberOfTracks()), 1226);
            stat(g, Icons.PERSON, 1370, 650, "Unique Artists", String.valueOf(organizer.getUniqueArtistCount()), 1410);
            stat(g, Icons.CLOCK, 1198, 699, "Total Duration", organizer.getTotalDuration(), 1226);
            stat(g, Icons.TAG, 1370, 699, "Genres", String.valueOf(organizer.getUniqueGenreCount()), 1410);
        }

        private void stat(Graphics2D g, Glyph icon, double ix, double iy, String label, String value, double tx) {
            icon.draw(g, ix, iy, 26, Theme.TEXT);
            Ui.text(g, label, tx, iy - 9, Ui.font(false, 13.5), Theme.MUTED);
            Ui.text(g, value, tx, iy + 11, Ui.font(true, 17), Theme.TEXT);
        }
    }

    /** Pie chart + legend, calculated from the real song list. */
    private class GenrePart extends Decor {
        GenrePart() { super(1167, 556, 351, 341); }

        @Override void paintGlobal(Graphics2D g) {
            g.translate(0, -182);
            card(g, 1167, 738, 351, 341, 18);           // fills the space down to the bottom player
            Icons.EQUALIZER.draw(g, 1196, 759, 22, Theme.RED_LIGHT);
            Ui.text(g, "Genre Distribution", 1222, 759, Ui.font(true, 17), Theme.TEXT);
            g.translate(0, 91);                          // centre the pie + legend in the taller card

            List<Track> all = organizer.getTracks();
            int[] counts = new int[PIE_GENRES.length];
            int other = 0;
            for (Track t : all) {
                int idx = -1;
                for (int i = 0; i < PIE_GENRES.length; i++) if (PIE_GENRES[i].equalsIgnoreCase(t.getGenre())) idx = i;
                if (idx >= 0) counts[idx]++; else other++;
            }
            int total = all.size();

            // pie
            double cx = 1232, cy = 829, r = 44;
            if (total == 0) {
                g.setColor(Theme.BORDER);
                g.setStroke(new BasicStroke(10));
                g.draw(new Ellipse2D.Double(cx - r + 5, cy - r + 5, 2 * r - 10, 2 * r - 10));
            } else {
                double start = 90;
                for (int i = 0; i <= PIE_GENRES.length; i++) {
                    int n = i < PIE_GENRES.length ? counts[i] : other;
                    if (n == 0) continue;
                    double extent = -360.0 * n / total;
                    g.setColor(i < PIE_GENRES.length ? PIE_COLORS[i] : OTHER_COLOR);
                    g.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, Arc2D.PIE));
                    start += extent;
                }
                // thin separators between slices
                g.setColor(Theme.PANEL);
                g.setStroke(new BasicStroke(1.6f));
                start = 90;
                for (int i = 0; i <= PIE_GENRES.length; i++) {
                    int n = i < PIE_GENRES.length ? counts[i] : other;
                    if (n == 0) continue;
                    double a = Math.toRadians(start);
                    g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * r, cy - Math.sin(a) * r));
                    start -= 360.0 * n / total;
                }
                g.setColor(new Color(0xC3C8D0));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }

            // legend: the four standard genres, plus "Other" only when such songs exist
            int rows = other > 0 ? 5 : 4;
            double pitch = rows == 5 ? 22 : 26;
            double y = 829 - (rows - 1) * pitch / 2.0;
            Font lf = Ui.font(false, 14.5);
            for (int i = 0; i < rows; i++) {
                boolean isOther = i == 4;
                g.setColor(isOther ? OTHER_COLOR : PIE_COLORS[i]);
                g.fill(new Ellipse2D.Double(1306, y - 5, 10, 10));
                Ui.text(g, isOther ? "Other" : PIE_GENRES[i], 1326, y, lf, Theme.TEXT);
                Ui.textR(g, String.valueOf(isOther ? other : counts[i]), 1498, y, Ui.font(true, 14.5), Theme.TEXT);
                y += pitch;
            }
        }
    }

    private class BottomPart extends Decor {
        BottomPart() { super(14, 910, 1508, 100); }

        @Override void paintGlobal(Graphics2D g) {
            card(g, 14, 910, 1508, 100, 20);
            Track t = player.getCurrentTrack();
            if (t != null) {
                Art.cover(g, Art.styleFor(t), 30, 924, 76, 72, 9);
                Ui.text(g, fit(g, t.getTitle(), Ui.font(true, 18), 400), 124, 946, Ui.font(true, 18), Theme.TEXT);
                Ui.text(g, fit(g, t.getArtist(), Ui.font(false, 15), 400), 124, 973, Ui.font(false, 15), Theme.MUTED);
            } else {
                Ui.roundRect(g, 30, 924, 76, 72, 9, Theme.ROW, Theme.BORDER, 1.2);
                Icons.NOTE.draw(g, 68, 960, 30, Theme.DIM);
                Ui.text(g, "Nothing playing", 124, 946, Ui.font(true, 18), Theme.TEXT);
                Ui.text(g, "Select a song to begin", 124, 973, Ui.font(false, 15), Theme.MUTED);
            }
        }
    }

    /** Cuts text with "..." so it never runs into the next column. */
    private static String fit(Graphics2D g, String s, Font font, double maxWidth) {
        if (Ui.textW(g, s, font) <= maxWidth) return s;
        String cut = s;
        while (cut.length() > 1 && Ui.textW(g, cut + "...", font) > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut.trim() + "...";
    }

    // =====================================================================
    // Controls (buttons, dropdowns, progress, volume, table)
    // =====================================================================
    private void buildControls() {
        // table first so the buttons above it stay on top
        put(new TrackList(296, 403, 841, 466));

        // window controls + header icons
        put(new Button(1391, 2, 36, 28, Button.Look.GHOST, Icons.MINIMIZE, null, () -> {
            Window w = window();
            if (w instanceof Frame) ((Frame) w).setState(Frame.ICONIFIED);
        }).icon(18, 18));
        put(new Button(1443, 2, 36, 28, Button.Look.GHOST, Icons.MAXIMIZE, null, this::toggleMaximize).icon(18, 18));
        put(new Button(1493, 2, 36, 28, Button.Look.GHOST, Icons.CLOSE, null, () -> {
            Window w = window();
            if (w != null) w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING));
        }).icon(18, 18));
        put(new Button(1328, 36, 44, 44, Button.Look.GHOST, Icons.BELL, null, this::showNotifications).icon(22, 26));
        put(new Button(1386, 36, 44, 44, Button.Look.GHOST, Icons.GEAR, null, this::settings).icon(22, 26));
        put(new AvatarPart());

        // sidebar navigation
        String[] names = {"Home", "Add Music", "Remove Music", "Playlists", "Search Music", "Statistics", "Settings"};
        Glyph[] icons = {Icons.HOME, Icons.PLUS, Icons.TRASH, Icons.LIST, Icons.SEARCH, Icons.BARS, Icons.GEAR};
        for (int i = 0; i < nav.length; i++) {
            final int index = i;
            int y = (int) Math.round(145 + 58.6 * i - 25.5);
            nav[i] = new Button(19, y, 228, 51, Button.Look.NAV, icons[i], names[i], () -> navigate(index));
            nav[i].icon(31, 27).text(74, 17, false).radius(11);
            put(nav[i]);
        }
        nav[0].active = true;

        // collection toolbar
        put(new Button(296, 269, 122, 41, Button.Look.RED, Icons.PLUS, "Add Song", this::addSong).icon(22, 18).text(41, 15, true).radius(9));
        put(new Button(427, 269, 165, 41, Button.Look.GRAY, Icons.TRASH, "Remove Selected", this::removeChecked).icon(24, 19).text(46, 15, false).radius(9));
        genreBox = put(new Dropdown(842, 269, 124, 41, genreOptions(), i -> {
            genreFilter = genreBox.options[i];
            refreshList();
        }));
        sortBox = put(new Dropdown(977, 269, 116, 41, SORTS, i -> {
            sortMode = i;
            refreshList();
        }));
        sortBox.prefix = "Sort by ";
        sortBox.placeholder = "Sort by Title";
        sortBox.select(-1);
        put(new Button(1103, 269, 41, 41, Button.Look.GRAY, Icons.REFRESH, null, this::refresh).icon(20.5, 20).radius(9));

        // now playing
        put(new ProgressPart());
        put(new Button(1181, 347, 44, 44, Button.Look.GHOST, Icons.SHUFFLE, null, this::toggleShuffle)
                .icon(22, 24).dynamicColor(() -> player.isShuffle() ? Theme.RED_LIGHT : Theme.TEXT));
        put(new Button(1250, 347, 44, 44, Button.Look.GHOST, Icons.PREV, null, this::previous).icon(22, 22));
        put(playButton(1318, 344, 50));
        put(new Button(1393, 347, 44, 44, Button.Look.GHOST, Icons.NEXT, null, this::next).icon(22, 22));
        put(new Button(1460, 347, 44, 44, Button.Look.GHOST, Icons.REPEAT, null, this::toggleRepeat)
                .icon(22, 24).dynamicColor(() -> player.isRepeat() ? Theme.RED_LIGHT : Theme.TEXT));

        // bottom player
        put(new Button(592, 938, 44, 44, Button.Look.GHOST, Icons.SHUFFLE, null, this::toggleShuffle)
                .icon(22, 26).dynamicColor(() -> player.isShuffle() ? Theme.RED_LIGHT : Theme.TEXT));
        put(new Button(663, 938, 44, 44, Button.Look.GHOST, Icons.PREV, null, this::previous).icon(22, 25));
        put(playButton(734, 927, 66));
        put(new Button(828, 938, 44, 44, Button.Look.GHOST, Icons.NEXT, null, this::next).icon(22, 25));
        put(new Button(899, 938, 44, 44, Button.Look.GHOST, Icons.REPEAT, null, this::toggleRepeat)
                .icon(22, 26).dynamicColor(() -> player.isRepeat() ? Theme.RED_LIGHT : Theme.TEXT));
        put(new Button(1192, 938, 44, 44, Button.Look.GHOST, Icons.VOLUME, null, () -> {
            volume = volume > 0 ? 0 : 0.65;
            player.setVolume(volume);
            repaint();
        }).icon(22, 26));
        put(new VolumePart());
        put(new Button(1462, 938, 52, 44, Button.Look.GHOST, Icons.LIST, null, this::showQueue).icon(26, 26));
        put(new ToastPart());                  // last = on top of everything
    }

    /** Records an event: red badge on the bell, an entry in the bell list and a short pop-up message. */
    private void notifyEvent(String title, String message) {
        notifications.add(0, java.time.LocalTime.now().withNano(0) + "  -  " + title + ": " + message);
        while (notifications.size() > 20) notifications.remove(notifications.size() - 1);
        unread++;
        toastTitle = title;
        toastMsg = message;
        toastUntil = System.currentTimeMillis() + 3500;
        repaint();
    }

    private void showNotifications() {
        if (notifications.isEmpty()) {
            Dialogs.message(window(), f, "Notifications", "You have no new notifications.");
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String n : notifications) b.append(n).append('\n');
        unread = 0;
        repaint();
        Dialogs.message(window(), f, "Notifications", b.toString().trim());
    }

    /** The small message that slides in under the header for a few seconds. It never blocks the mouse. */
    private class ToastPart extends Part {
        ToastPart() { super(1148, 100, 370, 54); }
        @Override public boolean contains(int x, int y) { return false; }
        @Override void paintPart(Graphics2D g, int w, int h) {
            if (toastTitle == null) return;
            long left = toastUntil - System.currentTimeMillis();
            if (left <= 0) return;
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1.0, left / 500.0)));
            Ui.roundRect(g, 0, 0, w, h, 12, new Color(0x161C26), Theme.RED, 1.4);
            Icons.BELL.draw(g, 28, h / 2.0, 22, Theme.RED_LIGHT);
            Ui.text(g, toastTitle, 52, 18, Ui.font(true, 14), Theme.TEXT);
            Font mf = Ui.font(false, 13);
            Ui.text(g, fit(g, toastMsg, mf, 300), 52, 37, mf, Theme.MUTED);
        }
    }

    private String[] genreOptions() {
        String[] out = new String[Dialogs.GENRES.length + 1];
        out[0] = ALL;
        System.arraycopy(Dialogs.GENRES, 0, out, 1, Dialogs.GENRES.length);
        return out;
    }

    private Button playButton(int x, int y, int size) {
        Button b = new Button(x, y, size, size, Button.Look.CIRCLE, Icons.PAUSE, null, this::togglePlay);
        b.icon(size / 2.0, size * 0.42).dynamicGlyph(() -> player.isPlaying() ? Icons.PAUSE : Icons.PLAY);
        return b;
    }

    /** Avatar with the small chevron beside it (decoration only for now). */
    private class AvatarPart extends Part {
        AvatarPart() {
            super(1448, 38, 74, 40);          // decoration only for now: no click, no hover, no menu
        }
        @Override public boolean contains(int x, int y) { return false; }
        @Override void paintPart(Graphics2D g, int w, int h) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x4A5470), 36, 36, new Color(0x2F3750)));
            g.fillOval(8, 2, 36, 36);
            g.setColor(new Color(0x7B84A6));
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(8, 2, 36, 36);
            Ui.textC(g, "A", 26, 20, Ui.font(true, 18), Color.WHITE);
            Icons.CHEVRON.draw(g, 63, 20, 13, Theme.TEXT);
        }
    }

    private class ProgressPart extends Part {
        ProgressPart() {
            super(1183, 314, 319, 16);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { seek(e); }
                @Override public void mouseDragged(MouseEvent e) { seek(e); }
                private void seek(MouseEvent e) {
                    Track t = player.getCurrentTrack();
                    if (t == null) return;
                    double frac = Math.max(0, Math.min(1, toDesign(e).getX() / design.width));
                    elapsed = (int) Math.round(frac * t.getDurationSeconds());
                    player.seekSeconds(elapsed);
                    MusicPanel.this.repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override void paintPart(Graphics2D g, int w, int h) {
            Track t = player.getCurrentTrack();
            double frac = t == null || t.getDurationSeconds() == 0 ? 0 : Math.min(1, elapsed / (double) t.getDurationSeconds());
            Ui.roundRect(g, 0, 5, w, 6, 3, new Color(0x2B3039), null, 0);
            if (frac > 0) Ui.roundRect(g, 0, 5, Math.max(6, w * frac), 6, 3, Theme.RED, null, 0);
        }
    }

    private class VolumePart extends Part {
        VolumePart() {
            super(1244, 948, 212, 24);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { set(e); }
                @Override public void mouseDragged(MouseEvent e) { set(e); }
                private void set(MouseEvent e) {
                    volume = Math.max(0, Math.min(1, (toDesign(e).getX() - 9) / (design.width - 18)));
                    player.setVolume(volume);
                    MusicPanel.this.repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override void paintPart(Graphics2D g, int w, int h) {
            double x = 9 + volume * (w - 18);
            Ui.roundRect(g, 0, h / 2.0 - 3, w, 6, 3, new Color(0x2B3039), null, 0);
            Ui.roundRect(g, 0, h / 2.0 - 3, x, 6, 3, Theme.RED, null, 0);
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new Ellipse2D.Double(x - 9, h / 2.0 - 8, 18, 18));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(x - 9, h / 2.0 - 9, 18, 18));
        }
    }

    /** A drop-down (genre filter / sort order) that opens a dark popup menu. */
    private class Dropdown extends Part {
        final String[] options;
        int selected;
        String prefix = "";
        String placeholder;                 // shown while nothing is selected (selected == -1)
        private final IntConsumer onChange;
        private boolean hover;

        Dropdown(int x, int y, int w, int h, String[] options, IntConsumer onChange) {
            super(x, y, w, h);
            this.options = options;
            this.onChange = onChange;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { if (contains(e.getPoint())) open(); }
            });
        }

        void select(int i) { selected = i; repaint(); }

        private void open() {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(Theme.PANEL);
            menu.setBorder(new LineBorder(Theme.RED_DARK, 1));
            for (int i = 0; i < options.length; i++) {
                final int idx = i;
                JMenuItem item = new JMenuItem(prefix + options[i]);
                item.setFont(Ui.font(i == selected, 14.5 * f));
                item.setForeground(i == selected ? Theme.RED_LIGHT : Theme.TEXT);
                item.setBackground(Theme.PANEL);
                item.setBorder(new EmptyBorder(7, 16, 7, 28));
                item.addActionListener(e -> { selected = idx; repaint(); onChange.accept(idx); });
                menu.add(item);
            }
            menu.show(this, 0, getHeight() + 2);
        }

        @Override void paintPart(Graphics2D g, int w, int h) {
            Ui.roundRect(g, 0, 0, w, h, 9, hover ? new Color(0x171D26) : new Color(0x10151C),
                    hover ? Ui.alpha(Theme.RED, 150) : new Color(0x242B36), 1.2);
            Ui.text(g, selected < 0 ? placeholder : prefix + options[selected], 15, h / 2.0, Ui.font(false, 14.5), Theme.TEXT);
            Icons.CHEVRON.draw(g, w - 17, h / 2.0, 12, Theme.MUTED);
        }
    }

    // =====================================================================
    // The song table: checkbox, #, cover, title, artist, duration, genre, added on, actions
    // =====================================================================
    private class TrackList extends Part {
        private static final int HEADER_H = 51, ROW_H = 69;
        // column positions, relative to the table's left edge
        private static final int X_CHECK = 26, X_NUM = 70, X_COVER = 95, X_TITLE = 162, X_ARTIST = 316,
                X_DUR = 449, X_GENRE = 506, X_ADDED = 650, X_PLAY = 746, X_EDIT = 782, X_DEL = 817;
        private int hoverRow = -1, hoverCol;
        private double scroll;

        TrackList(int x, int y, int w, int h) {
            super(x, y, w, h);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int[] h = hit(toDesign(e));
                    if (h[0] != hoverRow || h[1] != hoverCol) { hoverRow = h[0]; hoverCol = h[1]; repaint(); }
                    setCursor(Cursor.getPredefinedCursor(h[1] != 0 || h[0] >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
                @Override public void mouseExited(MouseEvent e) { hoverRow = -1; hoverCol = 0; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    int[] h = hit(toDesign(e));
                    if (h[1] == 5) { toggleAll(); return; }
                    if (h[0] < 0) return;
                    Track t = shown.get(h[0]);
                    switch (h[1]) {
                        case 1: toggleChecked(t); break;
                        case 2: playTrack(t); break;
                        case 3: editSong(t); break;
                        case 4: removeOne(t); break;
                        default: if (e.getClickCount() >= 2) playTrack(t); break;
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(e -> {
                scroll += e.getPreciseWheelRotation() * 40;
                clampScroll();
                repaint();
            });
        }

        private void toggleChecked(Track t) {
            if (!checked.remove(t)) checked.add(t);
            MusicPanel.this.repaint();
        }

        /** Header checkbox: select every visible song, or clear the selection when all are already ticked. */
        private void toggleAll() {
            if (shown.isEmpty()) return;
            if (checked.containsAll(shown)) checked.removeAll(shown); else checked.addAll(shown);
            MusicPanel.this.repaint();
        }

        private double contentHeight() { return shown.size() * (double) ROW_H; }

        private void clampScroll() {
            double max = Math.max(0, contentHeight() - (design.height - HEADER_H));
            scroll = Math.max(0, Math.min(max, scroll));
        }

        /** Returns {row, column}; column 1 check, 2 play, 3 edit, 4 delete, 5 select-all, 0 anything else. */
        private int[] hit(Point2D p) {
            double x = p.getX(), y = p.getY();
            if (y < HEADER_H) return new int[]{-1, Math.abs(x - X_CHECK) <= 16 && y > 10 && y < 42 ? 5 : 0};
            int r = (int) Math.floor((y - HEADER_H + scroll) / ROW_H);
            if (r < 0 || r >= shown.size()) return new int[]{-1, 0};
            int col = 0;
            if (Math.abs(x - X_CHECK) <= 16) col = 1;
            else if (Math.abs(x - X_PLAY) <= 15) col = 2;
            else if (Math.abs(x - X_EDIT) <= 14) col = 3;
            else if (Math.abs(x - X_DEL) <= 14) col = 4;
            return new int[]{r, col};
        }

        @Override
        void paintPart(Graphics2D g, int w, int h) {
            clampScroll();
            Shape outline = new RoundRectangle2D.Double(0, 0, w, h, 16, 16);
            Graphics2D c = (Graphics2D) g.create();
            c.clip(outline);
            c.setColor(new Color(0x0C1016));
            c.fillRect(0, 0, w, h);

            Graphics2D rows = (Graphics2D) c.create();
            rows.clip(new Rectangle(0, HEADER_H, w, h - HEADER_H));
            for (int i = 0; i < shown.size(); i++) {
                double y = HEADER_H + i * ROW_H - scroll;
                if (y > h || y + ROW_H < HEADER_H) continue;
                paintRow(rows, shown.get(i), i, y, w);
            }
            if (shown.isEmpty()) {
                Ui.textC(rows, "No songs to show", w / 2.0, 190, Ui.font(true, 20), Theme.DIM);
                Ui.textC(rows, "Use Add Song, or press Refresh to clear the filters", w / 2.0, 224, Ui.font(false, 15), Theme.DIM);
            }
            rows.dispose();

            // header row
            c.setColor(new Color(0x121720));
            c.fillRect(0, 0, w, HEADER_H);
            c.setColor(Theme.BORDER);
            c.fillRect(0, HEADER_H - 1, w, 1);
            boolean all = !shown.isEmpty() && checked.containsAll(shown);
            boolean some = !all && !shown.isEmpty() && anyChecked();
            checkbox(c, X_CHECK, HEADER_H / 2.0, all, some, hoverCol == 5);
            Font hf = Ui.font(true, 14.5);
            Ui.textC(c, "#", X_NUM, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.text(c, "Title", 118, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.text(c, "Artist", X_ARTIST, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.text(c, "Duration", 424, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.text(c, "Genre", X_GENRE, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.text(c, "Added On", 614, HEADER_H / 2.0, hf, Theme.MUTED);
            Ui.textC(c, "Actions", 782, HEADER_H / 2.0, hf, Theme.MUTED);

            // slim scrollbar when there are more songs than fit
            double visible = h - HEADER_H;
            if (contentHeight() > visible) {
                double barH = Math.max(40, visible * visible / contentHeight());
                double barY = HEADER_H + (visible - barH) * (scroll / (contentHeight() - visible));
                Ui.roundRect(c, w - 6, barY, 4, barH, 2, Theme.RED, null, 0);
            }
            c.dispose();
            g.setColor(new Color(0x232A35));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Double(0.7, 0.7, w - 1.4, h - 1.4, 16, 16));
        }

        private boolean anyChecked() {
            for (Track t : shown) if (checked.contains(t)) return true;
            return false;
        }

        private void checkbox(Graphics2D g, double cx, double cy, boolean on, boolean partial, boolean hover) {
            double bx = cx - 10, by = cy - 10;
            if (on || partial) {
                Ui.roundRect(g, bx, by, 20, 20, 5, Theme.RED, null, 0);
                if (on) Icons.CHECK.draw(g, cx, cy, 15, Color.WHITE);
                else {
                    g.setColor(Color.WHITE);
                    g.fill(new RoundRectangle2D.Double(cx - 5, cy - 1.2, 10, 2.4, 2, 2));
                }
            } else {
                Ui.roundRect(g, bx, by, 20, 20, 5, new Color(0x0E1218), hover ? Theme.RED_LIGHT : new Color(0xA9AFB9), 1.6);
            }
        }

        private void paintRow(Graphics2D g, Track t, int index, double y, int w) {
            boolean isChecked = checked.contains(t);
            boolean hover = index == hoverRow;
            boolean current = t == player.getCurrentTrack();
            Color bg = isChecked ? Theme.ROW_CHECK : hover ? Theme.ROW_HOVER : index % 2 == 0 ? new Color(0x0E1218) : new Color(0x121720);
            g.setColor(bg);
            g.fill(new Rectangle2D.Double(0, y, w, ROW_H));
            g.setColor(new Color(0x1A2029));
            g.fill(new Rectangle2D.Double(0, y + ROW_H - 1, w, 1));
            double cy = y + ROW_H / 2.0 - 0.5;

            checkbox(g, X_CHECK, cy, isChecked, false, hover && hoverCol == 1);
            Ui.textC(g, String.valueOf(index + 1), X_NUM, cy, Ui.font(false, 14.5), current ? Theme.RED_LIGHT : Theme.MUTED);
            Art.cover(g, Art.styleFor(t), X_COVER, cy - 25, 50, 50, 7);
            Ui.text(g, fit(g, t.getTitle(), Ui.font(true, 14.5), 140), X_TITLE, cy, Ui.font(true, 14.5), current ? Theme.RED_LIGHT : Theme.TEXT);
            Ui.text(g, fit(g, t.getArtist(), Ui.font(false, 14), 112), X_ARTIST, cy, Ui.font(false, 14), Theme.MUTED);
            Ui.textC(g, t.getDuration(), X_DUR, cy, Ui.font(false, 14), Theme.TEXT);

            // genre pill (colour depends on the genre)
            Font gf = Ui.font(false, 13);
            double pw = Math.max(52, Ui.textW(g, t.getGenre(), gf) + 24);
            Color[] pc = pillColors(t.getGenre());
            Ui.roundRect(g, X_GENRE, cy - 12, pw, 24, 12, pc[0], null, 0);
            Ui.textC(g, t.getGenre(), X_GENRE + pw / 2, cy, gf, pc[1]);

            Ui.textC(g, t.getAddedOn().toString(), X_ADDED, cy, Ui.font(false, 14), Theme.TEXT);

            // actions: Play (red circle), Edit (pencil), Delete (bin)
            boolean hp = hover && hoverCol == 2, he = hover && hoverCol == 3, hd = hover && hoverCol == 4;
            g.setPaint(hp ? Ui.redGradient(X_PLAY - 14, cy - 14, 28, 36) : Ui.redGradient(X_PLAY - 14, cy - 14, 28, 28));
            g.fill(new Ellipse2D.Double(X_PLAY - 14, cy - 14, 28, 28));
            Icons.PLAY.draw(g, X_PLAY + 0.8, cy, 14, Color.WHITE);
            Icons.EDIT.draw(g, X_EDIT, cy, 19, he ? Theme.RED_LIGHT : Theme.TEXT);
            Icons.TRASH.draw(g, X_DEL, cy, 19, hd ? Color.WHITE : new Color(0xE5324A));
        }
    }

    private static Color[] pillColors(String genre) {
        switch (genre.toLowerCase()) {
            case "pop":     return new Color[]{new Color(0x6E1022), new Color(0xFFC2CB)};
            case "rock":    return new Color[]{new Color(0x262C37), new Color(0xD3D8E0)};
            case "hip hop": return new Color[]{new Color(0x5A3A12), new Color(0xFFCB85)};
            case "r&b":     return new Color[]{new Color(0x1F3552), new Color(0xB3D0FF)};
            default:        return new Color[]{new Color(0x2D2F36), new Color(0xD0D3D9)};
        }
    }

    // =====================================================================
    // Search fields (header + toolbar share one query)
    // =====================================================================
    private void buildSearchFields() {
        styleField(headerSearch, "Search by title, artist or genre");
        styleField(toolSearch, "Filter the songs below");
        bind(headerSearch, toolSearch);
        bind(toolSearch, headerSearch);
        add(headerSearch, 0);
        add(toolSearch, 0);
    }

    private void styleField(JTextField tf, String tip) {
        tf.setOpaque(false);
        tf.setBorder(new EmptyBorder(0, 0, 0, 0));
        tf.setForeground(Theme.TEXT);
        tf.setCaretColor(Theme.TEXT);
        tf.setToolTipText(tip);
    }

    private void bind(JTextField from, JTextField to) {
        from.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!syncing) {
                    syncing = true;
                    to.setText(from.getText());
                    syncing = false;
                    refreshList();
                }
                repaint();
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
    }

    // =====================================================================
    // Behaviour
    // =====================================================================
    private void toggleMaximize() {
        Window w = window();
        if (w instanceof Frame) {
            Frame fr = (Frame) w;
            fr.setExtendedState((fr.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0 ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
        }
    }

    private void navigate(int index) {
        switch (index) {
            case 0:                                   // Home: show the whole collection again
                active = 0;
                resetView();
                break;
            case 1: addSong(); break;
            case 2: removeChecked(); break;
            case 3: showPlaylists(); break;
            case 4:
                active = 4;
                headerSearch.requestFocusInWindow();
                break;
            case 5: showStatistics(); break;
            default: settings(); break;
        }
        for (int i = 0; i < nav.length; i++) nav[i].setActive(i == active);
    }

    private JDialog labDialog;

    /** Settings: collection info plus the "Exercise Lab" that runs the assignment methods and shows their console output. */
    private void settings() {
        if (labDialog != null && labDialog.isShowing()) { labDialog.toFront(); return; }
        labDialog = Dialogs.lab(window(), f, organizer, this::afterRemoval);
    }

    private void showStatistics() {
        StringBuilder b = new StringBuilder();
        b.append("Total songs: ").append(organizer.getNumberOfTracks()).append('\n');
        b.append("Unique artists: ").append(organizer.getUniqueArtistCount()).append('\n');
        b.append("Genres: ").append(organizer.getUniqueGenreCount()).append('\n');
        b.append("Total duration: ").append(organizer.getTotalDuration()).append('\n');
        Track top = null;
        for (Track t : organizer.getTracks()) if (top == null || t.getPlayCount() > top.getPlayCount()) top = t;
        if (top != null && top.getPlayCount() > 0) {
            b.append("Most played: ").append(top.getTitle()).append(" (").append(top.getPlayCount()).append("x)\n");
        }
        b.append('\n');
        Map<String, Integer> perGenre = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : organizer.getTracks()) perGenre.merge(t.getGenre(), 1, Integer::sum);
        for (Map.Entry<String, Integer> e : perGenre.entrySet()) b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        Dialogs.message(window(), f, "Statistics", b.toString().trim());
    }

    /** Playlists are built from the genres that exist in the collection. */
    private void showPlaylists() {
        List<Track> all = organizer.getTracks();
        StringBuilder b = new StringBuilder("All Songs (" + all.size() + ")\n");
        Map<String, List<Track>> byGenre = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : all) byGenre.computeIfAbsent(t.getGenre(), k -> new ArrayList<>()).add(t);
        for (Map.Entry<String, List<Track>> e : byGenre.entrySet()) {
            b.append('\n').append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
            for (Track t : e.getValue()) b.append("   ").append(t.getTitle()).append('\n');
        }
        if (all.isEmpty()) b.append("\nAdd songs to build playlists.");
        Dialogs.message(window(), f, "Playlists", b.toString().trim());
    }

    /** Clears search, genre filter and selection (Home, View All Music). */
    private void resetView() {
        syncing = true;
        headerSearch.setText("");
        toolSearch.setText("");
        syncing = false;
        genreFilter = ALL;
        genreBox.select(0);
        checked.clear();
        refreshList();
    }

    /** Refresh button: clears filters, selection and sort order, then reloads the table from the collection. */
    private void refresh() {
        sortMode = -1;
        sortBox.select(-1);
        resetView();
    }

    private void refreshList() {
        List<Track> list = new ArrayList<>(organizer.search(headerSearch.getText(), genreFilter));
        if (sortMode >= 0) {
            Comparator<Track> cmp;
            switch (sortMode) {
                case 1: cmp = Comparator.comparing(t -> t.getArtist().toLowerCase(Locale.ROOT)); break;
                case 2: cmp = Comparator.comparingInt(Track::getDurationSeconds); break;
                case 3: cmp = Comparator.comparing(t -> t.getGenre().toLowerCase(Locale.ROOT)); break;
                case 4: cmp = Comparator.comparing(Track::getAddedOn); break;
                default: cmp = Comparator.comparing(t -> t.getTitle().toLowerCase(Locale.ROOT)); break;
            }
            list.sort(sortMode == 0 ? cmp : cmp.thenComparing(t -> t.getTitle().toLowerCase(Locale.ROOT)));
        }
        shown = list;
        checked.retainAll(organizer.getTracks());
        repaint();
    }

    private void playTrack(Track t) {
        organizer.playTrack(t);
        elapsed = 0;
        lastCurrent = t;
        repaint();
    }

    private void togglePlay() {
        if (player.getCurrentTrack() == null) {
            Track first = shown.isEmpty() ? organizer.getTrack(0) : shown.get(0);
            if (first == null) {
                Dialogs.message(window(), f, "Empty collection", "Add a song first.");
                return;
            }
            playTrack(first);
        } else {
            player.togglePlayPause();
        }
        repaint();
    }

    private void toggleShuffle() { player.toggleShuffle(); repaint(); }

    private void toggleRepeat() { player.toggleRepeat(); repaint(); }

    private void next() {
        if (organizer.playNext() != null) { elapsed = 0; lastCurrent = player.getCurrentTrack(); }
        repaint();
    }

    private void previous() {
        if (organizer.playPrevious() != null) { elapsed = 0; lastCurrent = player.getCurrentTrack(); }
        repaint();
    }

    private void tick() {
        if (toastTitle != null) {
            if (System.currentTimeMillis() > toastUntil) toastTitle = null;
            repaint();
        }
        Track cur = player.getCurrentTrack();
        if (cur != lastCurrent) { lastCurrent = cur; elapsed = 0; }
        if (cur != null && player.isPlaying()) {
            elapsed = player.getPositionSeconds();          // follows the real audio
            if (player.hasFinishedTrack()) {
                if (player.isRepeat()) { cur.incrementPlayCount(); player.play(cur); elapsed = 0; }
                else { organizer.playNext(); elapsed = 0; lastCurrent = player.getCurrentTrack(); }
            }
            repaint();
        }
    }

    /** Writes the current collection to disk so edits survive closing/reopening BlueJ. */
    private void saveLibrary() {
        try {
            MusicLibraryStorage.save(organizer.getTracks());
        } catch (IOException ex) {
            ex.printStackTrace();
            Dialogs.message(window(), f, "Could not save songs", "The music library could not be saved. Check that the project folder is writable.\n" + ex.getMessage());
        }
    }

    private void addSong() {
        String[] v = Dialogs.trackForm(window(), f, "Add New Song", null);
        if (v == null) return;
        try {
            if (v[4] != null && !v[4].trim().isEmpty()) {
                v[4] = copySongIntoProject(v[4]);
            }
            organizer.addTrack(new Track(v[0], v[1], v[2], v[3], v[4]));
            saveLibrary();
            refreshList();
            notifyEvent("Song added", "\"" + v[0] + "\" by " + v[1]);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(window(),
                "Could not copy the song into the project's music folder.\n" + ex.getMessage(),
                "Song Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Copies selected audio into this project's music folder so the original can be moved or deleted. */
    private String copySongIntoProject(String originalPath) throws IOException {
        java.nio.file.Path source = java.nio.file.Paths.get(originalPath.trim()).toAbsolutePath();
        if (!java.nio.file.Files.isRegularFile(source)) {
            throw new IOException("The selected music file does not exist:\n" + source);
        }
        java.nio.file.Path musicFolder = java.nio.file.Paths.get("music");
        java.nio.file.Files.createDirectories(musicFolder);
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot) : ".wav";
        String uniqueName = java.util.UUID.randomUUID().toString() + extension;
        java.nio.file.Path destination = musicFolder.resolve(uniqueName);
        java.nio.file.Files.copy(source, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return destination.toString();
    }

    private void editSong(Track t) {
        String[] v = Dialogs.trackForm(window(), f, "Edit Song", t);
        if (v == null) return;
        t.setTitle(v[0]); t.setArtist(v[1]); t.setDuration(v[2]); t.setGenre(v[3]);
        try {
            String newPath = v[4] == null ? "" : v[4].trim();
            boolean fileChanged = !newPath.equals(t.getFilePath());
            if (fileChanged && !newPath.isEmpty()) newPath = copySongIntoProject(newPath);
            t.setFilePath(newPath);
            if (fileChanged && t == player.getCurrentTrack()) { boolean was = player.isPlaying(); player.stop(); player.play(t); if (!was) player.pause(); elapsed = 0; }
            saveLibrary();
            refreshList();
            notifyEvent("Song updated", "\"" + t.getTitle() + "\" was saved");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(window(),
                "Could not copy the song into the project's music folder.\n" + ex.getMessage(),
                "Song Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeChecked() {
        if (checked.isEmpty()) {
            Dialogs.message(window(), f, "No songs selected", "Tick the checkbox of one or more songs first, then press Remove Selected.");
            return;
        }
        String q = "Remove " + checked.size() + " selected song" + (checked.size() == 1 ? "" : "s") + " from your collection?";
        if (!Dialogs.confirm(window(), f, "Remove songs", q, "Remove")) return;
        int count = checked.size();
        organizer.removeTracks(new ArrayList<>(checked));
        checked.clear();
        saveLibrary();
        afterRemoval();
        notifyEvent(count == 1 ? "Song deleted" : "Songs deleted", count + " song" + (count == 1 ? "" : "s") + " removed from your collection");
    }

    private void removeOne(Track t) {
        if (!Dialogs.confirm(window(), f, "Remove song", "Remove \"" + t.getTitle() + "\" from your collection?", "Remove")) return;
        organizer.removeTracks(Collections.singletonList(t));
        checked.remove(t);
        saveLibrary();
        afterRemoval();
        notifyEvent("Song deleted", "\"" + t.getTitle() + "\" was removed");
    }

    /** Keeps Now Playing and the bottom player valid when the current song was deleted. */
    private void afterRemoval() {
        Track cur = player.getCurrentTrack();
        if (cur != null && !organizer.getTracks().contains(cur)) {
            List<Track> all = organizer.getTracks();
            if (all.isEmpty()) player.clear(); else organizer.playTrack(all.get(0));
            elapsed = 0;
            lastCurrent = player.getCurrentTrack();
        }
        refreshList();
    }

    private void trackInfo() {
        Track t = player.getCurrentTrack();
        if (t != null) Dialogs.message(window(), f, "Track details", t.getDetails());
    }

    private void showQueue() {
        StringBuilder b = new StringBuilder();
        int i = 1;
        for (Track t : organizer.tracksUsingIterator()) b.append(i++).append(". ").append(t).append('\n');
        Dialogs.message(window(), f, "Playback queue", organizer.getNumberOfTracks() == 0 ? "The queue is empty." : b.toString().trim());
    }

    @Override
    public void removeNotify() {
        ticker.stop();
        super.removeNotify();
    }

    // =====================================================================
    // Helpers (static nested classes: colours, drawing, icons, cover art, buttons, dialogs)
    // =====================================================================

    /** Colours and font family. All sizes in the GUI are "design pixels" of the 1536 x 1024 mockup. */
    static final class Theme {
        private Theme() { }

        public static final Color BG        = new Color(0x090C11);
        public static final Color SIDEBAR   = new Color(0x0D1118);
        public static final Color PANEL     = new Color(0x0F131A);
        public static final Color BORDER    = new Color(0x1C222C);
        public static final Color ROW       = new Color(0x141920);
        public static final Color ROW_HOVER = new Color(0x1A2029);
        public static final Color ROW_CHECK = new Color(0x2A1219);
        public static final Color RED       = new Color(0xE50D22);
        public static final Color RED_LIGHT = new Color(0xFF2B3F);
        public static final Color RED_DARK  = new Color(0x9E0A19);
        public static final Color GRAY      = new Color(0x151A22);
        public static final Color GRAY_HOVER= new Color(0x202733);
        public static final Color TEXT      = new Color(0xF4F5F7);
        public static final Color MUTED     = new Color(0xC3C8D0);
        public static final Color DIM       = new Color(0x8A919C);

        /** Segoe UI on Windows; the first installed fallback elsewhere. */
        public static final String FAMILY = pickFamily();

        private static String pickFamily() {
            Set<String> have = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[]{"Segoe UI", "Inter", "Helvetica Neue", "Calibri", "Carlito", "Arial", "Liberation Sans"}) {
                if (have.contains(f)) return f;
            }
            return "SansSerif";
        }
    }

    /** Small drawing helpers shared by all components. */
    static final class Ui {
        private Ui() { }

        static Graphics2D g2(Graphics g) {
            Graphics2D x = (Graphics2D) g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            x.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            x.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            x.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            x.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            return x;
        }

        static Font font(boolean bold, double size) {
            return new Font(Theme.FAMILY, bold ? Font.BOLD : Font.PLAIN, 12).deriveFont((float) size);
        }

        /** Left aligned text, vertically centred on cy. */
        static void text(Graphics2D g, String s, double x, double cy, Font f, Color c) {
            g.setFont(f);
            g.setColor(c);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (float) x, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }

        static void textC(Graphics2D g, String s, double cx, double cy, Font f, Color c) {
            g.setFont(f);
            double w = g.getFontMetrics().stringWidth(s);
            text(g, s, cx - w / 2.0, cy, f, c);
        }

        static void textR(Graphics2D g, String s, double right, double cy, Font f, Color c) {
            g.setFont(f);
            text(g, s, right - g.getFontMetrics().stringWidth(s), cy, f, c);
        }

        /** Letter-spaced text centred on cx. */
        static void textSpaced(Graphics2D g, String s, double cx, double cy, Font f, Color c, double tracking) {
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            double total = -tracking;
            for (char ch : s.toCharArray()) total += fm.charWidth(ch) + tracking;
            double x = cx - total / 2.0;
            for (char ch : s.toCharArray()) {
                text(g, String.valueOf(ch), x, cy, f, c);
                x += fm.charWidth(ch) + tracking;
            }
        }

        static int textW(Graphics2D g, String s, Font f) {
            g.setFont(f);
            return g.getFontMetrics().stringWidth(s);
        }

        static void roundRect(Graphics2D g, double x, double y, double w, double h, double r, Paint fill, Color border, double bw) {
            Shape s = new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
            if (fill != null) { g.setPaint(fill); g.fill(s); }
            if (border != null) {
                g.setPaint(border);
                g.setStroke(new BasicStroke((float) bw));
                g.draw(new RoundRectangle2D.Double(x + bw / 2, y + bw / 2, w - bw, h - bw, r * 2, r * 2));
            }
        }

        static Paint redGradient(double x, double y, double w, double h) {
            return new GradientPaint((float) x, (float) y, Theme.RED_LIGHT, (float) (x + w), (float) (y + h), Theme.RED_DARK);
        }

        static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

        static String mmss(int seconds) {
            return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }
    }

    /** A vector icon drawn inside a 20 x 20 box centred on (cx, cy) and scaled to size s. */
    interface Glyph {
        void draw(Graphics2D g, double cx, double cy, double s, Color c);
    }

    /** All icons are vector shapes, so the GUI does not depend on any emoji or symbol font. */
    static final class Icons {
        private Icons() { }

        private static Graphics2D box(Graphics2D g, double cx, double cy, double s, Color c) {
            Graphics2D k = (Graphics2D) g.create();
            k.translate(cx, cy);
            k.scale(s / 20.0, s / 20.0);
            k.setColor(c);
            return k;
        }

        private static void line(Graphics2D k, double w) {
            k.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        private static Path2D poly(double... p) {
            Path2D.Double d = new Path2D.Double();
            d.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) d.lineTo(p[i], p[i + 1]);
            d.closePath();
            return d;
        }

        /** Beamed double music note. */
        static final Glyph NOTE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            AffineTransform keep = k.getTransform();
            k.translate(-5.5, 6.6); k.rotate(Math.toRadians(-20));
            k.fill(new Ellipse2D.Double(-3.6, -2.7, 7.2, 5.4));
            k.setTransform(keep);
            k.translate(4.1, 4.9); k.rotate(Math.toRadians(-20));
            k.fill(new Ellipse2D.Double(-3.6, -2.7, 7.2, 5.4));
            k.setTransform(keep);
            k.fill(new Rectangle2D.Double(-3.9, -7.2, 1.9, 13.4));
            k.fill(new Rectangle2D.Double(5.5, -9.4, 1.9, 13.4));
            k.fill(poly(-3.9, -7.2, 7.4, -9.9, 7.4, -5.6, -3.9, -2.9));
            k.dispose();
        };

        static final Glyph HOME = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(poly(0, -9.5, 10, -0.6, 7.3, -0.6, 7.3, 9, 2.6, 9, 2.6, 3, -2.6, 3, -2.6, 9, -7.3, 9, -7.3, -0.6, -10, -0.6));
            k.dispose();
        };

        static final Glyph PLUS = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.8);
            k.draw(new Line2D.Double(-8, 0, 8, 0));
            k.draw(new Line2D.Double(0, -8, 0, 8));
            k.dispose();
        };

        static final Glyph TRASH = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.1);
            k.draw(new Line2D.Double(-8.5, -5.2, 8.5, -5.2));
            k.draw(new RoundRectangle2D.Double(-3.2, -9, 6.4, 3.8, 1.5, 1.5));
            Path2D.Double body = new Path2D.Double();
            body.moveTo(-6.6, -3); body.lineTo(-5.6, 8.2); body.quadTo(-5.5, 9.2, -4.5, 9.2);
            body.lineTo(4.5, 9.2); body.quadTo(5.5, 9.2, 5.6, 8.2); body.lineTo(6.6, -3);
            k.draw(body);
            k.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            k.draw(new Line2D.Double(-2.2, -0.5, -2.2, 6.3));
            k.draw(new Line2D.Double(2.2, -0.5, 2.2, 6.3));
            k.dispose();
        };

        static final Glyph LIST = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.3);
            for (int i = -1; i <= 1; i++) {
                k.fill(new Ellipse2D.Double(-9.6, i * 6.2 - 1.5, 3, 3));
                k.draw(new Line2D.Double(-4.4, i * 6.2, 9, i * 6.2));
            }
            k.dispose();
        };

        static final Glyph GEAR = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            double rin = 7.0, rout = 9.8;
            boolean first = true;
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                double[] ang = {a - Math.toRadians(17), a - Math.toRadians(11), a + Math.toRadians(11), a + Math.toRadians(17)};
                double[] rad = {rin, rout, rout, rin};
                for (int j = 0; j < 4; j++) {
                    double x = Math.cos(ang[j]) * rad[j], y = Math.sin(ang[j]) * rad[j];
                    if (first) { p.moveTo(x, y); first = false; } else p.lineTo(x, y);
                }
            }
            p.closePath();
            p.append(new Ellipse2D.Double(-3.4, -3.4, 6.8, 6.8), false);
            k.fill(p);
            k.dispose();
        };

        static final Glyph SEARCH = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.3);
            k.draw(new Ellipse2D.Double(-8.6, -8.6, 13.6, 13.6));
            line(k, 2.8);
            k.draw(new Line2D.Double(3.2, 3.2, 8.8, 8.8));
            k.dispose();
        };

        static final Glyph BELL = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.0);
            Path2D.Double p = new Path2D.Double();
            p.moveTo(-8, 5.8);
            p.curveTo(-6, 4.2, -5.6, 2.5, -5.6, -1);
            p.curveTo(-5.6, -4.8, -3, -6.8, 0, -6.8);
            p.curveTo(3, -6.8, 5.6, -4.8, 5.6, -1);
            p.curveTo(5.6, 2.5, 6, 4.2, 8, 5.8);
            p.closePath();
            k.draw(p);
            k.draw(new Line2D.Double(0, -9, 0, -6.8));
            k.draw(new Arc2D.Double(-2.6, 4.2, 5.2, 5.2, 190, 160, Arc2D.OPEN));
            k.dispose();
        };

        /** Red equaliser bars used by "Now Playing". */
        static final Glyph EQUALIZER = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            double[] h = {7, 14, 20, 11, 16};
            for (int i = 0; i < 5; i++) {
                k.fill(new RoundRectangle2D.Double(-9.6 + i * 4.4, -h[i] / 2, 2.4, h[i], 2, 2));
            }
            k.dispose();
        };

        static final Glyph BOLT = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(poly(3.5, -10, -6, 2, -0.8, 2, -3, 10, 6.5, -3, 1, -3));
            k.dispose();
        };

        static final Glyph BARS = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(new RoundRectangle2D.Double(-9, 1, 4.6, 8, 1.2, 1.2));
            k.fill(new RoundRectangle2D.Double(-2.3, -5, 4.6, 14, 1.2, 1.2));
            k.fill(new RoundRectangle2D.Double(4.4, -10, 4.6, 19, 1.2, 1.2));
            k.dispose();
        };

        static final Glyph PERSON = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(new Ellipse2D.Double(-4.4, -9.4, 8.8, 8.8));
            Path2D.Double p = new Path2D.Double();
            p.moveTo(-8.6, 9.6); p.curveTo(-8.6, 2.6, -4.6, 0.6, 0, 0.6);
            p.curveTo(4.6, 0.6, 8.6, 2.6, 8.6, 9.6); p.closePath();
            k.fill(p);
            k.dispose();
        };

        static final Glyph CLOCK = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.1);
            k.draw(new Ellipse2D.Double(-9, -9, 18, 18));
            Path2D.Double h = new Path2D.Double();
            h.moveTo(0, -5); h.lineTo(0, 0.4); h.lineTo(4.2, 3);
            k.draw(h);
            k.dispose();
        };

        static final Glyph TAG = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            p.append(poly(-9.5, -9.5, -0.5, -9.5, 9.5, 0.5, 0.5, 9.5, -9.5, -0.5), false);
            p.append(new Ellipse2D.Double(-6.4, -6.4, 4, 4), false);
            k.fill(p);
            k.dispose();
        };

        static final Glyph SHUFFLE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.1);
            for (int sign = -1; sign <= 1; sign += 2) {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(-9, 5.5 * sign); p.lineTo(-5, 5.5 * sign);
                p.curveTo(0, 5.5 * sign, -1, -5.5 * sign, 4, -5.5 * sign); p.lineTo(6.5, -5.5 * sign);
                k.draw(p);
                k.fill(poly(5.6, -5.5 * sign - 3.2, 10, -5.5 * sign, 5.6, -5.5 * sign + 3.2));
            }
            k.dispose();
        };

        static final Glyph PREV = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(new RoundRectangle2D.Double(-9.5, -8, 2.6, 16, 1, 1));
            k.fill(poly(9.5, -8.6, -5.6, 0, 9.5, 8.6));
            k.dispose();
        };

        static final Glyph NEXT = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.scale(-1, 1);
            k.fill(new RoundRectangle2D.Double(-9.5, -8, 2.6, 16, 1, 1));
            k.fill(poly(9.5, -8.6, -5.6, 0, 9.5, 8.6));
            k.dispose();
        };

        static final Glyph REPEAT = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.1);
            for (int i = 0; i < 2; i++) {
                Path2D.Double p = new Path2D.Double();
                p.moveTo(-9, 2); p.lineTo(-9, -1.6); p.quadTo(-9, -5.2, -5.4, -5.2); p.lineTo(4.6, -5.2);
                k.draw(p);
                k.fill(poly(4, -9, 9.8, -5.2, 4, -1.4));
                k.rotate(Math.PI);
            }
            k.dispose();
        };

        static final Glyph PAUSE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(new RoundRectangle2D.Double(-6.6, -8.2, 4.8, 16.4, 1.8, 1.8));
            k.fill(new RoundRectangle2D.Double(1.8, -8.2, 4.8, 16.4, 1.8, 1.8));
            k.dispose();
        };

        static final Glyph PLAY = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.4);
            Path2D t = poly(-5, -8.5, 8.5, 0, -5, 8.5);
            k.fill(t);
            k.draw(t);
            k.dispose();
        };

        static final Glyph VOLUME = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.fill(poly(-9.5, -3.6, -5.2, -3.6, 0.2, -8.2, 0.2, 8.2, -5.2, 3.6, -9.5, 3.6));
            line(k, 2.0);
            k.draw(new Arc2D.Double(-5.2, -5.6, 11.2, 11.2, -48, 96, Arc2D.OPEN));
            k.draw(new Arc2D.Double(-8.4, -9.4, 18.8, 18.8, -48, 96, Arc2D.OPEN));
            k.dispose();
        };

        static final Glyph REFRESH = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.3);
            k.draw(new Arc2D.Double(-7.4, -7.4, 14.8, 14.8, 60, 285, Arc2D.OPEN));
            k.fill(poly(7.8, -8.8, 8.8, -1.8, 1.8, -3.6));
            k.dispose();
        };

        static final Glyph MORE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            for (int i = -1; i <= 1; i++) k.fill(new Ellipse2D.Double(i * 7 - 2.1, -2.1, 4.2, 4.2));
            k.dispose();
        };

        static final Glyph MINIMIZE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 1.5);
            k.draw(new Line2D.Double(-6.5, 0, 6.5, 0));
            k.dispose();
        };

        static final Glyph MAXIMIZE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 1.5);
            k.draw(new RoundRectangle2D.Double(-6, -6, 12, 12, 2, 2));
            k.dispose();
        };

        static final Glyph CLOSE = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 1.6);
            k.draw(new Line2D.Double(-6, -6, 6, 6));
            k.draw(new Line2D.Double(-6, 6, 6, -6));
            k.dispose();
        };

        static final Glyph EDIT = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            k.rotate(Math.toRadians(45));
            line(k, 2.0);
            k.draw(new RoundRectangle2D.Double(-2.8, -10.5, 5.6, 16, 1.5, 1.5));
            k.fill(poly(-2.8, 7.2, 2.8, 7.2, 0, 11.4));
            k.draw(new Line2D.Double(-2.8, -6.2, 2.8, -6.2));
            k.dispose();
        };

        static final Glyph CHEVRON = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 2.6);
            Path2D.Double p = new Path2D.Double();
            p.moveTo(-6.5, -3.2); p.lineTo(0, 3.4); p.lineTo(6.5, -3.2);
            k.draw(p);
            k.dispose();
        };

        static final Glyph CHECK = (g, cx, cy, s, c) -> {
            Graphics2D k = box(g, cx, cy, s, c);
            line(k, 3.0);
            Path2D.Double p = new Path2D.Double();
            p.moveTo(-6.5, 0.5); p.lineTo(-2.2, 5); p.lineTo(6.8, -5.2);
            k.draw(p);
            k.dispose();
        };
    }

    /**
     * Generated cover art (no image files are needed). The six sample songs get
     * the six covers from the design; any new song gets one of them by title hash.
     */
    static final class Art {
        private Art() { }

        static int styleFor(Track t) {
            if (t == null) return 0;
            switch (t.getTitle().toLowerCase()) {
                case "night changes":      return 0;
                case "perfect":            return 1;
                case "believer":           return 2;
                case "counting stars":     return 3;
                case "someone you loved":  return 4;
                case "shape of you":       return 5;
                default: return Math.abs(t.getTitle().toLowerCase().hashCode()) % 6;
            }
        }

        /** Paints the cover into the rectangle (x, y, w, h) with rounded corners. */
        static void cover(Graphics2D g0, int style, double x, double y, double w, double h, double radius) {
            Graphics2D g = (Graphics2D) g0.create();
            g.clip(new RoundRectangle2D.Double(x, y, w, h, radius * 2, radius * 2));
            g.translate(x, y);
            g.scale(w / 100.0, h / 100.0);
            switch (style) {
                case 0: sunsetPalms(g); break;
                case 1: burst(g); break;
                case 2: mountain(g); break;
                case 3: silhouette(g); break;
                case 4: forestRoad(g); break;
                default: moonHorizon(g); break;
            }
            g.dispose();
        }

        private static GradientPaint v(double y0, Color a, double y1, Color b) {
            return new GradientPaint(0, (float) y0, a, 0, (float) y1, b);
        }

        private static void sunsetPalms(Graphics2D g) {
            g.setPaint(new LinearGradientPaint(0, 0, 0, 68, new float[]{0f, 0.4f, 0.75f, 1f},
                    new Color[]{new Color(0x2B2070), new Color(0x9B2F8E), new Color(0xF0466A), new Color(0xFF9440)}));
            g.fillRect(0, 0, 100, 68);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(42, 67), 30, new float[]{0f, 1f},
                    new Color[]{new Color(255, 214, 120, 230), new Color(255, 150, 70, 0)}));
            g.fill(new Rectangle2D.Double(0, 30, 100, 40));
            g.setColor(new Color(0xFFD98A));
            g.fill(new Ellipse2D.Double(34, 58, 16, 16));
            g.setPaint(v(66, new Color(0x4A1E5C), 100, new Color(0x1A0D30)));
            g.fillRect(0, 66, 100, 34);
            g.setColor(new Color(255, 170, 90, 150));
            for (int i = 0; i < 6; i++) g.fill(new Rectangle2D.Double(32 + i * 1.5, 69 + i * 4.5, 20 - i * 3, 1.6));
            g.setColor(new Color(0x14091F));
            palm(g, 82, 100, 74, 30, 0.9);
            palm(g, 93, 100, 91, 26, 0.8);
            palm(g, 10, 67, 9, 56, 0.45);
            palm(g, 21, 67, 22, 58, 0.35);
        }

        private static void palm(Graphics2D g, double bx, double by, double tx, double ty, double scale) {
            g.setStroke(new BasicStroke((float) (2.6 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double trunk = new Path2D.Double();
            trunk.moveTo(bx, by);
            trunk.quadTo(bx + (tx - bx) * 0.2 - 3, (by + ty) / 2, tx, ty);
            g.draw(trunk);
            g.setStroke(new BasicStroke((float) (1.5 * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] angles = {-170, -145, -118, -90, -62, -35, -10};
            for (int a : angles) {
                double r = Math.toRadians(a);
                double len = 17 * scale;
                Path2D.Double leaf = new Path2D.Double();
                leaf.moveTo(tx, ty);
                leaf.quadTo(tx + Math.cos(r) * len * 0.55, ty + Math.sin(r) * len * 0.55 - 5 * scale,
                        tx + Math.cos(r) * len, ty + Math.sin(r) * len + 6 * scale);
                g.draw(leaf);
            }
        }

        private static void burst(Graphics2D g) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 50), 75, new float[]{0f, 0.25f, 0.6f, 1f},
                    new Color[]{new Color(0xF2C9FF), new Color(0x8B3CFF), new Color(0x3A1385), new Color(0x120626)}));
            g.fillRect(0, 0, 100, 100);
            g.setColor(new Color(214, 160, 255, 110));
            for (int i = 0; i < 28; i++) {
                double a = Math.toRadians(i * 360.0 / 28), b = Math.toRadians(i * 360.0 / 28 + 4);
                Path2D.Double ray = new Path2D.Double();
                ray.moveTo(50, 50);
                ray.lineTo(50 + Math.cos(a) * 90, 50 + Math.sin(a) * 90);
                ray.lineTo(50 + Math.cos(b) * 90, 50 + Math.sin(b) * 90);
                ray.closePath();
                g.fill(ray);
            }
            g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 50), 14, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 240), new Color(255, 255, 255, 0)}));
            g.fill(new Ellipse2D.Double(30, 30, 40, 40));
        }

        private static void mountain(Graphics2D g) {
            g.setPaint(v(0, new Color(0xD7DAE0), 70, new Color(0x7A808C)));
            g.fillRect(0, 0, 100, 100);
            g.setColor(new Color(0x4B5262));
            g.fill(new Polygon(new int[]{0, 22, 40, 52, 66, 100, 100, 0}, new int[]{72, 50, 20, 36, 30, 58, 100, 100}, 8));
            g.setColor(new Color(0xEEF0F4));
            g.fill(new Polygon(new int[]{40, 34, 44, 49, 52}, new int[]{20, 38, 33, 40, 36}, 5));
            g.fill(new Polygon(new int[]{66, 60, 68, 74, 72}, new int[]{30, 42, 40, 44, 38}, 5));
            g.setPaint(v(66, new Color(0x2C313B), 100, new Color(0x14171D)));
            g.fillRect(0, 68, 100, 32);
            g.setPaint(v(68, new Color(0xC6764A), 100, new Color(0x5A3524)));
            g.fill(new Polygon(new int[]{48, 52, 74, 26}, new int[]{68, 68, 100, 100}, 4));
        }

        private static void silhouette(Graphics2D g) {
            g.setPaint(new LinearGradientPaint(0, 0, 0, 100, new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(0x4B2A78), new Color(0xE0583C), new Color(0xFFA14A)}));
            g.fillRect(0, 0, 100, 100);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 52), 42, new float[]{0f, 1f},
                    new Color[]{new Color(255, 200, 110, 200), new Color(255, 140, 70, 0)}));
            g.fillRect(0, 0, 100, 100);
            g.setColor(new Color(0x140B14));
            g.fill(new Ellipse2D.Double(41, 33, 18, 20));
            Path2D.Double body = new Path2D.Double();
            body.moveTo(18, 100); body.curveTo(20, 70, 30, 60, 50, 60);
            body.curveTo(70, 60, 80, 70, 82, 100); body.closePath();
            g.fill(body);
        }

        private static void forestRoad(Graphics2D g) {
            g.setPaint(v(0, new Color(0xE3E4E7), 100, new Color(0x8E9096)));
            g.fillRect(0, 0, 100, 100);
            g.setColor(new Color(0x2B2D32));
            int[] left = {6, 16, 27};
            int[] right = {94, 84, 73};
            for (int i = 0; i < 3; i++) {
                double w = 6 - i * 1.6;
                g.fill(new Rectangle2D.Double(left[i] - w / 2, 0, w, 100));
                g.fill(new Rectangle2D.Double(right[i] - w / 2, 0, w, 100));
            }
            g.setColor(new Color(0x6B6E75));
            for (int x : new int[]{34, 40, 60, 66}) g.fill(new Rectangle2D.Double(x - 1, 20, 2, 60));
            g.setPaint(v(50, new Color(255, 255, 255, 0), 100, new Color(0xB8B9BD)));
            g.fill(new Polygon(new int[]{47, 53, 80, 20}, new int[]{50, 50, 100, 100}, 4));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 52), 26, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 190), new Color(255, 255, 255, 0)}));
            g.fill(new Rectangle2D.Double(20, 25, 60, 55));
        }

        private static void moonHorizon(Graphics2D g) {
            g.setPaint(new LinearGradientPaint(0, 0, 0, 100, new float[]{0f, 0.55f, 0.85f, 1f},
                    new Color[]{new Color(0x1B0F4D), new Color(0x4F25C6), new Color(0xA14FFF), new Color(0x2A1170)}));
            g.fillRect(0, 0, 100, 100);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 76), 24, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 190), new Color(255, 255, 255, 0)}));
            g.fill(new Ellipse2D.Double(24, 50, 52, 52));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(45, 71, 10, 10));
            g.setColor(new Color(0x0C0720));
            g.fill(new Polygon(new int[]{0, 0, 30, 60, 100, 100}, new int[]{100, 88, 84, 86, 82, 100}, 6));
        }
    }

    /**
     * A piece of the window with a fixed place in the 1536 x 1024 design. The stage
     * scales the component's bounds; the Part scales its drawing and mouse
     * coordinates so subclasses can work purely in design pixels.
     */
    static abstract class Part extends JComponent {
        final Rectangle design;

        Part(int x, int y, int w, int h) {
            design = new Rectangle(x, y, w, h);
            setOpaque(false);
        }

        double sx() { return Math.max(1, getWidth()) / (double) design.width; }
        double sy() { return Math.max(1, getHeight()) / (double) design.height; }

        Point2D toDesign(MouseEvent e) { return new Point2D.Double(e.getX() / sx(), e.getY() / sy()); }

        @Override
        protected final void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.g2(g);
            g2.scale(sx(), sy());
            paintPart(g2, design.width, design.height);
            g2.dispose();
        }

        abstract void paintPart(Graphics2D g, int w, int h);
    }

    /** A flat button drawn in design pixels: red, grey, round red, or icon-only. */
    static class Button extends Part {
        enum Look { RED, GRAY, CIRCLE, GHOST, NAV, NAV_ACTIVE }

        private final Look look;
        private final Glyph glyph;
        private final String label;
        private final Runnable action;
        private boolean hover;

        // layout tweaks (design px)
        double iconX = -1, iconSize = 22, textX = -1, textSize = 17, radius = 10;
        boolean boldText = true;
        Color iconColor = Theme.TEXT;
        boolean centerText;
        boolean active;                       // used by sidebar items
        Supplier<Color> colorSupplier;        // icon colour that depends on state (shuffle / repeat)
        Supplier<Glyph> glyphSupplier;        // icon that depends on state (play / pause)

        Button(int x, int y, int w, int h, Look look, Glyph glyph, String label, Runnable action) {
            super(x, y, w, h);
            this.look = look;
            this.glyph = glyph;
            this.label = label;
            this.action = action;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint()) && action != null) action.run();
                }
            });
        }

        /** Fluent setters used while building the window. */
        Button icon(double x, double size) { iconX = x; iconSize = size; return this; }
        Button text(double x, double size, boolean bold) { textX = x; textSize = size; boldText = bold; return this; }
        Button radius(double r) { radius = r; return this; }
        Button iconColor(Color c) { iconColor = c; return this; }
        Button dynamicColor(Supplier<Color> s) { colorSupplier = s; return this; }
        Button dynamicGlyph(Supplier<Glyph> s) { glyphSupplier = s; return this; }
        void setActive(boolean a) { active = a; repaint(); }

        @Override
        void paintPart(Graphics2D g, int w, int h) {
            switch (look) {
                case RED:
                    Ui.roundRect(g, 0, 0, w, h, radius, hover ? Ui.redGradient(0, 0, w, h * 1.4) : Ui.redGradient(0, 0, w, h), null, 0);
                    break;
                case NAV_ACTIVE:
                    Ui.roundRect(g, 0, 0, w, h, radius, new GradientPaint(0, 0, Theme.RED, w, 0, new Color(0x7A0816)), null, 0);
                    break;
                case GRAY:
                    Ui.roundRect(g, 0, 0, w, h, radius, hover ? Theme.GRAY_HOVER : Theme.GRAY, hover ? Ui.alpha(Theme.RED, 120) : new Color(0x242B36), 1.2);
                    break;
                case NAV:
                    if (active) Ui.roundRect(g, 0, 0, w, h, radius, new GradientPaint(0, 0, Theme.RED, w, 0, new Color(0x7A0816)), null, 0);
                    else if (hover) Ui.roundRect(g, 0, 0, w, h, radius, new Color(255, 255, 255, 14), null, 0);
                    break;
                case CIRCLE:
                    g.setPaint(hover ? Ui.redGradient(0, 0, w, h * 1.3) : Ui.redGradient(0, 0, w, h));
                    g.fillOval(0, 0, w, h);
                    break;
                case GHOST:
                    if (hover) g.setColor(new Color(255, 255, 255, 22)); else g.setColor(new Color(0, 0, 0, 0));
                    g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 10, 10));
                    break;
                default: break;
            }
            Glyph gl = glyphSupplier != null ? glyphSupplier.get() : glyph;
            if (gl != null) {
                double ix = iconX >= 0 ? iconX : w / 2.0;
                gl.draw(g, ix, h / 2.0, iconSize, colorSupplier != null ? colorSupplier.get() : iconColor);
            }
            if (label != null) {
                Font f = Ui.font(boldText || active, textSize);
                if (centerText || textX < 0) Ui.textC(g, label, w / 2.0, h / 2.0, f, Theme.TEXT);
                else Ui.text(g, label, textX, h / 2.0, f, Theme.TEXT);
            }
        }
    }

    /** Dark, red-accented dialogs that match the main window. */
    static final class Dialogs {
        private Dialogs() { }

        static final String[] GENRES = {"Pop", "Rock", "Hip Hop", "R&B", "Other"};

        /** Rounded flat button used inside dialogs. */
        static class FlatButton extends JButton {
            private final boolean red;
            private boolean selectedChip;
            private final boolean chip;

            FlatButton(String text, boolean red, boolean chip, double f) {
                super(text);
                this.red = red;
                this.chip = chip;
                setFont(Ui.font(true, 14 * f));
                setForeground(Theme.TEXT);
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setBorder(new EmptyBorder((int) (9 * f), (int) (18 * f), (int) (9 * f), (int) (18 * f)));
            }

            void setChipSelected(boolean s) { selectedChip = s; repaint(); }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.g2(g);
                boolean hover = getModel().isRollover();
                Paint fill;
                if (chip) fill = selectedChip ? Theme.RED : (hover ? Theme.GRAY_HOVER : Theme.GRAY);
                else if (red) fill = Ui.redGradient(0, 0, getWidth(), getHeight());
                else fill = hover ? Theme.GRAY_HOVER : Theme.GRAY;
                Ui.roundRect(g2, 0, 0, getWidth(), getHeight(), getHeight() / 4.0, fill, null, 0);
                g2.dispose();
                super.paintComponent(g);
            }
        }

        private static JDialog shell(Window owner, double f, String title, JComponent body, JComponent buttons, int width) {
            JDialog d = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
            d.setUndecorated(true);
            JPanel root = new JPanel(new BorderLayout(0, (int) (14 * f)));
            root.setBackground(Theme.PANEL);
            root.setBorder(new CompoundBorder(new LineBorder(Theme.RED_DARK, 1), new EmptyBorder((int) (22 * f), (int) (26 * f), (int) (20 * f), (int) (26 * f))));
            JLabel t = new JLabel(title);
            t.setFont(Ui.font(true, 20 * f));
            t.setForeground(Theme.TEXT);
            root.add(t, BorderLayout.NORTH);
            root.add(body, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);
            d.setContentPane(root);
            d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            d.pack();
            d.setSize(new Dimension(Math.max(d.getWidth(), (int) (width * f)), d.getHeight()));   // never narrower than the content
            d.setLocationRelativeTo(owner);
            return d;
        }

        private static JComponent messageBody(String msg, double f) {
            String html = "<html><body style='width:" + (int) (356 * f) + "px'>"
                    + msg.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</body></html>";
            JLabel a = new JLabel(html);
            a.setVerticalAlignment(SwingConstants.TOP);
            a.setFont(Ui.font(false, 15 * f));
            a.setForeground(Theme.MUTED);
            return a;
        }

        private static JPanel buttonRow(JComponent... bs) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            p.setOpaque(false);
            for (JComponent b : bs) p.add(b);
            return p;
        }

        static void message(Window owner, double f, String title, String msg) {
            FlatButton ok = new FlatButton("OK", true, false, f);
            JDialog d = shell(owner, f, title, messageBody(msg, f), buttonRow(ok), 440);
            ok.addActionListener(e -> d.dispose());
            d.getRootPane().setDefaultButton(ok);
            d.setVisible(true);
        }

        static boolean confirm(Window owner, double f, String title, String msg, String yes) {
            boolean[] result = {false};
            FlatButton cancel = new FlatButton("Cancel", false, false, f);
            FlatButton ok = new FlatButton(yes, true, false, f);
            JDialog d = shell(owner, f, title, messageBody(msg, f), buttonRow(cancel, ok), 440);
            cancel.addActionListener(e -> d.dispose());
            ok.addActionListener(e -> { result[0] = true; d.dispose(); });
            d.getRootPane().setDefaultButton(ok);
            d.setVisible(true);
            return result[0];
        }

        private static JTextField field(String text, double f) {
            JTextField tf = new JTextField(text);
            tf.setFont(Ui.font(false, 16 * f));
            tf.setForeground(Theme.TEXT);
            tf.setCaretColor(Theme.TEXT);
            tf.setBackground(Theme.ROW);
            tf.setBorder(new CompoundBorder(new LineBorder(Theme.BORDER, 1), new EmptyBorder((int) (8 * f), (int) (10 * f), (int) (8 * f), (int) (10 * f))));
            return tf;
        }

        private static JLabel caption(String s, double f) {
            JLabel l = new JLabel(s);
            l.setFont(Ui.font(true, 13 * f));
            l.setForeground(Theme.DIM);
            return l;
        }

        /**
         * Settings / Exercise Lab: buttons that call the assignment methods of MusicOrganizer on the live
         * collection and show what they print to the console, so every exercise can be demonstrated inside the GUI.
         */
        static JDialog lab(Window owner, double f, MusicOrganizer organizer, Runnable changed) {
            JDialog d = new JDialog(owner, Dialog.ModalityType.MODELESS);
            d.setUndecorated(true);
            JPanel root = new JPanel(new BorderLayout(0, (int) (10 * f)));
            root.setBackground(Theme.PANEL);
            root.setBorder(new CompoundBorder(new LineBorder(Theme.RED_DARK, 1), new EmptyBorder((int) (18 * f), (int) (22 * f), (int) (16 * f), (int) (22 * f))));

            JLabel title = new JLabel("Settings  -  Exercise Lab");
            title.setFont(Ui.font(true, 20 * f));
            title.setForeground(Theme.TEXT);
            root.add(title, BorderLayout.NORTH);

            JTextArea out = new JTextArea();
            out.setEditable(false);
            out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(9, (int) Math.round(13 * f))));
            out.setBackground(Theme.BG);
            out.setForeground(new Color(0xD7DBE2));
            out.setCaretColor(Theme.TEXT);
            out.setBorder(new EmptyBorder(8, 10, 8, 10));
            out.setText("Songs in collection: " + organizer.getNumberOfTracks() + "   (ArrayList<Track>, removal while looping uses Iterator.remove())\n"
                    + "Type an index / text / value below, then press a button to run that exercise method.\n\n");
            JScrollPane sp = new JScrollPane(out);
            sp.setBorder(new LineBorder(Theme.BORDER, 1));
            sp.setPreferredSize(new Dimension((int) (700 * f), (int) (250 * f)));
            root.add(sp, BorderLayout.CENTER);

            JTextField arg = field("", f);
            JTextField val = field("", f);
            JPanel inputs = new JPanel(new GridLayout(2, 2, (int) (8 * f), (int) (2 * f)));
            inputs.setOpaque(false);
            inputs.add(caption("INDEX or TEXT  (artist / title / search text)", f));
            inputs.add(caption("VALUE  (rating 0-5)", f));
            inputs.add(arg);
            inputs.add(val);

            JPanel grid = new JPanel(new GridLayout(0, 3, (int) (6 * f), (int) (6 * f)));
            grid.setOpaque(false);
            boolean[] busy = {false};

            // runs an action while capturing System.out, then shows it in the console area
            java.util.function.Consumer<Runnable> exec = r -> {
                PrintStream old = System.out;
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                System.setOut(new PrintStream(buf, true));
                try { r.run(); }
                catch (RuntimeException ex) { System.out.println("Error: " + ex); }
                finally { System.setOut(old); }
                String text = buf.toString();
                out.append(text.isEmpty() ? "   (nothing printed)\n" : text);
                out.append("\n");
                out.setCaretPosition(out.getDocument().getLength());
                changed.run();
            };
            java.util.function.BiConsumer<String, Runnable> add = (label, action) -> {
                FlatButton b = new FlatButton(label, false, false, f);
                b.setFont(Ui.font(false, 13 * f));
                b.addActionListener(e -> {
                    if (busy[0]) { out.append("Please wait: a sample is still playing.\n\n"); return; }
                    out.append("> " + label + "\n");
                    action.run();
                });
                grid.add(b);
            };
            java.util.function.IntSupplier idx = () -> {
                try { return Integer.parseInt(arg.getText().trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("index must be a whole number"); }
            };

            add.accept("checkIndex(index)", () -> exec.accept(() -> organizer.checkIndex(idx.getAsInt())));
            add.accept("validIndex(index)", () -> exec.accept(() -> System.out.println("validIndex(" + idx.getAsInt() + ") = " + organizer.validIndex(idx.getAsInt()))));
            add.accept("listTrack(index)", () -> exec.accept(() -> organizer.listTrack(idx.getAsInt())));
            add.accept("listAllTracks()", () -> exec.accept(organizer::listAllTracks));
            add.accept("listAllTracksWithPosition()", () -> exec.accept(organizer::listAllTracksWithPosition));
            add.accept("listMatching(text)", () -> exec.accept(() -> organizer.listMatching(arg.getText())));
            add.accept("playTrack(index)", () -> exec.accept(() -> {
                Track t = organizer.getTrack(idx.getAsInt());
                if (t == null) { System.out.println("No track at that index."); return; }
                organizer.playTrack(t);
                System.out.println("Now playing: " + t.getDetails());
            }));
            add.accept("resetPlayCount(index)", () -> exec.accept(() -> {
                Track t = organizer.getTrack(idx.getAsInt());
                if (t == null) { System.out.println("No track at that index."); return; }
                t.resetPlayCount();
                System.out.println(t.getDetails());
            }));
            add.accept("setRating(index, value)", () -> exec.accept(() -> {
                int v;
                try { v = Integer.parseInt(val.getText().trim()); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("value must be a whole number"); }
                organizer.setRating(idx.getAsInt(), v);
                organizer.listTrack(idx.getAsInt());
            }));
            add.accept("removeByTitle(text)", () -> exec.accept(() -> {
                int n = organizer.removeByTitle(arg.getText());
                System.out.println("Removed " + n + " track(s); " + organizer.getNumberOfTracks() + " left.");
            }));
            add.accept("playSamplesByArtist(text)", () -> {
                busy[0] = true;
                organizer.getPlayer().setSampleMillis(1200);
                Thread t = new Thread(() -> {
                    PrintStream old = System.out;
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    System.setOut(new PrintStream(buf, true));
                    try { organizer.playSamplesByArtist(arg.getText().trim()); }
                    finally { System.setOut(old); }
                    SwingUtilities.invokeLater(() -> {
                        out.append("   one sample after the other, in collection order (play counts went up by one)\n");
                        out.append(buf.toString().isEmpty() ? "" : buf.toString());
                        out.append("\n");
                        out.setCaretPosition(out.getDocument().getLength());
                        busy[0] = false;
                        changed.run();
                    });
                });
                t.start();
            });
            add.accept("getNumberOfTracks()", () -> exec.accept(() -> System.out.println("getNumberOfTracks() = " + organizer.getNumberOfTracks())));

            FlatButton close = new FlatButton("Close", true, false, f);
            FlatButton clear = new FlatButton("Clear console", false, false, f);
            close.addActionListener(e -> d.dispose());
            clear.addActionListener(e -> out.setText(""));

            JPanel south = new JPanel(new BorderLayout(0, (int) (8 * f)));
            south.setOpaque(false);
            JPanel top = new JPanel(new BorderLayout(0, (int) (6 * f)));
            top.setOpaque(false);
            top.add(inputs, BorderLayout.NORTH);
            top.add(grid, BorderLayout.CENTER);
            south.add(top, BorderLayout.CENTER);
            south.add(buttonRow(clear, close), BorderLayout.SOUTH);
            root.add(south, BorderLayout.SOUTH);

            d.setContentPane(root);
            d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            d.pack();
            d.setLocationRelativeTo(owner);
            d.setVisible(true);
            return d;
        }

        /** Add / edit form. Returns {title, artist, duration, genre, audioFile} or null when cancelled. */
        static String[] trackForm(Window owner, double f, String heading, Track existing) {
            JTextField title = field(existing == null ? "" : existing.getTitle(), f);
            JTextField artist = field(existing == null ? "" : existing.getArtist(), f);
            JTextField duration = field(existing == null ? "3:30" : existing.getDuration(), f);
            String[] genre = {existing == null ? "Pop" : existing.getGenre()};
            JTextField file = field(existing == null ? "" : existing.getFilePath(), f);
            FlatButton browse = new FlatButton("Browse...", false, false, f);
            JLabel error = new JLabel(" ");
            error.setFont(Ui.font(false, 13 * f));
            error.setForeground(Theme.RED_LIGHT);

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            chips.setOpaque(false);
            java.util.List<FlatButton> chipButtons = new java.util.ArrayList<>();
            for (String g : GENRES) {
                FlatButton b = new FlatButton(g, false, true, f);
                b.setFont(Ui.font(false, 13 * f));
                b.setBorder(new EmptyBorder((int) (6 * f), (int) (12 * f), (int) (6 * f), (int) (12 * f)));
                b.setChipSelected(g.equalsIgnoreCase(genre[0]));
                b.addActionListener(e -> {
                    genre[0] = g;
                    for (FlatButton o : chipButtons) o.setChipSelected(o == b);
                });
                chipButtons.add(b);
                chips.add(b);
            }

            JPanel body = new JPanel(new GridLayout(0, 1, 0, (int) (4 * f)));
            body.setOpaque(false);
            body.add(caption("SONG TITLE", f)); body.add(title);
            body.add(caption("ARTIST", f)); body.add(artist);
            body.add(caption("DURATION (m:ss)", f)); body.add(duration);
            body.add(caption("GENRE", f)); body.add(chips);
            body.add(caption("AUDIO FILE (optional, WAV / AIFF / AU)", f));
            JPanel fileRow = new JPanel(new BorderLayout((int) (8 * f), 0));
            fileRow.setOpaque(false);
            fileRow.add(file, BorderLayout.CENTER);
            fileRow.add(browse, BorderLayout.EAST);
            body.add(fileRow);
            body.add(error);

            String[][] out = {null};
            FlatButton cancel = new FlatButton("Cancel", false, false, f);
            FlatButton ok = new FlatButton(existing == null ? "Add Song" : "Save", true, false, f);
            JDialog d = shell(owner, f, heading, body, buttonRow(cancel, ok), 460);
            cancel.addActionListener(e -> d.dispose());
            browse.addActionListener(e -> {
                javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Audio files (wav, aiff, au, mp3, ogg, m4a)", "wav", "aif", "aiff", "au", "mp3", "ogg", "m4a"));
                if (fc.showOpenDialog(d) != javax.swing.JFileChooser.APPROVE_OPTION) return;
                String path = fc.getSelectedFile().getAbsolutePath();
                file.setText(path);
                int secs = assignment.AudioEngine.probeSeconds(path);
                if (secs > 0) {
                    duration.setText((secs / 60) + ":" + String.format("%02d", secs % 60));
                    error.setText(" ");
                    if (title.getText().trim().isEmpty()) {
                        String n = fc.getSelectedFile().getName();
                        int dot = n.lastIndexOf('.');
                        title.setText(dot > 0 ? n.substring(0, dot) : n);
                    }
                    if (artist.getText().trim().isEmpty()) artist.setText("Unknown artist");
                } else {
                    error.setText("Java cannot decode this format here (needs WAV/AIFF/AU); a melody will play instead.");
                }
            });
            ok.addActionListener(e -> {
                if (title.getText().trim().isEmpty() || artist.getText().trim().isEmpty()) {
                    error.setText("Please enter a title and an artist.");
                } else if (!duration.getText().trim().matches("\\d{1,3}:[0-5]\\d")) {
                    error.setText("Duration must look like 3:46.");
                } else {
                    out[0] = new String[]{title.getText().trim(), artist.getText().trim(), duration.getText().trim(), genre[0], file.getText().trim()};
                    d.dispose();
                }
            });
            d.getRootPane().setDefaultButton(ok);
            d.addWindowListener(new WindowAdapter() { @Override public void windowOpened(WindowEvent e) { title.requestFocusInWindow(); } });
            d.setVisible(true);
            return out[0];
        }
    }
}