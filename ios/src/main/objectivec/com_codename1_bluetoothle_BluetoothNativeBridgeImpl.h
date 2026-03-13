#import <Foundation/Foundation.h>
#import "BluetoothLePlugin.h"

@interface com_codename1_bluetoothle_BluetoothNativeBridgeImpl : NSObject {
    BluetoothLePlugin* _bluetoothPlugin;
}

-(BOOL)initialize:(BOOL)request param1:(BOOL)statusReceiver param2:(NSString*)restoreKey;
-(BOOL)enable;
-(BOOL)disable;
-(BOOL)startScan:(NSString*)servicesJson param1:(BOOL)allowDuplicates param2:(int)scanMode param3:(int)matchMode param4:(int)matchNum param5:(int)callbackType;
-(BOOL)stopScan;
-(BOOL)retrieveConnected:(NSString*)servicesJson;
-(BOOL)connect:(NSString*)address;
-(BOOL)reconnect:(NSString*)address;
-(BOOL)disconnect:(NSString*)address;
-(BOOL)close:(NSString*)address;
-(BOOL)discover:(NSString*)address;
-(BOOL)services:(NSString*)address param1:(NSString*)servicesJson;
-(BOOL)characteristics:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristicsJson;
-(BOOL)descriptors:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic;
-(BOOL)read:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic;
-(BOOL)subscribe:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic;
-(BOOL)unsubscribe:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic;
-(BOOL)write:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)value param4:(BOOL)noResponse;
-(BOOL)writeQ:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)value param4:(BOOL)noResponse;
-(BOOL)readDescriptor:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)descriptor;
-(BOOL)writeDescriptor:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)descriptor param4:(NSString*)value;
-(BOOL)rssi:(NSString*)address;
-(BOOL)mtu:(NSString*)address param1:(int)mtu;
-(BOOL)requestConnectionPriority:(NSString*)address param1:(NSString*)priority;
-(BOOL)isInitialized;
-(BOOL)isEnabled;
-(BOOL)isScanning;
-(BOOL)wasConnected:(NSString*)address;
-(BOOL)isConnected:(NSString*)address;
-(BOOL)isDiscovered:(NSString*)address;
-(BOOL)hasPermission;
-(BOOL)requestPermission;
-(BOOL)isLocationEnabled;
-(BOOL)requestLocation;
-(BOOL)isSupported;
@end
