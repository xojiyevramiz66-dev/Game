package com.example.catchgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.exp
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(5, 5, 15)
        window.navigationBarColor = AndroidColor.rgb(5, 5, 15)
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
    var x: Float,
    var y: Float,
    val radius: Float,
    val kind: ItemKind,
    val speed: Float
)

@Composable
private fun CatchGameApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("catch_game_settings", Context.MODE_PRIVATE)
    }

    var screen by remember { mutableStateOf(GameScreen.MENU) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(prefs.getInt("best_score", 0)) }
    var lives by remember { mutableIntStateOf(3) }
    var slowSeconds by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound_on", true)) }
    var vibrationOn by remember { mutableStateOf(prefs.getBoolean("vibration_on", true)) }

    val level = score / 10 + 1

    LaunchedEffect(score) {
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", score).apply()
        }
    }

    fun feedback(isBomb: Boolean = false) {
        if (soundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tone.startTone(
                    if (isBomb) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP,
                    if (isBomb) 140 else 70
                )
                tone.release()
            } catch (_: Exception) { }
        }
        if (vibrationOn) {
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
                AndroidView(
                    factory = { ctx ->
                        GameSurfaceView(ctx).apply {
                            onScoreChanged = { pts -> score += pts }
                            onLifeLost = {
                                lives = (lives - 1).coerceAtLeast(0)
                                if (lives == 0) gameOver = true
                            }
                            onSlowTriggered = { slowSeconds = 3f }
                            onFeedback = { isBomb -> feedback(isBomb) }
                            getLevel = { score / 10 + 1 }
                            isPaused = { paused }
                            isGameOver = { gameOver } // Исправлено: убран аргумент
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

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

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private var thread: Thread? = null
    private var running = false

    private val items = mutableListOf<FallingItem>()
    private var nextItemId = 0
    private var lastTimeNanos = System.nanoTime()

    var onScoreChanged: (Int) -> Unit = {}
    var onLifeLost: () -> Unit = {}
    var onSlowTriggered: () -> Unit = {}
    var onFeedback: (Boolean) -> Unit = {}
    var getLevel: () -> Int = { 1 }
    var isPaused: () -> Boolean = { false }
    var isGameOver: () -> Boolean = { false }

    private val paint = Paint().apply { isAntiAlias = true }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this)
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        var retry = true
        while (retry) {
            try {
                thread?.join()
                retry = false
            } catch (_: InterruptedException) { }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isPaused() || isGameOver()) return true

            val tapX = event.x
            val tapY = event.y

            synchronized(items) {
                val hit = items.asReversed().firstOrNull { item ->
                    val dx = tapX - item.x
                    val dy = tapY - item.y
                    val hitRadius = item.radius + 75f
                    dx * dx + dy * dy <= hitRadius * hitRadius
                }

                if (hit != null) {
                    items.remove(hit)
                    when (hit.kind) {
                        ItemKind.BOMB -> {
                            onLifeLost()
                            onFeedback(true)
                        }
                        ItemKind.GOLD -> {
                            onScoreChanged(5)
                            onFeedback(false)
                        }
                        ItemKind.SLOW -> {
                            onScoreChanged(2)
                            onSlowTriggered()
                            onFeedback(false)
                        }
                        ItemKind.NORMAL -> {
                            onScoreChanged(1)
                            onFeedback(false)
                        }
                    }
                }
            }
        }
        return true
    }

    override fun run() {
        while (running) {
            val nowNanos = System.nanoTime()
            val deltaSeconds = ((nowNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastTimeNanos = nowNanos

            if (!isPaused() && !isGameOver() && width > 0 && height > 0) {
                val level = getLevel()
                val spawnRate = 2.4f * (1f + (level - 1) * 0.05f)
                val spawnChance = if (items.size < 30) {
                    (1f - exp(-spawnRate * deltaSeconds)).coerceIn(0f, 0.16f)
                } else {
                    0f
                }

                synchronized(items) {
                    if (Random.nextFloat() < spawnChance) {
                        val radius = 40f
                        val maxX = (width - radius * 2).coerceAtLeast(0f)
                        val roll = Random.nextFloat()
                        val kind = when {
                            roll < 0.15f -> ItemKind.BOMB
                            roll < 0.27f -> ItemKind.GOLD
                            roll < 0.34f -> ItemKind.SLOW
                            else -> ItemKind.NORMAL
                        }
                        val levelBonus = ((level - 1) * 0.25f).coerceAtMost(4f)
                        val speed = Random.nextFloat() * 2.0f + 3.0f + levelBonus

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

                    val iterator = items.listIterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        val newY = item.y + item.speed * (deltaSeconds / 0.025f)
                        if (newY > height + item.radius * 2) {
                            iterator.remove()
                        } else {
                            item.y = newY
                        }
                    }
                }
            }

            val canvas = holder.lockCanvas()
            if (canvas != null) {
                canvas.drawColor(AndroidColor.rgb(3, 3, 11))

                synchronized(items) {
                    items.forEach { item ->
                        val color = when (item.kind) {
                            ItemKind.NORMAL -> AndroidColor.rgb(255, 201, 40)
                            ItemKind.BOMB -> AndroidColor.rgb(255, 49, 90)
                            ItemKind.GOLD -> AndroidColor.rgb(255, 215, 0)
                            ItemKind.SLOW -> AndroidColor.rgb(0, 217, 255)
                        }

                        paint.shader = RadialGradient(
                            item.x - item.radius * 0.34f, item.y - item.radius * 0.38f,
                            item.radius * 1.48f,
                            intArrayOf(AndroidColor.WHITE, color, color, AndroidColor.rgb(40, 40, 80)),
                            floatArrayOf(0f, 0.3f, 0.7f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawCircle(item.x, item.y, item.radius, paint)
                        paint.shader = null

                        paint.color = color
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.7f
                        canvas.drawCircle(item.x, item.y, item.radius, paint)
                        paint.style = Paint.Style.FILL

                        paint.color = AndroidColor.argb(220, 255, 255, 255)
                        canvas.drawCircle(item.x - item.radius * 0.34f, item.y - item.radius * 0.38f, item.radius * 0.20f, paint)
                    }
                }

                holder.unlockCanvasAndPost(canvas)
            }

            try {
                Thread.sleep(14L)
            } catch (_: InterruptedException) { }
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
