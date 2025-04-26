// import 'package:plugin_platform_interface/plugin_platform_interface.dart';

// import 'barcode_scanner_mlkit_method_channel.dart';

// abstract class BarcodeScannerMlkitPlatform extends PlatformInterface {
//   /// Constructs a BarcodeScannerMlkitPlatform.
//   BarcodeScannerMlkitPlatform() : super(token: _token);

//   static final Object _token = Object();

//   static BarcodeScannerMlkitPlatform _instance = MethodChannelBarcodeScannerMlkit();

//   /// The default instance of [BarcodeScannerMlkitPlatform] to use.
//   ///
//   /// Defaults to [MethodChannelBarcodeScannerMlkit].
//   static BarcodeScannerMlkitPlatform get instance => _instance;

//   /// Platform-specific implementations should set this with their own
//   /// platform-specific class that extends [BarcodeScannerMlkitPlatform] when
//   /// they register themselves.
//   static set instance(BarcodeScannerMlkitPlatform instance) {
//     PlatformInterface.verifyToken(instance, _token);
//     _instance = instance;
//   }

//   Future<String?> getPlatformVersion() {
//     throw UnimplementedError('platformVersion() has not been implemented.');
//   }
// }
