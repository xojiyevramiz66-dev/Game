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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
                    color = Color(0xFF151522)
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

    var screen by remember { mutableStateOf(GameScreen.MENU) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember {
        mutableIntStateOf(prefs.getInt("best_score", 0))
    }
    var bestCombo by remember {
        mutableIntStateOf(prefs.getInt("best_combo", 0))
    }

    var lives by remember { mutableIntStateOf(3) }
    var combo by remember { mutableIntStateOf(0) }
    var slowTicks by remember { mutableIntStateOf(0) }

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
    var nextItemId by remember { mutableIntStateOf(0) }

    // Актуальные значения для обработчика нажатий и игрового цикла.
    val currentItems by rememberUpdatedState(items)
    val currentScreen by rememberUpdatedState(screen)
    val currentPaused by rememberUpdatedState(paused)
    val currentGameOver by rememberUpdatedState(gameOver)
    val currentScore by rememberUpdatedState(score)
    val currentSlowTicks by rememberUpdatedState(slowTicks)
    val currentSoundOn by rememberUpdatedState(soundOn)
    val currentVibrationOn by rememberUpdatedState(vibrationOn)

    val level = score / 10 + 1

    // Сохранение рекорда между запусками приложения.
    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", score).apply()
        }
    }

    LaunchedEffect(bestCombo) {
        prefs.edit().putInt("best_combo", bestCombo).apply()
    }

    // Звук и вибрация при попадании или нажатии на бомбу.
    fun feedback(isBomb: Boolean = false) {
        if (currentSoundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
                tone.startTone(
                    if (isBomb) ToneGenerator.TONE_PROP_NACK
                    else ToneGenerator.TONE_PROP_BEEP,
                    100
                )
                tone.release()
            } catch (_: Exception) {
                // Игра продолжает работать, если звук недоступен.
            }
        }

        if (currentVibrationOn) {
            try {
                val vibrator = context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as Vibrator

                if (vibrator.hasVibrator()) {
                    val duration = if (isBomb) 180L else 45L

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                duration,
                                if (isBomb) 180 else 70
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(duration)
                    }
                }
            } catch (_: Exception) {
                // Вибрация необязательна для игрового процесса.
            }
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
                title = "CATCH GAME",
                subtitle = "Лови круги. Избегай бомб!"
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

                Spacer(Modifier.height(12.dp))

                Text(
                    "🟡 Монеты дают больше очков",
                    color = Color(0xFFFFD54F),
                    fontSize = 15.sp
                )

                Text(
                    "🔵 Синий круг замедляет время",
                    color = Color(0xFF40C4FF),
                    fontSize = 15.sp
                )
            }
        }

        GameScreen.RECORDS -> {
            GamePanel(
                title = "РЕКОРДЫ",
                subtitle = "Результаты на этом устройстве"
            ) {
                InfoCard(
                    title = "Лучший счёт",
                    value = "$bestScore очков",
                    symbol = "🏆"
                )

                InfoCard(
                    title = "Лучшее комбо",
                    value = "$bestCombo попаданий",
                    symbol = "🔥"
                )

                Spacer(Modifier.height(12.dp))

                MenuButton("Назад", "←") {
                    screen = GameScreen.MENU
                }
            }
        }

        GameScreen.SETTINGS -> {
            GamePanel(
                title = "НАСТРОЙКИ",
                subtitle = "Настрой игру под себя"
            ) {
                SettingToggle(
                    title = "Звуковые эффекты",
                    checked = soundOn
                ) { enabled ->
                    soundOn = enabled
                    prefs.edit().putBoolean("sound_on", enabled).apply()
                }

                SettingToggle(
                    title = "Вибрация",
                    checked = vibrationOn
                ) { enabled ->
                    vibrationOn = enabled
                    prefs.edit().putBoolean("vibration_on", enabled).apply()
                }

                Spacer(Modifier.height(12.dp))

                MenuButton("Назад", "←") {
                    screen = GameScreen.MENU
                }
            }
        }

        GameScreen.GAME -> {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF151522))
            ) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                // Игровой цикл. Уровень влияет на скорость и частоту предметов.
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
                        while (true) {
                            delay(40L)

                            if (
                                currentScreen != GameScreen.GAME ||
                                currentPaused ||
                                currentGameOver
                            ) {
                                break
                            }

                            val activeLevel = currentScore / 10 + 1

                            val spawnChance = (
                                0.075f + activeLevel * 0.008f
                            ).coerceAtMost(0.24f)

                            val updated = currentItems.toMutableList()

                            if (Random.nextFloat() < spawnChance) {
                                val radius = 34f
                                val maxX = (
                                    screenWidth - radius * 2
                                ).coerceAtLeast(0f)

                                val roll = Random.nextFloat()

                                val kind = when {
                                    roll < 0.17f -> ItemKind.BOMB
                                    roll < 0.29f -> ItemKind.GOLD
                                    roll < 0.36f -> ItemKind.SLOW
                                    else -> ItemKind.NORMAL
                                }

                                val levelBonus = (
                                    (activeLevel - 1) * 0.4f
                                ).coerceAtMost(8f)

                                val speed =
                                    Random.nextFloat() * 2.5f +
                                    3.5f +
                                    levelBonus

                                updated.add(
                                    FallingItem(
                                        id = nextItemId++,
                                        x = Random.nextFloat() * maxX + radius,
                                        y = -radius,
                                        radius = if (kind == ItemKind.GOLD) {
                                            29f
                                        } else {
                                            radius
                                        },
                                        kind = kind,
                                        speed = speed
                                    )
                                )
                            }

                            // Синий бонус временно замедляет все предметы.
                            val timeScale = if (currentSlowTicks > 0) {
                                0.42f
                            } else {
                                1f
                            }

                            items = updated.mapNotNull { item ->
                                val newY = item.y + item.speed * timeScale

                                if (newY > screenHeight + item.radius) {
                                    null
                                } else {
                                    item.copy(y = newY)
                                }
                            }

                            if (currentSlowTicks > 0) {
                                slowTicks = (
                                    currentSlowTicks - 1
                                ).coerceAtLeast(0)
                            }
                        }
                    }
                }

                // Игровое поле с актуальным списком предметов.
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
                                        val radius = item.radius + 23f

                                        dx * dx + dy * dy <= radius * radius
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

                                            combo = 0
                                            feedback(isBomb = true)

                                            if (lives == 0) {
                                                gameOver = true
                                            }
                                        }

                                        ItemKind.GOLD -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)

                                            // Золотой предмет даёт 5 очков
                                            // и дополнительный бонус за комбо.
                                            score += 5 + combo / 5
                                            feedback()
                                        }

                                        ItemKind.SLOW -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)
                                            score += 2

                                            // Примерно 4 секунды замедления.
                                            slowTicks = 100
                                            feedback()
                                        }

                                        ItemKind.NORMAL -> {
                                            combo += 1
                                            bestCombo = maxOf(bestCombo, combo)

                                            // Каждое пятое попадание подряд
                                            // приносит дополнительные очки.
                                            score += if (combo % 5 == 0) {
                                                4
                                            } else {
                                                1
                                            }

                                            feedback()
                                        }
                                    }
                                } else {
                                    // Промах сбрасывает серию попаданий.
                                    combo = 0
                                }
                            }
                        }
                ) {
                    items.forEach { item ->
                        val color = when (item.kind) {
                            ItemKind.NORMAL -> Color(0xFFFFCC00)
                            ItemKind.BOMB -> Color(0xFFFF5252)
                            ItemKind.GOLD -> Color(0xFFFFD700)
                            ItemKind.SLOW -> Color(0xFF40C4FF)
                        }

                        drawCircle(
                            color = color,
                            radius = item.radius,
                            center = Offset(item.x, item.y)
                        )

                        if (item.kind == ItemKind.GOLD) {
                            drawCircle(
                                color = Color(0xFFFFF3B0),
                                radius = item.radius * 0.45f,
                                center = Offset(item.x, item.y)
                            )
                        }

                        if (item.kind == ItemKind.SLOW) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = item.radius * 0.3f,
                                center = Offset(item.x, item.y)
                            )
                        }
                    }
                }

                // Счёт, рекорд, уровень и комбо.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Счёт: $score",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Text(
                                "Рекорд: $bestScore",
                                color = Color(0xFFFFD54F),
                                fontSize = 14.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "Уровень $level",
                                color = Color(0xFF80CBC4),
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "Жизни: " + "❤️".repeat(lives),
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = if (combo >= 2) {
                            "Комбо: x$combo"
                        } else {
                            "Комбо: —"
                        },
                        color = Color(0xFFFFAB40),
                        fontWeight = FontWeight.Bold
                    )

                    if (slowTicks > 0) {
                        Text(
                            "⏳ Время замедлено!",
                            color = Color(0xFF40C4FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Пауза и окончание игры.
                if (paused && !gameOver) {
                    OverlayPanel(
                        title = "ПАУЗА",
                        subtitle = "Игра приостановлена"
                    ) {
                        MenuButton("Продолжить", "▶") {
                            paused = false
                        }

                        MenuButton("Начать заново", "↻") {
                            startGame()
                        }

                        MenuButton("В меню", "⌂") {
                            paused = false
                            screen = GameScreen.MENU
                        }
                    }
                } else if (gameOver) {
                    OverlayPanel(
                        title = "ИГРА ОКОНЧЕНА",
                        subtitle = "Счёт: $score • Рекорд: $bestScore"
                    ) {
                        Text(
                            "Уровень: $level • Комбо: $bestCombo",
                            color = Color.LightGray
                        )

                        MenuButton("Играть снова", "↻") {
                            startGame()
                        }

                        MenuButton("В меню", "⌂") {
                            screen = GameScreen.MENU
                        }
                    }
                } else {
                    Button(
                        onClick = { paused = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF383850)
                        )
                    ) {
                        Text("Ⅱ Пауза", color = Color.White)
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
            .background(Color(0xFF151522))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                color = Color(0xFFFFD54F),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                subtitle,
                color = Color.LightGray,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(28.dp))

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
            .padding(vertical = 5.dp)
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF34344D)
        )
    ) {
        Text(
            "$symbol   $label",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF29293D)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    title,
                    color = Color.LightGray,
                    fontSize = 14.sp
                )

                Text(
                    value,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(symbol, fontSize = 28.sp)
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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF29293D)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 17.sp
            )

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
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
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                subtitle,
                color = Color.LightGray,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(20.dp))

            content()
        }
    }
}
