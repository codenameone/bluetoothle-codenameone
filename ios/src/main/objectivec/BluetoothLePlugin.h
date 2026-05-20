#import "CDV.h"
#import <CoreBluetooth/CoreBluetooth.h>

@interface BluetoothLePlugin : CDVPlugin <CBCentralManagerDelegate, CBPeripheralDelegate, CBPeripheralManagerDelegate> {
  CBCentralManager *centralManager;
  NSNumber* statusReceiver;
  NSString* initCallback;
  NSString* scanCallback;
  NSMutableDictionary* connections;

  CBPeripheralManager* peripheralManager;
  NSString* initPeripheralCallback;
  NSString* addServiceCallback;
  NSString* advertisingCallback;
  int requestId;
  NSMutableDictionary* requestsHash;
  NSMutableDictionary* servicesHash;

  BOOL writeQIsRunning;
  int writeQtype;
  NSInteger writeQLocation;
  NSInteger writeQLength;
  NSInteger writeQChunkSize;
  NSData *writeQData;
  CBCharacteristic *currentWriteCharacteristic;
}

- (void)cn1_initialize:(CDVInvokedUrlCommand *)command;
- (void)cn1_enable:(CDVInvokedUrlCommand *)command;
- (void)cn1_disable:(CDVInvokedUrlCommand *)command;
- (void)cn1_startScan:(CDVInvokedUrlCommand *)command;
- (void)cn1_stopScan:(CDVInvokedUrlCommand *)command;
- (void)cn1_retrieveConnected:(CDVInvokedUrlCommand *)command;
- (void)cn1_bond:(CDVInvokedUrlCommand *)command;
- (void)cn1_unbond:(CDVInvokedUrlCommand *)command;
- (void)cn1_connect:(CDVInvokedUrlCommand *)command;
- (void)cn1_reconnect:(CDVInvokedUrlCommand *)command;
- (void)cn1_disconnect:(CDVInvokedUrlCommand *)command;
- (void)cn1_close:(CDVInvokedUrlCommand *)command;
- (void)cn1_discover:(CDVInvokedUrlCommand *)command;
- (void)cn1_services:(CDVInvokedUrlCommand *)command;
- (void)cn1_characteristics:(CDVInvokedUrlCommand *)command;
- (void)cn1_descriptors:(CDVInvokedUrlCommand *)command;
- (void)cn1_read:(CDVInvokedUrlCommand *)command;
- (void)cn1_subscribe:(CDVInvokedUrlCommand *)command;
- (void)cn1_unsubscribe:(CDVInvokedUrlCommand *)command;
- (void)cn1_write:(CDVInvokedUrlCommand *)command;
- (void)cn1_writeQ:(CDVInvokedUrlCommand *)command;
- (void)cn1_readDescriptor:(CDVInvokedUrlCommand *)command;
- (void)cn1_writeDescriptor:(CDVInvokedUrlCommand *)command;
- (void)cn1_rssi:(CDVInvokedUrlCommand *)command;
- (void)cn1_mtu:(CDVInvokedUrlCommand *)command;
- (void)cn1_requestConnectionPriority:(CDVInvokedUrlCommand *)command;
- (void)cn1_isInitialized:(CDVInvokedUrlCommand *)command;
- (void)cn1_isEnabled:(CDVInvokedUrlCommand *)command;
- (void)cn1_isScanning:(CDVInvokedUrlCommand *)command;
- (void)cn1_isBonded:(CDVInvokedUrlCommand *)command;
- (void)cn1_wasConnected:(CDVInvokedUrlCommand *)command;
- (void)cn1_isConnected:(CDVInvokedUrlCommand *)command;
- (void)cn1_isDiscovered:(CDVInvokedUrlCommand *)command;
- (void)cn1_hasPermission:(CDVInvokedUrlCommand *)command;
- (void)cn1_requestPermission:(CDVInvokedUrlCommand *)command;
- (void)cn1_isLocationEnabled:(CDVInvokedUrlCommand *)command;
- (void)cn1_requestLocation:(CDVInvokedUrlCommand *)command;
- (void)cn1_retrievePeripheralsByAddress:(CDVInvokedUrlCommand *)command;

- (void)cn1_initializePeripheral:(CDVInvokedUrlCommand *)command;
- (void)cn1_addService:(CDVInvokedUrlCommand *)command;
- (void)cn1_removeService:(CDVInvokedUrlCommand *)command;
- (void)cn1_removeAllServices:(CDVInvokedUrlCommand *)command;
- (void)cn1_startAdvertising:(CDVInvokedUrlCommand *)command;
- (void)cn1_stopAdvertising:(CDVInvokedUrlCommand *)command;
- (void)cn1_isAdvertising:(CDVInvokedUrlCommand *)command;
- (void)cn1_respond:(CDVInvokedUrlCommand *)command;
- (void)cn1_notify:(CDVInvokedUrlCommand *)command;

@end
