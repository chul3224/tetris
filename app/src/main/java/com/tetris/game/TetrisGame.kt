package com.tetris.game

import android.content.Context
import android.content.SharedPreferences

enum class GameState {
    IDLE, PLAYING, PAUSED, GAME_OVER
}

class TetrisGame(private val context: Context) {

    companion object {
        const val BOARD_WIDTH = 10
        const val BOARD_HEIGHT = 20
        const val BASE_SPEED_MS = 800L
        const val MIN_SPEED_MS = 100L
        const val SPEED_DECREMENT = 50L
        const val LINES_PER_LEVEL = 10
    }

    // Board: null = empty, Int = color of locked piece
    val board = Array(BOARD_HEIGHT) { arrayOfNulls<Int>(BOARD_WIDTH) }

    var currentPiece: Tetromino? = null
    var nextPiece: Tetromino = Tetromino.random()
    var holdPiece: Tetromino? = null
    var ghostPiece: Tetromino? = null
    var canHold: Boolean = true

    var score: Int = 0
    var level: Int = 1
    var lines: Int = 0
    var highScore: Int = 0
    var gameState: GameState = GameState.IDLE

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)

    init {
        highScore = prefs.getInt("high_score", 0)
    }

    fun getDropInterval(): Long {
        val speed = BASE_SPEED_MS - ((level - 1) * SPEED_DECREMENT)
        return maxOf(speed, MIN_SPEED_MS)
    }

    fun startGame() {
        clearBoard()
        score = 0
        level = 1
        lines = 0
        canHold = true
        holdPiece = null
        nextPiece = Tetromino.random()
        spawnPiece()
        gameState = GameState.PLAYING
    }

    fun pauseGame() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED
        }
    }

    fun resumeGame() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING
        }
    }

    fun togglePause() {
        when (gameState) {
            GameState.PLAYING -> pauseGame()
            GameState.PAUSED -> resumeGame()
            else -> {}
        }
    }

    private fun clearBoard() {
        for (row in board) {
            row.fill(null)
        }
    }

    private fun spawnPiece() {
        val piece = nextPiece.copy(
            x = BOARD_WIDTH / 2 - 2,
            y = 0,
            rotation = 0
        )
        nextPiece = Tetromino.random()

        if (!isValidPosition(piece)) {
            // Game over
            gameState = GameState.GAME_OVER
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt("high_score", highScore).apply()
            }
            currentPiece = null
        } else {
            currentPiece = piece
            canHold = true
            updateGhostPiece()
        }
    }

    fun moveLeft(): Boolean {
        val piece = currentPiece ?: return false
        if (gameState != GameState.PLAYING) return false
        val moved = piece.copy(x = piece.x - 1)
        return if (isValidPosition(moved)) {
            currentPiece = moved
            updateGhostPiece()
            true
        } else false
    }

    fun moveRight(): Boolean {
        val piece = currentPiece ?: return false
        if (gameState != GameState.PLAYING) return false
        val moved = piece.copy(x = piece.x + 1)
        return if (isValidPosition(moved)) {
            currentPiece = moved
            updateGhostPiece()
            true
        } else false
    }

    fun moveDown(): Boolean {
        val piece = currentPiece ?: return false
        if (gameState != GameState.PLAYING) return false
        val moved = piece.copy(y = piece.y + 1)
        return if (isValidPosition(moved)) {
            currentPiece = moved
            true
        } else {
            lockPiece()
            false
        }
    }

    fun hardDrop() {
        if (gameState != GameState.PLAYING) return
        val ghost = ghostPiece ?: return
        val piece = currentPiece ?: return
        val dropDistance = ghost.y - piece.y
        score += dropDistance * 2
        currentPiece = ghost.copy()
        lockPiece()
    }

    fun rotate(direction: Int = 1): Boolean {
        val piece = currentPiece ?: return false
        if (gameState != GameState.PLAYING) return false
        val rotated = piece.rotated(direction)

        // Try basic rotation
        if (isValidPosition(rotated)) {
            currentPiece = rotated
            updateGhostPiece()
            return true
        }

        // Wall kick attempts: try offsets
        val kicks = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(-2, 0), Pair(2, 0),
            Pair(0, -1), Pair(-1, -1), Pair(1, -1)
        )
        for ((dx, dy) in kicks) {
            val kicked = rotated.copy(x = rotated.x + dx, y = rotated.y + dy)
            if (isValidPosition(kicked)) {
                currentPiece = kicked
                updateGhostPiece()
                return true
            }
        }
        return false
    }

    fun holdCurrentPiece() {
        if (!canHold || gameState != GameState.PLAYING) return
        val piece = currentPiece ?: return

        canHold = false
        val heldType = holdPiece?.type
        holdPiece = Tetromino(piece.type)

        if (heldType != null) {
            val restored = Tetromino(
                type = heldType,
                x = BOARD_WIDTH / 2 - 2,
                y = 0,
                rotation = 0
            )
            if (isValidPosition(restored)) {
                currentPiece = restored
                updateGhostPiece()
            } else {
                gameState = GameState.GAME_OVER
                if (score > highScore) {
                    highScore = score
                    prefs.edit().putInt("high_score", highScore).apply()
                }
            }
        } else {
            spawnPiece()
        }
    }

    private fun lockPiece() {
        val piece = currentPiece ?: return
        val shape = piece.getShape()
        val color = piece.getColor()

        for (row in shape.indices) {
            for (col in shape[row].indices) {
                if (shape[row][col] != 0) {
                    val boardY = piece.y + row
                    val boardX = piece.x + col
                    if (boardY in 0 until BOARD_HEIGHT && boardX in 0 until BOARD_WIDTH) {
                        board[boardY][boardX] = color
                    }
                }
            }
        }

        currentPiece = null
        ghostPiece = null

        val clearedLines = clearLines()
        addScore(clearedLines)

        if (gameState == GameState.PLAYING) {
            spawnPiece()
        }
    }

    private fun clearLines(): Int {
        val fullRows = mutableListOf<Int>()
        for (row in 0 until BOARD_HEIGHT) {
            if (board[row].all { it != null }) {
                fullRows.add(row)
            }
        }

        for (row in fullRows) {
            // Shift rows down
            for (r in row downTo 1) {
                board[r] = board[r - 1].copyOf()
            }
            board[0] = arrayOfNulls(BOARD_WIDTH)
        }

        return fullRows.size
    }

    private fun addScore(clearedLines: Int) {
        if (clearedLines == 0) return

        val linePoints = when (clearedLines) {
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> 800
        }
        score += linePoints * level
        lines += clearedLines

        val newLevel = (lines / LINES_PER_LEVEL) + 1
        if (newLevel > level) {
            level = newLevel
        }
    }

    private fun updateGhostPiece() {
        val piece = currentPiece ?: run {
            ghostPiece = null
            return
        }
        var ghost = piece.copy()
        while (isValidPosition(ghost.copy(y = ghost.y + 1))) {
            ghost = ghost.copy(y = ghost.y + 1)
        }
        ghostPiece = ghost
    }

    fun isValidPosition(piece: Tetromino): Boolean {
        val shape = piece.getShape()
        for (row in shape.indices) {
            for (col in shape[row].indices) {
                if (shape[row][col] != 0) {
                    val boardX = piece.x + col
                    val boardY = piece.y + row
                    if (boardX < 0 || boardX >= BOARD_WIDTH) return false
                    if (boardY >= BOARD_HEIGHT) return false
                    if (boardY >= 0 && board[boardY][boardX] != null) return false
                }
            }
        }
        return true
    }

    fun softDrop() {
        if (gameState != GameState.PLAYING) return
        if (moveDown()) {
            score += 1
        }
    }

    fun isGameOver() = gameState == GameState.GAME_OVER
    fun isPlaying() = gameState == GameState.PLAYING
    fun isPaused() = gameState == GameState.PAUSED
    fun isIdle() = gameState == GameState.IDLE
}
