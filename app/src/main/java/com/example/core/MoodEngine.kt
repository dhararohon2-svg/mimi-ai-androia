package com.example.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class CharacterState(
    val name: String,
    val mood: String, // "happy", "sad", "angry", "shy", "neutral"
    val affection: Int, // 0 - 100
    val modelType: String
)

class MoodEngine(private val prefs: Prefs) {

    private val _characterState = MutableStateFlow(
        CharacterState(
            name = prefs.characterName,
            mood = prefs.currentMood,
            affection = prefs.affectionScore,
            modelType = prefs.characterModel
        )
    )
    val characterState: StateFlow<CharacterState> = _characterState.asStateFlow()

    private val tapDialoguesBengali = listOf(
        "হিহি! আমাকে বারবার ছুঁলে লজ্জা লাগে তো~",
        "হেই! আপনি আমার সবচেয়ে প্রিয় বন্ধু!",
        "কি ব্যাপার? কোনো সাহায্য লাগবে নাকি শুধু আদর করতে এলেন?",
        "মিমি সবসময় আপনার কথা শুনতে প্রস্তুত!",
        "আরে! একটু সাবধানে, আমি একটা কিউট রোবট মেয়ে কিন্তু!"
    )

    private val tapDialoguesEnglish = listOf(
        "Hehe! You're poking me~ That tickles!",
        "I love hanging out with you, Master!",
        "Need anything done, or just saying hello?",
        "Mimi is always ready at your command!",
        "Ehehe, my affection for you just increased!"
    )

    fun onAvatarTapped(): String {
        val newAffection = (prefs.affectionScore + 2).coerceAtMost(100)
        prefs.affectionScore = newAffection
        val newMood = if (newAffection > 70) "shy" else "happy"
        setMood(newMood)

        val isBengali = prefs.voiceLanguage.startsWith("bn")
        val dialogues = if (isBengali) tapDialoguesBengali else tapDialoguesEnglish
        return dialogues.random()
    }

    fun setMood(mood: String) {
        val validMood = when (mood.lowercase()) {
            "happy", "sad", "angry", "shy", "neutral", "thinking" -> mood.lowercase()
            else -> "neutral"
        }
        prefs.currentMood = validMood
        updateState()
    }

    fun addAffection(amount: Int) {
        val newScore = (prefs.affectionScore + amount).coerceIn(0, 100)
        prefs.affectionScore = newScore
        updateState()
    }

    fun updateModel(model: String) {
        prefs.characterModel = model
        updateState()
    }

    fun updateName(name: String) {
        prefs.characterName = name
        updateState()
    }

    fun getTimeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isBengali = prefs.voiceLanguage.startsWith("bn")
        return when {
            hour in 5..11 -> if (isBengali) "শুভ সকাল! আজ আপনাকে কীভাবে সাহায্য করতে পারি?" else "Good morning! How can I assist you today?"
            hour in 12..16 -> if (isBengali) "শুভ দুপুর! আপনার ফোনের সব কাজ কি ঠিকঠাক চলছে?" else "Good afternoon! Everything running smoothly on your device?"
            hour in 17..21 -> if (isBengali) "শুভ সন্ধ্যা! দিনটি কেমন কাটল আপনার?" else "Good evening! How was your day?"
            else -> if (isBengali) "দেরি হয়ে গেছে! কোনো দরকার থাকলে বলুন, আমি জেগে আছি।" else "It's late at night! Let me know if you need anything before sleep."
        }
    }

    private fun updateState() {
        _characterState.value = CharacterState(
            name = prefs.characterName,
            mood = prefs.currentMood,
            affection = prefs.affectionScore,
            modelType = prefs.characterModel
        )
    }
}
