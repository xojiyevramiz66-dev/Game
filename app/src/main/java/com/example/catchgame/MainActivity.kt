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
                    color = Color(0xFF070710)
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

// Эффект всплывающего текста при клике (очки / урон)
private data class FloatingText(
    val id: Int,
    val text: String,
    val color: Color,
    var x: Float,
    var y: Float,
    var alpha: Float = 1f
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
    var floatingTexts by remember { mutableStateOf<List<FloatingText>>(emptyList()) }
    var nextItemId by remember { mutableIntStateOf(0) }
    var nextTextId by remember { mutableIntStateOf(0) }

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
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                tone.startTone(if (isBomb) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP, 90)
                tone.release()
            } catch (_: Exception) {}
        }
        if (currentVibrationOn) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    val duration = if (isBomb) 200L else 40L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, if (isBomb) 255 else 90))
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
        floatingTexts = emptyList()
        nextItemId = 0
        gameOver = false
        paused = false
        screen = GameScreen.GAME
    }

    when (screen) {
        GameScreen.MENU -> {
            GamePanel(
                title = "CATCH GAME ULTRA",
                subtitle = "Кибернетический аркадный экшен"
            ) {
                MenuButton("Играть", "▶") { startGame() }
                MenuButton("Рекорды", "🏆") { screen = GameScreen.RECORDS }
                MenuButton("Настройки", "⚙") { screen = GameScreen.SETTINGS }
                Spacer(Modifier.height(16.dp))
                Text("✨ Золото: +очки | 🔵 Время: замедление", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        GameScreen.RECORDS -> {
            GamePanel(title = "РЕКОРДЫ", subtitle = "Ваши высшие достижения") {
                InfoCard("Лучший счёт", "$bestScore очков", "🏆")
                InfoCard("Максимальное комбо", "$bestCombo подряд", "🔥")
                Spacer(Modifier.height(16.dp))
                MenuButton("Назад", "←") { screen = GameScreen.MENU }
            }
        }
        GameScreen.SETTINGS -> {
            GamePanel(title = "НАСТРОЙКИ", subtitle = "Аудио и тактильный отклик") {
                SettingToggle("Звуковые эффекты", soundOn) { enabled ->
                    soundOn = enabled
                    prefs.edit().putBoolean("sound_on", enabled).apply()
                }
                SettingToggle("Haptic Вибрация", vibrationOn) { enabled ->
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
                            colors = listOf(Color(0xFF05050C), Color(0xFF0F0F1E), Color(0xFF1A1A3A))
                        )
                    )
            ) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                // Пульсация для Ultra HD элементов
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                LaunchedEffect(screen, paused, gameOver, screenWidth, screenHeight) {
                    if (screen == GameScreen.GAME && !paused && !gameOver && screenWidth > 0f && screenHeight > 0f) {
                        while (true) {
                            delay(25L) // Высокая частота кадров (~40 fps) для плавности
                            if (currentScreen != GameScreen.GAME || currentPaused || currentGameOver) break

                            val activeLevel = currentScore / 10 + 1
                            val spawnChance = (0.075f + activeLevel * 0.007f).coerceAtMost(0.24f)
                            val updatedItems = currentItems.toMutableList()

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

                                updatedItems.add(
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

                            val timeScale = if (currentSlowTicks > 0) 0.38f else 1f
                            items = updatedItems.mapNotNull { item ->
                                val newY = item.y + item.speed * timeScale
                                if (newY > screenHeight + item.radius * 2) null else item.copy(y = newY)
                            }

                            // Анимация всплывающих текстов
                            floatingTexts = floatingTexts.mapNotNull { ft ->
                                val newY = ft.y - 3f
                                val newAlpha = ft.alpha - 0.035f
                                if (newAlpha <= 0f) null else ft.copy(y = newY, alpha = newAlpha)
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
                                    val hitRadius = item.radius + 36f
                                    dx * dx + dy * dy <= hitRadius * hitRadius
                                }

                                if (hit != null) {
                                    items = currentItems.filterNot { it.id == hit.id }
                                    when (hit.kind) {
                                        ItemKind.BOMB -> {
                                            lives = (lives - 1).coerceAtLeast(0)
                                            combo = 0
                                            feedback(isBomb = true)
                                            floatingTexts = floatingTexts + FloatingText(nextTextId++, "-1 Жизнь", Color(0xFFFF3366), hit.x, hit.y)
                                            if (lives == 0) gameOver = true
                                        }
                                        ItemKind.GOLD -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            val pts = 5 + combo / 5
                                            score += pts
                                            feedback()
                                            floatingTexts = floatingTexts + FloatingText(nextTextId++, "+$pts Золото!", Color(0xFFFFD700), hit.x, hit.y)
                                        }
                                        ItemKind.SLOW -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            score += 2
                                            slowTicks = 120
                                            feedback()
                                            floatingTexts = floatingTexts + FloatingText(nextTextId++, "⏳ Замедление", Color(0xFF00E5FF), hit.x, hit.y)
                                        }
                                        ItemKind.NORMAL -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            val pts = if (combo % 5 == 0) 4 else 1
                                            score += pts
                                            feedback()
                                            if (pts > 1) {
                                                floatingTexts = floatingTexts + FloatingText(nextTextId++, "+$pts Комбо!", Color(0xFFFF9100), hit.x, hit.y)
                                            }
                                        }
                                    }
                                } else {
                                    combo = 0
                                }
                            }
                        }
                ) {
                    // 1. Отрисовка падающих объектов с Ultra HD градиентами и свечением
                    items.forEach { item ->
                        val center = Offset(item.x, item.y)
                        val mainColor = when (item.kind) {
                            ItemKind.NORMAL -> Color(0xFFFFCC00)
                            ItemKind.BOMB -> Color(0xFFFF2255)
                            ItemKind.GOLD -> Color(0xFFFFD700)
                            ItemKind.SLOW -> Color(0xFF00E5FF)
                        }

                        // Неоновое свечение (Bloom)
                        drawCircle(
                            color = mainColor.copy(alpha = 0.3f),
                            radius = item.radius * 1.6f * (if (item.kind == ItemKind.GOLD) pulseScale else 1f),
                            center = center
                        )

                        // Объемный радиальный градиент
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, mainColor, mainColor.copy(alpha = 0.7f)),
                                center = Offset(item.x - item.radius * 0.3f, item.y - item.radius * 0.3f),
                                radius = item.radius * 1.3f
                            ),
                            radius = item.radius,
                            center = center
                        )

                        // Блик высокой четкости
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = item.radius * 0.28f,
                            center = Offset(item.x - item.radius * 0.35f, item.y - item.radius * 0.35f)
                        )
                    }
                }

                // Верхняя панель HUD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF14142B).copy(alpha = 0.9f))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("СЧЁТ", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("$score", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text("РЕКОРД: $bestScore", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("УРОВЕНЬ $level", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
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
                                Text("⏳ ВРЕМЯ ЗАМЕДЛЕНО", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // Пауза / Экран завершения игры
                if (paused && !gameOver) {
                    OverlayPanel(title = "ПАУЗА", subtitle = "Игра приостановлена") {
                        MenuButton("Продолжить", "▶") { paused = false }
                        MenuButton("Заново", "↻") { startGame() }
                        MenuButton("В меню", "⌂") {
                            paused = false
                            screen = GameScreen.MENU
                        }
                    }
                } else if (gameOver) {
                    OverlayPanel(title = "ИГРА ОКОНЧЕНА", subtitle = "Ваш результат: $score | Рекорд: $bestScore") {
                        MenuButton("Играть снова", "↻") { startGame() }
                        MenuButton("Главное меню", "⌂") { screen = GameScreen.MENU }
                    }
                } else {
                    Button(
                        onClick = { paused = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252545))
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
                    colors = listOf(Color(0xFF04040A), Color(0xFF121226))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF14142B).copy(alpha = 0.92f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color(0xFFFFD700), fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
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
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242442))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C36))
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C36))
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
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF14142B))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}
