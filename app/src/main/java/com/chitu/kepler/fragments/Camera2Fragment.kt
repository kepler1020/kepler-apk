package com.chitu.kepler.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.window.WindowManager
import com.chitu.kepler.R
import com.chitu.kepler.client.AudioPlayCallback
import com.chitu.kepler.client.KeplerClient
import com.chitu.kepler.client.KeplerClient.Companion.MESSAGE_TYPE_VIDEO
import com.chitu.kepler.databinding.CameraUiContainerBinding
import com.chitu.kepler.databinding.FragmentCamera2Binding
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class Camera2Fragment : Fragment() {

    private var _fragmentCameraBinding: FragmentCamera2Binding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!
    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private lateinit var keplerClient: KeplerClient

    private var preview: Preview? = null
    private lateinit var imageAnalysis: ImageAnalysis

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var windowManager: WindowManager

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService


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

        keplerClient.stop()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCamera2Binding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize WindowManager to retrieve display metrics
        windowManager = WindowManager(view.context)

        keplerClient = KeplerClient(requireContext(), MESSAGE_TYPE_VIDEO).apply {
            init(object :
                AudioPlayCallback {
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


        // 初始化 ImageAnalysis
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        keplerClient.start(imageAnalysis)


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
                this, cameraSelector, preview, imageAnalysis
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
        cameraUiContainerBinding?.cameraCaptureButton?.isVisible = true
        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {
            keplerClient.stop()
        }
    }

    companion object {
        private const val TAG = "Kepler-Camera2"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}