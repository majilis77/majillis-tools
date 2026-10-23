package com.sandbox.autoclick
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
class AutoClickService : AccessibilityService() {
 companion object { @Volatile var instance: AutoClickService? = null; private set }
 override fun onServiceConnected() { super.onServiceConnected(); instance = this }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
 override fun onInterrupt() = Unit
 override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
 fun performClick(x: Float, y: Float, durationMs: Long = 50L, callback: ((Boolean) -> Unit)? = null) {
  val path = Path().apply { moveTo(x, y) }
  val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
  val gesture = GestureDescription.Builder().addStroke(stroke).build()
  dispatchGesture(gesture, object : GestureResultCallback() {
   override fun onCompleted(gestureDescription: GestureDescription?) { callback?.invoke(true) }
   override fun onCancelled(gestureDescription: GestureDescription?) { callback?.invoke(false) }
  }, null)
 }
}
