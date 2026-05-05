package com.example.myapplication
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import kotlin.math.abs
import androidx.core.graphics.scale
import androidx.core.graphics.get
import org.pytorch.Module
import org.pytorch.IValue
import org.pytorch.Tensor
class Classifier : Service(), ScreenRecord.VideoReadyCallback {
    lateinit var tflite: Interpreter


    lateinit var module: Module

    fun loadModel(context: Context) {
        module = Module.load(assetFilePath(context, "ffpp_model_mobile.pt"))

    }
    inner class LocalBinder : Binder() {
        fun getService(): Classifier = this@Classifier
    }

    private val binder = LocalBinder()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("APP", "Classification starting")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, NotificationHelper.processingNotification(this))

        loadModel(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onVideoReady(file: File) {
        val acc: Double

        /*
        val frames = extractFrames(file)

        val results = frames.map {classifyFrames(it)}
        val avgFake = results.map {it.second}.average()
        val avgReal = results.map {it.first}.average()
        */
        val frames = extractFrames(file)

// 🔥 NEW: clip-based inference
        val result = classifyClip(frames)

        val avgReal = result.first
        val avgFake = result.second

        val label = if (avgFake > 0.5) "Fake" else "Real"
        val confidence= abs(avgReal - avgFake)
        if(label== "Fake"){
            acc = avgFake.toDouble()
        }else{
            acc = avgReal.toDouble()
        }

        val prediction = Prediction(confidence.toDouble(), acc,label)
        Log.d("APP", "Classification ending")
        sendToUI(prediction)
        Log.d("APP", "Prediction is send to ui")
        Log.d("APP", "Prediction content $prediction")
    }




    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendToUI(prediction: Prediction){
        PipelineController.setPrediction(prediction)
        PipelineController.setResult()
    }


    //fun loadModel(context: Context) {
    //    val model = FileUtil.loadMappedFile(context, "xception_deepfake_float32.tflite")
     //   tflite = Interpreter(model)
    //}
/*
    fun classifyFrames(frameBitmap: Bitmap): Pair<Float, Float>{

        val input = preprocess(frameBitmap)
        val output = Array(1) { FloatArray(2) }  //

        //tflite.run(input, output)

        val real = output[0][0]
        val fake = output[0][1]

       return  real to fake
    }
*/
    fun extractFrames(videoSource: File): MutableList<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val file = videoSource.absolutePath
        retriever.setDataSource(file)

        val frames = mutableListOf<Bitmap>()
        val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()?.times(1000) ?:0L
        val step = durationUs / 5

        for(t in 0 until durationUs step step){
            retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)?.let {
             frames.add(it)
            }
        }
        retriever.release()

        return frames
    }



    fun classifyClip(frames: List<Bitmap>): Pair<Float, Float> {

        val inputTensor = preprocessClip(frames)

        val output = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = output.dataAsFloatArray

        val real = scores[0]
        val fake = scores[1]

        return real to fake
    }


    /*
    fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = bitmap.scale(224, 224)

        val input = Array(1) { Array(4) { Array(224) { FloatArray(3) } } }

        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized[x, y]

                input[0][y][x][0] = (pixel shr 16 and 0xFF) / 255f
                input[0][y][x][1] = (pixel shr 8 and 0xFF) / 255f
                input[0][y][x][2] = (pixel and 0xFF) / 255f
            }
        }

        return input
    }
*/


    fun preprocessClip(frames: List<Bitmap>): Tensor {

        val T = 12
        val C = 3
        val H = 112
        val W = 112

        val input = FloatArray(1 * T * C * H * W)

        for (t in 0 until T) {

            val bmp = frames[minOf(t, frames.size - 1)].scale(W, H)

            for (y in 0 until H) {
                for (x in 0 until W) {

                    val pixel = bmp[x, y]

                    val r = (pixel shr 16 and 0xFF) / 255f
                    val g = (pixel shr 8 and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f

                    val base = t * C * H * W + y * W + x

                    input[base + 0 * H * W] = r
                    input[base + 1 * H * W] = g
                    input[base + 2 * H * W] = b
                }
            }
        }

        return Tensor.fromBlob(input, longArrayOf(1, T.toLong(), 3, H.toLong(), W.toLong()))
    }

    fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        context.assets.open(assetName).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    override fun onBind(intent: Intent?): IBinder{
        return binder
    }


}