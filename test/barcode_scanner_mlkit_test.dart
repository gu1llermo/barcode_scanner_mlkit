// import 'package:flutter_test/flutter_test.dart';
// import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit.dart';
// import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit_platform_interface.dart';
// import 'package:barcode_scanner_mlkit/barcode_scanner_mlkit_method_channel.dart';
// import 'package:plugin_platform_interface/plugin_platform_interface.dart';

// class MockBarcodeScannerMlkitPlatform
//     with MockPlatformInterfaceMixin
//     implements BarcodeScannerMlkitPlatform {

//   @override
//   Future<String?> getPlatformVersion() => Future.value('42');
// }

// void main() {
//   final BarcodeScannerMlkitPlatform initialPlatform = BarcodeScannerMlkitPlatform.instance;

//   test('$MethodChannelBarcodeScannerMlkit is the default instance', () {
//     expect(initialPlatform, isInstanceOf<MethodChannelBarcodeScannerMlkit>());
//   });

//   test('getPlatformVersion', () async {
//     BarcodeScannerMlkit barcodeScannerMlkitPlugin = BarcodeScannerMlkit();
//     MockBarcodeScannerMlkitPlatform fakePlatform = MockBarcodeScannerMlkitPlatform();
//     BarcodeScannerMlkitPlatform.instance = fakePlatform;

//     expect(await barcodeScannerMlkitPlugin.getPlatformVersion(), '42');
//   });
// }
