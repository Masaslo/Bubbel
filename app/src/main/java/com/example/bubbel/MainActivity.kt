package com.example.bubbel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import com.example.bubbel.audio.AudioRoute
import com.example.bubbel.audio.AudioSessionState
import com.example.bubbel.audio.DefaultAudioSessionController
import com.example.bubbel.presentation.home.ListeningViewModel
import com.example.bubbel.ui.theme.BubbelTheme

class MainActivity : ComponentActivity() {
    private lateinit var listeningViewModel: ListeningViewModel
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listeningViewModel.start() else listeningViewModel.onPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listeningViewModel = ViewModelProvider(
            this,
            viewModelFactory {
                initializer { ListeningViewModel(DefaultAudioSessionController(applicationContext)) }
            }
        )[ListeningViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            BubbelTheme {
                BubbelHomeScreen(
                    listeningViewModel = listeningViewModel,
                    onStartRequested = ::startAfterMicrophonePermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sessionIsInFlight = listeningViewModel.state.value is AudioSessionState.Starting ||
            listeningViewModel.state.value is AudioSessionState.Running ||
            listeningViewModel.state.value is AudioSessionState.Recovering
        if (sessionIsInFlight &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            listeningViewModel.onMicrophonePermissionRevoked()
        }
    }

    private fun startAfterMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listeningViewModel.start()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun BubbelHomeScreen(
    listeningViewModel: ListeningViewModel,
    onStartRequested: () -> Unit,
) {
    val state by listeningViewModel.state.collectAsState()
    val permissionDenied by listeningViewModel.permissionDenied.collectAsState()
    val uiState = listeningViewModel.uiState
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var muteSounds by rememberSaveable { mutableStateOf(true) }
    var haptics by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SoundField(
            fieldWidth = maxWidth,
            fieldHeight = maxHeight
        )
        BubbleToggle(
            isActive = uiState.isActive,
            description = bubbleDescription(state, permissionDenied, uiState.route, uiState.failureDescription),
            onToggle = {
                if (state is AudioSessionState.Starting || state is AudioSessionState.Running || state is AudioSessionState.Recovering) {
                    listeningViewModel.stop()
                } else {
                    onStartRequested()
                }
            },
            modifier = Modifier.align(Alignment.Center)
        )
        uiState.route?.let { RouteStatus(route = it, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 16.dp, start = 20.dp)) }
        SettingsButton(
            onClick = { settingsOpen = !settingsOpen },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
        )
        SettingsDropdown(
            visible = settingsOpen,
            muteSounds = muteSounds,
            onMuteSoundsChanged = { muteSounds = it },
            haptics = haptics,
            onHapticsChanged = { haptics = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 92.dp, end = 20.dp)
        )
    }
}

private fun bubbleDescription(
    state: AudioSessionState,
    permissionDenied: Boolean,
    route: AudioRoute?,
    failureDescription: String?,
): String = when {
    permissionDenied -> "Luistermodus mislukt: microfoonmachtiging geweigerd"
    state is AudioSessionState.Running -> "Luistermodus aan via ${route?.label ?: state.route.label}"
    state is AudioSessionState.Starting -> "Luistermodus starten"
    state is AudioSessionState.Recovering -> "Luistermodus herstellen"
    failureDescription != null -> "Luistermodus mislukt: $failureDescription"
    else -> "Luistermodus uit"
}

@Composable
private fun RouteStatus(route: AudioRoute, modifier: Modifier = Modifier) {
    val warning = route.warning
    IconButton(
        onClick = {},
        enabled = false,
        modifier = modifier
            .size(64.dp)
            .background(
                if (warning == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                RoundedCornerShape(22.dp)
            )
            .semantics {
                contentDescription = if (warning == null) {
                    "Audioroute: ${route.label}"
                } else {
                    "Audioroute: ${route.label}. Waarschuwing: $warning"
                }
            }
            .testTag("audio_route_status")
    ) {
        Icon(
            imageVector = if (warning == null) Icons.Outlined.GraphicEq else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = if (warning == null) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Instellingen" }
            .testTag("settings_button")
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun SettingsDropdown(
    visible: Boolean,
    muteSounds: Boolean,
    onMuteSoundsChanged: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(196.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsSwitch(
                icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null) },
                description = "Geluiden dempen",
                checked = muteSounds,
                onCheckedChange = onMuteSoundsChanged
            )
            SettingsSwitch(
                icon = { Icon(Icons.Outlined.Vibration, null) },
                description = "Trillingen",
                checked = haptics,
                onCheckedChange = onHapticsChanged
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: @Composable () -> Unit,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { icon() }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .scale(1.18f)
                .semantics { contentDescription = description },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun BubbleToggle(
    isActive: Boolean,
    description: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = isActive, label = "bubble state")
    val activation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 760, easing = FastOutSlowInEasing) },
        label = "radial fill"
    ) { active -> if (active) 1f else 0f }
    val faceRelaxation by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(durationMillis = 620, delayMillis = 420, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 420, easing = FastOutSlowInEasing)
            }
        },
        label = "face relaxation"
    ) { active -> if (active) 1f else 0f }
    val breathing = rememberInfiniteTransition(label = "bubble breath").animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring breath"
    ).value
    val colors = MaterialTheme.colorScheme
    val visuals = bubbleAnimationVisuals(activation)
    val interactionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(264.dp)
            .semantics { contentDescription = description }
            .testTag("bubble_toggle")
            .selectable(
                selected = isActive,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.33f
        val ringRadius = radius * (1.29f + (breathing - 1f) * activation)
        val ringOuterRadius = ringRadius + radius * 0.105f / 2f
        drawCircle(
            color = colors.background,
            radius = soundMaskRadius(radius, ringOuterRadius, activation),
            center = centre
        )
        if (visuals.bubbleSweepDegrees > 0.1f) {
            drawArc(
                color = colors.secondary,
                startAngle = -90f,
                sweepAngle = visuals.bubbleSweepDegrees,
                useCenter = false,
                topLeft = Offset(centre.x - ringRadius, centre.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = radius * 0.105f, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = lerpColor(colors.error, colors.primary, visuals.emojiColorProgress),
            radius = radius,
            center = centre
        )
        drawFace(centre, radius, faceRelaxation, colors.onBackground)
    }
}

private fun DrawScope.drawFace(centre: Offset, radius: Float, relaxed: Float, color: Color) {
    val geometry = faceGeometry(relaxed)
    val stroke = radius * 0.09f
    val browY = centre.y - radius * 0.52f
    val eyeY = centre.y - radius * 0.05f
    val eyeOffset = radius * 0.38f
    val browLift = radius * 0.15f * relaxed
    drawArc(
        color = color,
        startAngle = 26f,
        sweepAngle = 128f,
        useCenter = false,
        topLeft = Offset(centre.x - eyeOffset - radius * 0.22f, browY - browLift),
        size = Size(radius * 0.44f, radius * 0.28f),
        style = Stroke(stroke, cap = StrokeCap.Round)
    )
    drawArc(
        color = color,
        startAngle = 26f,
        sweepAngle = 128f,
        useCenter = false,
        topLeft = Offset(centre.x + eyeOffset - radius * 0.22f, browY - browLift),
        size = Size(radius * 0.44f, radius * 0.28f),
        style = Stroke(stroke, cap = StrokeCap.Round)
    )
    val leftEye = Path().apply {
        moveTo(centre.x - eyeOffset - radius * 0.18f, eyeY)
        quadraticTo(centre.x - eyeOffset, eyeY + radius * geometry.eyeBend, centre.x - eyeOffset + radius * 0.18f, eyeY)
    }
    val rightEye = Path().apply {
        moveTo(centre.x + eyeOffset - radius * 0.18f, eyeY)
        quadraticTo(centre.x + eyeOffset, eyeY + radius * geometry.eyeBend, centre.x + eyeOffset + radius * 0.18f, eyeY)
    }
    drawPath(leftEye, color, style = Stroke(stroke, cap = StrokeCap.Round))
    drawPath(rightEye, color, style = Stroke(stroke, cap = StrokeCap.Round))
    val mouthY = centre.y + radius * 0.31f
    val mouth = Path().apply {
        moveTo(centre.x - radius * 0.31f, mouthY)
        quadraticTo(centre.x, mouthY + radius * geometry.mouthBend, centre.x + radius * 0.31f, mouthY)
    }
    drawPath(mouth, color, style = Stroke(stroke, cap = StrokeCap.Round))
}

internal data class FaceGeometry(val eyeBend: Float, val mouthBend: Float)

internal fun faceGeometry(relaxed: Float): FaceGeometry {
    val progress = relaxed.coerceIn(0f, 1f)
    return FaceGeometry(
        eyeBend = -0.22f + progress * 0.52f,
        mouthBend = -0.24f + progress * 0.54f
    )
}

internal data class BubbleAnimationVisuals(
    val emojiColorProgress: Float,
    val bubbleSweepDegrees: Float
)

internal fun bubbleAnimationVisuals(progress: Float): BubbleAnimationVisuals {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    return BubbleAnimationVisuals(
        emojiColorProgress = normalizedProgress,
        bubbleSweepDegrees = normalizedProgress * 360f
    )
}

internal fun soundMaskRadius(
    emojiRadius: Float,
    bubbleOuterRadius: Float,
    progress: Float
): Float {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    return emojiRadius + (bubbleOuterRadius - emojiRadius) * normalizedProgress
}

@Composable
private fun SoundField(fieldWidth: Dp, fieldHeight: Dp) {
    val symbols = listOf(Icons.Outlined.MusicNote, Icons.AutoMirrored.Outlined.VolumeUp, Icons.Outlined.GraphicEq)
    val startingPoints = soundStartingPoints()
    startingPoints.forEachIndexed { index, point ->
        SoundParticle(
            icon = symbols[index % symbols.size],
            startX = fieldWidth * point.first,
            startY = fieldHeight * point.second,
            targetX = fieldWidth * 0.5f,
            targetY = fieldHeight * 0.5f,
            delayMillis = index * 390
        )
    }
}

@Composable
private fun SoundParticle(
    icon: ImageVector,
    startX: Dp,
    startY: Dp,
    targetX: Dp,
    targetY: Dp,
    delayMillis: Int
) {
    val movement = rememberInfiniteTransition(label = "sound movement").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300, delayMillis, FastOutSlowInEasing), RepeatMode.Restart),
        label = "sound particle"
    ).value
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .offset(lerp(startX, targetX, movement), lerp(startY, targetY, movement))
            .size(22.dp)
    )
}

internal fun soundStartingPoints(): List<Pair<Float, Float>> = listOf(
    Pair(-0.08f, 0.22f),
    Pair(1.08f, 0.34f),
    Pair(-0.08f, 0.70f),
    Pair(1.08f, 0.80f),
    Pair(0.43f, -0.06f),
    Pair(0.60f, 1.06f)
)
