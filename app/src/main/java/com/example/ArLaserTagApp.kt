package com.example

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.GameUiState
import com.example.viewmodel.GameViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

// Sci-Fi Cyberpunk Color Palette
val ObsidianBackground = Color(0xFF040814)
val LaserCrimson = Color(0xFFFF2A5F)
val NeonCyan = Color(0xFF00F5FF)
val BrightGreen = Color(0xFF10E74C)
val TechPurple = Color(0xFF7A1FA2)
val HologramBlue = Color(0xFF00AAFF)
val CardDarkGray = Color(0xFF0D1326)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ArLaserTagApp(
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Internal navigation state: LOBBY, CALIBRATION, ARENA
    var currentScreen by remember { mutableStateOf("LOBBY") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ObsidianBackground
    ) {
        if (!cameraPermissionState.status.isGranted) {
            CameraPermissionRequestScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        } else {
            AnimatedContent(
                targetState = currentScreen,
                label = "ScreenTransition",
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }
            ) { screen ->
                when (screen) {
                    "LOBBY" -> LobbyScreen(
                        uiState = uiState,
                        onHostLobby = {
                            viewModel.hostLobby()
                            currentScreen = "CALIBRATION"
                        },
                        onJoinLobby = { ip ->
                            viewModel.joinLobby(ip)
                            currentScreen = "CALIBRATION"
                        },
                        onStartSolo = {
                            viewModel.switchTopracticeMode()
                            currentScreen = "CALIBRATION"
                        },
                        localIp = viewModel.getLocalIp()
                    )
                    
                    "CALIBRATION" -> CalibrationScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = {
                            viewModel.switchTopracticeMode()
                            currentScreen = "LOBBY"
                        },
                        onStartGame = {
                            currentScreen = "ARENA"
                        }
                    )

                    "ARENA" -> ArenaScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onExit = {
                            currentScreen = "CALIBRATION"
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(LaserCrimson.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Camera Permission Required",
                tint = LaserCrimson,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Camera Permission Required",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "AR Laser Tag uses your device camera output in real-time to track targets, process colors, and trigger laser beams instantly. Your video never leaves your phone.",
            fontSize = 14.sp,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
                .testTag("request_permission_button")
        ) {
            Text("Authorize Grid Feed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun LobbyScreen(
    uiState: GameUiState,
    onHostLobby: () -> Unit,
    onJoinLobby: (String) -> Unit,
    onStartSolo: () -> Unit,
    localIp: String
) {
    var targetIpInput by remember { mutableStateOf("") }
    
    // Pulse animation for radar
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        
        // App Title Logo with Glow
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "COLOR AR",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 4.sp
            )
            Text(
                text = "LASER TAG",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.25f),
                        radius = size.width / 2,
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(width = 8f)
                    )
                }
            )
            Text(
                text = "SPECTRUM TARGET LOCK SYSTEM",
                fontSize = 11.sp,
                color = Color.Gray,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interlocking Sci-Fi Radar Circle
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.1f * radarPulse),
                        radius = size.minDimension / 2
                    )
                    drawCircle(
                        color = NeonCyan,
                        radius = size.minDimension / 2 * radarPulse,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = LaserCrimson,
                        radius = size.minDimension / 4,
                        style = Stroke(width = 4f)
                    )
                    // Draw Crosshairs
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.5f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.5f),
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = LaserCrimson,
                modifier = Modifier.size(40.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardDarkGray),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "YOUR COORDINATES (LOCAL IP)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Text(
                    text = localIp,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Read this IP to another device to connect over the same Wi-Fi network.",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Action Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Host Game Server
            Button(
                onClick = onHostLobby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("host_lobby_button"),
                colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                    Text("HOST MULTIPLAYER ARENA", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Divider or OR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = Color.DarkGray)
                Text(
                    text = "OR LOCK IP SIGNAL",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Divider(modifier = Modifier.weight(1f), color = Color.DarkGray)
            }

            // Join Section with Target IP Input
            OutlinedTextField(
                value = targetIpInput,
                onValueChange = { targetIpInput = it },
                label = { Text("Enemy Host IP Address") },
                placeholder = { Text("e.g. 192.168.1.5") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_ip_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = CardDarkGray,
                    unfocusedContainerColor = CardDarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { if (targetIpInput.isNotBlank()) onJoinLobby(targetIpInput.trim()) },
                enabled = targetIpInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("join_lobby_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HologramBlue,
                    disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Text("JOIN TARGET MULTIPLAYER", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Practice Mode (No network required)
            TextButton(
                onClick = onStartSolo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("solo_practice_button"),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = BrightGreen)
                    Text("SOLO TRAINING / SIMULATOR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CalibrationScreen(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onStartGame: () -> Unit
) {
    val context = LocalContext.current
    var calibrationMessage by remember { mutableStateOf("Aim crosshair at target and tap caliber button") }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Camera view running in Calibration mode
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            analyzer = viewModel.colorAnalyzer
        )

        // Overlay layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
        ) {
            // Header Stats bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isMultiplayer) "LOBBY RECONNAISSANCE" else "OFFLINE PRACTICE PREP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "SPECTRUM CALIBRATION",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = { viewModel.resetToDefaultRanges() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = LaserCrimson)
                }
            }

            // Connection indicator subbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (uiState.isPeerConnected) BrightGreen.copy(alpha = 0.2f) else TechPurple.copy(alpha = 0.2f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.connectionStatus.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isPeerConnected) BrightGreen else Color.LightGray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center Target Crosshair ROI Marker
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(68.dp)
                    .border(2.dp, NeonCyan, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Crosshair internal ticks
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(LaserCrimson, CircleShape)
                )
                // Small indicator text showing "ROI" (Region of Interest)
                Text(
                    text = "ROI",
                    fontSize = 8.sp,
                    color = NeonCyan,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Real-time HUD info for the center ROI pixel average
            Card(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SAMPLED HUE: ${uiState.currentHue.toInt()}°",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SAT: ${(uiState.currentSat * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "VAL: ${(uiState.currentVal * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1.5f))

            // Bottom Calibration Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = calibrationMessage,
                        fontSize = 13.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3 Calibration Target buttons for clothing parts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Headshot Red
                        Button(
                            onClick = {
                                viewModel.calibrateTarget("HEAD")
                                calibrationMessage = "Locking Red Headshot: H:${uiState.currentHue.toInt()}°"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("calibrate_head_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("LOCK HEAD 🔴", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Chest Green
                        Button(
                            onClick = {
                                viewModel.calibrateTarget("BODY")
                                calibrationMessage = "Locking Green Body: H:${uiState.currentHue.toInt()}°"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("calibrate_body_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrightGreen.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("LOCK BODY 🟢", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        // Limbs Blue
                        Button(
                            onClick = {
                                viewModel.calibrateTarget("LIMBS")
                                calibrationMessage = "Locking Blue Limbs: H:${uiState.currentHue.toInt()}°"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("calibrate_limbs_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = HologramBlue.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("LOCK LIMBS 🔵", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Display active specifications
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpecRow("Headshot Range", uiState.headRangeDesc, LaserCrimson)
                        SpecRow("Chest Match Range", uiState.bodyRangeDesc, BrightGreen)
                        SpecRow("Limbs Match Range", uiState.limbsRangeDesc, HologramBlue)
                    }

                    // Proceed Action
                    Button(
                        onClick = onStartGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("enter_battlefield_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "COMMENCE LASER BATTLE ⚔️", 
                            color = Color.Black, 
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecRow(label: String, value: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(accentColor, CircleShape))
            Text(text = label, fontSize = 10.sp, color = Color.LightGray)
        }
        Text(text = value, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}

@SuppressLint("ModifierParameter")
@Composable
fun ArenaScreen(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onExit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Entire camera field is the background
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            analyzer = viewModel.colorAnalyzer
        )

        // RED DAMAGE FLASH OVERLAY (when player is hit)
        AnimatedVisibility(
            visible = uiState.hitFlashSelf,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LaserCrimson.copy(alpha = 0.45f))
            )
        }

        // CYAN HIT FLASH OVERLAY (when opponent is successfully hit)
        AnimatedVisibility(
            visible = uiState.hitFlashEnemy,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonCyan.copy(alpha = 0.25f))
            )
        }

        // Main overlay containing UI elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
        ) {
            
            // Header stats panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit battle
                IconButton(onClick = onExit) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Exit to prep", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Tactical scorebars
                Column(modifier = Modifier.weight(1f)) {
                    // Own Shield indicator
                    Text(
                        text = "YOUR FIELD REACTOR: ${uiState.selfHealth}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.selfHealth < 30) LaserCrimson else NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { uiState.selfHealth.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (uiState.selfHealth < 30) LaserCrimson else NeonCyan,
                        trackColor = Color.DarkGray
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Enemy indicator
                    Text(
                        text = "OPPONENT SIGNAL: ${uiState.enemyHealth}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LaserCrimson
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { uiState.enemyHealth.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = LaserCrimson,
                        trackColor = Color.DarkGray
                    )
                }
            }

            // Connection notification tag
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.isMultiplayer) "SIGNAL TUNNEL: ONLINE" else "TRAINING MATRIX SIMULATION ENABLED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isMultiplayer) NeonCyan else Color.Yellow
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center target locator (Fixed size Anti-Cheat)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(60.dp)
                    .border(2.dp, if (uiState.isReloading) Color.Gray else NeonCyan, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Tactical target line hashes
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (uiState.isReloading) Color.Gray else LaserCrimson, CircleShape)
                )

                // Miniature Spectrum Analysis Bar overlay inside the center
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        HValueIndicator(color = LaserCrimson, density = uiState.headMatchDensity)
                        HValueIndicator(color = BrightGreen, density = uiState.bodyMatchDensity)
                        HValueIndicator(color = HologramBlue, density = uiState.limbsMatchDensity)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Status flash messages on hit
            AnimatedVisibility(
                visible = uiState.lastHitType != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LaserCrimson.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = uiState.lastHitType ?: "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Weapon Control HUD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Ammo display
                    Column {
                        Text(
                            text = "PLASMA CELLS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${uiState.ammo}",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = if (uiState.ammo <= 3) LaserCrimson else NeonCyan,
                                modifier = Modifier.testTag("ammo_count")
                            )
                            Text(
                                text = "/${uiState.maxAmmo}",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Compact energy tick cells representation
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            for (cell in 1..uiState.maxAmmo) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = 12.dp)
                                        .background(
                                            if (cell <= uiState.ammo) {
                                                if (uiState.ammo <= 3) LaserCrimson else NeonCyan
                                            } else {
                                                Color.DarkGray
                                            }
                                        )
                                )
                            }
                        }
                    }

                    // Tactical Fire & Reload actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reload Action
                        IconButton(
                            onClick = { viewModel.reloadWeapon() },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .testTag("reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Ammo",
                                tint = if (uiState.isReloading) Color.Gray else Color.White
                            )
                        }

                        // TRIGGER / FIRE BUTTON
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(
                                    if (uiState.isReloading || uiState.ammo <= 0) Color.DarkGray else LaserCrimson,
                                    CircleShape
                                )
                                .clickable { viewModel.triggerLaserShot() }
                                .testTag("trigger_fire_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "FIRE",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            )
                            
                            // Glowing border indicating active trigger charge
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 3.dp,
                                        color = if (uiState.isReloading) Color.Transparent else Color.White.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                }
            }

        }

        // Game Over Dialog overlay
        if (uiState.gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(enabled = false) {}, // absorb touch events
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDarkGray),
                    border = BorderStroke(2.dp, NeonCyan)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "BATTLE RESOLVED",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 2.sp
                        )

                        Text(
                            text = uiState.gameMessage ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Final Stats: Enemy hp left: ${uiState.enemyHealth}% | Your hp left: ${uiState.selfHealth}%",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onExit,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("game_over_exit_button")
                            ) {
                                Text("EXIT ARENA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { viewModel.resetGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = LaserCrimson),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("game_over_rematch_button")
                            ) {
                                Text("REDEPLOY BATTLER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun HValueIndicator(color: Color, density: Float) {
    // Indicator tick heights based on match density
    val heightFraction = density.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .size(width = 4.dp, height = 14.dp)
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .background(color)
        )
    }
}

/**
 * Encapsulated CameraX Preview binding helper
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    // Start Binding preview provider
    LaunchedEffect(analyzer) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        if (analyzer != null) {
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
        }

        try {
            cameraProvider.unbindAll()
            if (analyzer != null) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
