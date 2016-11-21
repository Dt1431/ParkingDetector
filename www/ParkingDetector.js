var exec = require('cordova/exec');

window.parkingDetector = {
    parkedCallback: function(data){},
    deparkedCallback: function(data){},
    messageReceiver: function(data){},
    initParkingDetectorPlugin: function(showMessages, maxPrompts, endpoint, success, error) {
        exec(function(data){
            if(success){
                if(typeof data.isPDEnabled != "undefined"){
                    //Already a JS Object
                }else{
                    //Try parsing the JSON
                    try {
                        data = JSON.parse(data);
                    }
                    catch(err) {
                        data = "JSON Parse Error";
                    }
                }                
                success(data);
            }
        }, error, "ParkingDetector", "initPlugin", [showMessages, maxPrompts, endpoint]);
    },

    setParkedCallback: function(newCB){
        if(typeof newCB === "function"){
            this.parkedCallback = newCB;
        }else{
            console.log("Cannot set callback", newCB, "Is not a function");
        }
    },

    setDeparkedCallback: function(newCB){
        if(typeof newCB === "function"){
            this.deparkedCallback = newCB;
        }else{
            console.log("Cannot set callback", newCB, "Is not a function");
        }
    },
    
    setMessageReceiver: function(newCB){
        if(typeof newCB === "function"){
            this.messageReceiver = newCB;
        }else{
            console.log("Cannot set receiver", newCB, "Is not a function");
        }
    },

    userInitiatedPark: function(userLat, userLng, success, error) {
        exec(success, error, "ParkingDetector", "userInitiatedPark", [userLat, userLng]);
    },
    
    userInitiatedDepark: function(userLat, userLng, success, error) {
        exec(success, error, "ParkingDetector", "userInitiatedDepark", [userLat, userLng]);
    },
    
    resetBluetooth: function(success, error) {
        exec(success, error, "ParkingDetector", "resetBluetooth", []);
    },
    
    startParkingDetector: function(success, error) {
        exec(success, error, "ParkingDetector", "startParkingDetector", []);
    },
    
    disableParkingDetector: function(success, error) {
        exec(success, error, "ParkingDetector", "disableParkingDetector", []);
    },
    
    enableParkingDetector: function(success, error) {
        exec(success, error, "ParkingDetector", "enableParkingDetector", []);
    },

    confirmAudioPort: function(success, error) {
        exec(success, error, "ParkingDetector", "confirmAudioPort", []);
    },

    getDetectorStatus: function(success, error) {
        exec(function(data){
            if(success){
                if(typeof data.isPDEnabled != "undefined"){
                    //Already a JS Object
                }else{
                    //Try parsing the JSON
                    try {
                        data = JSON.parse(data);
                    }
                    catch(err) {
                        data = "JSON Parse Error";
                    }
                }                
                success(data);
            }
        }, error, "ParkingDetector", "getDetectorStatus", []);
    }
};
//For legacy purposes
window.initParkingDetectorPlugin = parkingDetector.initParkingDetectorPlugin;

