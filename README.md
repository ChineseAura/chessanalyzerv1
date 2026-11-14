# ChessAnalyser

A Java Swing desktop chess board, engine analyzer, and bot simulator powered by **Stockfish**.

## Features

- **Three Game & Analysis Modes**
  - **Analysis Mode (Manual):** Analyze any position, set up custom boards with the piece painter, and explore top engine lines.
  - **Play vs. Stockfish (Auto-Reply):** Play as White or Black against Stockfish at configurable skill levels or target Elo ratings (1320–3190).
  - **Stockfish vs. Stockfish (Watch):** Pit two configurable bot ratings against each other with **dynamic per-move Elo fluctuations and momentum modifiers** so evenly matched bots trade blows and play decisive games.
- **Live Evaluation & Accuracy Tracking**
  - Real-time evaluation bar, principal variation (best line), and checkmate threat alerts.
  - Automatic move-by-move accuracy percentages for both sides, opening/defense book detection, and live captured-piece material trays.
- **Custom Themes & Visual Effects**
  - Customizable board square colors, piece colors, radial piece glow presets (CS2 Team, Cyberpunk, Solar Gold, Tactical), move marker styles, sound effects, and a cascade drop-and-bounce animation when resetting the board.

## Requirements

- **Java JDK 17+** (or compatible standard JDK with Swing)
- **Stockfish Engine Binary** (download the official executable for your OS from [stockfishchess.org](https://stockfishchess.org))

## Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com<your-username>/ChessAnalyser.git
   cd ChessAnalyser
   ```
2. **Compile and run:**
   ```bash
   javac -d out src/*.java
   java -cp out Main
   ```
3. **Connect Stockfish:**
   - Open the **Engine** tab in the sidebar, select your downloaded `stockfish` executable path, and start analyzing or watching bot matches.
