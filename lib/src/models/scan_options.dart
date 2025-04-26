// Archivo: lib/src/models/scan_options.dart

import 'barcode_value.dart';

enum CameraQuality {
  low,
  medium,
  high,
  max
}

class CameraResolution {
  final int width;
  final int height;
  
  const CameraResolution(this.width, this.height);
  
  Map<String, int> toMap() {
    return {
      'width': width,
      'height': height
    };
  }
}

class ScanOptions {
  final List<BarcodeFormat>? formats;
  final bool enableAutoFocus;
  final bool enableFlash;
  final CameraQuality? quality;
  final CameraResolution? resolution;
  
  const ScanOptions({
    this.formats,
    this.enableAutoFocus = true,
    this.enableFlash = false,
    this.quality,
    this.resolution,
  });
  
  Map<String, dynamic> toMap() {
    final map = {
      'formats': formats?.map((f) => f.toString().split('.').last).toList(),
      'enableAutoFocus': enableAutoFocus,
      'enableFlash': enableFlash,
    };
    
    // Add quality setting if specified
    if (quality != null) {
      map['quality'] = quality.toString().split('.').last;
    }
    
    // Add custom resolution if specified
    if (resolution != null) {
      map['resolution'] = resolution!.toMap();
    }
    
    return map;
  }
}