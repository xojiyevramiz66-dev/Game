package com.example.catchgame

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F0F1A)
                ) {
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

@Composable
private fun CatchGameApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("catch_game_settings", Context.MODE_PRIVATE) }

    var screen by remember { mutableStateOf(GameScreen.MENU) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getInt("best_score", 0)) }
    var bestCombo by remember { mutableIntStateOf(prefs.getInt("best_combo", 0)) }
    var lives by remember { mutableIntStateOf(3) }
    var combo by remember { mutableIntStateOf(0) }
    var slowTicks by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound_on", true)) }
    var vibrationOn by remember { mutableStateOf(prefs.getBoolean("vibration_on", true)) }

    var items by remember { mutableStateOf<List<FallingItem>>(emptyList()) }
    var nextItemId by remember { mutableIntStateOf(0) }

    val currentItems by rememberUpdatedState(items)
    val currentScreen by rememberUpdatedState(screen)
    val currentPaused by rememberUpdatedState(paused)
    val currentGameOver by rememberUpdatedState(gameOver)
    val currentScore by rememberUpdatedState(score)
    val currentSlowTicks by rememberUpdatedState(slowTicks)
    val currentSoundOn by rememberUpdatedState(soundOn)
    val currentVibrationOn by rememberUpdatedState(vibrationOn)

    val level = score / 10 + 1

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", score).apply()
        }
    }

    LaunchedEffect(bestCombo) {
        prefs.edit().putInt("best_combo", bestCombo).apply()
    }

    fun feedback(isBomb: Boolean = false) {
        if (currentSoundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                tone.startTone(if (isBomb) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP, 100)
                tone.release()
            } catch (_: Exception) {}
        }
        if (currentVibrationOn) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    val duration = if (isBomb) 180L else 45L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, if (isBomb) 200 else 80))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun startGame() {
        score = 0
        lives = 3
        combo = 0
        slowTicks = 0
        items = emptyList()
        nextItemId = 0
        gameOver = false
        paused = false
        screen = GameScreen.GAME
    }

    when (screen) {
        GameScreen.MENU -> {
            GamePanel(
                title = "CATCH GAME 4K",
                subtitle = "Ультра-чёткий ловчий азарт!"
            ) {
                MenuButton("Играть", "▶") { startGame() }
                MenuButton("Рекорды", "🏆") { screen = GameScreen.RECORDS }
                MenuButton("Настройки", "⚙") { screen = GameScreen.SETTINGS }
                Spacer(Modifier.height(16.dp))
                Text("🟡 Золото даёт +5 и бонус комбо", color = Color(0xFFFFD700), fontSize = 14.sp)
                Text("🔵 Замедление замораживает время", color = Color(0xFF00E5FF), fontSize = 14.sp)
            }
        }
        GameScreen.RECORDS -> {
            GamePanel(title = "РЕКОРДЫ", subtitle = "Зафиксированные достижения") {
                InfoCard("Лучший счёт", "$bestScore очков", "🏆")
                InfoCard("Макс. комбо", "$bestCombo подряд", "🔥")
                Spacer(Modifier.height(16.dp))
                MenuButton("Назад", "←") { screen = GameScreen.MENU }
            }
        }
        GameScreen.SETTINGS -> {
            GamePanel(title = "НАСТРОЙКИ", subtitle = "Параметры обратной связи") {
                SettingToggle("Звуковые эффекты", soundOn) { enabled ->
                    soundOn = enabled
                    prefs.edit().putBoolean("sound_on", enabled).apply()
                }
                SettingToggle("Вибрация Haptic", vibrationOn) { enabled ->
                    vibrationOn = enabled
                    prefs.edit().putBoolean("vibration_on", enabled).apply()
                }
                Spacer(Modifier.height(16.dp))
                MenuButton("Назад", "←") { screen = GameScreen.MENU }
            }
        }
        GameScreen.GAME -> {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0B0B1A), Color(0xFF16162E))
                        )
                    )
            ) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                LaunchedEffect(screen, paused, gameOver, screenWidth, screenHeight) {
                    if (screen == GameScreen.GAME && !paused && !gameOver && screenWidth > 0f && screenHeight > 0f) {
                        while (true) {
                            delay(33L)
                            if (currentScreen != GameScreen.GAME || currentPaused || currentGameOver) break

                            val activeLevel = currentScore / 10 + 1
                            val spawnChance = (0.07f + activeLevel * 0.008f).coerceAtMost(0.22f)
                            val updated = currentItems.toMutableList()

                            if (Random.nextFloat() < spawnChance) {
                                val radius = 38f
                                val maxX = (screenWidth - radius * 2).coerceAtLeast(0f)
                                val roll = Random.nextFloat()
                                val kind = when {
                                    roll < 0.16f -> ItemKind.BOMB
                                    roll < 0.28f -> ItemKind.GOLD
                                    roll < 0.35f -> ItemKind.SLOW
                                    else -> ItemKind.NORMAL
                                }
                                val levelBonus = ((activeLevel - 1) * 0.35f).coerceAtMost(7f)
                                val speed = Random.nextFloat() * 2.2f + 3.2f + levelBonus

                                updated.add(
                                    FallingItem(
                                        id = nextItemId++,
                                        x = Random.nextFloat() * maxX + radius,
                                        y = -radius,
                                        radius = if (kind == ItemKind.GOLD) 32f else radius,
                                        kind = kind,
                                        speed = speed
                                    )
                                )
                            }

                            val timeScale = if (currentSlowTicks > 0) 0.4f else 1f
                            items = updated.mapNotNull { item ->
                                val newY = item.y + item.speed * timeScale
                                if (newY > screenHeight + item.radius * 2) null else item.copy(y = newY)
                            }

                            if (currentSlowTicks > 0) {
                                slowTicks = (currentSlowTicks - 1).coerceAtLeast(0)
                            }
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                if (currentScreen != GameScreen.GAME || currentPaused || currentGameOver) return@detectTapGestures

                                val hit = currentItems.asReversed().firstOrNull { item ->
                                    val dx = tap.x - item.x
                                    val dy = tap.y - item.y
                                    val hitRadius = item.radius + 32f
                                    dx * dx + dy * dy <= hitRadius * hitRadius
                                }

                                if (hit != null) {
                                    items = currentItems.filterNot { it.id == hit.id }
                                    when (hit.kind) {
                                        ItemKind.BOMB -> {
                                            lives = (lives - 1).coerceAtLeast(0)
                                            combo = 0
                                            feedback(isBomb = true)
                                            if (lives == 0) gameOver = true
                                        }
                                        ItemKind.GOLD -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            score += 5 + combo / 5
                                            feedback()
                                        }
                                        ItemKind.SLOW -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            score += 2
                                            slowTicks = 110
                                            feedback()
                                        }
                                        ItemKind.NORMAL -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            score += if (combo % 5 == 0) 4 else 1
                                            feedback()
                                        }
                                    }
                                } else {
                                    combo = 0
                                }
                            }
                        }
                ) {
                    items.forEach { item ->
                        val center = Offset(item.x, item.y)
                        val mainColor = when (item.kind) {
                            ItemKind.NORMAL -> Color(0xFFFFCC00)
                            ItemKind.BOMB -> Color(0xFFFF3366)
                            ItemKind.GOLD -> Color(0xFFFFD700)
                            ItemKind.SLOW -> Color(0xFF00E5FF)
                        }

                        drawCircle(
                            color = mainColor.copy(alpha = 0.25f),
                            radius = item.radius * 1.55f * (if (item.kind == ItemKind.GOLD) pulseScale else 1f),
                            center = center
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, mainColor, mainColor.copy(alpha = 0.8f)),
                                center = Offset(item.x - item.radius * 0.3f, item.y - item.radius * 0.3f),
                                radius = item.radius * 1.2f
                            ),
                            radius = item.radius,
                            center = center
                        )

                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = item.radius * 0.28f,
                            center = Offset(item.x - item.radius * 0.35f, item.y - item.radius * 0.35f)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E1E36).copy(alpha = 0.85f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("СЧЁТ", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$score", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("РЕКОРД: $bestScore", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("УРОВЕНЬ $level", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(2.dp))
                            Text("❤️".repeat(lives), fontSize = 18.sp)
                        }
                    }

                    if (combo >= 2 || slowTicks > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (combo >= 2) {
                                Text("🔥 КОМБО x$combo", color = Color(0xFFFF9100), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            if (slowTicks > 0) {
                                Text("⏳ ЗАМЕДЛЕНИЕ", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                if (paused && !gameOver) {
                    OverlayPanel(title = "ПАУЗА", subtitle = "Игра на паузе") {
                        MenuButton("Продолжить", "▶") { paused = false }
                        MenuButton("Перезапустить", "↻") { startGame() }
                        MenuButton("Главное меню", "⌂") {
                            paused = false
                            screen = GameScreen.MENU
                        }
                    }
                } else if (gameOver) {
                    OverlayPanel(title = "ИГРА ОКОНЧЕНА", subtitle = "Итоговый счёт: $score | Макс. комбо: $bestCombo") {
                        MenuButton("Играть снова", "↻") { startGame() }
                        MenuButton("Главное меню", "⌂") { screen = GameScreen.MENU }
                    }
                } else {
                    Button(
                        onClick = { paused = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B4A))
                    ) {
                        Text("⏸ Пауза", color = Color.White, fontWeight = FontWeight.Bold)
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A14), Color(0xFF18182E))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16162B).copy(alpha = 0.9f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color(0xFFFFD700), fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun MenuButton(label: String, symbol: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D4A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(symbol, fontSize = 20.sp, color = Color(0xFFFFD700))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, symbol: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22223D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF22223D))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16162B))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
