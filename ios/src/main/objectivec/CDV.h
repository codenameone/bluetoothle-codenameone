#import <Foundation/Foundation.h>

#define CDV_EXEC_LOG(...) NSLog(__VA_ARGS__)

typedef NS_ENUM(NSInteger, CDVCommandStatus) {
    CDVCommandStatus_NO_RESULT = 0,
    CDVCommandStatus_OK,
    CDVCommandStatus_CLASS_NOT_FOUND_EXCEPTION,
    CDVCommandStatus_ILLEGAL_ACCESS_EXCEPTION,
    CDVCommandStatus_INSTANTIATION_EXCEPTION,
    CDVCommandStatus_MALFORMED_URL_EXCEPTION,
    CDVCommandStatus_IO_EXCEPTION,
    CDVCommandStatus_INVALID_ACTION,
    CDVCommandStatus_JSON_EXCEPTION,
    CDVCommandStatus_ERROR
};

@class CDVPluginResult;

@protocol CDVCommandDelegate <NSObject>
- (void)sendPluginResult:(CDVPluginResult*)result callbackId:(NSString*)callbackId;
@end

@interface CDVPlugin : NSObject
@property (nonatomic, assign) id<CDVCommandDelegate> commandDelegate;
- (void)dispose;
@end

@interface CDVInvokedUrlCommand : NSObject
@property (nonatomic, retain) NSArray* arguments;
@property (nonatomic, retain) NSString* callbackId;
@property (nonatomic, retain) NSString* className;
@property (nonatomic, retain) NSString* methodName;

- (id)initWithArguments:(NSArray*)arguments callbackId:(NSString*)callbackId className:(NSString*)className methodName:(NSString*)methodName;
@end

@interface CDVPluginResult : NSObject
@property (nonatomic, retain) NSNumber* status;
@property (nonatomic, retain) NSNumber* keepCallback;
@property (nonatomic, retain) id message;

+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsDictionary:(NSDictionary*)message;
+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsArray:(NSArray*)message;
+ (CDVPluginResult*)resultWithStatus:(CDVCommandStatus)status messageAsString:(NSString*)message;
- (void)setKeepCallbackAsBool:(BOOL)b;
- (NSString*)argumentsAsJSON;
@end
