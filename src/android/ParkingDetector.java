package cordova.plugin.parking.detector;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import cordova.plugin.parking.detector.SendParkReport;

public class ParkingDetector extends CordovaPlugin implements ParkingDetectionService.Callbacks {
    // Google Api Client

    private static CordovaWebView mwebView;
    public static CordovaInterface mcordova;
    private static ParkingDetectionService mParkingDetectionService;
    private static final String LOG_TAG = "SS Parking Detector";
    boolean mBound = false;
    Intent parkingDetectionIntent;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mwebView = super.webView;
        mcordova = cordova;
        parkingDetectionIntent = new Intent(cordova.getActivity(), ParkingDetectionService.class);
    }
    @Override
    public void onDestroy(){
        cordova.getActivity().startService(parkingDetectionIntent);
        cordova.getActivity().getApplicationContext().unbindService(mConnection);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mPrefs.edit();

        if(action.equals("initPlugin")) {
            ParkingDetectionService.showMessages = args.getBoolean(0);
            ParkingDetectionService.askedForConformationMax = args.getInt(1);
            ParkingDetectionService.endpoint = args.getString(2);
            cordova.getActivity().startService(parkingDetectionIntent);
            cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            JSONObject result = ParkingDetectionService.buildSettingsJSON();
            callbackContext.success(result);
            return true;
        }else if(action.equals("userInitiatedPark")) {
            double userLat = args.getDouble(0);
            double userLng = args.getDouble(1);
            Location location = new Location("");
            location.setLatitude(userLat);
            location.setLongitude(userLng);
            ParkingDetectionService.parkingDetected(location, "user");
        }else if(action.equals("userInitiatedDepark")) {
            double userLat = args.getDouble(0);
            double userLng = args.getDouble(1);
            Location location = new Location("");
            location.setLatitude(userLat);
            location.setLongitude(userLng);
            ParkingDetectionService.deparkingDetected(location, "user");
        }else if(action.equals("resetBluetooth")) {
            ParkingDetectionService.askedForConformationCount = 0;
            ParkingDetectionService.btVerificed = false;
            ParkingDetectionService.bluetoothTarget = "";
            ParkingDetectionService.notCarSet = new HashSet<String>();
            editor.putInt("askedForConformationCount", ParkingDetectionService.askedForConformationCount);
            editor.putStringSet("notCarSet", ParkingDetectionService.notCarSet);
            editor.putString("bluetoothTarget", ParkingDetectionService.bluetoothTarget);
            editor.putBoolean("btVerificed", ParkingDetectionService.btVerificed);
            editor.commit();
            callbackContext.success();
            return true;
        }else if(action.equals("startParkingDetector")) {
            cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            cordova.getActivity().startService(parkingDetectionIntent);
            callbackContext.success();
            return true;
        }else if(action.equals("disableParkingDetector")) {
            cordova.getActivity().unbindService(mConnection);
            ParkingDetectionService.isPDEnabled = false;
            editor.putBoolean("isPDEnabled", ParkingDetectionService.isPDEnabled);
            editor.commit();
            callbackContext.success();
            return true;
        }else if(action.equals("enableParkingDetector")) {
            cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            cordova.getActivity().startService(parkingDetectionIntent);
            ParkingDetectionService.isPDEnabled = true;
            editor.putBoolean("isPDEnabled", ParkingDetectionService.isPDEnabled);
            editor.commit();
            cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            cordova.getActivity().startService(parkingDetectionIntent);
            callbackContext.success();
            return true;
        }else if(action.equals("confirmAudioPort")) {
            ParkingDetectionService.btVerificed = true;
            ParkingDetectionService.bluetoothTarget = ParkingDetectionService.curAudioPort;
            editor.putString("bluetoothTarget", ParkingDetectionService.bluetoothTarget);
            editor.putBoolean("btVerificed", ParkingDetectionService.btVerificed);
            editor.commit();
            callbackContext.success();
            return true;
        }else if(action.equals("getDetectorStatus")) {
            JSONObject result = ParkingDetectionService.buildSettingsJSON();
            callbackContext.success(result);
            return true;
        }
        return false;
    }


    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(LOG_TAG, "Connected to Parking Detection Service");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ParkingDetectionService.LocalBinder binder = (ParkingDetectionService.LocalBinder) service;
            mParkingDetectionService  = binder.getService();
            mParkingDetectionService.registerClient(ParkingDetector.this);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            Log.d(LOG_TAG, "Disconnected from Parking Detection Service");
        }
    };

    @Override
    public void updateMessage(String message){
        Log.d(LOG_TAG, "Update message called");
        final String js = "javascript:setTimeout(function(){window.parkingDetector.messageReceiver('" + message +  "');}, 0);";
        if (message != null && message.length() > 0) {
            if(!ParkingDetectionService.showMessages){
                Log.i(LOG_TAG,message);
                return;
            }
            mcordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    mwebView.loadUrl(js);
                    //Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    @Override
    public void parkedEvent(Location location){
        JSONObject parkedEvent = new JSONObject();
        try {
            parkedEvent.put("lat", location.getLatitude());
            parkedEvent.put("lng", location.getLongitude());
            parkedEvent.put("eventType","park");
        }catch (JSONException e) {

        }
        final String js = "javascript:setTimeout(function(){window.parkingDetector.parkedCallback("+parkedEvent.toString()+");}, 0);";
        mcordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                mwebView.loadUrl(js);
            }
        });
    }
    @Override
    public void deparkedEvent(Location location){
        JSONObject parkedEvent = new JSONObject();
        try {
            parkedEvent.put("lat", location.getLatitude());
            parkedEvent.put("lng", location.getLongitude());
            parkedEvent.put("eventType","depark");
        }catch (JSONException e) {

        }
        final String js = "javascript:setTimeout(function(){window.parkingDetector.deparkedCallback("+parkedEvent.toString()+");}, 0);";
        mcordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                mwebView.loadUrl(js);
            }
        });
    }
}
