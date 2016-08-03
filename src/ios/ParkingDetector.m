/********* ParkingDetector.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "MBProgressHUD.h"

@interface ParkingDetector : CDVPlugin {
  // Member variables go here.
    NSString* endpoint;
    NSNumber* showMessages;
    NSNumber* askedForConformationMax;
}

- (void)initPlugin:(CDVInvokedUrlCommand*)command;
@end

@implementation ParkingDetector

- (void)initPlugin:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    
    showMessages = [command.arguments objectAtIndex:0];
    askedForConformationMax =  [command.arguments objectAtIndex:1];
    endpoint = [command.arguments objectAtIndex:2];
    

    MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.webView.superview animated:YES];
    
    // Configure for text only and offset down
    hud.mode = MBProgressHUDModeText;
    hud.label.text = @"Hi Dave this is working mapybe";
    hud.margin = 10.f;
    hud.yOffset = 150.f;
    hud.removeFromSuperViewOnHide = YES;
    
    [hud hideAnimated:YES afterDelay:3];

    

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
