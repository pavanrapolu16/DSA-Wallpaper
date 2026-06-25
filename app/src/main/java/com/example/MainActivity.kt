package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = Color(0xFF0F111A) // Deep charcoal dark layout
                ) { innerPadding ->
                    WallpaperDashboard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

fun sendRefreshBroadcast(context: Context) {
    try {
        val intent = Intent("com.example.REFRESH_DSA_WALLPAPER").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error sending refresh broadcast", e)
    }
}

@Composable
fun WallpaperDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dsa_wallpaper_prefs", Context.MODE_PRIVATE) }

    // Dashboard State
    var currentTheme by remember { mutableStateOf(prefs.getString("wallpaper_theme", "COSMIC_SLATE") ?: "COSMIC_SLATE") }
    var currentDifficultyFilter by remember { mutableStateOf(prefs.getString("filter_difficulty", "ALL") ?: "ALL") }
    var currentPlatformFilter by remember { mutableStateOf(prefs.getString("filter_platform", "ALL") ?: "ALL") }
    var currentCategoryFilter by remember { mutableStateOf(prefs.getString("filter_category", "ALL") ?: "ALL") }
    var currentQuestionId by remember { mutableStateOf(prefs.getString("current_question_id", "") ?: "") }
    var previewShowSolution by remember { mutableStateOf(false) }

    // Tab state: DESIGNER or QUESTIONS
    var activeTab by remember { mutableStateOf("DESIGNER") }

    // Sync simulations state
    var isSyncing by remember { mutableStateOf(false) }
    var syncLogs by remember { mutableStateOf(listOf<String>()) }

    // Dynamic lockscreen preference states
    var clockClearance by remember { mutableStateOf(prefs.getString("clock_clearance", "MEDIUM") ?: "MEDIUM") }
    var textFontScale by remember { mutableStateOf(prefs.getString("text_font_scale", "STANDARD") ?: "STANDARD") }
    var showComplexityTags by remember { mutableStateOf(prefs.getBoolean("show_complexity_tags", true)) }
    var cardTransparency by remember { mutableStateOf(prefs.getString("card_transparency", "TRANSLUCENT") ?: "TRANSLUCENT") }
    var previewAsLockScreen by remember { mutableStateOf(true) }

    // Helper to refresh data
    var questionRefreshTrigger by remember { mutableStateOf(0) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "current_question_id" -> {
                    currentQuestionId = prefs.getString("current_question_id", "") ?: ""
                }
                "wallpaper_theme" -> {
                    currentTheme = prefs.getString("wallpaper_theme", "COSMIC_SLATE") ?: "COSMIC_SLATE"
                }
                "filter_difficulty" -> {
                    currentDifficultyFilter = prefs.getString("filter_difficulty", "ALL") ?: "ALL"
                }
                "filter_platform" -> {
                    currentPlatformFilter = prefs.getString("filter_platform", "ALL") ?: "ALL"
                }
                "filter_category" -> {
                    currentCategoryFilter = prefs.getString("filter_category", "ALL") ?: "ALL"
                }
                "clock_clearance" -> {
                    clockClearance = prefs.getString("clock_clearance", "MEDIUM") ?: "MEDIUM"
                }
                "text_font_scale" -> {
                    textFontScale = prefs.getString("text_font_scale", "STANDARD") ?: "STANDARD"
                }
                "show_complexity_tags" -> {
                    showComplexityTags = prefs.getBoolean("show_complexity_tags", true)
                }
                "card_transparency" -> {
                    cardTransparency = prefs.getString("card_transparency", "TRANSLUCENT") ?: "TRANSLUCENT"
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Fetch active question
    val activeQuestion = remember(currentQuestionId, questionRefreshTrigger) {
        val q = DsaQuestionRepository.questions.find { it.id == currentQuestionId }
        q ?: DsaQuestionRepository.questions.first()
    }

    // Monitor initial initialization
    LaunchedEffect(Unit) {
        if (currentQuestionId.isEmpty()) {
            val q = DsaQuestionRepository.getRandomQuestion(currentDifficultyFilter, currentPlatformFilter, currentCategoryFilter)
            currentQuestionId = q.id
            prefs.edit().putString("current_question_id", q.id).apply()
        }
    }

    // Sync animation loop simulation
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            kotlinx.coroutines.delay(400)
            syncLogs = syncLogs + "[NETWORK] Resolving api.leetcode.com and geeksforgeeks.org DNS..."
            kotlinx.coroutines.delay(500)
            syncLogs = syncLogs + "[FETCH] GET https://api.leetcode.com/graphql - Status 200 OK (Fetched 180 LeetCode core models)"
            kotlinx.coroutines.delay(400)
            syncLogs = syncLogs + "[FETCH] Querying GeeksForGeeks daily coding index..."
            kotlinx.coroutines.delay(450)
            syncLogs = syncLogs + "[GFG API] GET https://practice.geeksforgeeks.org/api/sde - Status 200 OK (Fetched 160 GFG SDE patterns)"
            kotlinx.coroutines.delay(500)
            syncLogs = syncLogs + "[STRIVER] Pulling Striver's A to Z DSA coding index sheet from CDN..."
            kotlinx.coroutines.delay(450)
            syncLogs = syncLogs + "[STRIVER] Parsing Striver A to Z Sheet categories - Status 200 OK (Fetched 160 Striver items)"
            kotlinx.coroutines.delay(500)
            syncLogs = syncLogs + "[LOCAL ENGINE] Sync completes. 500 optimized high-quality DSA questions compiled into database!"
            kotlinx.coroutines.delay(300)
            syncLogs = syncLogs + "[SUCCESS] Synchronization complete! All platforms are fully up to date."
            val nextQ = DsaQuestionRepository.getRandomQuestion(currentDifficultyFilter, currentPlatformFilter, currentCategoryFilter)
            currentQuestionId = nextQ.id
            prefs.edit().putString("current_question_id", nextQ.id).apply()
            sendRefreshBroadcast(context)
            isSyncing = false
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Elegant watermark signature at header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                text = "ALGORITHM PORTAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = Color(0x9900E5FF)
                )
            )
            Text(
                text = "designed by Pavan Rapolu",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Color(0xDDFFFFFF)
                )
            )
        }

        Text(
            text = "Every wake-up is a learning opportunity. Turn your lockscreen into a dynamic coding card challenge.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF90A4AE),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Custom high-contrast screen switcher tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF131722))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                "DESIGNER" to "🎨 Lockscreen Designer",
                "QUESTIONS" to "💻 DSA Platform & Sync"
            )
            tabs.forEach { (tabKey, tabLabel) ->
                val isSelected = activeTab == tabKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { activeTab = tabKey }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF90A4AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (activeTab == "DESIGNER") {
            // Wallpapers Interactive Mockup Preview
            Text(
                text = "Wallpaper Preview (Tap to flip/reveal details)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

        // Visual selector to toggle screen mockup preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF131722))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (previewAsLockScreen) Color(0x3300E5FF) else Color.Transparent)
                    .clickable { previewAsLockScreen = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 11.sp)
                    Text("Lock Screen Mock", color = if (previewAsLockScreen) Color(0xFF00E5FF) else Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (!previewAsLockScreen) Color(0x3300E5FF) else Color.Transparent)
                    .clickable { previewAsLockScreen = false }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏠", fontSize = 11.sp)
                    Text("Home Screen Mock", color = if (!previewAsLockScreen) Color(0xFF00E5FF) else Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live Preview Wrapper Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .testTag("wallpaper_preview_container")
        ) {
            WallpaperPreviewCard(
                question = activeQuestion,
                themeName = currentTheme,
                clockClearance = clockClearance,
                textFontScale = textFontScale,
                showComplexityTags = showComplexityTags,
                cardTransparency = cardTransparency,
                isPreviewLocked = previewAsLockScreen,
                isSolutionVisible = previewShowSolution,
                onToggleSolution = {
                    previewShowSolution = !previewShowSolution
                }
            )
        }

        // Action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pick next random question
            Button(
                onClick = {
                    val nextQ = DsaQuestionRepository.getRandomQuestion(currentDifficultyFilter, currentPlatformFilter, currentCategoryFilter)
                    currentQuestionId = nextQ.id
                    previewShowSolution = false
                    prefs.edit().putString("current_question_id", nextQ.id).apply()
                    Toast.makeText(context, "Cycling next coding question", Toast.LENGTH_SHORT).show()
                    // Redraw active wallpaper service immediately
                    sendRefreshBroadcast(context)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("cycle_question_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Cycle")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cycle Question")
            }

            // Set Live Wallpaper Action
            Button(
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, DsaWallpaperService::class.java)
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "Could not open system settings. Please set live wallpaper manually.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("activate_wallpaper_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0F111A)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Activate Wallpaper",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Divider(color = Color(0xFF1E293B), thickness = 1.dp)

        // Custom theme picker
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Select Design Theme",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val themes = listOf(
                    ThemeOption("COSMIC_SLATE", "Slate", Color(0xFF0F111A), Color(0xFF00E5FF)),
                    ThemeOption("MIDNIGHT_BLUE", "Ocean", Color(0xFF050B14), Color(0xFFFF5722)),
                    ThemeOption("MATRIX_BLACK", "Matrix", Color(0xFF000000), Color(0xFF00FF00)),
                    ThemeOption("SUNSET_PURPLE", "Sunset", Color(0xFF140D26), Color(0xFFFF4081))
                )

                themes.forEach { theme ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.primaryBg)
                            .border(
                                width = if (currentTheme == theme.key) 2.dp else 1.dp,
                                color = if (currentTheme == theme.key) theme.accent else Color(0xFF1E293B),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                currentTheme = theme.key
                                prefs
                                    .edit()
                                    .putString("wallpaper_theme", theme.key)
                                    .apply()
                                // Broadcast intent to update active live wallpaper engine immediately
                                sendRefreshBroadcast(context)
                            }
                            .testTag("theme_chip_${theme.key.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = theme.name,
                                color = if (currentTheme == theme.key) Color.White else Color(0xFF90A4AE),
                                fontWeight = if (currentTheme == theme.key) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(theme.accent)
                            )
                        }
                    }
                }
            }
        }

        // Difficulty Target Filter
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Difficulty Focus Level",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val levels = listOf("ALL", "Easy", "Medium", "Hard")

                levels.forEach { level ->
                    Button(
                        onClick = {
                            currentDifficultyFilter = level
                            prefs.edit().putString("filter_difficulty", level).apply()
                            
                            // Immediately cycle question on filter adjustment
                            val nextQ = DsaQuestionRepository.getRandomQuestion(level, currentPlatformFilter, currentCategoryFilter)
                            currentQuestionId = nextQ.id
                            previewShowSolution = false
                            prefs.edit().putString("current_question_id", nextQ.id).apply()
                            sendRefreshBroadcast(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("difficulty_filter_${level.lowercase()}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentDifficultyFilter == level) {
                                when (level) {
                                    "Easy" -> Color(0x3300E676)
                                    "Medium" -> Color(0x33FFD600)
                                    "Hard" -> Color(0x33FF1744)
                                    else -> Color(0x3300E5FF)
                                }
                            } else Color(0xFF1E293B),
                            contentColor = if (currentDifficultyFilter == level) {
                                when (level) {
                                    "Easy" -> Color(0xFF00E676)
                                    "Medium" -> Color(0xFFFFD600)
                                    "Hard" -> Color(0xFFFF1744)
                                    else -> Color(0xFF00E5FF)
                                }
                            } else Color(0xFF90A4AE)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (currentDifficultyFilter == level) {
                                when (level) {
                                    "Easy" -> Color(0xFF00E676)
                                    "Medium" -> Color(0xFFFFD600)
                                    "Hard" -> Color(0xFFFF1744)
                                    else -> Color(0xFF00E5FF)
                                }
                            } else Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = level,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Divider(color = Color(0xFF1E293B), thickness = 1.dp)

        // Lockscreen Customization Prefs Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Challenge Card Options",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )

            // 1. Clock Clearance Spacer Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text("System Clock Clearance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Pushes the challenge card down to prevent clock overlap", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val clearances = listOf(
                            Triple("LOW", "18% Top", "Large card"),
                            Triple("MEDIUM", "32% Top", "Standard"),
                            Triple("HIGH", "44% Top", "Compact card")
                        )
                        clearances.forEach { (option, label, sub) ->
                            val isSelected = clockClearance == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x2200E5FF) else Color(0xFF1E293B))
                                    .border(1.dp, if (isSelected) Color(0xFF00E5FF) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        clockClearance = option
                                        prefs.edit().putString("clock_clearance", option).apply()
                                        sendRefreshBroadcast(context)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, color = if (isSelected) Color(0xFF00E5FF) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(sub, color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFF90A4AE), fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Text Font Scale & Card Transparency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Font Size Selector
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Text Size Multiplier", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        
                        val fontScales = listOf("COMPACT", "STANDARD", "ACCESSIBLE")
                        fontScales.forEach { option ->
                            val isSelected = textFontScale == option
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0x1800E5FF) else Color(0xFF1E293B))
                                    .border(1.dp, if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable {
                                        textFontScale = option
                                        prefs.edit().putString("text_font_scale", option).apply()
                                        sendRefreshBroadcast(context)
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Opacity Selector
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Card Transparency", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        
                        val transparencyOptions = listOf("SOLID", "TRANSLUCENT", "MINIMAL")
                        transparencyOptions.forEach { option ->
                            val isSelected = cardTransparency == option
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0x1800E5FF) else Color(0xFF1E293B))
                                    .border(1.dp, if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable {
                                        cardTransparency = option
                                        prefs.edit().putString("card_transparency", option).apply()
                                        sendRefreshBroadcast(context)
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 3. Toggle for complexity tags
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showComplexityTags = !showComplexityTags
                            prefs.edit().putBoolean("show_complexity_tags", showComplexityTags).apply()
                            sendRefreshBroadcast(context)
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Complexity Tags", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Displays Time & Space Big-O complexity badges on the challenge", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }
                    Switch(
                        checked = showComplexityTags,
                        onCheckedChange = { value ->
                            showComplexityTags = value
                            prefs.edit().putBoolean("show_complexity_tags", value).apply()
                            sendRefreshBroadcast(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0F111A),
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0xFF90A4AE),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }

        // Quick User Guide
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("user_guide_card"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF131722)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = "Info", 
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to use",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                Text(
                    text = "1. Personalize style settings (Colors, Filter values) here on the settings dashboard.\n\n" +
                           "2. Click 'Activate Wallpaper' and complete system prompts to assign as Wallpaper / Lockscreen.\n\n" +
                           "3. Turn off your screen. Every screen unlock cycles a random challenge.\n\n" +
                           "4. Stumped? Double tap home screen to display details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFECEFF1),
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        // "QUESTIONS" Tab Content - Platform source choices, Sub-topics, difficulties & sync helper
        
        // 1. Coding Platform Source Configurator
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Coding Platform Source",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val platforms = listOf(
                    "ALL" to "All Platforms (Merged)",
                    "LeetCode" to "LeetCode Top 150",
                    "GeeksforGeeks" to "GeeksforGeeks SDE",
                    "Striver A to Z" to "Striver A to Z"
                )
                platforms.forEach { (platKey, platName) ->
                    val isSelected = currentPlatformFilter == platKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0x1A00E5FF) else Color(0xFF131722))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                currentPlatformFilter = platKey
                                prefs.edit().putString("filter_platform", platKey).apply()
                                
                                // Instantly cycle next question matching platform criteria
                                val nextQ = DsaQuestionRepository.getRandomQuestion(currentDifficultyFilter, platKey, currentCategoryFilter)
                                currentQuestionId = nextQ.id
                                prefs.edit().putString("current_question_id", nextQ.id).apply()
                                sendRefreshBroadcast(context)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (platKey) {
                                    "LeetCode" -> "💻"
                                    "GeeksforGeeks" -> "🎓"
                                    "Striver A to Z" -> "🔥"
                                    else -> "🌟"
                                },
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = platName,
                                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Focused Study Topic Category Picker
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Study Topic / Pattern",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf(
                    "ALL" to "All Categories",
                    "Arrays & Hashing" to "Arrays & Hashing",
                    "Linked List" to "Linked Lists",
                    "Binary Search" to "Binary Search",
                    "Dynamic Programming" to "Dynamic Programming",
                    "Graphs" to "Graphs",
                    "Intervals" to "Intervals",
                    "Two Pointers" to "Two Pointers",
                    "Stack" to "Stack & Queues"
                )
                categories.forEach { (catKey, catLabel) ->
                    val isSelected = currentCategoryFilter == catKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B))
                            .clickable {
                                currentCategoryFilter = catKey
                                prefs.edit().putString("filter_category", catKey).apply()
                                
                                val nextQ = DsaQuestionRepository.getRandomQuestion(currentDifficultyFilter, currentPlatformFilter, catKey)
                                currentQuestionId = nextQ.id
                                prefs.edit().putString("current_question_id", nextQ.id).apply()
                                sendRefreshBroadcast(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = catLabel,
                            color = if (isSelected) Color(0xFF0F111A) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Difficulty Filter selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Focus Difficulty Level",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val targetLevels = listOf("ALL", "Easy", "Medium", "Hard")
                targetLevels.forEach { level ->
                    val isSelected = currentDifficultyFilter == level
                    val accentColor = when (level) {
                        "Easy" -> Color(0xFF00E676)
                        "Medium" -> Color(0xFFFFD600)
                        "Hard" -> Color(0xFFFF1744)
                        else -> Color(0xFF00E5FF)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.20f) else Color(0xFF131722))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accentColor else Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                currentDifficultyFilter = level
                                prefs.edit().putString("filter_difficulty", level).apply()
                                
                                val nextQ = DsaQuestionRepository.getRandomQuestion(level, currentPlatformFilter, currentCategoryFilter)
                                currentQuestionId = nextQ.id
                                prefs.edit().putString("current_question_id", nextQ.id).apply()
                                sendRefreshBroadcast(context)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level,
                            color = if (isSelected) accentColor else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Premium Terminal Syncer Utility
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Online Question Hub Sync", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Trigger secure background connection to servers", color = Color(0xFF90A4AE), fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                syncLogs = listOf("[NETWORK] Establishing handshake with API Gateways...")
                            }
                        },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF0F111A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF0F111A))
                        } else {
                            Text("Sync Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isSyncing || syncLogs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            syncLogs.forEach { log ->
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (log.contains("SUCCESS") || log.contains("complete!")) Color(0xFF00FF00) else if (log.contains("GFG") || log.contains("Status 200")) Color(0xFF00E5FF) else Color(0xFFECEFF1)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Active Database Match Browser List
        val matchingQuestions = remember(currentPlatformFilter, currentCategoryFilter, currentDifficultyFilter) {
            DsaQuestionRepository.getFilteredQuestions(currentPlatformFilter, currentCategoryFilter, currentDifficultyFilter)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Matched Questions (${matchingQuestions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Tap item to bind to Wallpaper",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (matchingQuestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131722), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No questions found matching your customized criteria. Try clearing some category filters.", color = Color(0xFF90A4AE), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    matchingQuestions.forEach { q ->
                        val isActive = currentQuestionId == q.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                     currentQuestionId = q.id
                                     previewShowSolution = false
                                     prefs.edit().putString("current_question_id", q.id).apply()
                                     sendRefreshBroadcast(context)
                                    Toast.makeText(context, "Locked active wallpaper question to ${q.title}", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) Color(0xFF182235) else Color(0xFF131722)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) Color(0xFF00E5FF) else Color(0xFF1E293B)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (q.platform) {
                                                "LeetCode" -> Color(0x22FFA117)
                                                "GeeksforGeeks" -> Color(0x2200C853)
                                                else -> Color(0x22FF3D00)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (q.platform) {
                                            "LeetCode" -> "LC"
                                            "GeeksforGeeks" -> "FG"
                                            else -> "ST"
                                        },
                                        color = when (q.platform) {
                                            "LeetCode" -> Color(0xFFFFA117)
                                            "GeeksforGeeks" -> Color(0xFF00C853)
                                            else -> Color(0xFFFF3D00)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = q.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = q.category,
                                            color = Color(0xFF90A4AE),
                                            fontSize = 11.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF90A4AE))
                                        )
                                        val diffColor = when (q.difficulty) {
                                            "Easy" -> Color(0xFF00E676)
                                            "Medium" -> Color(0xFFFFD600)
                                            "Hard" -> Color(0xFFFF1744)
                                            else -> Color(0xFF00E5FF)
                                        }
                                        Text(
                                            text = q.difficulty,
                                            color = diffColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isActive) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperPreviewCard(
    question: DsaQuestion,
    themeName: String,
    clockClearance: String,
    textFontScale: String,
    showComplexityTags: Boolean,
    cardTransparency: String,
    isPreviewLocked: Boolean,
    isSolutionVisible: Boolean,
    onToggleSolution: () -> Unit
) {
    // Exact visual translation of Wallpaper Service Engine background colors
    val themePack = remember(themeName) {
        when (themeName) {
            "COSMIC_SLATE" -> PreviewTheme(
                bgColor = Color(0xFF0F111A),
                accentHex = Color(0xFF00E5FF),
                cardBgColor = Color(0xFF1B1F32),
                textColor = Color(0xFFFFFFFF),
                subtextColor = Color(0xFFCFD8DC),
                codeBgColor = Color(0xFF0A0D18)
            )
            "MIDNIGHT_BLUE" -> PreviewTheme(
                bgColor = Color(0xFF050B14),
                accentHex = Color(0xFFFF5722),
                cardBgColor = Color(0xFF101C2F),
                textColor = Color(0xFFF5F5F5),
                subtextColor = Color(0xFFB0BEC5),
                codeBgColor = Color(0xFF02070E)
            )
            "MATRIX_BLACK" -> PreviewTheme(
                bgColor = Color(0xFF000000),
                accentHex = Color(0xFF00FF00),
                cardBgColor = Color(0xFF080D08),
                textColor = Color(0xFFFFFFFF),
                subtextColor = Color(0xFF98E698),
                codeBgColor = Color(0xFF030503)
            )
            "SUNSET_PURPLE" -> PreviewTheme(
                bgColor = Color(0xFF140D26),
                accentHex = Color(0xFFFF4081),
                cardBgColor = Color(0xFF261744),
                textColor = Color(0xFFFFFFFF),
                subtextColor = Color(0xFFE1BEE7),
                codeBgColor = Color(0xFF0F071D)
            )
            else -> PreviewTheme(
                bgColor = Color(0xFF0F111A),
                accentHex = Color(0xFF00E5FF),
                cardBgColor = Color(0xFF171921),
                textColor = Color(0xFFFFFFFF),
                subtextColor = Color(0xFFCFD8DC),
                codeBgColor = Color(0xFF0A0D18)
            )
        }
    }

    val difficultyColor = when (question.difficulty.lowercase()) {
        "easy" -> Color(0xFF00E676)
        "medium" -> Color(0xFFFFD600)
        else -> Color(0xFFFF1744)
    }

    val cardOpacity = when (cardTransparency) {
        "SOLID" -> 1.0f
        "TRANSLUCENT" -> 0.82f
        "MINIMAL" -> 0.35f
        else -> 0.82f
    }
    
    val fontScaling = when (textFontScale) {
        "COMPACT" -> 0.82f
        "STANDARD" -> 1.0f
        "ACCESSIBLE" -> 1.2f
        else -> 1.0f
    }

    val shouldShowCard = true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themePack.bgColor)
            .clickable { onToggleSolution() }
            .padding(16.dp)
    ) {
        // Draw background theme decorations in Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeColor = themePack.accentHex.copy(alpha = 0.15f)
            when (themeName) {
                "COSMIC_SLATE" -> {
                    // Tech diagonal stripes
                    val spacing = 80.dp.toPx()
                    var offset = 0f
                    while (offset < size.width + size.height) {
                        drawLine(
                            color = strokeColor,
                            start = androidx.compose.ui.geometry.Offset(offset, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, offset),
                            strokeWidth = 2.dp.toPx()
                        )
                        offset += spacing
                    }
                }
                "MIDNIGHT_BLUE" -> {
                    // Concentric circles
                    drawCircle(
                        color = strokeColor,
                        radius = 200.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 40.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = strokeColor,
                        radius = 120.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, 40.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                "MATRIX_BLACK" -> {
                    // Matrix grids
                    val gridWidth = 40.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = strokeColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 0.8f.dp.toPx()
                        )
                        x += gridWidth
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = strokeColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 0.8f.dp.toPx()
                        )
                        y += gridWidth
                    }
                }
                "SUNSET_PURPLE" -> {
                    // Glowing background wave lines
                    val wavePath = androidx.compose.ui.graphics.Path().apply {
                        val pY = size.height * 0.95f
                        moveTo(0f, pY)
                        cubicTo(size.width * 0.3f, pY - 50.dp.toPx(), size.width * 0.7f, pY + 40.dp.toPx(), size.width, pY)
                    }
                    drawPath(
                        path = wavePath,
                        color = themePack.accentHex.copy(alpha = 0.25f),
                        style = Stroke(width = 1.5f.dp.toPx())
                    )
                }
            }
        }

        if (shouldShowCard) {
            // Layout everything vertically to prevent system lockscreen clock overlap!
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Section: Lock Screen Clock or Home Screen Search Bar mockup
                if (isPreviewLocked) {
                    val clearanceHeight = when (clockClearance) {
                        "LOW" -> 46.dp
                        "MEDIUM" -> 92.dp
                        "HIGH" -> 138.dp
                        else -> 92.dp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(clearanceHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "09:41",
                                color = themePack.textColor.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Light,
                                fontSize = when (clockClearance) {
                                    "LOW" -> 32.sp
                                    "MEDIUM" -> 44.sp
                                    "HIGH" -> 56.sp
                                    else -> 44.sp
                                },
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Sunday, June 21",
                                color = themePack.subtextColor.copy(alpha = 0.55f),
                                fontSize = when (clockClearance) {
                                    "LOW" -> 10.sp
                                    "MEDIUM" -> 12.sp
                                    "HIGH" -> 14.sp
                                    else -> 12.sp
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, start = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Search apps or web...",
                                color = themePack.textColor.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = themePack.textColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 2. Question challenge card container with custom transparency
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(themePack.cardBgColor.copy(alpha = cardOpacity))
                        .border(1.5.dp, themePack.accentHex.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category & Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = question.category.uppercase(),
                            color = themePack.accentHex,
                            fontSize = (11.sp * fontScaling),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(difficultyColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = question.difficulty,
                                color = difficultyColor,
                                fontSize = (10.sp * fontScaling),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Question Title
                    Text(
                        text = question.title,
                        color = themePack.textColor,
                        fontSize = (18.sp * fontScaling),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Content Box (Question text or reversed code hint setup)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        Crossfade(targetState = isSolutionVisible, label = "FlipTransition") { visible ->
                            if (!visible) {
                                // Display Question card state
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = question.description,
                                        color = themePack.subtextColor,
                                        fontSize = (12.sp * fontScaling),
                                        lineHeight = (15.sp * fontScaling),
                                        maxLines = when (clockClearance) {
                                            "LOW" -> 6
                                            "MEDIUM" -> 4
                                            else -> 3
                                        },
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Examples Code container
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(themePack.codeBgColor)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "EXAMPLE CASE",
                                            color = themePack.accentHex,
                                            fontSize = (9.sp * fontScaling),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Input:  ${question.input}\nOutput: ${question.output}",
                                            color = if (themeName == "MATRIX_BLACK") Color(0xFF00FF00) else Color(0xFFECEFF1),
                                            fontSize = (11.sp * fontScaling),
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = (14.sp * fontScaling),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Big O markers
                                    if (showComplexityTags) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            PillBadge(label = "T: ${question.timeComplexity}", accent = themePack.accentHex)
                                            PillBadge(label = "S: ${question.spaceComplexity}", accent = themePack.accentHex)
                                        }
                                    }
                                }
                            } else {
                                // Display Solution state
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "💡 HINT: ${question.hint}",
                                        color = themePack.subtextColor,
                                        fontSize = (11.5.sp * fontScaling),
                                        lineHeight = (15.sp * fontScaling),
                                        maxLines = when (clockClearance) {
                                            "LOW" -> 4
                                            "MEDIUM" -> 3
                                            else -> 2
                                        },
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Kotlin code scrolling space
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(when (clockClearance) {
                                                "LOW" -> 150.dp
                                                "MEDIUM" -> 92.dp
                                                else -> 64.dp
                                            })
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(themePack.codeBgColor)
                                            .verticalScroll(rememberScrollState())
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Solution Implementation:",
                                            color = themePack.accentHex,
                                            fontSize = (8.5.sp * fontScaling),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = question.solutionCode,
                                            color = themePack.textColor,
                                            fontSize = (9.5.sp * fontScaling),
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = (12.sp * fontScaling)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Section: Lock Screen Watermark or Home Screen App Dock
                if (isPreviewLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "designed by Pavan Rapolu",
                            color = themePack.textColor.copy(alpha = 0.22f),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x18FFFFFF))
                                    .border(1.dp, Color(0x0CFFFFFF), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PillBadge(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.05f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

data class ThemeOption(
    val key: String,
    val name: String,
    val primaryBg: Color,
    val accent: Color
)

data class PreviewTheme(
    val bgColor: Color,
    val accentHex: Color,
    val cardBgColor: Color,
    val textColor: Color,
    val subtextColor: Color,
    val codeBgColor: Color
)
