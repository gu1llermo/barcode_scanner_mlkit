// Archivo: lib/src/image_scanner.dart
// a wrapper class for easier image scanning with quality options

import 'dart:io';
import 'barcode_scanner.dart';
import 'models/barcode_value.dart';
import 'models/scan_options.dart';

class ImageScanner {
  final MLKitBarcodeScanner _scanner = MLKitBarcodeScanner();
  bool _isInitialized = false;
  
  /// Scan a barcode from an image file with quality control
  /// Returns a list of detected barcodes
  Future<List<BarcodeValue>> scanImage({
    required File imageFile,
    List<BarcodeFormat>? formats,
    CameraQuality? quality,
  }) async {
    // Initialize scanner if not already done
    if (!_isInitialized) {
      _isInitialized = await _scanner.initialize();
      if (!_isInitialized) {
        return [];
      }
    }
    
    // Configure options
    final options = ScanOptions(
      formats: formats,
      quality: quality,
    );
    
    // Scan the image
    return await _scanner.scanFromImage(
      imageFile.path,
      options: options,
    );
  }
  
  /// Clean up resources when done
  Future<void> dispose() async {
    if (_isInitialized) {
      await _scanner.dispose();
      _isInitialized = false;
    }
  }
}