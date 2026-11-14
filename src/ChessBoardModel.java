import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the piece placement on an 8x8 grid and handles standard FEN string conversions.
 */
public final class ChessBoardModel {

    // Internal board mapping row,col to the respective unicode representation
    private final String[][] grid = new String[8][8];

    // Standard translation maps between piece characters and unicode display variants
    private static final Map<Character, String> CHAR_TO_UNICODE = new HashMap<>();
    private static final Map<String, Character> UNICODE_TO_CHAR = new HashMap<>();

    static {
        // White pieces
        CHAR_TO_UNICODE.put('K', "\u2654");
        CHAR_TO_UNICODE.put('Q', "\u2655");
        CHAR_TO_UNICODE.put('R', "\u2656");
        CHAR_TO_UNICODE.put('B', "\u2657");
        CHAR_TO_UNICODE.put('N', "\u2658");
        CHAR_TO_UNICODE.put('P', "\u2659");

        // Black pieces
        CHAR_TO_UNICODE.put('k', "\u265A");
        CHAR_TO_UNICODE.put('q', "\u265B");
        CHAR_TO_UNICODE.put('r', "\u265C");
        CHAR_TO_UNICODE.put('b', "\u265D");
        CHAR_TO_UNICODE.put('n', "\u265E");
        CHAR_TO_UNICODE.put('p', "\u265F");

        for (Map.Entry<Character, String> entry : CHAR_TO_UNICODE.entrySet()) {
            UNICODE_TO_CHAR.put(entry.getValue(), entry.getKey());
        }
    }

    private char activeColor = 'w';
    private String castlingRights = "KQkq";
    private String enPassantTarget = "-";
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    public ChessBoardModel() {
        // Automatically initialize to the standard starting position
        try {
            loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * Parses a standard FEN string to populate the board state components.
     */
    public void loadFen(String fen) {
        if (fen == null || fen.trim().isEmpty()) {
            throw new IllegalArgumentException("FEN cannot be empty");
        }

        String[] parts = fen.trim().split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid FEN format");
        }

        // Clean out active array data
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                grid[r][c] = "";
            }
        }

        // Parse piece layout segment
        String[] rows = parts[0].split("/");
        if (rows.length != 8) {
            throw new IllegalArgumentException("FEN must contain exactly 8 ranks separated by '/'");
        }

        for (int r = 0; r < 8; r++) {
            String rowStr = rows[r];
            int colIdx = 0;
            for (int i = 0; i < rowStr.length(); i++) {
                char ch = rowStr.charAt(i);
                if (Character.isDigit(ch)) {
                    int emptySquares = ch - '0';
                    colIdx += emptySquares;
                } else {
                    if (colIdx >= 8) {
                        throw new IllegalArgumentException("Rank sequence exceeds 8 files at rank " + (8 - r));
                    }
                    String unicodePiece = CHAR_TO_UNICODE.get(ch);
                    if (unicodePiece == null) {
                        throw new IllegalArgumentException("Unknown piece token: " + ch);
                    }
                    grid[r][colIdx] = unicodePiece;
                    colIdx++;
                }
            }
        }

        // Parse trailing structural metadata attributes if present
        if (parts.length > 1) activeColor = parts[1].length() == 1 ? parts[1].charAt(0) : 'w';
        if (parts.length > 2) castlingRights = parts[2];
        if (parts.length > 3) enPassantTarget = parts[3];
        try {
            if (parts.length > 4) halfmoveClock = Integer.parseInt(parts[4]);
            if (parts.length > 5) fullmoveNumber = Integer.parseInt(parts[5]);
        } catch (NumberFormatException ignored) {}
    }

    /**
     * Generates a standard FEN layout string corresponding to the live grid coordinates.
     */
    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int emptyCount = 0;
            for (int c = 0; c < 8; c++) {
                String val = grid[r][c];
                if (val == null || val.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    Character ch = UNICODE_TO_CHAR.get(val);
                    sb.append(ch != null ? ch : '?');
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount);
            }
            if (r < 7) {
                sb.append('/');
            }
        }

        // Concat background structural tokens
        sb.append(" ").append(activeColor)
                .append(" ").append(castlingRights)
                .append(" ").append(enPassantTarget)
                .append(" ").append(halfmoveClock)
                .append(" ").append(fullmoveNumber);

        return sb.toString();
    }

    /**
     * Retrieves the piece rendering code at a designated position matrix index.
     */
    /**
     * Retrieves the piece rendering code at a designated position matrix index.
     */
    public String getPieceAt(int row, int col) {
        if (row >= 0 && row < 8 && col >= 0 && col < 8) {
            return grid[row][col];
        }
        return "";
    }

    public void setPieceAt(int row, int col, String unicodePiece) {
        if (row >= 0 && row < 8 && col >= 0 && col < 8) {
            grid[row][col] = (unicodePiece == null) ? "" : unicodePiece;
        }
    }

    /**
     * Manually moves a piece from one square to another on the grid matrix.
     */
    /**
     * Manually moves a piece from one square to another on the grid matrix,
     * ensuring white cannot capture white and black cannot capture black.
     */
    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow >= 0 && fromRow < 8 && fromCol >= 0 && fromCol < 8 &&
                toRow >= 0 && toRow < 8 && toCol >= 0 && toCol < 8) {

            String fromPiece = grid[fromRow][fromCol];
            String toPiece = grid[toRow][toCol];

            // If the destination isn't empty, check colors
            if (toPiece != null && !toPiece.isEmpty()) {
                boolean isFromWhite = isWhitePiece(fromPiece);
                boolean isToWhite = isWhitePiece(toPiece);

                // If they are the same color, block the move!
                if (isFromWhite == isToWhite) {
                    return;
                }
            }

            // Execute move/capture
            grid[toRow][toCol] = fromPiece;
            grid[fromRow][fromCol] = "";

            // Alternate player turn color
            activeColor = (activeColor == 'w') ? 'b' : 'w';
        }
    }

    /**
     * Helper to determine piece color using internal unicode ranges
     */
    private boolean isWhitePiece(String unicodeStr) {
        if (unicodeStr == null || unicodeStr.isEmpty()) return false;
        char ch = unicodeStr.charAt(0);
        // White chess pieces are in the unicode range \u2654 to \u2659
        return ch >= '\u2654' && ch <= '\u2659';
    }
}

