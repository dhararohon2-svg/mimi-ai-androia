package com.example.ui.components

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates natural, non-repeating "human" motion for the character.
 *
 * It does not touch any 3D API. Every frame [update] returns a [Pose] (angles in degrees, morph
 * weights 0..1) that [VrmRig] applies to the skeleton / face morph targets of the rigged model, or
 * that a plain static model uses as a simple whole-body idle.
 *
 * Angle conventions (model space, the VRM model faces -Z):
 *  - pitch: positive = look up / lean back, negative = look down / lean forward
 *  - yaw:   positive = turn toward the character's left
 *  - roll:  positive = tilt toward the character's left
 */
class HumanMotion(seed: Long = System.nanoTime()) {

    class Pose {
        // body (degrees; hipsLift in metres)
        var hipsYaw = 0f
        var hipsRoll = 0f
        var hipsLift = 0f
        var spinePitch = 0f
        var spineYaw = 0f
        var chestPitch = 0f
        var breath = 0f          // -1..1 breathing cycle (used by the static-model fallback)
        var armSway = 0f

        // head / neck / eyes (degrees)
        var neckYaw = 0f
        var neckPitch = 0f
        var neckRoll = 0f
        var headYaw = 0f
        var headPitch = 0f
        var headRoll = 0f
        var eyeYaw = 0f
        var eyePitch = 0f

        // face morph weights 0..1
        var blink = 0f
        var vowelA = 0f
        var vowelI = 0f
        var vowelU = 0f
        var vowelE = 0f
        var vowelO = 0f
        var joy = 0f
        var playful = 0f
        var sorrow = 0f
        var angry = 0f
        var surprised = 0f
    }

    private val rnd = Random(seed)
    private val pose = Pose()

    private var t = 0.0

    // smoothed mood channels
    private var mJoy = 0f
    private var mPlayful = 0f
    private var mSorrow = 0f
    private var mAngry = 0f
    private var mHeadPitch = 0f
    private var mHeadYaw = 0f
    private var mHeadRoll = 0f
    private var mChest = 0f
    private var mEyePitch = 0f
    private var breathPeriod = 4.2f
    private var breathPhase = 0.0

    // gaze
    private var gazeTimer = 0.8f
    private var gazeTargetYaw = 0f
    private var gazeTargetPitch = 0f
    private var gazeYaw = 0f
    private var gazePitch = 0f
    private var headFollowYaw = 0f
    private var headFollowPitch = 0f
    private var lookAwayTimer = 0f
    private var lookAwayYaw = 0f

    // blink
    private var blinkTimer = 1.5f
    private var blinkPhase = -1f
    private var justDoubled = false

    // speech
    private var mouthSmooth = 0f
    private var speakGain = 0f
    private var vowel = 0
    private var vowelTimer = 0f
    private val vowelWeights = FloatArray(5)

    // tap reaction
    private var tapTimer = 0f
    private var surpriseSmooth = 0f
    private var tapJoySmooth = 0f
    private var joltPitch = 0f
    private var joltYaw = 0f

    fun update(dt: Float, mood: String, speaking: Boolean, mouthLevel: Float, tapped: Boolean): Pose {
        t += dt.toDouble()

        // ---------- mood targets ----------
        var tJoy = 0f
        var tPlayful = 0f
        var tSorrow = 0f
        var tAngry = 0f
        var tHeadPitch = 0f
        var tHeadYaw = 0f
        var tHeadRoll = 0f
        var tChest = 0f
        var tEyePitch = 0f
        var tBreathPeriod = 4.2f
        var gazeRange = 1f
        when (mood) {
            "happy" -> {
                tJoy = 0.55f
                tHeadRoll = 3f
                tChest = 0.8f
            }
            "shy" -> {
                tPlayful = 0.45f
                tHeadPitch = -5f
                tHeadYaw = 9f
                tHeadRoll = -4f
                tEyePitch = -3f
                tChest = -1.5f
                tBreathPeriod = 3.6f
            }
            "sad" -> {
                tSorrow = 0.75f
                tHeadPitch = -7f
                tChest = -2.5f
                tEyePitch = -4f
                tBreathPeriod = 5.4f
                gazeRange = 0.5f
            }
            "angry" -> {
                tAngry = 0.7f
                tHeadPitch = 2f
                tChest = 1.5f
                tBreathPeriod = 3.0f
                gazeRange = 0.4f
            }
            else -> {
            }
        }

        mJoy = approach(mJoy, tJoy, MOOD_RATE, dt)
        mPlayful = approach(mPlayful, tPlayful, MOOD_RATE, dt)
        mSorrow = approach(mSorrow, tSorrow, MOOD_RATE, dt)
        mAngry = approach(mAngry, tAngry, MOOD_RATE, dt)
        mHeadPitch = approach(mHeadPitch, tHeadPitch, MOOD_RATE, dt)
        mHeadYaw = approach(mHeadYaw, tHeadYaw, MOOD_RATE, dt)
        mHeadRoll = approach(mHeadRoll, tHeadRoll, MOOD_RATE, dt)
        mChest = approach(mChest, tChest, MOOD_RATE, dt)
        mEyePitch = approach(mEyePitch, tEyePitch, MOOD_RATE, dt)
        breathPeriod = approach(breathPeriod, tBreathPeriod, 1.5f, dt)

        // ---------- breathing ----------
        breathPhase += dt * TWO_PI / breathPeriod
        if (breathPhase > TWO_PI) breathPhase -= TWO_PI
        val breath = sin(breathPhase).toFloat()

        // ---------- slow weight shift ----------
        val shift = wave(9.3)
        val shift2 = wave(14.1, 1.3)

        // ---------- tap reaction ----------
        if (tapped) {
            tapTimer = 1.8f
            joltPitch = -3.5f
            joltYaw = (rnd.nextFloat() - 0.5f) * 10f
        }
        if (tapTimer > 0f) tapTimer -= dt
        val surpriseTarget = if (tapTimer > 1.45f) 0.85f else 0f
        val tapJoyTarget = if (tapTimer in 0.01f..1.45f) 0.6f else 0f
        surpriseSmooth = approach(surpriseSmooth, surpriseTarget, 14f, dt)
        tapJoySmooth = approach(tapJoySmooth, tapJoyTarget, 5f, dt)
        joltPitch = approach(joltPitch, 0f, 6f, dt)
        joltYaw = approach(joltYaw, 0f, 6f, dt)

        // ---------- gaze: eyes lead, the head follows slowly ----------
        gazeTimer -= dt
        if (gazeTimer <= 0f) {
            val range = gazeRange * (if (speaking) 0.5f else 1f)
            gazeTargetYaw = (rnd.nextFloat() * 2f - 1f) * 7f * range
            gazeTargetPitch = (rnd.nextFloat() * 2f - 1f) * 4f * range - 0.5f
            gazeTimer = 1.1f + rnd.nextFloat() * 2.6f
            if (!speaking && rnd.nextFloat() < 0.18f) {
                lookAwayTimer = 1.4f + rnd.nextFloat() * 1.4f
                lookAwayYaw = (if (rnd.nextBoolean()) 1f else -1f) * (8f + rnd.nextFloat() * 8f)
            }
        }
        if (lookAwayTimer > 0f) lookAwayTimer -= dt
        val awayTarget = if (lookAwayTimer > 0f) lookAwayYaw else 0f
        gazeYaw = approach(gazeYaw, gazeTargetYaw, 22f, dt)
        gazePitch = approach(gazePitch, gazeTargetPitch, 22f, dt)
        headFollowYaw = approach(headFollowYaw, gazeTargetYaw * 0.45f + awayTarget, 3f, dt)
        headFollowPitch = approach(headFollowPitch, gazeTargetPitch * 0.35f, 3f, dt)

        // ---------- speech ----------
        val mouthTarget = if (speaking) mouthLevel.coerceIn(0f, 1f) else 0f
        val mouthRate = if (mouthTarget > mouthSmooth) 28f else 14f
        mouthSmooth = approach(mouthSmooth, mouthTarget, mouthRate, dt)
        speakGain = approach(speakGain, if (speaking) 1f else 0f, 4f, dt)

        vowelTimer -= dt
        if (vowelTimer <= 0f) {
            val r = rnd.nextFloat()
            vowel = when {
                r < 0.38f -> 0 // a
                r < 0.54f -> 4 // o
                r < 0.70f -> 3 // e
                r < 0.85f -> 1 // i
                else -> 2      // u
            }
            vowelTimer = 0.07f + rnd.nextFloat() * 0.09f
        }
        for (i in 0 until 5) {
            val target = if (speaking && i == vowel) mouthSmooth * 0.85f else 0f
            vowelWeights[i] = approach(vowelWeights[i], target, 30f, dt)
        }

        val nod = wave(1.0 / 1.7) * 1.3f * speakGain - mouthSmooth * 2f * speakGain
        val speakSway = wave(3.1) * 2.2f * speakGain

        // ---------- blink ----------
        val joyTotal = (mJoy + tapJoySmooth).coerceIn(0f, 1f)
        val blinkShape = updateBlink(dt)
        val blinkWeight = blinkShape * blinkShape * (3f - 2f * blinkShape) * (1f - 0.5f * joyTotal)

        // ---------- compose the final pose ----------
        val headYawTotal = headFollowYaw + mHeadYaw + speakSway + joltYaw
        val headPitchTotal = headFollowPitch + mHeadPitch + nod + joltPitch
        val headRollTotal = mHeadRoll + wave(7.7) * 1.2f

        val p = pose
        p.hipsYaw = 1.4f * shift2
        p.hipsRoll = 1.1f * shift
        p.hipsLift = 0.003f * breath
        p.spinePitch = 0.5f * breath + mChest * 0.4f
        p.spineYaw = -p.hipsYaw * 0.6f + headYawTotal * 0.12f
        p.chestPitch = 1.2f * breath + mChest * 0.6f
        p.breath = breath
        p.armSway = 0.8f * sin(breathPhase + 0.6).toFloat()

        p.neckYaw = headYawTotal * 0.4f
        p.headYaw = headYawTotal * 0.6f
        p.neckPitch = headPitchTotal * 0.4f
        p.headPitch = headPitchTotal * 0.6f
        p.neckRoll = headRollTotal * 0.4f
        p.headRoll = headRollTotal * 0.6f
        p.eyeYaw = gazeYaw * 0.8f + awayTarget * 0.3f
        p.eyePitch = gazePitch + mEyePitch

        val expressionDamp = 1f - 0.45f * speakGain
        p.blink = blinkWeight
        p.vowelA = vowelWeights[0]
        p.vowelI = vowelWeights[1]
        p.vowelU = vowelWeights[2]
        p.vowelE = vowelWeights[3]
        p.vowelO = vowelWeights[4]
        p.joy = joyTotal * expressionDamp
        p.playful = mPlayful * expressionDamp
        p.sorrow = mSorrow * expressionDamp
        p.angry = mAngry * expressionDamp
        p.surprised = surpriseSmooth
        return p
    }

    /** One blink = quick close, short hold, slower open. 15% of blinks are a double blink. */
    private fun updateBlink(dt: Float): Float {
        if (blinkPhase < 0f) {
            blinkTimer -= dt
            if (blinkTimer > 0f) return 0f
            blinkPhase = 0f
        }
        blinkPhase += dt
        val value: Float
        if (blinkPhase < BLINK_CLOSE) {
            value = blinkPhase / BLINK_CLOSE
        } else if (blinkPhase < BLINK_CLOSE + BLINK_HOLD) {
            value = 1f
        } else if (blinkPhase < BLINK_CLOSE + BLINK_HOLD + BLINK_OPEN) {
            value = 1f - (blinkPhase - BLINK_CLOSE - BLINK_HOLD) / BLINK_OPEN
        } else {
            value = 0f
            blinkPhase = -1f
            if (!justDoubled && rnd.nextFloat() < 0.15f) {
                justDoubled = true
                blinkTimer = 0.10f
            } else {
                justDoubled = false
                blinkTimer = 2.2f + rnd.nextFloat() * 3.6f
            }
        }
        return value.coerceIn(0f, 1f)
    }

    private fun wave(period: Double, phase: Double = 0.0): Float =
        sin(TWO_PI * ((t % period) / period) + phase).toFloat()

    private fun approach(current: Float, target: Float, rate: Float, dt: Float): Float =
        current + (target - current) * (1f - exp(-rate * dt))

    private companion object {
        const val TWO_PI = 2.0 * PI
        const val MOOD_RATE = 4f
        const val BLINK_CLOSE = 0.07f
        const val BLINK_HOLD = 0.04f
        const val BLINK_OPEN = 0.12f
    }
}
