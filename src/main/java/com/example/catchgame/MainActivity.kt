
package com.example.catchgame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private data class FallingItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val radius: Float,
    val isBomb: Boolean,
    val speed: Float
)

@Composable
private fun GameScreen() {
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var gameOver by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(emptyList<FallingItem>()) }
    var nextId by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        LaunchedEffect(gameOver, screenWidth, screenHeight) {
            if (!gameOver && screenWidth > 80f && screenHeight > 0f) {
                while (true) {
                    delay(30L)

                    val spawned = if (Random.nextFloat() < 0.12f) {
                        val radius = 40f
                        val maxX = (screenWidth - radius * 2)
                            .coerceAtLeast(1f)

                        listOf(
                            FallingItem(
                                id = nextId++,
                                x = Random.nextFloat() * maxX + radius,
                                y = -radius,
                                radius = radius,
                                isBomb = Random.nextFloat() < 0.25f,
                                speed = Random.nextFloat() * 6f + 6f
                            )
                        )
                    } else {
                        emptyList()
                    }

                    items = (items + spawned).mapNotNull { item ->
                        val moved = item.copy(y = item.y + item.speed)

                        if (moved.y > screenHeight + moved.radius) {
                            null
                        } else {
                            moved
                        }
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameOver, items) {
                    if (!gameOver) {
                        detectTapGestures { tap ->
                            val hit = items.lastOrNull { item ->
                                (tap - Offset(item.x, item.y))
                                    .getDistance() <= item.radius + 25f
                            }

                            if (hit != null) {
                                items = items.filterNot {
                                    it.id == hit.id
                                }

                                if (hit.isBomb) {
                                    lives = (lives - 1).coerceAtLeast(0)

                                    if (lives == 0) {
                                        gameOver = true
                                    }
                                } else {
                                    score++
                                }
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

                if (item.isBomb) {
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = Offset(
                            item.x - 10f,
                            item.y - 10f
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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

        if (gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
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
                        text = "Твой результат: $score",
                        color = Color.LightGray,
                        fontSize = 21.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            score = 0
                            lives = 3
                            items = emptyList()
                            nextId = 0
                            gameOver = false
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
