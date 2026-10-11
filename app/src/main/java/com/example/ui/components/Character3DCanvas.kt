package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

/** Renders the selected GLB with Filament through SceneView. The provided model has no rig or
 * animation clips, so facial animation/lip-sync must remain a visual fallback until an animated
 * model is supplied. */
@Composable
fun Character3DCanvas(
    modelId: String,
    mood: String,
    mouthOpenLevel: Float,
    isSpeaking: Boolean,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 3.5f)
    }
    val modelPath = remember(modelId) {
        // Keep model IDs mapped to local packaged assets. The supplied full model replaces the
        // former placeholder anime_mimi.glb; alternate tiny placeholders remain selectable.
        "models/${if (modelId.isBlank()) "anime_mimi" else modelId}.glb"
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
        // key(modelPath): মডেল বদলালে নতুন করে লোড হবে এবং নোড লিস্টও নতুন হবে।
        key(modelPath) {
            // SceneView 2.2.1 এ rememberModelInstance নেই (ওটা 3.x এর API)।
            // এখানে modelLoader.createModelInstance() দিয়ে সরাসরি লোড করা হয়।
            val modelInstance = remember {
                try {
                    modelLoader.createModelInstance(modelPath)
                } catch (e: Exception) {
                    null
                }
            }
            val childNodes = rememberNodes {
                if (modelInstance != null) {
                    add(ModelNode(modelInstance = modelInstance, scaleToUnits = 1.8f))
                }
            }

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
                    text = "3D model could not be loaded",
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
                .clickable(onClick = onAvatarTap)
                .clip(RoundedCornerShape(50))
                .background(Color(0xAA17111D))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )

    }
}
