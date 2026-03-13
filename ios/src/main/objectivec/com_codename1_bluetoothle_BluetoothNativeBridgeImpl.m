#import <Foundation/Foundation.h>
#import "com_codename1_bluetoothle_BluetoothNativeBridgeImpl.h"
#import "BluetoothLeCommandDelegateImpl.h"
#import <objc/message.h>

@implementation com_codename1_bluetoothle_BluetoothNativeBridgeImpl

-(BluetoothLePlugin*)getBluetoothPlugin {
    if (_bluetoothPlugin == nil) {
        _bluetoothPlugin = [[BluetoothLePlugin alloc] init];
        _bluetoothPlugin.commandDelegate = [[BluetoothLeCommandDelegateImpl alloc] init];
    }
    return _bluetoothPlugin;
}

-(id)parseJson:(NSString*)json {
    if (json == nil || [json length] == 0) {
        return nil;
    }
    NSError* error;
    NSData* data = [json dataUsingEncoding:NSUTF8StringEncoding];
    id parsed = [NSJSONSerialization JSONObjectWithData:data options:nil error:&error];
    if (error != nil) {
        return nil;
    }
    return parsed;
}

-(BOOL)executeAction:(NSString*)action args:(NSDictionary*)args {
    NSArray *commandArgs = nil;
    if (args == nil) {
        commandArgs = [NSArray array];
    } else {
        commandArgs = [NSArray arrayWithObjects:args, nil];
    }

    CDVInvokedUrlCommand *command = [[CDVInvokedUrlCommand alloc] initWithArguments:commandArgs callbackId:action className:@"BluetoothLePlugin" methodName:action];
    BOOL ret = [self executeCommand:command];
    [command release];
    return ret;
}

- (BOOL)executeCommand:(CDVInvokedUrlCommand*)command {
    BOOL retVal = YES;
    CDVPlugin* plugin = [self getBluetoothPlugin];
    NSString* methodName = [NSString stringWithFormat:@"%@:", command.methodName];
    SEL selector = NSSelectorFromString(methodName);
    if ([plugin respondsToSelector:selector]) {
        ((void (*)(id, SEL, id))objc_msgSend)(plugin, selector, command);
    } else {
        NSLog(@"ERROR: Method '%@' not defined in Plugin '%@'", methodName, command.className);
        retVal = NO;
    }
    return retVal;
}

-(BOOL)initialize:(BOOL)request param1:(BOOL)statusReceiver param2:(NSString*)restoreKey {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          [NSNumber numberWithBool:request], @"request",
                          [NSNumber numberWithBool:statusReceiver], @"statusReceiver",
                          (restoreKey == nil ? @"" : restoreKey), @"restoreKey",
                          nil];
    return [self executeAction:@"initialize" args:args];
}

-(BOOL)enable { return [self executeAction:@"enable" args:nil]; }
-(BOOL)disable { return [self executeAction:@"disable" args:nil]; }

-(BOOL)startScan:(NSString*)servicesJson param1:(BOOL)allowDuplicates param2:(int)scanMode param3:(int)matchMode param4:(int)matchNum param5:(int)callbackType {
    NSMutableDictionary* args = [NSMutableDictionary dictionary];
    [args setObject:[NSNumber numberWithBool:allowDuplicates] forKey:@"allowDuplicates"];
    [args setObject:[NSNumber numberWithInt:scanMode] forKey:@"scanMode"];
    [args setObject:[NSNumber numberWithInt:matchMode] forKey:@"matchMode"];
    [args setObject:[NSNumber numberWithInt:matchNum] forKey:@"matchNum"];
    [args setObject:[NSNumber numberWithInt:callbackType] forKey:@"callbackType"];
    id services = [self parseJson:servicesJson];
    if (services != nil) {
        [args setObject:services forKey:@"services"];
    }
    return [self executeAction:@"startScan" args:args];
}

-(BOOL)stopScan { return [self executeAction:@"stopScan" args:nil]; }

-(BOOL)retrieveConnected:(NSString*)servicesJson {
    NSMutableDictionary* args = [NSMutableDictionary dictionary];
    id services = [self parseJson:servicesJson];
    if (services != nil) {
        [args setObject:services forKey:@"services"];
    }
    return [self executeAction:@"retrieveConnected" args:args];
}

-(BOOL)connect:(NSString*)address { return [self executeAction:@"connect" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)reconnect:(NSString*)address { return [self executeAction:@"reconnect" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)disconnect:(NSString*)address { return [self executeAction:@"disconnect" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)close:(NSString*)address { return [self executeAction:@"close" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)discover:(NSString*)address { return [self executeAction:@"discover" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }

-(BOOL)services:(NSString*)address param1:(NSString*)servicesJson {
    NSMutableDictionary* args = [NSMutableDictionary dictionaryWithObject:address forKey:@"address"];
    id services = [self parseJson:servicesJson];
    if (services != nil) {
        [args setObject:services forKey:@"services"];
    }
    return [self executeAction:@"services" args:args];
}

-(BOOL)characteristics:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristicsJson {
    NSMutableDictionary* args = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                 address, @"address",
                                 service, @"service",
                                 nil];
    id characteristics = [self parseJson:characteristicsJson];
    if (characteristics != nil) {
        [args setObject:characteristics forKey:@"characteristics"];
    }
    return [self executeAction:@"characteristics" args:args];
}

-(BOOL)descriptors:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          nil];
    return [self executeAction:@"descriptors" args:args];
}

-(BOOL)read:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          nil];
    return [self executeAction:@"read" args:args];
}

-(BOOL)subscribe:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          nil];
    return [self executeAction:@"subscribe" args:args];
}

-(BOOL)unsubscribe:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          nil];
    return [self executeAction:@"unsubscribe" args:args];
}

-(BOOL)write:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)value param4:(BOOL)noResponse {
    NSMutableDictionary* args = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                 address, @"address",
                                 service, @"service",
                                 characteristic, @"characteristic",
                                 value, @"value",
                                 nil];
    if (noResponse) {
        [args setObject:@"noResponse" forKey:@"type"];
    }
    return [self executeAction:@"write" args:args];
}

-(BOOL)writeQ:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)value param4:(BOOL)noResponse {
    NSMutableDictionary* args = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                 address, @"address",
                                 service, @"service",
                                 characteristic, @"characteristic",
                                 value, @"value",
                                 nil];
    if (noResponse) {
        [args setObject:@"noResponse" forKey:@"type"];
    }
    return [self executeAction:@"writeQ" args:args];
}

-(BOOL)readDescriptor:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)descriptor {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          descriptor, @"descriptor",
                          nil];
    return [self executeAction:@"readDescriptor" args:args];
}

-(BOOL)writeDescriptor:(NSString*)address param1:(NSString*)service param2:(NSString*)characteristic param3:(NSString*)descriptor param4:(NSString*)value {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          service, @"service",
                          characteristic, @"characteristic",
                          descriptor, @"descriptor",
                          value, @"value",
                          nil];
    return [self executeAction:@"writeDescriptor" args:args];
}

-(BOOL)rssi:(NSString*)address { return [self executeAction:@"rssi" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }

-(BOOL)mtu:(NSString*)address param1:(int)mtu {
    NSDictionary* args = [NSDictionary dictionaryWithObjectsAndKeys:
                          address, @"address",
                          [NSNumber numberWithInt:mtu], @"mtu",
                          nil];
    return [self executeAction:@"mtu" args:args];
}

-(BOOL)requestConnectionPriority:(NSString*)address param1:(NSString*)priority {
    NSMutableDictionary* args = [NSMutableDictionary dictionaryWithObject:address forKey:@"address"];
    if (priority != nil && [priority length] > 0) {
        [args setObject:priority forKey:@"connectionPriority"];
    }
    return [self executeAction:@"requestConnectionPriority" args:args];
}

-(BOOL)isInitialized { return [self executeAction:@"isInitialized" args:nil]; }
-(BOOL)isEnabled { return [self executeAction:@"isEnabled" args:nil]; }
-(BOOL)isScanning { return [self executeAction:@"isScanning" args:nil]; }
-(BOOL)hasPermission { return [self executeAction:@"hasPermission" args:nil]; }
-(BOOL)requestPermission { return [self executeAction:@"requestPermission" args:nil]; }
-(BOOL)isLocationEnabled { return [self executeAction:@"isLocationEnabled" args:nil]; }
-(BOOL)requestLocation { return [self executeAction:@"requestLocation" args:nil]; }

-(BOOL)wasConnected:(NSString*)address { return [self executeAction:@"wasConnected" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)isConnected:(NSString*)address { return [self executeAction:@"isConnected" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }
-(BOOL)isDiscovered:(NSString*)address { return [self executeAction:@"isDiscovered" args:[NSDictionary dictionaryWithObject:address forKey:@"address"]]; }

-(BOOL)isSupported {
    return YES;
}

-(void)dealloc {
    if (_bluetoothPlugin != nil) {
        [_bluetoothPlugin dispose];
        _bluetoothPlugin = nil;
    }
    [super dealloc];
}

@end
