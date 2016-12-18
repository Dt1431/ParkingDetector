
#import "ParkingDetectorService.h"

#define IS_OS_9_OR_LATER ([[[UIDevice currentDevice] systemVersion] floatValue] >= 9.0)

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
            lastParkDate = [defaults integerForKey:@"pd_lastParkDate"];
            firstTime = [defaults boolForKey:@"pd_firstTime"];
        }else{
            NSLog(@"First Time");
            //First Time
            firstTime = true;
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
            [defaults setInteger:firstTime forKey:@"pd_firstTime"];
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
        lastUpdateMessage = [NSDate new];
        lastUpdateMessage = [NSDate dateWithTimeInterval:-100 sinceDate:lastUpdateMessage];
        NSLog(@"Not car BT: %@", notCarAudio);
        NSLog(@"Is activity enabled: %ld", isActivityEnabled);
        NSLog(@"Conformation Count: %ld", conformationCount);
        NSLog(@"is Verified:%s", isBTVerified ? "true" : "false");
        if(verifiedBT != nil){
            NSLog(@"Cars BT: %@", verifiedBT);
        }
        //Create background audio stream
        
        /*
        NSURL *url = [NSURL URLWithString:@"http://daveturner.tech/Level-up-sound-effect.mp3"];
        NSData *data = [NSData dataWithContentsOfURL:url];
        AVAudioPlayer *player =[[AVAudioPlayer alloc] initWithData:data error:nil];
        player.numberOfLoops = 1;
        self.audioPlayer = player; 
         */

        //Initialize Central Manager
        if (nil == centralManager){
            NSDictionary *options = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithBool:NO], CBCentralManagerOptionShowPowerAlertKey, nil];
            centralManager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options:options];
        }
        
        //Initialize Location Manager
        if (nil == self.locationManager){
            self.locationManager = [[CLLocationManager alloc] init];
            self.locationManager.delegate = self;
            if (IS_OS_9_OR_LATER) {
                self.locationManager.allowsBackgroundLocationUpdates = YES;
            }
        }
        //Reg
        NSNotificationCenter *defaultCenter = [NSNotificationCenter defaultCenter];
        [defaultCenter addObserver:self
                          selector:@selector(onClose:)
                              name:UIApplicationWillTerminateNotification
                            object:nil];

        
        //[self loadAllGeofences]; Don't think we need to load each time
        
        //Initalize Motion Activity Manager
        if (nil == motionActivityManager){
            motionActivityManager = [[CMMotionActivityManager alloc]init];
        }
        
        //Register for audio port change updates
        [defaultCenter addObserver:self
                          selector:@selector(handleRouteChange:)
                              name:@"AVAudioSessionRouteChangeNotification"
                            object:nil];


        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:nil];
        [[AVAudioSession sharedInstance] setActive: YES error: nil];
    }
    return self;
}

- (void)dealloc {
    // Should never be called
}

- (void)onClose:(NSNotification *)notification{
    NSLog(@"Stopping parking detector service");
    UIApplication *app = [UIApplication sharedApplication];
    if (pendingDetectionID != UIBackgroundTaskInvalid){
        pendingDetectionID = UIBackgroundTaskInvalid;
        [app endBackgroundTask: pendingDetectionID];
    }
    [self.locationManager stopUpdatingLocation];
    [self.locationManager startMonitoringSignificantLocationChanges];
}


/************** Notifications and JSON for UI *********************/

- (void)sendSettingsChangeNotification{
    NSString *message = @"settingsChange";
    NSDictionary *dict = @{@"message": message};
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"settingsChangeNotification"
     object:nil
     userInfo:dict];
}

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
    NSLog(@"In Send Park Note");
    NSDictionary *dict = @{@"lastParkLng": [[NSNumber alloc] initWithDouble:lastParkLng], @"lastParkLat":[[NSNumber alloc] initWithDouble:lastParkLat]};
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"parkNotification"
     object:nil
     userInfo:dict];
}

- (void)sendDeparkNotification{
    NSDictionary *dict = @{@"lastDeparkLng": [[NSNumber alloc] initWithDouble:parkLng], @"lastDeparkLat":[[NSNumber alloc] initWithDouble:parkLat]};
    [[NSNotificationCenter defaultCenter]
     postNotificationName:@"deparkNotification"
     object:nil
     userInfo:dict];
}

- (NSString*)buildSettingsJSON{
    if([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways){
        isBkLocEnabled = 1;
    }else if([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedWhenInUse){
        isBkLocEnabled = 0;
    }else{
        isBkLocEnabled = -1;
    }
    self.curAudioPort = [self getAudioPortName];
    NSMutableString* jsonString = [NSMutableString stringWithFormat:@"{\"isPDEnabled\": %s, \"isBkLocEnabled\": %s, \"isActivityEnabled\": %s, \"isBTVerified\": %s, \"verifiedBT\": \"%@\", \"curAudioPort\": \"%@\",\"firstTime\": %s, \"geofences\":[", isPDEnabled ? "true" : "false", isBkLocEnabled < 0 ? "\"unknown\"" : isBkLocEnabled > 0 ? "true" : "false", isActivityEnabled < 0 ? "\"unknown\"" : isActivityEnabled > 0 ? "true" : "false", isBTVerified ? "true" : "false", verifiedBT, self.curAudioPort, firstTime ? "true" : "false"];
    
    for (CLCircularRegion *geofence in geofences) {
        [jsonString appendString:[NSString stringWithFormat:@"{\"lat\": %f, \"lng\": %f, \"radius\": %f},", geofence.center.latitude, geofence.center.longitude, geofence.radius]];
    }
    if([geofences count] > 0){
        [jsonString deleteCharactersInRange:NSMakeRange([jsonString length]-1, 1)];
    }
    [jsonString appendString:@"]"];
    if(lastParkLat != 0){
        long long adjLastParkDate = (long long) 1000*lastParkDate;
        [jsonString appendString:[NSMutableString stringWithFormat:@", \"lastParkLat\": %f, \"lastParkLng\": %f, \"lastParkDate\": %lld", lastParkLat, lastParkLng, adjLastParkDate]];
    }
    [jsonString appendString:@"}"];
    if(firstTime){
        firstTime = false;
        [defaults setBool:firstTime forKey:@"pd_firstTime"];
        [defaults synchronize];
    }
    
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
    [defaults synchronize];

    isParkingKnown = NO;
    foundFirstActivity = NO;
    updateParkLocation = YES;
    initiatedBy = @"audio confirm";
    if (pendingDetection){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Updating detection"]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Starting detection"]];
    }
    pendingDetection = NO;
    //Check to see if de-parking has occured
    [self startDetection];
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
    [self runParkingDetector: false];
}

- (void)setParkLat:(double)lat andLng: (double)lng{
    parkLat = lat;
    parkLng = lng;
}

/************** Motion Activity Functions *********************/

- (void)checkActivitiesBySpeed{
    NSLog(@"SSD - Check by Speed");
    if(!pendingDetection){
        //[self sendUpdateNotification: @"Stopping activity detection"];
        return;
    }
    NSDate *now = [NSDate new];
    NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
    if(secs <=5){
        return;
    }
    if(secs > 120){
        pendingDetection = NO;
        [self failedActivityCheck2];
        return;
    }
    if(!foundFirstActivity){
        if(userSpeed > 10 && (isParking || !isParkingKnown)){
            foundFirstActivity = YES;
            isParking = YES;
        }else if(userSpeed < 2  && (!isParking || !isParkingKnown)){
            foundFirstActivity = YES;
            isParking = NO;
        }else if(secs > 10){
            pendingDetection = NO;
            [self failedActivityCheck1];
        }
        return;
    }else{
        if(isParking && userSpeed < 1){
            //Use current location
            [self setParkLat:userLat andLng: userLng];
            [self addNewGeofenceWithLat: parkLat andLng: parkLng setLastPark: YES];
            [self sendParkingEventToServer: -1 userInitiated:false];
        }
        else if(!isParking && userSpeed > 10){
            [self sendParkingEventToServer: 1 userInitiated:false];
        }else{
            [self waitingForActivityCheck: [NSString stringWithFormat:@"Speed: %i mph. ", (int) userSpeed]];
        }
    }
}

- (void)checkPastMotionActivities{
    NSLog(@"SSD - IN Past Activities");
    
    if([CMMotionActivityManager isActivityAvailable]){
        [motionActivityManager queryActivityStartingFromDate:[NSDate dateWithTimeIntervalSinceNow:-60*2]
                                                      toDate:[NSDate new]
                                                     toQueue:[NSOperationQueue new]
                                                 withHandler:^(NSArray *activities, NSError *error) {
                                                     
             long preIsActivityEnabled = isActivityEnabled;
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
                                                     
             if(preIsActivityEnabled != isActivityEnabled){
                 [self sendSettingsChangeNotification];
             }
             
             NSLog(@"SSD - IN Past Activities Handeler");
             
             for (CMMotionActivity *activity in activities) {
                 NSLog(@"Activity Found: %@ DEBUG: %@", activity.startDate, activity.debugDescription);
                 if((isParking || !isParkingKnown) && activity.confidence >= 1 && activity.automotive){
                     foundFirstActivity = YES;
                     isParking = YES;
                 }
                 if((!isParking || !isParkingKnown) && activity.confidence >= 1 && (activity.stationary || activity.walking)){
                     foundFirstActivity = YES;
                     isParking = NO;
                 }
             }
                                                     
             if(!foundFirstActivity && (isParking || !isParkingKnown) && userSpeed > 10){
                 foundFirstActivity = YES;
                 isParking = YES;
             }
                                                     
             if(!foundFirstActivity && (!isParking || !isParkingKnown) && userSpeed < 2){
                 foundFirstActivity = YES;
                 isParking = NO;
             }
                                                     
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
        [self sendUpdateNotification: [NSString stringWithFormat:@"Stopping detection. Cannot determine recent activities"]];
    }
    else if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Stopping detection. Recent driving not detected"]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Stopping detection. Recent walking not detected"]];
    }
}

- (void)failedActivityCheck2{
    NSLog(@"Failed Activity Check 2");
    if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Stopping detection. Still driving"]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Stopping detection. Driving not detected."]];
    }
}

- (void)waitingForActivityCheck:(NSString*)curActivityDesc {
    NSDate *now = [NSDate new];
    int secs = (int)[now timeIntervalSinceDate:lastDetectionDate];
    int secs2 = (int)[now timeIntervalSinceDate:lastUpdateMessage];
    if(secs2 < 10){
        return;
    }
    lastUpdateMessage = [NSDate new];
    if(isParking){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Waiting for car to stop. Countdown: %i<br>%@", (120 - secs), curActivityDesc]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Waiting for car to begin driving. Countdown: %i<br>%@",(120 - secs),  curActivityDesc]];
    }
}


- (void)checkFutureMotionActivities{
    
    if([CMMotionActivityManager isActivityAvailable] == YES){
        //register for Coremotion notifications
        [motionActivityManager startActivityUpdatesToQueue:[NSOperationQueue mainQueue] withHandler:^(CMMotionActivity * activity){
            if(!pendingDetection){
                //[self sendUpdateNotification: @"Stopping activity detection"];
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
            if(secs <= 5){
                return;
            }
            if(!isParking && activity.confidence >= 1 && activity.automotive){
                //Use location from parking detector start as park location
                [motionActivityManager stopActivityUpdates];
                [self sendParkingEventToServer: 1 userInitiated:false];
                return;
            }
            else if(isParking && activity.confidence >= 1 && ((activity.stationary && isParkingKnown)|| activity.walking)){
                [motionActivityManager stopActivityUpdates];
                //Use current location as park location
                [self setParkLat:userLat andLng: userLng];
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
    NSLog(@"Sending park event: %i", parkingEvent);
    if(userInitiated){
        initiatedBy = @"user";
        isActivityVerified = false;
    }else{
        isActivityVerified = true;
    }
    pendingDetection = NO;
    if(parkingEvent == -1){
        [self sendUpdateNotification: @"Parking detected"];
        [self sendParkNotification];

    }else{
        [self clearLastPark];
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

- (void)startDetection{
    if(!isPDEnabled){
        NSLog(@"Parking detector is disabled");
        return;
    }
    if([CLLocationManager locationServicesEnabled]){
        if([CLLocationManager authorizationStatus]==kCLAuthorizationStatusDenied){
            [self sendUpdateNotification: @"Location Services are not permitted. Cannot determine parking spot location"];
        }else{
            UIApplication *app = [UIApplication sharedApplication];

            //Clean up location settings and stop any current tasks
            [self.locationManager stopMonitoringSignificantLocationChanges];
            [self.locationManager stopUpdatingLocation];
            if (pendingDetectionID != UIBackgroundTaskInvalid){
                [app endBackgroundTask: pendingDetectionID];
                pendingDetectionID = UIBackgroundTaskInvalid;
            }
            
            //Reust permission (nothing happens if already granted)
            if([self.locationManager respondsToSelector:@selector(requestAlwaysAuthorization)]){
                [self.locationManager requestAlwaysAuthorization];
            }
            
            //Set detection controls
            pendingDetection = YES;
            checkActivities = YES;
            lastDetectionDate = [NSDate new];
            NSLog(@"Requesting location");

            //Let OS know we're running a background task which could take a while
            if ([app respondsToSelector:@selector(beginBackgroundTaskWithExpirationHandler:)]){
                pendingDetectionID = [app beginBackgroundTaskWithExpirationHandler:^{
                    dispatch_async(dispatch_get_main_queue(), ^{
                        if (pendingDetectionID != UIBackgroundTaskInvalid){
                            [app endBackgroundTask: pendingDetectionID];
                            pendingDetectionID = UIBackgroundTaskInvalid;
                        }
                    });
               }];
            }
            
            //Start getting location
            self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
            [self.locationManager startUpdatingLocation];
        }
    }else{
        [self sendUpdateNotification: @"Location Services are disabled. Cannot determine parking spot location"];
    }
}

/** Location Manager Delegates **/

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error{
    isBkLocEnabled = -1;
    NSLog(@"Location Error: %@",[NSString stringWithFormat:@"Location Error: %@",error.description]);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations{
    if(!isPDEnabled){
        pendingDetection = NO;
        NSLog(@"Parking detector is disabled. Stopping location updates");
        [self.locationManager stopUpdatingLocation];
        return;
    }
    long preIsBkLocEnabled = isBkLocEnabled;
    if([CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways){
        isBkLocEnabled = 1;
    }else{
        isBkLocEnabled = 0;
    }
    if(preIsBkLocEnabled != isBkLocEnabled){
        [self sendSettingsChangeNotification];
    }
    CLLocation* location = [locations lastObject];
    NSLog(@"latitude %+.6f, longitude %+.6f\n",
          location.coordinate.latitude,
          location.coordinate.longitude);
    
    userLat = location.coordinate.latitude;
    userLng = location.coordinate.longitude;
    userSpeed = location.speed/0.44704; //Converts to mph
    if(userSpeed < 0){
        userSpeed = 0;
    }

    
    NSDate *now = [NSDate new];
    NSTimeInterval secs = [now timeIntervalSinceDate:lastDetectionDate];
    if(secs > 60*5){
        [self.locationManager stopUpdatingLocation];
    }
    if(updateParkLocation){
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
        updateParkLocation = NO;
        CLLocation *tempLocation = [[CLLocation alloc] initWithLatitude:lastParkLat longitude:lastParkLng];
        CLLocation *newLocation = [[CLLocation alloc] initWithLatitude:userLat longitude:userLng];
        CLLocationDistance distance = [tempLocation distanceFromLocation:newLocation];

        if(isParking == NO && isParkingKnown == YES && distance < 200){
            parkLat = lastParkLat;
            parkLng = lastParkLng;
        }else{
            parkLat = userLat;
            parkLng = userLng;
            [self addNewGeofenceWithLat: parkLat andLng: parkLng setLastPark: NO];
        }
    }else if(pendingDetection && checkActivities){
        self.locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters;
        checkActivities = NO;
        if(isActivityEnabled == 0){
            NSLog(@"SSD - Check Activity from Location");
            [self checkActivitiesBySpeed];
        }else if(foundFirstActivity){
            NSLog(@"SSD - Check Future Activities");
            [self checkFutureMotionActivities];
        }else{
            //Validate parking
            NSLog(@"SSD - Checking Past Activities");
            [self checkPastMotionActivities];
        }
    }else if(pendingDetection && isActivityEnabled == 0){
        [self checkActivitiesBySpeed];
    }else{
        self.locationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters;
    }
}
//For geofence
- (void)locationManager:(CLLocationManager *)manager didEnterRegion:(CLCircularRegion *)region{
    self.curAudioPort = [self getAudioPortName];
    curBT = [self getBTPortName];
    foundFirstActivity = NO;
    if(lastParkID == region.identifier){
        isParking = NO;
        isParkingKnown = YES;
        updateParkLocation = NO;
        parkLat = lastParkLat;
        parkLng = lastParkLng;
        if([self.curAudioPort isEqual: verifiedBT] || (!isBTVerified && ![curBT isEqual: @"Not BT"] && ![notCarAudio containsObject: self.curAudioPort])){
            foundFirstActivity = YES;
        }
    }else{
        isParkingKnown = NO;
        updateParkLocation = YES;
        foundFirstActivity = NO;
    }
    
    //Check for parking
    isParkingKnown = NO;
    initiatedBy = @"geofence";

    if (pendingDetection){
        [self sendUpdateNotification: [NSString stringWithFormat:@"Updating detection"]];
    }else{
        [self sendUpdateNotification: [NSString stringWithFormat:@"Starting detection"]];
    }
    pendingDetection = NO;
    
    //Check to see if de-parking has occured
    [self startDetection];
}
/*
- (void)playAudioTest{
    [self.audioPlayer play];
}
*/

- (void)runParkingDetector: (BOOL)waitForBluetooth{
    if(!isPDEnabled){
        [self sendUpdateNotification: @"Parking detector is disabled"];
        return;
    }else if(pendingDetection){
        [self sendUpdateNotification: @"Updating detection"];
    }else if(!isBTVerified){
        [self sendUpdateNotification: @"Starting Invalidated Parking Detector"];
    }else{
        [self sendUpdateNotification: @"Starting Validated Parking Detector"];
    }
    
    /************************ Audio Session Approach *********************/
    
    curBT = [self getBTPortName];
    self.curAudioPort = [self getAudioPortName];
    updateParkLocation = YES;
    isParkingKnown = NO;
    foundFirstActivity = NO;
    pendingDetection = NO;
    initiatedBy = [NSString stringWithFormat:@"PD start"];
    
    if(!waitForBluetooth){
        [self startDetection];
    }else if(!self.wasLaunchedByLocation
       && ![curBT isEqual: @"Not BT"]
       && ![self.curAudioPort isEqual: @"No Valid Port"]
       && isBTVerified != YES
       && conformationCount < self.askedForConformationMax
       && ![notCarAudio containsObject: self.curAudioPort]){
        
        [self sendAlertNotification:self.curAudioPort];
        
    }else if([self.curAudioPort isEqual: verifiedBT]
             || (!isBTVerified && isActivityEnabled >= 0)
             || (!isBTVerified && ![curBT isEqual: @"Not BT"] && ![notCarAudio containsObject: self.curAudioPort] && ![self.curAudioPort isEqual:@"No Valid Port"])
             || !waitForBluetooth){
        [self startDetection];
    }
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
    } else if(isPDEnabled) {
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
            ||[[desc portType] isEqualToString:AVAudioSessionPortBluetoothHFP]){
            return [desc portName];
        }
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

- (void)handleRouteChange:(NSNotification*)notification{
    NSString* seccReason = @"";
    NSString *lastPort = self.curAudioPort;
    NSString *lastBT = curBT;

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
            if([lastPort isEqual: verifiedBT] || (!isBTVerified && ![lastBT isEqual: @"Not BT"] && ![notCarAudio containsObject: lastPort])){
                NSLog(@"Disconnected from %@",lastPort);
                if([lastPort isEqual: verifiedBT]){
                    foundFirstActivity = YES;
                }else{
                    foundFirstActivity = NO;
                }
                isParking = YES;
                isParkingKnown = YES;
                updateParkLocation = YES;
                initiatedBy = @"BT disconnect";
                if (pendingDetection){
                    [self sendUpdateNotification: [NSString stringWithFormat:@"Updating detection"]];
                }else{
                    [self sendUpdateNotification: [NSString stringWithFormat:@"Starting detection"]];
                }
                pendingDetection = NO;
                [self startDetection];
            }else{
                NSLog(@"Lost conenction was not BT or BT in not car BT list");
            }
            curBT = [self getBTPortName];
            self.curAudioPort = @"No Valid Port";
            break;
        case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
            curBT = [self getBTPortName];
            self.curAudioPort = [self getAudioPortName];
            NSLog(@"Connected to %@",self.curAudioPort);

            if([self.curAudioPort isEqual: verifiedBT] || (!isBTVerified && ![curBT isEqual: @"Not BT"] && ![notCarAudio containsObject: self.curAudioPort] && ![self.curAudioPort isEqual:@"No Valid Port"])){
                
                if([self.curAudioPort isEqual: verifiedBT]){
                    foundFirstActivity = YES;
                }else{
                    foundFirstActivity = NO;
                }
                isParking = NO;
                isParkingKnown = YES;
                updateParkLocation = YES;
                initiatedBy = @"BT connect";

                if(!self.wasLaunchedByLocation
                   && isBTVerified != YES
                   && conformationCount < self.askedForConformationMax
                   && ![notCarAudio containsObject: self.curAudioPort]){
                    pendingDetection = NO;
                    [self sendAlertNotification: self.curAudioPort];
                }else{
                    if (pendingDetection){
                        [self sendUpdateNotification: [NSString stringWithFormat:@"Updating detection"]];
                    }else{
                        [self sendUpdateNotification: [NSString stringWithFormat:@"Starting detection"]];
                    }
                    pendingDetection = NO;
                    //Check for de-parking
                    [self startDetection];
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
        [defaults setObject:lastParkID forKey:@"pd_lastParkID"];
        [defaults synchronize];
        [self saveLastParkLat: newLat andLng: newLng];
    }
}

- (void)saveLastParkLat:(double)lat andLng: (double)lng{
    lastParkLat = lat;
    lastParkLng = lng;
    lastParkDate = (long)[[NSDate new]timeIntervalSince1970];
    [defaults setDouble:lastParkLat forKey:@"pd_lastParkLat"];
    [defaults setDouble:lastParkLng forKey:@"pd_lastParkLng"];
    [defaults setInteger: lastParkDate forKey:@"pd_lastParkDate"];
    [defaults synchronize];
}
- (void)clearLastPark{
    lastParkLat = 0;
    lastParkLng = 0;
    lastParkDate = 0;
    lastParkID = @"No Last Park";
    [defaults setObject:lastParkID forKey:@"pd_lastParkID"];
    [defaults synchronize];
    [defaults setDouble:lastParkLat forKey:@"pd_lastParkLat"];
    [defaults setDouble:lastParkLng forKey:@"pd_lastParkLng"];
    [defaults setInteger: lastParkDate forKey:@"pd_lastParkDate"];
    [defaults synchronize];

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

