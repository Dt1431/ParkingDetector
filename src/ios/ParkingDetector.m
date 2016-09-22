/********* ParkingDetector.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <ExternalAccessory/ExternalAccessory.h>
#import <CoreLocation/CoreLocation.h>
#import <CoreMotion/CoreMotion.h>
#import "MBProgressHUD.h"

//Error Messages
NSString *const logPoweredOff = @"Bluetooth powered off";
NSString *const logUnauthorized = @"Bluetooth unauthorized";
NSString *const logUnknown = @"Bluetooth unknown state";
NSString *const logResetting = @"Bluetooth resetting";
NSString *const logUnsupported = @"Bluetooth unsupported";
NSString *const logNotInit = @"Bluetooth not initialized";
NSString *const logNotEnabled = @"Bluetooth not enabled";
NSString *const logOperationUnsupported = @"Operation unsupported";


@interface ParkingDetector : CDVPlugin <CBCentralManagerDelegate, CBPeripheralDelegate, CLLocationManagerDelegate> {
  // Member variables go here.
    CBCentralManager *centralManager;
    NSNumber* statusReceiver;
    NSString* endpoint;
    NSNumber* showMessages;
    NSDate* lastBTDetectionDate;
    double userLat;
    double userLng;
    NSNumber* askedForConformationMax;
    NSString* userId;
    CLLocationManager* locationManager;
    CMMotionActivityManager* motionActivityManager;
    NSString* curBT;
    Boolean isVerified;
    Boolean pendingActivityDetection;
    Boolean isParking;
}

- (void)initPlugin:(CDVInvokedUrlCommand*)command;
- (void)sendMessage:(NSString*)message;
- (void)getCurrentLocation;
- (void)checkPastMotionActivities;
- (void)checkFutureMotionActivities;
- (void)sendParkingEventToServer:(double)parkingEvent;

@end



@implementation ParkingDetector

- (void)initPlugin:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    
    //Get arguments from Cordova
    showMessages = [command.arguments objectAtIndex:0];
    askedForConformationMax =  [command.arguments objectAtIndex:1];
    endpoint = [command.arguments objectAtIndex:2];
    
    //Initialize Central Manager
    if (nil == centralManager){
        centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
    }
    //Initalize Location Manager
    if (nil == locationManager){
        locationManager = [[CLLocationManager alloc] init];
        locationManager.delegate = self;
        locationManager.desiredAccuracy = kCLLocationAccuracyBest;
    }
    //Initalize Motion Activity Manager
    if (nil == motionActivityManager){
        motionActivityManager=[[CMMotionActivityManager alloc]init];
    }
    //Set parking variables
    pendingActivityDetection = NO;
    isParking = NO;
    userId = [[[UIDevice currentDevice] identifierForVendor] UUIDString];
    curBT = @"";
    isVerified = false;
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)sendMessage:(NSString*)message {
    if(showMessages){
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.webView.superview animated:YES];
            // Configure for text only and offset down
            hud.mode = MBProgressHUDModeText;
            hud.label.text = message;
            hud.margin = 10.f;
            hud.yOffset = 150.f;
            hud.removeFromSuperViewOnHide = YES;
            [hud hideAnimated:YES afterDelay:3];
        }];
    }else{
        NSLog(@"SteetSmart Message: %@",message);
    }

}

- (void)getCurrentLocation{
    if([CLLocationManager locationServicesEnabled]){
        if([CLLocationManager authorizationStatus]==kCLAuthorizationStatusDenied){
            [self sendMessage: @"Location Services are not permitted.\nCannot determine parking spot location"];
        }else{
            //Get current location
            if ([locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]){
                [locationManager requestAlwaysAuthorization];
            }
            [locationManager requestLocation];
        }
    }else{
        [self sendMessage: @"Location Services are disabled.\nCannot determine parking spot location"];
    }

}

/************** Motion Activity Functions *********************/

- (void)checkPastMotionActivities{
    if([CMMotionActivityManager isActivityAvailable]){
        [motionActivityManager queryActivityStartingFromDate:[NSDate dateWithTimeIntervalSinceNow:-60*60*24]
                                                      toDate:[NSDate new]
                                                     toQueue:[NSOperationQueue new]
                                                 withHandler:^(NSArray *activities, NSError *error) {
                                                     
            Boolean foundFirst = NO;
            for (CMMotionActivity *activity in activities) {
                if(isParking){
                    //Look for high confidence automotive, followed by stationary or walking
                    if(!foundFirst){
                        if(activity.confidence == 2 && activity.automotive){
                            foundFirst = YES;
                        }
                    }
                    else{
                        if(activity.confidence == 2 && (activity.stationary || activity.walking)){
                            [self sendParkingEventToServer: 1];
                            return;
                        }
                    }
                }else{
                    //Look for high confidence stationary or walking, followed by automotive
                    if(!foundFirst){
                        if(activity.confidence == 2 && (activity.stationary || activity.walking)){
                            foundFirst = YES;
                        }
                    }
                    else{
                        if(activity.confidence == 2 && activity.automotive){
                            [self sendParkingEventToServer: -1];
                            return;
                        }
                    }
                }
            }
            //If parking / de-parking is partially validated, listen for future activities
            if(foundFirst){
                pendingActivityDetection = YES;
                if(isParking){
                    [self sendMessage: @"Waiting for car to stop"];
                }else{
                    [self sendMessage: @"Waiting for car to begin driving"];
                }
                [self checkFutureMotionActivities];
            }else{
                if(isParking){
                    [self sendMessage: @"Failed activity check/n parking NOT detected"];
                }else{
                    [self sendMessage: @"Failed activity check/n new parking spot NOT detected"];
                }
            }
        }];
    }
}

- (void)checkFutureMotionActivities{
    
    if([CMMotionActivityManager isActivityAvailable] == YES){
        //register for Coremotion notifications
        [motionActivityManager startActivityUpdatesToQueue:[NSOperationQueue mainQueue] withHandler:^(CMMotionActivity * activity){
            NSDate *now = [NSDate new];
            NSTimeInterval secs = [now timeIntervalSinceDate:lastBTDetectionDate];
            if(secs > 60){
                pendingActivityDetection = NO;
            }
            if(!isParking && activity.confidence == 2 && activity.automotive){
                [motionActivityManager stopActivityUpdates];
                [self sendParkingEventToServer: 1];
                return;
            }
            else if(isParking && activity.confidence == 2 && (activity.stationary || activity.walking)){
                [motionActivityManager stopActivityUpdates];
                [self sendParkingEventToServer: -1];
                return;
            }else{
                if(isParking){
                    [self sendMessage: @"Waiting for car to stop"];
                }else{
                    [self sendMessage: @"Waiting for car to begin driving"];
                }
            }
            /*
            USEFUL for debugging
            
            NSLog(@"Got a core motion update");
            NSLog(@"Current activity date is %f",activity.timestamp);
            NSLog(@"Current activity confidence from a scale of 0 to 2 - 2 being best- is: %ld",activity.confidence);
            NSLog(@"Current activity type is unknown: %i",activity.unknown);
            NSLog(@"Current activity type is stationary: %i",activity.stationary);
            NSLog(@"Current activity type is walking: %i",activity.walking);
            NSLog(@"Current activity type is running: %i",activity.running);
            NSLog(@"Current activity type is automotive: %i",activity.automotive);
             
             */
            if(pendingActivityDetection == NO){
                [motionActivityManager stopActivityUpdates];
                if(isParking){
                    [self sendMessage: @"Failed activity check/n parking NOT detected"];
                }else{
                    [self sendMessage: @"Failed activity check/n new parking spot NOT detected"];
                }
            }
        }];
        
        pendingActivityDetection = NO;
    }
}

/************** Post parking data *******************************/

- (void)sendParkingEventToServer: (double)parkingEvent{
    if(parkingEvent == 1){
        [self sendMessage: @"Parking detected"];
    }else{
        [self sendMessage: @"New parking spot detected"];
    }
 
    NSString *post = [NSString stringWithFormat:@"userId=%@&userLat=%f@&userLng=%f@&activity=%f&curBT=%@&isVerified=%hhu",userId, userLat, userLng, parkingEvent, curBT,isVerified];
    NSLog(@"POST STRING: %@",post);
    NSData *postData = [post dataUsingEncoding:NSASCIIStringEncoding allowLossyConversion:YES];
    NSString *postLength = [NSString stringWithFormat:@"%lu",(unsigned long)[postData length]];
    NSURL *url = [NSURL URLWithString:endpoint];
    
    // Create a POST request
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    [request setValue:postLength forHTTPHeaderField:@"Content-Length"];
    [request setValue:@"application/x-www-form-urlencoded" forHTTPHeaderField:@"Content-Type"];
    [request setValue:@"en-US" forHTTPHeaderField:@"Content-Language"];
    [request setHTTPMethod:@"POST"];
    [request setHTTPBody:postData];
    
    //Create a task.
    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request
                                                                 completionHandler:^(NSData *data,
                                                                                     NSURLResponse *response,
                                                                                     NSError *error){
        if (!error){
            NSLog(@"Status code: %li", (long)((NSHTTPURLResponse *)response).statusCode);
        }
        else{
            NSLog(@"Error: %@", error.localizedDescription);
        }
    }];
    
    // Start the task.
    [task resume];
    // Reset variables

}


/************** Location Manager Delegates *********************/

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error{
    NSString* message = [NSString stringWithFormat:@"Location Error: %@",error.description];
    [self sendMessage: message];
}
- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations{
    CLLocation* location = [locations lastObject];
    NSLog(@"latitude %+.6f, longitude %+.6f\n",
          location.coordinate.latitude,
          location.coordinate.longitude);
    
    userLat = location.coordinate.latitude;
    userLng = location.coordinate.longitude;

    //Validate parking
    [self checkPastMotionActivities];
}


/************** Central Manager Delegates *********************/

- (void) centralManagerDidUpdateState:(CBCentralManager *)central {
    
    //Decide on error message
    NSString* error = nil;
    switch ([centralManager state])     {
        case CBCentralManagerStatePoweredOff: {
            error = logPoweredOff;
            break;
        }
            
        case CBCentralManagerStateUnauthorized: {
            error = logUnauthorized;
            break;
        }
            
        case CBCentralManagerStateUnknown: {
            error = logUnknown;
            break;
        }
            
        case CBCentralManagerStateResetting: {
            error = logResetting;
            break;
        }
            
        case CBCentralManagerStateUnsupported: {
            error = logUnsupported;
            break;
        }
            
        case CBCentralManagerStatePoweredOn: {
            //Bluetooth on!
            break;
        }
    }
    
    NSDictionary* returnObj = nil;
    CDVPluginResult* pluginResult = nil;
    
    //If error message exists, send error
    if (error != nil) {
        [self sendMessage: error];
    } else {
        /*
        NSLog(@"*********** Connected device list **************");
        [[EAAccessoryManager sharedAccessoryManager] showBluetoothAccessoryPickerWithNameFilter:nil completion:^(NSError *error) {
            
        }];
        NSArray *accessoryList = [[EAAccessoryManager sharedAccessoryManager] connectedAccessories];
        for (EAAccessory *acc in accessoryList) {
            NSLog(@"Connected device: %@",acc.name);
        }
        //Else enabling was successful
        [centralManager scanForPeripheralsWithServices:[NSArray arrayWithObject:[CBUUID UUIDWithString:@"111F"]] options:@{ CBCentralManagerScanOptionAllowDuplicatesKey : @NO }];
        
        

        [centralManager scanForPeripheralsWithServices:nil options:nil];
        */
        
        
        [self sendMessage: @"Starting Parking Detector"];
        //[self getCurrentLocation];
        lastBTDetectionDate = [NSDate new];
    }
}

- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI {
    
    NSLog(@"Discovered %@ at %@", peripheral.name, RSSI);
    //[self sendMessage: @"Discovered \(peripheral.name)"];
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
    
    NSLog(@"Testing Testing, Peripheral connected");
    [self sendMessage: @"Connected"];
}


- (void)centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
    [self sendMessage: @"Connect"];
    //Get connection
    NSString* btID = peripheral.identifier;

}



@end
