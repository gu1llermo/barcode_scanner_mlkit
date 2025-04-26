// import 'package:flutter/foundation.dart';
// import 'package:flutter/services.dart';

// import 'barcode_scanner_mlkit_platform_interface.dart';

// /// An implementation of [BarcodeScannerMlkitPlatform] that uses method channels.
// class MethodChannelBarcodeScannerMlkit extends BarcodeScannerMlkitPlatform {
//   /// The method channel used to interact with the native platform.
//   @visibleForTesting
//   final methodChannel = const MethodChannel('barcode_scanner_mlkit');

//   @override
//   Future<String?> getPlatformVersion() async {
//     final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
//     return version;
//   }
// }
