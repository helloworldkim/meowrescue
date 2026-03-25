package com.meowrescue.game.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    private const val TAG = "AdManager"

    // Google test ad unit IDs
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    // Ad policy constants (GDD Section 5.2)
    private const val NO_ADS_UNTIL_LEVEL = 5
    private const val INTERSTITIAL_LEVEL_INTERVAL = 3
    private const val MAX_ADS_PER_SESSION = 10

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    private var lastInterstitialLevel: Int = 0
    private var sessionAdCount: Int = 0
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            Log.d(TAG, "MobileAds SDK initialized")
            isInitialized = true
        }
    }

    // --- Interstitial ---

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null) return
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Interstitial ad failed to load: ${error.message}")
                }
            })
    }

    fun shouldShowInterstitial(levelId: Int): Boolean {
        if (levelId <= NO_ADS_UNTIL_LEVEL) return false
        if (sessionAdCount >= MAX_ADS_PER_SESSION) return false
        if (lastInterstitialLevel > 0 && levelId - lastInterstitialLevel < INTERSTITIAL_LEVEL_INTERVAL) return false
        return interstitialAd != null
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed()
            }
        }
        sessionAdCount++
        lastInterstitialLevel = activity.intent.getIntExtra("level_id", 0)
        ad.show(activity)
    }

    // --- Rewarded ---

    fun loadRewarded(context: Context) {
        if (rewardedAd != null) return
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            })
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onDismissed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onDismissed()
            return
        }
        var rewarded = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded(activity)
                if (rewarded) onRewarded() else onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewarded(activity)
                onDismissed()
            }
        }
        sessionAdCount++
        ad.show(activity) { rewarded = true }
    }

    // --- Banner ---

    fun createBannerAd(context: Context): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }
}
