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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.exp
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

private enum class GameScreen {
    MENU, GAME, RECORDS, SETTINGS
}

private enum class ItemKind {
    NORMAL, BOMB, GOLD, SLOW
}

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

    val prefs = remember(context) {
        context.getSharedPreferences(
            "catch_game_settings",
            Context.MODE_PRIVATE
        )
    }

    var screen by remember {
        mutableStateOf(GameScreen.MENU)
    }

    var score by remember { mutableIntStateOf(0) }

    var bestScore by remember {
        mutableIntStateOf(prefs.getInt("best_score", 0))
    }

    var lives by remember { mutableIntStateOf(3) }

    var slowSeconds by remember {
        mutableFloatStateOf(0f)
    }

    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    var soundOn by remember {
        mutableStateOf(prefs.getBoolean("sound_on", true))
    }

    var vibrationOn by remember {
        mutableStateOf(prefs.getBoolean("vibration_on", true))
    }

    var items by remember {
        mutableStateOf<List<FallingItem>>(emptyList())
    }

    var nextItemId by remember {
        mutableIntStateOf(0)
    }

    val currentItems by rememberUpdatedState(items)
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
            prefs.edit()
                .putInt("best_score", score)
                .apply()
        }
    }

    fun feedback(isBomb: Boolean = false) {
        if (currentSoundOn) {
            try {
                val tone = ToneGenerator(
                    AudioManager.STREAM_MUSIC,
                    80
                )

                tone.startTone(
                    if (isBomb) {
                        ToneGenerator.TONE_PROP_NACK
                    } else {
                        ToneGenerator.TONE_PROP_BEEP
                    },
                    if (isBomb) 140 else 70
                )

                tone.release()
            } catch (_: Exception) {}
        }

        if (currentVibrationOn) {
            try {
                val vibrator = context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator

                if (vibrator?.hasVibrator() == true) {
                    val duration = if (isBomb) 180L else 35L
                    val amplitude = if (isBomb) 230 else 80

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                duration,
                                amplitude
                            )
                        )
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
        slowSeconds = 0f
        items = emptyList()
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
                MenuButton("Играть", "▶") {
                    startGame()
                }

                MenuButton("Рекорды", "🏆") {
                    screen = GameScreen.RECORDS
                }

                MenuButton("Настройки", "⚙") {
                    screen = GameScreen.SETTINGS
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "✨ Золото: +5 очков | 🔵 Время: замедление",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        GameScreen.RECORDS -> {
            GamePanel(
                title = "РЕКОРДЫ",
                subtitle = "Ваши высшие достижения"
            ) {
                InfoCard(
                    title = "Лучший счёт",
                    value = "$bestScore очков",
                    symbol = "🏆"
                )

                Spacer(Modifier.height(16.dp))

                MenuButton("Назад", "←") {
                    screen = GameScreen.MENU
                }
            }
        }

        GameScreen.SETTINGS -> {
            GamePanel(
                title = "НАСТРОЙКИ",
                subtitle = "Звук и тактильный отклик"
            ) {
                SettingToggle(
                    title = "Звуковые эффекты",
                    checked = soundOn
                ) { enabled ->
                    soundOn = enabled

                    prefs.edit()
                        .putBoolean("sound_on", enabled)
                        .apply()
                }

                SettingToggle(
                    title = "Вибрация",
                    checked = vibrationOn
                ) { enabled ->
                    vibrationOn = enabled

                    prefs.edit()
                        .putBoolean("vibration_on", enabled)
                        .apply()
                }

                Spacer(Modifier.height(16.dp))

                MenuButton("Назад", "←") {
                    screen = GameScreen.MENU
                }
            }
        }

        GameScreen.GAME -> {
            // Используем Box для удержания оверлеев паузы/игры поверх всего
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF05050C),
                                Color(0xFF0F0F1E),
                                Color(0xFF1A1A3A)
                            )
                        )
                    )
            ) {
                // Выстраиваем интерфейс и игровое поле через Column, чтобы они не перекрывали друг друга некорректно
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Верхняя информационная панель (счет, уровень, жизни)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Color(0xFF14142B).copy(alpha = 0.9f)
                                )
                                .padding(
                                    horizontal = 20.dp,
                                    vertical = 14.dp
                                ),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "СЧЁТ",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "$score",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Text(
                                    "РЕКОРД: $bestScore",
                                    color = Color(0xFFFFD700),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    "УРОВЕНЬ $level",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = "❤️".repeat(lives),
                                    fontSize = 18.sp
                                )
                            }
                        }

                        if (slowSeconds > 0f) {
                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "⏳ ВРЕМЯ ЗАМЕДЛЕНО",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Игровое поле под панелью со свободным откликом на касания по всей площади
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val screenWidth = constraints.maxWidth.toFloat()
                        val screenHeight = constraints.maxHeight.toFloat()

                        val infiniteTransition =
                            rememberInfiniteTransition(label = "pulse")

                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 700,
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )

                        LaunchedEffect(
                            screen,
                            paused,
                            gameOver,
                            screenWidth,
                            screenHeight
                        ) {
                            if (
                                screen == GameScreen.GAME &&
                                !paused &&
                                !gameOver &&
                                screenWidth > 0f &&
                                screenHeight > 0f
                            ) {
                                var previousFrameMs =
                                    SystemClock.elapsedRealtime()

                                while (true) {
                                    delay(16L)

                                    if (
                                        currentScreen != GameScreen.GAME ||
                                        currentPaused ||
                                        currentGameOver
                                    ) {
                                        break
                                    }

                                    val nowMs = SystemClock.elapsedRealtime()

                                    val deltaSeconds = (
                                        (nowMs - previousFrameMs) / 1000f
                                    ).coerceIn(0f, 0.05f)

                                    previousFrameMs = nowMs

                                    val frameScale = deltaSeconds / 0.025f

                                    val activeLevel =
                                        currentScore / 10 + 1

                                    val spawnRate = 3.4f *
                                        (1f + (activeLevel - 1) * 0.09f)

                                    val spawnChance = (
                                        1f - exp(-spawnRate * deltaSeconds)
                                    ).coerceIn(0f, 0.24f)

                                    val updatedItems =
                                        currentItems.toMutableList()

                                    if (Random.nextFloat() < spawnChance) {
                                        val radius = 40f

                                        val maxX = (
                                            screenWidth - radius * 2
                                        ).coerceAtLeast(0f)

                                        val roll = Random.nextFloat()

                                        val kind = when {
                                            roll < 0.15f -> ItemKind.BOMB
                                            roll < 0.27f -> ItemKind.GOLD
                                            roll < 0.34f -> ItemKind.SLOW
                                            else -> ItemKind.NORMAL
                                        }

                                        val levelBonus = (
                                            (activeLevel - 1) * 0.3f
                                        ).coerceAtMost(6f)

                                        val speed =
                                            Random.nextFloat() * 2.5f +
                                                3.5f +
                                                levelBonus

                                        updatedItems.add(
                                            FallingItem(
                                                id = nextItemId++,
                                                x = Random.nextFloat() *
                                                    maxX + radius,
                                                y = -radius,
                                                radius = if (
                                                    kind == ItemKind.GOLD
                                                ) 34f else radius,
                                                kind = kind,
                                                speed = speed
                                            )
                                        )
                                    }

                                    val timeScale =
                                        if (currentSlowSeconds > 0f) {
                                            0.38f
                                        } else {
                                            1f
                                        }

                                    items = updatedItems.mapNotNull { item ->
                                        val newY = item.y +
                                            item.speed *
                                            timeScale *
                                            frameScale

                                        if (
                                            newY > screenHeight +
                                            item.radius * 2
                                        ) {
                                            null
                                        } else {
                                            item.copy(y = newY)
                                        }
                                    }

                                    if (currentSlowSeconds > 0f) {
                                        slowSeconds = (
                                            currentSlowSeconds - deltaSeconds
                                        ).coerceAtLeast(0f)
                                    }
                                }
                            }
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { tap ->
                                        if (
                                            currentScreen != GameScreen.GAME ||
                                            currentPaused ||
                                            currentGameOver
                                        ) {
                                            return@detectTapGestures
                                        }

                                        val hit = currentItems
                                            .asReversed()
                                            .firstOrNull { item ->
                                                val dx = tap.x - item.x
                                                val dy = tap.y - item.y
                                                val hitRadius = item.radius + 70f

                                                dx * dx + dy * dy <=
                                                    hitRadius * hitRadius
                                            }

                                        if (hit != null) {
                                            items = currentItems.filterNot {
                                                it.id == hit.id
                                            }

                                            when (hit.kind) {
                                                ItemKind.BOMB -> {
                                                    lives = (
                                                        lives - 1
                                                    ).coerceAtLeast(0)

                                                    feedback(isBomb = true)

                                                    if (lives == 0) {
                                                        gameOver = true
                                                    }
                                                }

                                                ItemKind.GOLD -> {
                                                    score += 5
                                                    feedback()
                                                }

                                                ItemKind.SLOW -> {
                                                    score += 2
                                                    slowSeconds = 3f
                                                    feedback()
                                                }

                                                ItemKind.NORMAL -> {
                                                    score += 1
                                                    feedback()
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            items.forEach { item ->
                                val center = Offset(item.x, item.y)

                                val mainColor = when (item.kind) {
                                    ItemKind.NORMAL -> Color(0xFFFFCC00)
                                    ItemKind.BOMB -> Color(0xFFFF2255)
                                    ItemKind.GOLD -> Color(0xFFFFD700)
                                    ItemKind.SLOW -> Color(0xFF00E5FF)
                                }

                                drawCircle(
                                    color = mainColor.copy(alpha = 0.3f),
                                    radius = item.radius * 1.6f *
                                        (
                                            if (item.kind == ItemKind.GOLD) {
                                                pulseScale
                                            } else {
                                                1f
                                            }
                                        ),
                                    center = center
                                )

                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,
                                            mainColor,
                                            mainColor.copy(alpha = 0.7f)
                                        ),
                                        center = Offset(
                                            item.x - item.radius * 0.3f,
                                            item.y - item.radius * 0.3f
                                        ),
                                        radius = item.radius * 1.3f
                                    ),
                                    radius = item.radius,
                                    center = center
                                )

                                drawCircle(
                                    color = Color.White.copy(alpha = 0.8f),
                                    radius = item.radius * 0.28f,
                                    center = Offset(
                                        item.x - item.radius * 0.35f,
                                        item.y - item.radius * 0.35f
                                    )
                                )
                            }
                        }
                    }
                }

                // Кнопка паузы поверх экрана в правом нижнем углу
                if (!paused && !gameOver) {
                    Button(
                        onClick = {
                            paused = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF252545)
                        )
                    ) {
                        Text(
                            "⏸ Пауза",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Оверлей Паузы
                if (paused && !gameOver) {
                    OverlayPanel(
                        title = "ПАУЗА",
                        subtitle = "Игра приостановлена"
                    ) {
                        MenuButton("Продолжить", "▶") {
                            paused = false
                        }

                        MenuButton("Заново", "↻") {
                            startGame()
                        }

                        MenuButton("В меню", "⌂") {
                            paused = false
                            screen = GameScreen.MENU
                        }
                    }
                }

                // Оверлей Конец игры
                if (gameOver) {
                    OverlayPanel(
                        title = "ИГРА ОКОНЧЕНА",
                        subtitle = "Результат: $score | Рекорд: $bestScore"
                    ) {
                        MenuButton("Играть снова", "↻") {
                            startGame()
                        }

                        MenuButton("Главное меню", "⌂") {
                            screen = GameScreen.MENU
                        }
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
                    colors = listOf(
                        Color(0xFF04040A),
                        Color(0xFF121226)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Color(0xFF14142B).copy(alpha = 0.92f)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = Color(0xFFFFD700),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(24.dp))

            content()
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    symbol: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF242442)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                symbol,
                fontSize = 18.sp,
                color = Color(0xFFFFD700)
            )

            Spacer(Modifier.width(14.dp))

            Text(
                label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    symbol: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C36)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    title,
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                Text(
                    value,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                symbol,
                fontSize = 26.sp
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C36)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFD700)
                )
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
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            content()
        }
    }
}
