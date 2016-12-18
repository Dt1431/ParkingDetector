/********* ParkingDetector.m Cordova Plugin Implementation *******/

#import <CoreBluetooth/CoreBluetooth.h>
#import <ExternalAccessory/ExternalAccessory.h>
#import <CoreLocation/CoreLocation.h>
#import <CoreMotion/CoreMotion.h>
#import <AVFoundation/AVFoundation.h>


@interface ParkingDetectorService: NSObject <CBCentralManagerDelegate, CLLocationManagerDelegate> {
    //DT NOTE: Maybe use property instead? Think the additional overhead is insignificant
    CBCentralManager* centralManager;
    NSDate* lastDetectionDate;
    NSDate* lastUpdateMessage;
    double userLat;
    double userLng;
    double userSpeed;
    double parkLat;
    double parkLng;
    BOOL isParked;
    BOOL isPDEnabled;
    BOOL firstTime;
    BOOL foundFirstActivity;
    long isBkLocEnabled;
    long isActivityEnabled;
    BOOL updateParkLocation;
    BOOL checkActivities;
    double lastParkLat;
    double lastParkLng;
    long lastParkDate;
    NSString* pendingCallbackID;
    NSString* lastParkID;
    NSString* initiatedBy;
    NSString* userId;
    CMMotionActivityManager* motionActivityManager;
    NSString* curBT;
    NSString* verifiedBT;
    BOOL isBTVerified;
    BOOL isActivityVerified;
    long conformationCount;
    BOOL pendingDetection;
    UIBackgroundTaskIdentifier pendingDetectionID;
    BOOL isParking;
    BOOL isParkingKnown;
    NSUserDefaults* defaults;
    NSMutableArray* notCarAudio;
    NSMutableArray *geofences;
}

@property NSString* endpoint;
@property NSString* showMessages;
@property int askedForConformationMax;
@property NSString* curAudioPort;
@property BOOL wasLaunchedByLocation;


@property (nonatomic, strong) AVPlayer* audioPlayer;
@property (nonatomic, strong) CLLocationManager *locationManager;

+ (id)sharedManager;
- (void)onClose:(NSNotification *)notification;
- (void)handleRouteChange:(NSNotification*)notification;
- (void)sendUpdateNotification:(NSString*)message;
- (void)sendAlertNotification:(NSString*)audioPort;
- (void)sendParkNotification;
- (void)sendDeparkNotification;
- (void)sendSettingsChangeNotification;
- (NSString*)buildSettingsJSON;
- (void)setCarAudioPort:(NSString*)newAudioPort;
- (void)setNotCarAudioPort:(NSString*)newAudioPort;
- (void)resetBluetooth;
- (void)disableParkingDetector;
- (void)enableParkingDetector;
- (void)setParkLat:(double)lat andLng: (double)lng;
- (void)saveLastParkLat:(double)lat andLng: (double)lng;
- (void)clearLastPark;
- (void)checkActivitiesBySpeed;
- (void)checkPastMotionActivities;
- (void)checkFutureMotionActivities;
- (void)sendParkingEventToServer: (int)parkingEvent userInitiated: (BOOL)userInitiated;
- (void)failedActivityCheck1;
- (void)failedActivityCheck2;
- (void)waitingForActivityCheck: (NSString*)curActivityDesc;
- (void)startDetection;
- (void)runParkingDetector: (BOOL)waitForBluetooth;
- (BOOL)prepareAudioSession;
- (BOOL)isHeadsetPluggedIn;
- (NSString*)getBTPortName;
- (NSString*)getAudioPortName;
- (void)addNewGeofenceWithLat: (double) newLat andLng: (double) newLng setLastPark: (BOOL) setLastPark;
- (void)loadAllGeofences;
- (void)saveAllGeofences;

@end
