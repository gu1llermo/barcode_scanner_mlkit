import 'dart:io';
// import 'package:barcode_scanner_mlkit/src/image_scanner.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit.dart';

class ScannerExamplePage extends StatefulWidget {
  const ScannerExamplePage({super.key});

  @override
  State<ScannerExamplePage> createState() => _ScannerExamplePageState();
}

class _ScannerExamplePageState extends State<ScannerExamplePage> {
  final ImageScanner _imageScanner = ImageScanner();
  List<BarcodeValue> _detectedBarcodes = [];
  File? _selectedImage;
  bool _isProcessing = false;
  String? _errorMessage;

  CameraQuality _selectedQuality = CameraQuality.medium;
  final List<BarcodeFormat> _selectedFormats = [BarcodeFormat.qrCode];

  @override
  void dispose() {
    _imageScanner.dispose();
    super.dispose();
  }

  Future<void> _pickAndScanImage() async {
    try {
      final ImagePicker picker = ImagePicker();
      final XFile? image = await picker.pickImage(source: ImageSource.gallery);

      if (image != null) {
        setState(() {
          _selectedImage = File(image.path);
          _isProcessing = true;
          _errorMessage = null;
        });

        final barcodes = await _imageScanner.scanImage(
          imageFile: _selectedImage!,
          formats: _selectedFormats,
          quality: _selectedQuality,
        );

        setState(() {
          _detectedBarcodes = barcodes;
          _isProcessing = false;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'Error: $e';
        _isProcessing = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Barcode Scanner Example'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Quality selector
            DropdownButtonFormField<CameraQuality>(
              value: _selectedQuality,
              decoration: const InputDecoration(
                labelText: 'Image Quality',
                border: OutlineInputBorder(),
              ),
              items: [
                DropdownMenuItem(value: CameraQuality.low, child: Text('Low')),
                DropdownMenuItem(value: CameraQuality.medium, child: Text('Medium')),
                DropdownMenuItem(value: CameraQuality.high, child: Text('High')),
                DropdownMenuItem(value: CameraQuality.max, child: Text('Maximum')),
              ],
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _selectedQuality = value;
                  });
                }
              },
            ),

            const SizedBox(height: 16),

            // Format selection checkboxes
            Text('Select Barcode Formats:', style: Theme.of(context).textTheme.titleMedium),
            Wrap(
              spacing: 8,
              children: [
                _buildFormatChip(BarcodeFormat.qrCode, 'QR Code'),
                _buildFormatChip(BarcodeFormat.code128, 'Code 128'),
                _buildFormatChip(BarcodeFormat.code39, 'Code 39'),
                _buildFormatChip(BarcodeFormat.ean13, 'EAN-13'),
                _buildFormatChip(BarcodeFormat.dataMatrix, 'Data Matrix'),
                _buildFormatChip(BarcodeFormat.pdf417, 'PDF417'),
              ],
            ),

            const SizedBox(height: 16),

            // Image preview
            if (_selectedImage != null) ...[
              AspectRatio(
                aspectRatio: 1,
                child: Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey),
                  ),
                  child: Image.file(_selectedImage!, fit: BoxFit.cover),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Scan button
            ElevatedButton(
              onPressed: _isProcessing ? null : _pickAndScanImage,
              child: _isProcessing
                  ? const CircularProgressIndicator()
                  : const Text('Pick and Scan Image'),
            ),

            // Error message
            if (_errorMessage != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8.0),
                child: Text(
                  _errorMessage!,
                  style: TextStyle(color: Colors.red),
                ),
              ),

            // Results
            const SizedBox(height: 16),
            Text(
              'Detected Barcodes:',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            if (_detectedBarcodes.isEmpty)
              const Padding(
                padding: EdgeInsets.all(8.0),
                child: Text('No barcodes detected'),
              )
            else
              ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _detectedBarcodes.length,
                itemBuilder: (context, index) {
                  final barcode = _detectedBarcodes[index];
                  return Card(
                    child: ListTile(
                      title: Text(barcode.value),
                      subtitle: Text('Format: ${barcode.format.toString().split('.').last}'),
                    ),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildFormatChip(BarcodeFormat format, String label) {
    final bool isSelected = _selectedFormats.contains(format);

    return FilterChip(
      label: Text(label),
      selected: isSelected,
      onSelected: (bool selected) {
        setState(() {
          if (selected) {
            _selectedFormats.add(format);
          } else {
            _selectedFormats.remove(format);
          }
          // Always keep at least one format selected
          if (_selectedFormats.isEmpty) {
            _selectedFormats.add(format);
          }
        });
      },
    );
  }
}