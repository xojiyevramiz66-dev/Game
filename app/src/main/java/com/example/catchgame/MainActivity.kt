package com.example.catchgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
                    color = Color(0xFF1E1E2E)
                ) {
                    GameScreen()
                }
            }
        }
    }
}

data class FallingItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val isBomb: Boolean,
    val speed: Float
)

@Composable
fun GameScreen() {
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var isGameOver by remember { mutableStateOf(false) }
    var items by remember {
        mutableStateOf<List<FallingItem>>(emptyList())
    }

    // Важно: обработчик касаний всегда получает свежий список.
    val currentItems by rememberUpdatedState(items)
    val currentGameOver by rememberUpdatedState(isGameOver)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // Создание и движение падающих предметов.
        LaunchedEffect(isGameOver, screenWidth, screenHeight) {
            if (!isGameOver && screenWidth > 0f && screenHeight > 0f) {
                var nextId = 0

                while (true) {
                    delay(40L)

                    if (isGameOver) break

                    val updatedItems = items.toMutableList()

                    if (Random.nextFloat() < 0.08f) {
                        val radius = 38f
                        val maxX = (screenWidth - radius * 2)
                            .coerceAtLeast(0f)

                        updatedItems.add(
                            FallingItem(
                                id = nextId++,
                                x = Random.nextFloat() * maxX + radius,
                                y = -radius,
                                radius = radius,
                                isBomb = Random.nextFloat() < 0.25f,
                                speed = Random.nextFloat() * 3f + 4f
                            )
                        )
                    }

                    items = updatedItems.mapNotNull { item ->
                        val newY = item.y + item.speed

                        if (newY > screenHeight + item.radius) {
                            null
                        } else {
                            item.copy(y = newY)
                        }
                    }
                }
            }
        }

        // Игровое поле и обработка касаний.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        if (currentGameOver) {
                            return@detectTapGestures
                        }

                        val snapshot = currentItems

                        // Проверяем сначала верхний нарисованный предмет.
                        val clickedItem = snapshot
                            .asReversed()
                            .firstOrNull { item ->
                                val dx = tapOffset.x - item.x
                                val dy = tapOffset.y - item.y
                                val hitRadius = item.radius + 24f

                                dx * dx + dy * dy <=
                                    hitRadius * hitRadius
                            }

                        if (clickedItem != null) {
                            // Удаляем именно тот предмет, по которому попали.
                            items = currentItems.filterNot {
                                it.id == clickedItem.id
                            }

                            if (clickedItem.isBomb) {
                                lives = (lives - 1).coerceAtLeast(0)

                                if (lives == 0) {
                                    isGameOver = true
                                }
                            } else {
                                score += 1
                            }
                        }
                    }
                }
        ) {
            items.forEach { item ->
                drawCircle(
                    color = if (item.isBomb) {
                        Color(0xFFFF5555)
                    } else {
                        Color(0xFFFFCC00)
                    },
                    radius = item.radius,
                    center = Offset(item.x, item.y)
                )
            }
        }

        // Счёт и жизни.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Счёт: $score",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Жизни: ${"❤️".repeat(lives)}",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        // Экран окончания игры.
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Игра окончена",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Ваш результат: $score",
                        color = Color.LightGray,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            score = 0
                            lives = 3
                            items = emptyList()
                            isGameOver = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF50FA7B)
                        )
                    ) {
                        Text(
                            text = "Играть снова",
                            color = Color.Black,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
