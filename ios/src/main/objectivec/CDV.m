#import "CDV.h"

@implementation CDVPlugin
- (void)dispose {
}
@end

@implementation CDVInvokedUrlCommand
- (id)initWithArguments:(NSArray*)arguments callbackId:(NSString*)callbackId className:(NSString*)className methodName:(NSString*)methodName {
    self = [super init];
    if (self) {
        self.arguments = arguments;
        self.callbackId = callbackId;
        self.className = className;
        self.methodName = methodName;
    }
    return self;
}

- (void)dealloc {
    self.arguments = nil;
    self.callbackId = nil;
    self.className = nil;
    self.methodName = nil;
    [super dealloc];
}
@end

@implementation CDVPluginResult

+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsDictionary:(NSDictionary*)message {
    CDVPluginResult *result = [[CDVPluginResult alloc] init];
    result.status = [NSNumber numberWithInteger:status];
    result.keepCallback = [NSNumber numberWithBool:NO];
    result.message = message;
    return [result autorelease];
}

+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsArray:(NSArray*)message {
    CDVPluginResult *result = [[CDVPluginResult alloc] init];
    result.status = [NSNumber numberWithInteger:status];
    result.keepCallback = [NSNumber numberWithBool:NO];
    result.message = message;
    return [result autorelease];
}

+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsString:(NSString*)message {
    CDVPluginResult *result = [[CDVPluginResult alloc] init];
    result.status = [NSNumber numberWithInteger:status];
    result.keepCallback = [NSNumber numberWithBool:NO];
    result.message = message;
    return [result autorelease];
}

- (void)setKeepCallbackAsBool:(BOOL)b {
    self.keepCallback = [NSNumber numberWithBool:b];
}

- (NSString*)argumentsAsJSON {
    if (self.message == nil) {
        return @"{}";
    }

    if ([self.message isKindOfClass:[NSString class]]) {
        return (NSString*)self.message;
    }

    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:self.message options:0 error:&error];
    if (error != nil || data == nil) {
        return @"{}";
    }
    return [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] autorelease];
}

- (void)dealloc {
    self.status = nil;
    self.keepCallback = nil;
    self.message = nil;
    [super dealloc];
}
@end
