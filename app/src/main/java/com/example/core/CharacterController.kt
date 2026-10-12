package com.example.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CharacterModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val fileName: String
)

class CharacterController(
    private val context: Context,
    private val prefs: Prefs
) {

    companion object {
        const val ANIMATED_MODEL_ID = "anime_mimi"
        const val ANIMATED_MODEL_FILE = "anime_mimi.glb"

        /** Main model (replaces the old 45 MB static model) and other models that have a skeleton and face morph targets (VRoid/VRM exports renamed to .glb). */
        val RIGGED_MODEL_IDS: Set<String> = setOf("anime_mimi", "maid_mimi")

        /**
         * Variants that are still 1-triangle placeholder files (about 540 bytes). They are hidden from
         * the model list and, if one is still saved in the settings, the animated model is shown
         * instead. Remove an id from this set when a real model file is added for it.
         */
        val PLACEHOLDER_IDS: Set<String> = setOf("cyber_mimi", "chibi_mimi")

        private const val MIN_REAL_MODEL_BYTES = 4096

        fun isRigged(modelId: String): Boolean = modelId in RIGGED_MODEL_IDS
    }

    private val knownModels = listOf(
        CharacterModelInfo(ANIMATED_MODEL_ID, "Anime Mimi (Classic)", "Rigged model: blinking, lip-sync, expressions and human-like motion", ANIMATED_MODEL_FILE),
        CharacterModelInfo("maid_mimi", "Maid Mimi", "Rigged maid variant with the same animations", "maid_mimi.glb"),
        CharacterModelInfo("cyber_mimi", "Cyberpunk Mimi", "Neon cyan and deep violet futuristic high-tech suit", "cyber_mimi.glb"),
        CharacterModelInfo("chibi_mimi", "Chibi Mimi", "Adorable mini mascot style with oversized ribbons and sparkles", "chibi_mimi.glb")
    )

    // Used only if the assets folder cannot be read.
    private val fallbackModels = knownModels.filter { it.id !in PLACEHOLDER_IDS }

    private val _availableModels = MutableStateFlow<List<CharacterModelInfo>>(fallbackModels)
    val availableModels: StateFlow<List<CharacterModelInfo>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.characterModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    init {
        scanAssetModels()
    }

    private fun scanAssetModels() {
        try {
            val assetFiles = context.assets.list("models")
            if (assetFiles != null && assetFiles.isNotEmpty()) {
                val list = mutableListOf<CharacterModelInfo>()
                for (file in assetFiles.sorted()) {
                    if (!file.endsWith(".glb", ignoreCase = true)) continue
                    // Skip tiny placeholder files: selecting them would show a single triangle.
                    if (!isRealModel(file)) continue
                    val id = file.removeSuffix(".glb")
                    val matching = knownModels.find { it.id == id }
                    if (matching != null) {
                        list.add(matching)
                    } else {
                        val name = id.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        list.add(CharacterModelInfo(id, name, "Custom 3D Model: $file", file))
                    }
                }
                val ordered = list.sortedBy { if (it.id == ANIMATED_MODEL_ID) 0 else 1 }
                if (ordered.isNotEmpty()) {
                    _availableModels.value = ordered
                }
            }
        } catch (e: Exception) {
            _availableModels.value = fallbackModels
        }
    }

    /** A real model is larger than a few KB; the old variant placeholders are about 540 bytes. */
    private fun isRealModel(fileName: String): Boolean {
        return try {
            context.assets.open("models/$fileName").use { stream ->
                val buffer = ByteArray(MIN_REAL_MODEL_BYTES)
                var total = 0
                while (total < MIN_REAL_MODEL_BYTES) {
                    val read = stream.read(buffer, total, MIN_REAL_MODEL_BYTES - total)
                    if (read <= 0) break
                    total += read
                }
                total >= MIN_REAL_MODEL_BYTES
            }
        } catch (e: Exception) {
            true // if the size cannot be checked, keep the model listed
        }
    }

    fun selectModel(modelId: String) {
        prefs.characterModel = modelId
        _selectedModel.value = modelId
    }
}
