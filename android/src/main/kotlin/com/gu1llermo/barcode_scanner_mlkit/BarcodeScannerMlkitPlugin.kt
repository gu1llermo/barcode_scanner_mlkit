package com.gu1llermo.barcode_scanner_mlkit

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.flutter.view.TextureRegistry
import java.io.File
import androidx.core.net.toUri

class BarcodeScannerMlkitPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var channel: MethodChannel
  private lateinit var cameraChannel: MethodChannel
  private lateinit var context: Context
  private lateinit var activity: Activity
  private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null
  private var scanner: BarcodeScanner? = null
  private var cameraHandler: CameraHandler? = null

  override fun onAttachedToEngine( flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    this.flutterPluginBinding = flutterPluginBinding
    context = flutterPluginBinding.applicationContext

    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_mlkit")
    cameraChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "barcode_scanner_mlkit/camera")

    channel.setMethodCallHandler(this)
    cameraChannel.setMethodCallHandler(CameraMethodHandler())
  }

  override fun onMethodCall( call: MethodCall, result: Result) {
    when (call.method) {
      "initialize" -> {
        try {
          // Configurar opciones de escaneo por defecto (todos los formatos)
          val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
          scanner = BarcodeScanning.getClient(options)
          result.success(true)
        } catch (e: Exception) {
          result.error("INIT_ERROR", "Error al inicializar el escáner", e.message)
        }
      }
      "scanFromImage" -> {
        val imagePath = call.argument<String>("imagePath")
        val options = call.argument<Map<String, Any>>("options")

        if (imagePath != null) {
          try {
            // Configurar opciones de escaneo
            scanner?.close()
            scanner = BarcodeScanning.getClient(buildScannerOptions(options))

            // Obtener la imagen desde la ruta
            val image = getInputImageFromPath(imagePath)

            // Procesar la imagen
            scanner?.process(image)
              ?.addOnSuccessListener { barcodes ->
                val results = barcodes.map { barcode ->
                  mapOf(
                    "value" to (barcode.rawValue ?: ""),
                    "format" to getBarcodeFormatString(barcode.format),
                    "cornerPoints" to getCornerPoints(barcode)
                  )
                }
                result.success(results)
              }
              ?.addOnFailureListener { e ->
                result.error("SCAN_ERROR", "Error al escanear imagen", e.message)
              }
          } catch (e: Exception) {
            result.error("IMAGE_ERROR", "Error al procesar la imagen", e.message)
          }
        } else {
          result.error("INVALID_ARGS", "Ruta de imagen no proporcionada", null)
        }
      }
      "dispose" -> {
        scanner?.close()
        scanner = null
        result.success(null)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun buildScannerOptions(options: Map<String, Any>?): BarcodeScannerOptions {
    val optionsBuilder = BarcodeScannerOptions.Builder()

    if (options != null) {
      val formats = options["formats"] as? List<*>
      if (formats != null && formats.isNotEmpty()) {
        val barcodeFormats = getBarcodeFormats(formats)
        if (barcodeFormats.isNotEmpty()) {
          optionsBuilder.setBarcodeFormats(barcodeFormats.first(), *barcodeFormats.drop(1).toIntArray())
        }
      } else {
        optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
      }
    } else {
      optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
    }

    return optionsBuilder.build()
  }

  private fun getBarcodeFormats(formatNames: List<*>): List<Int> {
    val formats = mutableListOf<Int>()

    formatNames.forEach { formatName ->
      when (formatName) {
        "qrCode" -> formats.add(Barcode.FORMAT_QR_CODE)
        "code128" -> formats.add(Barcode.FORMAT_CODE_128)
        "code39" -> formats.add(Barcode.FORMAT_CODE_39)
        "code93" -> formats.add(Barcode.FORMAT_CODE_93)
        "codabar" -> formats.add(Barcode.FORMAT_CODABAR)
        "dataMatrix" -> formats.add(Barcode.FORMAT_DATA_MATRIX)
        "ean13" -> formats.add(Barcode.FORMAT_EAN_13)
        "ean8" -> formats.add(Barcode.FORMAT_EAN_8)
        "itf" -> formats.add(Barcode.FORMAT_ITF)
        "upcA" -> formats.add(Barcode.FORMAT_UPC_A)
        "upcE" -> formats.add(Barcode.FORMAT_UPC_E)
        "pdf417" -> formats.add(Barcode.FORMAT_PDF417)
        "aztec" -> formats.add(Barcode.FORMAT_AZTEC)
      }
    }

    return if (formats.isEmpty()) listOf(Barcode.FORMAT_ALL_FORMATS) else formats
  }

  private fun getBarcodeFormatString(format: Int): String {
    return when (format) {
      Barcode.FORMAT_QR_CODE -> "qrCode"
      Barcode.FORMAT_CODE_128 -> "code128"
      Barcode.FORMAT_CODE_39 -> "code39"
      Barcode.FORMAT_CODE_93 -> "code93"
      Barcode.FORMAT_CODABAR -> "codabar"
      Barcode.FORMAT_DATA_MATRIX -> "dataMatrix"
      Barcode.FORMAT_EAN_13 -> "ean13"
      Barcode.FORMAT_EAN_8 -> "ean8"
      Barcode.FORMAT_ITF -> "itf"
      Barcode.FORMAT_UPC_A -> "upcA"
      Barcode.FORMAT_UPC_E -> "upcE"
      Barcode.FORMAT_PDF417 -> "pdf417"
      Barcode.FORMAT_AZTEC -> "aztec"
      else -> "unknown"
    }
  }

  private fun getCornerPoints(barcode: Barcode): List<Map<String, Int>> {
    val points = mutableListOf<Map<String, Int>>()
    barcode.cornerPoints?.forEach { point ->
      points.add(mapOf("x" to point.x, "y" to point.y))
    }
    return points
  }

  private fun getInputImageFromPath(path: String): InputImage {
    return if (path.startsWith("content://") || path.startsWith("file://")) {
      val uri = path.toUri()
      InputImage.fromFilePath(context, uri)
    } else {
      val file = File(path)
      val bitmap = BitmapFactory.decodeFile(file.absolutePath)
      InputImage.fromBitmap(bitmap, 0)
    }
  }

  override fun onDetachedFromEngine( binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    cameraChannel.setMethodCallHandler(null)
    scanner?.close()
    scanner = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // No necesitamos hacer nada aquí
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    // No necesitamos hacer nada aquí
  }

  inner class CameraMethodHandler : MethodCallHandler {
    override fun onMethodCall(call: MethodCall, result: Result) {
      when (call.method) {
        "initializeCamera" -> {
          val options = call.argument<Map<String, Any>>("options")

          if (cameraHandler == null) {
            val textureRegistry = flutterPluginBinding?.textureRegistry
            if (textureRegistry != null) {
              cameraHandler = CameraHandler(context, activity, textureRegistry, cameraChannel)
              cameraHandler?.initializeCamera(options, result)
            } else {
              result.error("TEXTURE_REGISTRY_ERROR", "No se pudo obtener el registro de texturas", null)
            }
          } else {
            cameraHandler?.initializeCamera(options, result)
          }
        }
        "toggleFlash" -> {
          cameraHandler?.toggleFlash(result)
        }
        "updateOptions" -> {
          val options = call.argument<Map<String, Any>>("options")
          cameraHandler?.updateScannerOptions(options, result)
        }
        "pauseScanning" -> {
          cameraHandler?.pauseScanning()
          result.success(null)
        }
        "resumeScanning" -> {
          cameraHandler?.resumeScanning()
          result.success(null)
        }
        "disposeCamera" -> {
          cameraHandler?.dispose()
          cameraHandler = null
          result.success(null)
        }
        "touchToFocus" -> {
          val x = call.argument<Double>("x")
          val y = call.argument<Double>("y")

          if (x != null && y != null) {
            cameraHandler?.onTouchToFocus(x.toFloat(), y.toFloat())
            result.success(null)
          } else {
            result.error("INVALID_ARGS", "Coordenadas x,y no proporcionadas o inválidas", null)
          }
        }
        else -> {
          result.notImplemented()
        }
      }
    }
  }
}

