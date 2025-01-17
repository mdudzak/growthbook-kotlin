package com.sdk.growthbook

import com.sdk.growthbook.Network.NetworkDispatcher
import com.sdk.growthbook.Utils.Crypto
import com.sdk.growthbook.Utils.GBCacheRefreshHandler
import com.sdk.growthbook.Utils.GBError
import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.Utils.GBUtils.Companion.refreshStickyBuckets
import com.sdk.growthbook.Utils.Resource
import com.sdk.growthbook.Utils.getFeaturesFromEncryptedFeatures
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.Flow

typealias GBTrackingCallback = (GBExperiment, GBExperimentResult) -> Unit

/**
 * SDKBuilder - Root Class for SDK Initializers for GrowthBook SDK
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption.
 */
abstract class SDKBuilder(
    val apiKey: String,
    val hostURL: String,
    val attributes: Map<String, Any>,
    val trackingCallback: GBTrackingCallback,
    val encryptionKey: String?,
    val networkDispatcher: NetworkDispatcher
) {
    internal var qaMode: Boolean = false
    internal var forcedVariations: Map<String, Int> = HashMap()
    internal var enabled: Boolean = true

    /**
     * Set Forced Variations - Default Empty
     */
    fun setForcedVariations(forcedVariations: Map<String, Int>): SDKBuilder {
        this.forcedVariations = forcedVariations
        return this
    }

    /**
     * Set QA Mode - Default Disabled
     */
    fun setQAMode(isEnabled: Boolean): SDKBuilder {
        this.qaMode = isEnabled
        return this
    }

    /**
     * Set Enabled - Default Disabled - If Enabled - then experiments will be disabled
     */
    fun setEnabled(isEnabled: Boolean): SDKBuilder {
        this.enabled = isEnabled
        return this
    }

    /**
     * This method is open to be overridden by subclasses
     */
    @DelicateCoroutinesApi
    abstract fun initialize(): GrowthBookSDK
}

/**
 * SDKBuilder - Initializer for GrowthBook SDK for JAVA
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Features - GrowthBook Features Map - Synced via Web API / Web Hooks
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption.
 */
class GBSDKBuilderJAVA(
    apiKey: String,
    hostURL: String,
    attributes: Map<String, Any>,
    val features: GBFeatures,
    trackingCallback: GBTrackingCallback,
    encryptionKey: String?,
    networkDispatcher: NetworkDispatcher
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher
) {
    /**
     * Initialize the JAVA SDK
     */
    @DelicateCoroutinesApi
    override fun initialize(): GrowthBookSDK {

        val gbContext = GBContext(
            apiKey = apiKey,
            enabled = enabled,
            attributes = attributes,
            hostURL = hostURL,
            qaMode = qaMode,
            forcedVariations = forcedVariations,
            trackingCallback = trackingCallback,
            encryptionKey = encryptionKey
        )

        return GrowthBookSDK(gbContext, null, networkDispatcher, features)
    }
}

/**
 * SDKBuilder - Initializer for GrowthBook SDK for Apps
 * APIKey - API Key
 * HostURL - Server URL
 * UserAttributes - User Attributes
 * Tracking Callback - Track Events for Experiments
 * EncryptionKey - Encryption key if you intend to use data encryption.
 */
class GBSDKBuilder(
    apiKey: String,
    hostURL: String,
    attributes: Map<String, Any>,
    trackingCallback: GBTrackingCallback,
    encryptionKey: String? = null,
    networkDispatcher: NetworkDispatcher
) : SDKBuilder(
    apiKey, hostURL,
    attributes, trackingCallback, encryptionKey, networkDispatcher
) {

    private var refreshHandler: GBCacheRefreshHandler? = null

    /**
     * Set Refresh Handler - Will be called when cache is refreshed
     */
    fun setRefreshHandler(refreshHandler: GBCacheRefreshHandler): GBSDKBuilder {
        this.refreshHandler = refreshHandler
        return this
    }

    /**
     * Initialize the JAVA SDK
     */
    @DelicateCoroutinesApi
    override fun initialize(): GrowthBookSDK {

        val gbContext = GBContext(
            apiKey = apiKey,
            enabled = enabled,
            attributes = attributes,
            hostURL = hostURL,
            qaMode = qaMode,
            forcedVariations = forcedVariations,
            trackingCallback = trackingCallback,
            encryptionKey = encryptionKey
        )

        return GrowthBookSDK(gbContext, refreshHandler, networkDispatcher, features = null)
    }
}

/**
 * The main export of the libraries is a simple GrowthBook wrapper class that takes a Context object in the constructor.
 * It exposes two main methods: feature and run.
 */
class GrowthBookSDK() : FeaturesFlowDelegate {

    private var refreshHandler: GBCacheRefreshHandler? = null
    private lateinit var networkDispatcher: NetworkDispatcher
    private lateinit var featuresViewModel: FeaturesViewModel
    private lateinit var attributeOverrides: Map<String, Any>

    //@ThreadLocal
    internal companion object {
        internal lateinit var gbContext: GBContext
    }

    @DelicateCoroutinesApi
    internal constructor(
        context: GBContext,
        refreshHandler: GBCacheRefreshHandler?,
        networkDispatcher: NetworkDispatcher,
        features: GBFeatures? = null,
    ) : this() {
        gbContext = context
        this.refreshHandler = refreshHandler
        this.networkDispatcher = networkDispatcher
        /**
         * JAVA Consumers preset Features
         * SDK will not call API to fetch Features List
         */
        this.featuresViewModel =
            FeaturesViewModel(
                delegate = this,
                dataSource = FeaturesDataSource(dispatcher = networkDispatcher),
                encryptionKey = null
            )
        if (features != null) {
            gbContext.features = features
        } else {
            featuresViewModel.encryptionKey = gbContext.encryptionKey
            refreshCache()
        }
        this.attributeOverrides = gbContext.attributes
    }

    /**
     * Manually Refresh Cache
     */
    @DelicateCoroutinesApi
    fun refreshCache() {
        featuresViewModel.fetchFeatures()
    }

    /**
     * Get Context - Holding the complete data regarding cached features & attributes etc.
     */
    fun getGBContext(): GBContext {
        return gbContext
    }

    /**
     * receive Features automatically when updated
     * SSE
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>> {
        return featuresViewModel.autoRefreshFeatures()
    }

    /**
     * Get Cached Features
     */
    fun getFeatures(): GBFeatures {
        return gbContext.features
    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        gbContext.features = features
        if (isRemote) {
            this.refreshHandler?.invoke(true, null)
        }
    }

    /**
     * The setEncryptedFeatures method takes an encrypted string with an encryption key and then decrypts it with the default method of decrypting or with a method of decrypting from the user
     */
    fun setEncryptedFeatures(
        encryptedString: String,
        encryptionKey: String,
        subtleCrypto: Crypto?
    ) {
        gbContext.features =
            getFeaturesFromEncryptedFeatures(
                encryptedString = encryptedString,
                encryptionKey = encryptionKey,
                subtleCrypto = subtleCrypto
            ) ?: return
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {

        if (isRemote) {
            this.refreshHandler?.invoke(false, error)
        }
    }

    /**
     * The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.
     */
    fun feature(id: String): GBFeatureResult {
        return GBFeatureEvaluator().evaluateFeature(
            context = gbContext,
            featureKey = id,
            attributeOverrides = attributeOverrides
        )
    }

    /**
     * The isOn method takes a single string argument, which is the unique identifier for the feature and returns the feature state on/off
     */
    fun isOn(featureDd: String): Boolean {
        return feature(id = featureDd).on
    }

    /**
     * The run method takes an Experiment object and returns an ExperimentResult
     */
    fun run(experiment: GBExperiment): GBExperimentResult {
        return GBExperimentEvaluator().evaluateExperiment(
            context = gbContext,
            experiment = experiment,
            attributeOverrides = attributeOverrides
        )
    }

    /**
     * The setAttributes method replaces the Map of user attributes that are used to assign variations
     */
    fun setAttributes(attributes: Map<String, Any>) {
        gbContext.attributes = attributes
        refreshStickyBucketService()
    }

    fun setAttributeOverrides(overrides: Map<String, Any>) {
        attributeOverrides = overrides
        refreshStickyBucketService()
    }

    override fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {
        refreshStickyBucketService(dataModel = model)
    }

    private fun refreshStickyBucketService(dataModel: FeaturesDataModel? = null) {
        if (gbContext.stickyBucketService != null) {
            val featureEvaluator = GBFeatureEvaluator().evaluateFeature(
                context = gbContext,
                featureKey = "",
                attributeOverrides = attributeOverrides
            )
            refreshStickyBuckets(context = gbContext, data = dataModel, attributeOverrides = attributeOverrides)
        }
    }
}