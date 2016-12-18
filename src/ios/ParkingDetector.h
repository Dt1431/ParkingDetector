/********* ParkingDetector.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <ExternalAccessory/ExternalAccessory.h>
#import <CoreLocation/CoreLocation.h>
#import <CoreMotion/CoreMotion.h>
#import <AVFoundation/AVFoundation.h>
#import "MBProgressHUD.h"
#import "ParkingDetectorService.h"

@interface ParkingDetector : CDVPlugin {
    ParkingDetectorService *parkingDetectorService;
    UIView *webView;
}

- (void)initPlugin:(CDVInvokedUrlCommand*)command;
- (void)userInitiatedPark:(CDVInvokedUrlCommand*)command;
- (void)userInitiatedDepark:(CDVInvokedUrlCommand*)command;
- (void)startParkingDetector:(CDVInvokedUrlCommand*)command;
- (void)confirmAudioPort:(CDVInvokedUrlCommand*)command;
- (void)resetBluetooth:(CDVInvokedUrlCommand *)command;
- (void)disableParkingDetector:(CDVInvokedUrlCommand*)command;
- (void)enableParkingDetector:(CDVInvokedUrlCommand*)command;
- (void)getDetectorStatus:(CDVInvokedUrlCommand*)command;

- (void)onResume:(NSNotification *)notification;
- (void)onPause:(NSNotification *)notification;
- (void)onClose:(NSNotification *)notification;
- (void)onFinishLaunching:(NSNotification *)notification;
- (void)sendMessage:(NSNotification*)notification;
- (void)showBTAlertBox:(NSNotification*)notification;
- (void)parkedEvent:(NSNotification*)notification;
- (void)deparkedEvent:(NSNotification*)notification;
- (void)settingsChangeEvent:(NSNotification*)notification;

@end
