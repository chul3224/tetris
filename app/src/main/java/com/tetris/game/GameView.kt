package com.tetris.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val game = TetrisGame(context)
    private var gameThread: GameThread? = null

    // ── Layout ──────────────────────────────────────────────────────────────
    private var screenW = 0f
    private var screenH = 0f
    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardWidth = 0f
    private var boardHeight = 0f
    private var gameAreaH = 0f
    private var controllerTop = 0f
    private var leftPanelW = 0f

    // ── D-pad ────────────────────────────────────────────────────────────────
    private var dpadCx = 0f
    private var dpadCy = 0f
    private var dpadCrossW = 0f
    private var dpadTotalSize = 0f
    @Volatile private var dpadHeld: String? = null
    private var dpadHoldStart = 0L
    private var dpadLastRepeat = 0L
    private val DPAD_DELAY = 220L
    private val DPAD_REPEAT = 70L

    // ── Action Buttons ───────────────────────────────────────────────────────
    private var btnACx = 0f; private var btnACy = 0f  // A = Rotate
    private var btnBCx = 0f; private var btnBCy = 0f  // B = Hard Drop
    private var actionBtnR = 0f
    @Volatile private var btnAPressed = false
    @Volatile private var btnBPressed = false

    // ── Small Buttons ────────────────────────────────────────────────────────
    private var startBtnRect = RectF()
    private var holdBtnRect  = RectF()

    // ── Welcome Animation ────────────────────────────────────────────────────
    private data class FallingBlock(
        var x: Float, var y: Float,
        val color: Int, val speed: Float, val size: Float
    )
    private val fallingBlocks = mutableListOf<FallingBlock>()
    private var blinkOn = true
    private var lastBlinkTime = 0L

    // ── Colors ───────────────────────────────────────────────────────────────
    private val colorBg        = Color.parseColor("#0D0D1A")
    private val colorPanel     = Color.parseColor("#12122A")
    private val colorGrid      = Color.parseColor("#1E1E3A")
    private val colorOrange    = Color.parseColor("#FF6B35")
    private val colorAmber     = Color.parseColor("#FFB347")
    private val colorBlue      = Color.parseColor("#4D9FFF")
    private val colorCyan      = Color.parseColor("#00F5FF")
    private val colorBody      = Color.parseColor("#13132A")
    private val colorBodyLight = Color.parseColor("#1C1C3A")
    private val colorDpad      = Color.parseColor("#18183A")
    private val colorBtnA      = Color.parseColor("#FF6B35")
    private val colorBtnB      = Color.parseColor("#4D9FFF")

    // ── Paints ───────────────────────────────────────────────────────────────
    private val bgPaint      = Paint().apply { color = colorBg }
    private val panelPaint   = Paint().apply { color = colorPanel }
    private val gridPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid; style = Paint.Style.STROKE; strokeWidth = 0.8f
    }
    private val scanlinePaint = Paint().apply {
        color = Color.argb(22, 0, 0, 10); style = Paint.Style.FILL
    }
    private val overlayPaint  = Paint().apply { color = Color.argb(190, 5, 5, 20) }
    private val blockPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blockBorderP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun tp(color: Int, size: Float, shadow: Int = 0) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize  = size
            if (shadow != 0) setShadowLayer(8f, 0f, 0f, shadow)
        }

    // ── Init ─────────────────────────────────────────────────────────────────
    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ── SurfaceHolder ─────────────────────────────────────────────────────────
    override fun surfaceCreated(holder: SurfaceHolder) {
        val t = GameThread(holder, this); gameThread = t; t.running = true; t.start()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        calculateLayout(w.toFloat(), h.toFloat())
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread?.running = false; gameThread?.join(); gameThread = null
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    private fun calculateLayout(w: Float, h: Float) {
        screenW = w; screenH = h
        gameAreaH    = h * 0.60f
        controllerTop = gameAreaH
        leftPanelW    = w * 0.22f
        val rightPanelW = w * 0.22f
        val boardAreaW  = w - leftPanelW - rightPanelW
        val titleH      = h * 0.058f
        val maxByW = boardAreaW / TetrisGame.BOARD_WIDTH
        val maxByH = (gameAreaH - titleH) / TetrisGame.BOARD_HEIGHT
        cellSize    = min(maxByW, maxByH)
        boardWidth  = cellSize * TetrisGame.BOARD_WIDTH
        boardHeight = cellSize * TetrisGame.BOARD_HEIGHT
        boardLeft   = leftPanelW + (boardAreaW - boardWidth) / 2f
        boardTop    = titleH + (gameAreaH - titleH - boardHeight) / 2f
        setupController(w, h)
        initFallingBlocks(w, h)
    }

    private fun setupController(w: Float, h: Float) {
        val ctrlH   = h - controllerTop
        val ctrlMidY = controllerTop + ctrlH * 0.54f

        // D-pad
        dpadCx        = w * 0.27f
        dpadCy        = ctrlMidY
        dpadTotalSize = ctrlH * 0.54f
        dpadCrossW    = dpadTotalSize * 0.33f

        // A / B buttons
        actionBtnR = ctrlH * 0.108f
        val bcx    = w * 0.75f
        btnACx = bcx + actionBtnR * 1.15f;  btnACy = ctrlMidY - actionBtnR * 0.85f
        btnBCx = bcx - actionBtnR * 0.55f;  btnBCy = ctrlMidY + actionBtnR * 1.05f

        // Small center buttons
        val sbH = ctrlH * 0.09f
        val sbW = w * 0.13f
        val sbY = controllerTop + ctrlH * 0.18f
        holdBtnRect.set(w * 0.5f - sbW - w * 0.03f, sbY, w * 0.5f - w * 0.03f, sbY + sbH)
        startBtnRect.set(w * 0.5f + w * 0.03f, sbY, w * 0.5f + w * 0.03f + sbW, sbY + sbH)
    }

    private fun initFallingBlocks(w: Float, h: Float) {
        fallingBlocks.clear()
        val cols = listOf(colorCyan, colorOrange, Color.parseColor("#FFE600"),
            Color.parseColor("#39FF14"), colorBlue, Color.parseColor("#FF3131"), colorAmber)
        val sz = w * 0.044f
        repeat(22) { i ->
            fallingBlocks.add(FallingBlock(
                x     = (Math.random() * w).toFloat(),
                y     = (Math.random() * h).toFloat(),
                color = cols[i % cols.size],
                speed = (0.4f + Math.random() * 1.4f).toFloat(),
                size  = (sz * (0.6f + Math.random() * 0.9f)).toFloat()
            ))
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    fun update() {
        gameThread?.let { t ->
            if (game.isPlaying() && t.shouldDrop()) { game.moveDown(); t.resetDropTimer() }
        }
        // D-pad auto-repeat
        dpadHeld?.let { dir ->
            if (!game.isPlaying()) return@let
            val now = System.currentTimeMillis()
            if (now - dpadHoldStart > DPAD_DELAY && now - dpadLastRepeat > DPAD_REPEAT) {
                executeDpad(dir); dpadLastRepeat = now
            }
        }
        // Blink
        val now = System.currentTimeMillis()
        if (now - lastBlinkTime > 480) { blinkOn = !blinkOn; lastBlinkTime = now }
        // Falling blocks animation
        if (game.isIdle() || game.isGameOver()) {
            for (b in fallingBlocks) { b.y += b.speed; if (b.y > screenH + b.size) b.y = -b.size }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (screenW == 0f) return
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)
        drawGameArea(canvas)
        drawController(canvas)
        when {
            game.isGameOver() -> drawGameOverOverlay(canvas)
            game.isPaused()   -> drawPausedOverlay(canvas)
            game.isIdle()     -> drawWelcomeScreen(canvas)
        }
    }

    // ── Game Area ─────────────────────────────────────────────────────────────
    private fun drawGameArea(canvas: Canvas) {
        drawTitle(canvas)
        drawLeftPanel(canvas)
        drawBoard(canvas)
        drawRightPanel(canvas)
    }

    private fun drawTitle(canvas: Canvas) {
        val y = boardTop * 0.72f
        val sz = screenH * 0.040f
        // glow shadow
        val shP = tp(Color.argb(100, 255, 80, 0), sz + 2f)
        shP.letterSpacing = 0.28f
        canvas.drawText("TETRIS", screenW / 2f + 2f, y + 2f, shP)
        val tP = tp(colorOrange, sz, colorAmber); tP.letterSpacing = 0.28f
        canvas.drawText("TETRIS", screenW / 2f, y, tP)
    }

    private fun drawLeftPanel(canvas: Canvas) {
        canvas.drawRect(0f, boardTop, leftPanelW, boardTop + boardHeight, panelPaint)
        val cx = leftPanelW / 2f
        val lSz = cellSize * 0.50f
        val vSz = cellSize * 0.70f
        val sec = boardHeight / 3f

        fun stat(label: String, value: String, i: Int) {
            val by = boardTop + sec * i + sec * 0.17f
            canvas.drawText(label, cx, by + lSz,          tp(colorAmber, lSz))
            canvas.drawText(value, cx, by + lSz + vSz * 1.1f, tp(colorOrange, vSz))
        }
        stat("SCORE", game.score.toString(), 0)
        stat("LEVEL", game.level.toString(), 1)
        stat("LINES", game.lines.toString(), 2)

        val bp = tp(Color.parseColor("#3A3A5A"), cellSize * 0.42f)
        canvas.drawText("BEST", cx, boardTop + boardHeight - cellSize * 0.90f, bp)
        canvas.drawText(game.highScore.toString(), cx, boardTop + boardHeight - cellSize * 0.25f, bp)
    }

    private fun drawBoard(canvas: Canvas) {
        val bRect = RectF(boardLeft, boardTop, boardLeft + boardWidth, boardTop + boardHeight)
        canvas.drawRect(bRect, panelPaint)

        for (c in 0..TetrisGame.BOARD_WIDTH) {
            val x = boardLeft + c * cellSize
            canvas.drawLine(x, boardTop, x, boardTop + boardHeight, gridPaint)
        }
        for (r in 0..TetrisGame.BOARD_HEIGHT) {
            val y = boardTop + r * cellSize
            canvas.drawLine(boardLeft, y, boardLeft + boardWidth, y, gridPaint)
        }

        for (row in 0 until TetrisGame.BOARD_HEIGHT)
            for (col in 0 until TetrisGame.BOARD_WIDTH)
                game.board[row][col]?.let { clr ->
                    drawBlock(canvas, boardLeft + col * cellSize, boardTop + row * cellSize, cellSize, clr, 255)
                }

        game.ghostPiece?.let { g ->
            val sh = g.getShape()
            for (r in sh.indices) for (c in sh[r].indices)
                if (sh[r][c] != 0) drawGhostBlock(canvas,
                    boardLeft + (g.x + c) * cellSize, boardTop + (g.y + r) * cellSize, cellSize, g.getColor())
        }

        game.currentPiece?.let { p ->
            val sh = p.getShape()
            for (r in sh.indices) for (c in sh[r].indices)
                if (sh[r][c] != 0) {
                    val bx = boardLeft + (p.x + c) * cellSize
                    val by = boardTop  + (p.y + r) * cellSize
                    if (by >= boardTop - cellSize)
                        drawBlock(canvas, bx, by, cellSize, p.getColor(), 255)
                }
        }

        // Scanlines
        var sy = boardTop
        while (sy < boardTop + boardHeight) { canvas.drawRect(boardLeft, sy, boardLeft + boardWidth, sy + 1f, scanlinePaint); sy += 4f }

        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorOrange; style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawRect(bRect, borderP)
    }

    private fun drawRightPanel(canvas: Canvas) {
        val rx = boardLeft + boardWidth
        canvas.drawRect(rx, boardTop, screenW, boardTop + boardHeight, panelPaint)
        val cx  = rx + (screenW - rx) / 2f
        val psz = cellSize * 0.66f
        val lsz = cellSize * 0.50f

        canvas.drawText("NEXT", cx, boardTop + cellSize * 0.80f, tp(colorAmber, lsz))
        drawMiniPiece(canvas, game.nextPiece, cx, boardTop + cellSize * 1.0f, psz, 255)

        val holdC = if (game.canHold) colorAmber else Color.parseColor("#444460")
        canvas.drawText("HOLD", cx, boardTop + cellSize * 4.6f, tp(holdC, lsz))
        game.holdPiece?.let { hp ->
            drawMiniPiece(canvas, hp, cx, boardTop + cellSize * 4.8f, psz, if (game.canHold) 255 else 90)
        }
    }

    // ── Block Helpers ─────────────────────────────────────────────────────────
    private fun drawBlock(canvas: Canvas, x: Float, y: Float, sz: Float, color: Int, alpha: Int) {
        val pd = sz * 0.045f
        val l = x+pd; val t = y+pd; val r = x+sz-pd; val b = y+sz-pd
        blockPaint.color = color; blockPaint.alpha = alpha; blockPaint.style = Paint.Style.FILL
        canvas.drawRect(l, t, r, b, blockPaint)
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb((alpha*.50f).toInt(),255,255,255); style=Paint.Style.FILL }
        canvas.drawRect(l, t, r-sz*.20f, t+sz*.20f, hp)
        canvas.drawRect(l, t, l+sz*.20f, b-sz*.20f, hp)
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb((alpha*.55f).toInt(),0,0,0); style=Paint.Style.FILL }
        canvas.drawRect(l+sz*.20f, b-sz*.20f, r, b, sp)
        canvas.drawRect(r-sz*.20f, t+sz*.20f, r, b, sp)
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=Color.argb((alpha*.50f).toInt(),255,255,255); style=Paint.Style.FILL }
        val ss = sz*.13f
        canvas.drawRect(l+sz*.1f, t+sz*.1f, l+sz*.1f+ss, t+sz*.1f+ss, shine)
        blockBorderP.color = darkenColor(color, .45f); blockBorderP.alpha = alpha; blockBorderP.strokeWidth = sz*.07f
        canvas.drawRect(l, t, r, b, blockBorderP)
    }

    private fun drawGhostBlock(canvas: Canvas, x: Float, y: Float, sz: Float, color: Int) {
        val pd = sz * .07f
        val gP = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; alpha=65; style=Paint.Style.STROKE; strokeWidth=sz*.10f }
        canvas.drawRect(x+pd, y+pd, x+sz-pd, y+sz-pd, gP)
        val gf = Paint().apply { this.color=color; alpha=16; style=Paint.Style.FILL }
        canvas.drawRect(x+pd, y+pd, x+sz-pd, y+sz-pd, gf)
    }

    private fun drawMiniPiece(canvas: Canvas, piece: Tetromino, cx: Float, topY: Float, csz: Float, alpha: Int) {
        val sh = piece.copy(rotation=0).getShape()
        var minC=4; var maxC=-1
        for (r in sh.indices) for (c in sh[r].indices) if (sh[r][c]!=0) { if(c<minC) minC=c; if(c>maxC) maxC=c }
        val pw = (maxC - minC + 1) * csz
        val sx = cx - pw/2f - minC*csz
        val sy = topY + csz*.4f
        for (r in sh.indices) for (c in sh[r].indices)
            if (sh[r][c]!=0) drawBlock(canvas, sx+c*csz, sy+r*csz, csz, piece.getColor(), alpha)
    }

    private fun darkenColor(color: Int, f: Float): Int {
        val r = (Color.red(color)*f).toInt().coerceIn(0,255)
        val g = (Color.green(color)*f).toInt().coerceIn(0,255)
        val b = (Color.blue(color)*f).toInt().coerceIn(0,255)
        return Color.rgb(r, g, b)
    }

    // ── Controller ────────────────────────────────────────────────────────────
    private fun drawController(canvas: Canvas) {
        drawDeviceBody(canvas)
        drawSpeakerGrille(canvas)
        drawSmallButtons(canvas)
        drawDPad(canvas)
        drawActionButtons(canvas)
    }

    private fun drawDeviceBody(canvas: Canvas) {
        val ctrlH = screenH - controllerTop
        // Body fill
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBody }
        canvas.drawRect(0f, controllerTop, screenW, screenH, bodyP)
        // Top accent strip
        val stripP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBodyLight }
        canvas.drawRect(0f, controllerTop, screenW, controllerTop + ctrlH * .035f, stripP)
        // Orange top line
        val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorOrange; strokeWidth = 1.8f }
        canvas.drawLine(0f, controllerTop, screenW, controllerTop, lineP)
        // Bottom dark accent
        val botP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#09091A") }
        canvas.drawRect(0f, screenH - ctrlH * .07f, screenW, screenH, botP)
        // Subtle horizontal ridges (retro plastic texture)
        val ridgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F0F24"); strokeWidth = 1f }
        var ry = controllerTop + ctrlH * .06f
        while (ry < screenH - ctrlH * .08f) {
            canvas.drawLine(0f, ry, screenW, ry, ridgeP)
            ry += ctrlH * .045f
        }
    }

    private fun drawSpeakerGrille(canvas: Canvas) {
        val ctrlH = screenH - controllerTop
        val dotR  = screenW * .007f
        val dotSp = dotR * 3.0f
        val gcx   = screenW * .88f
        val gcy   = controllerTop + ctrlH * .70f
        val dotP  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A1E") }
        for (row in -2..2) for (col in -3..3)
            canvas.drawCircle(gcx + col*dotSp, gcy + row*dotSp, dotR, dotP)
        // Speaker label
        val spP = tp(Color.argb(70, 150, 150, 200), screenW * .022f)
        canvas.drawText("♪", gcx, gcy + dotSp * 4f, spP)
    }

    private fun drawSmallButtons(canvas: Canvas) {
        val bP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#222245") }
        val bBP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#383870"); style=Paint.Style.STROKE; strokeWidth=1f }
        val lP = tp(Color.argb(200, 180, 180, 220), holdBtnRect.height() * .48f)

        fun smallBtn(rect: RectF, label: String) {
            val cr = rect.height() * .35f
            canvas.drawRoundRect(rect, cr, cr, bP)
            canvas.drawRoundRect(rect, cr, cr, bBP)
            canvas.drawText(label, rect.centerX(), rect.centerY() + rect.height()*.20f, lP)
        }
        smallBtn(holdBtnRect,  "HOLD")
        smallBtn(startBtnRect, "START")
    }

    private fun drawDPad(canvas: Canvas) {
        val half = dpadTotalSize / 2f
        val armW = dpadCrossW
        val cr   = armW * .22f

        // Outer shadow circle
        val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0); style = Paint.Style.FILL
        }
        canvas.drawCircle(dpadCx + 3f, dpadCy + 3f, half * .62f, shadowP)

        // Dark base circle
        val baseP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A1E") }
        canvas.drawCircle(dpadCx, dpadCy, half * .60f, baseP)

        // Cross arms
        val dpadP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorDpad }
        val hRect = RectF(dpadCx-half, dpadCy-armW/2f, dpadCx+half, dpadCy+armW/2f)
        val vRect = RectF(dpadCx-armW/2f, dpadCy-half, dpadCx+armW/2f, dpadCy+half)
        canvas.drawRoundRect(hRect, cr, cr, dpadP)
        canvas.drawRoundRect(vRect, cr, cr, dpadP)

        // Pressed highlight
        dpadHeld?.let { dir ->
            val pressP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorOrange; alpha = 140 }
            when (dir) {
                "LEFT"  -> canvas.drawRoundRect(RectF(dpadCx-half, dpadCy-armW/2f, dpadCx-armW/2f+6f, dpadCy+armW/2f), cr, cr, pressP)
                "RIGHT" -> canvas.drawRoundRect(RectF(dpadCx+armW/2f-6f, dpadCy-armW/2f, dpadCx+half, dpadCy+armW/2f), cr, cr, pressP)
                "UP"    -> canvas.drawRoundRect(RectF(dpadCx-armW/2f, dpadCy-half, dpadCx+armW/2f, dpadCy-armW/2f+6f), cr, cr, pressP)
                "DOWN"  -> canvas.drawRoundRect(RectF(dpadCx-armW/2f, dpadCy+armW/2f-6f, dpadCx+armW/2f, dpadCy+half), cr, cr, pressP)
            }
        }

        // Subtle ridge on arms (highlight top edge)
        val ridgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60,255,255,255); strokeWidth=1.5f }
        canvas.drawLine(dpadCx-half+cr, dpadCy-armW/2f, dpadCx+half-cr, dpadCy-armW/2f, ridgeP)
        canvas.drawLine(dpadCx-armW/2f, dpadCy-half+cr, dpadCx-armW/2f, dpadCy+half-cr, ridgeP)

        // Center circle
        val cenP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E1E40") }
        canvas.drawCircle(dpadCx, dpadCy, armW * .46f, cenP)
        // Center dot
        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#282850") }
        canvas.drawCircle(dpadCx, dpadCy, armW * .20f, dotP)

        // Arrow triangles
        drawArrow(canvas, dpadCx,        dpadCy-half*.66f, "UP")
        drawArrow(canvas, dpadCx,        dpadCy+half*.66f, "DOWN")
        drawArrow(canvas, dpadCx-half*.66f, dpadCy,        "LEFT")
        drawArrow(canvas, dpadCx+half*.66f, dpadCy,        "RIGHT")
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, dir: String) {
        val s     = dpadCrossW * .20f
        val alpha = if (dpadHeld == dir) 230 else 110
        val aP    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha,190,190,230); style=Paint.Style.FILL }
        val path  = Path()
        when (dir) {
            "UP"    -> { path.moveTo(x, y-s);       path.lineTo(x-s*.7f, y+s*.5f); path.lineTo(x+s*.7f, y+s*.5f) }
            "DOWN"  -> { path.moveTo(x, y+s);       path.lineTo(x-s*.7f, y-s*.5f); path.lineTo(x+s*.7f, y-s*.5f) }
            "LEFT"  -> { path.moveTo(x-s, y);       path.lineTo(x+s*.5f, y-s*.7f); path.lineTo(x+s*.5f, y+s*.7f) }
            "RIGHT" -> { path.moveTo(x+s, y);       path.lineTo(x-s*.5f, y-s*.7f); path.lineTo(x-s*.5f, y+s*.7f) }
        }
        path.close(); canvas.drawPath(path, aP)
    }

    private fun drawActionButtons(canvas: Canvas) {
        drawCircleButton(canvas, btnACx, btnACy, actionBtnR, colorBtnA, "A", "ROTATE", btnAPressed)
        drawCircleButton(canvas, btnBCx, btnBCy, actionBtnR, colorBtnB, "B", "DROP",   btnBPressed)
    }

    private fun drawCircleButton(
        canvas: Canvas, cx: Float, cy: Float, r: Float,
        color: Int, label: String, sub: String, pressed: Boolean
    ) {
        // Drop shadow
        val shadowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=Color.argb(110,0,0,0) }
        canvas.drawCircle(cx+r*.12f, cy+r*.12f, r, shadowP)

        // Main fill
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (pressed) darkenColor(color,.65f) else color
        }
        canvas.drawCircle(cx, cy, r, fillP)

        // Top-left arc highlight (3D raised look)
        if (!pressed) {
            val hlP = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=Color.argb(75,255,255,255) }
            canvas.drawArc(cx-r, cy-r, cx+r, cy+r, 200f, 140f, false, hlP)
            // Small inner highlight circle
            val innerP = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=Color.argb(40,255,255,255) }
            canvas.drawCircle(cx - r*.25f, cy - r*.25f, r*.30f, innerP)
        }

        // Border
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=darkenColor(color,.35f); style=Paint.Style.STROKE; strokeWidth=r*.09f
        }
        canvas.drawCircle(cx, cy, r, borderP)

        // Letter label
        val lP = tp(Color.WHITE, r*.80f); lP.isFakeBoldText = true
        canvas.drawText(label, cx, cy + r*.30f, lP)

        // Sub label below
        val sP = tp(Color.argb(170,200,200,230), r*.38f)
        canvas.drawText(sub, cx, cy + r*1.65f, sP)
    }

    // ── Welcome Screen ────────────────────────────────────────────────────────
    private fun drawWelcomeScreen(canvas: Canvas) {
        // Dark overlay over game area
        val ovP = Paint().apply { color = Color.argb(210, 5, 5, 18) }
        canvas.drawRect(0f, 0f, screenW, gameAreaH, ovP)

        // Falling blocks (background)
        for (b in fallingBlocks)
            drawBlock(canvas, b.x - b.size/2f, b.y - b.size/2f, b.size, b.color, 75)

        val cx = screenW / 2f

        // ── Logo area ──
        val titleY = gameAreaH * 0.28f
        val titleSz = screenH * 0.092f

        // Multi-layer glow
        for (i in 3 downTo 1) {
            val glowP = tp(Color.argb(30*i, 255, 80, 0), titleSz + i*4f)
            glowP.letterSpacing = 0.30f
            canvas.drawText("TETRIS", cx, titleY, glowP)
        }
        // Main title
        val titleP = tp(colorOrange, titleSz, colorAmber); titleP.letterSpacing = 0.30f
        canvas.drawText("TETRIS", cx, titleY, titleP)

        // Subtitle
        val subP = tp(colorAmber, screenH * .028f); subP.letterSpacing = 0.20f
        canvas.drawText("RETRO EDITION", cx, titleY + screenH * .068f, subP)

        // Orange divider
        val divP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=colorOrange; alpha=130; strokeWidth=1.2f }
        val divY = titleY + screenH * .096f
        canvas.drawLine(screenW * .18f, divY, screenW * .82f, divY, divP)

        // High score
        if (game.highScore > 0) {
            canvas.drawText("HIGH SCORE", cx, divY + screenH*.062f, tp(Color.parseColor("#7777AA"), screenH*.025f))
            canvas.drawText(game.highScore.toString(), cx, divY + screenH*.104f, tp(colorAmber, screenH*.034f))
        }

        // Blinking PRESS START
        if (blinkOn) {
            val psP = tp(colorCyan, screenH * .031f); psP.letterSpacing = 0.10f
            canvas.drawText("PRESS  START", cx, gameAreaH * .80f, psP)
        }

        // Bottom decoration row of pixel blocks
        val dsz = screenH * .036f
        val cols = listOf(colorOrange, colorAmber, colorCyan, colorBlue,
            Color.parseColor("#39FF14"), Color.parseColor("#FF3131"), Color.parseColor("#FFE600"))
        val count = (screenW / (dsz + 2f)).toInt()
        val decorY = gameAreaH * .89f
        for (i in 0 until count)
            drawBlock(canvas, i*(dsz+2f), decorY, dsz, cols[i%cols.size], 150)
    }

    // ── Overlays ──────────────────────────────────────────────────────────────
    private fun drawGameOverOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, gameAreaH, overlayPaint)
        for (b in fallingBlocks)
            drawBlock(canvas, b.x-b.size/2f, b.y-b.size/2f, b.size, b.color, 55)

        val cx = screenW / 2f
        val bH = gameAreaH * .40f; val bW = screenW * .82f
        val bT = (gameAreaH - bH) * .38f
        val bR = RectF(cx-bW/2f, bT, cx+bW/2f, bT+bH)
        canvas.drawRoundRect(bR, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.parseColor("#0D0D22") })
        canvas.drawRoundRect(bR, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=colorOrange; style=Paint.Style.STROKE; strokeWidth=2.5f
        })

        val goP = tp(colorOrange, screenH*.060f, Color.parseColor("#FF3131")); goP.letterSpacing=0.15f
        canvas.drawText("GAME OVER", cx, bT+bH*.30f, goP)
        canvas.drawText("SCORE: ${game.score}", cx, bT+bH*.54f, tp(colorAmber, screenH*.036f))
        canvas.drawText("BEST:  ${game.highScore}", cx, bT+bH*.71f, tp(colorOrange, screenH*.030f))
        if (blinkOn) canvas.drawText("PRESS START TO RETRY", cx, bT+bH*.92f, tp(colorCyan, screenH*.024f))
    }

    private fun drawPausedOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenW, gameAreaH, overlayPaint)
        val cx = screenW / 2f
        val pP = tp(colorOrange, screenH*.070f, colorAmber); pP.letterSpacing = 0.15f
        canvas.drawText("PAUSED", cx, gameAreaH * .44f, pP)
        canvas.drawText("PRESS START TO RESUME", cx, gameAreaH * .57f, tp(colorAmber, screenH*.028f))
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN  -> handleDown(x, y)
            MotionEvent.ACTION_MOVE  -> handleMove(x, y)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> handleUp()
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        // State transitions — tap anywhere on game area
        if (y < controllerTop) {
            when {
                game.isIdle()     -> { game.startGame(); return }
                game.isGameOver() -> { game.startGame(); return }
                game.isPaused()   -> { game.resumeGame(); return }
            }
            return  // ignore taps on game board while playing
        }

        // Controller area
        if (game.isIdle() || game.isGameOver()) { game.startGame(); return }
        if (game.isPaused()) { /* only START button resumes */ }

        // START button
        if (startBtnRect.contains(x, y)) {
            when {
                game.isPlaying() -> game.togglePause()
                game.isIdle()    -> game.startGame()
                game.isGameOver()-> game.startGame()
                game.isPaused()  -> game.resumeGame()
            }
            return
        }
        // HOLD button
        if (holdBtnRect.contains(x, y)) { game.holdCurrentPiece(); return }

        // D-pad
        val dir = getDpadDir(x, y)
        if (dir != null) {
            dpadHeld = dir; dpadHoldStart = System.currentTimeMillis()
            dpadLastRepeat = dpadHoldStart
            executeDpad(dir); return
        }

        // Action buttons
        if (inCircle(x, y, btnACx, btnACy, actionBtnR)) { btnAPressed = true; game.rotate(); return }
        if (inCircle(x, y, btnBCx, btnBCy, actionBtnR)) {
            btnBPressed = true; game.hardDrop(); gameThread?.resetDropTimer(); return
        }
    }

    private fun handleMove(x: Float, y: Float) {
        if (y < controllerTop) return
        val dir = getDpadDir(x, y)
        if (dir != null && dir != dpadHeld) {
            dpadHeld = dir; dpadHoldStart = System.currentTimeMillis()
            dpadLastRepeat = dpadHoldStart; executeDpad(dir)
        } else if (dir == null) { dpadHeld = null }
        btnAPressed = inCircle(x, y, btnACx, btnACy, actionBtnR)
        btnBPressed = inCircle(x, y, btnBCx, btnBCy, actionBtnR)
    }

    private fun handleUp() { dpadHeld = null; btnAPressed = false; btnBPressed = false }

    private fun getDpadDir(x: Float, y: Float): String? {
        val dx = x - dpadCx; val dy = y - dpadCy
        val half = dpadTotalSize / 2f; val aw = dpadCrossW
        val inH = abs(dy) <= aw/2f && abs(dx) <= half
        val inV = abs(dx) <= aw/2f && abs(dy) <= half
        if (!inH && !inV) return null
        return when {
            inH && abs(dx) > aw/2f -> if (dx < 0) "LEFT" else "RIGHT"
            inV && abs(dy) > aw/2f -> if (dy < 0) "UP"   else "DOWN"
            abs(dx) > abs(dy)      -> if (dx < 0) "LEFT" else "RIGHT"
            else                   -> if (dy < 0) "UP"   else "DOWN"
        }
    }

    private fun executeDpad(dir: String) {
        if (!game.isPlaying()) return
        when (dir) {
            "LEFT"  -> game.moveLeft()
            "RIGHT" -> game.moveRight()
            "DOWN"  -> { game.softDrop(); gameThread?.resetDropTimer() }
            "UP"    -> { game.hardDrop(); gameThread?.resetDropTimer() }
        }
    }

    private fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x-cx; val dy = y-cy
        return sqrt(dx*dx + dy*dy) <= r * 1.25f
    }

    // ── Game Thread ───────────────────────────────────────────────────────────
    inner class GameThread(private val sh: SurfaceHolder, private val view: GameView) : Thread() {
        var running = false
        private var lastDrop = System.currentTimeMillis()

        fun shouldDrop() = System.currentTimeMillis() - lastDrop >= game.getDropInterval()
        fun resetDropTimer() { lastDrop = System.currentTimeMillis() }

        override fun run() {
            lastDrop = System.currentTimeMillis()
            while (running) {
                var canvas: Canvas? = null
                try {
                    canvas = sh.lockCanvas()
                    synchronized(sh) { view.update(); canvas?.let { view.draw(it) } }
                } finally {
                    canvas?.let { try { sh.unlockCanvasAndPost(it) } catch (_: Exception) {} }
                }
                sleep(16)
            }
        }
    }
}
