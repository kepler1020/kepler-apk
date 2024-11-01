/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chitu.kepler.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import androidx.window.WindowManager
import com.chitu.kepler.KEY_EVENT_EXTRA
import com.chitu.kepler.R
import com.chitu.kepler.client.AudioPlayCallback
import com.chitu.kepler.client.KeplerClient
import com.chitu.kepler.client.KeplerClient.Companion.MESSAGE_TYPE_IMAGE
import com.chitu.kepler.databinding.CameraUiContainerBinding
import com.chitu.kepler.databinding.FragmentCamera1Binding
import com.chitu.kepler.utils.simulateClick
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class Camera1Fragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCamera1Binding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!
    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private lateinit var broadcastManager: LocalBroadcastManager

    private lateinit var keplerClient: KeplerClient

    private var displayId: Int = -1
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    // private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var windowManager: WindowManager

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    cameraUiContainerBinding?.cameraCaptureButton?.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@Camera1Fragment.displayId) {
                Log.i(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                // imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                Camera1FragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)

        keplerClient.stop()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCamera1Binding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        // val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        // broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        // Initialize WindowManager to retrieve display metrics
        windowManager = WindowManager(view.context)

        keplerClient = KeplerClient(requireContext(), MESSAGE_TYPE_IMAGE).apply {
            init(object : AudioPlayCallback {
                override fun start() {
                    lifecycleScope.launch {
                        cameraUiContainerBinding?.audioPlayIcon?.isVisible = true
                    }
                }

                override fun stop() {
                    lifecycleScope.launch {
                        cameraUiContainerBinding?.audioPlayIcon?.isVisible = false
                    }
                }
            })
        }

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = fragmentCameraBinding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            lifecycleScope.launch {
                setUpCamera()
            }
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        bindCameraUseCases()

    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private suspend fun setUpCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

        // Build and bind the camera use cases
        bindCameraUseCases()
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = windowManager.getCurrentWindowMetrics().bounds
        Log.i(TAG, "Screen metrics: ${metrics.width()} x ${metrics.height()}")

        val screenAspectRatio = aspectRatio(metrics.width(), metrics.height())
        Log.i(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = fragmentCameraBinding.viewFinder.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // We request aspect ratio but no resolution to match preview config, but letting
            // CameraX optimize for whatever specific resolution best fits our use cases
            // .setTargetAspectRatio(screenAspectRatio)
            .setTargetResolution(Size(1080, 1920))
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            .setTargetRotation(rotation)
            .build()
        keplerClient.start(imageCapture)

        // ImageAnalysis
        // imageAnalyzer = ImageAnalysis.Builder()
        //     // We request aspect ratio but no resolution
        //     .setTargetAspectRatio(screenAspectRatio)
        //     // Set initial target rotation, we will have to call this again if rotation changes
        //     // during the lifecycle of this use case
        //     .setTargetRotation(rotation)
        //     .build()
        //     // The analyzer can then be assigned to the instance
        //     .also {
        //         it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
        //             // Values returned from our analyzer are passed to the attached listener
        //             // We log image analysis results here - you should do something useful
        //             // instead!
        //             Log.i(TAG, "Average luminosity: $luma")
        //         })
        //     }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        if (camera != null) {
            // Must remove observers from the previous camera instance
            removeCameraStateObservers(camera!!.cameraInfo)
        }

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            // camera = cameraProvider.bindToLifecycle(
            //     this, cameraSelector, preview, imageCapture, imageAnalyzer
            // )
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun removeCameraStateObservers(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.removeObservers(viewLifecycleOwner)
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        // Toast.makeText(context, "CameraState: Pending Open", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "CameraState: Pending Open")
                    }

                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        // Toast.makeText(context, "CameraState: Opening", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "CameraState: Opening")
                    }

                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        // Toast.makeText(context, "CameraState: Open", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "CameraState: Open")
                    }

                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        // Toast.makeText(context, "CameraState: Closing", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "CameraState: Closing")
                    }

                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        // Toast.makeText(context, "CameraState: Closed", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "CameraState: Closed")
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        // Toast.makeText(context, "Stream config error", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Stream config error")
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        // Toast.makeText(context, "Camera in use", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Camera in use")
                    }

                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        // Toast.makeText(context, "Max cameras in use", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Max cameras in use")
                    }

                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        // Toast.makeText(context, "Other recoverable error", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Other recoverable error")
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        // Toast.makeText(context, "Camera disabled", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Camera disabled")
                    }

                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        // Toast.makeText(context, "Fatal error", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Fatal error")
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        // Toast.makeText(context, "Do not disturb mode enabled", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Do not disturb mode enabled")
                    }
                }
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysis.Builder] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    @SuppressLint("ClickableViewAccessibility")
    private fun updateCameraUi() {

        // Remove previous UI if any
        cameraUiContainerBinding?.root?.let {
            fragmentCameraBinding.root.removeView(it)
        }

        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
            LayoutInflater.from(requireContext()),
            fragmentCameraBinding.root,
            true
        )

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch {
            // val thumbnailUri = mediaStoreUtils.getLatestImageFilename()
            // thumbnailUri?.let {
            //     // setGalleryThumbnail(it)
            // }
        }

        // Listener for button used to capture photo
        cameraUiContainerBinding?.audioPlayIcon?.isVisible = false
        cameraUiContainerBinding?.cameraCaptureButton?.isVisible = false
        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {
            // imgCaptureClient.takePicture(imageCapture)
        }
    }

    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    // private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
    //     private val frameRateWindow = 8
    //     private val frameTimestamps = ArrayDeque<Long>(5)
    //     private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
    //     private var lastAnalyzedTimestamp = 0L
    //     var framesPerSecond: Double = -1.0
    //         private set
    //
    //     /**
    //      * Helper extension function used to extract a byte array from an image plane buffer
    //      */
    //     private fun ByteBuffer.toByteArray(): ByteArray {
    //         rewind()    // Rewind the buffer to zero
    //         val data = ByteArray(remaining())
    //         get(data)   // Copy the buffer into a byte array
    //         return data // Return the byte array
    //     }
    //
    //     /**
    //      * Analyzes an image to produce a result.
    //      *
    //      * <p>The caller is responsible for ensuring this analysis method can be executed quickly
    //      * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
    //      * images will not be acquired and analyzed.
    //      *
    //      * <p>The image passed to this method becomes invalid after this method returns. The caller
    //      * should not store external references to this image, as these references will become
    //      * invalid.
    //      *
    //      * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
    //      * call image.close() on received images when finished using them. Otherwise, new images
    //      * may not be received or the camera may stall, depending on back pressure setting.
    //      *
    //      */
    //     override fun analyze(image: ImageProxy) {
    //         // If there are no listeners attached, we don't need to perform analysis
    //         if (listeners.isEmpty()) {
    //             image.close()
    //             return
    //         }
    //
    //         // Keep track of frames analyzed
    //         val currentTime = System.currentTimeMillis()
    //         frameTimestamps.push(currentTime)
    //
    //         // Compute the FPS using a moving average
    //         while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
    //         val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
    //         val timestampLast = frameTimestamps.peekLast() ?: currentTime
    //         framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
    //                 frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0
    //
    //         // Analysis could take an arbitrarily long amount of time
    //         // Since we are running in a different thread, it won't stall other use cases
    //
    //         lastAnalyzedTimestamp = frameTimestamps.first
    //
    //         // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
    //         val buffer = image.planes[0].buffer
    //
    //         // Extract image data from callback object
    //         val data = buffer.toByteArray()
    //
    //         // Convert the data into an array of pixel values ranging 0-255
    //         val pixels = data.map { it.toInt() and 0xFF }
    //
    //         // Compute average luminance for the image
    //         val luma = pixels.average()
    //
    //         // Call all listeners with new value
    //         listeners.forEach { it(luma) }
    //
    //         image.close()
    //     }
    // }

    companion object {
        private const val TAG = "Kepler-Camera1"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}
