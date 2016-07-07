StreetSmart
========================

Cordova Parking Detector Plugin
----------------
  
   - For more information contact dave@waitbot.com
        
  ## Install:
  
  1. Open command line client
  2. Navigate to your Cordova application's root directory
  3. Run `cordova plugin add https://github.com/Dt1431/ParkingDetector.git`
  
  ## Remove:
  
  1. Open command line client
  2. Navigate to your Cordova application's root directory
  3. Run `cordova plugin remove cordova-plugin-parking-detector`
   
  ## Use:
  The parking detector plugin runs in the background of your cordova application. To start the parking detector plugin simply call the javascript function `initParkingDetectorPlugin(showMessages, maxPrompts, endpoint)` once your device is ready i.e. on onDeviceReady callback or equivalent. A description of the plugin options is below. All three arguments are required.
  
  | Name | Type |  Description |
  |----------|-------------|------|
  | showMessages |  boolean | Should be `false` in a production enviroment. If true the plugin will show status messages to help with testing, etc. |
  | maxPrompts |    int   | The parking detector plugin will prompt the user X times in an attempt to confirm the user's car bluetooth device name. `0` means the plugin will never prompt the user. |
  | endpoint | string | The plugin will send (via post) parking and un-parking data to this URL.|

  ### Example:
  `initParkingDetectorPlugin(false, 2, "http://streetsmartdemo.cloudapp.net/newParkingActivity")`;

  Creates a parking detector with no status messages, will prompt the user a maximum of two times, and will send data to http://streetsmartdemo.cloudapp.net/newParkingActivity. This is the recommended implementation.




