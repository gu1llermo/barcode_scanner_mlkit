# Barcode Scanner MLKit

Una librería de Flutter para escanear códigos de barras y QR usando MLKit exclusivamente para Android.

## Características

- Escaneo de códigos de barras y QR desde la cámara en tiempo real
- Soporte para escaneo desde imágenes
- Soporte para múltiples formatos de códigos de barras
- Configuración personalizada de formatos de códigos a detectar
- Control de flash
- Auto-enfoque
- Información de puntos de esquina del código

## Instalación

Agrega este paquete a tu `pubspec.yaml`:

```yaml
dependencies:
  barcode_scanner_mlkit: ^0.1.0
```

O, si quieres usar la última versión desde GitHub:

```yaml
dependencies:
  barcode_scanner_mlkit:
    git:
      url: https://github.com/tuusuario/barcode_scanner_mlkit.git
```

### Configuración Android

Asegúrate de tener los siguientes permisos en tu archivo `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

También asegúrate de que tu `minSdkVersion` sea al menos 21 en tu archivo `android/app/build.gradle`:

```gradle
android {
    defaultConfig {
        minSdkVersion 21
        // ...
    }
}
```

## Uso

### Escaneo en tiempo real con cámara

```dart
import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit.dart';
import 'package:flutter/material.dart';

class ScannerScreen extends StatefulWidget {
  @override
  _ScannerScreenState createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  String result = "Escanea un código";

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Escáner de Códigos')),
      body: Column(
        children: [
          Expanded(
            flex: 4,
            child: MLKitCameraView(
              onBarcodeDetected: _onBarcodeDetected,
              options: ScanOptions(
                formats: [BarcodeFormat.qrCode, BarcodeFormat.ean13],
                enableAutoFocus: true,
                enableFlash: false,
              ),
              overlay: _buildOverlay(),
            ),
          ),
          Expanded(
            flex: 1,
            child: Center(child: Text(result, style: TextStyle(fontSize: 18))),
          ),
        ],
      ),
    );
  }

  Widget _buildOverlay() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.5),
      ),
      child: Center(
        child: Container(
          width: 250,
          height: 250,
          decoration: BoxDecoration(
            border: Border.all(color: Colors.white, width: 2.0),
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
    );
  }

  void _onBarcodeDetected(List<BarcodeValue> barcodes) {
    if (barcodes.isNotEmpty) {
      setState(() {
        result = "Código: ${barcodes[0].value}";
      });
    }
  }
}
```

### Escaneo desde una imagen

```dart
import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

class ImageScannerScreen extends StatefulWidget {
  @override
  _ImageScannerScreenState createState() => _ImageScannerScreenState();
}

class _ImageScannerScreenState extends State<ImageScannerScreen> {
  final MLKitBarcodeScanner _scanner = MLKitBarcodeScanner();
  String result = "Selecciona una imagen para escanear";
  String? imagePath;

  @override
  void initState() {
    super.initState();
    _scanner.initialize();
  }

  @override
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  Future<void> _scanImage() async {
    final picker = ImagePicker();
    final pickedFile = await picker.pickImage(source: ImageSource.gallery);
    
    if (pickedFile != null) {
      setState(() {
        imagePath = pickedFile.path;
        result = "Procesando...";
      });
      
      final barcodes = await _scanner.scanFromImage(
        pickedFile.path,
        options: ScanOptions(
          formats: [BarcodeFormat.qrCode, BarcodeFormat.ean13],
        ),
      );
      
      setState(() {
        if (barcodes.isNotEmpty) {
          result = "Código: ${barcodes[0].value}";
        } else {
          result = "No se encontraron códigos";
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Escaneo desde Imagen')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (imagePath != null)
              Image.file(File(imagePath!), height: 300),
            SizedBox(height: 20),
            Text(result, style: TextStyle(fontSize: 18)),
            SizedBox(height: 20),
            ElevatedButton(
              onPressed: _scanImage,
              child: Text('Seleccionar Imagen'),
            ),
          ],
        ),
      ),
    );
  }
}
```

## Formatos de códigos soportados

- `BarcodeFormat.qrCode` - Códigos QR
- `BarcodeFormat.code128` - Code 128
- `BarcodeFormat.code39` - Code 39
- `BarcodeFormat.code93` - Code 93
- `BarcodeFormat.codabar` - Codabar
- `BarcodeFormat.dataMatrix` - Data Matrix
- `BarcodeFormat.ean13` - EAN-13
- `BarcodeFormat.ean8` - EAN-8
- `BarcodeFormat.itf` - ITF
- `BarcodeFormat.upcA` - UPC-A
- `BarcodeFormat.upcE` - UPC-E
- `BarcodeFormat.pdf417` - PDF-417
- `BarcodeFormat.aztec` - Aztec

## Contribución

Las contribuciones son bienvenidas. Puedes abrir un issue o enviar un pull request.

## Licencia

Este proyecto está bajo la licencia MIT.