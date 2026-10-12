package com.example.ui.components

import android.util.Log
import java.lang.reflect.Method
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives the skeleton and the face morph targets of a VRoid/VRM character that is loaded as a
 * plain glTF (.glb) through SceneView/Filament.
 *
 * Filament objects are reached with reflection on purpose: the exact SceneView wrapper types differ
 * between versions, and a missing method must never crash the app. If anything is unavailable,
 * [create] returns null (or the rig disables itself after repeated failures) and the character is
 * simply shown with a basic idle motion instead.
 *
 * Model facts this class relies on (checked in the supplied VRoid 1.16.5 files):
 *  - bone node names J_Bip_* / J_Adj_*, all humanoid bones have an identity local rotation (T-pose)
 *  - the face mesh has 49 morph targets; indices below come from the VRM blend-shape binds
 *  - the model faces -Z, the character's left arm is on -X
 */
class VrmRig private constructor(
    private val transformManager: Any,
    private val renderableManager: Any?,
    private val animator: Any?,
    private val setTransform: Method,
    private val setMorphWeights: Method?,
    private val updateBoneMatrices: Method?,
    private val slots: Map<Bone, Slot>,
    private val faceRenderables: IntArray,
    morphCount: Int,
    val status: String
) {

    enum class Bone(val nodeName: String) {
        HIPS("J_Bip_C_Hips"),
        SPINE("J_Bip_C_Spine"),
        CHEST("J_Bip_C_Chest"),
        UPPER_CHEST("J_Bip_C_UpperChest"),
        NECK("J_Bip_C_Neck"),
        HEAD("J_Bip_C_Head"),
        EYE_L("J_Adj_L_FaceEye"),
        EYE_R("J_Adj_R_FaceEye"),
        SHOULDER_L("J_Bip_L_Shoulder"),
        SHOULDER_R("J_Bip_R_Shoulder"),
        UPPER_ARM_L("J_Bip_L_UpperArm"),
        UPPER_ARM_R("J_Bip_R_UpperArm"),
        LOWER_ARM_L("J_Bip_L_LowerArm"),
        LOWER_ARM_R("J_Bip_R_LowerArm")
    }

    class Slot(val instance: Int, val rest: FloatArray)

    private val weights = FloatArray(if (morphCount > 0) morphCount else 1)
    private val rot = FloatArray(9)
    private val out = FloatArray(16)
    private var failures = 0

    /** Applies one frame of motion. Safe to call every frame; never throws. */
    fun applyPose(p: HumanMotion.Pose) {
        if (failures >= MAX_FAILURES) return
        try {
            // --- body: the rest pose is a T-pose, so the arms are lowered every frame ---
            setBone(Bone.HIPS, 0f, p.hipsYaw, p.hipsRoll, 0f, p.hipsLift, 0f)
            setBone(Bone.SPINE, p.spinePitch, p.spineYaw, 0f)
            setBone(Bone.CHEST, p.chestPitch, 0f, 0f)
            setBone(Bone.UPPER_CHEST, p.chestPitch * 0.5f, 0f, 0f)
            setBone(Bone.NECK, p.neckPitch, p.neckYaw, p.neckRoll)
            setBone(Bone.HEAD, p.headPitch, p.headYaw, p.headRoll)
            setBone(Bone.EYE_L, p.eyePitch, p.eyeYaw, 0f)
            setBone(Bone.EYE_R, p.eyePitch, p.eyeYaw, 0f)
            setBone(Bone.SHOULDER_L, 0f, 0f, SHOULDER_DROP_DEG)
            setBone(Bone.SHOULDER_R, 0f, 0f, -SHOULDER_DROP_DEG)
            setBone(Bone.UPPER_ARM_L, 0f, 0f, ARM_DOWN_DEG + p.armSway)
            setBone(Bone.UPPER_ARM_R, 0f, 0f, -(ARM_DOWN_DEG + p.armSway))
            setBone(Bone.LOWER_ARM_L, 0f, -ELBOW_BEND_DEG, 0f)
            setBone(Bone.LOWER_ARM_R, 0f, ELBOW_BEND_DEG, 0f)

            // skinning must be refreshed after the joint transforms changed
            updateBoneMatrices?.invoke(animator)

            // --- face morph targets ---
            val setWeights = setMorphWeights
            if (setWeights != null && faceRenderables.isNotEmpty()) {
                java.util.Arrays.fill(weights, 0f)
                put(IDX_ANGRY, p.angry)
                put(IDX_FUN, p.playful)
                put(IDX_JOY, p.joy)
                put(IDX_SORROW, p.sorrow)
                put(IDX_SURPRISED, p.surprised)
                put(IDX_BLINK, p.blink)
                put(IDX_A, p.vowelA)
                put(IDX_I, p.vowelI)
                put(IDX_U, p.vowelU)
                put(IDX_E, p.vowelE)
                put(IDX_O, p.vowelO)
                for (renderable in faceRenderables) {
                    setWeights.invoke(renderableManager, renderable, weights, 0)
                }
            }
        } catch (t: Throwable) {
            failures++
            Log.w(TAG, "applyPose failed ($failures/$MAX_FAILURES): ${t.message}", t)
        }
    }

    private fun put(index: Int, value: Float) {
        if (index < weights.size) weights[index] = value.coerceIn(0f, 1f)
    }

    /**
     * Rotates [bone] (in its parent's frame, on top of its rest transform) and optionally moves it.
     * Rotation order is yaw (Y) * pitch (X) * roll (Z).
     */
    private fun setBone(
        bone: Bone,
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
        dx: Float = 0f,
        dy: Float = 0f,
        dz: Float = 0f
    ) {
        val slot = slots[bone] ?: return
        eulerToMat3(pitchDeg, yawDeg, rollDeg, rot)
        val rest = slot.rest
        for (c in 0..2) {
            val b = c * 4
            for (row in 0..2) {
                out[b + row] = rot[row * 3] * rest[b] +
                    rot[row * 3 + 1] * rest[b + 1] +
                    rot[row * 3 + 2] * rest[b + 2]
            }
            out[b + 3] = 0f
        }
        out[12] = rest[12] + dx
        out[13] = rest[13] + dy
        out[14] = rest[14] + dz
        out[15] = 1f
        setTransform.invoke(transformManager, slot.instance, out)
    }

    companion object {
        private const val TAG = "VrmRig"
        private const val MAX_FAILURES = 5
        private const val MIN_FACE_MORPHS = 40

        // Resting pose: the VRoid export is a T-pose, so lower the arms into a natural stance.
        private const val ARM_DOWN_DEG = 70f
        private const val ELBOW_BEND_DEG = 14f
        private const val SHOULDER_DROP_DEG = 3f

        // Face morph target indices (from the VRM blendShapeMaster binds of the supplied models)
        private const val IDX_ANGRY = 0
        private const val IDX_FUN = 1
        private const val IDX_JOY = 2
        private const val IDX_SORROW = 3
        private const val IDX_SURPRISED = 4
        private const val IDX_BLINK = 12
        private const val IDX_A = 32
        private const val IDX_I = 33
        private const val IDX_U = 34
        private const val IDX_E = 35
        private const val IDX_O = 36

        /** Returns a rig for [modelInstance], or null when the model/engine does not allow it. */
        fun create(engine: Any, modelInstance: Any): VrmRig? {
            return try {
                build(engine, modelInstance)
            } catch (t: Throwable) {
                Log.w(TAG, "Rig unavailable: ${t.message}", t)
                null
            }
        }

        private fun build(engine: Any, modelInstance: Any): VrmRig? {
            val intType = Int::class.javaPrimitiveType!!

            val asset = call0(modelInstance, "getAsset") ?: modelInstance
            val tm = call0(engine, "getTransformManager") ?: return null
            val rm = call0(engine, "getRenderableManager")

            val getFirstEntity = asset.javaClass.getMethod("getFirstEntityByName", String::class.java)
            val tmGetInstance = tm.javaClass.getMethod("getInstance", intType)
            val tmGetTransform = tm.javaClass.getMethod("getTransform", intType, FloatArray::class.java)
            val tmSetTransform = tm.javaClass.getMethod("setTransform", intType, FloatArray::class.java)

            val slots = HashMap<Bone, Slot>()
            for (bone in Bone.values()) {
                val entity = getFirstEntity.invoke(asset, bone.nodeName) as Int
                if (entity == 0) continue
                val instance = tmGetInstance.invoke(tm, entity) as Int
                if (instance == 0) continue
                val rest = FloatArray(16)
                tmGetTransform.invoke(tm, instance, rest)
                slots[bone] = Slot(instance, rest)
            }
            if (!slots.containsKey(Bone.HEAD) || slots.size < 6) {
                Log.w(TAG, "Skeleton not found (bones: ${slots.size}); using basic idle")
                return null
            }

            // face renderables: every primitive of the face mesh has the same 49 morph targets
            val faceList = ArrayList<Int>()
            var morphCount = 0
            var setMorph: Method? = null
            if (rm != null) {
                try {
                    val rmGetInstance = rm.javaClass.getMethod("getInstance", intType)
                    val rmCount = try {
                        rm.javaClass.getMethod("getMorphTargetCount", intType)
                    } catch (e: NoSuchMethodException) {
                        null
                    }
                    val namesOf = try {
                        asset.javaClass.getMethod("getMorphTargetNames", intType)
                    } catch (e: NoSuchMethodException) {
                        null
                    }
                    val renderables = call0(asset, "getRenderableEntities") as? IntArray
                    if (renderables != null) {
                        for (entity in renderables) {
                            val ri = rmGetInstance.invoke(rm, entity) as Int
                            if (ri == 0) continue
                            var count = 0
                            if (rmCount != null) {
                                count = rmCount.invoke(rm, ri) as Int
                            } else if (namesOf != null) {
                                val names = namesOf.invoke(asset, entity) as? Array<*>
                                count = names?.size ?: 0
                            }
                            if (count >= MIN_FACE_MORPHS) {
                                faceList.add(ri)
                                if (count > morphCount) morphCount = count
                            }
                        }
                    }
                    if (faceList.isNotEmpty()) {
                        setMorph = rm.javaClass.getMethod(
                            "setMorphWeights", intType, FloatArray::class.java, intType
                        )
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Face morph targets unavailable: ${t.message}")
                    faceList.clear()
                }
            }

            val animator = call0(modelInstance, "getAnimator") ?: call0(asset, "getAnimator")
            val updateBones = try {
                animator?.javaClass?.getMethod("updateBoneMatrices")
            } catch (e: NoSuchMethodException) {
                null
            }

            val status = "rig ok · bones ${slots.size}/${Bone.values().size}" +
                " · face morph ${if (faceList.isEmpty()) "n/a" else "${faceList.size}x$morphCount"}" +
                (if (updateBones == null) " · skin refresh n/a" else "")
            Log.i(TAG, status)

            return VrmRig(
                transformManager = tm,
                renderableManager = rm,
                animator = animator,
                setTransform = tmSetTransform,
                setMorphWeights = setMorph,
                updateBoneMatrices = updateBones,
                slots = slots,
                faceRenderables = faceList.toIntArray(),
                morphCount = morphCount,
                status = status
            )
        }

        private fun call0(target: Any, name: String): Any? {
            return try {
                target.javaClass.getMethod(name).invoke(target)
            } catch (e: NoSuchMethodException) {
                null
            }
        }

        /** Row-major 3x3 rotation matrix R = Ry(yaw) * Rx(pitch) * Rz(roll), angles in degrees. */
        private fun eulerToMat3(pitchDeg: Float, yawDeg: Float, rollDeg: Float, out: FloatArray) {
            val rx = Math.toRadians(pitchDeg.toDouble())
            val ry = Math.toRadians(yawDeg.toDouble())
            val rz = Math.toRadians(rollDeg.toDouble())
            val cx = cos(rx).toFloat()
            val sx = sin(rx).toFloat()
            val cy = cos(ry).toFloat()
            val sy = sin(ry).toFloat()
            val cz = cos(rz).toFloat()
            val sz = sin(rz).toFloat()

            out[0] = cy * cz + sy * sx * sz
            out[1] = -cy * sz + sy * sx * cz
            out[2] = sy * cx
            out[3] = cx * sz
            out[4] = cx * cz
            out[5] = -sx
            out[6] = -sy * cz + cy * sx * sz
            out[7] = sy * sz + cy * sx * cz
            out[8] = cy * cx
        }
    }
}
