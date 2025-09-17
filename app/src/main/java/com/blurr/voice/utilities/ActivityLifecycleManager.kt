package com.blurr.voice.utilities

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object ActivityLifecycleManager : Application.ActivityLifecycleCallbacks {

    private var currentActivity: WeakReference<Activity?> = WeakReference(null)

    fun getCurrentActivity(): Activity? {
        return currentActivity.get()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivity.get() == activity) {
            currentActivity.clear()
        }
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity.get() == activity) {
            currentActivity.clear()
        }
    }
}
