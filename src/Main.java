import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            System.err.println("Using the default Swing look and feel: " + ex.getMessage());
        }
        // Fixed: Changed ChessAnalysisGUI to ChessAnalyserGui to match your file exactly
        SwingUtilities.invokeLater(() -> new ChessAnalyserGui().setVisible(true));
    }
}
