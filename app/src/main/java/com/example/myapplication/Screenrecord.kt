package com.example.myapplication

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import android.content.IntentFilter
import androidx.core.content.getSystemService
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.parcelize.Parcelize
@Parcelize
data class ScreenRecordConfig(
    val resultCode: Int,
    val data: Intent
): Parcelable
object ProjectionStore{
    var resultCode: Int? = null
    var data : Intent? = null
    var projectionConsumed: Boolean = false
    var mediaProjection: MediaProjection? = null

}

class  ScreenRecord: Service(){

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var mediaRecorder: MediaRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val outputFile by lazy {
           File(cacheDir, "temp.mp4")
    }

    private var callback: VideoReadyCallback? = null

    private val restartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_RESTART_PIPELINE_INTERNAL") {

                _isServiceRunning.value = false
                // 1. Release VirtualDisplay
                virtualDisplay?.release()
                virtualDisplay = null

                mediaRecorder?.apply {
                    try { stop() } catch (_: Exception) {}
                    try { reset() } catch (_: Exception) {}
                    try { release() } catch (_: Exception) {}
                }
                mediaRecorder = null
                Log.d("ScreenRecord", "BROADCAST RECEIVED")
                val restartIntent = Intent("ACTION_RESTART_PIPELINE")
                sendBroadcast(restartIntent)

            }
        }
    }

    fun setCallback(cb: VideoReadyCallback) {
        callback = cb
    }
    interface VideoReadyCallback {
        fun onVideoReady(file: File)
    }

    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback(){
        override fun onStop() {
            super.onStop()
            releaseResources()
            stopService()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("ACTION_RESTART_PIPELINE_INTERNAL")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restartReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(restartReceiver, filter)
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            START_RECORDING -> {
                val notification = NotificationHelper.createNotification(applicationContext)
                NotificationHelper.createNotificationChannel(applicationContext)
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                  startForeground(
                      1,
                      notification,
                      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                  )
                }else {
                    startForeground(
                        1,
                        notification
                    )
                }
                startRecording(intent)
            }

        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent) {
        PipelineController.setRecording()

        mediaProjection = ProjectionStore.mediaProjection

        mediaProjection = mediaProjectionManager?.getMediaProjection(
            ProjectionStore.resultCode!!,
            ProjectionStore.data!!
        )
        ProjectionStore.mediaProjection = mediaProjection
        ProjectionStore.projectionConsumed = true   // 🔒 token can NEVER be used again
        mediaProjection?.registerCallback(mediaProjectionCallback, null)
        Log.d("ScreenRecord", "Created MediaProjection from token")
/*
        if (mediaProjection == null &&
            !ProjectionStore.projectionConsumed &&
            ProjectionStore.resultCode != null &&
            ProjectionStore.data != null
        ) {
            mediaProjection = mediaProjectionManager?.getMediaProjection(
                ProjectionStore.resultCode!!,
                ProjectionStore.data!!
            )
            ProjectionStore.mediaProjection = mediaProjection
            ProjectionStore.projectionConsumed = true   // 🔒 token can NEVER be used again
            mediaProjection?.registerCallback(mediaProjectionCallback, null)
            Log.d("ScreenRecord", "Created MediaProjection from token")
        } else {
            Log.d("ScreenRecord", "Reusing existing MediaProjection")
            mediaProjection = ProjectionStore.mediaProjection

        }
*/
        if (mediaProjection == null) {
            Log.e("ScreenRecord", "MediaProjection is null, cannot start recording")
            return
        }


        val classifierIntent = Intent(this, Classifier::class.java)
            startService(classifierIntent)


        if (_isServiceRunning.value) {
            Log.d("ScreenRecord", "Already recording, ignoring start request")
            return
        }

        val overlayIntent = Intent(this, FloatingFragment::class.java)
        startService(overlayIntent)

        //Recording starts here
        /*val config = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
               intent.getParcelableExtra(
                   KEY_RECORDING_CONFIG,
                   ScreenRecordConfig::class.java
               )
        } else {
            intent.getParcelableExtra(KEY_RECORDING_CONFIG)

        }

        if(config == null)   {
            return
        }
*/



        initializeRecorder()
        virtualDisplay= createVirtualDisplay()
        Log.d("REC", "VirtualDisplay created: $virtualDisplay")
        mediaRecorder?.start()
        Log.d("REC", "VirtualDisplay created: $virtualDisplay")

        _isServiceRunning.value = true
        serviceScope.launch {
            delay(5000)
            stopRecording()
        }

        Log.d("ScreenRecord", "Recording started")
    }

    private  fun stopRecording() {
        Log.d("ScreenRecord", "recording is being stopped")


        Log.d("ScreenRecord", "is service running is true ")

        // 1. Release VirtualDisplay
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        // 3. Release MediaRecorder
        mediaRecorder?.apply {
            try {
                stop()
            } catch (_: Exception) {

                Log.d("ScreenRecord", "fails if recorder was never started")
            }
            try {
                reset()
            } catch (_: Exception) {

                Log.d("ScreenRecord", "fails if recorder is in wrong state")
            }
            try {
            release()
        }

            catch (_: Exception) {}

            Log.d("ScreenRecord", "almost never fails, but just in case")
        }

        mediaRecorder = null
        _isServiceRunning.value = false

        //float is updated with loading ui
        PipelineController.setProcessing()
        Log.d("ScreenRecord", "Calling onVideoReady with file: ${outputFile.path}")
        // 4. Notify callback

        serviceScope.launch {
            delay(200)
            callback?.onVideoReady(outputFile)

            Log.d("ScreenRecord", "Recording stopped successfully")
        }
        stopService()
        stopSelf()
        Log.d("REC", "File exists=${outputFile.exists()} size=${outputFile.length()}")

        ProjectionStore.mediaProjection=null
        ProjectionStore.data = null
        ProjectionStore.resultCode= null
        ProjectionStore.projectionConsumed = false
    }


    private fun stopService(){
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

    }

    private fun getWindowSize(): Pair<Int, Int>{
        val calculator = WindowMetricsCalculator.getOrCreate()
        val metrics = calculator.computeMaximumWindowMetrics(applicationContext)
        return metrics.bounds.width() to metrics.bounds.height()
    }


    private fun getScaledDimensions(
        maxWidth: Int,
        maxHeight: Int,
        scaleFactor: Float = 0.8f
    ):  Pair<Int, Int> {
        val aspectRatio = maxWidth / maxHeight.toFloat()

        var newWidth = (maxWidth * scaleFactor).toInt()
        var newHeight = (newWidth / aspectRatio).toInt()

        if(newHeight > (maxHeight * scaleFactor)) {
            newHeight = (maxHeight * scaleFactor).toInt()
            newWidth = (newHeight * aspectRatio).toInt()
        }

        return newWidth to newHeight
    }

    private fun initializeRecorder(){
        val (width, height)  = getWindowSize()
        val (scaledWidth, scaledHeight)  = getScaledDimensions(
            maxWidth = width,
            maxHeight = height
        )
        mediaRecorder = MediaRecorder().apply {
              setVideoSource(MediaRecorder.VideoSource.SURFACE)
              setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
              setOutputFile(outputFile.absolutePath)
              setVideoSize(scaledWidth, scaledHeight)
              setVideoEncoder(MediaRecorder.VideoEncoder.H264)
             setVideoEncodingBitRate(VIDEO_BIT_RATE_KILOBITS * 1000)
             setVideoFrameRate(VIDEO_FRAME_RATE)
             prepare()
        }
        Log.d("REC", "Recorder prepared: surface=${mediaRecorder?.surface}")
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        Log.d("REC", "Creating VirtualDisplay with surface=${mediaRecorder?.surface}")
        val (width, height) = getWindowSize()
        return mediaProjection?.createVirtualDisplay(
            "Screen",
            width,
            height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface,
            null,
            null
        )
    }




    override fun onDestroy(){
        super.onDestroy()
        _isServiceRunning.value= false
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("ScreenRecord", "Error stopping MediaRecorder", e)
        }

        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        unregisterReceiver(restartReceiver)


        serviceScope.coroutineContext.cancelChildren()
    }

    private fun releaseResources(){
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection = null
    }


    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecord = this@ScreenRecord
    }


    private val binder = LocalBinder()

    override fun onBind(intent: Intent?):IBinder {
        return binder
    }


    companion object{
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE_KILOBITS = 512

        const val START_RECORDING = "START_RECORDING"
        const val STOP_RECORDING = "STOP_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
    }
}