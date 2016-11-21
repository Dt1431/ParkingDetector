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
    double userLat;
    double userLng;
    double userSpeed;
    double parkLat;
    double parkLng;
    BOOL isParked;
    BOOL isPDEnabled;
    BOOL foundFirstActivity;
    int isBkLocEnabled;
    int isActivityEnabled;
    BOOL updateParkLocation;
    BOOL checkActivities;
    double lastParkLat;
    double lastParkLng;
    NSDate* lastParkDate;
    NSString* lastParkID;
    NSString* initiatedBy;
    NSString* userId;
    CMMotionActivityManager* motionActivityManager;
    NSString* curBT;
    NSString* verifiedBT;
    BOOL isBTVerified;
    BOOL isActivityVerified;
    int conformationCount;
    BOOL pendingDetection;
    BOOL isParking;
    BOOL isParkingKnown;
    NSUserDefaults* defaults;
    NSMutableArray* notCarAudio;
    NSMutableArray *geofences;
}

@property NSString* endpoint;
@property NSNumber* showMessages;
@property int askedForConformationMax;
@property NSString* curAudioPort;

@property (nonatomic, strong) AVPlayer* audioPlayer;
@property (nonatomic, strong) CLLocationManager *locationManager;

+ (id)sharedManager;
- (void)sendUpdateNotification:(NSString*)message;
- (void)sendAlertNotification:(NSString*)audioPort;
- (void)sendParkNotification;
- (void)sendDeparkNotification;
- (NSString*)buildSettingsJSON;
- (void)setCarAudioPort:(NSString*)newAudioPort;
- (void)setNotCarAudioPort:(NSString*)newAudioPort;
- (void)resetBluetooth;
- (void)disableParkingDetector;
- (void)enableParkingDetector;
- (void)setParkLat:(double)lat andLng: (double)lng;
- (void)checkActivitiesBySpeed;
- (void)checkPastMotionActivities;
- (void)checkFutureMotionActivities;
- (void)sendParkingEventToServer: (int)parkingEvent userInitiated: (BOOL)userInitiated;
- (void)failedActivityCheck1;
- (void)failedActivityCheck2;
- (void)waitingForActivityCheck: (NSString*)curActivityDesc;
- (void)getCurrentLocation;
- (void)runParkingDetector: (BOOL)waitForBluetooth;
- (BOOL)prepareAudioSession;
- (BOOL)isHeadsetPluggedIn;
- (NSString*)getBTPortName;
- (NSString*)getAudioPortName;
- (void)addNewGeofenceWithLat: (double) newLat andLng: (double) newLng setLastPark: (BOOL) setLastPark;
- (void)loadAllGeofences;
- (void)saveAllGeofences;

@end
