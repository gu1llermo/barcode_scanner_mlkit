import 'package:flutter/material.dart';
import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit.dart';

class CameraExamplePage extends StatefulWidget {
  const CameraExamplePage({Key? key}) : super(key: key);

  @override
  State<CameraExamplePage> createState() => _CameraExamplePageState();
}

class _CameraExamplePageState extends State<CameraExamplePage> {
  final List<BarcodeValue> _detectedBarcodes = [];
  String? _errorMessage;
  bool _isScanning = true;

  CameraQuality? _selectedQuality = CameraQuality.medium;
  CameraResolution? _customResolution;
  final List<BarcodeFormat> _selectedFormats = [
    BarcodeFormat.qrCode,
    BarcodeFormat.code128,
    BarcodeFormat.ean13
  ];

  void _onBarcodeDetected(List<BarcodeValue> barcodes) {
    setState(() {
      _detectedBarcodes.clear();
      _detectedBarcodes.addAll(barcodes);
      _isScanning = false;
    });
  }

  void _resumeScanning() {
    setState(() {
      _isScanning = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Live Scanning Example'),
      ),
      body: Column(
        children: [
          // Camera view takes most of the screen
          Expanded(
            flex: 3,
            child: Stack(
              children: [
                MLKitCameraView(
                  onBarcodeDetected: _onBarcodeDetected,
                  options: ScanOptions(
                    formats: _selectedFormats,
                    enableAutoFocus: true,
                    quality: _selectedQuality,
                    resolution: _customResolution,
                  ),
                  overlay: _buildOverlay(),
                  onError: (message) {
                    setState(() {
                      _errorMessage = message;
                    });
                  },
                ),
                // Display an error message if needed
                if (_errorMessage != null)
                  Positioned(
                    top: 10,
                    left: 10,
                    right: 10,
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      color: Colors.red.withOpacity(0.7),
                      child: Text(
                        _errorMessage!,
                        style: const TextStyle(color: Colors.white),
                      ),
                    ),
                  ),
              ],
            ),
          ),

          // Settings panel at the bottom
          Container(
            padding: const EdgeInsets.all(16),
            color: Colors.grey[200],
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    const Text('Quality:'),
                    const SizedBox(width: 8),
                    Expanded(
                      child: DropdownButton<CameraQuality>(
                        value: _selectedQuality,
                        isExpanded: true,
                        onChanged: (value) {
                          if (value != null) {
                            setState(() {
                              _selectedQuality = value;
                              // Reset custom resolution when using predefined quality
                              _customResolution = null;
                            });
                          }
                        },
                        items: const [
                          DropdownMenuItem(value: CameraQuality.low, child: Text('Low')),
                          DropdownMenuItem(value: CameraQuality.medium, child: Text('Medium')),
                          DropdownMenuItem(value: CameraQuality.high, child: Text('High')),
                          DropdownMenuItem(value: CameraQuality.max, child: Text('Maximum')),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.settings),
                      onPressed: () {
                        _showCustomResolutionDialog();
                      },
                    ),
                  ],
                ),

                const SizedBox(height: 8),

                // Display detected barcodes or scan button
                if (_detectedBarcodes.isNotEmpty)
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        color: Colors.green[100],
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: _detectedBarcodes.map((barcode) {
                            return Text(
                              '${barcode.format.toString().split('.').last}: ${barcode.value}',
                              style: const TextStyle(fontSize: 16),
                            );
                          }).toList(),
                        ),
                      ),
                      const SizedBox(height: 8),
                      ElevatedButton(
                        onPressed: _resumeScanning,
                        child: const Text('Continue Scanning'),
                      ),
                    ],
                  )
                else
                  const Center(child: Text('Scanning for barcodes...')),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOverlay() {
    if (!_isScanning) return Container();

    return CustomPaint(
      painter: ScannerOverlayPainter(),
    );
  }

  void _showCustomResolutionDialog() {
    final TextEditingController widthController = TextEditingController();
    final TextEditingController heightController = TextEditingController();

    // Pre-fill with current custom resolution if available
    if (_customResolution != null) {
      widthController.text = _customResolution!.width.toString();
      heightController.text = _customResolution!.height.toString();
    } else {
      // Default values
      widthController.text = '1280';
      heightController.text = '720';
    }

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Custom Resolution'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: widthController,
              decoration: const InputDecoration(labelText: 'Width (pixels)'),
              keyboardType: TextInputType.number,
            ),
            TextField(
              controller: heightController,
              decoration: const InputDecoration(labelText: 'Height (pixels)'),
              keyboardType: TextInputType.number,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
            },
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final int width = int.tryParse(widthController.text) ?? 1280;
              final int height = int.tryParse(heightController.text) ?? 720;

              setState(() {
                _customResolution = CameraResolution(width, height);
                _selectedQuality = null; // Set to null to indicate custom resolution
              });

              Navigator.of(context).pop();
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
  }
}

// A simple overlay painter for the scanner
class ScannerOverlayPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final Rect outerRect = Rect.fromLTWH(0, 0, size.width, size.height);
    final Rect innerRect = Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: size.width * 0.7,
      height: size.width * 0.7,
    );

    final Paint paint = Paint()
      ..color = Colors.black.withOpacity(0.5)
      ..style = PaintingStyle.fill;

    // Draw the semi-transparent overlay
    canvas.drawPath(
      Path.combine(
        PathOperation.difference,
        Path()..addRect(outerRect),
        Path()..addRect(innerRect),
      ),
      paint,
    );

    // Draw scan area border
    final Paint borderPaint = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;

    canvas.drawRect(innerRect, borderPaint);

    // Draw corner markers
    final Paint cornerPaint = Paint()
      ..color = Colors.green
      ..style = PaintingStyle.stroke
      ..strokeWidth = 4.0;

    final double cornerSize = size.width * 0.05;

    // Top-left corner
    canvas.drawLine(
      innerRect.topLeft,
      innerRect.topLeft.translate(cornerSize, 0),
      cornerPaint,
    );
    canvas.drawLine(
      innerRect.topLeft,
      innerRect.topLeft.translate(0, cornerSize),
      cornerPaint,
    );

    // Top-right corner
    canvas.drawLine(
      innerRect.topRight,
      innerRect.topRight.translate(-cornerSize, 0),
      cornerPaint,
    );
    canvas.drawLine(
      innerRect.topRight,
      innerRect.topRight.translate(0, cornerSize),
      cornerPaint,
    );

    // Bottom-left corner
    canvas.drawLine(
      innerRect.bottomLeft,
      innerRect.bottomLeft.translate(cornerSize, 0),
      cornerPaint,
    );
    canvas.drawLine(
      innerRect.bottomLeft,
      innerRect.bottomLeft.translate(0, -cornerSize),
      cornerPaint,
    );

    // Bottom-right corner
    canvas.drawLine(
      innerRect.bottomRight,
      innerRect.bottomRight.translate(-cornerSize, 0),
      cornerPaint,
    );
    canvas.drawLine(
      innerRect.bottomRight,
      innerRect.bottomRight.translate(0, -cornerSize),
      cornerPaint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return false;
  }
}