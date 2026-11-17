package com.rajdeep.unidoc.feature.scanner.capture.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.rajdeep.unidoc.core.model.FlashMode
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Owns the CameraX use cases. Preview, analysis and capture all use 4:3 so the overlay maps 1:1
 * onto the viewfinder without crop math.
 */
class CameraController(private val context: Context) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null

    private val ratio43 = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

    private val preview = Preview.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio43).build())
        .build()
        .also { it.setSurfaceProvider { request -> _surfaceRequest.value = request } }

    private val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratio43)
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()

    private val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratio43)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()
        )
        .build()

    suspend fun bind(owner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer) {
        val p = ProcessCameraProvider.awaitInstance(context)
        provider = p
        analysis.setAnalyzer(analysisExecutor, analyzer)
        p.unbindAll()
        camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture)
    }

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun setFlash(mode: FlashMode) {
        val control = camera?.cameraControl ?: return
        when (mode) {
            FlashMode.TORCH -> {
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                control.enableTorch(true)
            }
            else -> {
                control.enableTorch(false)
                capture.flashMode = when (mode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            }
        }
    }

    suspend fun takePicture(dest: File): File = suspendCancellableCoroutine { cont ->
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(dest).build(),
            analysisExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    tagFocalLength(dest)
                    cont.resume(dest)
                }

                override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
            }
        )
    }

    /** 35 mm-equivalent focal length of the bound lens, from the sensor size; null if unknown. */
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun focal35mm(): Int? = runCatching {
        val info = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera!!.cameraInfo)
        val focal = info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )?.firstOrNull()
        val sensor = info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal == null || sensor == null || focal <= 0f) {
            null
        } else {
            (focal * 43.27 / kotlin.math.hypot(sensor.width.toDouble(), sensor.height.toDouble())).roundToInt().takeIf { it in 10..300 }
        }
    }.getOrNull()

    /**
     * Writes the 35 mm focal length into the photo's EXIF when the camera didn't: the perspective
     * correction uses it to recover the page's true proportions from a tilted shot.
     */
    private fun tagFocalLength(file: File) {
        runCatching {
            val exif = ExifInterface(file)
            if (exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) > 0) return
            val f35 = focal35mm() ?: return
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, f35.toString())
            exif.saveAttributes()
        }
    }

    fun release() {
        analysis.clearAnalyzer()
        provider?.unbindAll()
        analysisExecutor.shutdown()
    }
}
