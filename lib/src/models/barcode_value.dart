// lib/src/models/barcode_value.dart
enum BarcodeFormat {
  unknown,
  code128,
  code39,
  code93,
  codabar,
  dataMatrix,
  ean13,
  ean8,
  itf,
  qrCode,
  upcA,
  upcE,
  pdf417,
  aztec
}

class BarcodeValue {
  final String value;
  final BarcodeFormat format;
  final List<Point> cornerPoints;

  BarcodeValue({
    required this.value,
    required this.format,
    required this.cornerPoints,
  });

  factory BarcodeValue.fromMap(Map<dynamic, dynamic> map) {
    final List<dynamic> pointsData = map['cornerPoints'] ?? [];
    final List<Point> points = pointsData
        .map((point) => Point(
      (point['x'] is int) ? point['x'].toDouble() : point['x'],
      (point['y'] is int) ? point['y'].toDouble() : point['y'],
    ))
        .toList();

    // Convertir el formato correctamente
    BarcodeFormat format = BarcodeFormat.unknown;
    String formatStr = map['format'] ?? 'unknown';

    try {
      format = BarcodeFormat.values.firstWhere(
            (f) => f.toString().split('.').last == formatStr,
        orElse: () => BarcodeFormat.unknown,
      );
    } catch(e) {
      print("Error al convertir el formato del código: $e");
    }

    return BarcodeValue(
      value: map['value'] ?? '',
      format: format,
      cornerPoints: points,
    );
  }

  @override
  String toString() {
    return 'BarcodeValue(value: $value, format: $format)';
  }
}

class Point {
  final double x;
  final double y;

  Point(this.x, this.y);

  @override
  String toString() {
    return 'Point(x: $x, y: $y)';
  }
}