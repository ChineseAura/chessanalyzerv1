import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class StockfishEngine {

    private static final long STARTUP_TIMEOUT_SECONDS = 8L;
    private static final long QUIT_WAIT_MILLIS = 1200L;

    private enum State {
        IDLE, STARTING, WAITING_UCIOK, WAITING_READY, READY, WAITING_READY_FOR_GO, SEARCHING
    }

    private final Listener listener;
    private final Object lock = new Object();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stockfish-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private Process process;
    private BufferedWriter stdin;
    private State state = State.IDLE;
    private int generation;
    private boolean closed;

    private String searchFen = "";
    private int searchDepth = 12;
    private int searchMoveTimeMs = 1000;
    private int skillLevel = 20;
    private boolean limitStrength = false;
    private int targetElo = 1500;
    private int threads = 2;
    private int hashMb = 64;

    private Integer latestScoreCp;
    private Integer latestMate;
    private int latestDepth;
    private String latestPv = "";

    public StockfishEngine(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void configure(int skillLevel, boolean limitStrength, int targetElo, int threads, int hashMb) {
        synchronized (lock) {
            this.skillLevel = Math.max(0, Math.min(20, skillLevel));
            this.limitStrength = limitStrength;
            this.targetElo = Math.max(1320, Math.min(3190, targetElo));
            this.threads = Math.max(1, Math.min(32, threads));
            this.hashMb = Math.max(16, Math.min(2048, hashMb));
            if (state == State.READY && process != null && process.isAlive()) {
                try {
                    applyOptionsLocked();
                } catch (RuntimeException ignored) {}
            }
        }
    }

    private void applyOptionsLocked() {
        writeLocked("setoption name Threads value " + threads);
        writeLocked("setoption name Hash value " + hashMb);
        writeLocked("setoption name UCI_LimitStrength value " + limitStrength);
        if (limitStrength) {
            writeLocked("setoption name UCI_Elo value " + targetElo);
        } else {
            writeLocked("setoption name Skill Level value " + skillLevel);
        }
    }

    public void start(Path executable) {
        Path path = Objects.requireNonNull(executable, "executable").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Stockfish executable was not found: " + path);
        }

        Process previous;
        int generationAtStart;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Engine is already shut down.");
            }
            generationAtStart = ++generation;
            previous = detachProcess();
            state = State.STARTING;
            clearSearchStats();
        }
        destroyProcess(previous, false);

        Thread launch = new Thread(() -> launch(path, generationAtStart), "stockfish-launch");
        launch.setDaemon(true);
        launch.start();
    }

    public void analyze(String fen, int depth, int moveTimeMs) {
        Objects.requireNonNull(fen, "fen");
        if (depth < 1 || depth > 40) {
            throw new IllegalArgumentException("Depth must be from 1 to 40.");
        }
        if (moveTimeMs < 50 || moveTimeMs > 120_000) {
            throw new IllegalArgumentException("Move time must be from 50 to 120000 milliseconds.");
        }
        if (fen.indexOf('\n') >= 0 || fen.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("FEN must be a single line.");
        }

        List<String> sent = new ArrayList<>(3);
        Process abandoned = null;
        RuntimeException failure = null;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Engine is already shut down.");
            }
            if (state != State.READY || process == null || !process.isAlive()) {
                throw new IllegalStateException("Engine is not ready. Check the Stockfish path and try again.");
            }
            searchFen = fen;
            searchDepth = depth;
            searchMoveTimeMs = moveTimeMs;
            state = State.WAITING_READY_FOR_GO;
            clearSearchStats();
            try {
                applyOptionsLocked();
                writeLocked("isready");
                sent.add("isready");
            } catch (RuntimeException ex) {

                generation++;
                abandoned = detachProcess();
                state = State.IDLE;
                failure = ex;
            }
        }
        if (failure != null) {
            destroyProcess(abandoned, false);
            throw failure;
        }
        sent.forEach(command -> notifyLog("> " + command));
    }

    public boolean isReady() {
        synchronized (lock) {
            return state == State.READY && process != null && process.isAlive();
        }
    }

    public boolean isAlive() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    public void close() {
        Process old;
        List<String> sent = new ArrayList<>(1);
        synchronized (lock) {
            if (closed && process == null) {
                watchdog.shutdownNow();
                return;
            }
            closed = true;
            generation++;
            try {
                if (stdin != null && process != null && process.isAlive()) {
                    writeLocked("quit");
                    sent.add("quit");
                }
            } catch (RuntimeException ignored) {}
            old = detachProcess();
            state = State.IDLE;
        }
        sent.forEach(command -> notifyLog("> " + command));
        destroyProcess(old, true);
        watchdog.shutdownNow();
    }

    private void launch(Path path, int launchGeneration) {
        ProcessBuilder builder = new ProcessBuilder(path.toString());
        Path workingDirectory = path.getParent();
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process started;
        try {
            started = builder.start();
        } catch (IOException ex) {
            failLaunch(launchGeneration, "Could not start Stockfish: " + ex.getMessage());
            return;
        }

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
        boolean accepted;
        synchronized (lock) {
            accepted = launchGeneration == generation && !closed;
            if (accepted) {
                process = started;
                stdin = writer;
                state = State.WAITING_UCIOK;
            }
        }
        if (!accepted) {
            closeQuietly(writer);
            destroyProcess(started, false);
            return;
        }

        startStdoutReader(started, launchGeneration);
        startStderrReader(started, launchGeneration);
        try {
            send(launchGeneration, "uci");
        } catch (IllegalStateException ex) {
            failLaunch(launchGeneration, ex.getMessage());
            return;
        }
        armStartupTimeout(started, launchGeneration);
    }

    private void failLaunch(int launchGeneration, String message) {
        Process old = null;
        boolean current;
        synchronized (lock) {
            current = launchGeneration == generation && !closed;
            if (current) {
                generation++;
                old = detachProcess();
                state = State.IDLE;
            }
        }
        destroyProcess(old, false);
        if (current) {
            notifyError(message);
        }
    }

    private void armStartupTimeout(Process started, int launchGeneration) {
        watchdog.schedule(() -> {
            boolean timedOut = false;
            synchronized (lock) {
                boolean stillStarting = state == State.STARTING
                        || state == State.WAITING_UCIOK
                        || state == State.WAITING_READY;
                if (launchGeneration == generation && stillStarting) {
                    generation++;
                    detachProcess();
                    state = State.IDLE;
                    timedOut = true;
                }
            }
            if (timedOut) {
                destroyProcess(started, false);
                notifyError("Stockfish failed to respond within timeout.");
            }
        }, STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void send(int activeGeneration, String command) {
        synchronized (lock) {
            if (activeGeneration != generation || closed) {
                throw new IllegalStateException("Command abandoned.");
            }
            writeLocked(command);
        }
        notifyLog("> " + command);
    }

    private void writeLocked(String line) {
        if (stdin == null) {
            throw new IllegalStateException("Process streams are unavailable.");
        }
        try {
            stdin.write(line);
            stdin.write("\n");
            stdin.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed communicating with engine: " + ex.getMessage(), ex);
        }
    }
    private Process detachProcess() {
        Process old = process;
        process = null;
        closeQuietly(stdin);
        stdin = null;
        return old;
    }
    private void destroyProcess(Process p, boolean gracefully) {
        if (p == null) return;
        if (gracefully) {
            try {
                if (p.waitFor(QUIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        p.destroyForcibly();
    }
    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {}
        }
    }
    private void startStdoutReader(Process started, int activeGeneration) {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    parseStdoutLine(line, activeGeneration);
                }
            } catch (IOException ignored) {}
            handleProcessExit(started, activeGeneration);
        }, "stockfish-stdout-" + activeGeneration);
        reader.setDaemon(true);
        reader.start();
    }
    private void startStderrReader(Process started, int activeGeneration) {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    notifyLog("[stderr] " + line);
                }
            } catch (IOException ignored) {}
        }, "stockfish-stderr-" + activeGeneration);
        reader.setDaemon(true);
        reader.start();
    }
    private void parseStdoutLine(String line, int activeGeneration) {
        notifyLog("< " + line);
        String trimmed = line.trim();
        String[] tokens = trimmed.split("\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return;
        synchronized (lock) {
            if (activeGeneration != generation || closed) return;
            switch (state) {
                case WAITING_UCIOK:
                    if ("uciok".equals(tokens[0])) {
                        state = State.WAITING_READY;
                        try {
                            applyOptionsLocked();
                            writeLocked("isready");
                        } catch (RuntimeException ex) {
                            failLaunch(activeGeneration, ex.getMessage());
                            return;
                        }
                        notifyLog("> isready");
                    }
                    break;
                case WAITING_READY:
                    if ("readyok".equals(tokens[0])) {
                        state = State.READY;
                        notifyReady();
                    }
                    break;
                case WAITING_READY_FOR_GO:
                    if ("readyok".equals(tokens[0])) {
                        state = State.SEARCHING;
                        try {
                            writeLocked("position fen " + searchFen);
                            notifyLog("> position fen " + searchFen);
                            String goCmd = String.format(Locale.ROOT, "go depth %d movetime %d", searchDepth, searchMoveTimeMs);
                            writeLocked(goCmd);
                            notifyLog("> " + goCmd);
                        } catch (RuntimeException ex) {
                            generation++;
                            destroyProcess(detachProcess(), false);
                            state = State.IDLE;
                            notifyError(ex.getMessage());
                        }
                    }
                    break;
                case SEARCHING:
                    if ("bestmove".equals(tokens[0])) {
                        state = State.READY;
                        String move = tokens.length > 1 ? tokens[1] : "none";
                        listener.onBestMove(move);
                    } else if ("info".equals(tokens[0])) {
                        parseInfoTokens(tokens);
                    }
                    break;
                default:
                    if ("info".equals(tokens[0])) {
                        parseInfoTokens(tokens);
                    }
                    break;
            }
        }
    }
    private void parseInfoTokens(String[] tokens) {
        boolean isBlackTurn = searchFen.contains(" b ");
        int perspective = isBlackTurn ? -1 : 1;
        for (int i = 1; i < tokens.length - 1; i++) {
            if ("depth".equals(tokens[i])) {
                try {
                    latestDepth = Integer.parseInt(tokens[i + 1]);
                } catch (NumberFormatException ignored) {}
            } else if ("score".equals(tokens[i]) && i < tokens.length - 2) {
                if ("cp".equals(tokens[i + 1])) {
                    try {
                        latestScoreCp = Integer.parseInt(tokens[i + 2]) * perspective;
                        latestMate = null;
                    } catch (NumberFormatException ignored) {}
                } else if ("mate".equals(tokens[i + 1])) {
                    try {
                        latestMate = Integer.parseInt(tokens[i + 2]) * perspective;
                        latestScoreCp = null;
                    } catch (NumberFormatException ignored) {}
                }
            } else if ("pv".equals(tokens[i])) {
                StringBuilder pvBuilder = new StringBuilder();
                int maxMoves = Math.min(tokens.length, i + 7);
                for (int j = i + 1; j < maxMoves; j++) {
                    if (pvBuilder.length() > 0) pvBuilder.append(" ");
                    pvBuilder.append(tokens[j]);
                }
                latestPv = pvBuilder.toString();
                break;
            }
        }
        if (latestScoreCp != null || latestMate != null) {
            listener.onEval(latestScoreCp, latestMate, latestDepth, latestPv);
        }
        listener.onInfo(String.join(" ", tokens));
    }
    private void handleProcessExit(Process started, int activeGeneration) {
        boolean crashed = false;
        synchronized (lock) {
            if (activeGeneration == generation && !closed && state != State.IDLE) {
                generation++;
                detachProcess();
                state = State.IDLE;
                crashed = true;
            }
        }
        if (crashed) {
            notifyError("Stockfish process terminated unexpectedly.");
        }
    }
    private void clearSearchStats() {
        latestScoreCp = null;
        latestMate = null;
        latestDepth = 0;
        latestPv = "";
    }
    private void notifyReady() {
        listener.onInfo("Engine ready.");
    }
    private void notifyError(String msg) {
        listener.onInfo("Error: " + msg);
    }
    private void notifyLog(String line) {
        listener.onInfo(line);
    }
    public interface Listener {
        void onBestMove(String move);
        void onInfo(String info);
        default void onEval(Integer scoreCp, Integer mateIn, int depth, String pv) {}
    }
}