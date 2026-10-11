package com.example.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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

    private val defaultModels = listOf(
        CharacterModelInfo("anime_mimi", "Anime Mimi (Classic)", "Pink holographic cyber-maid suit with cat ear headset", "anime_mimi.glb"),
        CharacterModelInfo("cyber_mimi", "Cyberpunk Mimi", "Neon cyan and deep violet futuristic high-tech suit", "cyber_mimi.glb"),
        CharacterModelInfo("maid_mimi", "Maid Mimi", "Cute maid costume with delicate pink frills and ribbon", "maid_mimi.glb"),
        CharacterModelInfo("chibi_mimi", "Chibi Mimi", "Adorable mini mascot style with oversized ribbons and sparkles", "chibi_mimi.glb")
    )

    private val _availableModels = MutableStateFlow<List<CharacterModelInfo>>(defaultModels)
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
                for (file in assetFiles) {
                    if (file.endsWith(".glb", ignoreCase = true)) {
                        val id = file.removeSuffix(".glb")
                        val matching = defaultModels.find { it.id == id }
                        if (matching != null) {
                            list.add(matching)
                        } else {
                            val name = id.replace('_', ' ').replaceFirstChar { it.uppercase() }
                            list.add(CharacterModelInfo(id, name, "Custom 3D Model: $file", file))
                        }
                    }
                }
                if (list.isNotEmpty()) {
                    _availableModels.value = list
                }
            }
        } catch (e: Exception) {
            _availableModels.value = defaultModels
        }
    }

    fun selectModel(modelId: String) {
        prefs.characterModel = modelId
        _selectedModel.value = modelId
    }
}
