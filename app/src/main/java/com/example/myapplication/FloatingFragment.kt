package com.example.myapplication

import android.animation.ValueAnimator
import com.google.android.material.progressindicator.CircularProgressIndicator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class FloatingFragment : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var container: ViewGroup
    private lateinit var  finalLabelText: TextView
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scrollJob: Job? = null
    private var lastFrameTime = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            detectMovement()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundNotification()
        }
        Log.d("APP", "Starting floating service")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.fragment_floating, null)

        container = floatingView.findViewById(R.id.floating_root)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 200
        params.y = 200

        windowManager.addView(floatingView, params)
        makeDraggable(floatingView, params)

        serviceScope.launch {
            PipelineController.state.collect { state ->
                when(state) {
                    PipelineState.Recording -> displayRecord()
                    PipelineState.Processing -> displayLoading()
                    PipelineState.Result ->  {
                        val prediction = PipelineController.prediction.value
                        if (prediction != null) {
                            displayResult(prediction)
                        }
                    }
                    PipelineState.Idle, PipelineState.Countdown -> stopSelf()
                }
            }

        }



        Log.d("FLOAT", "Pipeline state = $PipelineController.state")
    }



    private fun displayLoading(){
        container.removeAllViews()
        val view = LayoutInflater.from(this)
            .inflate(R.layout.loading, container, false)

        container.addView(view)

    }
    private fun displayResult(result: Prediction) {

        container.removeAllViews()

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)

        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.fragment_result, container, false)

        view.setOnClickListener {
            expandResult(result)
        }

        container.addView(view)

        progressBar = view.findViewById(R.id.circularProgressIndicator)
        progressText = view.findViewById(R.id.progressText)
        finalLabelText = view.findViewById(R.id.AI_REAL)
        startProgressAnimation(result.accuracy)
        updateColor(result.label)
    }

    private fun startProgressAnimation(result: Double) {
        val confidence = (result * 100).toInt()
        val animator = ValueAnimator.ofInt(0, confidence)
        animator.duration = 800
        animator.addUpdateListener { value ->
            val progress = value.animatedValue as Int
            progressBar.progress = progress
            progressText.text = "$progress%"
        }
        animator.start()

    }

    fun updateColor(result: String) {
        val colorRes = when (result) {
            "Real" -> R.color.green
            else -> R.color.red
        }

        val color = ContextCompat.getColor(this, colorRes)

        progressBar.setIndicatorColor(color)
        progressText.setTextColor(color)
        finalLabelText.setTextColor(color)

        finalLabelText.text = result.uppercase()


    }


    private fun displayRecord(){
        container.removeAllViews()
        val view = LayoutInflater.from(this)
            .inflate(R.layout.fragment_recording, container, false)

        container.addView(view)

    }

    private fun expandResult(prediction: Prediction){
        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)

        val expandedView = LayoutInflater.from(themedContext)
            .inflate(R.layout.expanded_result, null)

        val params = WindowManager.LayoutParams(
            800,
            800,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        expandedView.alpha = 0f
        expandedView.animate().alpha(1f).setDuration(200).start()

        params.gravity = Gravity.CENTER

        windowManager.addView(expandedView, params)
        expandedView.findViewById<View>(R.id.Exit).setOnClickListener {
            windowManager.removeView(expandedView)
        }

        expandedView.findViewById<View>(R.id.refresh).setOnClickListener {
            val intent = Intent("ACTION_RESTART_PIPELINE")
            sendBroadcast(intent)
            windowManager.removeView(expandedView)
        }

        setText(expandedView, prediction)
    }

    private fun setText(view: View, prediction: Prediction){

        val acc: TextView = view.findViewById(R.id.accuracy_score)
        val con: TextView = view.findViewById(R.id.confidence_sc)

        val conSc = (prediction.confidence * 100).toInt()
        val accSc = (prediction.accuracy * 100).toInt()

        acc.text = "$accSc%"
        con.text = "$conSc%"

    }
    private var movementFrames = 0

    private fun detectMovement() {
        val now = System.currentTimeMillis()

        if (lastFrameTime != 0L && now - lastFrameTime < 16) {
            movementFrames++
        } else {
            movementFrames = 0
        }

        if (movementFrames > 10) {
            // movement detected
            scrollJob?.cancel()
            scrollJob = serviceScope.launch {
                delay(150) // wait for scroll to stop
                sendBroadcast(Intent("ACTION_RESTART_PIPELINE"))
                Log.d("FLOAT", "Pipeline reset")
            }
        }

        lastFrameTime = now
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }

                }
                return false
            }
        })
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundNotification() {
        val channelId = "floating_service"
        val channel = NotificationChannel(
            channelId,
            "Floating Widget Service",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Floating widget active")
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView.let { windowManager.removeView(it) }
        serviceScope.cancel()

    }


    override fun onBind(intent: Intent?): IBinder? = null


}