package cordova.plugin.parking.detector;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognition;

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
    private static boolean mBound = false;
    private static CallbackContext pendingCallbackContext;
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
        if(mBound){
            mParkingDetectionService.unregisterClient();
            cordova.getActivity().unbindService(mConnection);
        }
        super.onDestroy();
    }
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(action.equals("initPlugin")) {
            ParkingDetectionService.showMessages = args.getString(0);
            ParkingDetectionService.askedForConformationMax = args.getInt(1);
            ParkingDetectionService.endpoint = args.getString(2);
            if(!mBound){
                cordova.getActivity().startService(parkingDetectionIntent);
                mBound = cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
                pendingCallbackContext = callbackContext;
            }else{
                JSONObject result = ParkingDetectionService.buildSettingsJSON();
                callbackContext.success(result);
                return true;
            }
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
            mParkingDetectionService.resetBluetooth();
            callbackContext.success();
            return true;

        }else if(action.equals("startParkingDetector")) {
            if(!mBound){
                cordova.getActivity().startService(parkingDetectionIntent);
                mBound = cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            }else{
                mParkingDetectionService.startParkingDetector();
            }
            callbackContext.success();
            return true;

        }else if(action.equals("disableParkingDetector")) {
            mParkingDetectionService.disableParkingDetector();
            if(mBound){
                mBound = false;
                mParkingDetectionService.unregisterClient();
                cordova.getActivity().unbindService(mConnection);
            }
            callbackContext.success();
            return true;

        }else if(action.equals("enableParkingDetector")) {
            mParkingDetectionService.enableParkingDetector();
            if(!mBound){
                cordova.getActivity().startService(parkingDetectionIntent);
                mBound = cordova.getActivity().bindService(parkingDetectionIntent, mConnection, Context.BIND_AUTO_CREATE);
            }else{
                mParkingDetectionService.startParkingDetector();
            }
            callbackContext.success();
            return true;

        }else if(action.equals("confirmAudioPort")) {
            ParkingDetectionService.confirmAudioPort();
            callbackContext.success();
            return true;

        }else if(action.equals("getDetectorStatus")) {
            JSONObject result = ParkingDetectionService.buildSettingsJSON();
            callbackContext.success(result);
            return true;
        }else{
            return false;
        }
        return true;
    }

    /* Callbacks for ParkingDetectionService */

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(LOG_TAG, "Connected to Parking Detection Service");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ParkingDetectionService.LocalBinder binder = (ParkingDetectionService.LocalBinder) service;
            mParkingDetectionService = binder.getService();
            mParkingDetectionService.registerClient(ParkingDetector.this);
            //Start parking detector
            mParkingDetectionService.startParkingDetector();
            mBound = true;
            if(pendingCallbackContext != null){
                JSONObject result = mParkingDetectionService.buildSettingsJSON();
                pendingCallbackContext.success(result);
                pendingCallbackContext = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mParkingDetectionService.unregisterClient();
            Log.d(LOG_TAG, "Disconnected from Parking Detection Service");
        }
    };

    @Override
    public void updateMessage(String message){
        final String js = "javascript:setTimeout(function(){window.parkingDetector.messageReceiver('" + message +  "');}, 0);";
        final String toastMessage = message;
        if (message != null && message.length() > 0) {
            if(ParkingDetectionService.showMessages.equals("log")){
                Log.i(LOG_TAG,message);
            }else{
                mcordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if(ParkingDetectionService.showMessages.equals("callback")){
                            mwebView.loadUrl(js);
                        }else if(ParkingDetectionService.showMessages.equals("overlay")){
                            Toast.makeText(mcordova.getActivity(), toastMessage.replace("<br>","\r\n"), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }
    }
    @Override
    public void confirmBluetoothDialog(){
        mcordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                AlertDialog.Builder confirmBT = new AlertDialog.Builder(mcordova.getActivity())
                        .setMessage("Is " + ParkingDetectionService.curAudioPort + " your car's bluetooth?")
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(mcordova.getActivity());
                                SharedPreferences.Editor editor = mPrefs.edit();
                                ParkingDetectionService.bluetoothTarget = ParkingDetectionService.curAudioPort;
                                ParkingDetectionService.isVerified = true;
                                editor.putString("bluetoothTarget", ParkingDetectionService.bluetoothTarget);
                                editor.putBoolean("isVerified", ParkingDetectionService.isVerified);
                                editor.commit();
                                Log.d(LOG_TAG, "Bluetooth target identified " + ParkingDetectionService.bluetoothTarget);
                            }
                        })
                        .setNegativeButton("no", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                ParkingDetectionService.pendingBTDetection = null;
                                Log.d(LOG_TAG, "Remove updates - 2");
                                ParkingDetectionService.notCarSet.add(ParkingDetectionService.curAudioPort);
                                SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(mcordova.getActivity());
                                SharedPreferences.Editor editor = mPrefs.edit();
                                editor.putStringSet("notCarSet", ParkingDetectionService.notCarSet);
                                editor.commit();
                                ParkingDetectionService.pendingBTDetection = null;
                                Log.d(LOG_TAG, "Bluetooth " + ParkingDetectionService.lastBluetoothName + " added to not car list");
                            }
                        });
                AlertDialog alertDialog = confirmBT.create();
                alertDialog.show();
            }
        });
    }
    @Override
    public void parkedEvent(Location location){
        JSONObject parkedEvent = new JSONObject();
        try {

            parkedEvent.put("lastParkLat", location.getLatitude());
            parkedEvent.put("lastParkLng", location.getLongitude());
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
            parkedEvent.put("lastParkLat", location.getLatitude());
            parkedEvent.put("lastParkLng", location.getLongitude());
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
