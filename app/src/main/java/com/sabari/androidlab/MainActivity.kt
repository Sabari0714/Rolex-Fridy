package com.sabari.androidlab

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var recordButton: Button
    private lateinit var playButton: Button
    private lateinit var statusText: TextView
    private var imageCapture: ImageCapture? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var latestAudio: File? = null
    private var recording = false

    companion object {
        private const val REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        recordButton = findViewById(R.id.recordButton)
        playButton = findViewById(R.id.playButton)
        statusText = findViewById(R.id.statusText)

        captureButton.setOnClickListener { takePhoto() }
        recordButton.setOnClickListener { if (recording) stopRecording() else startRecording() }
        playButton.setOnClickListener { playRecording() }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.CAMERA
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isEmpty()) startCamera() else ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                statusText.text = "Camera permission is required for preview and capture."
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                statusText.text = "Camera ready."
            } catch (e: Exception) {
                statusText.text = "Camera start failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val dir = File(filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                statusText.text = "Photo saved locally."
            }
            override fun onError(exception: ImageCaptureException) {
                statusText.text = "Photo failed: ${exception.message}"
            }
        })
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
            return
        }
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "recording_${System.currentTimeMillis()}.m4a")
        try {
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            latestAudio = file
            recording = true
            recordButton.text = "Stop"
            statusText.text = "Recording..."
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            statusText.text = "Recording failed: ${e.message}"
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
            latestAudio?.let { statusText.text = "Audio saved locally." }
        } catch (e: RuntimeException) {
            latestAudio?.delete()
            statusText.text = "Recording was too short."
        } finally {
            recorder?.release()
            recorder = null
            recording = false
            recordButton.text = "Record"
        }
    }

    private fun playRecording() {
        val file = latestAudio
        if (file == null || !file.exists()) {
            statusText.text = "No recording available."
            return
        }
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            setOnCompletionListener { statusText.text = "Playback complete." }
            start()
        }
        statusText.text = "Playing recording..."
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        player?.release()
        super.onDestroy()
    }
}
