package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ScreenRecord.Companion.KEY_RECORDING_CONFIG
import com.example.myapplication.ScreenRecord.Companion.START_RECORDING
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity(),  InitialFragment.OnButtonClickListener, CountdownFragment.CountdownListener{
    private var screenRecord: ScreenRecord? = null
    private var classifierService: Classifier? = null
    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()!!
    }

    private var hasNotificationPermission: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    private val screenRecordLauncher =
        registerForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
               val intent = result.data ?: return@registerForActivityResult
               val config = ScreenRecordConfig(
                   resultCode = result.resultCode,
                   data = intent
               )

               ProjectionStore.resultCode = result.resultCode
               ProjectionStore.data = intent

            val serviceIntent = Intent(
                applicationContext,
                ScreenRecord::class.java
            ).apply {
                action = START_RECORDING
                putExtra(KEY_RECORDING_CONFIG, config)
            }
            startForegroundService(serviceIntent)


        }

    @RequiresApi(Build.VERSION_CODES.O)
    private val permissionLauncher = registerForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (hasNotificationPermission ) {
            screenRecordLauncher.launch(
                mediaProjectionManager.createScreenCaptureIntent()
            )
        }
    }

    private val restartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_RESTART_PIPELINE") {

            requestScreenCapture()

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        Log.d("APP", "MAIN ACTIVITY STARTED")

        lifecycleScope.launch {
            PipelineController.state.collect { state ->
                if (state == PipelineState.Idle) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.frameLayout, InitialFragment())
                        .commit()
                }
            }
        }


        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }


        bindService(
            Intent(this, ScreenRecord::class.java),
            screenRecordConnection,
            BIND_AUTO_CREATE
        )

        bindService(
            Intent(this, Classifier::class.java),
            classifierConnection,
            BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        PipelineController.reset()
       val filter = IntentFilter("ACTION_RESTART_PIPELINE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restartReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(restartReceiver, filter)
        }

    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun checkOverlayPermission() {
        Log.d("APP", "checkOverlayPermission called")

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        } else {
            PipelineController.startPipeline()
            startCountdown()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun countdownDone() {
        Log.d("APP", "Countdown finished")
        requestScreenCapture()
    }

     fun requestScreenCapture() {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

         val hasStoredProjection = ProjectionStore.data != null

         if (!hasStoredProjection) {

             screenRecordLauncher.launch(
                 mediaProjectionManager.createScreenCaptureIntent()
             )
             return
         }

         /*if (ProjectionStore.projectionConsumed && ProjectionStore.mediaProjection != null) {
             restartPipelineWithoutDialog()
             return
         }

*/
        screenRecordLauncher.launch(
            mediaProjectionManager.createScreenCaptureIntent()
        )

    }


     fun startCountdown(){
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, CountdownFragment())
            .addToBackStack(null)
            .commit()
    }


    private val screenRecordConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            screenRecord = (binder as ScreenRecord.LocalBinder).getService()
            tryConnectServices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenRecord = null
        }
    }

    private val classifierConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            classifierService = (binder as Classifier.LocalBinder).getService()
            tryConnectServices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            classifierService = null
        }
    }

    private fun tryConnectServices() {
        val s = screenRecord
        val c = classifierService

        if (s != null && c != null) {
            s.setCallback(c)   // 🔥 THIS is the magic line
            Log.d("APP", "Callback connected: ScreenRecord → Classifier")
        }
    }

    private fun restartPipelineWithoutDialog(){

        PipelineController.setRecording()
                val config = ScreenRecordConfig(
                    resultCode = ProjectionStore.resultCode!!,
                    data = ProjectionStore.data!!
                )


                val serviceIntent = Intent(
                    applicationContext,
                    ScreenRecord::class.java
                ).apply {
                    action = START_RECORDING
                    putExtra(KEY_RECORDING_CONFIG, config)
                }
                startForegroundService(serviceIntent)



    }

}
