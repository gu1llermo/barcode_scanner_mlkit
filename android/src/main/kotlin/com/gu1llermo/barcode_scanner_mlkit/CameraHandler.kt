package com.gu1llermo.barcode_scanner_mlkit

import android.view.OrientationEventListener
import android.graphics.ImageFormat
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.view.TextureRegistry
import java.util.concurrent.Executors

class CameraHandler(
    private val context: Context,
    private val activity: Activity,
    private val textureRegistry: TextureRegistry,
    private val methodChannel: MethodChannel
) {
    private val tagCameraHandler = "CameraHandler"
    private val tagOrientation = "OrientationDebug"

    // private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val cameraExecutor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var flutterTexture: TextureRegistry.SurfaceTextureEntry? = null
    private var scanner: BarcodeScanner? = null
    private var isPaused = false
    private var flashEnabled = false

    private var lastAnalysisTime = 0L
    private val minAnalysisInterval = 200
    private val pauseAfterDetectionTime = 1000L

    private var orientationEventListener: OrientationEventListener? = null
    private var lastKnownRotation = Surface.ROTATION_0

    @SuppressLint("UnsafeOptInUsageError")
    fun initializeCamera(options: Map<String, Any>?, result: Result) {
        try {
            flutterTexture = textureRegistry.createSurfaceTexture()
            val textureId = flutterTexture?.id() ?: -1

            // Configurar scanner
            scanner = BarcodeScanning.getClient(buildScannerOptions(options))

            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProvider = cameraProviderFuture.get()

                        // Extraer resolución personalizada de las opciones
//                            val resolution = extractResolution(options)

                        // CameraX ya maneja automáticamente las orientaciones
//                            preview = Preview.Builder().setTargetResolution(resolution).build()
                        preview = Preview.Builder().build()

                        // Usar la superficie de Flutter
                        preview?.setSurfaceProvider { request ->
                            val texture = flutterTexture?.surfaceTexture()
                            texture?.setDefaultBufferSize(
                                request.resolution.width,
                                request.resolution.height
                            )
                            val surface = Surface(texture!!)
                            request.provideSurface(surface, cameraExecutor) {
                                surface.release()
                            }
                        }

                        // Configurar análisis de imágenes
                        imageAnalysis =
                            ImageAnalysis.Builder()
//                                            .setTargetResolution(resolution)
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!isPaused) {
                                processImageProxy(imageProxy)
                            } else {
                                imageProxy.close()
                            }
                        }

                        // Cámara trasera
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        // Vincular los casos de uso
                        cameraProvider?.unbindAll()
                        camera =
                            cameraProvider?.bindToLifecycle(
                                activity as LifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )

                        // Configurar flash y enfoque
                        camera?.cameraControl?.enableTorch(flashEnabled)
                        setupImprovedFocus()

                        result.success(mapOf("textureId" to textureId))
                        //setupOrientationListener()
                    } catch (e: Exception) {
                        Log.e(tagCameraHandler, "Error al inicializar la cámara", e)
                        result.error(
                            "CAMERA_ERROR",
                            "Error al inicializar la cámara",
                            e.message
                        )
                    }
                },
                ContextCompat.getMainExecutor(context)
            )

        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error general al inicializar la cámara", e)
            result.error("INIT_ERROR", "Error general al inicializar la cámara", e.message)
        }
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                // Log para depuración de orientación
                //Log.d(TAG_ORIENTATION, "Orientación actual: $orientation")

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return

                // When orientation changes, determine the current rotation
                val currentRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
//                val currentRotation = when {
//                    orientation <= 45 || orientation > 315 -> Surface.ROTATION_0
//                    orientation <= 135 -> Surface.ROTATION_90
//                    orientation <= 225 -> Surface.ROTATION_180
//                    else -> Surface.ROTATION_270
//                }

                // Only update if needed to avoid flickering
                if (currentRotation != lastKnownRotation) {
                    lastKnownRotation = currentRotation
                    Log.d(tagOrientation, "Orientación actual: $orientation")
                    Log.d(tagOrientation, "restartPreviewIfNeeded: $currentRotation")
                    // Restart camera preview if necessary
                    restartPreviewIfNeeded()

                }
            }
        }

        if (orientationEventListener?.canDetectOrientation() == true) {
            orientationEventListener?.enable()
        }
    }

    private fun restartPreviewIfNeeded() {
        try {
//            Log.d(TAG_ORIENTATION, "Entró aquí")
//
//
//            //Log.d(TAG_ORIENTATION, "actualicé el texture")
//
//            camera?.cameraControl?.cancelFocusAndMetering()
//
//            // Unbind and rebind to update rotation
//            cameraProvider?.let { provider ->
//                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//                // Configurar el análisis de imágenes con la orientación actualizada
//                // Actualizar rotación para el preview y el análisis de imágenes
////                val rotation = when (lastKnownRotation) {
////                    Surface.ROTATION_0 -> 0
////                    Surface.ROTATION_90 -> 90
////                    Surface.ROTATION_180 -> 180
////                    Surface.ROTATION_270 -> 270
////                    else -> 0
////                }
//
//                // Recrear la configuración con la nueva resolución
//                //val resolution = extractResolution(null) // O pasar las opciones guardadas
//
//                preview?.setSurfaceProvider { request ->
//                    val texture = flutterTexture?.surfaceTexture()
//                    texture?.setDefaultBufferSize(
//                        request.resolution.width,
//                        request.resolution.height
//                    )
//                    val surface = Surface(texture!!)
//                    request.provideSurface(surface, cameraExecutor) {
//                        surface.release()
//                    }
//                }
//
////                preview?.targetTotation = rotation
////                imageAnalysis?.targetTotation = rotation
//
//                // Unbind all use cases
//                provider.unbindAll()
//
//                // Rebind with current orientation
//                camera = provider.bindToLifecycle(
//                    activity as LifecycleOwner,
//                    cameraSelector,
//                    preview,
//                    imageAnalysis
//                )
//
//                // Reset flash and focus
//                camera?.cameraControl?.enableTorch(flashEnabled)
//                setupImprovedFocus()
//                // Log para depuración
//                Log.d(TAG_ORIENTATION, "Preview reiniciado")
            // }
        } catch (e: Exception) {
            Log.e(tagOrientation, "Error restarting preview", e)
        }
    }


    // Método para extraer resolución de las opciones o seleccionar una óptima
    private fun extractResolution(options: Map<String, Any>?): Size {
        // Por defecto, resolución HD
        var width = 1280
        var height = 720

        try {
            // Intentar obtener resolución de las opciones
            if (options != null) {
                val resolutionMap = options["resolution"] as? Map<String, Int>
                if (resolutionMap != null) {
                    val requestedWidth = resolutionMap["width"]
                    val requestedHeight = resolutionMap["height"]

                    if (requestedWidth != null && requestedHeight != null) {
                        width = requestedWidth
                        height = requestedHeight
                        return Size(width, height)
                    }
                }

                // O por nivel de calidad
                val quality = options["quality"] as? String
                if (quality != null) {
                    return when (quality) {
                        "max" -> getBestResolution()
                        "high" -> Size(1920, 1080)
                        "medium" -> Size(1280, 720)
                        "low" -> Size(640, 480)
                        else -> Size(1280, 720)
                    }
                }
            }

            // Si no hay opciones específicas, seleccionar una resolución óptima
            // basada en las características del dispositivo
            return selectOptimalResolution()
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al extraer resolución, usando valor predeterminado", e)
            return Size(width, height)
        }
    }

    // Determinar la mejor resolución para el dispositivo actual
    private fun selectOptimalResolution(): Size {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Ajustar según la orientación de la pantalla
        val isPortrait =
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // Obtener la mejor resolución que no exceda el tamaño de la pantalla
        // pero mantenga una buena calidad para el escaneo de códigos
        return if (isPortrait) {
            // En modo portrait, altura > anchura
            val targetHeight = screenHeight
            val targetWidth = screenWidth

            when {
                targetHeight >= 1080 -> Size(1080, 1920)
                targetHeight >= 720 -> Size(720, 1280)
                else -> Size(480, 640)
            }
        } else {
            // En modo landscape, anchura > altura
            val targetWidth = screenWidth
            val targetHeight = screenHeight

            when {
                targetWidth >= 1920 -> Size(1920, 1080)
                targetWidth >= 1280 -> Size(1280, 720)
                else -> Size(640, 480)
            }
        }
    }

    private fun getCorrectImageRotation(rotationDegrees: Int): Int {
        val displayRotation = activity.windowManager.defaultDisplay.rotation

        return when (displayRotation) {
            Surface.ROTATION_0 -> rotationDegrees
            Surface.ROTATION_90 -> (rotationDegrees + 270) % 360
            Surface.ROTATION_180 -> (rotationDegrees + 180) % 360
            Surface.ROTATION_270 -> (rotationDegrees + 90) % 360
            else -> rotationDegrees
        }
    }

    // Obtener la mejor resolución disponible en la cámara
    private fun getBestResolution(): Size {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0] // Asumimos cámara trasera
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val streamConfigurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            if (streamConfigurationMap != null) {
                // Obtener resoluciones disponibles y encontrar la mejor
                val availableSizes =
                    streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)
                        ?: return Size(1920, 1080)

                // Ordenar por área de píxeles (mayor primero)
                return availableSizes.sortedByDescending { it.width * it.height }.firstOrNull {
                    it.width <= 1920 && it.height <= 1080
                }
                    ?: availableSizes.first()
            }
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al obtener mejor resolución", e)
        }

        return Size(1920, 1080) // Valor por defecto
    }

    // Mejora: Configuración de enfoque avanzada específica para códigos de barras
    private fun setupImprovedFocus() {
        try {
            // 1. Cancelar cualquier configuración previa
            camera?.cameraControl?.cancelFocusAndMetering()

            // 2. Configurar el modo de enfoque automático continuo con prioridad para objetos
            // cercanos
            val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)

            // 3. Configurar zona de enfoque más amplia, cubriendo el área central donde suelen
            // estar los códigos
            val centerPoint = factory.createPoint(0.5f, 0.5f)
            val topPoint = factory.createPoint(0.5f, 0.3f)
            val bottomPoint = factory.createPoint(0.5f, 0.7f)
            val leftPoint = factory.createPoint(0.3f, 0.5f)
            val rightPoint = factory.createPoint(0.7f, 0.5f)

            // 4. Crear acción de enfoque mejorada con renovación automática cada 3 segundos
            val focusAction =
                FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
                    .addPoint(topPoint, FocusMeteringAction.FLAG_AF)
                    .addPoint(bottomPoint, FocusMeteringAction.FLAG_AF)
                    .addPoint(leftPoint, FocusMeteringAction.FLAG_AF)
                    .addPoint(rightPoint, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

            // 5. Aplicar la configuración de enfoque
            camera?.cameraControl
                ?.startFocusAndMetering(focusAction)
                ?.addListener(
                    {
                        // 6. Configurar enfoque periódico para mantenerlo actualizado
                        setupPeriodicFocus()
                    },
                    ContextCompat.getMainExecutor(context)
                )

            // 7. Establecer el rango de distancia de enfoque óptimo para códigos de barras
            setCameraDistance()
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al configurar el enfoque mejorado", e)
        }
    }

    // Nuevo método: Configurar enfoque periódico para mantener imagen nítida
    private var focusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var periodicFocusRunnable: Runnable? = null

    private fun setupPeriodicFocus() {
        // Cancelar cualquier enfoque periódico anterior
        periodicFocusRunnable?.let { focusHandler.removeCallbacks(it) }

        // Crear nuevo runnable para enfoque periódico
        periodicFocusRunnable = Runnable {
            if (camera != null && !isPaused) {
                try {
                    // Trigger enfoque automático cada 2.5 segundos
                    val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
                    val centerPoint = factory.createPoint(0.5f, 0.5f)

                    val focusAction =
                        FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                            .build()

                    camera?.cameraControl?.startFocusAndMetering(focusAction)

                    // Programar el próximo enfoque
                    focusHandler.postDelayed(periodicFocusRunnable!!, 2500)
                } catch (e: Exception) {
                    Log.e(tagCameraHandler, "Error en enfoque periódico", e)
                }
            }
        }

        // Iniciar enfoque periódico
        focusHandler.postDelayed(periodicFocusRunnable!!, 2500)
    }

    // Nuevo método: Configurar la distancia de enfoque óptima para códigos de barras
    private fun setCameraDistance() {
        try {
            // Get the camera manager
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Determine which camera we're using (assuming back camera by default)
            val cameraSelector = cameraProvider?.availableCameraInfos?.firstOrNull()?.cameraSelector
            val isBackCamera = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA

            // Find the appropriate camera ID
            var cameraId: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if ((isBackCamera && facing == CameraCharacteristics.LENS_FACING_BACK) ||
                    (!isBackCamera && facing == CameraCharacteristics.LENS_FACING_FRONT)) {
                    cameraId = id
                    break
                }
            }

            if (cameraId != null) {
                // Configure minimum focus distance (macro mode)
                camera?.cameraControl?.setLinearZoom(0.1f) // Minimum zoom for wide field of view

                // Try to set manual focus if available
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                if (minFocusDistance != null && minFocusDistance > 0) {
                    // If the camera supports manual focus, set it to an optimal distance
                    // for barcodes (approximately 20-30cm)
                    val optimalFocusDistance = minFocusDistance * 0.7f // 70% of the range
                }
            }
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error setting focus distance", e)
        }
    }

    // Nuevo método: Implementar un método para permitir enfoque manual al tocar la pantalla
    fun onTouchToFocus(x: Float, y: Float) {
        try {
            val factory = SurfaceOrientedMeteringPointFactory(1.0f, 1.0f)
            val point = factory.createPoint(x, y)

            // Crear acción de enfoque en el punto tocado con prioridad y duración extendida
            val focusAction =
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

            camera?.cameraControl
                ?.startFocusAndMetering(focusAction)
                ?.addListener(
                    { Log.d(tagCameraHandler, "Enfoque manual aplicado en ($x, $y)") },
                    ContextCompat.getMainExecutor(context)
                )
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al aplicar enfoque manual", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Aplicar límite de frecuencia para análisis
        if (currentTime - lastAnalysisTime < minAnalysisInterval) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = currentTime

        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Get the original rotation from the image
                val originalRotation = imageProxy.imageInfo.rotationDegrees
                // Get the corrected rotation based on device orientation
                //val correctedRotation = getCorrectImageRotation(originalRotation)

                // Use the corrected rotation for InputImage
                val image = InputImage.fromMediaImage(mediaImage, originalRotation)

                val task =
                    scanner?.process(image)
                        ?.addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val results =
                                    barcodes
                                        // filtra los valores vacíos
                                        .filter {
                                            it.rawValue != null && it.rawValue != ""
                                        }
                                        .map { barcode ->
                                            val value = barcode.rawValue ?: ""
                                            val format =
                                                getBarcodeFormatString(
                                                    barcode.format
                                                )

                                            mapOf(
                                                "value" to value,
                                                "format" to format,
                                                "cornerPoints" to
                                                        getCornerPoints(barcode)
                                            )
                                        }

                                if (results.isNotEmpty()) {
                                    activity.runOnUiThread {
                                        methodChannel.invokeMethod(
                                            "onBarcodeDetected",
                                            mapOf("barcodes" to results)
                                        )
                                    }
                                    // Hacer una pausa después de detectar un código para
                                    // evitar lecturas múltiples
                                    pauseTemporarily()
                                }
                            }
                        }
                        ?.addOnFailureListener { exception ->
                            Log.e(tagCameraHandler, "Error scanning barcode", exception)
                        }
                        ?.addOnCompleteListener { imageProxy.close() }

                // Si el task es null, necesitamos cerrar manualmente el imageProxy
                if (task == null) {
                    imageProxy.close()
                }
            } else {
                Log.w(tagCameraHandler, "La imagen es nula, cerrando imageProxy")
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error processing image proxy", e)
            imageProxy.close()
        }
    }

    // Mejora: Pausa más larga después de una detección exitosa
    private fun pauseTemporarily() {
        isPaused = true
        // Reanudar después de un intervalo más largo
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ isPaused = false }, pauseAfterDetectionTime)
    }

    private fun buildScannerOptions(options: Map<String, Any>?): BarcodeScannerOptions {
        // Mejora: Opciones de escáner optimizadas
        val optionsBuilder =
            BarcodeScannerOptions.Builder()
                .enableAllPotentialBarcodes() // Mejora para detectar todos los formatos
        // posibles

        if (options != null) {
            val formats = options["formats"] as? List<*>
            if (formats != null && formats.isNotEmpty()) {
                val barcodeFormats = getBarcodeFormats(formats)
                if (barcodeFormats.isNotEmpty()) {
                    optionsBuilder.setBarcodeFormats(
                        barcodeFormats.first(),
                        *barcodeFormats.drop(1).toIntArray()
                    )
                }
            } else {
                // Si no se especifican formatos, habilitar todos
                optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            }
        } else {
            optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        }

        return optionsBuilder.build()
    }

    private fun BarcodeScannerOptions.Builder.enableAllPotentialBarcodes(): BarcodeScannerOptions.Builder {
        // Configurar todos los formatos disponibles
        setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)

        // Habilitar el modo de escaneo de alta precisión 
        // (si está disponible en la versión de ML Kit que usas)
        // Algunas versiones de ML Kit tienen esta opción
        // enableHighPrecisionMode(true) 

        return this
    }

    private fun getBarcodeFormats(formatNames: List<*>): List<Int> {
        val formats = mutableListOf<Int>()

        formatNames.forEach { formatName ->
            when (formatName) {
                // Formatos 1D
                "code128" -> formats.add(Barcode.FORMAT_CODE_128)
                "code39" -> formats.add(Barcode.FORMAT_CODE_39)
                "code93" -> formats.add(Barcode.FORMAT_CODE_93)
                "codabar" -> formats.add(Barcode.FORMAT_CODABAR)
                "ean13" -> formats.add(Barcode.FORMAT_EAN_13)
                "ean8" -> formats.add(Barcode.FORMAT_EAN_8)
                "itf" -> formats.add(Barcode.FORMAT_ITF)
                "upcA" -> formats.add(Barcode.FORMAT_UPC_A)
                "upcE" -> formats.add(Barcode.FORMAT_UPC_E)

                // Formatos 2D
                "qrCode" -> formats.add(Barcode.FORMAT_QR_CODE)
                "dataMatrix" -> formats.add(Barcode.FORMAT_DATA_MATRIX)
                "pdf417" -> formats.add(Barcode.FORMAT_PDF417)
                "aztec" -> formats.add(Barcode.FORMAT_AZTEC)

                // Grupos de formatos
                "all" -> formats.add(Barcode.FORMAT_ALL_FORMATS)
                "all1D" -> formats.add(
                    Barcode.FORMAT_CODE_128 or
                            Barcode.FORMAT_CODE_39 or
                            Barcode.FORMAT_CODE_93 or
                            Barcode.FORMAT_CODABAR or
                            Barcode.FORMAT_EAN_13 or
                            Barcode.FORMAT_EAN_8 or
                            Barcode.FORMAT_ITF or
                            Barcode.FORMAT_UPC_A or
                            Barcode.FORMAT_UPC_E
                )

                "all2D" -> formats.add(
                    Barcode.FORMAT_QR_CODE or
                            Barcode.FORMAT_DATA_MATRIX or
                            Barcode.FORMAT_PDF417 or
                            Barcode.FORMAT_AZTEC
                )

                // Detectar automáticamente el formato (ML Kit seleccionará)
                "auto" -> formats.add(Barcode.FORMAT_ALL_FORMATS)
            }
        }

        // Si no se especificó un formato válido, usar todos
        return if (formats.isEmpty()) listOf(Barcode.FORMAT_ALL_FORMATS) else formats
    }

    private fun getBarcodeFormatString(format: Int): String {
        return when (format) {
            // Formatos 1D
            Barcode.FORMAT_CODE_128 -> "code128"
            Barcode.FORMAT_CODE_39 -> "code39"
            Barcode.FORMAT_CODE_93 -> "code93"
            Barcode.FORMAT_CODABAR -> "codabar"
            Barcode.FORMAT_EAN_13 -> "ean13"
            Barcode.FORMAT_EAN_8 -> "ean8"
            Barcode.FORMAT_ITF -> "itf"
            Barcode.FORMAT_UPC_A -> "upcA"
            Barcode.FORMAT_UPC_E -> "upcE"

            // Formatos 2D
            Barcode.FORMAT_QR_CODE -> "qrCode"
            Barcode.FORMAT_DATA_MATRIX -> "dataMatrix"
            Barcode.FORMAT_PDF417 -> "pdf417"
            Barcode.FORMAT_AZTEC -> "aztec"

            // Para cualquier otro formato no mapeado
            else -> "unknown"
        }
    }

    private fun getCornerPoints(barcode: Barcode): List<Map<String, Int>> {
        val points = mutableListOf<Map<String, Int>>()
        barcode.cornerPoints?.forEach { point -> points.add(mapOf("x" to point.x, "y" to point.y)) }
        return points
    }

    fun toggleFlash(result: Result) {
        try {
            flashEnabled = !flashEnabled
            camera?.cameraControl?.enableTorch(flashEnabled)
            result.success(flashEnabled)
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al cambiar el flash", e)
            result.error("FLASH_ERROR", "Error al cambiar el flash", e.message)
        }
    }

    fun updateScannerOptions(options: Map<String, Any>?, result: Result) {
        try {
            scanner?.close()
            scanner = BarcodeScanning.getClient(buildScannerOptions(options))
            result.success(true)
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al actualizar opciones", e)
            result.error("OPTIONS_ERROR", "Error al actualizar opciones", e.message)
        }
    }

    fun pauseScanning() {
        isPaused = true
    }

    fun resumeScanning() {
        isPaused = false
    }

    fun dispose() {
        try {
            isPaused = true

            // Disable orientation listener
            orientationEventListener?.disable()
            orientationEventListener = null

            // Detener el enfoque periódico
            periodicFocusRunnable?.let { focusHandler.removeCallbacks(it) }
            periodicFocusRunnable = null

            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
            scanner?.close()
            flutterTexture?.release()

            camera = null
            preview = null
            imageAnalysis = null
            cameraProvider = null
            scanner = null
            flutterTexture = null
        } catch (e: Exception) {
            Log.e(tagCameraHandler, "Error al liberar recursos", e)
        }
    }
}
