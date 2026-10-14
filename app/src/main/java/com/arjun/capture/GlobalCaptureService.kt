package com.arjun.capture

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

class GlobalCaptureService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingButton: ImageView? = null
    private var cropOverlayView: CropOverlayView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingButton()
    }

    private fun setupFloatingButton() {
        floatingButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    captureScreenWithoutPrompt()
                } else {
                    Toast.makeText(this@GlobalCaptureService, "Global Capture Requires Android 11+", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        windowManager?.addView(floatingButton, params)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenWithoutPrompt() {
        floatingButton?.visibility = View.GONE

        floatingButton?.postDelayed({
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val hwBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)

                    val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBuffer.close()

                    if (swBitmap != null) {
                        showCropOverlay(swBitmap)
                    } else {
                        floatingButton?.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(errorCode: Int) {
                    floatingButton?.visibility = View.VISIBLE
                    Toast.makeText(this@GlobalCaptureService, "Capture failed: $errorCode", Toast.LENGTH_SHORT).show()
                }
            })
        }, 150)
    }

    private fun showCropOverlay(screenshot: Bitmap) {
        cropOverlayView = CropOverlayView(this, screenshot) { topY, bottomY ->
            windowManager?.removeView(cropOverlayView)
            cropOverlayView = null
            floatingButton?.visibility = View.VISIBLE

            if (topY != -1 && bottomY != -1) {
                cropAndSave(screenshot, topY, bottomY)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(cropOverlayView, params)
    }

    private fun cropAndSave(fullBitmap: Bitmap, topY: Int, bottomY: Int) {
        val safeTop = Math.max(0, topY)
        val safeBottom = Math.min(fullBitmap.height, bottomY)
        val height = safeBottom - safeTop

        if (height <= 0) return

        val croppedBitmap = Bitmap.createBitmap(fullBitmap, 0, safeTop, fullBitmap.width, height)
        val filename = "snip_${System.currentTimeMillis()}.png"
        var savedPath = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Capture")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                savedPath = "Gallery (Pictures/Capture)"
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val captureDir = File(imagesDir, "Capture")
            if (!captureDir.exists()) captureDir.mkdirs()

            val file = File(captureDir, filename)
            FileOutputStream(file).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            android.media.MediaScannerConnection.scanFile(
                this, arrayOf(file.absolutePath), arrayOf("image/png"), null
            )
            savedPath = file.absolutePath
        }

        if (savedPath.isNotEmpty()) {
            Toast.makeText(this, "Saved to $savedPath", Toast.LENGTH_LONG).show()
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        floatingButton?.let { windowManager?.removeView(it) }
        cropOverlayView?.let { windowManager?.removeView(it) }
    }
}

class CropOverlayView(
    context: Context,
    private val screenshot: Bitmap,
    private val onCropComplete: (Int, Int) -> Unit
) : View(context) {

    private var startY = -1f
    private var currentY = -1f

    private val dimPaint = Paint().apply { color = Color.parseColor("#A6000000") }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(screenshot, 0f, 0f, null)

        if (startY == -1f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        } else {
            val topY = Math.min(startY, currentY)
            val bottomY = Math.max(startY, currentY)

            canvas.drawRect(0f, 0f, width.toFloat(), topY, dimPaint)
            canvas.drawRect(0f, bottomY, width.toFloat(), height.toFloat(), dimPaint)

            canvas.drawLine(0f, topY, width.toFloat(), topY, borderPaint)
            canvas.drawLine(0f, bottomY, width.toFloat(), bottomY, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Cancel the capture immediately if a second finger taps anywhere
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            onCropComplete(-1, -1)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                currentY = event.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                currentY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val topY = Math.min(startY, currentY).toInt()
                val bottomY = Math.max(startY, currentY).toInt()

                if (Math.abs(bottomY - topY) > 20) {
                    onCropComplete(topY, bottomY)
                } else {
                    onCropComplete(-1, -1)
                }
            }
        }
        return true
    }
}
