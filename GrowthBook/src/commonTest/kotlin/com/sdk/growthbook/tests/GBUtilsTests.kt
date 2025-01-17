package com.sdk.growthbook.tests

import com.sdk.growthbook.Utils.Constants
import com.sdk.growthbook.Utils.GBBucketRange
import com.sdk.growthbook.Utils.GBNameSpace
import com.sdk.growthbook.Utils.GBUtils
import com.sdk.growthbook.Utils.toJsonElement
import com.sdk.growthbook.Utils.toList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBUtilsTests {

    @Test
    fun testHash() {
        val evalConditions = GBTestHelper.getFNVHashData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testContext = item[1].jsonPrimitive.content
                val experiment = item[3].jsonPrimitive.content
                val hashVersion = item[2].jsonPrimitive.content
                val seed = item[0].jsonPrimitive.content

                val result = GBUtils.hash(testContext, hashVersion.toInt(), seed)

                val status =
                    item[0].toString() + "\nExpected Result - " + item[1].toString() + "\nActual result - " + result + "\n"

                if (experiment == result.toString()) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testBucketRange() {
        val evalConditions = GBTestHelper.getBucketRangeData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[1].jsonArray[0].jsonPrimitive.content.toIntOrNull()
                val coverage = item[1].jsonArray[1].jsonPrimitive.content.toFloatOrNull()
                var weights: List<Float>? = null
                if (item[1].jsonArray[2] != JsonNull) {
                    weights =
                        (item[1].jsonArray[2].jsonArray.toList() as? List<String>)?.map { value -> value.toFloat() }
                }

                val bucketRange = GBUtils.getBucketRanges(
                    numVariations ?: 1,
                    coverage ?: 1F,
                    weights ?: ArrayList()
                )

                val status =
                    item[0].toString() + "\nExpected Result - " + item[2].jsonArray.toString() + "\nActual result - " + bucketRange.toJsonElement().jsonArray.toString() + "\n"

                if (compareBucket(item[2].jsonArray.toList() as List<List<Float>>, bucketRange)) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    private fun compareBucket(
        expectedResults: List<List<Float>>,
        calaculatedResults: List<GBBucketRange>
    ): Boolean {

        val pairExpectedResults = getPairedData(expectedResults)

        if (pairExpectedResults.size != expectedResults.size) {
            return false
        }

        var result = true
        for (i in 0..pairExpectedResults.size - 1) {
            val source = pairExpectedResults[i]
            val target = calaculatedResults[i]

            if (source.first != target.first || source.second != target.second) {
                result = false
                break
            }
        }

        return result
    }

    private fun getPairedData(items: List<List<Float>>): List<GBBucketRange> {
        val pairExpectedResults: ArrayList<Pair<Float, Float>> = ArrayList()

        for (item in items) {
            val pair = item.zipWithNext().single()
            pairExpectedResults.add(
                Pair(
                    (pair.first as JsonPrimitive).content.toFloat(),
                    (pair.second as JsonPrimitive).content.toFloat()
                )
            )
        }
        return pairExpectedResults
    }

    @Test
    fun testChooseVariation() {
        val evalConditions = GBTestHelper.getChooseVariationData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val hash = item[1].jsonPrimitive.content.toFloatOrNull()
                val rangeData = getPairedData(item[2].jsonArray.toList() as List<List<Float>>)

                val result = GBUtils.chooseVariation(hash ?: 0F, rangeData)

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3].toString() + "\nActual result - " + result.toString() + "\n"

                if (item[3].toString() == result.toString()) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testInNameSpace() {
        val evalConditions = GBTestHelper.getInNameSpaceData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val userId = item[1].jsonPrimitive.content
                val jsonArray = item[2].jsonArray
                val namespace = GBUtils.getGBNameSpace(jsonArray)

                val result = namespace?.let { GBUtils.inNamespace(userId, it) }

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3].toString() + "\nActual result - " + result + "\n"


                if (item[3].toString() == result.toString()) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testEqualWeights() {
        val evalConditions = GBTestHelper.getEqualWeightsData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[0].jsonPrimitive.content.toInt()

                val result = GBUtils.getEqualWeights(numVariations)

                val status =
                    "Expected Result - " + item[1].toString() + "\nActual result - " + result + "\n"

                var resultTest = true

                if (item[1].jsonArray.size != result.size) {
                    resultTest = false
                } else {
                    for (i in 0..result.size - 1) {
                        val source = item[1].jsonArray[i].jsonPrimitive.float
                        val target = result[i]

                        if (source != target) {
                            resultTest = false
                            break
                        }
                    }
                }

                if (resultTest) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testEdgeCases() {

        GBUtils()
        Constants()

        assertFalse(GBUtils.inNamespace("4242", GBNameSpace("", 0F, 0F)))

        val items = ArrayList<JsonPrimitive>()
        items.add(JsonPrimitive(1))

        assertTrue(GBUtils.getGBNameSpace(JsonArray(items)) == null)
    }
}