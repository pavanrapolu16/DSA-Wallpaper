package com.example

import android.app.WallpaperManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder

class DsaWallpaperService : WallpaperService() {

    private val TAG = "DsaWallpaperService"
    private val activeEngines = java.util.concurrent.CopyOnWriteArrayList<DsaEngine>()
    private var isDeviceLocked = true

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isDeviceLocked = keyguardManager?.isKeyguardLocked ?: true
                    activeEngines.forEach {
                        it.pickNewQuestion()
                        it.triggerRedraw()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isDeviceLocked = true
                    activeEngines.forEach {
                        it.triggerRedraw()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    isDeviceLocked = false
                    activeEngines.forEach {
                        it.triggerRedraw()
                    }
                }
                "com.example.REFRESH_DSA_WALLPAPER" -> {
                    activeEngines.forEach {
                        it.loadState()
                        it.triggerRedraw()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        isDeviceLocked = keyguardManager?.isKeyguardLocked ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("com.example.REFRESH_DSA_WALLPAPER")
        }
        registerReceiver(serviceReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(serviceReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Unregistering service receiver failed: ${e.message}")
        }
    }

    override fun onCreateEngine(): Engine {
        val engine = DsaEngine()
        activeEngines.add(engine)
        return engine
    }

    inner class DsaEngine : Engine() {
        private val TAG = "DsaWallpaperEngine"
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0

        // Preferences & Data
        private val prefs by lazy { getSharedPreferences("dsa_wallpaper_prefs", Context.MODE_PRIVATE) }
        private var currentQuestion: DsaQuestion? = null
        private var showSolution = false

        // Drawing Paints & Caches
        private val bgPaint = Paint()
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(1.5f)
        }
        private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val categoryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val monospacePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
        }
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // GestureDetector for clicks/taps to show solution
        private val gestureDetector = GestureDetector(this@DsaWallpaperService, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showSolution = !showSolution
                prefs.edit().putBoolean("show_solution", showSolution).apply()
                triggerRedraw()
                return true
            }
        })

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "current_question_id" || key == "wallpaper_theme" || 
                key == "filter_difficulty" || key == "filter_platform" || 
                key == "filter_category" || key == "clock_clearance" || 
                key == "text_font_scale" || key == "show_complexity_tags" || 
                key == "card_transparency" || key == "show_solution") {
                loadState()
                triggerRedraw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            loadState()
            setTouchEventsEnabled(true)
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            activeEngines.remove(this)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                loadState() // Ensure sync if settings changed
                triggerRedraw()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            triggerRedraw()
        }

        override fun onTouchEvent(event: MotionEvent) {
            gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        fun loadState() {
            val savedId = prefs.getString("current_question_id", null)
            val difficultyFilter = prefs.getString("filter_difficulty", "ALL") ?: "ALL"
            showSolution = prefs.getBoolean("show_solution", false)

            if (savedId != null) {
                currentQuestion = DsaQuestionRepository.getQuestionById(savedId)
            } else {
                pickNewQuestion()
            }
        }

        fun pickNewQuestion() {
            val difficultyFilter = prefs.getString("filter_difficulty", "ALL") ?: "ALL"
            val platformFilter = prefs.getString("filter_platform", "ALL") ?: "ALL"
            val categoryFilter = prefs.getString("filter_category", "ALL") ?: "ALL"
            val nextQuestion = DsaQuestionRepository.getRandomQuestion(difficultyFilter, platformFilter, categoryFilter)
            currentQuestion = nextQuestion
            showSolution = false // reset solution on new question
            prefs.edit().apply {
                putString("current_question_id", nextQuestion.id)
                putBoolean("show_solution", false)
                apply()
            }
            Log.d(TAG, "Picked next question: ${nextQuestion.title} (${nextQuestion.difficulty})")
        }

        fun triggerRedraw() {
            if (visible) {
                handler.post { drawFrame() }
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawWallpaper(canvas)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing wallpaper canvas: ${e.message}")
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error posting canvas: ${e.message}")
                    }
                }
            }
        }

        private fun drawWallpaper(canvas: Canvas) {
            val question = currentQuestion ?: return

            // 1. Theme Configuration
            val themeStr = prefs.getString("wallpaper_theme", "COSMIC_SLATE") ?: "COSMIC_SLATE"
            
            val (bgColor, accentColor, cardBgColor, textColor, secondaryTextColor, codeBgColor) = getThemeColors(themeStr)

            // 2. Draw Background Layout (Gradient or Ambient design)
            drawBackgroundTheme(canvas, themeStr, bgColor, accentColor)

            // 3. Layout Bounds & Math
            if (isDeviceLocked) {
                val padding = dpToPx(24f)
            val startX = padding
            val endX = width - padding
            val contentWidth = endX - startX

            // Read customization settings
            val clockClearance = prefs.getString("clock_clearance", "MEDIUM") ?: "MEDIUM"
            val textFontScale = prefs.getString("text_font_scale", "STANDARD") ?: "STANDARD"
            val showComplexityTags = prefs.getBoolean("show_complexity_tags", true)
            val cardOpacityMode = prefs.getString("card_transparency", "TRANSLUCENT") ?: "TRANSLUCENT"

            // Adjust vertical position to clear the system clock perfectly based on preference
            val topOffset = when (clockClearance) {
                "LOW" -> height * 0.18f     // 18% top clearance
                "MEDIUM" -> height * 0.32f  // 32% top clearance (Safe from typical clocks)
                "HIGH" -> height * 0.44f    // 44% top clearance (Extreme clearance for massive clocks)
                else -> height * 0.32f
            }
            
            val cardHeightMax = when (clockClearance) {
                "LOW" -> height * 0.60f
                "MEDIUM" -> height * 0.47f  // Compact balanced size
                "HIGH" -> height * 0.35f    // Minimal compact card size
                else -> height * 0.47f
            }

            // Card Shape Coordinates
            val cardRect = RectF(startX, topOffset, endX, topOffset + cardHeightMax)
            
            // Draw Card Body with custom transparency opacity
            val alphaVal = when (cardOpacityMode) {
                "SOLID" -> 255
                "TRANSLUCENT" -> 210 // ~82% opaque
                "MINIMAL" -> 90    // ~35% opaque
                else -> 210
            }
            cardPaint.color = cardBgColor
            cardPaint.alpha = alphaVal
            canvas.drawRoundRect(cardRect, dpToPx(16f), dpToPx(16f), cardPaint)

            // Draw Card Border
            borderPaint.color = Color.parseColor(accentColor)
            borderPaint.alpha = 50 // Soft subtle border
            canvas.drawRoundRect(cardRect, dpToPx(16f), dpToPx(16f), borderPaint)

            // Inside Card margins
            val innerPadding = dpToPx(18f)
            var currentY = topOffset + innerPadding

            // Font scale multiplier
            val fontMultiplier = when (textFontScale) {
                "COMPACT" -> 0.82f     // compact and fit everything nicely
                "STANDARD" -> 1.0f     // standard default
                "ACCESSIBLE" -> 1.2f   // larger readable text
                else -> 1.0f
            }

            // --- CATEGORY ---
            categoryPaint.color = Color.parseColor(accentColor)
            categoryPaint.textSize = spToPx(12f * fontMultiplier)
            val badgePrefix = when {
                question.platform.equals("LeetCode", ignoreCase = true) -> "LC • "
                question.platform.equals("GeeksforGeeks", ignoreCase = true) -> "GFG • "
                else -> "STRIVER • "
            }
            canvas.drawText(badgePrefix + question.category.uppercase(), startX + innerPadding, currentY + dpToPx(5f), categoryPaint)
            currentY += dpToPx(16f)

            // --- TITLE & DIFFICULTY ---
            titlePaint.color = Color.parseColor(textColor)
            titlePaint.textSize = spToPx(22f * fontMultiplier)
            
            val diffText = question.difficulty
            val diffColor = when (diffText.lowercase()) {
                "easy" -> "#00E676" // Fluent emerald green
                "medium" -> "#FFD600" // Vibrant gold
                else -> "#FF1744" // Neon ruby red
            }

            // Draw difficulty badge text first
            val diffBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = spToPx(11f * fontMultiplier)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor(diffColor)
            }
            val diffWidth = diffBadgePaint.measureText(diffText)
            val badgePaddingH = dpToPx(8f)
            val badgePaddingV = dpToPx(4f)
            val badgeW = diffWidth + (badgePaddingH * 2)
            val badgeH = dpToPx(18f)

            // Layout Title text with StaticLayout to wraps nicely if the title is very long
            val titleTextWidthPre = contentWidth - (innerPadding * 2) - badgeW - dpToPx(8f)
            val titleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(question.title, 0, question.title.length, titlePaint, titleTextWidthPre.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(question.title, titlePaint, titleTextWidthPre.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0f, false)
            }

            // Draw parsed title
            canvas.save()
            canvas.translate(startX + innerPadding, currentY)
            titleLayout.draw(canvas)
            canvas.restore()

            // Draw Difficulty Badge at aligned height of title
            val badgeLeft = endX - innerPadding - badgeW
            val badgeRect = RectF(badgeLeft, currentY + dpToPx(2f), endX - innerPadding, currentY + dpToPx(2f) + badgeH)
            badgeBgPaint.color = Color.parseColor(diffColor)
            badgeBgPaint.alpha = 30 // Semi-translucent colored badge background
            canvas.drawRoundRect(badgeRect, dpToPx(4f), dpToPx(4f), badgeBgPaint)
            canvas.drawText(
                diffText, 
                badgeLeft + badgePaddingH, 
                currentY + dpToPx(2f) + badgeH - badgePaddingV - dpToPx(1f), 
                diffBadgePaint
            )

            currentY += titleLayout.height + dpToPx(16f)

            // Check if user is looking at the Question or reversed solution card
            if (!showSolution) {
                // --- QUESTION CONTENT MODE ---

                // --- DESCRIPTION ---
                bodyPaint.color = Color.parseColor(secondaryTextColor)
                bodyPaint.textSize = spToPx(13.5f * fontMultiplier)
                bodyPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                
                val bodyTextWidth = contentWidth - (innerPadding * 2)
                val descriptionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(question.description, 0, question.description.length, bodyPaint, bodyTextWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(question.description, bodyPaint, bodyTextWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
                }

                canvas.save()
                canvas.translate(startX + innerPadding, currentY)
                descriptionLayout.draw(canvas)
                canvas.restore()

                currentY += descriptionLayout.height + dpToPx(16f)

                // --- EXAMPLE INPUT/OUTPUT BOX ---
                // Setup Code Box Bounds
                val spaceReservedForTags = if (showComplexityTags) dpToPx(42f) else dpToPx(12f)
                val expectedBoxHOffset = (topOffset + cardHeightMax - dpToPx(16f) - currentY - spaceReservedForTags)
                val boxH = if (expectedBoxHOffset > dpToPx(60f)) expectedBoxHOffset else dpToPx(88f)

                val exampleRect = RectF(startX + innerPadding, currentY, endX - innerPadding, currentY + boxH)
                cardPaint.color = Color.parseColor(codeBgColor)
                canvas.drawRoundRect(exampleRect, dpToPx(8f), dpToPx(8f), cardPaint)

                // Draw Example Heading
                monospacePaint.textSize = spToPx(11f * fontMultiplier)
                monospacePaint.color = Color.parseColor(accentColor)
                canvas.drawText("EXAMPLE CASE", startX + innerPadding + dpToPx(12f), currentY + dpToPx(18f), monospacePaint)

                // Draw Input/Output Code Strings
                val textCodeColor = if (themeStr == "MATRIX_BLACK") "#00FF00" else "#ECEFF1"
                monospacePaint.color = Color.parseColor(textCodeColor)
                monospacePaint.textSize = spToPx(12f * fontMultiplier)

                val exBody = "Input:  ${question.input}\nOutput: ${question.output}"
                val codeTextWidth = contentWidth - (innerPadding * 2) - dpToPx(24f)
                val exampleLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(exBody, 0, exBody.length, monospacePaint, codeTextWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(exBody, monospacePaint, codeTextWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
                }

                canvas.save()
                canvas.translate(startX + innerPadding + dpToPx(12f), currentY + dpToPx(28f))
                exampleLayout.draw(canvas)
                canvas.restore()

                // --- COMPLEXITY PILLS ---
                if (showComplexityTags) {
                    val tagY = topOffset + cardHeightMax - dpToPx(34f)
                    drawComplexityPill(canvas, "Time Complexity", question.timeComplexity, startX + innerPadding, tagY, accentColor, textColor)
                    val spacePillLeft = startX + innerPadding + dpToPx(140f)
                    drawComplexityPill(canvas, "Space Complexity", question.spaceComplexity, spacePillLeft, tagY, accentColor, textColor)
                }

            } else {
                // --- SOLUTION MODE (Double Tap reveals this!) ---
                
                // Draw a nice indicator line that "Solution is revealed"
                val solIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(accentColor)
                    style = Paint.Style.STROKE
                    strokeWidth = dpToPx(2f)
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
                }
                canvas.drawLine(startX + innerPadding, currentY - dpToPx(8f), endX - innerPadding, currentY - dpToPx(8f), solIndicatorPaint)

                // Draw Hint text in balanced body layout
                bodyPaint.color = Color.parseColor(secondaryTextColor)
                bodyPaint.textSize = spToPx(12.5f * fontMultiplier)
                
                val hintLabel = "💡 HINT: "
                val hintFull = hintLabel + question.hint
                val hintTextWidth = contentWidth - (innerPadding * 2)
                
                val hintLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(hintFull, 0, hintFull.length, bodyPaint, hintTextWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.15f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(hintFull, bodyPaint, hintTextWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
                }

                canvas.save()
                canvas.translate(startX + innerPadding, currentY)
                hintLayout.draw(canvas)
                canvas.restore()

                currentY += hintLayout.height + dpToPx(12f)

                // Draw Solution Code Box
                val spaceRemainingCode = (topOffset + cardHeightMax - dpToPx(16f) - currentY)
                val codeBoxH = if (spaceRemainingCode > dpToPx(60f)) spaceRemainingCode else dpToPx(140f)

                val codeBoxRect = RectF(startX + innerPadding, currentY, endX - innerPadding, currentY + codeBoxH)
                cardPaint.color = Color.parseColor(codeBgColor)
                canvas.drawRoundRect(codeBoxRect, dpToPx(8f), dpToPx(8f), cardPaint)

                // Fill with code snippet
                monospacePaint.textSize = spToPx(10.5f * fontMultiplier)
                monospacePaint.color = Color.parseColor(textColor)
                val codePadding = dpToPx(10f)
                val scTextWidth = contentWidth - (innerPadding * 2) - (codePadding * 2)

                val solutionLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(question.solutionCode, 0, question.solutionCode.length, monospacePaint, scTextWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(question.solutionCode, monospacePaint, scTextWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
                }

                canvas.save()
                canvas.translate(startX + innerPadding + codePadding, currentY + codePadding)
                solutionLayout.draw(canvas)
                canvas.restore()
            }

            // 4. Draw Watermark signature below card to avoid lockscreen clock overlap!
            val watermarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = spToPx(10.5f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                color = Color.parseColor(textColor)
                alpha = 60 // subtle watermark transparency
            }
            canvas.drawText("designed by Pavan Rapolu", width / 2f, topOffset + cardHeightMax + dpToPx(28f), watermarkPaint)
            } else {
                // Device is unlocked: show clean ambient screen with subtle brand watermark at the bottom of launcher
                val watermarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = spToPx(11f)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    color = Color.parseColor(textColor)
                    alpha = 45 // subtle watermark transparency
                }
                canvas.drawText("designed by Pavan Rapolu", width / 2f, height - dpToPx(40f), watermarkPaint)
            }
        }

        private fun drawComplexityPill(canvas: Canvas, label: String, value: String, x: Float, y: Float, accentHex: String, textHex: String) {
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dpToPx(1f)
                color = Color.parseColor(accentHex)
                alpha = 60
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor(accentHex)
                alpha = 15
            }
            val textV = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = spToPx(10.5f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                color = Color.parseColor(textHex)
            }

            val rect = RectF(x, y, x + dpToPx(124f), y + dpToPx(24f))
            canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), fillPaint)
            canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), pillPaint)

            val displayStr = "${label.split(" ").first()}: $value"
            canvas.drawText(displayStr, x + dpToPx(8f), y + dpToPx(16f), textV)
        }

        private fun drawBackgroundTheme(canvas: Canvas, themeStr: String, bgColor: Int, accentHex: String) {
            // Fill background with core color first
            canvas.drawColor(bgColor)

            // Custom tech-stylized ambient decorations on background
            val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = dpToPx(1f)
                style = Paint.Style.STROKE
                color = Color.parseColor(accentHex)
            }

            when (themeStr) {
                "COSMIC_SLATE" -> {
                    // Elegantly drawn tech diagonal lines
                    drawPaint.alpha = 15
                    drawPaint.strokeWidth = dpToPx(1.5f)
                    var offset = 0f
                    while (offset < width + height) {
                        canvas.drawLine(offset, 0f, 0f, offset, drawPaint)
                        offset += dpToPx(120f)
                    }
                    // Draw a subtle soft radial glow overlay
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                            Color.parseColor("#1A2035"), Color.parseColor("#090C15"), Shader.TileMode.CLAMP)
                    }
                    glowPaint.alpha = 180
                    canvas.drawPaint(glowPaint)
                }
                "MIDNIGHT_BLUE" -> {
                    // Deep galactic grid circles
                    drawPaint.alpha = 20
                    canvas.drawCircle(width / 2f, height * 0.1f, dpToPx(350f), drawPaint)
                    canvas.drawCircle(width / 2f, height * 0.1f, dpToPx(250f), drawPaint)
                    canvas.drawCircle(width / 2f, height * 0.1f, dpToPx(150f), drawPaint)
                    canvas.drawCircle(width / 2f, height * 0.1f, dpToPx(80f), drawPaint)
                }
                "MATRIX_BLACK" -> {
                    // Digital terminal layout lines (retro tech grid)
                    drawPaint.alpha = 25
                    drawPaint.strokeWidth = dpToPx(1f)
                    var gridX = 0f
                    while (gridX < width) {
                        canvas.drawLine(gridX, 0f, gridX, height.toFloat(), drawPaint)
                        gridX += dpToPx(60f)
                    }
                    var gridY = 0f
                    while (gridY < height) {
                        canvas.drawLine(0f, gridY, width.toFloat(), gridY, drawPaint)
                        gridY += dpToPx(60f)
                    }
                }
                "SUNSET_PURPLE" -> {
                    // Generates flowing decorative node vectors to represent deep DSA tree elements
                    drawPaint.alpha = 30
                    drawPaint.strokeWidth = dpToPx(1.2f)
                    
                    val nodePath = Path()
                    val pY = height * 0.9f
                    nodePath.moveTo(width * 0.2f, pY)
                    nodePath.cubicTo(width * 0.4f, pY - dpToPx(80f), width * 0.6f, pY + dpToPx(60f), width * 0.8f, pY)
                    canvas.drawPath(nodePath, drawPaint)

                    val drawCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor(accentHex)
                        style = Paint.Style.FILL
                        alpha = 40
                    }
                    canvas.drawCircle(width * 0.2f, pY, dpToPx(4f), drawCirclePaint)
                    canvas.drawCircle(width * 0.5f, pY - dpToPx(10f), dpToPx(5f), drawCirclePaint)
                    canvas.drawCircle(width * 0.8f, pY, dpToPx(4f), drawCirclePaint)
                }
            }
        }

        private fun getThemeColors(themeName: String): ThemePack {
            return when (themeName) {
                "COSMIC_SLATE" -> ThemePack(
                    bgColor = Color.parseColor("#0F111A"), // ultra dark space
                    accentHex = "#00E5FF", // glowing cyan
                    cardBgColor = Color.parseColor("#1B1F32"), // high contrast deep blue-grey card
                    textColor = "#FFFFFF",
                    secondaryTextColor = "#CFD8DC",
                    codeBgColor = "#0A0D18" // pitch black-blue code editor background
                )
                "MIDNIGHT_BLUE" -> ThemePack(
                    bgColor = Color.parseColor("#050B14"), // dark deep ocean
                    accentHex = "#FF5722", // hot deep orange-coral
                    cardBgColor = Color.parseColor("#101C2F"), // dark marine card
                    textColor = "#F5F5F5",
                    secondaryTextColor = "#B0BEC5",
                    codeBgColor = "#02070E"
                )
                "MATRIX_BLACK" -> ThemePack(
                    bgColor = Color.parseColor("#000000"), // real black
                    accentHex = "#00FF00", // true matrix code green
                    cardBgColor = Color.parseColor("#080D08"), // extremely dark forest glow card
                    textColor = "#FFFFFF",
                    secondaryTextColor = "#98E698",
                    codeBgColor = "#030503"
                )
                "SUNSET_PURPLE" -> ThemePack(
                    bgColor = Color.parseColor("#140D26"), // purple obsidian
                    accentHex = "#FF4081", // vibrant pink-rose
                    cardBgColor = Color.parseColor("#261744"), // plum cards
                    textColor = "#FFFFFF",
                    secondaryTextColor = "#E1BEE7",
                    codeBgColor = "#0F071D"
                )
                else -> ThemePack(
                    bgColor = Color.parseColor("#0F111A"),
                    accentHex = "#00E5FF",
                    cardBgColor = Color.parseColor("#171921"),
                    textColor = "#FFFFFF",
                    secondaryTextColor = "#CFD8DC",
                    codeBgColor = "#0A0D18"
                )
            }
        }

        private fun dpToPx(dp: Float): Float {
            return dp * resources.displayMetrics.density
        }

        private fun spToPx(sp: Float): Float {
            return sp * resources.displayMetrics.scaledDensity
        }
    }

    data class ThemePack(
        val bgColor: Int,
        val accentHex: String,
        val cardBgColor: Int,
        val textColor: String,
        val secondaryTextColor: String,
        val codeBgColor: String
    )
}
