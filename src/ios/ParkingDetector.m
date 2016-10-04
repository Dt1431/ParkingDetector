/********* ParkingDetector.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <ExternalAccessory/ExternalAccessory.h>
#import <CoreLocation/CoreLocation.h>
#import <CoreMotion/CoreMotion.h>
#import <AVFoundation/AVFoundation.h>
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
    NSString *endpoint;
    NSNumber *showMessages;
    NSDate *lastBTDetectionDate;
    double userLat;
    double userLng;
    int askedForConformationMax;
    NSString *userId;
    CLLocationManager *locationManager;
    CMMotionActivityManager *motionActivityManager;
    NSString *curBT;
    NSString *verifiedBT;
    BOOL isVerified;
    int conformationCount;
    BOOL pendingActivityDetection;
    BOOL isParking;
    NSUserDefaults *defaults;
    NSMutableArray *notCarBT;
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
    askedForConformationMax =  [[command.arguments objectAtIndex:1] intValue];
    endpoint = [command.arguments objectAtIndex:2];
    
    //Initialize Central Manager
    if (nil == centralManager){
        NSDictionary *options = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:NO], CBCentralManagerOptionShowPowerAlertKey, nil];
        centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options:options];
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
    userId = [[[UIDevice currentDevice] identifierForVendor] UUIDString];
    defaults = [NSUserDefaults standardUserDefaults];
    
    if([defaults objectForKey:@"notCarBT"] != nil){
        isVerified = [defaults boolForKey:@"isVerified"];
        verifiedBT = [defaults objectForKey:@"verifiedBT"];
        conformationCount = [defaults integerForKey:@"conformationCount"];
        notCarBT = [NSMutableArray arrayWithArray:[defaults objectForKey:@"notCarBT"]];
    }else{
        NSLog(@"First Time");
        //First Time
        isVerified = NO;
        conformationCount = 0;
        notCarBT = [NSMutableArray new];
        [defaults setInteger:conformationCount forKey:@"conformationCount"];
        [defaults setBool:isVerified forKey:@"isVerified"];
        [defaults setObject:notCarBT forKey:@"notCarBT"];
        [defaults synchronize];
    }
    curBT = @"";
    pendingActivityDetection = NO;
    isParking = NO;
    NSLog(@"Not car BT: %@", notCarBT);
    NSLog(@"Conformation Count: %i", conformationCount);
    NSLog(@"is Verified:%s", isVerified ? "true" : "false");
    if(verifiedBT != nil){
        NSLog(@"Cars BT: %@", verifiedBT);
    }
    
    //Create audio stream,
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)sendMessage:(NSString*)message {
    if(showMessages){
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.webView.superview animated:YES];
            // Configure for text only and offset down
            hud.mode = MBProgressHUDModeText;
            hud.detailsLabelText = message;
            hud.margin = 10.f;
            hud.yOffset = 150.f;
            hud.removeFromSuperViewOnHide = YES;
            [hud hideAnimated:YES afterDelay:3];
        }];
    }else{
        NSLog(@"SteetSmart Message: %@",message);
    }

}

/************** BT Confirmation Alert Box *****************/
- (void)showBTAlertBox:(NSString*)BTName{
    conformationCount ++;
    [defaults setInteger:conformationCount forKey:@"conformationCount"];
    [defaults synchronize];
    UIAlertController * alert =   [UIAlertController
                                  alertControllerWithTitle:[NSString stringWithFormat:@"Is %@ your car's bluetooth?", curBT]
                                  message:[NSString stringWithFormat:@"%@ uses your car's bluetooth to crowdsource open parking spaces.", [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleDisplayName"]]
                                  preferredStyle:UIAlertControllerStyleActionSheet];
    
    UIAlertAction* no = [UIAlertAction
                         actionWithTitle:@"No"
                         style:UIAlertActionStyleDefault
                         handler:^(UIAlertAction * action){

                             [alert dismissViewControllerAnimated:YES completion:nil];
                             NSLog(@"Adding %@ to NOT BT array", curBT);
                             [notCarBT addObject:curBT];
                             [defaults setObject:notCarBT forKey:@"notCarBT"];
                             [defaults synchronize];
                             
                         }];
    
    UIAlertAction* yes = [UIAlertAction
                             actionWithTitle:@"Yes"
                             style:UIAlertActionStyleDefault
                             handler:^(UIAlertAction * action){
                             
                                 [alert dismissViewControllerAnimated:YES completion:nil];
                                 isVerified = YES;
                                 [defaults setBool:isVerified forKey:@"isVerified"];
                                 NSLog(@"Setting %@ as car's bluetooth", curBT);
                                 verifiedBT = curBT;
                                 [defaults setObject:verifiedBT forKey:@"verifiedBT"];
                                 isParking = NO;
                                 [defaults synchronize];
                                 //Check to see if de-parking has occured
                                 [self getCurrentLocation];

                             }];
    
    [alert addAction:yes];
    [alert addAction:no];
    [alert addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
        [alert dismissViewControllerAnimated:YES completion:nil];
    }]];
    [self.viewController presentViewController:alert animated:YES completion:nil];
}

/************** Motion Activity Functions *********************/

- (void)checkPastMotionActivities{
    if([CMMotionActivityManager isActivityAvailable]){
        [motionActivityManager queryActivityStartingFromDate:[NSDate dateWithTimeIntervalSinceNow:-60]
                                                      toDate:[NSDate new]
                                                     toQueue:[NSOperationQueue new]
                                                 withHandler:^(NSArray *activities, NSError *error) {
                                                     
            Boolean foundFirst = NO;
            for (CMMotionActivity *activity in activities) {
                NSLog(@"Activity Found: %@ Starting at: %@",activity.description, activity.startDate);
                if(isParking){
                    //Look for high confidence automotive, followed by stationary or walking
                    if(!foundFirst){
                        if(activity.confidence == 2 && activity.automotive){
                            foundFirst = YES;
                            break;
                        }
                    }
                    /*
                    else{
                        if(activity.confidence == 2 && (activity.stationary || activity.walking)){
                            [self sendParkingEventToServer: 1];
                            return;
                        }
                    }
                     */
                }else{
                    //Look for high confidence stationary or walking, followed by automotive
                    if(!foundFirst){
                        if(activity.confidence == 2 && (activity.stationary || activity.walking)){
                            foundFirst = YES;
                             break;
                        }
                    }
                    /*
                    else{
                        if(activity.confidence == 2 && activity.automotive){
                            [self sendParkingEventToServer: -1];
                            return;
                        }
                    }
                    */
                }
            }
            //If parking / de-parking is partially validated, listen for future activities
            if(foundFirst){
                pendingActivityDetection = YES;
                [self checkFutureMotionActivities];
            }else{
                if(isParking){
                    [self sendMessage: @"Failed parking activity check 1\rdriving not detected durring last minute"];
                }else{
                    [self sendMessage: @"Failed new  activity check 1\rwalking or stationary not detected durring last minute"];
                }
            }
        }];
    }
}

- (void)checkFutureMotionActivities{
    
    if([CMMotionActivityManager isActivityAvailable] == YES){
        //register for Coremotion notifications
        pendingActivityDetection = YES;
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
            }else if(secs > 2){
                if(isParking){
                    [self sendMessage: [NSString stringWithFormat:@"Waiting for car to stop\r%@",
                                        [NSString stringWithFormat:@"Countdown: %i", (int)(60 - secs)]]];
                }else{
                    [self sendMessage: [NSString stringWithFormat:@"Waiting for car to begin driving\r%@",
                                        [NSString stringWithFormat:@"Countdown: %i", (int)(60 - secs)]]];
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
                    [self sendMessage: @"Failed activity check 2\r parking NOT detected"];
                }else{
                    [self sendMessage: @"Failed activity check 2\r new parking spot NOT detected"];
                }
            }
        }];
    }
}

/************** Post parking data *******************************/

- (void)sendParkingEventToServer: (double)parkingEvent{
    if(parkingEvent == 1){
        [self sendMessage: @"Parking detected"];
    }else{
        [self sendMessage: @"New parking spot detected"];
    }
    NSString* version = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleVersion"];
 
    NSString *post = [NSString stringWithFormat:@"userId=%@&userLat=%f@&userLng=%f@&activity=%i&curBT=%@&isVerified=%s&os=%@&version=%@",
                      userId, userLat, userLng, (int) parkingEvent, curBT, isVerified ? "true" : "false", @"ios",version];

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


/************** Location Functions *********************/

- (void)getCurrentLocation{
    if([CLLocationManager locationServicesEnabled]){
        if([CLLocationManager authorizationStatus]==kCLAuthorizationStatusDenied){
            [self sendMessage: @"Location Services are not permitted.\rCannot determine parking spot location"];
        }else{
            //Get current location
            if ([locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]){
                [locationManager requestAlwaysAuthorization];
            }
            [locationManager requestLocation];
        }
    }else{
        [self sendMessage: @"Location Services are disabled.\rCannot determine parking spot location"];
    }
    
}

/** Location Manager Delegates **/
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
        //Looks like we've got some Bluetooth potential

        if(!isVerified){
            [self sendMessage: @"Starting Invalidated Parking Detector"];
        }else{
            [self sendMessage: @"Starting Validated Parking Detector"];
        }

        /************************ Audio Session Approach *********************/

        [self prepareAudioSession];
        curBT = [self getBTPortName];
        
        if(![curBT isEqual: @"Not BT"]
           && isVerified != YES
           && conformationCount < askedForConformationMax
           && ![notCarBT containsObject: curBT]){
            
            [self showBTAlertBox:curBT];
        
        }else{
            if(![curBT isEqual: @"Not BT"] && ![notCarBT containsObject: curBT]){
                isParking = NO;
                lastBTDetectionDate = [NSDate new];
                //Check to see if de-parking has occured
                [self getCurrentLocation];
            }else{
                NSLog(@"Audio connection is not BT or BT is in not car array");
            }

        }
        
        /********************** Shared Accessary Approach **************/
        /* ONLY WORKS WITH MFI (Made for iOS devices) products. Right now this is pretty
         useless, but CarPlay https://developer.apple.com/carplay/ appears to be gaining momentum */
        
        /*
         
        //Picker
        [[EAAccessoryManager sharedAccessoryManager] showBluetoothAccessoryPickerWithNameFilter:nil completion:^(NSError *error) {
        }];

        //Prints List
        NSArray *accessoryList = [[EAAccessoryManager sharedAccessoryManager] connectedAccessories];
        NSLog(@"*********** Connected device list **************");
        for (EAAccessory *acc in accessoryList) {
            NSLog(@"Connected device: %@",acc.name);
        }

        */
        
        
        /********************** Bluetooth Low Enery *****************/
        /* This was a waste of time! BLE is not designed for audio so highly unlikely to be used by cars */
        
        /*

         //Scan for specific BLE broadcasts
         [centralManager scanForPeripheralsWithServices:[NSArray arrayWithObject:[CBUUID UUIDWithString:@"111F"]] options:@{ CBCentralManagerScanOptionAllowDuplicatesKey : @NO }];

         //Scan for all BLE broadcasts
         [centralManager scanForPeripheralsWithServices:nil options:nil];

         */
        
    }
}
/*********************** Methods for Audio Approach ***************************/

- (BOOL)prepareAudioSession {
    
    // deactivate session, just like the highlander there can only be one
    BOOL success = [[AVAudioSession sharedInstance] setActive:NO error: nil];
    if (!success) {
        NSLog(@"deactivationError");
    }
    
    // set audio session category and options
    success = [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryAmbient withOptions:AVAudioSessionCategoryOptionAllowBluetooth error:nil];
    if (!success) {
        NSLog(@"setCategoryError");
    }
    
    // activate audio session
    success = [[AVAudioSession sharedInstance] setActive:YES error: nil];
    if (!success) {
        NSLog(@"activationError");
    }else{
        /* Register for speaker route change notifcation
         https://developer.apple.com/reference/foundation/nsnotification.name/1616493-avaudiosessionroutechange
         */
        NSNotificationCenter *defaultCenter = [NSNotificationCenter defaultCenter];
        [defaultCenter addObserver:self
                          selector:@selector(handleRouteChange:)
                              name:@"AVAudioSessionRouteChangeNotification"
                            object:nil];
    }
    
    return success;
}

/* http://stackoverflow.com/questions/21292586/are-headphones-plugged-in-ios7 */
- (BOOL)isHeadsetPluggedIn {
    AVAudioSessionRouteDescription* route = [[AVAudioSession sharedInstance] currentRoute];
    for (AVAudioSessionPortDescription* desc in [route outputs]) {
        if ([[desc portType] isEqualToString:AVAudioSessionPortHeadphones])
            return YES;
    }
    return NO;
}

- (NSString*)getBTPortName {
    AVAudioSessionRouteDescription* route = [[AVAudioSession sharedInstance] currentRoute];
    for (AVAudioSessionPortDescription* desc in [route outputs]) {
        if ([[desc portType] isEqualToString:AVAudioSessionPortBluetoothA2DP])
            return [desc portName];
    }
    return @"Not BT";
}

-(void)handleRouteChange:(NSNotification*)notification{
    AVAudioSession *session = [ AVAudioSession sharedInstance ];
    NSString* seccReason = @"";
    NSInteger  reason = [[[notification userInfo] objectForKey:AVAudioSessionRouteChangeReasonKey] integerValue];
    //  AVAudioSessionRouteDescription* prevRoute = [[notification userInfo] objectForKey:AVAudioSessionRouteChangePreviousRouteKey];
    switch (reason) {
        case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
            seccReason = @"The route changed because no suitable route is now available for the specified category.";
            break;
        case AVAudioSessionRouteChangeReasonWakeFromSleep:
            seccReason = @"The route changed when the device woke up from sleep.";
            break;
        case AVAudioSessionRouteChangeReasonOverride:
            seccReason = @"The output route was overridden by the app.";
            break;
        case AVAudioSessionRouteChangeReasonCategoryChange:
            seccReason = @"The category of the session object changed.";
            break;
        case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
            if(![curBT isEqual: @"Not BT"] && ![notCarBT containsObject: curBT]){
                [self sendMessage: [NSString stringWithFormat:@"Disconnected from %@",curBT]];
                isParking = YES;
                lastBTDetectionDate = [NSDate new];
                //Check if parking occured
                [self getCurrentLocation];
            }else{
                NSLog(@"Lost conenction was not BT or BT in not car BT list");
            }
            break;
        case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
            curBT = [self getBTPortName];
            if(![curBT isEqual: @"Not BT"] && ![notCarBT containsObject: curBT]){
                isParking = NO;
                lastBTDetectionDate = [NSDate new];
                //Check for de-parking
                [self getCurrentLocation];
            }else{
                NSLog(@"Not conencted to BT or BT in not car BT list");
            }
            break;
        case AVAudioSessionRouteChangeReasonUnknown:
        default:
            seccReason = @"The reason for the change is unknown.";
            break;
    }
    NSLog(@"Change in route: %@", seccReason);
    
    /*
    AVAudioSessionPortDescription *input = [[session.currentRoute.inputs count]?session.currentRoute.inputs:nil objectAtIndex:0];
    if (input.portType == AVAudioSessionPortHeadsetMic) {
        
    }*/
}

/****************************** Required delegates for BLE aka CoreBluetooth Approach, NOT CURRENTLY USED *************************/

- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI {

    NSLog(@"Discovered %@ at %@", peripheral.name, RSSI);
    if(peripheral.name != nil){
        [self sendMessage: [NSString stringWithFormat:@"Discovered  %@", peripheral.name]];
    }
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
