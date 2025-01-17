package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Constants Class - GrowthBook
 */
internal class Constants {

    companion object {
        /**
         * Identifier for Caching Feature Data in Internal Storage File
         */
        const val FEATURE_CACHE = "FeatureCache"
    }
}

/**
 * Type Alias for Feature in GrowthBook
 */
internal typealias GBFeatures = Map<String, GBFeature>

/**
 * Type Alias for Condition Element in GrowthBook Rules
 */
typealias GBCondition = JsonElement

/**
 * Handler for Refresh Cache Request
 * It updates back whether cache was refreshed or not
 */
typealias GBCacheRefreshHandler = (Boolean, GBError?) -> Unit

/**
 * Triple Tuple for GrowthBook Namespaces
 * It has ID, StartRange & EndRange
 */
typealias GBNameSpace = Triple<String, Float, Float>

/**
 * Double Tuple for GrowthBook Ranges
 */
typealias GBBucketRange = Pair<Float, Float>

/**
 *
 */
typealias GBStickyAttributeKey = String

/**
 *
 */
typealias GBStickyExperimentKey = String

/**
 *
 */
typealias GBStickyAssignments = Map<GBStickyExperimentKey, String>

/**
 * GrowthBook Error Class to handle any error / exception scenario
 */
class GBError(error: Throwable?) {

    /**
     * Error Message for the caught error / exception
     */
    private lateinit var errorMessage: String

    /**
     * Error Stacktrace for the caught error / exception
     */
    private lateinit var stackTrace: String

    /**
     * Constructor for initializing
     */
    init {
        if (error != null) {
            errorMessage = error.message ?: ""
            stackTrace = error.stackTraceToString()
        }
    }
}

/**
 * Object used for mutual exclusion and filtering users out of experiments based on random hashes. Has the following properties
 */
@Serializable
class GBFilter(
    /**
     * The seed used in the hash
     */
    var seed: String,
    /**
     * Array of ranges that are included
     */
    @Serializable(with = RangeSerializer.GBBucketRangeListSerializer::class)
    var ranges: List<GBBucketRange>,
    /**
     * The attribute to use (default to "id")
     */
    var attribute: String? = null,
    /**
     * The hash version to use (default to 2)
     */
    var hashVersion: Int? = null
)

/**
 * Meta info about an experiment variation. Has the following properties
 */
@Serializable
data class GBVariationMeta(
    /**
     * A unique key for this variation
     */
    var key: String? = null,
    /**
     * A human-readable name for this variation
     */
    var name: String? = null,
    /**
     * Used to implement holdout groups
     */
    var passthrough: Boolean? = null
)

/**
 * Used for remote feature evaluation to trigger the TrackingCallback. An object with 2 properties
 */
@Suppress("unused")
data class GBTrackData(
    /**
     * experiment - Experiment
     */
    var experiment: GBExperiment,
    /**
     * result - ExperimentResult
     */
    var experimentResult: GBExperimentResult
)

/**
 *
 */
@Serializable
data class GBStickyAssignmentsDocument(
    val attributeName: String,
    val attributeValue: String,
    val assignments: GBStickyAssignments
)

@Serializable
data class ParentConditionInterface(
    val id: String,
    val condition: GBCondition,
    val gate: Boolean? = null
)

@Serializable
data class TrackData(
    val experiment: GBExperiment,
    val result: GBExperimentResult
)

object RangeSerializer {
    object GBBucketRangeListSerializer : KSerializer<List<GBBucketRange>> {
        override val descriptor: SerialDescriptor =
            ListSerializer(PairSerializer(Float.serializer(), Float.serializer())).descriptor

        override fun serialize(encoder: Encoder, value: List<GBBucketRange>) {
            val jsonArray = JsonArray(value.map {
                JsonArray(
                    listOf(
                        JsonPrimitive(it.first),
                        JsonPrimitive(it.second)
                    )
                )
            })
            encoder.encodeSerializableValue(JsonArray.serializer(), jsonArray)
        }

        override fun deserialize(decoder: Decoder): List<GBBucketRange> {
            val jsonArray = decoder.decodeSerializableValue(JsonArray.serializer())
            return jsonArray.map {
                val first = it.jsonArray[0].jsonPrimitive.floatOrNull
                val second = it.jsonArray[1].jsonPrimitive.floatOrNull
                if (first != null && second != null) {
                    first to second
                } else {
                    throw IllegalArgumentException("Invalid range format")
                }
            }
        }
    }

    object GBBucketRangeSerializer : KSerializer<GBBucketRange> {
        override fun deserialize(decoder: Decoder): GBBucketRange {
            val array = decoder.decodeSerializableValue(JsonArray.serializer())
            val first = array[0].jsonPrimitive.floatOrNull
            val second = array[1].jsonPrimitive.floatOrNull
            return if (first != null && second != null) {
                first to second
            } else {
                throw IllegalArgumentException("Invalid range format")
            }
        }

        override val descriptor: SerialDescriptor =
            PairSerializer(Float.serializer(), Float.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: GBBucketRange) {
            val jsonArray = JsonArray(
                listOf(
                    JsonPrimitive(value.first),
                    JsonPrimitive(value.second)
                )
            )
            encoder.encodeSerializableValue(JsonArray.serializer(), jsonArray)
        }
    }
}
