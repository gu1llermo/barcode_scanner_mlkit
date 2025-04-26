// Archivo: lib/src/barcode_scanner.dart

import 'dart:async';
import 'package:flutter/services.dart';
import 'models/barcode_value.dart';
import 'models/scan_options.dart';

class MLKitBarcodeScanner {
  static const MethodChannel _channel = MethodChannel('barcode_scanner_mlkit');
  
  /// Inicializa el escáner de códigos de barras
  Future<bool> initialize() async {
    try {
      final bool result = await _channel.invokeMethod('initialize');
      return result;
    } on PlatformException catch (e) {
      print("Error al inicializar el escáner: ${e.message}");
      return false;
    }
  }

  /// Escanea un código de barras desde una imagen
  /// [imagePath] - Ruta de la imagen en el dispositivo
  /// [options] - Opciones de escaneo que incluyen formatos y calidad de imagen
  Future<List<BarcodeValue>> scanFromImage(String imagePath, {ScanOptions? options}) async {
    try {
      final List<dynamic> results = await _channel.invokeMethod('scanFromImage', {
        'imagePath': imagePath,
        'options': options?.toMap() ?? const ScanOptions().toMap(),
      });
      
      return results.map((result) => BarcodeValue.fromMap(result)).toList();
    } on PlatformException catch (e) {
      print("Error al escanear desde imagen: ${e.message}");
      return [];
    }
  }

  /// Detiene el escáner y libera recursos
  Future<void> dispose() async {
    try {
      await _channel.invokeMethod('dispose');
    } on PlatformException catch (e) {
      print("Error al liberar recursos: ${e.message}");
    }
  }


}