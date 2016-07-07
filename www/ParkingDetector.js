var exec = require('cordova/exec');

window.initParkingDetectorPlugin = function(showMessages, maxPrompts, endpoint, success, error) {
    exec(success, error, "ParkingDetector", "initPlugin", [showMessages, maxPrompts, endpoint]);
};
