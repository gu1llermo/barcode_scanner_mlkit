// import 'package:flutter/services.dart';
// import 'package:flutter_test/flutter_test.dart';
// import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit_method_channel.dart';

// void main() {
//   TestWidgetsFlutterBinding.ensureInitialized();

//   MethodChannelBarcodeScannerMlkit platform = MethodChannelBarcodeScannerMlkit();
//   const MethodChannel channel = MethodChannel('barcode_scanner_mlkit');

//   setUp(() {
//     TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
//       channel,
//       (MethodCall methodCall) async {
//         return '42';
//       },
//     );
//   });

//   tearDown(() {
//     TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
//   });

//   test('getPlatformVersion', () async {
//     expect(await platform.getPlatformVersion(), '42');
//   });
// }
