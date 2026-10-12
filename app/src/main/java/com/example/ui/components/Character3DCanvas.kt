package com.example.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CharacterController
import io.github.sceneview.Scene
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "Character3DCanvas"

// Camera and framing. If the character looks too big/small or is cut off, tune these 3 numbers.
private const val CAMERA_Z = 3.5f
private const val RIGGED_SCALE = 2.1f      // model metres -> scene units
private const val RIGGED_FOCUS_Y = 1.12f   // model height (m) that is placed at the screen centre (chest)

// VRM 0.x models face -Z, so they are turned 180 degrees to face the camera.
// If the character shows her back, change this to 0f.
private const val RIGGED_YAW_DEG = 180f

/**
 * Renders the selected GLB with Filament through SceneView.
 *
 * Rigged VRM models (see [CharacterController.RIGGED_MODEL_IDS]) get human-like motion: breathing,
 * weight shift, eye/head gaze, blinking, lip-sync, mood expressions and a tap reaction. Static models
 * (no skeleton) get a basic whole-body idle motion.
 */
@Composable
fun Character3DCanvas(
    modelId: String,
    mood: String,
    mouthOpenLevel: Float,
    isSpeaking: Boolean,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = CAMERA_Z)
    }

    val modelFile = remember(modelId) { resolveModelFile(context, modelId) }
    val rigged = CharacterController.isRigged(modelFile.removeSuffix(".glb"))
    val modelPath = "models/$modelFile"
    val modelInstance = rememberModelInstance(modelLoader, modelPath)

    // The model can take a few seconds to load (large file); only report failure after a timeout.
    var loadTimedOut by remember(modelPath) { mutableStateOf(false) }
    LaunchedEffect(modelPath) {
        delay(20_000)
        loadTimedOut = true
    }

    // The node is created only once the model instance exists (the old code built the node list
    // before the model finished loading, so the model could stay invisible).
    val modelNode = remember(modelInstance, rigged) {
        modelInstance?.let { instance ->
            if (rigged) {
                ModelNode(modelInstance = instance).apply {
                    scale = Position(RIGGED_SCALE, RIGGED_SCALE, RIGGED_SCALE)
                    position = Position(0f, -RIGGED_FOCUS_Y * RIGGED_SCALE, 0f)
                    rotation = Position(0f, RIGGED_YAW_DEG, 0f)
                }
            } else {
                ModelNode(modelInstance = instance, scaleToUnits = 1.8f)
            }
        }
    }
    val childNodes = remember(modelNode) { listOfNotNull<Node>(modelNode) }

    val rig = remember(modelInstance, rigged) {
        if (rigged && modelInstance != null) VrmRig.create(engine, modelInstance) else null
    }
    val rigStatus = when {
        !rigged -> "static model - basic idle motion"
        rig == null -> "rig unavailable - basic idle motion"
        else -> rig.status
    }

    val currentMood by rememberUpdatedState(mood)
    val currentMouth by rememberUpdatedState(mouthOpenLevel)
    val currentSpeaking by rememberUpdatedState(isSpeaking)
    val tapAction by rememberUpdatedState(onAvatarTap)
    var tapCount by remember { mutableStateOf(0) }

    // Animation loop. Declared after the engine/model so it is cancelled before they are destroyed.
    LaunchedEffect(modelNode, rig) {
        val node = modelNode ?: return@LaunchedEffect
        val director = HumanMotion()
        val basePosition = node.position
        val baseRotation = node.rotation
        val baseScale = node.scale
        var lastFrame = withFrameNanos { it }
        var seenTaps = tapCount
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrame = now
            val tapped = tapCount != seenTaps
            seenTaps = tapCount
            val pose = director.update(dt, currentMood, currentSpeaking, currentMouth, tapped)
            try {
                if (rig != null) {
                    rig.applyPose(pose)
                } else {
                    applyBasicIdle(node, pose, basePosition, baseRotation, baseScale)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Animation frame failed: ${t.message}")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF34213A), Color(0xFF111018), Color(0xFF09090D))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Tap on the character: watched in the Initial pass so SceneView's own
                    // drag / pinch camera gestures keep working. Drags are ignored via touch slop.
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null && (up.position - down.position).getDistance() <= slop) {
                            tapCount++
                            tapAction()
                        }
                    }
                }
        ) {
            if (modelInstance != null) {
                Scene(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    modelLoader = modelLoader,
                    cameraNode = cameraNode,
                    childNodes = childNodes
                )
            } else {
                Text(
                    text = if (loadTimedOut) {
                        "3D model could not be loaded\n($modelPath)"
                    } else {
                        "Loading 3D model..."
                    },
                    color = Color(0xFFFFB4D2),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Text(
            text = when (mood) {
                "happy" -> "♡ Mimi is happy"
                "shy" -> "Mimi is feeling shy"
                "sad" -> "Mimi is feeling sad"
                "angry" -> "Mimi is upset"
                else -> "Mimi is here"
            } + if (isSpeaking) "  ·  Speaking" else "",
            color = Color(0xFFFFD7E8),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .clickable {
                    tapCount++
                    tapAction()
                }
                .clip(RoundedCornerShape(50))
                .background(Color(0xAA17111D))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )

        // Temporary diagnostic line so problems can be reported from a screenshot.
        Text(
            text = rigStatus,
            color = Color(0x88FFD7E8),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 52.dp)
        )
    }
}

/**
 * Chooses which packaged model file to load. Falls back to a model that really exists so the screen
 * never ends up empty: requested file -> anime_mimi.glb (main model).
 */
private fun resolveModelFile(context: Context, modelId: String): String {
    val available: Set<String> = try {
        context.assets.list("models")?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }
    val wanted = if (modelId.isBlank() || modelId in CharacterController.PLACEHOLDER_IDS) {
        CharacterController.ANIMATED_MODEL_ID
    } else {
        modelId
    }
    val wantedFile = "$wanted.glb"
    return when {
        wantedFile in available -> wantedFile
        CharacterController.ANIMATED_MODEL_FILE in available -> CharacterController.ANIMATED_MODEL_FILE
        else -> wantedFile
    }
}

/** Whole-body idle for static models that have no skeleton (no blinking or lip movement possible). */
private fun applyBasicIdle(
    node: ModelNode,
    pose: HumanMotion.Pose,
    basePosition: Position,
    baseRotation: Position,
    baseScale: Position
) {
    val breath = 1f + 0.008f * pose.breath
    node.position = Position(basePosition.x, basePosition.y + pose.hipsLift * 1.5f, basePosition.z)
    node.rotation = Position(
        baseRotation.x + pose.headPitch * 0.3f,
        baseRotation.y + pose.hipsYaw + pose.headYaw * 0.5f,
        baseRotation.z + pose.hipsRoll + pose.headRoll * 0.4f
    )
    node.scale = Position(baseScale.x * breath, baseScale.y * breath, baseScale.z * breath)
}

/**
 * SceneView 2.2.1 does not provide a rememberModelInstance Compose helper.
 * Load through its ModelLoader API and expose the result as Compose state.
 * The owning rememberModelLoader manages the loaded model's lifetime.
 */
@Composable
private fun rememberModelInstance(
    modelLoader: ModelLoader,
    modelPath: String
): ModelInstance? {
    var instance by remember(modelLoader, modelPath) {
        mutableStateOf<ModelInstance?>(null)
    }

    LaunchedEffect(modelLoader, modelPath) {
        try {
            instance = modelLoader.loadModelInstance(
                fileLocation = modelPath
            )

            if (instance == null) {
                Log.e(TAG, "Model loader returned null: $modelPath")
            }
        } catch (e: CancellationException) {
            // Preserve structured cancellation when the model changes or the screen closes.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelPath", e)
        }
    }

    return instance
}
