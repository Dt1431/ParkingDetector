
#import "ParkingDetectorService.h"

@implementation ParkingDetectorService

/* Singleton setup */
+ (id)sharedManager {
    static ParkingDetectorService *sharedParkingDetectorService = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedParkingDetectorService  = [[self alloc] init];
    });
    return sharedParkingDetectorService;
}

- (id)init {
    if (self = [super init]) {

        //Initialize Central Manager
        if (nil == centralManager){
            NSDictionary *options = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:NO], CBCentralManagerOptionShowPowerAlertKey, nil];
            centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options:options];
        }
        
        //Initialize Location Manager
        if (nil == self.locationManager){
            self.locationManager = [[CLLocationManager alloc] init];
            self.locationManager.delegate = self;
            self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
        }
        if([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways){
            isBkLocEnabled = 1;
        }else{
            isBkLocEnabled = 0;
        }
        [defaults setInteger:isBkLocEnabled forKey:@"pd_isBkLocEnabled"];
        
        //Initalize Motion Activity Manager
        if (nil == motionActivityManager){
            motionActivityManager=[[CMMotionActivityManager alloc]init];
        }
        
        //Set parking variables
        userId = [[[UIDevice currentDevice] identifierForVendor] UUIDString];
        defaults = [NSUserDefaults standardUserDefaults];
        
        if([defaults objectForKey:@"pd_notCarAudio"] != nil){
            isBTVerified = [defaults boolForKey:@"pd_isBTVerified"];
            isPDEnabled = [defaults boolForKey:@"pd_isPDEnabled"];
            verifiedBT = [defaults objectForKey:@"pd_verifiedBT"];
            conformationCount = [defaults integerForKey:@"pd_conformationCount"];
            isActivityEnabled = [defaults integerForKey:@"pd_isActivityEnabled"];
            notCarAudio = [NSMutableArray arrayWithArray:[defaults objectForKey:@"pd_notCarAudio"]];
            lastParkLat = [defaults doubleForKey:@"pd_lastParkLat"];
            lastParkLng = [defaults doubleForKey:@"pd_lastParkLng"];
            lastParkDate = [defaults objectForKey:@"pd_lastParkDate"];
        }else{
            NSLog(@"First Time");
            //First Time
            isBTVerified = NO;
            isActivityEnabled = -1;
            isPDEnabled = YES;
            conformationCount = 0;
            verifiedBT = @"Not Set";
            notCarAudio = [NSMutableArray new];
            [defaults setInteger:conformationCount forKey:@"pd_conformationCount"];
            [defaults setInteger:isActivityEnabled forKey:@"pd_isActivityEnabled"];
            [defaults setBool:isPDEnabled forKey:@"pd_isPDEnabled"];
            [defaults setBool:isBTVerified forKey:@"pd_isBTVerified"];
            [defaults setObject:notCarAudio forKey:@"pd_notCarAudio"];
            [defaults setObject:verifiedBT forKey:@"pd_verifiedBT"];
            [defaults synchronize];
        }
        self.curAudioPort = @"";
        curBT = @"";
        pendingDetection = NO;
        foundFirstActivity = NO;
        isParking = NO;
        isParkingKnown = NO;
        updateParkLocation = NO;
        checkActivities = NO;
        NSLog(@"Not car BT: %@", notCarAudio);
        NSLog(@"Is background location enabled: %i", isBkLocEnabled);
        NSLog(@"Is activity enabled: %i", isActivityEnabled);
        NSLog(@"Conformation Count: %i", conformationCount);
        NSLog(@"is Verified:%s", isBTVerified ? "true" : "false");
        if(verifiedBT != nil){
            NSLog(@"Cars BT: %@", verifiedBT);
        }
        [self loadAllGeofences];
        
        //Create background audio stream
        NSURL *url = [NSURL URLWithString:@"http://daveturner.tech/Level-up-sound-effect.mp3"];
        NSData *data = [NSData dataWithContentsOfURL:url];
        AVAudioPlayer *player =[[AVAudioPlayer alloc] initWithData:data error:nil];
        player.numberOfLoops = 1;
        self.audioPlayer = player;
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:nil];
        [[AVAudioSession sharedInstance] setActive: YES error: nil];
    }
    return self;
}

- (void)dealloc {
    // Should never be called
}

/************** Notifications and JSON for UI *********************/

- (void)sendUpdateNotification:(NSString*)message {
    NSDictionary *dict = @{@"message": message};
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"messageNotification"
     object:nil
     userInfo:dict];
}
- (void)sendAlertNotification:(NSString*)audioPort {
    conformationCount ++;
    [defaults setInteger:conformationCount forKey:@"pd_conformationCount"];
    [defaults synchronize];
    NSDictionary *dict = @{@"audioPort": audioPort};
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"alertNotification"
     object:nil
     userInfo:dict];

}
- (void)sendParkNotification{
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"parkNotification"
     object:nil];
}
- (void)sendDeparkNotification{
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"deparkNotification"
     object:nil];
}

- (NSString*)buildSettingsJSON{
    NSMutableString* jsonString = [NSMutableString stringWithFormat:@"{\"isPDEnabled\": %s, \"isBkLocEnabled\": %s, \"isActivityEnabled\": %s, \"isBTVerified\": %s, \"verifiedBT\": \"%@\", \"curAudioPort\": \"%@\", \"geofences\":[", isPDEnabled ? "true" : "false", isBkLocEnabled < 0 ? "\"unknown\"" : isBkLocEnabled > 0 ? "true" : "false", isActivityEnabled < 0 ? "\"unknown\"" : isActivityEnabled > 0 ? "true" : "false", isBTVerified ? "true" : "false", verifiedBT, self.curAudioPort];
    
    for (CLCircularRegion *geofence in geofences) {
        [jsonString appendString:[NSString stringWithFormat:@"{\"lat\": %f, \"lng\": %f, \"radius\": %f},", geofence.center.latitude, geofence.center.longitude, geofence.radius]];
    }
    if([geofences count] > 0){
        [jsonString deleteCharactersInRange:NSMakeRange([jsonString length]-1, 1)];
    }
    [jsonString appendString:@"]"];
    if(lastParkLat != 0){
        [jsonString appendString:[NSMutableString stringWithFormat:@", \"lastParkLat\": %f, \"lastParkLng\": %f, \"lastParkDate\": %f", lastParkLat, lastParkLng, [lastParkDate timeIntervalSince1970]]];
    }
    [jsonString appendString:@"}"];
    
    return jsonString;
}


- (void)setCarAudioPort:(NSString*)newAudioPort{
    isBTVerified = YES;
    notCarAudio = [NSMutableArray new];
    [defaults setBool:isBTVerified forKey:@"pd_isBTVerified"];
    NSLog(@"Setting %@ as car's speakers", self.curAudioPort);
    verifiedBT = newAudioPort;
    [defaults setObject:verifiedBT forKey:@"pd_verifiedBT"];
    [defaults setObject:notCarAudio forKey:@"pd_notCarAudio"];
    isParking = NO;
    isParkingKnown = YES;
    initiatedBy = @"audio confirm";
    [defaults synchronize];
    //Check to see if de-parking has occured
    [self getCurrentLocation];
}

- (void)setNotCarAudioPort:(NSString*)newAudioPort{
    NSLog(@"Adding %@ to NOT BT array", self.curAudioPort);
    [notCarAudio addObject: self.curAudioPort];
    [defaults setObject:notCarAudio forKey:@"pd_notCarAudio"];
    [defaults synchronize];
}

- (void)resetBluetooth{
    isBTVerified = NO;
    conformationCount = 0;
    pendingDetection = NO;
    notCarAudio = [NSMutableArray new];
    verifiedBT =  @"Not Set";
    [defaults setInteger:conformationCount forKey:@"pd_conformationCount"];
    [defaults setBool:isBTVerified forKey:@"pd_isBTVerified"];
    [defaults setObject:verifiedBT forKey:@"pd_verifiedBT"];
    [defaults setObject:notCarAudio forKey:@"pd_notCarAudio"];
    [defaults synchronize];
}


- (void)disableParkingDetector{
    isPDEnabled = NO;
    pendingDetection = NO;
    [defaults setBool:isPDEnabled forKey:@"pd_isPDEnabled"];
    [defaults synchronize];
}

- (void)enableParkingDetector{
    isPDEnabled = YES;
    [defaults setBool:isPDEnabled forKey:@"pd_isPDEnabled"];
    [defaults synchronize];
    [self runParkingDetector: true];
}

- (void)setParkLat:(double)lat andLng: (double)lng{
    parkLat = lat;
    parkLng = lng;
}

/************** Motion Activity Functions *********************/

- (void)checkActivitiesBySpeed{
    NSLog(@"SSD - Check by Speed");
    if(!pendingDetection){
        [self sendUpdateNotification: @"Stopping activity detection"];
        return;
    }
    NSDate *now = [NSDate new];
    int mphSpeed = (int)(userSpeed/0.44704);
    if(mphSpeed < 0){
        mphSpeed = 0;
    }
    NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
    if(secs > 120){
        pendingDetection = NO;
        if(!foundFirstActivity){
            [self failedActivityCheck1];
        }else{
            [self failedActivityCheck2];
        }
        return;
    }
    if(isParkingKnown){
        foundFirstActivity = YES;
    }
    if(!foundFirstActivity){
        if(userSpeed > 7){
            foundFirstActivity = YES;
            isParking = YES;
        }else if(userSpeed < 0.5){
            foundFirstActivity = YES;
            isParking = NO;
        }
        return;
        
    }else{
        if(isParking && userSpeed < 0.5){
            [self sendParkingEventToServer: -1 userInitiated:false];
        }
        else if(!isParking && userSpeed > 7){
            [self sendParkingEventToServer: 1 userInitiated:false];
        }else{
            [self waitingForActivityCheck: [NSString stringWithFormat:@"Speed: %i mph. ", (int)(userSpeed/0.44704)]];
        }
    }
}

- (void)checkPastMotionActivities{
    NSLog(@"SSD - IN Past Activities");
    
    if([CMMotionActivityManager isActivityAvailable]){
        [motionActivityManager queryActivityStartingFromDate:[NSDate dateWithTimeIntervalSinceNow:-60*5]
                                                      toDate:[NSDate new]
                                                     toQueue:[NSOperationQueue new]
                                                 withHandler:^(NSArray *activities, NSError *error) {
                                                     
                                                     foundFirstActivity = NO;
                                                     if(error.code == CMErrorMotionActivityNotAuthorized){
                                                         isActivityEnabled = 0;
                                                         [self checkActivitiesBySpeed];
                                                         return;
                                                     }else{
                                                         isActivityEnabled = 1;
                                                     }
                                                     [defaults setInteger:isActivityEnabled forKey:@"pd_isActivityEnabled"];
                                                     [defaults synchronize];
                                                     
                                                     NSLog(@"SSD - IN Past Activities Handeler");
                                                     
                                                     for (CMMotionActivity *activity in activities) {
                                                         NSLog(@"Activity Found: %@ DEBUG: %@", activity.startDate, activity.debugDescription);
                                                         if((isParking || !isParkingKnown) && activity.confidence >= 1 && activity.automotive){
                                                             foundFirstActivity = YES;
                                                             isParking = YES;
                                                             break;
                                                         }
                                                         if((!isParking || !isParkingKnown) && activity.confidence >= 1 && (activity.stationary || activity.walking)){
                                                             foundFirstActivity = YES;
                                                             isParking = NO;
                                                             break;
                                                         }
                                                     }
                                                     //If parking / de-parking is partially validated, listen for future activities
                                                     if(foundFirstActivity){
                                                         NSLog(@"Passed Activity Check 1");
                                                         [self checkFutureMotionActivities];
                                                     }else{
                                                         pendingDetection = NO;
                                                         [self failedActivityCheck1];
                                                     }
                                                 }];
    }else{
        [self checkActivitiesBySpeed];
    }
}

- (void)failedActivityCheck1{
    if(!isParkingKnown){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Failed %@ initiated parking activity check 1. Driving, walking or stationary were not detected durring last two minutes", initiatedBy]];
    }
    else if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Failed %@ initiated parking activity check 1. Driving not detected durring last two minutes", initiatedBy]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Failed %@ initiated new activity check 1. Walking or stationary not detected durring last two minutes", initiatedBy]];
    }
}

- (void)failedActivityCheck2{
    NSLog(@"Failed Activity Check 2");
    if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Failed %@ initiated activity check 2. Walking or stationary not detected.", initiatedBy]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Failed %@ initiated activity check 2. Driving not detected.", initiatedBy]];
    }
}

- (void)waitingForActivityCheck:(NSString*)curActivityDesc{
    NSDate *now = [NSDate new];
    int secs = (int)[now timeIntervalSinceDate:lastDetectionDate];
    if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"%@Waiting for car to stop. Countdown: %i", curActivityDesc, (120 - secs)]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"%@Waiting for car to begin driving. Countdown: %i", curActivityDesc, (120 - secs)]];
    }
}


- (void)checkFutureMotionActivities{
    
    if([CMMotionActivityManager isActivityAvailable] == YES){
        //register for Coremotion notifications
        [motionActivityManager startActivityUpdatesToQueue:[NSOperationQueue mainQueue] withHandler:^(CMMotionActivity * activity){
            if(!pendingDetection){
                [self sendUpdateNotification: @"Stopping activity detection"];
                [motionActivityManager stopActivityUpdates];
                return;
            }
            NSLog(@"New Activity Found: %@ Starting at: %@",activity.description, activity.startDate);
            NSMutableString* activityDesc = [NSMutableString stringWithFormat:@""];
            if(activity.unknown){
                [activityDesc appendString:@"Unknown, "];
            }
            if(activity.stationary){
                [activityDesc appendString:@"Stationary, "];
            }
            if(activity.walking){
                [activityDesc appendString:@"Walking, "];
            }
            if(activity.running){
                [activityDesc appendString:@"Running, "];
            }
            if(activity.automotive){
                [activityDesc appendString:@"Automotive, "];
            }
            if(activity.cycling){
                [activityDesc appendString:@"Cycling, "];
            }
            if([activityDesc length] > 1){
                [activityDesc deleteCharactersInRange:NSMakeRange([activityDesc  length]-2, 2)];
                [activityDesc appendString:@" detected. "];
            }
            NSDate *now = [NSDate new];
            NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
            if(secs > 120){
                pendingDetection = NO;
            }
            if(!isParking && activity.confidence >= 1 && activity.automotive){
                [motionActivityManager stopActivityUpdates];
                [self sendParkingEventToServer: 1 userInitiated:false];
                return;
            }
            else if(isParking && activity.confidence >= 1 && (activity.stationary || activity.walking)){
                [motionActivityManager stopActivityUpdates];
                [self addNewGeofenceWithLat: parkLat andLng: parkLng setLastPark: YES];
                [self sendParkingEventToServer: -1 userInitiated:false];
                return;
            }else if([activityDesc length] > 1){
                [self waitingForActivityCheck: activityDesc];
            }
            /*
             USEFUL for debugging
             */
            NSLog(@"Got a core motion update");
            NSLog(@"Current activity date is %f",activity.timestamp);
            NSLog(@"Current activity confidence from a scale of 0 to 2 - 2 being best- is: %ld",activity.confidence);
            NSLog(@"Current activity type is unknown: %i",activity.unknown);
            NSLog(@"Current activity type is stationary: %i",activity.stationary);
            NSLog(@"Current activity type is walking: %i",activity.walking);
            NSLog(@"Current activity type is running: %i",activity.running);
            NSLog(@"Current activity type is automotive: %i",activity.automotive);
            
            if(pendingDetection == NO){
                [motionActivityManager stopActivityUpdates];
                [self failedActivityCheck2];
            }
        }];
    }
}

/************** Post parking data *******************************/

- (void)sendParkingEventToServer: (int)parkingEvent userInitiated: (BOOL)userInitiated{
    if(userInitiated){
        initiatedBy = @"user";
        isActivityVerified = false;
    }else{
        isActivityVerified = true;
    }
    pendingDetection = NO;
    if(parkingEvent == 1){
        [self sendUpdateNotification: @"New parking spot detected"];
        [self sendParkNotification];

    }else{
        [self sendUpdateNotification: @"New parking spot detected"];
        [self sendDeparkNotification];

    }
    NSString* version = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleVersion"];
    
    NSString *post = [NSString stringWithFormat:@"userId=%@&parkLat=%f&parkLng=%f&activity=%i&audioPort=%@&isValidated=%s&os=%@&version=%@&initiatedBy=%@&activityVerified=%s", userId, parkLat, parkLng, parkingEvent, self.curAudioPort, isBTVerified ? "true" : "false", @"ios",version, initiatedBy, isActivityVerified ? "true" : "false"];
    
    NSLog(@"POST STRING: %@",post);
    NSData *postData = [post dataUsingEncoding:NSASCIIStringEncoding allowLossyConversion:YES];
    NSString *postLength = [NSString stringWithFormat:@"%lu",(unsigned long)[postData length]];
    NSURL *url = [NSURL URLWithString:self.endpoint];
    
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
    if(!isPDEnabled){
        [self sendUpdateNotification: @"Parking detector is disabled"];
        return;
    }
    if([CLLocationManager locationServicesEnabled]){
        if([CLLocationManager authorizationStatus]==kCLAuthorizationStatusDenied){
            [self sendUpdateNotification: @"Location Services are not permitted. Cannot determine parking spot location"];
            isBkLocEnabled = 0;
        }else{
            if([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways){
                isBkLocEnabled = 1;
            }
            else if([self.locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]){
                [self.locationManager requestAlwaysAuthorization];
            }
            pendingDetection = YES;
            updateParkLocation = YES;
            checkActivities = YES;
            foundFirstActivity = NO;
            lastDetectionDate = [NSDate new];
            NSLog(@"Requesting location");
            [self.locationManager stopUpdatingLocation];
            [self.locationManager startUpdatingLocation];
            [self sendUpdateNotification: [NSString stringWithFormat:@"Starting detection, initiated by %@.", initiatedBy]];
        }
    }else{
        isBkLocEnabled = 0;
        [self sendUpdateNotification: @"Location Services are disabled.Cannot determine parking spot location"];
    }
    [defaults setInteger:isBkLocEnabled forKey:@"pd_isBkLocEnabled"];
    [defaults synchronize];
}

/** Location Manager Delegates **/

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error{
    NSLog(@"Location Error: %@",[NSString stringWithFormat:@"Location Error: %@",error.description]);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations{
    if(!isPDEnabled){
        [self sendUpdateNotification: @"Parking detector is disabled"];
        [self.locationManager stopUpdatingLocation];
        return;
    }
    CLLocation* location = [locations lastObject];
    NSLog(@"latitude %+.6f, longitude %+.6f\n",
          location.coordinate.latitude,
          location.coordinate.longitude);
    
    userLat = location.coordinate.latitude;
    userLng = location.coordinate.longitude;
    userSpeed = location.speed;
    
    NSDate *now = [NSDate new];
    NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
    if(secs > 60*5){
        [self.locationManager stopUpdatingLocation];
    }
    if(updateParkLocation){
        parkLat = userLat;
        parkLng = userLng;
        [self addNewGeofenceWithLat: parkLat andLng: parkLng setLastPark: NO];
        updateParkLocation = NO;
    }
    if(checkActivities){
        //Validate parking
        NSLog(@"SSD - Checking Past Activities");
        [self checkPastMotionActivities];
        checkActivities = NO;
    }
    else if(pendingDetection && !isActivityEnabled){
        NSLog(@"SSD - Check Activity from Location");
        [self checkActivitiesBySpeed];
    }
}
//For geofence
- (void)locationManager:(CLLocationManager *)manager didEnterRegion:(CLCircularRegion *)region{
    //Check for parking
    self.curAudioPort = [self getAudioPortName];
    isParkingKnown = NO;
    initiatedBy = @"geofence";
    [self getCurrentLocation];
}
- (void)playAudioTest{
    [self.audioPlayer play];
}

- (void)runParkingDetector: (BOOL)waitForBluetooth{
    if(!isPDEnabled){
        [self sendUpdateNotification: @"Parking detector is disabled"];
        return;
    }
    else if(!isBTVerified){
        [self sendUpdateNotification: @"Starting Invalidated Parking Detector"];
    }else{
        [self sendUpdateNotification: @"Starting Validated Parking Detector"];
    }
    
    /************************ Audio Session Approach *********************/
    
    [self prepareAudioSession];
    curBT = [self getBTPortName];
    self.curAudioPort = [self getAudioPortName];
    
    if(![curBT isEqual: @"Not BT"]
       && ![curBT isEqual: @"Not Valid Port"]
       && isBTVerified != YES
       && conformationCount < self.askedForConformationMax
       && ![notCarAudio containsObject: self.curAudioPort]){
        
        [self sendAlertNotification:self.curAudioPort];
        
    }else{
        if([self.curAudioPort isEqual: verifiedBT] || (![curBT isEqual: @"Not BT"] && ![notCarAudio containsObject: self.curAudioPort])){
            isParking = NO;
            isParkingKnown = YES;
            initiatedBy = @"PD start";
            //Check to see if de-parking has occured
            [self getCurrentLocation];
        }else{
            if(!waitForBluetooth){
                isParking = YES;
                isParkingKnown = NO;
                initiatedBy = @"PD start";
                [self getCurrentLocation];
            }
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

/************** Central Manager Delegates *********************/

- (void) centralManagerDidUpdateState:(CBCentralManager *)central {
    //Error Messages
    NSString *const logPoweredOff = @"Bluetooth powered off";
    NSString *const logUnauthorized = @"Bluetooth unauthorized";
    NSString *const logUnknown = @"Bluetooth unknown state";
    NSString *const logResetting = @"Bluetooth resetting";
    NSString *const logUnsupported = @"Bluetooth unsupported";
    
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
    
    //If error message exists, send error
    if (error != nil) {
        [self sendUpdateNotification: error];
    } else {
        //Looks like we've got some Bluetooth potential
        [self runParkingDetector: true];
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
    success = [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback withOptions: AVAudioSessionCategoryOptionAllowBluetooth | AVAudioSessionCategoryOptionMixWithOthers error:nil];
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
        if ([[desc portType] isEqualToString:AVAudioSessionPortBluetoothA2DP]
            ||[[desc portType] isEqualToString:AVAudioSessionPortBluetoothHFP])
            NSLog(@"SSD - portBefore - %@", [desc portName]);
            return [desc portName];
    }
    return @"Not BT";
}
- (NSString*)getAudioPortName {
    AVAudioSessionRouteDescription* route = [[AVAudioSession sharedInstance] currentRoute];
    for (AVAudioSessionPortDescription* desc in [route outputs]) {
        if (![[desc portType] isEqualToString:AVAudioSessionPortBuiltInSpeaker]
            && ![[desc portType] isEqualToString:AVAudioSessionPortBuiltInReceiver])
            return [desc portName];
    }
    return @"No Valid Port";
}

-(void)handleRouteChange:(NSNotification*)notification{
    AVAudioSession *session = [ AVAudioSession sharedInstance ];
    NSString* seccReason = @"";
    NSDate *now = [NSDate new];
    NSString *lastPort = self.curAudioPort;
    NSString *lastBT = curBT;
    NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
    
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
            if([lastPort isEqual: verifiedBT] || (![lastBT isEqual: @"Not BT"] && ![notCarAudio containsObject: lastPort])){
                if(pendingDetection){
                    pendingDetection = NO;
                    foundFirstActivity = NO;
                }
                NSLog(@"Disconnected from %@",self.curAudioPort);
                isParking = YES;
                isParkingKnown = YES;
                initiatedBy = @"BT disconnect";
                //Check if parking occured
                [self getCurrentLocation];
            }else{
                NSLog(@"Lost conenction was not BT or BT in not car BT list");
            }
            curBT = [self getBTPortName];
            self.curAudioPort = @"No Valid Port";
            break;
        case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
            curBT = [self getBTPortName];
            self.curAudioPort = [self getAudioPortName];
            NSLog(@"SSD - portAfter - %@", self.curAudioPort);

            if([self.curAudioPort isEqual: verifiedBT] || (![curBT isEqual: @"Not BT"] && ![notCarAudio containsObject: self.curAudioPort])){
                if(pendingDetection){
                    pendingDetection = NO;
                    foundFirstActivity = NO;
                }
                isParking = NO;
                isParkingKnown = YES;
                initiatedBy = @"BT connect";
                if(isBTVerified != YES
                   && conformationCount < self.askedForConformationMax
                   && ![notCarAudio containsObject: self.curAudioPort]){
                    [self sendAlertNotification: self.curAudioPort];
                }else{
                    //Check for de-parking
                    [self getCurrentLocation];
                }
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

/***************** GeoFencing Stuff ****************************/

- (void)addNewGeofenceWithLat: (double) newLat andLng: (double) newLng setLastPark: (BOOL) setLastPark{
    CLLocation *tempLocation;
    CLCircularRegion *tempRegion;
    CLLocationDistance distance;
    CLLocation *newLocation = [[CLLocation alloc] initWithLatitude:newLat longitude:newLng];
    NSTimeInterval now = [[NSDate date] timeIntervalSince1970];
    NSString *identifierString = [NSString stringWithFormat:@"%f", now];
    NSLog(@"Adding new geofence %@",identifierString);
    
    //Check if overlapping
    for (NSInteger index = ([geofences count] - 1); index >= 0; index--) {
        tempRegion = [geofences objectAtIndex:index];
        tempLocation = [[CLLocation alloc] initWithLatitude:tempRegion.center.latitude longitude:tempRegion.center.longitude];
        distance = [tempLocation distanceFromLocation:newLocation];
        if(distance <= 200){
            //Overlap, delete
            [geofences removeObjectAtIndex:index];
            NSLog(@"Geofence overlap for %@",identifierString);
            NSLog(@"Removing geofence %@",tempRegion.identifier);
            break;
        }
    }
    
    //Check if full and remove untill we're under 20 (max allowed)
    while([geofences count] >= 20){
        tempRegion = [geofences objectAtIndex:0];
        NSLog(@"Removing geofence %@",tempRegion.identifier);
        [geofences removeObjectAtIndex:0];
    }
    
    //Create a geofence and add to array
    CLLocationCoordinate2D center = CLLocationCoordinate2DMake(newLat,
                                                               newLng);
    CLCircularRegion *newGeofence = [[CLCircularRegion alloc]initWithCenter:center
                                                                     radius:100.0
                                                                 identifier:identifierString];
    //Add new geofence and sync everything
    [geofences addObject: newGeofence];
    [self saveAllGeofences];
    [self loadAllGeofences];
    if(setLastPark){
        lastParkID = identifierString;
        lastParkLat = newLat;
        lastParkLng = newLng;
        lastParkDate = [NSDate date];
        
        [defaults setDouble:lastParkLat forKey:@"pd_lastParkLat"];
        [defaults setDouble:lastParkLng forKey:@"pd_lastParkLng"];
        [defaults setObject:lastParkDate forKey:@"pd_lastParkDate"];
        [defaults setObject:lastParkID forKey:@"pd_lastParkID"];
        [defaults synchronize];
    }
}

- (void)loadAllGeofences{
    //Clear existing, just in case
    
    geofences = [NSMutableArray array];
    for (CLCircularRegion *monitored in [self.locationManager monitoredRegions])
        [self.locationManager stopMonitoringForRegion:monitored];
    
    //Grab from user preferences
    
    NSArray *savedItems = [defaults arrayForKey:@"pd_geofences"];
    if (savedItems) {
        for (id savedItem in savedItems) {
            CLCircularRegion *geofence = [NSKeyedUnarchiver unarchiveObjectWithData:savedItem];
            if ([geofence isKindOfClass:[CLCircularRegion class]]) {
                NSLog(@"Loading geofence %@",geofence.identifier);
                [geofences addObject:geofence];
                [self.locationManager startMonitoringForRegion:geofence];
            }
        }
    }else{
        NSLog(@"No saved geofences");
    }
}

- (void)saveAllGeofences{
    NSMutableArray *items = [NSMutableArray array];
    for (CLCircularRegion *geofence in geofences) {
        id item = [NSKeyedArchiver archivedDataWithRootObject:geofence];
        [items addObject:item];
    }
    [[NSUserDefaults standardUserDefaults] setObject:items forKey:@"pd_geofences"];
    [[NSUserDefaults standardUserDefaults] synchronize];
}


@end

