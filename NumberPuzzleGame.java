
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import javax.sound.sampled.*;

public class NumberPuzzleGame extends JFrame {

    private static final int SIZE = 3;
    private static final int TOTAL = 9;
    private static final float SAMPLE_RATE = 44100f;

    private static final AudioFormat SOUND_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

    private final int[] tiles = new int[TOTAL];
    private final int[] startingBoard = new int[TOTAL];
    private final TileButton[] tileButtons = new TileButton[TOTAL];
    private final Random random = new Random();

    private int currentTheme = 0;
    private int moves = 0;
    private int seconds = 0;
    private boolean gameWon = false;
    private boolean gameInitialized = false;

    private byte[] clickSoundData;
    private byte[] winSoundData;

    private JLabel movesValueLabel, timeValueLabel, messageLabel;
    private JLabel winMovesLabel, winTimeLabel, winThemeLabel;

    private ScenePanel scene;
    private BoardPanel boardFrame;
    private VictoryPanel victoryPanel;

    private CardLayout pageLayout;
    private JPanel mainContainer;

    private Timer gameTimer;
    private Timer animationTimer;

    private final String[] themeNames = {
            "WOODEN", "OCEAN", "FOREST",
            "GALAXY", "NEON", "PINK"
    };

    // Top background, bottom background, border, tile
    private final Color[][] themeColors = {
            {new Color(150,85,42), new Color(35,16,8),
                    new Color(255,205,120), new Color(195,125,65)},

            {new Color(15,185,215), new Color(5,35,100),
                    new Color(110,250,255), new Color(20,130,180)},

            {new Color(50,140,70), new Color(10,45,25),
                    new Color(180,245,125), new Color(75,145,75)},

            {new Color(100,45,170), new Color(15,5,45),
                    new Color(215,160,255), new Color(100,70,175)},

            {new Color(10,35,60), new Color(0,5,20),
                    new Color(0,255,225), new Color(20,85,110)},

            {new Color(255,175,210), new Color(115,40,100),
                    new Color(255,230,245), new Color(220,105,160)}
    };

    public NumberPuzzleGame() {
        setTitle("Number Puzzle | The Wooden Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 900);
        setMinimumSize(new Dimension(650, 720));
        setLocationRelativeTo(null);

        buildSounds();

        pageLayout = new CardLayout();
        mainContainer = new JPanel(pageLayout);
        setContentPane(mainContainer);

        createSplashScreen();
        createWinScreen();

        scene = new ScenePanel();
        scene.setLayout(new BorderLayout(12, 12));
        scene.setBorder(new EmptyBorder(15, 22, 15, 22));

        createHeader();
        createBoard();
        createFooter();
        createTimers();

        mainContainer.add(scene, "GAME");
        pageLayout.show(mainContainer, "SPLASH");
    }

    // ==================================================
    // SOUND SYSTEM
    // ==================================================

    private void buildSounds() {
        clickSoundData = buildClickSound();
        winSoundData = buildWinSound();
    }

    private byte[] buildClickSound() {
        int samples = (int) (SAMPLE_RATE * 0.07);
        double[] buffer = new double[samples];

        for (int i = 0; i < samples; i++) {
            double progress = (double) i / samples;
            double fade = Math.pow(1.0 - progress, 2.0);
            double frequency = 1300 - 500 * progress;

            buffer[i] = Math.sin(
                    2 * Math.PI * frequency * i / SAMPLE_RATE
            ) * fade * 0.7;
        }

        return toPcm(buffer);
    }

    // Upgraded victory melody with a longer final chord.
    private byte[] buildWinSound() {
        double[] notes = {
                523.25, 659.25, 783.99, 1046.50,
                783.99, 1046.50
        };

        double noteLength = 0.16;
        double finalLength = 1.20;

        int total = (int) (SAMPLE_RATE *
                (notes.length * noteLength + finalLength));

        double[] buffer = new double[total];

        // Ascending and celebratory melody
        for (int n = 0; n < notes.length; n++) {
            int start = (int) (SAMPLE_RATE * n * noteLength);
            addTone(buffer, start, notes[n], noteLength, 0.25);
        }

        // Final victory chord
        int chordStart =
                (int) (SAMPLE_RATE * notes.length * noteLength);

        for (double frequency : notes) {
            addTone(buffer, chordStart, frequency,
                    finalLength, 0.22);
        }

        return toPcm(buffer);
    }

    private void addTone(double[] buffer, int startSample,
                         double frequency, double durationSec,
                         double amplitude) {

        int length = (int) (SAMPLE_RATE * durationSec);

        for (int i = 0; i < length; i++) {
            int index = startSample + i;

            if (index >= buffer.length) {
                break;
            }

            double t = (double) i / SAMPLE_RATE;
            double attack = Math.min(
                    1.0, i / (SAMPLE_RATE * 0.005)
            );
            double decay = Math.exp(-3.5 * i / length);

            double wave =
                    Math.sin(2 * Math.PI * frequency * t)
                            + 0.35 * Math.sin(
                            4 * Math.PI * frequency * t)
                            + 0.15 * Math.sin(
                            6 * Math.PI * frequency * t);

            buffer[index] += wave * attack * decay * amplitude;
        }
    }

    private byte[] toPcm(double[] buffer) {
        byte[] data = new byte[buffer.length * 2];

        for (int i = 0; i < buffer.length; i++) {
            double value = Math.max(-1, Math.min(1, buffer[i]));
            short sample = (short) (value * 30000);

            data[i * 2] = (byte) (sample & 0xff);
            data[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }

        return data;
    }

    private void playSound(byte[] data) {
        if (data == null) return;

        Thread soundThread = new Thread(() -> {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(SOUND_FORMAT, data, 0, data.length);

                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

                clip.start();

            } catch (Exception ex) {
                Toolkit.getDefaultToolkit().beep();
            }
        }, "PuzzleSound");

        soundThread.setDaemon(true);
        soundThread.start();
    }

    private void playClickSound() {
        playSound(clickSoundData);
    }

    private void playWinSound() {
        playSound(winSoundData);
    }

    // ==================================================
    // SPLASH SCREEN
    // ==================================================

    private void createSplashScreen() {
        SplashPanel splash = new SplashPanel();
        splash.setLayout(new GridBagLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel emblem = centeredLabel(
                "✦ THE CLASSIC COLLECTION ✦",
                new Font("Serif", Font.BOLD, 14),
                new Color(255, 215, 145));

        JLabel icon = centeredLabel(
                "♟", new Font("Serif", Font.PLAIN, 65),
                new Color(255, 220, 155));

        JLabel title = centeredLabel(
                "THE WOODEN",
                new Font("Serif", Font.BOLD, 43),
                new Color(255, 224, 165));

        JLabel title2 = centeredLabel(
                "NUMBER PUZZLE",
                new Font("Serif", Font.BOLD, 38),
                Color.WHITE);

        JLabel subtitle = centeredLabel(
                "A LITTLE PATIENCE. A BRILLIANT SOLUTION.",
                new Font("Serif", Font.ITALIC, 14),
                new Color(240, 215, 180));

        JPanel preview = new JPanel(new GridLayout(3, 3, 5, 5));
        preview.setOpaque(false);
        preview.setMaximumSize(new Dimension(180, 180));
        preview.setAlignmentX(Component.CENTER_ALIGNMENT);

        for (int i = 1; i <= 9; i++) {
            JLabel cell = new JLabel(
                    i == 9 ? "" : String.valueOf(i),
                    SwingConstants.CENTER);

            cell.setFont(new Font("Serif", Font.BOLD, 23));
            cell.setForeground(Color.WHITE);
            cell.setOpaque(true);
            cell.setBackground(i == 9
                    ? new Color(30, 15, 8)
                    : new Color(145, 85, 42));

            cell.setBorder(BorderFactory.createLineBorder(
                    new Color(255, 211, 140), 2));

            preview.add(cell);
        }

        JButton start = createButton(
                "▶   BEGIN YOUR JOURNEY",
                new Color(34, 120, 65));

        start.setFont(new Font("Serif", Font.BOLD, 17));
        start.setAlignmentX(Component.CENTER_ALIGNMENT);
        start.setMaximumSize(new Dimension(310, 52));
        start.addActionListener(e -> openGame());

        JLabel instruction = centeredLabel(
                "Slide the tiles. Find the order. Beat your best.",
                new Font("Serif", Font.PLAIN, 14),
                new Color(255, 240, 215));

        JLabel footer = centeredLabel(
                "SIX WORLDS  •  ONE PUZZLE  •  YOUR OWN RECORD",
                new Font("Arial", Font.BOLD, 10),
                new Color(255, 210, 145));

        content.add(emblem);
        content.add(Box.createVerticalStrut(14));
        content.add(icon);
        content.add(Box.createVerticalStrut(4));
        content.add(title);
        content.add(title2);
        content.add(Box.createVerticalStrut(9));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(20));
        content.add(preview);
        content.add(Box.createVerticalStrut(22));
        content.add(start);
        content.add(Box.createVerticalStrut(13));
        content.add(instruction);
        content.add(Box.createVerticalStrut(20));
        content.add(footer);

        splash.add(content);
        mainContainer.add(splash, "SPLASH");
    }

    private JLabel centeredLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private class SplashPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setPaint(new GradientPaint(
                    0, 0, new Color(105, 57, 28),
                    w, h, new Color(19, 8, 4)));

            g2.fillRect(0, 0, w, h);

            for (int y = 0; y < h; y += 28) {
                g2.setColor(new Color(255, 195, 120, 18));
                g2.fillRect(0, y, w, 2);
            }

            for (int i = 0; i < 45; i++) {
                int x = (i * 137 + 31) % Math.max(1, w);
                int y = (i * 83 + 17) % Math.max(1, h);

                g2.setColor(new Color(255, 215, 145, 85));
                g2.fillOval(x, y, 3 + i % 3, 3 + i % 3);
            }

            g2.setColor(new Color(255, 204, 120));
            g2.setStroke(new BasicStroke(5));
            g2.drawRoundRect(12, 12, w - 24, h - 24, 32, 32);

            g2.setColor(new Color(255, 232, 190, 160));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(23, 23, w - 46, h - 46, 25, 25);

            g2.dispose();
        }
    }

    // ==================================================
    // WINNING SCREEN
    // ==================================================

    private void createWinScreen() {
        victoryPanel = new VictoryPanel();
        victoryPanel.setLayout(new GridBagLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel badge = centeredLabel(
                "✦  THE CLASSIC COLLECTION  ✦",
                new Font("Serif", Font.BOLD, 14),
                new Color(255, 225, 160));

        JLabel trophy = centeredLabel(
                "🏆", new Font("Segoe UI Emoji", Font.PLAIN, 65),
                Color.WHITE);

        JLabel title = centeredLabel(
                "PUZZLE COMPLETED!",
                new Font("Serif", Font.BOLD, 35),
                new Color(255, 225, 150));

        JLabel subtitle = centeredLabel(
                "BRILLIANCE UNLOCKED!",
                new Font("Serif", Font.BOLD, 21),
                Color.WHITE);

        JLabel description = centeredLabel(
                "Every tile found its place. Beautifully done!",
                new Font("Serif", Font.ITALIC, 14),
                new Color(255, 235, 210));

        // Results card
        JPanel results = new JPanel(new GridLayout(2, 1, 5, 8));
        results.setOpaque(false);
        results.setMaximumSize(new Dimension(360, 100));
        results.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(255, 215, 135), 2),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));

        winMovesLabel = new JLabel(
                "TOTAL MOVES: 0", SwingConstants.CENTER);
        winTimeLabel = new JLabel(
                "COMPLETION TIME: 00:00", SwingConstants.CENTER);
        winThemeLabel = new JLabel(
                "THEME: WOODEN", SwingConstants.CENTER);

        styleWinLabel(winMovesLabel);
        styleWinLabel(winTimeLabel);
        styleWinLabel(winThemeLabel);

        results.add(winMovesLabel);
        results.add(winTimeLabel);

        // Winning page buttons
        JButton playAgain = createButton(
                "↻   PLAY AGAIN", new Color(30, 150, 75));
        playAgain.setMaximumSize(new Dimension(310, 48));
        playAgain.setAlignmentX(Component.CENTER_ALIGNMENT);
        playAgain.addActionListener(e -> {
            pageLayout.show(mainContainer, "GAME");
            startNewGame();
        });

        JButton backToGame = createButton(
                "↩   BACK TO GAME", new Color(25, 115, 190));
        backToGame.setMaximumSize(new Dimension(310, 48));
        backToGame.setAlignmentX(Component.CENTER_ALIGNMENT);
        backToGame.addActionListener(e -> {
            pageLayout.show(mainContainer, "GAME");
            scene.repaint();
        });

        JButton home = createButton(
                "⌂   BACK TO HOME", new Color(140, 80, 40));
        home.setMaximumSize(new Dimension(310, 48));
        home.setAlignmentX(Component.CENTER_ALIGNMENT);
        home.addActionListener(e -> goHome());

        JLabel footer = centeredLabel(
                "A LITTLE PATIENCE. A BRILLIANT SOLUTION.",
                new Font("Serif", Font.ITALIC, 12),
                new Color(255, 220, 165));

        content.add(badge);
        content.add(Box.createVerticalStrut(10));
        content.add(trophy);
        content.add(Box.createVerticalStrut(5));
        content.add(title);
        content.add(Box.createVerticalStrut(5));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(8));
        content.add(description);
        content.add(Box.createVerticalStrut(15));
        content.add(results);
        content.add(Box.createVerticalStrut(7));
        content.add(winThemeLabel);
        content.add(Box.createVerticalStrut(14));
        content.add(playAgain);
        content.add(Box.createVerticalStrut(8));
        content.add(backToGame);
        content.add(Box.createVerticalStrut(8));
        content.add(home);
        content.add(Box.createVerticalStrut(12));
        content.add(footer);

        victoryPanel.add(content);
        mainContainer.add(victoryPanel, "WIN");
    }

    private void styleWinLabel(JLabel label) {
        label.setFont(new Font("Serif", Font.BOLD, 17));
        label.setForeground(new Color(255, 240, 195));
    }

    private class VictoryPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setPaint(new GradientPaint(
                    0, 0, themeColors[currentTheme][0],
                    w, h, themeColors[currentTheme][1]));

            g2.fillRect(0, 0, w, h);

            Color[] confettiColors = {
                    new Color(255, 220, 90),
                    new Color(255, 110, 130),
                    new Color(100, 255, 190),
                    new Color(150, 200, 255),
                    new Color(255, 255, 230)
            };

            int offset = (int) (System.currentTimeMillis() / 35);

            for (int i = 0; i < 95; i++) {
                int x = Math.floorMod(
                        i * 97 + 23, Math.max(1, w));
                int y = Math.floorMod(
                        i * 67 + offset + 15, Math.max(1, h));

                g2.setColor(confettiColors[i % confettiColors.length]);

                if (i % 2 == 0) {
                    g2.fillOval(x, y, 5 + i % 4, 5 + i % 4);
                } else {
                    g2.fillRoundRect(x, y, 5, 10, 3, 3);
                }
            }

            g2.setColor(themeColors[currentTheme][2]);
            g2.setStroke(new BasicStroke(5));
            g2.drawRoundRect(12, 12, w - 24, h - 24, 30, 30);

            g2.setColor(new Color(255, 255, 255, 110));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(23, 23, w - 46, h - 46, 24, 24);

            g2.dispose();
        }
    }

    // ==================================================
    // NAVIGATION
    // ==================================================

    private void openGame() {
        pageLayout.show(mainContainer, "GAME");

        if (!gameInitialized) {
            startNewGame();
        } else if (!gameWon) {
            gameTimer.start();
            messageLabel.setText(
                    "Welcome back! Continue your puzzle.");
        }

        scene.repaint();
    }

    private void goHome() {
        gameTimer.stop();
        pageLayout.show(mainContainer, "SPLASH");
    }

    private void showWinScreen() {
        gameWon = true;
        gameTimer.stop();

        winMovesLabel.setText("TOTAL MOVES: " + moves);
        winTimeLabel.setText("COMPLETION TIME: " + formatTime());
        winThemeLabel.setText("THEME: " + themeNames[currentTheme]);

        pageLayout.show(mainContainer, "WIN");
        victoryPanel.repaint();

        // Play the upgraded victory fanfare.
        playWinSound();
    }

    // ==================================================
    // GAME HEADER
    // ==================================================

    private void createHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.add(centeredLabel(
                "✦  THE CLASSIC COLLECTION  ✦",
                new Font("Serif", Font.BOLD, 13),
                new Color(255, 235, 180)));

        header.add(Box.createVerticalStrut(5));

        header.add(centeredLabel(
                "NUMBER PUZZLE",
                new Font("Serif", Font.BOLD, 36),
                new Color(255, 225, 150)));

        header.add(Box.createVerticalStrut(4));

        header.add(centeredLabel(
                "THINK  •  SLIDE  •  SOLVE",
                new Font("Serif", Font.ITALIC, 16),
                Color.WHITE));

        scene.add(header, BorderLayout.NORTH);
    }

    // ==================================================
    // PUZZLE BOARD
    // ==================================================

    private void createBoard() {
        boardFrame = new BoardPanel();
        boardFrame.setLayout(new GridLayout(SIZE, SIZE, 10, 10));
        boardFrame.setPreferredSize(new Dimension(390, 390));
        boardFrame.setBorder(new EmptyBorder(16, 16, 16, 16));

        for (int i = 0; i < TOTAL; i++) {
            final int index = i;

            tileButtons[i] = new TileButton(index);
            tileButtons[i].addActionListener(e -> {
                playClickSound();
                moveTile(index);
            });

            boardFrame.add(tileButtons[i]);
        }

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(boardFrame);

        scene.add(center, BorderLayout.CENTER);
    }

    // ==================================================
    // FOOTER AND CONTROLS
    // ==================================================

    private void createFooter() {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JPanel stats = new JPanel(new GridLayout(1, 2, 12, 0));
        stats.setOpaque(false);

        movesValueLabel = new JLabel("0", SwingConstants.CENTER);
        timeValueLabel = new JLabel("00:00", SwingConstants.CENTER);

        stats.add(createStatusPanel(
                "▦", "MOVES MADE", movesValueLabel,
                new Color(255, 190, 90)));

        stats.add(createStatusPanel(
                "◷", "TIME ELAPSED", timeValueLabel,
                new Color(90, 225, 255)));

        messageLabel = centeredLabel(
                "Arrange the tiles from 1 to 8.",
                new Font("Serif", Font.BOLD, 14),
                new Color(255, 245, 210));

        JButton newGame = createButton(
                "▶ NEW GAME", new Color(25, 145, 75));

        JButton restart = createButton(
                "⟳ RESTART", new Color(20, 115, 190));

        JButton home = createButton(
                "⌂ HOME", new Color(135, 80, 35));

        newGame.addActionListener(e -> startNewGame());
        restart.addActionListener(e -> restartGame());
        home.addActionListener(e -> goHome());

        JPanel controls = new JPanel(new GridLayout(1, 3, 10, 0));
        controls.setOpaque(false);
        controls.add(newGame);
        controls.add(restart);
        controls.add(home);

        JLabel themeTitle = centeredLabel(
                "✦  CHOOSE YOUR THEME  ✦",
                new Font("Serif", Font.BOLD, 13),
                new Color(255, 235, 190));

        footer.add(stats);
        footer.add(Box.createVerticalStrut(8));
        footer.add(messageLabel);
        footer.add(Box.createVerticalStrut(9));
        footer.add(controls);
        footer.add(Box.createVerticalStrut(9));
        footer.add(themeTitle);
        footer.add(Box.createVerticalStrut(6));
        footer.add(createThemeSelector());

        scene.add(footer, BorderLayout.SOUTH);
    }

    private JPanel createStatusPanel(String icon, String heading,
                                     JLabel valueLabel, Color accent) {

        JPanel panel = new JPanel(new BorderLayout(10, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                g2.setColor(new Color(0, 0, 0, 75));
                g2.fillRoundRect(4, 5, w - 8, h - 8, 24, 24);

                g2.setPaint(new GradientPaint(
                        0, 0, new Color(255, 255, 255, 35),
                        0, h, new Color(0, 0, 0, 85)));

                g2.fillRoundRect(2, 2, w - 7, h - 8, 24, 24);

                g2.setColor(accent);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(3, 3, w - 9, h - 10, 23, 23);

                g2.fillRoundRect(12, 13, 4,
                        Math.max(12, h - 30), 4, 4);

                g2.dispose();
            }
        };

        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(9, 12, 9, 8));

        JLabel iconLabel = new JLabel(
                icon, SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Symbol", Font.BOLD, 25));
        iconLabel.setForeground(accent);
        iconLabel.setPreferredSize(new Dimension(38, 42));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        headingLabel.setForeground(new Color(225, 225, 235));

        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 27));
        valueLabel.setForeground(Color.WHITE);

        textPanel.add(headingLabel);
        textPanel.add(Box.createVerticalStrut(1));
        textPanel.add(valueLabel);

        panel.add(iconLabel, BorderLayout.WEST);
        panel.add(textPanel, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(200, 76));
        panel.setMinimumSize(new Dimension(120, 70));

        return panel;
    }

    // ==================================================
    // THEME SELECTOR
    // ==================================================

    private JPanel createThemeSelector() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 7, 7));
        panel.setOpaque(false);

        for (int i = 0; i < themeNames.length; i++) {
            final int theme = i;

            JButton button = new ModernButton(
                    themeNames[i], themeColors[i][0]);

            button.setFont(new Font("SansSerif", Font.BOLD, 11));
            button.setForeground(Color.WHITE);
            button.setBackground(themeColors[i][0]);

            button.addActionListener(e -> applyTheme(theme));
            panel.add(button);
        }

        return panel;
    }

    private void applyTheme(int theme) {
        currentTheme = theme;

        scene.repaint();
        boardFrame.repaint();
        victoryPanel.repaint();

        for (TileButton button : tileButtons) {
            button.repaint();
        }

        messageLabel.setText(
                themeNames[theme] + " THEME SELECTED!");
    }

    // ==================================================
    // MODERN ROUNDED BUTTON
    // ==================================================

    private class ModernButton extends JButton {
        private Color baseColor;
        private boolean hovered;
        private boolean pressed;

        ModernButton(String text, Color color) {
            super(text);

            baseColor = color;
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setForeground(Color.WHITE);
            setBackground(color);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 14, 10, 14));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        pressed = true;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        @Override
        public void setBackground(Color color) {
            super.setBackground(color);
            if (color != null) {
                baseColor = color;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (w <= 2 || h <= 2) {
                g2.dispose();
                return;
            }

            int inset = pressed ? 2 : 0;
            int arc = Math.min(22, Math.max(12, h / 3));
            int x = 2 + inset;
            int y = pressed ? 3 : 1;
            int bw = w - 5 - inset * 2;
            int bh = h - 7 - inset;

            if (!pressed) {
                g2.setColor(new Color(0, 0, 0, 65));
                g2.fillRoundRect(x + 1, y + 4, bw, bh, arc, arc);
            }

            Color base = baseColor == null
                    ? new Color(65, 105, 160) : baseColor;

            Color top = hovered ? base.brighter() : base;

            g2.setPaint(new GradientPaint(
                    0, y, top, 0, y + bh, base.darker()));

            g2.fillRoundRect(x, y, bw, bh, arc, arc);

            g2.setColor(new Color(
                    255, 255, 255, hovered ? 105 : 55));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(x + 1, y + 1,
                    bw - 2, bh - 2, arc, arc);

            if (hovered && !pressed) {
                g2.setColor(new Color(255, 255, 255, 45));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x + 2, y + 2,
                        bw - 4, bh - 4, arc - 2, arc - 2);
            }

            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());

            String label = getText();
            int tx = (w - fm.stringWidth(label)) / 2;
            int ty = (h - fm.getHeight()) / 2
                    + fm.getAscent() + (pressed ? 1 : 0);

            g2.setColor(new Color(0, 0, 0, 75));
            g2.drawString(label, tx + 1, ty + 2);

            g2.setColor(getForeground());
            g2.drawString(label, tx, ty);

            g2.dispose();
        }
    }

    private JButton createButton(String text, Color color) {
        JButton button = new ModernButton(text, color);

        button.setFont(new Font("Serif", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // One click sound per normal button action.
        button.addActionListener(e -> playClickSound());

        return button;
    }

    // ==================================================
    // TIMERS
    // ==================================================

    private void createTimers() {
        gameTimer = new Timer(1000, e -> {
            seconds++;
            updateStatus();
        });

        gameTimer.setInitialDelay(1000);

        animationTimer = new Timer(50, e -> {
            if (scene != null) scene.repaint();
            if (victoryPanel != null) victoryPanel.repaint();
        });

        animationTimer.start();
    }

    // ==================================================
    // NEW GAME
    // ==================================================

    private void startNewGame() {
        moves = 0;
        seconds = 0;
        gameWon = false;
        gameInitialized = true;

        for (int i = 0; i < TOTAL - 1; i++) {
            tiles[i] = i + 1;
        }

        tiles[TOTAL - 1] = 0;

        // Shuffle only through legal moves.
        int empty = TOTAL - 1;
        int previousEmpty = -1;

        for (int i = 0; i < 150; i++) {
            int[] neighbors = getNeighbors(empty);
            int next;

            do {
                next = neighbors[random.nextInt(neighbors.length)];
            } while (next == previousEmpty && neighbors.length > 1);

            tiles[empty] = tiles[next];
            tiles[next] = 0;

            previousEmpty = empty;
            empty = next;
        }

        if (isSolved()) {
            int next = getNeighbors(TOTAL - 1)[0];
            tiles[TOTAL - 1] = tiles[next];
            tiles[next] = 0;
        }

        System.arraycopy(tiles, 0, startingBoard, 0, TOTAL);

        gameTimer.stop();
        gameTimer.start();

        messageLabel.setText("Arrange the tiles from 1 to 8.");

        refreshBoard();
        updateStatus();
    }

    // ==================================================
    // RESTART
    // ==================================================

    private void restartGame() {
        if (!gameInitialized) {
            startNewGame();
            return;
        }

        System.arraycopy(startingBoard, 0, tiles, 0, TOTAL);

        moves = 0;
        seconds = 0;
        gameWon = false;

        gameTimer.stop();
        gameTimer.start();

        messageLabel.setText("Puzzle restarted. Try again!");

        refreshBoard();
        updateStatus();
    }

    // ==================================================
    // TILE MOVEMENT
    // ==================================================

    private void moveTile(int index) {
        if (gameWon) return;

        int empty = findEmpty();

        int row = index / SIZE;
        int col = index % SIZE;
        int emptyRow = empty / SIZE;
        int emptyCol = empty % SIZE;

        if (Math.abs(row - emptyRow)
                + Math.abs(col - emptyCol) == 1) {

            tiles[empty] = tiles[index];
            tiles[index] = 0;

            moves++;

            refreshBoard();
            updateStatus();

            if (isSolved()) {
                showWinScreen();
            }
        }
    }

    private int findEmpty() {
        for (int i = 0; i < TOTAL; i++) {
            if (tiles[i] == 0) return i;
        }
        return -1;
    }

    private int[] getNeighbors(int index) {
        int row = index / SIZE;
        int col = index % SIZE;

        int[] temp = new int[4];
        int count = 0;

        if (row > 0) temp[count++] = index - SIZE;
        if (row < SIZE - 1) temp[count++] = index + SIZE;
        if (col > 0) temp[count++] = index - 1;
        if (col < SIZE - 1) temp[count++] = index + 1;

        int[] result = new int[count];
        System.arraycopy(temp, 0, result, 0, count);

        return result;
    }

    // ==================================================
    // WIN CHECK AND STATUS
    // ==================================================

    private boolean isSolved() {
        for (int i = 0; i < TOTAL - 1; i++) {
            if (tiles[i] != i + 1) return false;
        }

        return tiles[TOTAL - 1] == 0;
    }

    private void updateStatus() {
        if (movesValueLabel != null) {
            movesValueLabel.setText(String.valueOf(moves));
        }

        if (timeValueLabel != null) {
            timeValueLabel.setText(formatTime());
        }
    }

    private String formatTime() {
        return String.format("%02d:%02d",
                seconds / 60, seconds % 60);
    }

    private void refreshBoard() {
        for (TileButton button : tileButtons) {
            button.repaint();
        }

        boardFrame.repaint();
    }

    // ==================================================
    // GAME BACKGROUND
    // ==================================================

    private class ScenePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            Color top = themeColors[currentTheme][0];
            Color bottom = themeColors[currentTheme][1];

            g2.setPaint(new GradientPaint(
                    0, 0, top, getWidth(), getHeight(), bottom));

            g2.fillRect(0, 0, getWidth(), getHeight());

            switch (currentTheme) {
                case 0:
                    paintForest(g2, true);
                    break;
                case 1:
                    paintOcean(g2);
                    break;
                case 2:
                    paintForest(g2, false);
                    break;
                case 3:
                    paintGalaxy(g2);
                    break;
                case 4:
                    paintNeon(g2);
                    break;
                case 5:
                    paintPink(g2);
                    break;
            }

            g2.dispose();
        }
    }

    private void paintOcean(Graphics2D g2) {
        int w = scene.getWidth();

        g2.setColor(new Color(120, 245, 255, 100));

        for (int i = 0; i < 10; i++) {
            int x = (i * 95 + (int) (
                    System.currentTimeMillis() / 35
                            % Math.max(1, w))) % Math.max(1, w);

            g2.drawArc(x, 50 + i * 45, 55, 20, 0, 180);
        }

        g2.setColor(new Color(110, 245, 255, 170));
        g2.fillOval(w - 80, 30, 28, 28);
    }

    private void paintForest(Graphics2D g2, boolean wooden) {
        int w = scene.getWidth();
        int h = scene.getHeight();

        g2.setColor(wooden
                ? new Color(70, 40, 20, 90)
                : new Color(20, 100, 45, 120));

        for (int x = 0; x < w; x += 50) {
            Polygon tree = new Polygon();
            tree.addPoint(x, h);
            tree.addPoint(x + 25, h - 100);
            tree.addPoint(x + 50, h);
            g2.fillPolygon(tree);
        }

        g2.setColor(new Color(255, 225, 140, 180));
        g2.fillOval(w - 90, 25, 40, 40);
    }

    private void paintGalaxy(Graphics2D g2) {
        int w = scene.getWidth();
        int h = scene.getHeight();

        for (int i = 0; i < 80; i++) {
            int x = (i * 71 + 23) % Math.max(1, w);
            int y = (i * 47 + 13) % Math.max(1, h);

            g2.setColor(new Color(
                    255, 255, 255, 120 + (i % 4) * 30));

            g2.fillOval(x, y, (i % 3) + 2, (i % 3) + 2);
        }

        g2.setColor(new Color(190, 130, 255, 70));
        g2.fillOval(w / 3, h / 3, 100, 100);
    }

    private void paintNeon(Graphics2D g2) {
        int w = scene.getWidth();
        int h = scene.getHeight();

        g2.setColor(new Color(0, 255, 225, 65));
        g2.setStroke(new BasicStroke(1.5f));

        for (int x = 0; x < w; x += 45) {
            g2.drawLine(x, 0, x, h);
        }

        for (int y = 0; y < h; y += 45) {
            g2.drawLine(0, y, w, y);
        }
    }

    private void paintPink(Graphics2D g2) {
        int w = scene.getWidth();
        int h = scene.getHeight();

        g2.setColor(new Color(255, 235, 245, 130));

        for (int i = 0; i < 25; i++) {
            int x = (i * 59 + 13) % Math.max(1, w);
            int y = (i * 73 + 20) % Math.max(1, h);

            g2.fillOval(x, y, 8, 8);
        }

        g2.setColor(new Color(255, 220, 238, 110));
        g2.fillOval(w - 100, 25, 45, 45);
    }

    // ==================================================
    // PUZZLE BOARD FRAME
    // ==================================================

    private class BoardPanel extends JPanel {
        BoardPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRoundRect(5, 7, w - 12, h - 12, 30, 30);

            g2.setPaint(new GradientPaint(
                    0, 0, themeColors[currentTheme][2],
                    w, h, themeColors[currentTheme][1]));

            g2.fillRoundRect(0, 0, w - 5, h - 5, 30, 30);

            g2.setColor(themeColors[currentTheme][2]);
            g2.setStroke(new BasicStroke(4));
            g2.drawRoundRect(2, 2, w - 9, h - 10, 28, 28);

            g2.setColor(new Color(255, 255, 255, 65));
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(8, 8, w - 21, h - 22, 23, 23);

            g2.dispose();
        }
    }

    // ==================================================
    // NUMBER TILE
    // ==================================================

    private class TileButton extends JButton {
        private final int index;
        private boolean hovered = false;

        TileButton(int index) {
            this.index = index;

            setPreferredSize(new Dimension(110, 110));
            setMinimumSize(new Dimension(65, 65));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int number = tiles[index];

            if (number != 0) {
                Color base = themeColors[currentTheme][3];

                if (hovered) base = base.brighter();

                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRoundRect(5, 6, w - 11, h - 11, 22, 22);

                g2.setPaint(new GradientPaint(
                        0, 0, base.brighter(),
                        0, h, base.darker()));

                g2.fillRoundRect(2, 2, w - 6, h - 7, 22, 22);

                g2.setColor(themeColors[currentTheme][2]);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(3, 3, w - 8, h - 9, 22, 22);

                int fontSize = Math.max(24, Math.min(w, h) / 2);
                g2.setFont(new Font("Serif", Font.BOLD, fontSize));

                String text = String.valueOf(number);
                FontMetrics fm = g2.getFontMetrics();

                int textX = (w - fm.stringWidth(text)) / 2;
                int textY = (h - fm.getHeight()) / 2
                        + fm.getAscent();

                g2.setColor(new Color(0, 0, 0, 130));
                g2.drawString(text, textX + 2, textY + 2);

                g2.setColor(Color.WHITE);
                g2.drawString(text, textX, textY);

            } else {
                g2.setColor(new Color(0, 0, 0, 70));
                g2.fillRoundRect(2, 2, w - 6, h - 7, 22, 22);

                g2.setColor(new Color(255, 255, 255, 100));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(3, 3, w - 8, h - 9, 22, 22);
            }

            g2.dispose();
        }
    }

    // ==================================================
    // MAIN METHOD
    // ==================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NumberPuzzleGame game = new NumberPuzzleGame();
            game.setVisible(true);
        });
    }
}
