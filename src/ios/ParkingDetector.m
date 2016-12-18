#import "ParkingDetector.h"

@implementation ParkingDetector

- (void)pluginInitialize{
    
    //Save webview reference
    webView = self.webView;

    //Register for notifications
    NSNotificationCenter *defaultCenter = [NSNotificationCenter defaultCenter];
    
    //App State Change Notifications
    [defaultCenter addObserver:self
                      selector:@selector(onPause:)
                          name:UIApplicationDidEnterBackgroundNotification
                        object:nil];
    
    [defaultCenter addObserver:self
                      selector:@selector(onResume:)
                          name:UIApplicationWillEnterForegroundNotification
                        object:nil];
    
    [defaultCenter addObserver:self
                      selector:@selector(onClose:)
                          name:UIApplicationWillTerminateNotification
                        object:nil];
    
    [defaultCenter addObserver:self
                      selector:@selector(onFinishLaunching:)
                          name:UIApplicationDidFinishLaunchingNotification
                        object:nil];
}

- (void)initPlugin:(CDVInvokedUrlCommand*)command {
    
    //Get arguments from Cordova
    parkingDetectorService.showMessages = [command.arguments objectAtIndex:0];
    parkingDetectorService.askedForConformationMax =  [[command.arguments objectAtIndex:1] intValue];
    parkingDetectorService.endpoint = [command.arguments objectAtIndex:2];
    
    //Start detector
    [parkingDetectorService runParkingDetector: true];

    //Return results
    NSString* jsonString = [parkingDetectorService buildSettingsJSON];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonString];
    
    // The sendPluginResult method is thread-safe.
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)userInitiatedPark:(CDVInvokedUrlCommand*)command {
    double parkLat = [[command.arguments objectAtIndex:0] doubleValue];
    double parkLng = [[command.arguments objectAtIndex:1] doubleValue];
    [parkingDetectorService setParkLat:parkLat andLng:parkLng];
    [parkingDetectorService addNewGeofenceWithLat: parkLat andLng: parkLng setLastPark: YES];
    [parkingDetectorService sendParkingEventToServer: -1 userInitiated:true];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)userInitiatedDepark:(CDVInvokedUrlCommand*)command {    
    double parkLat = [[command.arguments objectAtIndex:0] doubleValue];
    double parkLng = [[command.arguments objectAtIndex:1] doubleValue];
    [parkingDetectorService setParkLat:parkLat andLng:parkLng];
    [parkingDetectorService sendParkingEventToServer: 1 userInitiated:true];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)startParkingDetector:(CDVInvokedUrlCommand*)command {
    [parkingDetectorService runParkingDetector: false];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)confirmAudioPort:(CDVInvokedUrlCommand*)command {
    [parkingDetectorService setCarAudioPort:parkingDetectorService.curAudioPort];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)resetBluetooth:(CDVInvokedUrlCommand *)command {
    [parkingDetectorService resetBluetooth];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)disableParkingDetector:(CDVInvokedUrlCommand*)command{
    [parkingDetectorService disableParkingDetector];

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)enableParkingDetector:(CDVInvokedUrlCommand*)command{
    [parkingDetectorService enableParkingDetector];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)getDetectorStatus:(CDVInvokedUrlCommand*)command{
    NSString* jsonString = [parkingDetectorService buildSettingsJSON];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:jsonString];
    // The sendPluginResult method is thread-safe.
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)startParkingDetectorService{
    //Start parking detector service (if already running, returns existing)
    parkingDetectorService = [ParkingDetectorService sharedManager];
    
    //Register for Parking Detector Service Notifications
    NSNotificationCenter *defaultCenter = [NSNotificationCenter defaultCenter];
    [defaultCenter addObserver:self
                      selector:@selector(parkedEvent:)
                          name:@"parkNotification"
                        object:nil];
    [defaultCenter addObserver:self
                      selector:@selector(deparkedEvent:)
                          name:@"deparkNotification"
                        object:nil];
    [defaultCenter addObserver:self
                      selector:@selector(sendMessage:)
                          name:@"messageNotification"
                        object:nil];
    [defaultCenter addObserver:self
                      selector:@selector(showBTAlertBox:)
                          name:@"alertNotification"
                        object:nil];
    [defaultCenter addObserver:self
                      selector:@selector(settingsChangeEvent:)
                          name:@"settingsChangeNotification"
                        object:nil];
    
}

// Notification callback functions

- (void)onResume:(NSNotification *)notification{
    NSLog(@"Parking detector about to enter foreground");
}

- (void)onPause:(NSNotification *)notification{
    NSLog(@"Parking detector about to enter background");
    NSLog(@"Time Remaining: %f", [[UIApplication sharedApplication] backgroundTimeRemaining]);
}

- (void)onClose:(NSNotification *)notification{
    NSLog(@"Parking detector closing");
}

- (void)onFinishLaunching:(NSNotification *)notification{
    NSDictionary *dict = [notification userInfo];
    [self startParkingDetectorService];
    if ([dict objectForKey:UIApplicationLaunchOptionsLocationKey]) {
        NSLog(@"Parking detector started by system on location event.");
        parkingDetectorService.wasLaunchedByLocation = true;
    }else{
        NSLog(@"Parking detector started normally.");
        parkingDetectorService.wasLaunchedByLocation = false;
    }
}

- (void)sendMessage:(NSNotification*)notification{
    NSString *message = [notification.userInfo valueForKey:@"message"];
    if([parkingDetectorService.showMessages isEqual: @"callback"] && [[UIApplication sharedApplication] applicationState] == UIApplicationStateActive){
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            NSString* jsString = [NSString stringWithFormat:@"setTimeout(function(){window.parkingDetector.messageReceiver(\"%@\");}, 0);",message];
            
            if ([webView isKindOfClass:[UIWebView class]]) {
                [(UIWebView*)webView stringByEvaluatingJavaScriptFromString:jsString];
            }
            
        }];
        return;
    }
    message = [message stringByReplacingOccurrencesOfString:@"<br>" withString:@"\r\n"];
    if([parkingDetectorService.showMessages isEqual: @"log"] || !([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive)){
        NSLog(@"SteetSmart Message: %@",message);
    }else if([parkingDetectorService.showMessages isEqual: @"overlay"]){
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.webView.superview animated:YES];
            // Configure for text only and offset down
            hud.mode = MBProgressHUDModeText;
            hud.detailsLabelText = message;
            hud.margin = 10.f;
            hud.removeFromSuperViewOnHide = YES;
            [hud hideAnimated:YES afterDelay:3];
        }];
    }

}

-(void)parkedEvent:(NSNotification*)notification{
    if([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive){
        NSString *lastParkLat = [notification.userInfo valueForKey:@"lastParkLat"];
        NSString *lastParkLng = [notification.userInfo valueForKey:@"lastParkLng"];
        NSString *jsString = [NSString stringWithFormat:@"setTimeout(function(){window.parkingDetector.parkedCallback({event:\"parked\", lastParkLat: %@, lastParkLng: %@});}, 0);", lastParkLat, lastParkLng];
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            if ([webView isKindOfClass:[UIWebView class]]) {
                [(UIWebView*)webView stringByEvaluatingJavaScriptFromString:jsString];
            }
        }];
    }
}

-(void)deparkedEvent:(NSNotification*)notification{
    if([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive){
        NSString *lastDeparkLat = [notification.userInfo valueForKey:@"lastDeparkLat"];
        NSString *lastDeparkLng = [notification.userInfo valueForKey:@"lastDeparkLng"];
        NSString *jsString = [NSString stringWithFormat:@"setTimeout(function(){window.parkingDetector.parkedCallback({event:\"deparked\", lastDeparkLat: %@, lastDeparkLng: %@});}, 0);", lastDeparkLat, lastDeparkLng];
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            if ([webView isKindOfClass:[UIWebView class]]) {
                [(UIWebView*)webView stringByEvaluatingJavaScriptFromString:jsString];
            }
        }];
    }
}

- (void)settingsChangeEvent:(NSNotification*)notification{
    if([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive){
        NSString *jsString = [NSString stringWithFormat:@"setTimeout(function(){window.parkingDetector.settingsChangedCallback({event:\"settingsChange\"});}, 0);"];
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            if ([webView isKindOfClass:[UIWebView class]]) {
                [(UIWebView*)webView stringByEvaluatingJavaScriptFromString:jsString];
            }
        }];
    }
}

- (void)showBTAlertBox:(NSNotification*)notification{
    if([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive){
        NSString *BTName = [notification.userInfo valueForKey:@"audioPort"];
        UIAlertController * alert =   [UIAlertController
                                      alertControllerWithTitle:[NSString stringWithFormat:@"Are you connected to your car's speakers?\rSpeaker name: %@", BTName]
                                      message:[NSString stringWithFormat:@"%@ uses speaker connection data to crowdsource open parking spaces.", [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleDisplayName"]]
                                      preferredStyle:UIAlertControllerStyleActionSheet];
        
        UIAlertAction* no = [UIAlertAction
                             actionWithTitle:@"No"
                             style:UIAlertActionStyleDefault
                             handler:^(UIAlertAction * action){
                                 [alert dismissViewControllerAnimated:YES completion:nil];
                                 [parkingDetectorService setCarAudioPort: BTName];
                             }];

        
        UIAlertAction* yes = [UIAlertAction
                                 actionWithTitle:@"Yes"
                                 style:UIAlertActionStyleDefault
                                 handler:^(UIAlertAction * action){
                                     [alert dismissViewControllerAnimated:YES completion:nil];
                                     [parkingDetectorService setCarAudioPort: BTName];
                                     //Check to see if de-parking has occured
                                     [parkingDetectorService runParkingDetector: false];
                                     [parkingDetectorService sendSettingsChangeNotification];
                                 }];
        
        [alert addAction:yes];
        [alert addAction:no];
        [alert addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
            [alert dismissViewControllerAnimated:YES completion:nil];
        }]];
        [self.viewController presentViewController:alert animated:YES completion:nil];
    }
}
@end
