package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GameServer
import com.example.utils.AudioHapticHelper
import com.example.utils.ColorAnalyzer
import com.example.utils.HsvRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class GameUiState(
    val isMultiplayer: Boolean = false,
    val isHost: Boolean = true,
    val connectionStatus: String = "Offline (Practice)",
    val isPeerConnected: Boolean = false,
    val peerIp: String? = null,
    val selfHealth: Int = 100,
    val enemyHealth: Int = 100,
    val ammo: Int = 12,
    val maxAmmo: Int = 12,
    val isReloading: Boolean = false,
    
    // Real-time camera analyzer inputs
    val currentHue: Float = 0f,
    val currentSat: Float = 0f,
    val currentVal: Float = 0f,
    
    // Calculated match densities (0.0 to 1.0)
    val headMatchDensity: Float = 0f,
    val bodyMatchDensity: Float = 0f,
    val limbsMatchDensity: Float = 0f,

    // Active Range parameters for display
    val headRangeDesc: String = "Red (~340°-20°)",
    val bodyRangeDesc: String = "Green (~80°-150°)",
    val limbsRangeDesc: String = "Blue (~185°-255°)",

    // Hit event flag (to trigger flash animations)
    val hitFlashSelf: Boolean = false,
    val hitFlashEnemy: Boolean = false,
    val lastHitType: String? = null,
    val gameOver: Boolean = false,
    val gameMessage: String? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val audioHapticHelper = AudioHapticHelper(application)
    
    // Set up our direct color analyzer
    val colorAnalyzer = ColorAnalyzer(
        onColorSampled = { h, s, v ->
            _uiState.update { it.copy(currentHue = h, currentSat = s, currentVal = v) }
        },
        onAnalysisCompleted = { h, s, v, densityHead, densityBody, densityLimbs ->
            _uiState.update { 
                it.copy(
                    headMatchDensity = densityHead,
                    bodyMatchDensity = densityBody,
                    limbsMatchDensity = densityLimbs
                )
            }
        }
    )

    private var gameServer: GameServer? = null

    init {
        // Initialize Game Server
        gameServer = GameServer(
            onConnectionStatusChanged = { connected, extra ->
                _uiState.update { state ->
                    state.copy(
                        isPeerConnected = connected,
                        connectionStatus = if (connected) "CONNECTED TO $extra" else (extra ?: "Disconnected"),
                        peerIp = extra
                    )
                }
                if (connected) {
                    resetGame()
                }
            },
            onMessageReceived = { msg ->
                handleIncomingMessage(msg)
            }
        )
    }

    /**
     * Start socket server to wait for a player.
     */
    fun hostLobby() {
        _uiState.update { it.copy(isMultiplayer = true, isHost = true) }
        gameServer?.startHosting()
    }

    /**
     * Connect to host's coordinate.
     */
    fun joinLobby(ip: String) {
        _uiState.update { it.copy(isMultiplayer = true, isHost = false) }
        gameServer?.connectToHost(ip)
    }

    /**
     * Switch back to Solo Practice mode.
     */
    fun switchTopracticeMode() {
        gameServer?.stopAll()
        _uiState.update { 
            it.copy(
                isMultiplayer = false, 
                isPeerConnected = false, 
                connectionStatus = "Offline (Practice)"
            ) 
        }
        resetGame()
    }

    /**
     * Get Device local Wi-Fi IP address
     */
    fun getLocalIp(): String {
        return gameServer?.getLocalIpAddress(getApplication()) ?: "127.0.0.1"
    }

    /**
     * Perform Color Calibration in the setup screen.
     * Takes the current HSV under the crosshair and builds a matching range.
     */
    fun calibrateTarget(part: String) {
        val centerHue = _uiState.value.currentHue
        val centerSat = _uiState.value.currentSat
        val centerVal = _uiState.value.currentVal

        // Build ranges with 25° tolerance for Hue, 0.25 margin for saturation/brightness
        val hueMin = (centerHue - 25f + 360f) % 360f
        val hueMax = (centerHue + 25f) % 360f
        
        when (part) {
            "HEAD" -> {
                colorAnalyzer.headRange = HsvRange(
                    "Calibrated Head (Red)", 
                    hueMin, hueMax, 
                    satMin = (centerSat - 0.25f).coerceAtLeast(0.2f),
                    valMin = (centerVal - 0.25f).coerceAtLeast(0.2f),
                    damage = 100
                )
                _uiState.update { 
                    it.copy(headRangeDesc = "Custom (H:${centerHue.toInt()}° S:${(centerSat*100).toInt()}% V:${(centerVal*100).toInt()}%)")
                }
            }
            "BODY" -> {
                colorAnalyzer.bodyRange = HsvRange(
                    "Calibrated Body (Green)", 
                    hueMin, hueMax, 
                    satMin = (centerSat - 0.25f).coerceAtLeast(0.2f),
                    valMin = (centerVal - 0.25f).coerceAtLeast(0.2f),
                    damage = 20
                )
                _uiState.update { 
                    it.copy(bodyRangeDesc = "Custom (H:${centerHue.toInt()}° S:${(centerSat*100).toInt()}% V:${(centerVal*100).toInt()}%)")
                }
            }
            "LIMBS" -> {
                colorAnalyzer.limbsRange = HsvRange(
                    "Calibrated Limbs (Blue)", 
                    hueMin, hueMax, 
                    satMin = (centerSat - 0.25f).coerceAtLeast(0.2f),
                    valMin = (centerVal - 0.25f).coerceAtLeast(0.2f),
                    damage = 10
                )
                _uiState.update { 
                    it.copy(limbsRangeDesc = "Custom (H:${centerHue.toInt()}° S:${(centerSat*100).toInt()}% V:${(centerVal*100).toInt()}%)")
                }
            }
        }
        audioHapticHelper.vibrate(80)
    }

    /**
     * Resets Calibration to Default RGB Ranges
     */
    fun resetToDefaultRanges() {
        colorAnalyzer.headRange = HsvRange("Headshot (Red)", 340f, 20f, damage = 100)
        colorAnalyzer.bodyRange = HsvRange("Body (Green)", 80f, 150f, damage = 20)
        colorAnalyzer.limbsRange = HsvRange("Limbs (Blue)", 185f, 255f, damage = 10)
        _uiState.update { 
            it.copy(
                headRangeDesc = "Red (~340°-20°)",
                bodyRangeDesc = "Green (~80°-150°)",
                limbsRangeDesc = "Blue (~185°-255°)"
            )
        }
        audioHapticHelper.vibrate(50)
    }

    /**
     * Player triggers core shot.
     */
    fun triggerLaserShot() {
        val state = _uiState.value
        
        if (state.gameOver) return

        if (state.isReloading) {
            audioHapticHelper.playNoAmmoSound()
            return
        }

        if (state.ammo <= 0) {
            audioHapticHelper.playNoAmmoSound()
            // Auto reload
            reloadWeapon()
            return
        }

        // Fire Weapon
        _uiState.update { it.copy(ammo = state.ammo - 1) }
        audioHapticHelper.playLaserSound()
        audioHapticHelper.vibrate(60) // physical shot impact

        // Analyze Hit Match percentage under crosshair
        // Match density above 35% is considered a hit
        val isHeadshot = state.headMatchDensity >= 0.35f
        val isBodyShot = state.bodyMatchDensity >= 0.35f
        val isLimbShot = state.limbsMatchDensity >= 0.35f

        if (isHeadshot) {
            registerSuccessfulHit(100, "HEADSHOT")
        } else if (isBodyShot) {
            registerSuccessfulHit(20, "BODYSHOT")
        } else if (isLimbShot) {
            registerSuccessfulHit(10, "LIMBSHOT")
        }
    }

    private fun registerSuccessfulHit(damage: Int, hitName: String) {
        audioHapticHelper.playHitSound()
        audioHapticHelper.vibrate(150) // double haptic force

        // Trigger flash
        _uiState.update { it.copy(hitFlashEnemy = true, lastHitType = "$hitName! -$damage% HP") }
        
        viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(hitFlashEnemy = false) }
        }

        if (_uiState.value.isMultiplayer && _uiState.value.isPeerConnected) {
            // Tell the client/host they got hit
            gameServer?.sendMessage("DAMAGE:$damage")
        } else {
            // Solo Simulated mode - decrease fake opponent health
            val newEnemyHp = (_uiState.value.enemyHealth - damage).coerceAtLeast(0)
            _uiState.update { it.copy(enemyHealth = newEnemyHp) }
            
            if (newEnemyHp <= 0) {
                _uiState.update { 
                    it.copy(
                        gameOver = true, 
                        gameMessage = "VICTORY! Targets neutralized."
                    ) 
                }
            }
        }
    }

    /**
     * Start weapon reload.
     */
    fun reloadWeapon() {
        if (_uiState.value.isReloading || _uiState.value.ammo == _uiState.value.maxAmmo) return
        
        _uiState.update { it.copy(isReloading = true) }
        audioHapticHelper.playReloadSound()
        
        viewModelScope.launch {
            delay(1500) // 1.5 seconds reload duration
            _uiState.update { it.copy(ammo = it.maxAmmo, isReloading = false) }
        }
    }

    /**
     * Incoming commands over the local network lobby.
     */
    private fun handleIncomingMessage(msg: String) {
        Log.d("Lobby", "Processing TCP: $msg")
        when {
            msg.startsWith("DAMAGE:") -> {
                val damage = msg.substringAfter("DAMAGE:").toIntOrNull() ?: 0
                val newSelfHp = (_uiState.value.selfHealth - damage).coerceAtLeast(0)
                
                audioHapticHelper.vibrate(350).also {
                    // Double warning feedback when sustaining severe damage
                    if (damage == 100) {
                        viewModelScope.launch {
                            delay(100)
                            audioHapticHelper.vibrate(350)
                        }
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        selfHealth = newSelfHp, 
                        hitFlashSelf = true
                    ) 
                }
                
                viewModelScope.launch {
                    delay(450)
                    _uiState.update { it.copy(hitFlashSelf = false) }
                }

                // Send back consolidated health status update
                gameServer?.sendMessage("HEALTH:$newSelfHp")

                if (newSelfHp <= 0) {
                    _uiState.update { 
                        it.copy(
                            gameOver = true, 
                            gameMessage = "DEFEATED. Reactor core failure."
                        ) 
                    }
                    // Inform the peer
                    gameServer?.sendMessage("PEER_WON")
                }
            }
            msg.startsWith("HEALTH:") -> {
                val pHealth = msg.substringAfter("HEALTH:").toIntOrNull() ?: 100
                _uiState.update { it.copy(enemyHealth = pHealth) }
            }
            msg == "PEER_WON" -> {
                _uiState.update { 
                    it.copy(
                        gameOver = true, 
                        gameMessage = "VICTORY! Target neutralized."
                    ) 
                }
            }
            msg == "WELCOME_HOST" -> {
                // Sent to sync starting states
                _uiState.update { it.copy(connectionStatus = "CONNECTED TO HOST") }
                resetGame()
            }
        }
    }

    /**
     * Reset health and states for next round.
     */
    fun resetGame() {
        _uiState.update { 
            it.copy(
                selfHealth = 100,
                enemyHealth = 100,
                ammo = 12,
                isReloading = false,
                gameOver = false,
                gameMessage = null,
                lastHitType = null
            ) 
        }
        // Tell connected peer we reset
        if (_uiState.value.isMultiplayer && _uiState.value.isPeerConnected) {
            gameServer?.sendMessage("HEALTH:100")
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameServer?.stopAll()
    }
}
