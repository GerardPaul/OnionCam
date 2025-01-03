package com.thegpworx.onioncam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import com.thegpworx.onioncam.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.camera.core.ImageCaptureException
import java.text.SimpleDateFormat
import java.util.Locale

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var capturedImageView: ImageView
    private lateinit var alphaSeekBar: SeekBar
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var captureButton: ImageButton
    private var isCapturing = false // Flag to track capture status

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        capturedImageView = findViewById(R.id.capturedImageView)
        alphaSeekBar = findViewById(R.id.alphaSeekBar)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        captureButton = findViewById(R.id.captureButton)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.captureButton.setOnClickListener {
            if (!isCapturing) {
                takePhoto()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        // Disable the capture button while capturing
        captureButton.isEnabled = false

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Show progress bar
        loadingProgressBar.visibility = View.VISIBLE
        alphaSeekBar.visibility = View.INVISIBLE

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OnionCam")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    handleCaptureError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    handleImageSaved(output.savedUri)
                }
            }
        )
    }

    private fun handleCaptureError(exc: ImageCaptureException) {
        // Hide the loading ProgressBar
        loadingProgressBar.visibility = View.INVISIBLE

        // Re-enable the capture button
        captureButton.isEnabled = true

        // Notify the user of the error
        Toast.makeText(baseContext, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()

        isCapturing = false // Capture is complete or failed
    }

    private fun handleImageSaved(savedUri: Uri?) {
        if (savedUri == null) {
            Toast.makeText(baseContext, "Photo capture failed: Saved URI is null", Toast.LENGTH_SHORT).show()
            captureButton.isEnabled = true
            loadingProgressBar.visibility = View.INVISIBLE
            return
        }

        // Compress the image before saving to reduce file size
//        compressImage(savedUri, 30)

        // Resize logic
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            resizeAndSaveImage(savedUri, maxWidth = 1920, maxHeight = 1080)
//        } else {
//            resizeAndSaveImageLegacy(savedUri, maxWidth = 1920, maxHeight = 1080)
//        }

        // Hide the loading ProgressBar
        loadingProgressBar.visibility = View.INVISIBLE

        // Show the captured image
        capturedImageView.apply {
            setImageURI(savedUri)
            visibility = View.VISIBLE
        }

        // Show and configure the alpha SeekBar
        alphaSeekBar.visibility = View.VISIBLE
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Update the alpha value of the ImageView based on SeekBar progress
                capturedImageView.alpha = progress.toFloat() / 100.0f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // No action needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // No action needed
            }
        })

        isCapturing = false // Capture is complete
        // Re-enable the capture button
        captureButton.isEnabled = true
    }

    private fun compressImage(uri: Uri, quality: Int) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for Android P and above
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                // Use BitmapFactory for older Android versions
                val inputStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream).also {
                    inputStream?.close()
                }
            }

            // Open the OutputStream for the given URI
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Compress the bitmap with the desired quality (0-100)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(baseContext, "Failed to compress image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeAndSaveImage(uri: Uri, maxWidth: Int, maxHeight: Int) {
        try {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.createSource(contentResolver, uri)
            } else {
                return
            }

            // Decode the image source into a Bitmap
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // Adjust the sample size to avoid memory issues with large images
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }

            // Calculate the aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Determine new dimensions while maintaining the aspect ratio
            val newWidth: Int
            val newHeight: Int

            if (bitmap.width > bitmap.height) {
                newWidth = maxWidth
                newHeight = (maxWidth / aspectRatio).toInt()
            } else {
                newHeight = maxHeight
                newWidth = (maxHeight * aspectRatio).toInt()
            }

            // Resize the bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            // Open the OutputStream for the given URI
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Compress and save the resized bitmap to the same URI
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(baseContext, "Failed to resize and save image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeAndSaveImageLegacy(uri: Uri, maxWidth: Int, maxHeight: Int) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Calculate the aspect ratio
            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // Determine new dimensions while maintaining the aspect ratio
            val newWidth: Int
            val newHeight: Int

            if (bitmap.width > bitmap.height) {
                newWidth = maxWidth
                newHeight = (maxWidth / aspectRatio).toInt()
            } else {
                newHeight = maxHeight
                newWidth = (maxHeight * aspectRatio).toInt()
            }

            // Resize the bitmap
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            // Open the OutputStream for the given URI
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Compress and save the resized bitmap to the same URI
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(baseContext, "Failed to resize and save image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Toast.makeText(baseContext,
                    "Failed to start camera: ${exc.message}",
                    Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "OnionCam"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}