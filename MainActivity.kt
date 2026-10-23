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
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen()
                }
            }
        }
    }
}

data class FallingItem(
    val id: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
    val isBomb: Boolean,
    val speed: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen() {
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var isGameOver by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf(listOf<FallingItem>()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // Игровой цикл
        LaunchedEffect(isGameOver) {
            if (!isGameOver) {
                var nextId = 0
                while (!isGameOver) {
                    delay(30L)

                    if (screenWidth > 0f && screenHeight > 0f) {
                        if (Random.nextFloat() < 0.12f) {
                            val isBomb = Random.nextFloat() < 0.25f
                            val radius = 40f
                            val x = Random.nextFloat() * (screenWidth - radius * 2) + radius
                            items = items + FallingItem(
                                id = nextId++,
                                x = x,
                                y = -radius,
                                radius = radius,
                                isBomb = isBomb,
                                speed = Random.nextFloat() * 6f + 6f
                            )
                        }

                        items = items.mapNotNull { item ->
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
        }

        // Игровое поле
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isGameOver) {
                    if (!isGameOver) {
                        detectTapGestures { tapOffset ->
                            val clickedItem = items.find { item ->
                                (tapOffset - Offset(item.x, item.y)).getDistance() <= item.radius + 25f
                            }

                            if (clickedItem != null) {
                                items = items.filter { it.id != clickedItem.id }

                                if (clickedItem.isBomb) {
                                    lives -= 1
                                    if (lives <= 0) {
                                        isGameOver = true
                                    }
                                } else {
                                    score += 1
                                }
                            }
                        }
                    }
                }
        ) {
            items.forEach { item ->
                drawCircle(
                    color = if (item.isBomb) Color(0xFFFF5555) else Color(0xFFFFCC00),
                    radius = item.radius,
                    center = Offset(item.x, item.y)
                )
            }
        }

        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Счет: $score",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Жизни: ${"❤️".repeat(maxOf(0, lives))}",
                fontSize = 20.sp
            )
        }

        // Экран Game Over
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Игра окончена",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ваш результат: $score",
                        color = Color.LightGray,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            score = 0
                            lives = 3
                            items = emptyList()
                            isGameOver = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF50FA7B))
                    ) {
                        Text(text = "Играть снова", color = Color.Black, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
