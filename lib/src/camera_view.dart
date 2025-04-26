import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'models/barcode_value.dart';
import 'models/scan_options.dart';

typedef BarcodeCallback = void Function(List<BarcodeValue> barcodes);

class MLKitCameraView extends StatefulWidget {
  final BarcodeCallback onBarcodeDetected;
  final ScanOptions options;
  final Widget? overlay;
  final bool showFlashButton;
 final Function(String)? onError; // Add callback for error handling


  const MLKitCameraView({
    super.key,
    required this.onBarcodeDetected,
    this.options = const ScanOptions(),
    this.overlay,
    this.showFlashButton = true,
    this.onError,
   // required this.cameraViewKey,
  });

  @override
  State<MLKitCameraView> createState() => _MLKitCameraViewState();
}

class _MLKitCameraViewState extends State<MLKitCameraView> with WidgetsBindingObserver {
  static const MethodChannel _channel = MethodChannel('barcode_scanner_mlkit/camera');
  bool _isInitialized = false;
  bool _flashEnabled = false;
  int _textureId = -1;
  bool _isFocusing = false;

  // New variables for focus animation
  // bool _showFocusAnimation = false;
  // Offset _focusPoint = Offset.zero;
  // Timer? _focusAnimationTimer;


  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // Configurar el manejador de métodos antes de inicializar la cámara
    _setupMethodCallHandler();
    _initializeCamera();
  }

  // Método que puede ser llamado desde fuera para enfocar manualmente
  //void focusCamera(double x, double y) {
   // cameraViewKey?.currentState?.focusOnPoint(x, y);
  //}

  Future<void> focusOnPoint(double x, double y) async {
    if (_isInitialized && !_isFocusing) {
      _isFocusing = true;
      try {
        // await _channel.invokeMethod('touchToFocus', {
        await _channel.invokeMethod('touchToFocus', {
          'x': x,
          'y': y,
        });

        // // Show focus animation
        // // Convert normalized coordinates to actual pixel positions
        // if (!context.mounted) return;
        // final size = MediaQuery.sizeOf(context);
        // setState(() {
        //   _focusPoint = Offset(x * size.width, y * size.height);
        //   _showFocusAnimation = true;
        // });
        
        // // Cancel any existing timer
        // _focusAnimationTimer?.cancel();
        
        // // Set timer to hide animation after 3 seconds
        // _focusAnimationTimer = Timer(const Duration(milliseconds: 1500), () {
        //   if (mounted) {
        //     setState(() {
        //       _showFocusAnimation = false;
        //     });
        //   }
        // });

      } on PlatformException catch (e) {
        _notifyError('Error al enfocar en el punto: ${e.message}');
      } finally {
        _isFocusing = false;
      }
    }
  }

  void _notifyError(String message) {
    print(message);
    if (widget.onError != null) {
      widget.onError!(message);
    }
  }

  void _setupMethodCallHandler() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onBarcodeDetected') {
        final Map<dynamic, dynamic> arguments = call.arguments;
        final List<dynamic> barcodes = arguments['barcodes'] ?? [];

        try {
          final List<BarcodeValue> detectedBarcodes = barcodes
              .map<BarcodeValue>((data) => BarcodeValue.fromMap(data as Map<dynamic, dynamic>))
              .toList();

          widget.onBarcodeDetected(detectedBarcodes);
        } catch (e) {
          _notifyError("Error procesando códigos de barras en Flutter: $e");
        }
      }
      return null;
    });
  }
  

Future<bool> _requestCameraPermission(BuildContext context) async {
  // Verificar estado actual del permiso
  var status = await Permission.camera.status;
  
  if (status.isGranted) {
    return true;
    // El permiso ya está concedido
   // print('Permiso de cámara ya concedido');
    // Aquí puedes iniciar la cámara
  } else if (status.isDenied) {
    // Solicitar permiso
    status = await Permission.camera.request();
    
    if (status.isGranted) {
      //print('Permiso de cámara concedido');
      // Proceder con la funcionalidad de la cámara
      return true;
    } else {
      //print('Permiso de cámara denegado');
      _notifyError('Permiso de cámara denegado');
      // Mostrar mensaje al usuario
      if (!context.mounted) return false;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Se requiere acceso a la cámara para esta función'))
      );
      return false;
    }
  } else if (status.isPermanentlyDenied) {
    // El usuario denegó permanentemente el permiso
    // Sugerir que vaya a la configuración
    final result = await openAppSettings();
    return result;
  }
  return false;
}

  Future<void> _initializeCamera() async {
  try {
    // Verificar y solicitar permiso de cámara
    final hasPermission = await _requestCameraPermission(context);
    if (!hasPermission) return;
    
    // Continuar con la inicialización de la cámara
    //print("Inicializando cámara...");
    final Map<String, dynamic>? result = await _channel.invokeMapMethod('initializeCamera', {
      'options': widget.options.toMap(),
    });

    if (result != null) {
      _textureId = result['textureId'] ?? -1;
      _isInitialized = _textureId != -1;
      //print("Cámara inicializada con textureId: $_textureId");
    } else {
      _notifyError("La inicialización de la cámara devolvió null");
    }

    if (mounted) {
      setState(() {});
    }
  } on PlatformException catch (e) {
    _notifyError('Error al inicializar la cámara: ${e.message}');
  } catch (e) {
    _notifyError('Error desconocido al inicializar la cámara: $e');
  }
}

  Future<void> _toggleFlash() async {
    try {
      final bool result = await _channel.invokeMethod('toggleFlash');
      if (mounted) {
        setState(() {
          _flashEnabled = result;
        });
      }
    } on PlatformException catch (e) {
      _notifyError('Error al cambiar el flash: ${e.message}');
    }
  }

  // Method to update scanner options at runtime
  Future<void> updateScannerOptions(ScanOptions newOptions) async {
    try {
      await _channel.invokeMethod('updateOptions', {
        'options': newOptions.toMap(),
      });
    } on PlatformException catch (e) {
      _notifyError('Error al actualizar las opciones del escáner: ${e.message}');
    }
  }

  @override
  void didUpdateWidget(MLKitCameraView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.options != widget.options) {
      _channel.invokeMethod('updateOptions', {
        'options': widget.options.toMap(),
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _resumeScanning();
    } else if (state == AppLifecycleState.paused) {
      _pauseScanning();
    }
  }

  Future<void> _resumeScanning() async {
    if (_isInitialized) {
      await _channel.invokeMethod('resumeScanning');
    }
  }

  Future<void> _pauseScanning() async {
    if (_isInitialized) {
      await _channel.invokeMethod('pauseScanning');
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _channel.setMethodCallHandler(null); // Importante: remover el handler
    _channel.invokeMethod('disposeCamera');
    // _focusAnimationTimer?.cancel(); // Cancel timer if it exists
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_isInitialized || _textureId < 0) {
      return const Center(child: CircularProgressIndicator());
    }

    return Stack(
      fit: StackFit.expand,
      children: [
        GestureDetector(
          onTapUp: (TapUpDetails details) {
            // Calcular coordenadas normalizadas (0-1)
            final RenderBox box = context.findRenderObject() as RenderBox;
            final Offset localPosition = box.globalToLocal(details.globalPosition);

            final double normalizedX = localPosition.dx / box.size.width;
            final double normalizedY = localPosition.dy / box.size.height;

            // Si las coordenadas están dentro del rango válido
            if (normalizedX >= 0 && normalizedX <= 1 && normalizedY >= 0 && normalizedY <= 1) {
              focusOnPoint(normalizedX, normalizedY);
            }
          },
          child: Texture(textureId: _textureId),
        ),
        if (widget.overlay != null) widget.overlay!,
        // Focus animation overlay
        // if (_showFocusAnimation)
        //   Positioned(
        //     left: _focusPoint.dx - 60,
        //     top: _focusPoint.dy - 80,
        //     child: const FocusAnimationWidget(),
        //   ),
        if (widget.showFlashButton)
          Positioned(
            bottom: 20,
            right: 20,
            child: FloatingActionButton(
              onPressed: _toggleFlash,
              child: Icon(
                _flashEnabled ? Icons.flash_on : Icons.flash_off,
              ),
            ),
          ),
      ],
    );
  }
}

// Custom widget for focus animation
class FocusAnimationWidget extends StatefulWidget {
  const FocusAnimationWidget({super.key});

  @override
  State<FocusAnimationWidget> createState() => _FocusAnimationWidgetState();
}

class _FocusAnimationWidgetState extends State<FocusAnimationWidget> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  late Animation<double> _opacityAnimation;

  @override
  void initState() {
    super.initState();
    
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    
    _scaleAnimation = Tween<double>(begin: 0.7, end: 1.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: Curves.easeOutQuad,
      ),
    );
    
    _opacityAnimation = Tween<double>(begin: 1.0, end: 0.0).animate(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.7, 1.0, curve: Curves.easeOut),
      ),
    );
    
    _controller.repeat();
  }
  
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Opacity(
          opacity: _opacityAnimation.value,
          child: Transform.scale(
            scale: _scaleAnimation.value,
            child: Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                border: Border.all(
                  color: Colors.white,
                  width: 2.0,
                ),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Container(
                  width: 10,
                  height: 10,
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}