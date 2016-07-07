var exec = require('cordova/exec');

window.initPlugin = function(showMessages, askedForConformationMax, success, error) {
    exec(success, error, "ParkingDetector", "initPlugin", [showMessages, askedForConformationMax]);
};
