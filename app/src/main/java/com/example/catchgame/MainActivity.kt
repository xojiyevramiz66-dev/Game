package com.example.catchgame

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 5, 15)
        window.navigationBarColor = android.graphics.Color.rgb(5, 5, 15)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF05050F)) {
                    CatchGameApp()
                }
            }
        }
    }
}

private enum class GameScreen { MENU, GAME, RECORDS, SETTINGS }
private enum class ItemKind { NORMAL, BOMB, GOLD, SLOW }

private data class FallingItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val kind: ItemKind,
    val speed: Float
)

private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

@Composable
private fun CatchGameApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("catch_game_settings", Context.MODE_PRIVATE)
    }

    var screen by remember { mutableStateOf(GameScreen.MENU) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember {
        mutableIntStateOf(prefs.getInt("best_score", 0))
    }
    var lives by remember { mutableIntStateOf(3) }
    var slowSeconds by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var soundOn by remember {
        mutableStateOf(prefs.getBoolean("sound_on", true))
    }
    var vibrationOn by remember {
        mutableStateOf(prefs.getBoolean("vibration_on", true))
    }

    val items = remember { mutableStateListOf<FallingItem>() }
    var nextItemId by remember { mutableIntStateOf(0) }

    val currentScreen by rememberUpdatedState(screen)
    val currentPaused by rememberUpdatedState(paused)
    val currentGameOver by rememberUpdatedState(gameOver)
    val currentScore by rememberUpdatedState(score)
    val currentSlowSeconds by rememberUpdatedState(slowSeconds)
    val currentSoundOn by rememberUpdatedState(soundOn)
    val currentVibrationOn by rememberUpdatedState(vibrationOn)
    val level = score / 10 + 1

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", score).apply()
        }
    }

    fun feedback(isBomb: Boolean = false) {
        if (currentSoundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tone.startTone(
                    if (isBomb) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP,
                    if (isBomb) 140 else 70
                )
                tone.release()
            } catch (_: Exception) { }
        }
        if (currentVibrationOn) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator?.hasVibrator() == true) {
                    val duration = if (isBomb) 180L else 35L
                    val amplitude = if (isBomb) 230 else 80
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun startGame() {
        score = 0
        lives = 3
        slowSeconds = 0f
        items.clear()
        nextItemId = 0
        gameOver = false
        paused = false
        screen = GameScreen.GAME
    }

    when (screen) {
        GameScreen.MENU -> GamePanel(
            title = "CATCH GAME ULTRA",
            subtitle = "Кибернетический аркадный экшен"
        ) {
            MenuButton("Играть", "▶") { startGame() }
            MenuButton("Рекорды", "🏆") { screen = GameScreen.RECORDS }
            MenuButton("Настройки", "⚙") { screen = GameScreen.SETTINGS }
            Spacer(Modifier.height(16.dp))
            Text(
                "✨ Золото: +5 очков | 🔵 Время: замедление",
                color = Color(0xFF00E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        GameScreen.RECORDS -> GamePanel(
            title = "РЕКОРДЫ",
            subtitle = "Ваши высшие достижения"
        ) {
            InfoCard("Лучший счёт", "$bestScore очков", "🏆")
            Spacer(Modifier.height(16.dp))
            MenuButton("Назад", "←") { screen = GameScreen.MENU }
        }

        GameScreen.SETTINGS -> GamePanel(
            title = "НАСТРОЙКИ",
            subtitle = "Звук и тактильный отклик"
        ) {
            SettingToggle("Звуковые эффекты", soundOn) { enabled ->
                soundOn = enabled
                prefs.edit().putBoolean("sound_on", enabled).apply()
            }
            SettingToggle("Вибрация", vibrationOn) { enabled ->
                vibrationOn = enabled
                prefs.edit().putBoolean("vibration_on", enabled).apply()
            }
            Spacer(Modifier.height(16.dp))
            MenuButton("Назад", "←") { screen = GameScreen.MENU }
        }

        GameScreen.GAME -> {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF03030B), Color(0xFF0A1027), Color(0xFF17183B))
                    )
                )
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xEE17172F), Color(0xEE202044), Color(0xEE17172F))
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("СЧЁТ", color = Color(0xFF9BA4C7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("$score", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                                Text("РЕКОРД: $bestScore", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("УРОВЕНЬ $level", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(4.dp))
                                Text("❤️".repeat(lives), fontSize = 18.sp)
                            }
                        }
                        if (slowSeconds > 0f) {
                            Spacer(Modifier.height(8.dp))
                            Text("⏳ ВРЕМЯ ЗАМЕДЛЕНО", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    BoxWithConstraints(
                        Modifier.fillMaxWidth().weight(1f)
                    ) {
                        val screenWidth = constraints.maxWidth.toFloat()
                        val screenHeight = constraints.maxHeight.toFloat()
                        val stars = remember(screenWidth, screenHeight) {
                            List(50) {
                                Star(
                                    x = Random.nextFloat() * screenWidth.coerceAtLeast(1f),
                                    y = Random.nextFloat() * screenHeight.coerceAtLeast(1f),
                                    radius = Random.nextFloat() * 1.8f + 0.35f,
                                    phase = Random.nextFloat() * 6.28f
                                )
                            }
                        }
                        var visualTime by remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(screen, paused, gameOver, screenWidth, screenHeight) {
                            if (screen == GameScreen.GAME && !paused && !gameOver &&
                                screenWidth > 0f && screenHeight > 0f
                            ) {
                                var previousFrameMs = SystemClock.elapsedRealtime()
                                while (true) {
                                    delay(16L)
                                    if (currentScreen != GameScreen.GAME || currentPaused || currentGameOver) break
                                    val nowMs = SystemClock.elapsedRealtime()
                                    val deltaSeconds = ((nowMs - previousFrameMs) / 1000f).coerceIn(0f, 0.05f)
                                    previousFrameMs = nowMs
                                    visualTime += deltaSeconds
                                    val frameScale = deltaSeconds / 0.025f
                                    val activeLevel = currentScore / 10 + 1
                                    val spawnRate = 3.4f * (1f + (activeLevel - 1) * 0.09f)
                                    val spawnChance = (1f - exp(-spawnRate * deltaSeconds)).coerceIn(0f, 0.24f)

                                    if (Random.nextFloat() < spawnChance) {
                                        val radius = 40f
                                        val maxX = (screenWidth - radius * 2).coerceAtLeast(0f)
                                        val roll = Random.nextFloat()
                                        val kind = when {
                                            roll < 0.15f -> ItemKind.BOMB
                                            roll < 0.27f -> ItemKind.GOLD
                                            roll < 0.34f -> ItemKind.SLOW
                                            else -> ItemKind.NORMAL
                                        }
                                        val levelBonus = ((activeLevel - 1) * 0.3f).coerceAtMost(6f)
                                        val speed = Random.nextFloat() * 2.5f + 3.5f + levelBonus
                                        
                                        items.add(
                                            FallingItem(
                                                id = nextItemId++,
                                                x = Random.nextFloat() * maxX + radius,
                                                y = -radius,
                                                radius = if (kind == ItemKind.GOLD) 34f else radius,
                                                kind = kind,
                                                speed = speed
                                            )
                                        )
                                    }

                                    val timeScale = if (currentSlowSeconds > 0f) 0.38f else 1f
                                    
                                    val updatedList = items.mapNotNull { item ->
                                        val newY = item.y + item.speed * timeScale * frameScale
                                        if (newY > screenHeight + item.radius * 2) {
                                            null
                                        } else {
                                            item.copy(y = newY)
                                        }
                                    }
                                    items.clear()
                                    items.addAll(updatedList)

                                    if (currentSlowSeconds > 0f) {
                                        slowSeconds = (currentSlowSeconds - deltaSeconds).coerceAtLeast(0f)
                                    }
                                }
                            }
                        }

                        Canvas(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures { tap ->
                                    if (currentScreen != GameScreen.GAME || currentPaused || currentGameOver) {
                                        return@detectTapGestures
                                    }
                                    
                                    val hit = items.asReversed().firstOrNull { item ->
                                        val dx = tap.x - item.x
                                        val dy = tap.y - item.y
                                        val hitRadius = item.radius + 70f
                                        dx * dx + dy * dy <= hitRadius * hitRadius
                                    }

                                    if (hit != null) {
                                        items.remove(hit)
                                        when (hit.kind) {
                                            ItemKind.BOMB -> {
                                                lives = (lives - 1).coerceAtLeast(0)
                                                feedback(isBomb = true)
                                                if (lives == 0) gameOver = true
                                            }
                                            ItemKind.GOLD -> { score += 5; feedback() }
                                            ItemKind.SLOW -> { score += 2; slowSeconds = 3f; feedback() }
                                            ItemKind.NORMAL -> { score += 1; feedback() }
                                        }
                                    }
                                }
                            }
                        ) {
                            stars.forEach { star ->
                                val shimmer = (0.38f + 0.32f * sin(visualTime * 1.7f + star.phase)).coerceIn(0.08f, 0.85f)
                                drawCircle(
                                    Color(0xFFB7D8FF).copy(alpha = shimmer),
                                    radius = star.radius,
                                    center = Offset(star.x, star.y)
                                )
                            }

                            items.forEach { item ->
                                val center = Offset(item.x, item.y)
                                val color = when (item.kind) {
                                    ItemKind.NORMAL -> Color(0xFFFFC928)
                                    ItemKind.BOMB -> Color(0xFFFF315A)
                                    ItemKind.GOLD -> Color(0xFFFFD700)
                                    ItemKind.SLOW -> Color(0xFF00D9FF)
                                }

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,
                                            color.copy(alpha = 0.98f),
                                            color.copy(alpha = 0.90f),
                                            color.copy(
                                                red = (color.red * 0.43f).coerceIn(0f, 1f),
                                                green = (color.green * 0.43f).coerceIn(0f, 1f),
                                                blue = (color.blue * 0.55f).coerceIn(0f, 1f)
                                            )
                                        ),
                                        center = Offset(item.x - item.radius * 0.34f, item.y - item.radius * 0.38f),
                                        radius = item.radius * 1.48f
                                    ),
                                    radius = item.radius,
                                    center = center
                                )
                                drawCircle(
                                    color.copy(alpha = 0.85f),
                                    radius = item.radius,
                                    center = center,
                                    style = Stroke(width = 1.7f)
                                )

                                drawCircle(
                                    Color.White.copy(alpha = 0.88f),
                                    radius = item.radius * 0.20f,
                                    center = Offset(item.x - item.radius * 0.34f, item.y - item.radius * 0.38f)
                                )
                                drawArc(
                                    color = Color.White.copy(alpha = 0.46f),
                                    startAngle = 205f,
                                    sweepAngle = 105f,
                                    useCenter = false,
                                    topLeft = Offset(item.x - item.radius * 0.68f, item.y - item.radius * 0.68f),
                                    size = androidx.compose.ui.geometry.Size(item.radius * 1.36f, item.radius * 1.36f),
                                    style = Stroke(width = 2.1f, cap = StrokeCap.Round)
                                )

                                when (item.kind) {
                                    ItemKind.NORMAL -> {
                                        drawCircle(
                                            Color.White.copy(alpha = 0.42f),
                                            radius = item.radius * 0.48f,
                                            center = Offset(item.x + item.radius * 0.12f, item.y + item.radius * 0.18f),
                                            style = Stroke(width = 1.4f)
                                        )
                                    }
                                    ItemKind.BOMB -> {
                                        val fuseStart = Offset(item.x + item.radius * 0.28f, item.y - item.radius * 0.76f)
                                        val fuseEnd = Offset(item.x + item.radius * 0.58f, item.y - item.radius * 1.02f)
                                        drawLine(Color(0xFFFFD7A1), fuseStart, fuseEnd, strokeWidth = 4f, cap = StrokeCap.Round)
                                        drawCircle(Color(0xFFFFE082), 4.2f, fuseEnd)
                                        drawLine(Color.White.copy(alpha = 0.9f),
                                            Offset(item.x - item.radius * 0.28f, item.y),
                                            Offset(item.x + item.radius * 0.28f, item.y),
                                            strokeWidth = 3f, cap = StrokeCap.Round)
                                        drawLine(Color.White.copy(alpha = 0.9f),
                                            Offset(item.x, item.y - item.radius * 0.28f),
                                            Offset(item.x, item.y + item.radius * 0.28f),
                                            strokeWidth = 3f, cap = StrokeCap.Round)
                                    }
                                    ItemKind.GOLD -> {
                                        val path = Path()
                                        val outer = item.radius * 0.43f
                                        val inner = outer * 0.46f
                                        for (i in 0 until 10) {
                                            val angle = -PI / 2.0 + i * PI / 5.0
                                            val r = if (i % 2 == 0) outer else inner
                                            val px = item.x + cos(angle).toFloat() * r
                                            val py = item.y + sin(angle).toFloat() * r
                                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                        }
                                        path.close()
                                        drawPath(path, Color(0xFFFFF4B0))
                                    }
                                    ItemKind.SLOW -> {
                                        drawCircle(Color.White.copy(alpha = 0.9f), item.radius * 0.48f,
                                            center, style = Stroke(width = 2.4f))
                                        drawLine(Color.White, center,
                                            Offset(item.x, item.y - item.radius * 0.28f),
                                            strokeWidth = 2.5f, cap = StrokeCap.Round)
                                        drawLine(Color.White, center,
                                            Offset(item.x + item.radius * 0.23f, item.y + item.radius * 0.10f),
                                            strokeWidth = 2.5f, cap = StrokeCap.Round)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!paused && !gameOver) {
                    Button(
                        onClick = { paused = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252545))
                    ) {
                        Text("⏸ Пауза", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (paused && !gameOver) {
                    OverlayPanel("ПАУЗА", "Игра приостановлена") {
                        MenuButton("Продолжить", "▶") { paused = false }
                        MenuButton("Заново", "↻") { startGame() }
                        MenuButton("В меню", "⌂") { paused = false; screen = GameScreen.MENU }
                    }
                } else if (gameOver) {
                    OverlayPanel("ИГРА ОКОНЧЕНА", "Результат: $score | Рекорд: $bestScore") {
                        MenuButton("Играть снова", "↻") { startGame() }
                        MenuButton("Главное меню", "⌂") { screen = GameScreen.MENU }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF04040A), Color(0xFF121226), Color(0xFF080817))))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF202044), Color(0xFF111126)))
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color(0xFFFFD700), fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFFACB2D0), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun MenuButton(label: String, symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29294F))
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, fontSize = 18.sp, color = Color(0xFFFFD700))
            Spacer(Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, symbol: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C36))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = Color.Gray, fontSize = 12.sp)
                Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(symbol, fontSize = 26.sp)
        }
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C36))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700))
            )
        }
    }
}

@Composable
private fun OverlayPanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE8000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF242448), Color(0xFF121228))))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFFACB2D0), fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
