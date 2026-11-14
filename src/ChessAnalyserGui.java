import java.awt.RadialGradientPaint;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class ChessAnalyserGui extends JFrame {

    private Color lightSquare = new Color(235, 236, 238);
    private Color darkSquare = new Color(138, 148, 158);
    private Color frameBackground = new Color(24, 25, 28);
    private Color surfaceColor = new Color(34, 36, 40);
    private Color borderColor = new Color(58, 61, 68);
    private Color textForeground = new Color(232, 234, 237);
    private Color mutedText = new Color(154, 160, 166);
    private Color whitePieceColor = new Color(248, 249, 250);
    private Color blackPieceColor = new Color(24, 25, 28);

    private Color fromHighlightColor = new Color(225, 215, 140, 150);
    private Color toHighlightColor = new Color(110, 175, 135, 175);

    private static final Color MATE_KING_DANGER = new Color(195, 65, 65, 165);
    private static final int SQUARE_SIZE = 72;
    private static final int BOARD_SIZE = 8;
    private static final String SETTINGS_FILE = "chess_settings.cfg";

    private final ChessBoardModel model = new ChessBoardModel();
    private final SquareButton[][] squares = new SquareButton[BOARD_SIZE][BOARD_SIZE];
    private Font pieceFont = new Font("SansSerif", Font.PLAIN, 52);

    private final Stack undoStack = new Stack();
    private final Stack moveHistoryUndoStack = new Stack();
    private final List playedUciMoves = new ArrayList();
    private String currentOpeningText = "Starting Position";
    private String currentMoveSequenceText = "Play a move to detect opening or defense";

    // Move Accuracy Tracking (White & Black)
    private final List whiteMoveAccuracies = new ArrayList();
    private final List blackMoveAccuracies = new ArrayList();
    private final Stack whiteAccUndoStack = new Stack();
    private final Stack blackAccUndoStack = new Stack();
    private double lastKnownWhiteWinPct = 52.0;
    private boolean pendingMoveGrade = false;
    private boolean lastMoverWasWhite = true;
    private double preMoveWhiteWinPct = 52.0;
    private String lastMoveGradeLabel = "No moves graded yet";

    private final JPanel rootPanel = new JPanel(new BorderLayout(16, 16));
    private final List themeableLabels = new ArrayList();
    private final List themeableChecks = new ArrayList();
    private final JPanel sidebarPanel = new JPanel(new BorderLayout(0, 8));
    private final JTabbedPane tabs = new JTabbedPane();
    private JPanel engineTabPanel;
    private JPanel customTabPanel;
    private JScrollPane logScrollPane;

    // Opening & Defense Header Bar (Above Chessboard)
    private final JPanel openingHeaderCard = new JPanel(new BorderLayout(8, 2));
    private final JLabel openingBookBadge = new JLabel("BOOK");
    private final JLabel openingTitleLabel = new JLabel("Starting Position");
    private final JLabel openingMovesLabel = new JLabel("Play a move to detect opening or defense");

    // Accuracy Scoreboard Card
    private final JPanel accuracyCard = new JPanel(new GridLayout(2, 2, 8, 2));
    private final JLabel myAccuracyLabel = new JLabel("My Accuracy: -");
    private final JLabel enemyAccuracyLabel = new JLabel("Enemy Accuracy: -");
    private final JLabel lastMoveGradeDisplay = new JLabel("Last: -");
    private final JLabel moveCountDisplay = new JLabel("Moves: 0");

    // Live Captured Pieces Trays (Top & Bottom of Chessboard)
    private final JPanel topCaptureStrip = new JPanel(new BorderLayout(6, 0));
    private final JLabel topCaptureSideLabel = new JLabel("Black Captures:");
    private final JLabel topCapturedPiecesLabel = new JLabel("-");
    private final JLabel topMaterialLeadLabel = new JLabel("");

    private final JPanel bottomCaptureStrip = new JPanel(new BorderLayout(6, 0));
    private final JLabel bottomCaptureSideLabel = new JLabel("White Captures:");
    private final JLabel bottomCapturedPiecesLabel = new JLabel("-");
    private final JLabel bottomMaterialLeadLabel = new JLabel("");

    private final JTextField pathField = new JTextField();
    private final JSpinner depthSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 40, 1));
    private final JSpinner moveTimeSpinner = new JSpinner(new SpinnerNumberModel(1200, 50, 120_000, 50));
    private final JSpinner skillSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 20, 1));
    private final JLabel skillDescLabel = new JLabel("Max (Full)");
    private final JCheckBox limitEloCheck = new JCheckBox("Target Elo");
    private final JTextField eloField = new JTextField("1500", 5);
    private final JSpinner whiteBotEloSpinner = new JSpinner(new SpinnerNumberModel(1350, 1320, 3190, 50));
    private final JSpinner blackBotEloSpinner = new JSpinner(new SpinnerNumberModel(2400, 1320, 3190, 50));
    private final JComboBox eloPresetChooser = new JComboBox(new String[]{
            "Custom", "1320 (Casual)", "1500 (Club)", "1800 (Tournament)",
            "2000 (Expert)", "2200 (National Master)", "2400 (IM)",
            "2600 (Grandmaster)", "2850 (Super GM)", "3190 (Max Engine)"
    });
    private final JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 16, 1));
    private final JSpinner hashSpinner = new JSpinner(new SpinnerNumberModel(64, 16, 1024, 16));

    private final JTextArea logArea = new JTextArea(10, 32);
    private final JLabel mateAlertBanner = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Engine idle.");
    private final JLabel evalLabel = new JLabel("Eval: +0.00 (Depth 0)");
    private final JLabel pvLabel = new JLabel("Best Line: -");
    private final EvalBarPanel evalBar = new EvalBarPanel();

    private final JButton analyzeButton = new JButton("Calculate Best Move");
    private final JButton autoplayButton = new JButton("Auto-Play Best Move");
    private final JButton flipBoardButton = new JButton("Flip Board");
    private final JButton undoMoveButton = new JButton("Undo");
    private final JButton resetBoardButton = new JButton("Reset");

    private final JComboBox gameModeChooser = new JComboBox(new String[]{
            "Analysis Mode (Manual)",
            "Play vs. Stockfish (Auto-Reply)",
            "Stockfish vs. Stockfish (Watch)"
    });

    private final JComboBox myTeamChooser = new JComboBox(new String[]{"Play as White", "Play as Black"});
    private final JComboBox paintPieceChooser = new JComboBox(new String[]{
            "Move Mode (Normal)", "Eraser (Clear Square)",
            "White King (♔)", "White Queen (♕)", "White Rook (♖)", "White Bishop (♗)", "White Knight (♘)", "White Pawn (♙)",
            "Black King (♚)", "Black Queen (♛)", "Black Rook (♜)", "Black Bishop (♝)", "Black Knight (♞)", "Black Pawn (♟)"
    });

    // Appearance Controls
    private final JComboBox boardChooser = new JComboBox(new String[]{
            "1. Minimal Slate", "2. Classic Walnut", "3. Tournament Sage", "4. Nordic Ice", "5. Charcoal Matte",
            "6. Warm Alabaster", "7. Deep Graphite", "8. Soft Sand", "9. Muted Olive", "10. Studio Ash"
    });
    private final JButton customLightBtn = new JButton("Light Square");
    private final JButton customDarkBtn = new JButton("Dark Square");

    private final JComboBox bgChooser = new JComboBox(new String[]{
            "Obsidian Matte", "Sleek Dark Slate", "Clean Paper White", "Soft Warm Gray", "Midnight Charcoal"
    });
    private final JButton customBgBtn = new JButton("Pick Background");
    private final JComboBox textColorChooser = new JComboBox(new String[]{
            "Auto (Match Theme)", "Soft White", "Charcoal Ink", "Cool Silver", "Warm Ivory"
    });
    private final JButton customTextColorBtn = new JButton("Pick Text");
    private final JComboBox uiFontChooser = new JComboBox(new String[]{
            "SansSerif", "Segoe UI", "Serif", "Monospaced"
    });

    private final JComboBox pieceStyleChooser = new JComboBox(new String[]{
            "Solid Filled (Dual-Color)", "Outlined Dual-Tone", "Classic Unicode (♔/♚)", "Minimal Badges (K/Q/R)"
    });
    private final JComboBox pieceThemeChooser = new JComboBox(new String[]{
            "Ivory & Obsidian", "Pure White & Graphite", "Warm Cream & Espresso", "Silver & Charcoal", "Custom Colors"
    });
    private final JComboBox pieceGlowChooser = new JComboBox(new String[]{
            "Glow: Off",
            "Glow: CS2 Team (Cyan vs. Crimson)",
            "Glow: Tactical Threat (Safe / Danger)",
            "Glow: Cyberpunk (Neon Blue & Pink)",
            "Glow: Solar Gold & Violet"
    });

    private final JButton customWhitePieceBtn = new JButton("White Piece");
    private final JButton customBlackPieceBtn = new JButton("Black Piece");

    // Best Move Indicator Style & Colors
    private final JComboBox moveMarkerStyleChooser = new JComboBox(new String[]{
            "Chess.com Circle", "Square Fill"
    });
    private final JComboBox highlightThemeChooser = new JComboBox(new String[]{
            "Muted Sage", "Chess.com Green", "Cool Slate Blue", "Soft Amber", "Subtle Crimson", "Neutral Silver", "Custom Colors"
    });
    private final JButton customFromHighlightBtn = new JButton("From Color");
    private final JButton customToHighlightBtn = new JButton("To Color");
    private final JComboBox pieceFontChooser = new JComboBox(new String[]{
            "SansSerif (Clean)", "Serif (Classic)", "Dialog (Bold)"
    });
    private final JSlider pieceSizeSlider = new JSlider(38, 66, 52);
    private final JCheckBox showCoordsCheck = new JCheckBox("Coordinates", true);
    private final JCheckBox showEvalBarCheck = new JCheckBox("Eval Bar", true);
    private final JCheckBox soundEffectsCheck = new JCheckBox("Sound Effects", true);
    private final JComboBox soundThemeChooser = new JComboBox(new String[]{
            "Chess.com (Wood Thock)", "Lichess (Crisp Pop)", "Classic (Clean Tone)", "Marble (Heavy Stone)", "Retro (8-Bit Arcade)"
    });
    private final JCheckBox mateAlertsCheck = new JCheckBox("Mate Alerts", true);
    private final StockfishEngine engine = new StockfishEngine(new EngineCallbacks());
    private String connectedPath = "";
    private boolean analysisQueued;
    private String queuedFen = "";
    private int queuedDepth = 14;
    private int queuedMoveTime = 1200;
    private boolean currentAnalysisIsBackgroundEval = false;
    private boolean pendingEngineAutoReply = false;
    private boolean isFlipped = false;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int highlightFromRow = -1;
    private int highlightFromCol = -1;
    private int highlightToRow = -1;
    private int highlightToCol = -1;
    private String latestBestMove = "";
    private String lastPromotionSuffix = "";
    // On-Board Move Quality Badge State
    private int lastGradedRow = -1;
    private int lastGradedCol = -1;
    private String lastBadgeSymbol = "";
    private Color lastBadgeColor = new Color(45, 106, 79);
    // Smooth Piece Slide Animation State
    private JComponent boardAnimationLayer = null;
    private boolean isAnimatingMove = false;
    private String animatingPieceToken = "";
    private int animFromRow = -1;
    private int animFromCol = -1;
    private int animToRow = -1;
    private int animToCol = -1;
    private double animProgress = 0.0;
    private Timer pieceAnimTimer = null;
    // On-Board Game Over Banner State
    private boolean isGameOverOverlay = false;
    private String gameOverHeadline = "";
    private String gameOverSubtext = "";
    private Color gameOverAccentColor = new Color(102, 168, 128);

    // Live Bot Game Clock State
    private final JComboBox timeControlChooser = new JComboBox(new String[]{
            "Clock: Off", "1+0 Bullet", "3+0 Blitz", "3+2 Blitz", "5+0 Blitz", "10+0 Rapid"
    });
    private final JLabel whiteClockLabel = new JLabel("White: --:--", SwingConstants.CENTER);
    private final JLabel blackClockLabel = new JLabel("Black: --:--", SwingConstants.CENTER);
    private long whiteTimeMs = 180_000L;
    private long blackTimeMs = 180_000L;
    private long incrementMs = 0L;
    private boolean clockEnabled = false;
    private Timer gameClockTimer;

    private int activeMateThreat = 0;
    private int lastAnnouncedMate = 0;
    private final JPanel ranksPanel = new JPanel(new GridLayout(8, 1));
    private final JPanel filesPanel = new JPanel(new GridLayout(1, 8));
    private static final Map OPENING_BOOK = createOpeningBook();

    private static Map createOpeningBook() {
        Map book = new LinkedHashMap();
        book.put("e2e4", "B00 King's Pawn Opening");
        book.put("e2e4 e7e5", "C20 Open Game (King's Pawn Game)");
        book.put("e2e4 e7e5 g1f3", "C40 King's Knight Opening");
        book.put("e2e4 e7e5 g1f3 b8c6", "C44 King's Knight Opening: Normal Variation");
        book.put("e2e4 e7e5 g1f3 b8c6 f1c4", "C50 Italian Game");
        book.put("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5", "C50 Giuoco Piano");
        book.put("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3", "C53 Giuoco Piano: Main Line");
        book.put("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6", "C55 Two Knights Defense");
        book.put("e2e4 e7e5 g1f3 b8c6 f1b5", "C60 Ruy Lopez (Spanish Opening)");
        book.put("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6", "C68 Ruy Lopez: Morphy Defense");
        book.put("e2e4 e7e5 g1f3 b8c6 f1b5 g8f6", "C65 Ruy Lopez: Berlin Defense");
        book.put("e2e4 e7e5 g1f3 b8c6 d2d4", "C44 Scotch Game");
        book.put("e2e4 e7e5 g1f3 b8c6 b1c3 g8f6", "C46 Four Knights Game");
        book.put("e2e4 e7e5 g1f3 g8f6", "C42 Petrov's Defense (Russian Game)");
        book.put("e2e4 e7e5 g1f3 d7d6", "C41 Philidor Defense");
        book.put("e2e4 e7e5 f2f4", "C30 King's Gambit");
        book.put("e2e4 e7e5 f2f4 e5f4", "C33 King's Gambit Accepted");
        book.put("e2e4 e7e5 f1c4", "C23 Bishop's Opening");
        book.put("e2e4 e7e5 b1c3", "C25 Vienna Game");
        book.put("e2e4 c7c5", "B20 Sicilian Defense");
        book.put("e2e4 c7c5 g1f3", "B27 Sicilian Defense: Open");
        book.put("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6", "B90 Sicilian Defense: Najdorf Variation");
        book.put("e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6", "B70 Sicilian Defense: Dragon Variation");
        book.put("e2e4 c7c5 g1f3 e7e6", "B40 Sicilian Defense: French Variation");
        book.put("e2e4 c7c5 g1f3 b8c6", "B30 Sicilian Defense: Old Sicilian");
        book.put("e2e4 c7c5 c2c3", "B22 Sicilian Defense: Alapin Variation");
        book.put("e2e4 c7c5 b1c3", "B23 Sicilian Defense: Closed");
        book.put("e2e4 e7e6", "C00 French Defense");
        book.put("e2e4 e7e6 d2d4 d7d5", "C01 French Defense: Main Line");
        book.put("e2e4 e7e6 d2d4 d7d5 b1c3", "C03 French Defense: Paulsen / Classical");
        book.put("e2e4 e7e6 d2d4 d7d5 b1d2", "C03 French Defense: Tarrasch Variation");
        book.put("e2e4 e7e6 d2d4 d7d5 e4e5", "C02 French Defense: Advance Variation");
        book.put("e2e4 c7c6", "B10 Caro-Kann Defense");
        book.put("e2e4 c7c6 d2d4 d7d5", "B12 Caro-Kann Defense: Main Line");
        book.put("e2e4 c7c6 d2d4 d7d5 e4e5", "B12 Caro-Kann Defense: Advance Variation");
        book.put("e2e4 d7d5", "B01 Scandinavian Defense (Center Counter)");
        book.put("e2e4 d7d5 e4d5 d8d5", "B01 Scandinavian Defense: Main Line");
        book.put("e2e4 d7d6", "B07 Pirc Defense");
        book.put("e2e4 g7g6", "B06 Modern Defense");
        book.put("e2e4 g8f6", "B02 Alekhine's Defense");
        book.put("d2d4", "A40 Queen's Pawn Opening");
        book.put("d2d4 d7d5", "D00 Queen's Pawn Game");
        book.put("d2d4 d7d5 c2c4", "D06 Queen's Gambit");
        book.put("d2d4 d7d5 c2c4 e7e6", "D30 Queen's Gambit Declined (QGD)");
        book.put("d2d4 d7d5 c2c4 d5c4", "D20 Queen's Gambit Accepted (QGA)");
        book.put("d2d4 d7d5 c2c4 c7c6", "D10 Slav Defense");
        book.put("d2d4 d7d5 c1f4", "D00 London System");
        book.put("d2d4 d7d5 g1f3 g8f6 c1f4", "D02 London System: Classical");
        book.put("d2d4 g8f6", "A45 Indian Defense");
        book.put("d2d4 g8f6 c2c4 g7g6", "E60 King's Indian Defense");
        book.put("d2d4 g8f6 c2c4 g7g6 b1c3 f8g7", "E61 King's Indian Defense: Classical");
        book.put("d2d4 g8f6 c2c4 g7g6 b1c3 d7d5", "D80 Grünfeld Defense");
        book.put("d2d4 g8f6 c2c4 e7e6 b1c3 f8b4", "E20 Nimzo-Indian Defense");
        book.put("d2d4 g8f6 c2c4 e7e6 g1f3 b7b6", "E12 Queen's Indian Defense");
        book.put("d2d4 g8f6 c2c4 c7c5", "A56 Benoni Defense");
        book.put("d2d4 f7f5", "A80 Dutch Defense");
        book.put("c2c4", "A10 English Opening");
        book.put("c2c4 e7e5", "A20 English Opening: Reversed Sicilian");
        book.put("c2c4 c7c5", "A30 English Opening: Symmetrical Variation");
        book.put("g1f3", "A04 Réti Opening");
        book.put("g1f3 d7d5 c2c4", "A09 Réti Gambit");
        book.put("b2b3", "A01 Nimzo-Larsen Attack");
        book.put("f2f4", "A02 Bird's Opening");
        return book;
    }

    public ChessAnalyserGui() {
        super("ChessAnalyzer V1");
        setAlwaysOnTop(true);
        buildInterface();
        loadSavedSettings();
        applySleekTheme();
        refreshBoard();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveCurrentSettings();
                Thread shutdown = new Thread(engine::close, "engine-shutdown");
                shutdown.setDaemon(false);
                shutdown.start();
                dispose();
            }
        });
        setMinimumSize(new Dimension(1120, 910));
        pack();
        setLocationRelativeTo(null);
        applyThemeToComponentTree(getContentPane());
    }

    private void buildInterface() {
        rootPanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        rootPanel.setBackground(frameBackground);
        JPanel leftSection = new JPanel(new BorderLayout(10, 0));
        leftSection.setOpaque(false);
        leftSection.add(evalBar, BorderLayout.WEST);
        leftSection.add(buildBoard(), BorderLayout.CENTER);
        rootPanel.add(leftSection, BorderLayout.CENTER);
        buildSidebar();
        rootPanel.add(sidebarPanel, BorderLayout.EAST);
        setContentPane(rootPanel);
        getRootPane().setDefaultButton(analyzeButton);
    }

    private JPanel buildBoard() {
        JPanel board = new JPanel(new BorderLayout(6, 6));
        board.setOpaque(false);
        ranksPanel.setOpaque(false);
        filesPanel.setOpaque(false);
        int boardPx = SQUARE_SIZE * BOARD_SIZE;
// 1. Opening & Defense Banner
        openingHeaderCard.setOpaque(true);
        openingHeaderCard.setBackground(surfaceColor);
        openingHeaderCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(7, 10, 7, 10)
        ));
        openingBookBadge.setOpaque(true);
        openingBookBadge.setBackground(new Color(74, 112, 86));
        openingBookBadge.setForeground(new Color(242, 246, 243));
        openingBookBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        openingBookBadge.setBorder(new EmptyBorder(2, 6, 2, 6));
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        openingTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        openingTitleLabel.setForeground(textForeground);
        titleRow.add(openingBookBadge, BorderLayout.WEST);
        titleRow.add(openingTitleLabel, BorderLayout.CENTER);
        openingMovesLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        openingMovesLabel.setForeground(mutedText);
        openingHeaderCard.add(titleRow, BorderLayout.NORTH);
        openingHeaderCard.add(openingMovesLabel, BorderLayout.SOUTH);
// 2. Accuracy Scoreboard Card
        accuracyCard.setOpaque(true);
        accuracyCard.setBackground(surfaceColor);
        accuracyCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        myAccuracyLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        enemyAccuracyLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        lastMoveGradeDisplay.setFont(new Font("SansSerif", Font.PLAIN, 11));
        moveCountDisplay.setFont(new Font("SansSerif", Font.PLAIN, 11));
        moveCountDisplay.setHorizontalAlignment(SwingConstants.RIGHT);
        enemyAccuracyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        accuracyCard.add(myAccuracyLabel);
        accuracyCard.add(enemyAccuracyLabel);
        accuracyCard.add(lastMoveGradeDisplay);
        accuracyCard.add(moveCountDisplay);
        // Game Clock & Time Control Bar
        JPanel clockBar = new JPanel(new GridLayout(1, 3, 6, 0));
        clockBar.setOpaque(false);
        whiteClockLabel.setOpaque(true);
        whiteClockLabel.setBackground(new Color(45, 48, 54));
        whiteClockLabel.setForeground(textForeground);
        whiteClockLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        whiteClockLabel.setBorder(new EmptyBorder(3, 6, 3, 6));

        blackClockLabel.setOpaque(true);
        blackClockLabel.setBackground(new Color(45, 48, 54));
        blackClockLabel.setForeground(textForeground);
        blackClockLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        blackClockLabel.setBorder(new EmptyBorder(3, 6, 3, 6));

        timeControlChooser.addActionListener(e -> resetGameClockFromSelection());
        clockBar.add(whiteClockLabel);
        clockBar.add(blackClockLabel);
        clockBar.add(timeControlChooser);

        gameClockTimer = new Timer(100, e -> tickGameClock());
        gameClockTimer.start();

        // 3. Stacked Captured Pieces Strips (Black Captures + White Captures)
        styleCaptureStrip(topCaptureStrip, topCaptureSideLabel, topCapturedPiecesLabel, topMaterialLeadLabel);
        styleCaptureStrip(bottomCaptureStrip, bottomCaptureSideLabel, bottomCapturedPiecesLabel, bottomMaterialLeadLabel);

        JPanel combinedCaptures = new JPanel(new GridLayout(2, 1, 0, 4));
        combinedCaptures.setOpaque(false);
        combinedCaptures.add(topCaptureStrip);
        combinedCaptures.add(bottomCaptureStrip);

        JPanel midScoreAndClock = new JPanel(new BorderLayout(0, 4));
        midScoreAndClock.setOpaque(false);
        midScoreAndClock.add(accuracyCard, BorderLayout.CENTER);
        midScoreAndClock.add(clockBar, BorderLayout.SOUTH);

        JPanel stackedCards = new JPanel(new BorderLayout(0, 6));
        stackedCards.setOpaque(false);
        stackedCards.setPreferredSize(new Dimension(boardPx, 198));
        stackedCards.add(openingHeaderCard, BorderLayout.NORTH);
        stackedCards.add(midScoreAndClock, BorderLayout.CENTER);
        stackedCards.add(combinedCaptures, BorderLayout.SOUTH);
        JPanel topHeaderWrapper = new JPanel(new BorderLayout());

        topHeaderWrapper.setOpaque(false);
        topHeaderWrapper.add(javax.swing.Box.createHorizontalStrut(28), BorderLayout.WEST);
        topHeaderWrapper.add(stackedCards, BorderLayout.CENTER);

        JPanel grid = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE, 0, 0));
        grid.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        grid.setPreferredSize(new Dimension(boardPx, boardPx));
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int column = 0; column < BOARD_SIZE; column++) {
                SquareButton square = new SquareButton(row, column);
                squares[row][column] = square;
                grid.add(square);
            }
        }
        javax.swing.plaf.LayerUI<JPanel> animLayerUI = new javax.swing.plaf.LayerUI<JPanel>() {
            @Override
            public void paint(Graphics g, JComponent c) {
                super.paint(g, c);
                if (isAnimatingMove && animatingPieceToken != null && !animatingPieceToken.isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    int dispFromRow = isFlipped ? (7 - animFromRow) : animFromRow;
                    int dispFromCol = isFlipped ? (7 - animFromCol) : animFromCol;
                    int dispToRow = isFlipped ? (7 - animToRow) : animToRow;
                    int dispToCol = isFlipped ? (7 - animToCol) : animToCol;

                    double sqW = c.getWidth() / 8.0;
                    double sqH = c.getHeight() / 8.0;

                    double startX = (dispFromCol + 0.5) * sqW;
                    double startY = (dispFromRow + 0.5) * sqH;
                    double endX = (dispToCol + 0.5) * sqW;
                    double endY = (dispToRow + 0.5) * sqH;

                    double t = 1.0 - Math.pow(1.0 - animProgress, 3.0);
                    double curX = startX + (endX - startX) * t;
                    double curY = startY + (endY - startY) * t;

                    drawPieceWithGlowAt(g2, animatingPieceToken, (int) Math.round(curX), (int) Math.round(curY), (int) sqW, (int) sqH);
                    g2.dispose();
                }

                if (isGameOverOverlay && gameOverHeadline != null && !gameOverHeadline.isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    int w = c.getWidth();
                    int h = c.getHeight();

                    g2.setColor(new Color(12, 14, 18, 145));
                    g2.fillRect(0, 0, w, h);

                    int cardW = Math.min(360, (int) (w * 0.74));
                    int cardH = 128;
                    int cx = (w - cardW) / 2;
                    int cy = (h - cardH) / 2;

                    g2.setColor(new Color(24, 27, 34, 242));
                    g2.fillRoundRect(cx, cy, cardW, cardH, 18, 18);

                    g2.setStroke(new BasicStroke(2.5f));
                    g2.setColor(gameOverAccentColor);
                    g2.drawRoundRect(cx, cy, cardW, cardH, 18, 18);

                    g2.fillRoundRect(cx + 24, cy + 12, cardW - 48, 4, 4, 4);

                    g2.setFont(new Font("SansSerif", Font.BOLD, 30));
                    FontMetrics hfm = g2.getFontMetrics();
                    int hx = cx + (cardW - hfm.stringWidth(gameOverHeadline)) / 2;
                    int hy = cy + 58;
                    g2.setColor(gameOverAccentColor);
                    g2.drawString(gameOverHeadline, hx, hy);

                    g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
                    FontMetrics sfm = g2.getFontMetrics();
                    int sx = cx + (cardW - sfm.stringWidth(gameOverSubtext)) / 2;
                    int sy = cy + 90;
                    g2.setColor(new Color(235, 238, 245));
                    g2.drawString(gameOverSubtext, sx, sy);

                    String hint = "Click Reset or Undo to play again";
                    g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
                    FontMetrics ffm = g2.getFontMetrics();
                    g2.setColor(new Color(155, 162, 175));
                    g2.drawString(hint, cx + (cardW - ffm.stringWidth(hint)) / 2, cy + 114);

                    g2.dispose();
                }
            }
        };
        javax.swing.JLayer<JPanel> gridLayer = new javax.swing.JLayer<JPanel>(grid, animLayerUI);
        boardAnimationLayer = gridLayer;

        JPanel boardCenterWrapper = new JPanel(new GridBagLayout());
        boardCenterWrapper.setOpaque(false);
        boardCenterWrapper.add(gridLayer);


        // 4. File Coordinates (Below Board)
        JPanel filesWrapper = new JPanel(new BorderLayout());
        filesWrapper.setOpaque(false);
        filesWrapper.add(javax.swing.Box.createHorizontalStrut(28), BorderLayout.WEST);
        filesWrapper.add(filesPanel, BorderLayout.CENTER);

        board.add(topHeaderWrapper, BorderLayout.NORTH);
        board.add(ranksPanel, BorderLayout.WEST);
        board.add(boardCenterWrapper, BorderLayout.CENTER);
        board.add(filesWrapper, BorderLayout.SOUTH);
        updateLabels();
        return board;

    }

    private void styleCaptureStrip(JPanel strip, JLabel sideLabel, JLabel piecesLabel, JLabel leadLabel) {
        strip.setOpaque(true);
        strip.setBackground(surfaceColor);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(4, 10, 4, 10)
        ));
        sideLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        sideLabel.setForeground(mutedText);
        piecesLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        piecesLabel.setForeground(textForeground);
        leadLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        leadLabel.setForeground(new Color(110, 175, 135));
        strip.add(sideLabel, BorderLayout.WEST);
        strip.add(piecesLabel, BorderLayout.CENTER);
        strip.add(leadLabel, BorderLayout.EAST);
    }

    private void updateCapturedPiecesDisplay() {
        int wQ = 0, wR = 0, wB = 0, wN = 0, wP = 0;
        int bQ = 0, bR = 0, bB = 0, bN = 0, bP = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                String p = model.getPieceAt(r, c);
                if (p == null || p.isEmpty()) continue;
                switch (p.charAt(0)) {
                    case '♕':
                    case 'Q':
                        wQ++;
                        break;
                    case '♖':
                    case 'R':
                        wR++;
                        break;
                    case '♗':
                    case 'B':
                        wB++;
                        break;
                    case '♘':
                    case 'N':
                        wN++;
                        break;
                    case '♙':
                    case 'P':
                        wP++;
                        break;
                    case '♛':
                    case 'q':
                        bQ++;
                        break;
                    case '♜':
                    case 'r':
                        bR++;
                        break;
                    case '♝':
                    case 'b':
                        bB++;
                        break;
                    case '♞':
                    case 'n':
                        bN++;
                        break;
                    case '♟':
                    case 'p':
                        bP++;
                        break;
                }
            }
        }
        String whiteCapturedStr = repeatIcons("♛", Math.max(0, 1 - bQ))
                + repeatIcons("♜", Math.max(0, 2 - bR))
                + repeatIcons("♝", Math.max(0, 2 - bB))
                + repeatIcons("♞", Math.max(0, 2 - bN))
                + repeatIcons("♟", Math.max(0, 8 - bP));
        String blackCapturedStr = repeatIcons("♕", Math.max(0, 1 - wQ))
                + repeatIcons("♖", Math.max(0, 2 - wR))
                + repeatIcons("♗", Math.max(0, 2 - wB))
                + repeatIcons("♘", Math.max(0, 2 - wN))
                + repeatIcons("♙", Math.max(0, 8 - wP));
        int whitePoints = wQ * 9 + wR * 5 + wB * 3 + wN * 3 + wP;
        int blackPoints = bQ * 9 + bR * 5 + bB * 3 + bN * 3 + bP;
        int diff = whitePoints - blackPoints;
        String whiteLead = diff > 0 ? "+" + diff : "";
        String blackLead = diff < 0 ? "+" + Math.abs(diff) : "";
        if (!isFlipped) {
            topCaptureSideLabel.setText("Black Captures:");
            topCapturedPiecesLabel.setText(blackCapturedStr.isEmpty() ? "-" : blackCapturedStr);
            topMaterialLeadLabel.setText(blackLead);
            bottomCaptureSideLabel.setText("White Captures:");
            bottomCapturedPiecesLabel.setText(whiteCapturedStr.isEmpty() ? "-" : whiteCapturedStr);
            bottomMaterialLeadLabel.setText(whiteLead);
        } else {
            topCaptureSideLabel.setText("White Captures:");
            topCapturedPiecesLabel.setText(whiteCapturedStr.isEmpty() ? "-" : whiteCapturedStr);
            topMaterialLeadLabel.setText(whiteLead);
            bottomCaptureSideLabel.setText("Black Captures:");
            bottomCapturedPiecesLabel.setText(blackCapturedStr.isEmpty() ? "-" : blackCapturedStr);
            bottomMaterialLeadLabel.setText(blackLead);
        }
    }

    private String repeatIcons(String icon, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(icon);
        return sb.toString();
    }

    private void recordPlayedMove(int fRow, int fCol, int tRow, int tCol, boolean moverIsWhite) {
        moveHistoryUndoStack.push(new ArrayList(playedUciMoves));
        whiteAccUndoStack.push(new ArrayList(whiteMoveAccuracies));
        blackAccUndoStack.push(new ArrayList(blackMoveAccuracies));

        preMoveWhiteWinPct = lastKnownWhiteWinPct;
        lastMoverWasWhite = moverIsWhite;
        pendingMoveGrade = true;

        String uci = "" + (char) ('a' + fCol) + (char) ('8' - fRow)
                + (char) ('a' + tCol) + (char) ('8' - tRow)
                + lastPromotionSuffix;
        lastPromotionSuffix = "";
        playedUciMoves.add(uci);
        updateOpeningDisplay();
        updateAccuracyDisplay();
    }

    private void evaluateAndGradeLastMove(double newWhiteWinPct) {
        lastKnownWhiteWinPct = newWhiteWinPct;
        if (!pendingMoveGrade) return;
        pendingMoveGrade = false;

        double winLoss = lastMoverWasWhite
                ? Math.max(0.0, preMoveWhiteWinPct - newWhiteWinPct)
                : Math.max(0.0, (100.0 - preMoveWhiteWinPct) - (100.0 - newWhiteWinPct));

        double rawAcc = 103.1668 * Math.exp(-0.04354 * winLoss) - 3.1669;
        double moveAcc = Math.max(0.0, Math.min(100.0, rawAcc));

        if (lastMoverWasWhite) {
            whiteMoveAccuracies.add(Double.valueOf(moveAcc));
        } else {
            blackMoveAccuracies.add(Double.valueOf(moveAcc));
        }

        // Set On-Board Move Quality Badge on the last moved square
        if (!playedUciMoves.isEmpty()) {
            String lastUci = (String) playedUciMoves.get(playedUciMoves.size() - 1);
            if (lastUci.length() >= 4) {
                lastGradedCol = lastUci.charAt(2) - 'a';
                lastGradedRow = '8' - lastUci.charAt(3);
            }
        }

        if (winLoss <= 2.0) {
            lastMoveGradeLabel = "Best (" + String.format("%.0f%%", moveAcc) + ")";
            lastBadgeSymbol = "\u2605"; // ★
            lastBadgeColor = new Color(46, 139, 87);
        } else if (winLoss <= 5.0) {
            lastMoveGradeLabel = "Good (" + String.format("%.0f%%", moveAcc) + ")";
            lastBadgeSymbol = "\u2713"; // ✓
            lastBadgeColor = new Color(86, 156, 104);
        } else if (winLoss <= 10.0) {
            lastMoveGradeLabel = "Inaccuracy ?! (" + String.format("%.0f%%", moveAcc) + ")";
            lastBadgeSymbol = "?!";
            lastBadgeColor = new Color(214, 158, 46);
        } else if (winLoss <= 20.0) {
            lastMoveGradeLabel = "Mistake ? (" + String.format("%.0f%%", moveAcc) + ")";
            lastBadgeSymbol = "?";
            lastBadgeColor = new Color(221, 107, 32);
        } else {
            lastMoveGradeLabel = "Blunder ?? (" + String.format("%.0f%%", moveAcc) + ")";
            lastBadgeSymbol = "??";
            lastBadgeColor = new Color(197, 48, 48);
        }

        updateAccuracyDisplay();
        refreshBoard();
    }

    private String estimateGameRating(List accList) {
        if (accList == null || accList.isEmpty()) return "-";
        double sum = 0.0;
        for (Object o : accList) sum += ((Double) o).doubleValue();
        double avg = sum / accList.size();
        int estElo = (int) Math.round(400.0 + Math.pow(avg / 100.0, 1.65) * 2450.0);
        return estElo + " Elo";
    }

    private void updateAccuracyDisplay() {
        String chosenTeam = (String) myTeamChooser.getSelectedItem();
        boolean playingWhite = chosenTeam == null || chosenTeam.contains("White");

        List myAccList = playingWhite ? whiteMoveAccuracies : blackMoveAccuracies;
        List enemyAccList = playingWhite ? blackMoveAccuracies : whiteMoveAccuracies;

        String myLabelPrefix = isStockfishVsStockfishActive() ? "White: " : "My Acc: ";
        String enemyLabelPrefix = isStockfishVsStockfishActive() ? "Black: " : "Enemy Acc: ";

        myAccuracyLabel.setText(myLabelPrefix + formatAverageAccuracy(myAccList) + " (" + estimateGameRating(myAccList) + ")");
        enemyAccuracyLabel.setText(enemyLabelPrefix + formatAverageAccuracy(enemyAccList) + " (" + estimateGameRating(enemyAccList) + ")");
        lastMoveGradeDisplay.setText("Last: " + lastMoveGradeLabel);
        moveCountDisplay.setText("Moves: " + playedUciMoves.size());
    }


    {
        String chosenTeam = (String) myTeamChooser.getSelectedItem();
        boolean playingWhite = chosenTeam == null || chosenTeam.contains("White");

        List myAccList = playingWhite ? whiteMoveAccuracies : blackMoveAccuracies;
        List enemyAccList = playingWhite ? blackMoveAccuracies : whiteMoveAccuracies;

        myAccuracyLabel.setText("My Accuracy: " + formatAverageAccuracy(myAccList));
        enemyAccuracyLabel.setText("Enemy Accuracy: " + formatAverageAccuracy(enemyAccList));
        lastMoveGradeDisplay.setText("Last: " + lastMoveGradeLabel);
        moveCountDisplay.setText("Moves: " + playedUciMoves.size());
    }

    private String formatAverageAccuracy(List scores) {
        if (scores == null || scores.isEmpty()) return "-";
        double total = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            total += ((Number) scores.get(i)).doubleValue();
        }
        return String.format("%.1f%%", total / scores.size());
    }

    private void updateOpeningDisplay() {
        if (playedUciMoves.isEmpty()) {
            currentOpeningText = "Starting Position";
            currentMoveSequenceText = "Play a move to detect opening or defense";
            openingBookBadge.setText("BOOK");
            openingTitleLabel.setText(currentOpeningText);
            openingMovesLabel.setText(currentMoveSequenceText);
            return;
        }

        String matchedOpening = null;
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < playedUciMoves.size(); i++) {
            if (i > 0) pathBuilder.append(" ");
            pathBuilder.append((String) playedUciMoves.get(i));
            String candidate = (String) OPENING_BOOK.get(pathBuilder.toString());
            if (candidate != null) {
                matchedOpening = candidate;
            }
        }

        if (matchedOpening != null) {
            currentOpeningText = matchedOpening;
            openingBookBadge.setText("BOOK");
        } else if (currentOpeningText.equals("Starting Position")) {
            currentOpeningText = "Unorthodox Opening";
            openingBookBadge.setText("LINE");
        }

        StringBuilder formattedMoves = new StringBuilder();
        int maxShown = Math.min(playedUciMoves.size(), 14);
        int startIdx = Math.max(0, playedUciMoves.size() - maxShown);
        if (startIdx > 0) formattedMoves.append("... ");
        for (int i = startIdx; i < playedUciMoves.size(); i++) {
            if (i % 2 == 0) {
                formattedMoves.append((i / 2) + 1).append(". ");
            }
            formattedMoves.append((String) playedUciMoves.get(i)).append(" ");
        }
        currentMoveSequenceText = formattedMoves.toString().trim();

        openingTitleLabel.setText(currentOpeningText);
        openingMovesLabel.setText(currentMoveSequenceText);
    }

    private void updateLabels() {
        ranksPanel.removeAll();
        filesPanel.removeAll();
        String fontName = getSelectedUiFontName();

        if (!isFlipped) {
            for (int rank = 8; rank >= 1; rank--) {
                JLabel label = new JLabel(Integer.toString(rank), JLabel.CENTER);
                label.setFont(new Font(fontName, Font.PLAIN, 13));
                label.setPreferredSize(new Dimension(22, SQUARE_SIZE));
                label.setForeground(mutedText);
                ranksPanel.add(label);
            }
            for (char file = 'a'; file <= 'h'; file++) {
                JLabel label = new JLabel(Character.toString(file), JLabel.CENTER);
                label.setFont(new Font(fontName, Font.PLAIN, 13));
                label.setForeground(mutedText);
                filesPanel.add(label);
            }
        } else {
            for (int rank = 1; rank <= 8; rank++) {
                JLabel label = new JLabel(Integer.toString(rank), JLabel.CENTER);
                label.setFont(new Font(fontName, Font.PLAIN, 13));
                label.setPreferredSize(new Dimension(22, SQUARE_SIZE));
                label.setForeground(mutedText);
                ranksPanel.add(label);
            }
            for (char file = 'h'; file >= 'a'; file--) {
                JLabel label = new JLabel(Character.toString(file), JLabel.CENTER);
                label.setFont(new Font(fontName, Font.PLAIN, 13));
                label.setForeground(mutedText);
                filesPanel.add(label);
            }
        }
        ranksPanel.setVisible(showCoordsCheck.isSelected());
        filesPanel.setVisible(showCoordsCheck.isSelected());
        ranksPanel.revalidate();
        ranksPanel.repaint();
        filesPanel.revalidate();
        filesPanel.repaint();
    }

    private void buildSidebar() {
        sidebarPanel.setOpaque(true);
        sidebarPanel.setBackground(frameBackground);
        sidebarPanel.setPreferredSize(new Dimension(420, 820));

        JLabel title = new JLabel("ChessAnalyzer V1", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setBorder(new EmptyBorder(0, 2, 4, 0));
        title.setForeground(textForeground);
        themeableLabels.add(title);
        sidebarPanel.add(title, BorderLayout.NORTH);

        engineTabPanel = buildEngineTab();
        customTabPanel = buildCustomizationTab();
        tabs.addTab("Engine", engineTabPanel);
        tabs.addTab("Appearance", customTabPanel);
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sidebarPanel.add(tabs, BorderLayout.CENTER);

        JLabel signatureLabel = new JLabel("@chineseaura", JLabel.RIGHT);
        signatureLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        signatureLabel.setForeground(mutedText);
        sidebarPanel.add(signatureLabel, BorderLayout.SOUTH);
    }

    private JPanel buildEngineTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(frameBackground);
        panel.setBorder(new EmptyBorder(8, 6, 6, 6));

        Insets gap = new Insets(3, 0, 3, 0);
        int row = 0;

        panel.add(createThemeLabel("Board Tool"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(paintPieceChooser, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));

        panel.add(createThemeLabel("Mode & Perspective / Side"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        JPanel modeSideRow = new JPanel(new GridLayout(1, 2, 6, 0));
        modeSideRow.setOpaque(false);
        gameModeChooser.addActionListener(e -> triggerEngineTurnIfNeeded());
        myTeamChooser.addActionListener(e -> {
            updateAccuracyDisplay();
            triggerEngineTurnIfNeeded();
        });
        modeSideRow.add(gameModeChooser);
        modeSideRow.add(myTeamChooser);
        panel.add(modeSideRow, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));

        panel.add(createThemeLabel("Stockfish Binary Path"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(pathField, constraints(0, row, 1, 1, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 6)));
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> chooseEngine());
        panel.add(browse, constraints(1, row++, 1, 0, GridBagConstraints.NONE, gap));

        JPanel boardActions = new JPanel(new GridLayout(1, 3, 6, 0));
        boardActions.setOpaque(false);
        flipBoardButton.addActionListener(event -> {
            isFlipped = !isFlipped;
            updateLabels();
            refreshBoard();
        });
        undoMoveButton.addActionListener(event -> {
            if (!undoStack.isEmpty()) {
                String previousFenSnapshot = (String) undoStack.pop();
                if (!moveHistoryUndoStack.isEmpty()) {
                    playedUciMoves.clear();
                    playedUciMoves.addAll((List) moveHistoryUndoStack.pop());
                }
                if (!whiteAccUndoStack.isEmpty()) {
                    whiteMoveAccuracies.clear();
                    whiteMoveAccuracies.addAll((List) whiteAccUndoStack.pop());
                }
                if (!blackAccUndoStack.isEmpty()) {
                    blackMoveAccuracies.clear();
                    blackMoveAccuracies.addAll((List) blackAccUndoStack.pop());
                }
                pendingMoveGrade = false;
                pendingEngineAutoReply = false;
                lastMoveGradeLabel = "Reverted";
                lastGradedRow = -1;
                lastGradedCol = -1;
                lastBadgeSymbol = "";
                try {
                    model.loadFen(previousFenSnapshot);
                    clearHighlights();
                    if (!playedUciMoves.isEmpty()) {
                        String prevUci = (String) playedUciMoves.get(playedUciMoves.size() - 1);
                        if (prevUci != null && prevUci.length() >= 4) {
                            highlightFromCol = prevUci.charAt(0) - 'a';
                            highlightFromRow = '8' - prevUci.charAt(1);
                            highlightToCol = prevUci.charAt(2) - 'a';
                            highlightToRow = '8' - prevUci.charAt(3);
                        }
                    }
                    clearMateAlert();
                    updateOpeningDisplay();
                    updateAccuracyDisplay();
                    statusLabel.setText("Move undone.");
                    logArea.append("\n[UNDO] Reverted position.");
                    playMoveSound(false);
                    refreshBoard();
                } catch (Exception ignored) {
                }
            } else {
                statusLabel.setText("Nothing to undo.");
            }
        });
        resetBoardButton.addActionListener(event -> {
            try {
                undoStack.push(model.toFen());
                moveHistoryUndoStack.push(new ArrayList(playedUciMoves));
                whiteAccUndoStack.push(new ArrayList(whiteMoveAccuracies));
                blackAccUndoStack.push(new ArrayList(blackMoveAccuracies));
                playedUciMoves.clear();
                whiteMoveAccuracies.clear();
                blackMoveAccuracies.clear();
                pendingMoveGrade = false;
                pendingEngineAutoReply = false;
                lastKnownWhiteWinPct = 52.0;
                lastMoveGradeLabel = "No moves graded yet";
                model.loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                clearHighlights();
                clearMateAlert();
                updateOpeningDisplay();
                updateAccuracyDisplay();
                evalBar.setEvaluation(0, null);
                evalLabel.setText("Eval: +0.00 (Depth 0)");
                logArea.append("\n[RESET] Starting position restored.");
                startResetDropAnimation();
                refreshBoard();
                statusLabel.setText("Board reset.");   refreshBoard();
                pvLabel.setText("Best Line: -");
                statusLabel.setText("Board reset.");
                triggerEngineTurnIfNeeded();
            } catch (Exception ignored) {
            }
        });
        boardActions.add(flipBoardButton);

        boardActions.add(undoMoveButton);
        boardActions.add(resetBoardButton);
        panel.add(boardActions, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 6, 0)));
        JPanel uciPanel = new JPanel(new GridLayout(4, 2, 6, 5));
        uciPanel.setOpaque(false);
        JLabel dLbl = createThemeLabel("Depth / Time (ms)");
        JLabel sLbl = createThemeLabel("Skill (0 - 20)");
        JLabel cLbl = createThemeLabel("Threads / Hash (MB)");
        String skillTip = "0 = Weakest, 20 = Full strength. Disabled when Target Elo is active.";
        sLbl.setToolTipText(skillTip);
        skillSpinner.setToolTipText(skillTip);
        skillDescLabel.setForeground(mutedText);
        skillDescLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        skillDescLabel.setToolTipText(skillTip);
        skillSpinner.addChangeListener(e -> updateSkillDescription());
        updateSkillDescription();
        JPanel skillPanel = new JPanel(new BorderLayout(6, 0));
        skillPanel.setOpaque(false);
        skillPanel.add(skillSpinner, BorderLayout.WEST);
        skillPanel.add(skillDescLabel, BorderLayout.CENTER);
        JPanel dtPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        dtPanel.setOpaque(false);
        dtPanel.add(depthSpinner);
        dtPanel.add(moveTimeSpinner);
        JPanel hwPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        hwPanel.setOpaque(false);
        hwPanel.add(threadsSpinner);
        hwPanel.add(hashSpinner);
        limitEloCheck.setOpaque(false);
        limitEloCheck.setForeground(textForeground);
        themeableChecks.add(limitEloCheck);
        eloField.setEnabled(false);
        eloPresetChooser.setEnabled(false);
        limitEloCheck.addActionListener(e -> {
            boolean useElo = limitEloCheck.isSelected();
            eloField.setEnabled(useElo);
            eloPresetChooser.setEnabled(useElo);
            skillSpinner.setEnabled(!useElo);
            skillDescLabel.setEnabled(!useElo);
            updateSkillDescription();
        });
        eloPresetChooser.addActionListener(e -> {
            String item = (String) eloPresetChooser.getSelectedItem();
            if (item != null && !item.startsWith("Custom")) {
                int sp = item.indexOf(' ');
                eloField.setText(sp > 0 ? item.substring(0, sp) : item);
            }
        });
        JPanel eloInputPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        eloInputPanel.setOpaque(false);
        eloInputPanel.add(eloField);
        eloInputPanel.add(eloPresetChooser);
        uciPanel.add(dLbl);
        uciPanel.add(dtPanel);
        uciPanel.add(sLbl);
        uciPanel.add(skillPanel);
        uciPanel.add(limitEloCheck);
        uciPanel.add(eloInputPanel);
        JLabel botMatchupLbl = createThemeLabel("Bot vs Bot Elo (W / B)");
        JPanel botEloRow = new JPanel(new GridLayout(1, 2, 4, 0));
        botEloRow.setOpaque(false);
        botEloRow.add(whiteBotEloSpinner);
        botEloRow.add(blackBotEloSpinner);
        uciPanel.add(botMatchupLbl);
        uciPanel.add(botEloRow);
        uciPanel.add(cLbl);
        uciPanel.add(hwPanel);
        panel.add(uciPanel, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 6, 0)));
        analyzeButton.addActionListener(event -> calculateBestMove(false));
        panel.add(analyzeButton, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 3, 0)));
        autoplayButton.addActionListener(event -> applyAutoplayMove(false));
        autoplayButton.setEnabled(false);
        panel.add(autoplayButton, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 6, 0)));
        mateAlertBanner.setOpaque(true);
        mateAlertBanner.setFont(new Font("SansSerif", Font.BOLD, 12));
        mateAlertBanner.setBorder(new EmptyBorder(6, 8, 6, 8));
        mateAlertBanner.setVisible(false);
        panel.add(mateAlertBanner, constraints(0, row++, 2, 1, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 6, 0)));
        evalLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        evalLabel.setForeground(textForeground);
        themeableLabels.add(evalLabel);
        panel.add(evalLabel, constraints(0, row++, 2, 1, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 1, 0)));
        pvLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pvLabel.setForeground(mutedText);
        panel.add(pvLabel, constraints(0, row++, 2, 1, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 2, 0)));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(textForeground);
        themeableLabels.add(statusLabel);
        panel.add(statusLabel, constraints(0, row++, 2, 1, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 6, 0)));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        logArea.setText("Engine ready.");
        logScrollPane = new JScrollPane(logArea);
        panel.add(logScrollPane, constraints(0, row, 2, 1, GridBagConstraints.BOTH, gap));
        return panel;
    }

    private boolean isPlayVsStockfishActive() {
        String mode = (String) gameModeChooser.getSelectedItem();
        return mode != null && mode.startsWith("Play vs. Stockfish");
    }

    private boolean isStockfishVsStockfishActive() {
        String mode = (String) gameModeChooser.getSelectedItem();
        return mode != null && mode.startsWith("Stockfish vs. Stockfish");
    }

    private void triggerEngineTurnIfNeeded() {
        if (!isPlayVsStockfishActive()) {
            pendingEngineAutoReply = false;
            return;
        }
        String fen = model.toFen();
        int spaceIdx = fen.indexOf(' ');
        String turn = (spaceIdx >= 0 && spaceIdx + 1 < fen.length())
                ? String.valueOf(fen.charAt(spaceIdx + 1)) : "w";
        String chosenTeam = (String) myTeamChooser.getSelectedItem();
        boolean playerIsWhite = chosenTeam == null || chosenTeam.contains("White");
        boolean isEngineTurn = (playerIsWhite && "b".equals(turn)) || (!playerIsWhite && "w".equals(turn));
        if (isEngineTurn && !pathField.getText().trim().isEmpty()) {
            pendingEngineAutoReply = true;
            calculateBestMove(false);
        }
    }

    private JPanel buildCustomizationTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(frameBackground);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));
        Insets gap = new Insets(2, 0, 2, 0);
        int row = 0;
        panel.add(createThemeLabel("Board Palette & Squares"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        boardChooser.addActionListener(e -> updateMasterStyling());
        panel.add(boardChooser, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        JPanel customSquarePanel = new JPanel(new GridLayout(1, 2, 6, 0));
        customSquarePanel.setOpaque(false);
        customLightBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Light Square Color", lightSquare);
            if (chosen != null) {
                lightSquare = chosen;
                refreshBoard();
            }
        });
        customDarkBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Dark Square Color", darkSquare);
            if (chosen != null) {
                darkSquare = chosen;
                refreshBoard();
            }
        });
        customSquarePanel.add(customLightBtn);
        customSquarePanel.add(customDarkBtn);
        panel.add(customSquarePanel, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(createThemeLabel("Best Move Marker Style & Colors"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 2, 0)));
        JPanel markerRow = new JPanel(new GridLayout(1, 2, 6, 0));
        markerRow.setOpaque(false);
        moveMarkerStyleChooser.addActionListener(e -> refreshBoard());
        highlightThemeChooser.addActionListener(e -> updateMasterStyling());
        markerRow.add(moveMarkerStyleChooser);
        markerRow.add(highlightThemeChooser);
        panel.add(markerRow, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        JPanel markerBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        markerBtns.setOpaque(false);
        customFromHighlightBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Origin Square Color", fromHighlightColor);
            if (chosen != null) {
                fromHighlightColor = new Color(chosen.getRed(), chosen.getGreen(), chosen.getBlue(), 165);
                highlightThemeChooser.setSelectedItem("Custom Colors");
                refreshBoard();
            }
        });
        customToHighlightBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Best Move Destination Color", toHighlightColor);
            if (chosen != null) {
                toHighlightColor = new Color(chosen.getRed(), chosen.getGreen(), chosen.getBlue(), 185);
                highlightThemeChooser.setSelectedItem("Custom Colors");
                refreshBoard();
            }
        });
        markerBtns.add(customFromHighlightBtn);
        markerBtns.add(customToHighlightBtn);
        panel.add(markerBtns, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(createThemeLabel("Interface Background & Typography"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 2, 0)));
        JPanel bgRow = new JPanel(new BorderLayout(6, 0));
        bgRow.setOpaque(false);
        bgChooser.addActionListener(e -> updateMasterStyling());
        customBgBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Interface Background", frameBackground);
            if (chosen != null) {
                frameBackground = chosen;
                deriveSurfaceColors();
                applySleekTheme();
            }
        });
        bgRow.add(bgChooser, BorderLayout.CENTER);
        bgRow.add(customBgBtn, BorderLayout.EAST);
        panel.add(bgRow, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        JPanel textPickerFontRow = new JPanel(new GridLayout(1, 3, 6, 0));
        textPickerFontRow.setOpaque(false);
        textColorChooser.addActionListener(e -> updateMasterStyling());
        customTextColorBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "UI Text Color", textForeground);
            if (chosen != null) {
                textForeground = chosen;
                applySleekTheme();
            }
        });
        uiFontChooser.addActionListener(e -> applySleekTheme());
        textPickerFontRow.add(textColorChooser);
        textPickerFontRow.add(customTextColorBtn);
        textPickerFontRow.add(uiFontChooser);
        panel.add(textPickerFontRow, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(createThemeLabel("Piece Geometry & Colors"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 2, 0)));
        JPanel pieceStyleRow = new JPanel(new GridLayout(1, 2, 6, 0));
        pieceStyleRow.setOpaque(false);
        pieceStyleChooser.addActionListener(e -> refreshBoard());
        pieceThemeChooser.addActionListener(e -> updateMasterStyling());
        pieceStyleRow.add(pieceStyleChooser);
        pieceStyleRow.add(pieceThemeChooser);
        panel.add(pieceStyleRow, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        JPanel pieceColorBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        pieceColorBtns.setOpaque(false);
        customWhitePieceBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "White Pieces Color", whitePieceColor);
            if (chosen != null) {
                whitePieceColor = chosen;
                pieceThemeChooser.setSelectedItem("Custom Colors");
                refreshBoard();
            }
        });
        customBlackPieceBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Black Pieces Color", blackPieceColor);
            if (chosen != null) {
                blackPieceColor = chosen;
                pieceThemeChooser.setSelectedItem("Custom Colors");
                refreshBoard();
            }
        });
        pieceColorBtns.add(customWhitePieceBtn);
        pieceColorBtns.add(customBlackPieceBtn);
        panel.add(pieceColorBtns, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));
        panel.add(createThemeLabel("Piece Bloom / CS2 Glow"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 2, 0)));
        pieceGlowChooser.addActionListener(e -> {
            refreshBoard();
            saveCurrentSettings();
        });
        panel.add(pieceGlowChooser, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));

        JPanel togglesGrid = new JPanel(new GridLayout(2, 2, 4, 4));
        togglesGrid.setOpaque(false);
        styleCheckBox(showCoordsCheck);
        styleCheckBox(showEvalBarCheck);
        styleCheckBox(soundEffectsCheck);
        styleCheckBox(mateAlertsCheck);
        showCoordsCheck.addActionListener(e -> updateLabels());
        showEvalBarCheck.addActionListener(e -> evalBar.setVisible(showEvalBarCheck.isSelected()));
        mateAlertsCheck.addActionListener(e -> {
            if (!mateAlertsCheck.isSelected()) {
                mateAlertBanner.setVisible(false);
                refreshBoard();
            } else if (activeMateThreat != 0) {
                updateMateNotification(activeMateThreat);
                refreshBoard();
            }
        });
        togglesGrid.add(showCoordsCheck);
        togglesGrid.add(showEvalBarCheck);
        togglesGrid.add(soundEffectsCheck);
        togglesGrid.add(mateAlertsCheck);
        panel.add(togglesGrid, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 4, 0)));

        panel.add(createThemeLabel("Move Sound Theme"), constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 2, 0)));
        soundThemeChooser.setEnabled(soundEffectsCheck.isSelected());
        soundEffectsCheck.addActionListener(e -> soundThemeChooser.setEnabled(soundEffectsCheck.isSelected()));
        soundThemeChooser.addActionListener(e -> playMoveSound(false));
        panel.add(soundThemeChooser, constraints(0, row++, 2, 0, GridBagConstraints.HORIZONTAL, gap));

        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, constraints(0, row, 2, 1, GridBagConstraints.BOTH, gap));
        return panel;
    }

    private void deriveSurfaceColors() {
        double brightness = (0.299 * frameBackground.getRed() + 0.587 * frameBackground.getGreen() + 0.114 * frameBackground.getBlue());
        if (brightness < 128) {
            surfaceColor = new Color(
                    Math.min(255, frameBackground.getRed() + 14),
                    Math.min(255, frameBackground.getGreen() + 15),
                    Math.min(255, frameBackground.getBlue() + 18)
            );
            borderColor = new Color(
                    Math.min(255, frameBackground.getRed() + 38),
                    Math.min(255, frameBackground.getGreen() + 40),
                    Math.min(255, frameBackground.getBlue() + 45)
            );
            mutedText = new Color(155, 160, 168);
        } else {
            surfaceColor = new Color(
                    Math.max(0, frameBackground.getRed() - 10),
                    Math.max(0, frameBackground.getGreen() - 10),
                    Math.max(0, frameBackground.getBlue() - 10)
            );
            borderColor = new Color(
                    Math.max(0, frameBackground.getRed() - 36),
                    Math.max(0, frameBackground.getGreen() - 36),
                    Math.max(0, frameBackground.getBlue() - 36)
            );
            mutedText = new Color(105, 110, 118);
        }
    }

    private String getSelectedUiFontName() {
        String sel = (String) uiFontChooser.getSelectedItem();
        if (sel == null) return "SansSerif";
        if (sel.startsWith("Segoe")) return "Segoe UI";
        if (sel.startsWith("Serif")) return "Serif";
        if (sel.startsWith("Monospaced")) return "Monospaced";
        return "SansSerif";
    }

    private void applySleekTheme() {
        String fontName = getSelectedUiFontName();
        rootPanel.setBackground(frameBackground);
        sidebarPanel.setBackground(frameBackground);
        tabs.setBackground(surfaceColor);
        tabs.setForeground(textForeground);
        tabs.setFont(new Font(fontName, Font.PLAIN, 12));
        if (engineTabPanel != null) engineTabPanel.setBackground(frameBackground);
        if (customTabPanel != null) customTabPanel.setBackground(frameBackground);
        openingHeaderCard.setBackground(surfaceColor);
        openingHeaderCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(7, 10, 7, 10)
        ));
        openingTitleLabel.setForeground(textForeground);
        openingTitleLabel.setFont(new Font(fontName, Font.BOLD, 13));
        openingMovesLabel.setForeground(mutedText);
        accuracyCard.setBackground(surfaceColor);
        accuracyCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        myAccuracyLabel.setForeground(textForeground);
        myAccuracyLabel.setFont(new Font(fontName, Font.BOLD, 12));
        enemyAccuracyLabel.setForeground(textForeground);
        enemyAccuracyLabel.setFont(new Font(fontName, Font.BOLD, 12));
        lastMoveGradeDisplay.setForeground(mutedText);
        lastMoveGradeDisplay.setFont(new Font(fontName, Font.PLAIN, 11));
        moveCountDisplay.setForeground(mutedText);
        moveCountDisplay.setFont(new Font(fontName, Font.PLAIN, 11));
        styleCaptureStrip(topCaptureStrip, topCaptureSideLabel, topCapturedPiecesLabel, topMaterialLeadLabel);
        styleCaptureStrip(bottomCaptureStrip, bottomCaptureSideLabel, bottomCapturedPiecesLabel, bottomMaterialLeadLabel);
        for (int i = 0; i < themeableLabels.size(); i++) {
            JLabel label = (JLabel) themeableLabels.get(i);
            label.setForeground(textForeground);
            Font f = label.getFont();
            label.setFont(new Font(fontName, f.getStyle(), f.getSize()));
        }
        for (int i = 0; i < themeableChecks.size(); i++) {
            JCheckBox cb = (JCheckBox) themeableChecks.get(i);
            cb.setForeground(textForeground);
            Font f = cb.getFont();
            cb.setFont(new Font(fontName, f.getStyle(), f.getSize()));
        }
        skillDescLabel.setForeground(mutedText);
        pvLabel.setForeground(mutedText);
        logArea.setBackground(surfaceColor);
        logArea.setForeground(textForeground);
        logArea.setCaretColor(textForeground);
        if (logScrollPane != null) {
            logScrollPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        }
        styleComponentTree(sidebarPanel, fontName);
        updateLabels();
        rootPanel.repaint();
        sidebarPanel.repaint();
    }

    private void styleComponentTree(Container container, String fontName) {
        Border flatInputBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(4, 7, 4, 7)
        );
        Border flatButtonBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                new EmptyBorder(5, 10, 5, 10)
        );
        Component[] comps = container.getComponents();
        for (int i = 0; i < comps.length; i++) {
            Component comp = comps[i];
            if (comp instanceof JButton) {
                JButton btn = (JButton) comp;
                btn.setBackground(surfaceColor);
                btn.setForeground(textForeground);
                btn.setFocusPainted(false);
                btn.setBorder(flatButtonBorder);
                btn.setFont(new Font(fontName, Font.PLAIN, 12));
            } else if (comp instanceof JComboBox) {
                JComponent combo = (JComponent) comp;
                combo.setBackground(surfaceColor);
                combo.setForeground(textForeground);
                combo.setBorder(BorderFactory.createLineBorder(borderColor, 1));
                combo.setFont(new Font(fontName, Font.PLAIN, 12));
            } else if (comp instanceof JTextField) {
                JTextField tf = (JTextField) comp;
                tf.setBackground(surfaceColor);
                tf.setForeground(textForeground);
                tf.setCaretColor(textForeground);
                tf.setBorder(flatInputBorder);
                tf.setFont(new Font(fontName, Font.PLAIN, 12));
            } else if (comp instanceof JSpinner) {
                JSpinner sp = (JSpinner) comp;
                sp.setBackground(surfaceColor);
                sp.setForeground(textForeground);
                sp.setBorder(BorderFactory.createLineBorder(borderColor, 1));
                JComponent editor = sp.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
                    tf.setBackground(surfaceColor);
                    tf.setForeground(textForeground);
                    tf.setFont(new Font(fontName, Font.PLAIN, 12));
                }
            } else if (comp instanceof Container) {
                styleComponentTree((Container) comp, fontName);
            }
        }
    }

    private void updateMateNotification(Integer mateIn) {
        if (mateIn == null || mateIn.intValue() == 0) {
            clearMateAlert();
            return;
        }
        int mVal = mateIn.intValue();
        activeMateThreat = mVal;
        if (!mateAlertsCheck.isSelected()) {
            mateAlertBanner.setVisible(false);
            return;
        }
        String chosenTeam = (String) myTeamChooser.getSelectedItem();
        boolean playingWhite = chosenTeam == null || chosenTeam.contains("White");
        boolean whiteDeliveringMate = mVal > 0;
        boolean playerDeliveringMate = (playingWhite && whiteDeliveringMate) || (!playingWhite && !whiteDeliveringMate);
        int moves = Math.abs(mVal);
        String moveWord = moves == 1 ? "MOVE" : "MOVES";
        if (playerDeliveringMate) {
            mateAlertBanner.setBackground(new Color(45, 106, 79));
            mateAlertBanner.setForeground(new Color(235, 245, 240));
            mateAlertBanner.setText("MATE IN " + moves + " " + moveWord + " (Winning Sequence)");
        } else {
            mateAlertBanner.setBackground(new Color(155, 44, 44));
            mateAlertBanner.setForeground(new Color(250, 235, 235));
            mateAlertBanner.setText("WARNING: FORCED MATE IN " + moves + " " + moveWord);
        }
        mateAlertBanner.setVisible(true);
        if (lastAnnouncedMate != mVal) {
            lastAnnouncedMate = mVal;
            playMateAlertSound(playerDeliveringMate);
        }
    }

    private void clearMateAlert() {
        activeMateThreat = 0;
        lastAnnouncedMate = 0;
        mateAlertBanner.setVisible(false);
    }

    private void styleCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setForeground(textForeground);
        themeableChecks.add(box);
    }

    private void updatePieceFont() {
        String selected = (String) pieceFontChooser.getSelectedItem();
        int size = pieceSizeSlider.getValue();
        if (selected != null && selected.startsWith("Serif")) {
            pieceFont = new Font("Serif", Font.PLAIN, size);
        } else if (selected != null && selected.startsWith("Dialog")) {
            pieceFont = new Font("Dialog", Font.BOLD, size);
        } else {
            pieceFont = new Font("SansSerif", Font.PLAIN, size);
        }
        refreshBoard();
    }

    private JLabel createThemeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(textForeground);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        themeableLabels.add(lbl);
        return lbl;
    }

    private void updateSkillDescription() {
        if (limitEloCheck.isSelected()) {
            skillDescLabel.setText("Elo Override");
            return;
        }
        int lvl = ((Number) skillSpinner.getValue()).intValue();
        if (lvl == 20) skillDescLabel.setText("Max (Full)");
        else if (lvl >= 17) skillDescLabel.setText("Master (~2400)");
        else if (lvl >= 13) skillDescLabel.setText("Advanced (~2000)");
        else if (lvl >= 9) skillDescLabel.setText("Club (~1600)");
        else if (lvl >= 5) skillDescLabel.setText("Casual (~1200)");
        else if (lvl >= 1) skillDescLabel.setText("Novice (~950)");
        else skillDescLabel.setText("Weakest (~800)");
    }

    private void clearHighlights() {
        highlightFromCol = -1;
        highlightFromRow = -1;
        highlightToCol = -1;
        highlightToRow = -1;
        selectedRow = -1;
        selectedCol = -1;
        latestBestMove = "";
        autoplayButton.setEnabled(false);
        refreshBoard();
    }

    private void updateMasterStyling() {
        String boardStyle = (String) boardChooser.getSelectedItem();
        if ("1. Minimal Slate".equals(boardStyle)) {
            lightSquare = new Color(235, 236, 238);
            darkSquare = new Color(138, 148, 158);
        } else if ("2. Classic Walnut".equals(boardStyle)) {
            lightSquare = new Color(236, 224, 206);
            darkSquare = new Color(168, 134, 102);
        } else if ("3. Tournament Sage".equals(boardStyle)) {
            lightSquare = new Color(235, 237, 224);
            darkSquare = new Color(122, 145, 108);
        } else if ("4. Nordic Ice".equals(boardStyle)) {
            lightSquare = new Color(232, 238, 242);
            darkSquare = new Color(125, 148, 168);
        } else if ("5. Charcoal Matte".equals(boardStyle)) {
            lightSquare = new Color(180, 184, 190);
            darkSquare = new Color(72, 76, 84);
        } else if ("6. Warm Alabaster".equals(boardStyle)) {
            lightSquare = new Color(244, 241, 234);
            darkSquare = new Color(188, 178, 164);
        } else if ("7. Deep Graphite".equals(boardStyle)) {
            lightSquare = new Color(145, 150, 158);
            darkSquare = new Color(55, 58, 64);
        } else if ("8. Soft Sand".equals(boardStyle)) {
            lightSquare = new Color(238, 228, 212);
            darkSquare = new Color(182, 160, 132);
        } else if ("9. Muted Olive".equals(boardStyle)) {
            lightSquare = new Color(228, 232, 218);
            darkSquare = new Color(118, 128, 98);
        } else if ("10. Studio Ash".equals(boardStyle)) {
            lightSquare = new Color(225, 227, 230);
            darkSquare = new Color(152, 156, 162);
        }
        String hlStyle = (String) highlightThemeChooser.getSelectedItem();
        if ("Muted Sage".equals(hlStyle)) {
            fromHighlightColor = new Color(225, 215, 140, 150);
            toHighlightColor = new Color(110, 175, 135, 175);
        } else if ("Chess.com Green".equals(hlStyle)) {
            fromHighlightColor = new Color(245, 246, 130, 175);
            toHighlightColor = new Color(118, 150, 86, 190);
        } else if ("Cool Slate Blue".equals(hlStyle)) {
            fromHighlightColor = new Color(185, 210, 235, 160);
            toHighlightColor = new Color(95, 145, 200, 185);
        } else if ("Soft Amber".equals(hlStyle)) {
            fromHighlightColor = new Color(245, 220, 150, 160);
            toHighlightColor = new Color(215, 160, 75, 185);
        } else if ("Subtle Crimson".equals(hlStyle)) {
            fromHighlightColor = new Color(235, 180, 180, 155);
            toHighlightColor = new Color(195, 78, 78, 185);
        } else if ("Neutral Silver".equals(hlStyle)) {
            fromHighlightColor = new Color(210, 214, 220, 160);
            toHighlightColor = new Color(145, 152, 162, 185);
        }
        Color autoText = new Color(232, 234, 237);
        String bgStyle = (String) bgChooser.getSelectedItem();
        if ("Obsidian Matte".equals(bgStyle)) {
            frameBackground = new Color(24, 25, 28);
            autoText = new Color(232, 234, 237);
        } else if ("Sleek Dark Slate".equals(bgStyle)) {
            frameBackground = new Color(36, 39, 44);
            autoText = new Color(236, 238, 242);
        } else if ("Clean Paper White".equals(bgStyle)) {
            frameBackground = new Color(248, 249, 250);
            autoText = new Color(32, 34, 38);
        } else if ("Soft Warm Gray".equals(bgStyle)) {
            frameBackground = new Color(236, 234, 230);
            autoText = new Color(42, 40, 38);
        } else if ("Midnight Charcoal".equals(bgStyle)) {
            frameBackground = new Color(18, 19, 21);
            autoText = new Color(224, 226, 230);
        }
        deriveSurfaceColors();
        String textStyle = (String) textColorChooser.getSelectedItem();
        if ("Auto (Match Theme)".equals(textStyle)) textForeground = autoText;
        else if ("Soft White".equals(textStyle)) textForeground = new Color(242, 244, 247);
        else if ("Charcoal Ink".equals(textStyle)) textForeground = new Color(28, 30, 34);
        else if ("Cool Silver".equals(textStyle)) textForeground = new Color(195, 200, 208);
        else if ("Warm Ivory".equals(textStyle)) textForeground = new Color(242, 236, 224);
        String pieceTheme = (String) pieceThemeChooser.getSelectedItem();
        if ("Ivory & Obsidian".equals(pieceTheme)) {
            whitePieceColor = new Color(248, 249, 250);
            blackPieceColor = new Color(24, 25, 28);
        } else if ("Pure White & Graphite".equals(pieceTheme)) {
            whitePieceColor = Color.WHITE;
            blackPieceColor = new Color(48, 52, 58);
        } else if ("Warm Cream & Espresso".equals(pieceTheme)) {
            whitePieceColor = new Color(248, 242, 230);
            blackPieceColor = new Color(56, 44, 36);
        } else if ("Silver & Charcoal".equals(pieceTheme)) {
            whitePieceColor = new Color(228, 232, 238);
            blackPieceColor = new Color(34, 36, 40);
        }
        applySleekTheme();
        applyThemeToComponentTree(getContentPane());
        refreshBoard();
    }

    private void applyThemeToComponentTree(Component comp) {
        if (comp == null) return;
        boolean isDark = (frameBackground.getRed() + frameBackground.getGreen() + frameBackground.getBlue()) < 380;
        Color controlBg = isDark ? new Color(44, 48, 56) : new Color(255, 255, 255);
        Color controlBorder = isDark ? new Color(82, 88, 100) : new Color(195, 200, 208);
        Color highContrastText = isDark ? new Color(244, 246, 250) : new Color(24, 26, 30);
        Color mutedContrastText = isDark ? new Color(185, 192, 204) : new Color(90, 96, 106);

        if (comp instanceof JCheckBox) {
            JCheckBox cb = (JCheckBox) comp;
            cb.setForeground(highContrastText);
            cb.setOpaque(false);
        } else if (comp instanceof JSlider) {
            JSlider slider = (JSlider) comp;
            slider.setForeground(highContrastText);
            slider.setOpaque(false);
        } else if (comp instanceof JLabel) {
            JLabel lbl = (JLabel) comp;
            if (comp != mateAlertBanner && !"BOOK".equals(lbl.getText())) {
                if (lbl == lastMoveGradeDisplay || lbl == moveCountDisplay || lbl == pvLabel) {
                    lbl.setForeground(mutedContrastText);
                } else {
                    lbl.setForeground(highContrastText);
                }
            }
        } else if (comp instanceof JTextArea) {
            JTextArea ta = (JTextArea) comp;
            ta.setBackground(controlBg);
            ta.setForeground(highContrastText);
            ta.setCaretColor(highContrastText);
        } else if (comp instanceof JSpinner) {
            JSpinner spinner = (JSpinner) comp;
            spinner.setForeground(highContrastText);
            spinner.setBackground(controlBg);
            JComponent editor = spinner.getEditor();
            if (editor instanceof JSpinner.DefaultEditor) {
                JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
                tf.setBackground(controlBg);
                tf.setForeground(highContrastText);
                tf.setCaretColor(highContrastText);
            }
        } else if (comp instanceof JTextField) {
            JTextField tf = (JTextField) comp;
            tf.setBackground(controlBg);
            tf.setForeground(highContrastText);
            tf.setCaretColor(highContrastText);
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(controlBorder, 1),
                    new EmptyBorder(3, 6, 3, 6)));
        } else if (comp instanceof JComboBox) {
            JComboBox combo = (JComboBox) comp;
            combo.setBackground(Color.WHITE);
            combo.setForeground(new Color(24, 26, 30));
        } else if (comp instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) comp;
            Color activeTabBg = isDark ? new Color(46, 50, 58) : new Color(255, 255, 255);
            Color inactiveTabBg = isDark ? new Color(28, 31, 36) : new Color(224, 228, 234);
            Color activeText = isDark ? new Color(248, 250, 252) : new Color(20, 22, 26);
            Color inactiveText = isDark ? new Color(165, 172, 184) : new Color(95, 102, 114);
            Color accentLine = new Color(102, 168, 128);

            tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
                @Override
                protected void installDefaults() {
                    super.installDefaults();
                    tabAreaInsets = new Insets(2, 2, 4, 2);
                    selectedTabPadInsets = new Insets(2, 4, 2, 4);
                }

                @Override
                protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                                  int x, int y, int w, int h, boolean isSelected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isSelected ? activeTabBg : inactiveTabBg);
                    g2.fillRoundRect(x + 1, y + 1, w - 3, h - 2, 8, 8);
                    if (isSelected) {
                        g2.setColor(accentLine);
                        g2.fillRect(x + 6, y + h - 3, w - 13, 2);
                    }
                    g2.dispose();
                }

                @Override
                protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                              int x, int y, int w, int h, boolean isSelected) {
                    // Clean borderless flat tab
                }

                @Override
                protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                    // Clean borderless content area
                }

                @Override
                protected void paintFocusIndicator(Graphics g, int tabPlacement, java.awt.Rectangle[] rects,
                                                   int tabIndex, java.awt.Rectangle iconRect, java.awt.Rectangle textRect,
                                                   boolean isSelected) {
                    // Remove dotted focus box


                    // Remove dotted focus box
                }
            });

            tabs.setBackground(frameBackground);
            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setForegroundAt(i, i == tabs.getSelectedIndex() ? activeText : inactiveText);
            }
            if (tabs.getClientProperty("themeTabListenerAdded") == null) {
                tabs.putClientProperty("themeTabListenerAdded", Boolean.TRUE);
                tabs.addChangeListener(e -> applyThemeToComponentTree(tabs));
            }
        }


        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyThemeToComponentTree(child);
            }
        }
    }

    private void playMoveSound(boolean isCapture) {
        if (!soundEffectsCheck.isSelected()) return;
        final int themeIdx = soundThemeChooser.getSelectedIndex();

        new Thread(() -> {
            try {
                float sampleRate = 44100f;
                int ms;
                switch (themeIdx) {
                    case 0:
                        ms = isCapture ? 95 : 75;
                        break;  // Chess.com Wood
                    case 1:
                        ms = isCapture ? 70 : 50;
                        break;  // Lichess Crisp
                    case 3:
                        ms = isCapture ? 105 : 80;
                        break; // Marble Stone
                    case 4:
                        ms = isCapture ? 100 : 65;
                        break; // Retro 8-Bit
                    default:
                        ms = isCapture ? 60 : 40;
                        break;  // Classic Tone
                }

                int samples = (int) ((ms / 1000.0) * sampleRate);
                byte[] buf = new byte[samples * 2]; // 16-bit PCM = 2 bytes per sample
                java.util.Random rng = new java.util.Random(isCapture ? 99L : 42L);

                for (int i = 0; i < samples; i++) {
                    double t = (double) i / sampleRate;
                    double progress = (double) i / samples;
                    double wave = 0.0;

                    if (themeIdx == 0) {
                        // Chess.com: Warm wooden thock + knock transient
                        double env = Math.exp(-progress * (isCapture ? 6.0 : 8.0));
                        double f1 = (isCapture ? 160.0 : 205.0) * (1.0 - 0.18 * progress);
                        double f2 = isCapture ? 360.0 : 450.0;
                        double knock = (progress < 0.14) ? (rng.nextDouble() * 2.0 - 1.0) * (1.0 - progress / 0.14) * 0.7 : 0.0;
                        wave = (0.65 * Math.sin(2.0 * Math.PI * f1 * t)
                                + 0.35 * Math.sin(2.0 * Math.PI * f2 * t)
                                + knock) * env;

                    } else if (themeIdx == 1) {
                        // Lichess: Snappy percussive wood/slate pop
                        double env = Math.exp(-progress * (isCapture ? 8.5 : 11.0));
                        double pitchSweep = (isCapture ? 250.0 : 340.0) + 190.0 * Math.exp(-progress * 16.0);
                        double snap = (progress < 0.10) ? (rng.nextDouble() * 2.0 - 1.0) * (1.0 - progress / 0.10) * 0.55 : 0.0;
                        wave = (0.85 * Math.sin(2.0 * Math.PI * pitchSweep * t) + snap) * env;

                    } else if (themeIdx == 3) {
                        // Marble: Heavy stone clack + mineral ring
                        double env = Math.exp(-progress * (isCapture ? 5.0 : 7.0));
                        double lowClack = Math.sin(2.0 * Math.PI * (isCapture ? 135.0 : 170.0) * t);
                        double stoneRing = Math.sin(2.0 * Math.PI * (isCapture ? 1180.0 : 1420.0) * t) * Math.exp(-progress * 15.0);
                        double impact = (progress < 0.08) ? (rng.nextDouble() * 2.0 - 1.0) * 0.85 : 0.0;
                        wave = (0.55 * lowClack + 0.35 * stoneRing + impact) * env;

                    } else if (themeIdx == 4) {
                        // Retro 8-Bit: Arcade square wave
                        double env = 1.0 - progress;
                        double freq = isCapture
                                ? 480.0 - 260.0 * progress
                                : (progress < 0.45 ? 520.0 : 780.0);
                        double sq = Math.sin(2.0 * Math.PI * freq * t) >= 0 ? 0.6 : -0.6;
                        wave = sq * env;

                    } else {
                        // Classic: Clean sine tone
                        double env = 1.0 - progress;
                        double freq = isCapture ? 250.0 : 400.0;
                        wave = Math.sin(2.0 * Math.PI * freq * t) * env;
                    }

                    wave = Math.max(-1.0, Math.min(1.0, wave));
                    short pcm = (short) (wave * 24000);
                    buf[i * 2] = (byte) (pcm & 0xFF);
                    buf[i * 2 + 1] = (byte) ((pcm >> 8) & 0xFF);
                }

                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                Clip clip = AudioSystem.getClip();
                clip.addLineListener(event -> {
                    if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
                clip.open(format, buf, 0, buf.length);
                clip.start();
                Thread.sleep(ms + 60);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, "chess-audio").start();
    }

    private void playMateAlertSound(boolean playerWinning) {
        if (!soundEffectsCheck.isSelected() || !mateAlertsCheck.isSelected()) return;
        new Thread(() -> {
            try {
                float sampleRate = 16000f;
                int totalMs = 180;
                int samples = (int) ((totalMs / 1000.0) * sampleRate);
                byte[] buf = new byte[samples];
                int half = samples / 2;
                double f1 = playerWinning ? 523.25 : 330.0;
                double f2 = playerWinning ? 783.99 : 220.0;
                for (int i = 0; i < samples; i++) {
                    double freq = (i < half) ? f1 : f2;
                    int localIdx = (i < half) ? i : (i - half);
                    double env = 1.0 - ((double) localIdx / half);
                    double wave = Math.sin(2.0 * Math.PI * i * freq / sampleRate) * env;
                    buf[i] = (byte) (wave * 95);
                }
                AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
                Clip clip = AudioSystem.getClip();
                clip.open(format, buf, 0, buf.length);
                clip.start();
            } catch (Exception ignored) {
            }
        }, "chess-mate-audio").start();
    }

    private void applyAutoplayMove(boolean isEngineSparringReply) {
        if (latestBestMove == null || latestBestMove.length() < 4 || "none".equals(latestBestMove)) {
            if (isStockfishVsStockfishActive()) {
                statusLabel.setText("Game over — Stockfish vs. Stockfish finished.");
                logArea.append("\n[WATCH] Game ended.");
                logArea.append("\n   White-Bot: \"Good game, Black-Bot! That was super fun.\"");
                logArea.append("\n   Black-Bot: \"GG, White-Bot! Great match!\"");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
            return;
        }

        boolean watchMode = isStockfishVsStockfishActive();
        int fCol = latestBestMove.charAt(0) - 'a';
        int fRow = '8' - latestBestMove.charAt(1);
        int tCol = latestBestMove.charAt(2) - 'a';
        int tRow = '8' - latestBestMove.charAt(3);
        String movingPiece = model.getPieceAt(fRow, fCol);
        String target = model.getPieceAt(tRow, tCol);
        boolean isCapture = target != null && !target.isEmpty();
        boolean moverIsWhite = isWhitePiece(movingPiece);

        undoStack.push(model.toFen());
        executeMoveAndHandleCastling(fRow, fCol, tRow, tCol);
        recordPlayedMove(fRow, fCol, tRow, tCol, moverIsWhite);
        playMoveSound(isCapture);

        String tag = watchMode ? (moverIsWhite ? "\n[SF-WHITE] " : "\n[SF-BLACK] ")
                : (isEngineSparringReply ? "\n[STOCKFISH] " : "\n[AUTO] ");
        logArea.append(tag + latestBestMove);

        if (watchMode) {
            boolean isPromoMove = latestBestMove.length() == 5;
            String chatLine = generateFriendlyBotChat(moverIsWhite, latestBestMove, movingPiece, isCapture, target, isPromoMove);
            logArea.append("\n   " + chatLine);
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());

        String playedMove = latestBestMove;
        clearHighlights();
        if (isEngineSparringReply || watchMode) {
            highlightFromRow = fRow;
            highlightFromCol = fCol;
            highlightToRow = tRow;
            highlightToCol = tCol;
            if (watchMode) {
                statusLabel.setText((moverIsWhite ? "White" : "Black") + " played " + playedMove + " — thinking...");
            } else {
                statusLabel.setText("Your turn (Stockfish played " + playedMove + ")");
            }
        }
        refreshBoard();

        if (watchMode && !pathField.getText().trim().isEmpty()) {
            Timer nextTurnTimer = new Timer(550, evt -> {
                if (isStockfishVsStockfishActive() && !pathField.getText().trim().isEmpty()) {
                    pendingEngineAutoReply = true;
                    calculateBestMove(false);
                }
            });
            nextTurnTimer.setRepeats(false);
            nextTurnTimer.start();
        } else if (!pathField.getText().trim().isEmpty()) {
            calculateBestMove(true);
        }
    }

    private String generateFriendlyBotChat(boolean moverIsWhite, String uciMove, String movingPiece,
                                           boolean isCapture, String capturedPiece, boolean isPromo) {
        String speaker = moverIsWhite ? "White-Bot" : "Black-Bot";
        String opponent = moverIsWhite ? "Black-Bot" : "White-Bot";
        java.util.Random rng = new java.util.Random();

        if (playedUciMoves.size() == 1) {
            return speaker + ": \"Hey " + opponent + "! Let's have a fun, clean game—opening with " + uciMove + ".\"";
        }

        if (activeMateThreat != 0) {
            boolean speakerDeliveringMate = (moverIsWhite && activeMateThreat > 0) || (!moverIsWhite && activeMateThreat < 0);
            int m = Math.abs(activeMateThreat);
            if (speakerDeliveringMate) {
                String[] mateLines = {
                        "Looks like mate in " + m + " from here—really fun game, " + opponent + "!",
                        "I spotted a forcing mate in " + m + "! Thanks for a great match.",
                        "Great battle, " + opponent + "! Mate in " + m + " is on the board."
                };
                return speaker + ": \"" + mateLines[rng.nextInt(mateLines.length)] + "\"";
            } else {
                String[] defendingLines = {
                        "Whoa, nice find! Looks like you've got mate in " + m + "—well played!",
                        "That's a super clean attack, " + opponent + "! Let's see the finish.",
                        "You out-calculated me there—gg in " + m + "!"
                };
                return speaker + ": \"" + defendingLines[rng.nextInt(defendingLines.length)] + "\"";
            }
        }

        if (isPromo) {
            String[] promoLines = {
                    "Made it across the board! Promoting this pawn.",
                    "Pawn promotion! Let's see how the endgame plays out now, " + opponent + ".",
                    "Fresh piece on the board—things are getting exciting!"
            };
            return speaker + ": \"" + promoLines[rng.nextInt(promoLines.length)] + "\"";
        }

        boolean isCastle = isKing(movingPiece) && ("e1g1".equals(uciMove) || "e1c1".equals(uciMove)
                || "e8g8".equals(uciMove) || "e8c8".equals(uciMove));
        if (isCastle) {
            String[] castleLines = {
                    "Tucking the King away safely—ready for the middlegame!",
                    "Castled and rooks connected. Over to you, " + opponent + "!",
                    "Getting the King to safety first. Nice setup so far!"
            };
            return speaker + ": \"" + castleLines[rng.nextInt(castleLines.length)] + "\"";
        }

        if (isCapture) {
            if ("\u2655".equals(capturedPiece) || "\u265B".equals(capturedPiece)) {
                return speaker + ": \"Whoa, grabbing the Queen! Let's see what counterplay you've got, " + opponent + ".\"";
            }
            if ("\u2656".equals(capturedPiece) || "\u265C".equals(capturedPiece)
                    || "\u2657".equals(capturedPiece) || "\u265D".equals(capturedPiece)
                    || "\u2658".equals(capturedPiece) || "\u265E".equals(capturedPiece)) {
                String[] pieceCapLines = {
                        "Nice tactical exchange! Let's see how the board opens up.",
                        "Trading off some material—good fight in the center, " + opponent + "!",
                        "Taking that piece—curious to see your follow-up!"
                };
                return speaker + ": \"" + pieceCapLines[rng.nextInt(pieceCapLines.length)] + "\"";
            }
            String[] pawnCapLines = {
                    "Picking up that pawn—opening a few lines up!",
                    "Nice tension in the center! Taking the pawn on " + uciMove.substring(2, 4) + ".",
                    "Snagging a pawn here—your pieces are still super active though, " + opponent + "!"
            };
            return speaker + ": \"" + pawnCapLines[rng.nextInt(pawnCapLines.length)] + "\"";
        }

        if (playedUciMoves.size() <= 8 && currentOpeningText != null && !currentOpeningText.startsWith("Starting")) {
            String[] bookLines = {
                    "Ooh, " + currentOpeningText + "! One of my favorite openings to play.",
                    "Love seeing " + currentOpeningText + "—always leads to interesting games!",
                    "Classic " + currentOpeningText + " setup. Good luck, " + opponent + "!"
            };
            return speaker + ": \"" + bookLines[rng.nextInt(bookLines.length)] + "\"";
        }

        double speakerWinPct = moverIsWhite ? lastKnownWhiteWinPct : (100.0 - lastKnownWhiteWinPct);
        if (speakerWinPct >= 68.0) {
            String[] aheadLines = {
                    "I like how my pieces are coordinating here, though you still have solid defense!",
                    "Trying to press a slight edge (" + String.format("%.0f%%", speakerWinPct) + ")—curious how you defend this, " + opponent + ".",
                    "Things are opening up nicely on my side!"
            };
            return speaker + ": \"" + aheadLines[rng.nextInt(aheadLines.length)] + "\"";
        } else if (speakerWinPct <= 32.0) {
            String[] behindLines = {
                    "Really strong pressure from you, " + opponent + "! Trying to find active counterplay.",
                    "You've got a great initiative here—let me see if I can hold the position!",
                    "Tough spot to defend, nice maneuvering so far!"
            };
            return speaker + ": \"" + behindLines[rng.nextInt(behindLines.length)] + "\"";
        }

        String[] neutralLines = {
                "Super balanced game so far—great positional play!",
                "Quiet improving move (" + uciMove + "). Your turn, " + opponent + "!",
                "Developing smoothly—this middlegame is going to be fun.",
                "Solid move! Looking forward to seeing your plan."
        };
        return speaker + ": \"" + neutralLines[rng.nextInt(neutralLines.length)] + "\"";
    }

    private void executeMoveAndHandleCastling(int fRow, int fCol, int tRow, int tCol) {
        String piece = model.getPieceAt(fRow, fCol);
        String target = model.getPieceAt(tRow, tCol);
        if (isKing(piece) && isRook(target) && fRow == tRow && isWhitePiece(piece) == isWhitePiece(target)) {
            if (tCol == 7) tCol = 6;
            else if (tCol == 0) tCol = 2;
        }
        startPieceAnimation(fRow, fCol, tRow, tCol);
        model.movePiece(fRow, fCol, tRow, tCol);


        // --- PAWN PROMOTION CHECK ---
        lastPromotionSuffix = "";
        boolean isWhitePawnPromoting = "\u2659".equals(piece) && tRow == 0;
        boolean isBlackPawnPromoting = "\u265F".equals(piece) && tRow == 7;

        if (isWhitePawnPromoting || isBlackPawnPromoting) {
            boolean isWhite = isWhitePawnPromoting;
            if (pendingEngineAutoReply && latestBestMove != null && latestBestMove.length() == 5) {
                char enginePromo = Character.toLowerCase(latestBestMove.charAt(4));
                model.setPieceAt(tRow, tCol, promotionCharToUnicode(enginePromo, isWhite));
                lastPromotionSuffix = String.valueOf(enginePromo);
            } else {
                char chosen = promptForPromotion(isWhite);
                model.setPieceAt(tRow, tCol, promotionCharToUnicode(chosen, isWhite));
                lastPromotionSuffix = String.valueOf(chosen);
            }
        }

        boolean pieceIsWhite = isWhitePiece(piece);
        try {
            java.lang.reflect.Field colorField = model.getClass().getDeclaredField("activeColor");
            colorField.setAccessible(true);
            colorField.set(model, pieceIsWhite ? 'b' : 'w');
        } catch (Exception ignored) {
        }
    }

    private char promptForPromotion(boolean isWhite) {
        String[] options = isWhite
                ? new String[]{"\u2655 Queen", "\u2656 Rook", "\u2657 Bishop", "\u2658 Knight"}
                : new String[]{"\u265B Queen", "\u265C Rook", "\u265D Bishop", "\u265E Knight"};

        javax.swing.JOptionPane pane = new javax.swing.JOptionPane(
                "Choose a piece for pawn promotion:",
                javax.swing.JOptionPane.PLAIN_MESSAGE,
                javax.swing.JOptionPane.DEFAULT_OPTION,
                null,
                options,
                options
        );
        javax.swing.JDialog dialog = pane.createDialog(this, "Pawn Promotion");
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);

        Object selected = pane.getValue();
        if (selected != null) {
            String choiceStr = selected.toString();
            if (choiceStr.contains("Rook")) return 'r';
            if (choiceStr.contains("Bishop")) return 'b';
            if (choiceStr.contains("Knight")) return 'n';
        }
        return 'q';
    }

    private String promotionCharToUnicode(char promoChar, boolean isWhite) {
        switch (Character.toLowerCase(promoChar)) {
            case 'r':
                return isWhite ? "\u2656" : "\u265C";
            case 'b':
                return isWhite ? "\u2657" : "\u265D";
            case 'n':
                return isWhite ? "\u2658" : "\u265E";
            default:
                return isWhite ? "\u2655" : "\u265B";
        }
    }

    private void saveCurrentSettings() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(SETTINGS_FILE))) {
            // 1. Existing Preset Indices & Slider
            bw.write(pathField.getText().trim() + "\n");
            bw.write(boardChooser.getSelectedIndex() + "\n");
            bw.write(bgChooser.getSelectedIndex() + "\n");
            bw.write(textColorChooser.getSelectedIndex() + "\n");
            bw.write(pieceStyleChooser.getSelectedIndex() + "\n");
            bw.write(pieceThemeChooser.getSelectedIndex() + "\n");
            bw.write(myTeamChooser.getSelectedIndex() + "\n");
            bw.write(pieceSizeSlider.getValue() + "\n");
            bw.write(moveMarkerStyleChooser.getSelectedIndex() + "\n");
            bw.write(highlightThemeChooser.getSelectedIndex() + "\n");

            // 2. Custom ARGB Colors (Preserves Color-Picker Overrides!)
            bw.write(lightSquare.getRGB() + "\n");
            bw.write(darkSquare.getRGB() + "\n");
            bw.write(whitePieceColor.getRGB() + "\n");
            bw.write(blackPieceColor.getRGB() + "\n");
            bw.write(fromHighlightColor.getRGB() + "\n");
            bw.write(toHighlightColor.getRGB() + "\n");
            bw.write(frameBackground.getRGB() + "\n");
            bw.write(textForeground.getRGB() + "\n");

            // 3. Sound, Clock & Mate Alerts
            bw.write(soundThemeChooser.getSelectedIndex() + "\n");
            bw.write(timeControlChooser.getSelectedIndex() + "\n");
            bw.write(mateAlertsCheck.isSelected() + "\n");
        } catch (Exception ignored) {
        }
    }


    private void loadSavedSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String p = br.readLine();
            if (p != null) pathField.setText(p);
            String bIdx = br.readLine();
            if (bIdx != null) boardChooser.setSelectedIndex(Integer.parseInt(bIdx));
            String bgIdx = br.readLine();
            if (bgIdx != null) bgChooser.setSelectedIndex(Integer.parseInt(bgIdx));
            String tcIdx = br.readLine();
            if (tcIdx != null) textColorChooser.setSelectedIndex(Integer.parseInt(tcIdx));
            String psIdx = br.readLine();
            if (psIdx != null) pieceStyleChooser.setSelectedIndex(Integer.parseInt(psIdx));
            String ptIdx = br.readLine();
            if (ptIdx != null) pieceThemeChooser.setSelectedIndex(Integer.parseInt(ptIdx));
            String tmIdx = br.readLine();
            if (tmIdx != null) myTeamChooser.setSelectedIndex(Integer.parseInt(tmIdx));
            String szVal = br.readLine();
            if (szVal != null) pieceSizeSlider.setValue(Integer.parseInt(szVal));
            String mmIdx = br.readLine();
            if (mmIdx != null) moveMarkerStyleChooser.setSelectedIndex(Integer.parseInt(mmIdx));
            String htIdx = br.readLine();
            if (htIdx != null) highlightThemeChooser.setSelectedIndex(Integer.parseInt(htIdx));

            // Apply preset defaults first, then override with saved custom ARGB colors
            updateMasterStyling();

            String lsRgb = br.readLine();
            if (lsRgb != null) lightSquare = new Color(Integer.parseInt(lsRgb), true);
            String dsRgb = br.readLine();
            if (dsRgb != null) darkSquare = new Color(Integer.parseInt(dsRgb), true);
            String wpRgb = br.readLine();
            if (wpRgb != null) whitePieceColor = new Color(Integer.parseInt(wpRgb), true);
            String bpRgb = br.readLine();
            if (bpRgb != null) blackPieceColor = new Color(Integer.parseInt(bpRgb), true);
            String fhRgb = br.readLine();
            if (fhRgb != null) fromHighlightColor = new Color(Integer.parseInt(fhRgb), true);
            String thRgb = br.readLine();
            if (thRgb != null) toHighlightColor = new Color(Integer.parseInt(thRgb), true);
            String fbRgb = br.readLine();
            if (fbRgb != null) frameBackground = new Color(Integer.parseInt(fbRgb), true);
            String tfRgb = br.readLine();
            if (tfRgb != null) textForeground = new Color(Integer.parseInt(tfRgb), true);

            String stIdx = br.readLine();
            if (stIdx != null) soundThemeChooser.setSelectedIndex(Integer.parseInt(stIdx));
            String clkIdx = br.readLine();
            if (clkIdx != null) timeControlChooser.setSelectedIndex(Integer.parseInt(clkIdx));
            String mChk = br.readLine();
            if (mChk != null) mateAlertsCheck.setSelected(Boolean.parseBoolean(mChk));

            deriveSurfaceColors();
            applySleekTheme();
            updatePieceFont();
            refreshBoard();
        } catch (Exception ignored) {
        }
    }

    private GridBagConstraints constraints(int gridx, int gridy, int gridwidth, double weightx, int fill, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = gridx;
        c.gridy = gridy;
        c.gridwidth = gridwidth;
        c.weightx = weightx;
        c.fill = fill;
        c.insets = insets;
        if (fill == GridBagConstraints.BOTH) c.weighty = 1.0;
        return c;
    }

    private void chooseEngine() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Executables", "exe"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            triggerEngineTurnIfNeeded();
        }
    }

    private void calculateBestMove(boolean backgroundEvalOnly) {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            if (!backgroundEvalOnly) {
                JOptionPane.showMessageDialog(this, "Please select the Stockfish executable path first.", "Missing Engine", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        currentAnalysisIsBackgroundEval = backgroundEvalOnly;
        int fullDepth = ((Number) depthSpinner.getValue()).intValue();
        int depth = backgroundEvalOnly ? Math.min(10, fullDepth) : fullDepth;
        int moveTime = backgroundEvalOnly ? 300 : ((Number) moveTimeSpinner.getValue()).intValue();
        int skill = ((Number) skillSpinner.getValue()).intValue();
        boolean limitElo = limitEloCheck.isSelected();
        int targetElo = 1500;
        if (limitElo) {
            try {
                targetElo = Integer.parseInt(eloField.getText().trim());
                targetElo = Math.max(1320, Math.min(3190, targetElo));
                eloField.setText(String.valueOf(targetElo));
            } catch (NumberFormatException ex) {
                if (!backgroundEvalOnly) {
                    JOptionPane.showMessageDialog(this, "Enter an Elo between 1320 and 3190.", "Invalid Elo", JOptionPane.WARNING_MESSAGE);
                }
                return;
            }
        }

        String fen = model.toFen();

        // Dynamic Elo override for Stockfish vs. Stockfish (Watch) mode
        String mode = (String) gameModeChooser.getSelectedItem();
        if (!backgroundEvalOnly && "Stockfish vs. Stockfish (Watch)".equals(mode)) {
            limitElo = true;
            boolean whiteTurn = fen.contains(" w ");
            int baseElo = whiteTurn
                    ? ((Number) whiteBotEloSpinner.getValue()).intValue()
                    : ((Number) blackBotEloSpinner.getValue()).intValue();

            // Per-move form fluctuation (+/- 135 Elo) + momentum shift
            int jitter = (int) (Math.random() * 271) - 135;
            int momentum = 0;
            if (whiteTurn) {
                if (lastKnownWhiteWinPct < 35.0) momentum += 85;
                else if (lastKnownWhiteWinPct > 70.0) momentum -= 75;
            } else {
                if (lastKnownWhiteWinPct > 65.0) momentum += 85;
                else if (lastKnownWhiteWinPct < 30.0) momentum -= 75;
            }

            targetElo = Math.max(1320, Math.min(3190, baseElo + jitter + momentum));
            statusLabel.setText((whiteTurn ? "White" : "Black") + " Bot playing at Elo " + targetElo + "...");
        }

        int threads = ((Number) threadsSpinner.getValue()).intValue();
        int hash = ((Number) hashSpinner.getValue()).intValue();
        engine.configure(skill, limitElo, targetElo, threads, hash);
        try {
            if (!engine.isAlive() || !connectedPath.equals(path)) {
                statusLabel.setText(pendingEngineAutoReply ? "Stockfish thinking..." : (backgroundEvalOnly ? "Grading move..." : "Starting engine..."));
                connectedPath = path;
                analysisQueued = true;
                queuedFen = fen;
                queuedDepth = depth;
                queuedMoveTime = moveTime;
                engine.start(Path.of(path));
            } else if (engine.isReady()) {
                statusLabel.setText(pendingEngineAutoReply ? "Stockfish thinking..." : (backgroundEvalOnly ? "Grading move..." : "Calculating..."));
                engine.analyze(fen, depth, moveTime);
            } else {
                analysisQueued = true;
                queuedFen = fen;
                queuedDepth = depth;
                queuedMoveTime = moveTime;
            }
        } catch (Exception ex) {
            statusLabel.setText("Engine launch failed.");
        }
    }

    private Timer resetDropTimer;
    private final double[][] pieceDropOffsetY = new double[BOARD_SIZE][BOARD_SIZE];
    private final double[][] pieceDropVelocity = new double[BOARD_SIZE][BOARD_SIZE];
    private final int[][] pieceDropDelay = new int[BOARD_SIZE][BOARD_SIZE];



    private void refreshBoard () {
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (squares[row][col] != null) {
                        squares[row][col].repaint();
                    }
                }
            }
            updateCapturedPiecesDisplay();
        }

    private String resolvePaintedPieceToken(String selection) {
        if (selection.contains("Eraser")) return "";
        if (selection.contains("White King")) return "♔";
        if (selection.contains("White Queen")) return "♕";
        if (selection.contains("White Rook")) return "♖";
        if (selection.contains("White Bishop")) return "♗";
        if (selection.contains("White Knight")) return "♘";
        if (selection.contains("White Pawn")) return "♙";
        if (selection.contains("Black King")) return "♚";
        if (selection.contains("Black Queen")) return "♛";
        if (selection.contains("Black Rook")) return "♜";
        if (selection.contains("Black Bishop")) return "♝";
        if (selection.contains("Black Knight")) return "♞";
        if (selection.contains("Black Pawn")) return "♟";
        return null;
    }

    private boolean isWhitePiece(String pieceStr) {
        if (pieceStr == null || pieceStr.isEmpty()) return false;
        char ch = pieceStr.charAt(0);
        return (ch >= '♔' && ch <= '♙') || "KQRBNP".indexOf(ch) >= 0;
    }

    private boolean isKing(String piece) {
        return "♔".equals(piece) || "♚".equals(piece) || "K".equals(piece) || "k".equals(piece);
    }

    private void startResetDropAnimation() {
        if (resetDropTimer != null && resetDropTimer.isRunning()) {
            resetDropTimer.stop();
        }
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                pieceDropOffsetY[row][col] = -SQUARE_SIZE * 0.75;
                pieceDropVelocity[row][col] = 0.0;
                pieceDropDelay[row][col] = ((BOARD_SIZE - 1 - row) * 2) + col;
            }
        }
        resetDropTimer = new Timer(16, e -> {
            boolean anyMoving = false;
            for (int row = 0; row < BOARD_SIZE; row++) {
                for (int col = 0; col < BOARD_SIZE; col++) {
                    if (pieceDropDelay[row][col] > 0) {
                        pieceDropDelay[row][col]--;
                        anyMoving = true;
                        continue;
                    }
                    if (pieceDropOffsetY[row][col] < 0 || pieceDropVelocity[row][col] != 0.0) {
                        pieceDropVelocity[row][col] += 1.25;
                        pieceDropOffsetY[row][col] += pieceDropVelocity[row][col];
                        if (pieceDropOffsetY[row][col] >= 0) {
                            pieceDropOffsetY[row][col] = 0;
                            if (pieceDropVelocity[row][col] > 3.5) {
                                pieceDropVelocity[row][col] = -pieceDropVelocity[row][col] * 0.42;
                            } else {
                                pieceDropVelocity[row][col] = 0.0;
                            }
                        }
                        anyMoving = true;
                    }
                }
            }
            refreshBoard();
            if (!anyMoving) {
                ((Timer) e.getSource()).stop();
            }
        });
        resetDropTimer.start();
    }

    private boolean isRook(String piece) {
        return "♖".equals(piece) || "♜".equals(piece) || "R".equals(piece) || "r".equals(piece);
    }

    private String toSolidGlyph(String piece) {
        if ("♔".equals(piece) || "K".equals(piece) || "♚".equals(piece) || "k".equals(piece)) return "♚";
        if ("♕".equals(piece) || "Q".equals(piece) || "♛".equals(piece) || "q".equals(piece)) return "♛";
        if ("♖".equals(piece) || "R".equals(piece) || "♜".equals(piece) || "r".equals(piece)) return "♜";
        if ("♗".equals(piece) || "B".equals(piece) || "♝".equals(piece) || "b".equals(piece)) return "♝";
        if ("♘".equals(piece) || "N".equals(piece) || "♞".equals(piece) || "n".equals(piece)) return "♞";
        if ("♙".equals(piece) || "P".equals(piece) || "♟".equals(piece) || "p".equals(piece)) return "♟";
        return piece;
    }

    private String toLetterToken(String piece) {
        if (isKing(piece)) return "K";
        if ("♕".equals(piece) || "♛".equals(piece) || "Q".equals(piece) || "q".equals(piece)) return "Q";
        if (isRook(piece)) return "R";
        if ("♗".equals(piece) || "♝".equals(piece) || "B".equals(piece) || "b".equals(piece)) return "B";
        if ("♘".equals(piece) || "♞".equals(piece) || "N".equals(piece) || "n".equals(piece)) return "N";
        return "P";
    }

    private boolean isLegalMovementPath(int fRow, int fCol, int tRow, int tCol, String piece) {
        if (piece == null || piece.isEmpty()) return false;
        char token = piece.charAt(0);
        int dRow = Math.abs(tRow - fRow);
        int dCol = Math.abs(tCol - fCol);
        switch (token) {
            case '♖':
            case '♜':
            case 'R':
            case 'r':
                return (fRow == tRow || fCol == tCol);
            case '♗':
            case '♝':
            case 'B':
            case 'b':
                return (dRow == dCol);
            case '♕':
            case '♛':
            case 'Q':
            case 'q':
                return (fRow == tRow || fCol == tCol || dRow == dCol);
            case '♔':
            case '♚':
            case 'K':
            case 'k':
                return (dRow <= 1 && dCol <= 1) || (fRow == tRow && dCol == 2 && (fRow == 0 || fRow == 7) && fCol == 4);
            case '♘':
            case '♞':
            case 'N':
            case 'n':
                return (dRow == 2 && dCol == 1) || (dRow == 1 && dCol == 2);
            case '♙':
            case 'P':
                if (fCol == tCol && tRow == fRow - 1)
                    return ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty();
                if (fCol == tCol && fRow == 6 && tRow == fRow - 2)
                    return ChessAnalyserGui.this.model.getPieceAt(fRow - 1, fCol).isEmpty() && ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty();
                if (dCol == 1 && tRow == fRow - 1)
                    return !ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty() && !isWhitePiece(ChessAnalyserGui.this.model.getPieceAt(tRow, tCol));
                return false;
            case '♟':
            case 'p':
                if (fCol == tCol && tRow == fRow + 1)
                    return ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty();
                if (fCol == tCol && fRow == 1 && tRow == fRow + 2)
                    return ChessAnalyserGui.this.model.getPieceAt(fRow + 1, fCol).isEmpty() && ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty();
                if (dCol == 1 && tRow == fRow + 1)
                    return !ChessAnalyserGui.this.model.getPieceAt(tRow, tCol).isEmpty() && isWhitePiece(ChessAnalyserGui.this.model.getPieceAt(tRow, tCol));
                return false;
        }
        return true;
    }

    private class EvalBarPanel extends JPanel {
        private double whiteWinRatio = 0.5;
        private String scoreText = "0.0";

        public EvalBarPanel() {
            setPreferredSize(new Dimension(26, SQUARE_SIZE * BOARD_SIZE));
            setBorder(BorderFactory.createLineBorder(borderColor, 1));
            setToolTipText("Win Probability — White: 50% | Black: 50%");
        }

        public void setEvaluation(Integer scoreCp, Integer mateIn) {
            if (mateIn != null) {
                whiteWinRatio = mateIn.intValue() > 0 ? 1.0 : 0.0;
                scoreText = "M" + Math.abs(mateIn.intValue());
            } else if (scoreCp != null) {
                double pawns = scoreCp.intValue() / 100.0;
                whiteWinRatio = 1.0 / (1.0 + Math.exp(-0.4 * pawns));
                scoreText = String.

                        format("%.1f", Math.abs(pawns));
            } else {
                whiteWinRatio = 0.5;
                scoreText = "0.0";
            }
            int whitePct = (int) Math.round(whiteWinRatio * 100.0);
            int blackPct = 100 - whitePct;
            setToolTipText("Win Probability — White: " + whitePct + "% | Black: " + blackPct + "%");
            repaint();
        }

        public double getWhiteWinPercentage() {
            return whiteWinRatio * 100.0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            int whiteHeight = (int) Math.round(h * whiteWinRatio);
            int blackHeight = h - whiteHeight;
            if (!isFlipped) {
                g.setColor(new Color(32, 34, 38));
                g.fillRect(0, 0, w, blackHeight);
                g.setColor(new Color(242, 244, 247));
                g.fillRect(0, blackHeight, w, whiteHeight);
            } else {
                g.setColor(new Color(242, 244, 247));
                g.fillRect(0, 0, w, whiteHeight);
                g.setColor(new Color(32, 34, 38));
                g.fillRect(0, whiteHeight, w, blackHeight);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(scoreText)) / 2;
            if (whiteWinRatio >= 0.5) {
                g.setColor(new Color(24, 25, 28));
                g.drawString(scoreText, tx, isFlipped ? 14 : h - 6);
            } else {
                g.setColor(new Color(242, 244, 247));
                g.drawString(scoreText, tx, isFlipped ? h - 6 : 14);
            }
        }
    }

    private class SquareButton extends JButton {
        private final int layoutRow;
        private final int layoutCol;

        public SquareButton(int r, int c) {
            this.layoutRow = r;
            this.layoutCol = c;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setPreferredSize(new Dimension(SQUARE_SIZE, SQUARE_SIZE));
            addActionListener(event -> {
                int actualRow = isFlipped ? (7 - layoutRow) : layoutRow;
                int actualCol = isFlipped ? (7 - layoutCol) : layoutCol;
                String activePainterSelection = (String) paintPieceChooser.getSelectedItem();
                if (activePainterSelection != null && !activePainterSelection.startsWith("Move Mode")) {
                    String pieceToken = ChessAnalyserGui.this.resolvePaintedPieceToken(activePainterSelection);
                    if (pieceToken != null) {
                        ChessAnalyserGui.this.undoStack.push(ChessAnalyserGui.this.model.toFen());
                        StringBuilder sb = new StringBuilder();
                        String currentFen = ChessAnalyserGui.this.model.toFen();
                        List fenTokens = Arrays.asList(currentFen.trim().split("\\s+"));
                        for (int rowIdx = 0; rowIdx < BOARD_SIZE; rowIdx++) {
                            int emptyCount = 0;
                            for (int colIdx = 0; colIdx < BOARD_SIZE; colIdx++) {
                                String targetPiece = (rowIdx == actualRow && colIdx == actualCol)
                                        ? pieceToken
                                        : ChessAnalyserGui.this.model.getPieceAt(rowIdx, colIdx);
                                if (targetPiece == null || targetPiece.isEmpty()) {
                                    emptyCount++;
                                } else {
                                    if (emptyCount > 0) {
                                        sb.append(emptyCount);
                                        emptyCount = 0;
                                    }
                                    char fenChar = 'P';
                                    if ("♔".equals(targetPiece) || "K".equals(targetPiece)) fenChar = 'K';
                                    else if ("♕".equals(targetPiece) || "Q".equals(targetPiece)) fenChar = 'Q';
                                    else if ("♖".equals(targetPiece) || "R".equals(targetPiece)) fenChar = 'R';
                                    else if ("♗".equals(targetPiece) || "B".equals(targetPiece)) fenChar = 'B';
                                    else if ("♘".equals(targetPiece) || "N".equals(targetPiece)) fenChar = 'N';
                                    else if ("♙".equals(targetPiece) || "P".equals(targetPiece)) fenChar = 'P';
                                    else if ("♚".equals(targetPiece) || "k".equals(targetPiece)) fenChar = 'k';
                                    else if ("♛".equals(targetPiece) || "q".equals(targetPiece)) fenChar = 'q';
                                    else if ("♜".equals(targetPiece) || "r".equals(targetPiece)) fenChar = 'r';
                                    else if ("♝".equals(targetPiece) || "b".equals(targetPiece)) fenChar = 'b';
                                    else if ("♞".equals(targetPiece) || "n".equals(targetPiece)) fenChar = 'n';
                                    else if ("♟".equals(targetPiece) || "p".equals(targetPiece)) fenChar = 'p';
                                    sb.append(fenChar);
                                }
                            }
                            if (emptyCount > 0) sb.append(emptyCount);
                            if (rowIdx < 7) sb.append("/");
                        }
                        sb.append(" ").append(fenTokens.size() > 1 ? fenTokens.get(1) : "w");
                        sb.append(" ").append(fenTokens.size() > 2 ? fenTokens.get(2) : "KQkq");
                        sb.append(" ").append(fenTokens.size() > 3 ? fenTokens.get(3) : "-");
                        sb.append(" ").append(fenTokens.size() > 4 ? fenTokens.get(4) : "0");
                        sb.append(" ").append(fenTokens.size() > 5 ? fenTokens.get(5) : "1");
                        try {
                            ChessAnalyserGui.this.model.loadFen(sb.toString());
                            playMoveSound(false);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    selectedRow = -1;
                    selectedCol = -1;
                    refreshBoard();
                    return;
                }
                if (selectedRow == -1) {
                    String piece = ChessAnalyserGui.this.model.getPieceAt(actualRow, actualCol);
                    if (piece != null && !piece.isEmpty()) {
                        selectedRow = actualRow;
                        selectedCol = actualCol;
                    }
                } else {
                    if (selectedRow != actualRow || selectedCol != actualCol) {
                        String fromPiece = ChessAnalyserGui.this.model.getPieceAt(selectedRow, selectedCol);
                        String toPiece = ChessAnalyserGui.this.model.getPieceAt(actualRow, actualCol);
                        boolean hasTarget = (toPiece != null && !toPiece.isEmpty());
                        boolean isKingOntoRook = isKing(fromPiece) && isRook(toPiece)
                                && selectedRow == actualRow && (actualCol == 0 || actualCol == 7)
                                && isWhitePiece(fromPiece) == isWhitePiece(toPiece);
                        boolean illegalActionBlocked = false;
                        if (!isKingOntoRook) {
                            if (!isLegalMovementPath(selectedRow, selectedCol, actualRow, actualCol, fromPiece))
                                illegalActionBlocked = true;
                            if (hasTarget && isWhitePiece(fromPiece) == isWhitePiece(toPiece))
                                illegalActionBlocked = true;
                        }
                        if (!illegalActionBlocked) {
                            ChessAnalyserGui.this.undoStack.push(ChessAnalyserGui.this.model.toFen());
                            recordPlayedMove(selectedRow, selectedCol, actualRow, actualCol, isWhitePiece(fromPiece));
                            executeMoveAndHandleCastling(selectedRow, selectedCol, actualRow, actualCol);
                            playMoveSound(hasTarget && !isKingOntoRook);
                            String updatedFen = ChessAnalyserGui.this.model.toFen();
                            int spaceIdx = updatedFen.indexOf(' ');
                            String nextTurnColor = (spaceIdx >= 0 && spaceIdx + 1 < updatedFen.length())
                                    ? String.valueOf(updatedFen.charAt(spaceIdx + 1))
                                    : "w";
                            String chosenTeam = (String) myTeamChooser.getSelectedItem();
                            boolean isMyTurnNow = chosenTeam != null && ((chosenTeam.contains("White") && "w".equals(nextTurnColor)) ||
                                    (chosenTeam.contains("Black") && "b".equals(nextTurnColor)));
                            if (isPlayVsStockfishActive()) {
                                if (!isMyTurnNow) {
                                    highlightFromCol = -1;
                                    highlightFromRow = -1;
                                    highlightToCol = -1;
                                    highlightToRow = -1;
                                    if (!pathField.getText().trim().isEmpty()) {
                                        pendingEngineAutoReply = true;
                                        calculateBestMove(false);
                                    } else {
                                        statusLabel.setText("Select Stockfish path to play.");
                                    }
                                } else {
                                    statusLabel.setText("Your turn.");
                                }
                            } else {
                                if (!pathField.getText().trim().isEmpty()) {
                                    calculateBestMove(true);
                                }
                            }
                        }
                    }

                    selectedRow = -1;
                    selectedCol = -1;
                }

                refreshBoard();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int actualRow = isFlipped ? (7 - layoutRow) : layoutRow;
            int actualCol = isFlipped ? (7 - layoutCol) : layoutCol;
            String piece = ChessAnalyserGui.this.model.getPieceAt(actualRow, actualCol);
            boolean hasPiece = (piece != null && !piece.isEmpty());
            Color bg = ((actualRow + actualCol) % 2 == 0) ? lightSquare : darkSquare;
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            String markerStyle = (String) moveMarkerStyleChooser.getSelectedItem();
            boolean useCircleStyle = markerStyle == null || markerStyle.startsWith("Chess.com Circle");
            if (actualRow == selectedRow && actualCol == selectedCol) {
                g2.setColor(fromHighlightColor);
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else if (actualRow == highlightFromRow && actualCol == highlightFromCol) {
                if (useCircleStyle) {
                    int pad = 6;
                    g2.setColor(fromHighlightColor);
                    g2.setStroke(new BasicStroke(3.5f));
                    g2.drawOval(pad, pad, getWidth() - pad * 2, getHeight() - pad * 2);
                } else {
                    g2.setColor(fromHighlightColor);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            if (actualRow == highlightToRow && actualCol == highlightToCol) {
                if (useCircleStyle) {
                    g2.setColor(toHighlightColor);
                    if (hasPiece) {
                        int pad = 5;
                        g2.setStroke(new BasicStroke(5f));
                        g2.drawOval(pad, pad, getWidth() - pad * 2, getHeight() - pad * 2);
                    } else {
                        int dotSize = getWidth() / 3;
                        int offset = (getWidth() - dotSize) / 2;
                        g2.fillOval(offset, offset, dotSize, dotSize);
                    }
                } else {
                    g2.setColor(toHighlightColor);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            if (mateAlertsCheck.isSelected() && activeMateThreat != 0 && isKing(piece)) {
                boolean kingIsWhite = isWhitePiece(piece);
                boolean kingIsThreatened = (activeMateThreat > 0 && !kingIsWhite) || (activeMateThreat < 0 && kingIsWhite);
                if (kingIsThreatened) {
                    g2.setColor(MATE_KING_DANGER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            if (hasPiece) {
                int dropY = (int) Math.round(pieceDropOffsetY[layoutRow][layoutCol]);
                g2.translate(0, dropY);
                boolean whitePiece = isWhitePiece(piece);
                String glowMode = (String) pieceGlowChooser.getSelectedItem();
                Color coreGlow = null;

                if (glowMode != null && !glowMode.startsWith("Glow: Off")) {
                    if (glowMode.contains("CS2 Team")) {
                        coreGlow = whitePiece ? new Color(0, 220, 255) : new Color(255, 45, 75);
                    } else if (glowMode.contains("Cyberpunk")) {
                        coreGlow = whitePiece ? new Color(0, 245, 212) : new Color(255, 0, 128);
                    } else if (glowMode.contains("Solar Gold")) {
                        coreGlow = whitePiece ? new Color(255, 200, 40) : new Color(168, 85, 247);
                    } else {
                        boolean isBestMover = latestBestMove != null && latestBestMove.length() >= 4
                                && actualCol == (latestBestMove.charAt(0) - 'a')
                                && actualRow == ('8' - latestBestMove.charAt(1));
                        boolean isKingInMateDanger = activeMateThreat != 0 && isKing(piece)
                                && ((activeMateThreat > 0 && !whitePiece) || (activeMateThreat < 0 && whitePiece));
                        if (isKingInMateDanger) {
                            coreGlow = new Color(255, 30, 50);
                        } else if (isBestMover) {
                            coreGlow = new Color(50, 255, 110);
                        } else {
                            coreGlow = whitePiece ? new Color(90, 185, 255) : new Color(255, 120, 65);
                        }
                    }

                    float cx = getWidth() / 2.0f;
                    float cy = getHeight() / 2.0f;
                    float outerRadius = Math.min(getWidth(), getHeight()) * 0.49f;

                    // 1. Wide Ambient Radial Bloom Falloff
                    RadialGradientPaint outerBloom = new RadialGradientPaint(
                            new java.awt.geom.Point2D.Float(cx, cy),
                            outerRadius,
                            new float[]{0.0f, 0.45f, 1.0f},
                            new Color[]{
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 175),
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 75),
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 0)
                            }
                    );
                    g2.setPaint(outerBloom);
                    g2.fillOval((int) (cx - outerRadius), (int) (cy - outerRadius),
                            (int) (outerRadius * 2), (int) (outerRadius * 2));

                    // 2. Tight High-Intensity Inner Core Halo
                    float innerRadius = outerRadius * 0.58f;
                    RadialGradientPaint innerCore = new RadialGradientPaint(
                            new java.awt.geom.Point2D.Float(cx, cy),
                            innerRadius,
                            new float[]{0.0f, 0.7f, 1.0f},
                            new Color[]{
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 230),
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 110),
                                    new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 0)
                            }
                    );
                    g2.setPaint(innerCore);
                    g2.fillOval((int) (cx - innerRadius), (int) (cy - innerRadius),
                            (int) (innerRadius * 2), (int) (innerRadius * 2));
                }

                Color fillCol = whitePiece ? whitePieceColor : blackPieceColor;
                Color outlineCol = whitePiece ? new Color(28, 30, 34) : new Color(240, 242, 245);
                String style = (String) pieceStyleChooser.getSelectedItem();
                if (style != null && style.startsWith("Minimal Badges")) {
                    int pad = 13;
                    int d = getWidth() - pad * 2;
                    g2.setColor(fillCol);
                    g2.fillOval(pad, pad, d, d);
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(coreGlow != null ? coreGlow : outlineCol);
                    g2.drawOval(pad, pad, d, d);
                    String letter = toLetterToken(piece);
                    Font badgeFont = new Font("SansSerif", Font.BOLD, Math.max(20, pieceFont.getSize() - 22));
                    g2.setFont(badgeFont);
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(letter)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                    g2.setColor(outlineCol);
                    g2.drawString(letter, x, y);
                } else {
                    String glyph = (style != null && style.startsWith("Classic Unicode")) ? piece : toSolidGlyph(piece);
                    g2.setFont(pieceFont);
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (getWidth() - fm.stringWidth(glyph)) / 2;
                    int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();

                    // 3. Soft Neon Rim-Light Around Piece Silhouette
                    if (coreGlow != null) {
                        g2.setColor(new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 145));
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dy = -2; dy <= 2; dy++) {
                                if (dx != 0 || dy != 0) {
                                    g2.drawString(glyph, x + dx, y + dy);
                                }
                            }
                        }
                    }

                    if (style != null && style.startsWith("Outlined")) {
                        g2.setColor(outlineCol);
                        g2.drawString(glyph, x - 1, y);
                        g2.drawString(glyph, x + 1, y);
                        g2.drawString(glyph, x, y - 1);
                        g2.drawString(glyph, x, y + 1);
                    }
                    g2.setColor(fillCol);
                    g2.drawString(glyph, x, y);
                }
            }

            if (actualRow == lastGradedRow && actualCol == lastGradedCol && !lastBadgeSymbol.isEmpty()) {
                int badgeSize = 22;
                int bx = getWidth() - badgeSize - 3;
                int by = 3;
                g2.setColor(lastBadgeColor);
                g2.fillOval(bx, by, badgeSize, badgeSize);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(bx, by, badgeSize, badgeSize);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                FontMetrics bfm = g2.getFontMetrics();
                int tx = bx + (badgeSize - bfm.stringWidth(lastBadgeSymbol)) / 2;
                int ty = by + ((badgeSize - bfm.getHeight()) / 2) + bfm.getAscent();
                g2.drawString(lastBadgeSymbol, tx, ty);
            }
            g2.dispose();
        }
    }

    private void resetGameClockFromSelection() {

        String sel = (String) timeControlChooser.getSelectedItem();
            if (sel == null || sel.startsWith("Clock: Off")) {
                clockEnabled = false;
                whiteClockLabel.setText("White: --:--");
                blackClockLabel.setText("Black: --:--");
                return;
            }
            clockEnabled = true;
            if (sel.startsWith("1+0")) {
                whiteTimeMs = 60_000L;
                blackTimeMs = 60_000L;
                incrementMs = 0L;
            } else if (sel.startsWith("3+0")) {
                whiteTimeMs = 180_000L;
                blackTimeMs = 180_000L;
                incrementMs = 0L;
            } else if (sel.startsWith("3+2")) {
                whiteTimeMs = 180_000L;
                blackTimeMs = 180_000L;
                incrementMs = 2_000L;
            } else if (sel.startsWith("5+0")) {
                whiteTimeMs = 300_000L;
                blackTimeMs = 300_000L;
                incrementMs = 0L;
            } else {
                whiteTimeMs = 600_000L;
                blackTimeMs = 600_000L;
                incrementMs = 0L;
            }
            updateClockLabels();
        }

        private void tickGameClock() {
            if (!clockEnabled || playedUciMoves.isEmpty()) return;
            String fen = model.toFen();
            int spaceIdx = fen.indexOf(' ');
            boolean whiteTurn = spaceIdx < 0 || spaceIdx + 1 >= fen.length() || fen.charAt(spaceIdx + 1) == 'w';

            if (whiteTurn) {
                whiteTimeMs = Math.max(0L, whiteTimeMs - 100L);
                if (whiteTimeMs == 0L) {
                    clockEnabled = false;
                    statusLabel.setText("Time out! Black wins on time.");
                    logArea.append("\n[CLOCK] White flagged! Black wins on time.");
                }
            } else {
                blackTimeMs = Math.max(0L, blackTimeMs - 100L);
                if (blackTimeMs == 0L) {
                    clockEnabled = false;
                    statusLabel.setText("Time out! White wins on time.");
                    logArea.append("\n[CLOCK] Black flagged! White wins on time.");
                }
            }
            updateClockLabels();
        }

        private void updateClockLabels() {
            whiteClockLabel.setText("W: " + formatClockMs(whiteTimeMs));
            blackClockLabel.setText("B: " + formatClockMs(blackTimeMs));
        }

        private String formatClockMs(long ms) {
            long totalSec = (ms + 999L) / 1000L;
            long mins = totalSec / 60L;
            long secs = totalSec % 60L;
            return String.format("%02d:%02d", mins, secs);
        }
    private void startPieceAnimation(int fromRow, int fromCol, int toRow, int toCol) {
        String movingPiece = model.getPieceAt(fromRow, fromCol);
        if (movingPiece == null || movingPiece.isEmpty()) return;

        if (pieceAnimTimer != null && pieceAnimTimer.isRunning()) {
            pieceAnimTimer.stop();
        }
        animatingPieceToken = movingPiece;
        animFromRow = fromRow;
        animFromCol = fromCol;
        animToRow = toRow;
        animToCol = toCol;
        animProgress = 0.0;
        isAnimatingMove = true;

        final long startTime = System.currentTimeMillis();
        final long durationMs = 155L;

        pieceAnimTimer = new Timer(12, e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            animProgress = Math.min(1.0, (double) elapsed / durationMs);
            if (boardAnimationLayer != null) {
                boardAnimationLayer.repaint();
            }
            if (animProgress >= 1.0) {
                ((Timer) e.getSource()).stop();
                isAnimatingMove = false;
                animatingPieceToken = "";
                refreshBoard();
            }
        });
        pieceAnimTimer.start();
    }
    private void drawPieceWithGlowAt(Graphics2D g2, String piece, int centerX, int centerY, int sqW, int sqH) {
        boolean whitePiece = isWhitePiece(piece);
        String glowMode = (String) pieceGlowChooser.getSelectedItem();
        Color coreGlow = null;

        if (glowMode != null && !glowMode.startsWith("Glow: Off")) {
            if (glowMode.contains("CS2 Team")) {
                coreGlow = whitePiece ? new Color(0, 220, 255) : new Color(255, 45, 75);
            } else if (glowMode.contains("Cyberpunk")) {
                coreGlow = whitePiece ? new Color(0, 245, 212) : new Color(255, 0, 128);
            } else if (glowMode.contains("Solar Gold")) {
                coreGlow = whitePiece ? new Color(255, 200, 40) : new Color(168, 85, 247);
            } else {
                coreGlow = new Color(50, 255, 110);
            }

            float outerRadius = Math.min(sqW, sqH) * 0.52f;
            RadialGradientPaint outerBloom = new RadialGradientPaint(
                    new java.awt.geom.Point2D.Float(centerX, centerY),
                    outerRadius,
                    new float[]{0.0f, 0.45f, 1.0f},
                    new Color[]{
                            new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 190),
                            new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 85),
                            new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 0)
                    }
            );
            g2.setPaint(outerBloom);
            g2.fillOval((int) (centerX - outerRadius), (int) (centerY - outerRadius),
                    (int) (outerRadius * 2), (int) (outerRadius * 2));
        }

        Color fillCol = whitePiece ? whitePieceColor : blackPieceColor;
        Color outlineCol = whitePiece ? new Color(28, 30, 34) : new Color(240, 242, 245);
        String style = (String) pieceStyleChooser.getSelectedItem();

        if (style != null && style.startsWith("Minimal Badges")) {
            int d = sqW - 26;
            g2.setColor(fillCol);
            g2.fillOval(centerX - d / 2, centerY - d / 2, d, d);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(coreGlow != null ? coreGlow : outlineCol);
            g2.drawOval(centerX - d / 2, centerY - d / 2, d, d);
            String letter = toLetterToken(piece);
            Font badgeFont = new Font("SansSerif", Font.BOLD, Math.max(20, pieceFont.getSize() - 22));
            g2.setFont(badgeFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(outlineCol);
            g2.drawString(letter, centerX - fm.stringWidth(letter) / 2, centerY - fm.getHeight() / 2 + fm.getAscent());
        } else {
            String glyph = (style != null && style.startsWith("Classic Unicode")) ? piece : toSolidGlyph(piece);
            g2.setFont(pieceFont);
            FontMetrics fm = g2.getFontMetrics();
            int x = centerX - fm.stringWidth(glyph) / 2;
            int y = centerY - fm.getHeight() / 2 + fm.getAscent();

            if (coreGlow != null) {
                g2.setColor(new Color(coreGlow.getRed(), coreGlow.getGreen(), coreGlow.getBlue(), 155));
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (dx != 0 || dy != 0) g2.drawString(glyph, x + dx, y + dy);
                    }
                }
            }
            if (style != null && style.startsWith("Outlined")) {
                g2.setColor(outlineCol);
                g2.drawString(glyph, x - 1, y);
                g2.drawString(glyph, x + 1, y);
                g2.drawString(glyph, x, y - 1);
                g2.drawString(glyph, x, y + 1);
            }
            g2.setColor(fillCol);
            g2.drawString(glyph, x, y);
        }
    }
        private class EngineCallbacks implements StockfishEngine.Listener {
            @Override
            public void onEval(Integer scoreCp, Integer mateIn, int depth, String pv) {
                SwingUtilities.invokeLater(() -> {
                    evalBar.setEvaluation(scoreCp, mateIn);
                    if (mateIn != null && mateIn.intValue() != 0) {
                        int mVal = mateIn.intValue();
                        String prefix = mVal > 0 ? "+M" : "-M";
                        evalLabel.setText("Eval: " + prefix + Math.abs(mVal) + " (Depth " + depth + ")");
                        updateMateNotification(mateIn);
                        refreshBoard();
                    } else if (scoreCp != null) {
                        double pawns = scoreCp.intValue() / 100.0;
                        evalLabel.setText(String.format("Eval: %+.2f (Depth %d)", pawns, depth));
                        if (activeMateThreat != 0) {
                            clearMateAlert();
                            refreshBoard();
                        }
                    }
                    if (pv != null && !pv.isEmpty()) {
                        pvLabel.setText("Best Line: " + pv);
                    }
                });
            }

            @Override
            public void onBestMove(String move) {
                SwingUtilities.invokeLater(() -> {
                    evaluateAndGradeLastMove(evalBar.getWhiteWinPercentage());
                    if (currentAnalysisIsBackgroundEval) {
                        if (isPlayVsStockfishActive()) {
                            statusLabel.setText("Your turn (" + lastMoveGradeLabel + ")");
                        } else {
                            statusLabel.setText("Opponent's turn (" + lastMoveGradeLabel + ")");
                        }
                        return;
                    }
                    statusLabel.setText("Best Move: " + move);
                    logArea.append("\nBest: " + move);
                    latestBestMove = move;
                    if (move != null && move.length() >= 4 && !"none".equals(move)) {
                        if ((pendingEngineAutoReply && isPlayVsStockfishActive()) || isStockfishVsStockfishActive()) {
                            pendingEngineAutoReply = false;
                            Timer replyTimer = new Timer(350, evt -> applyAutoplayMove(true));
                            replyTimer.setRepeats(false);
                            replyTimer.start();
                            return;
                        }


                        autoplayButton.setEnabled(true);
                        highlightFromCol = move.charAt(0) - 'a';
                        highlightFromRow = '8' - move.charAt(1);
                        highlightToCol = move.charAt(2) - 'a';
                        highlightToRow = '8' - move.charAt(3);
                        refreshBoard();
                    } else {
                        pendingEngineAutoReply = false;
                        autoplayButton.setEnabled(false);
                    }
                });
            }

            @Override
            public void onInfo(String info) {
                SwingUtilities.invokeLater(() -> {
                    if (info.contains("Engine ready.")) {
                        logArea.append("\nStockfish connected.");
                        if (analysisQueued) {
                            analysisQueued = false;
                            statusLabel.setText(pendingEngineAutoReply ? "Stockfish thinking..." : (currentAnalysisIsBackgroundEval ? "Grading move..." : "Calculating..."));
                            engine.analyze(queuedFen, queuedDepth, queuedMoveTime);
                        }
                    }
                    if (!currentAnalysisIsBackgroundEval && !pendingEngineAutoReply && (info.contains("score") || info.contains("depth") || info.startsWith("Error:"))) {
                        logArea.append("\n" + info);
                    }
                });
            }
        }
}