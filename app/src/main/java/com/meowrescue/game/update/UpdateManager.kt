package com.meowrescue.game.update

import android.app.Activity
import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play In-App Updates 관리자.
 *
 * - updatePriority >= 4 또는 stalenessDays >= 7 → IMMEDIATE (강제 업데이트)
 * - 그 외 → FLEXIBLE (백그라운드 다운로드 + 스낵바 안내)
 */
class UpdateManager(private val activity: Activity) {

    companion object {
        const val REQUEST_CODE_UPDATE = 9001
        private const val TAG = "UpdateManager"
        private const val IMMEDIATE_PRIORITY_THRESHOLD = 4
        private const val IMMEDIATE_STALENESS_DAYS = 7
    }

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

    private var currentUpdateType: Int = AppUpdateType.FLEXIBLE

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (activity.isFinishing || activity.isDestroyed) return@InstallStateUpdatedListener
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            showCompleteUpdateSnackbar()
        }
    }

    /** 업데이트 체크 후 우선순위에 따라 IMMEDIATE/FLEXIBLE 자동 분기 */
    fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { updateInfo ->
            if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
            if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                val priority = updateInfo.updatePriority()
                val stalenessDays = updateInfo.clientVersionStalenessDays() ?: 0

                if (priority >= IMMEDIATE_PRIORITY_THRESHOLD || stalenessDays >= IMMEDIATE_STALENESS_DAYS) {
                    startImmediateUpdate(updateInfo)
                } else {
                    startFlexibleUpdate(updateInfo)
                }
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "업데이트 정보 조회 실패", e)
        }
    }

    private fun startImmediateUpdate(updateInfo: AppUpdateInfo) {
        if (!updateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return
        currentUpdateType = AppUpdateType.IMMEDIATE
        try {
            appUpdateManager.startUpdateFlowForResult(
                updateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                REQUEST_CODE_UPDATE
            )
        } catch (e: Exception) {
            Log.e(TAG, "IMMEDIATE 업데이트 시작 실패", e)
        }
    }

    private fun startFlexibleUpdate(updateInfo: AppUpdateInfo) {
        if (!updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return
        currentUpdateType = AppUpdateType.FLEXIBLE
        appUpdateManager.unregisterListener(installStateListener)
        appUpdateManager.registerListener(installStateListener)
        try {
            appUpdateManager.startUpdateFlowForResult(
                updateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                REQUEST_CODE_UPDATE
            )
        } catch (e: Exception) {
            appUpdateManager.unregisterListener(installStateListener)
            Log.e(TAG, "FLEXIBLE 업데이트 시작 실패", e)
        }
    }

    private fun showCompleteUpdateSnackbar() {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        Snackbar.make(rootView, "업데이트가 다운로드되었습니다.", Snackbar.LENGTH_INDEFINITE)
            .setAction("설치") { appUpdateManager.completeUpdate() }
            .show()
    }

    /**
     * onActivityResult에서 호출.
     * IMMEDIATE 업데이트를 사용자가 거부하면 앱 종료 (강제 업데이트 정책).
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQUEST_CODE_UPDATE) return
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "업데이트 취소 또는 실패: resultCode=$resultCode")
            if (currentUpdateType == AppUpdateType.IMMEDIATE) {
                activity.finish()
            }
        }
    }

    /**
     * onResume에서 호출.
     * - IMMEDIATE: 미완료 업데이트가 있으면 재시도
     * - FLEXIBLE: 이미 다운로드 완료된 업데이트가 있으면 스낵바 표시
     */
    fun onResume() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { updateInfo ->
            if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
            if (updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate(updateInfo)
            }
            if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showCompleteUpdateSnackbar()
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "onResume 업데이트 정보 조회 실패", e)
        }
    }

    fun onDestroy() {
        appUpdateManager.unregisterListener(installStateListener)
    }
}
