package cordova.plugin.parking.detector;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import android.Manifest;
import android.app.AlertDialog;
import android.app.IntentService;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;

import cordova.plugin.parking.detector.SendParkReport;

public class ParkingDetector extends CordovaPlugin implements
        ConnectionCallbacks, OnConnectionFailedListener, ResultCallback<Status> {
    // Google Api Client
    public static GoogleApiClient mGoogleApiClient;

    public static boolean isParked = false;
    public static String lastBluetoothName = "";
    public static String bluetoothTarget = "";
    public static Set<String> notCarSet = new HashSet<String>();
    public static int activityCounter = 0;
    public static int activityCountMax = 60;

    public static int askedForConformationCount = 0;
    public static int askedForConformationMax = 0;

    public static boolean btVerificed = false;

    protected LocationRequest mLocationRequest;
    public static String userID;
    private static final String LOCK_TAG="ACCELEROMETER_MONITOR";

    public static BTPendingDetection pendingBTDetection = null;
    private int currentTransportationMode = DetectedActivity.UNKNOWN;
    private int prevTransportationMode = DetectedActivity.UNKNOWN;

    private static CordovaWebView mwebView;
    private static CordovaInterface mcordova;
    public static long lastStatusChangeTime = 0;
    public static boolean showMessages = false;

    private static final String LOG_TAG = "BluetoothStatus";
    private BluetoothAdapter bluetoothAdapter;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(action.equals("initPlugin")) {
            showMessages = args.getBoolean(0);
            askedForConformationMax = args.getInt(1);
            initPlugin();
            return true;
        }
        return false;
    }

    private void initPlugin() {
        Log.d("dt-test", "Plugin Init for user: " + userID);
        //test if B supported
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            toastMessage("Bluetooth is not supported. Parking detector cannot start");
        } else {
            Log.d(LOG_TAG, "Bluetooth is supported");
            //test if BT enabled
            if (bluetoothAdapter.isEnabled()) {
                if (mGoogleApiClient == null) {
                    mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .addApi(ActivityRecognition.API)
                            //.addApi(LocationServices.API)
                            .build();
                }
                if(!mGoogleApiClient.isConnected()){
                    mGoogleApiClient.connect();
                }
                if(bluetoothTarget != ""){
                    toastMessage("Starting validated parking detector. Listening for " + bluetoothTarget);
                }else{
                    toastMessage("Starting unvalidated parking detector. Listening for all bluetooth connections");
                }
            } else {
                toastMessage("Bluetooth is disabled. Parking detector cannot start untill bluetooth is enabled.");
            }
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        userID = Settings.Secure.getString(cordova.getActivity().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
        notCarSet = mPrefs.getStringSet("notCarSet", notCarSet);
        bluetoothTarget = mPrefs.getString("bluetoothTarget", "");
        btVerificed = mPrefs.getBoolean("btVerificed", false);
        askedForConformationCount = mPrefs.getInt("askedForConformationCount", 0);
        mwebView = super.webView;
        mcordova = cordova;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Connected to google services");
        // Register for broadcasts on BluetoothAdapter state change
        mcordova.getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        mcordova.getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                3000,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Connecting to google services fail - "
                + connectionResult.toString());
        //Google Play services can resolve some errors it detects. If the error
        //has a resolution, try sending an Intent to start a Google Play
        //services activity that can resolve error.

        if (connectionResult.hasResolution()) {

            // If no resolution is available, display an error dialog
        } else {

        }
    }
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "GoogleApiClient connection has been suspend. Trying to reconnect");
        mGoogleApiClient.connect();
    }
    private PendingIntent getActivityDetectionPendingIntent() {
        Log.d("dt-test", "making intent");
        Intent intent = new Intent(mcordova.getActivity(), ActivityRecognitionIntentService.class);
        Log.d("dt-test", "making intent 2");
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(mcordova.getActivity(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver2 = new ActivityDetectionBroadcastReceiver();

    //broadcast receiver for Bluetooth
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                Log.d(LOG_TAG, "Bluetooth action state changed");
                long curTime = System.currentTimeMillis() / 1000;
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(LOG_TAG, "Not car set: " + notCarSet.toString() + " device: " + device.getName());
                if(!notCarSet.isEmpty( ) && notCarSet.contains(device.getName())){
                    Log.d(LOG_TAG, "Ignoring non-car bluetooth change");
                }
                else if (curTime - lastStatusChangeTime > Constants.STATUS_CHANGE_INTERVAL_THRESHOLD) {
                    Log.d(LOG_TAG, "Passed parking status time check");
                    lastBluetoothName = device.getName();
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                        if(lastBluetoothName.equals(bluetoothTarget)){
                            toastMessage("bluetooth connected to car");
                        }else{
                            toastMessage("bluetooth connected to " + device.getName());
                        }
                    } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                        if(lastBluetoothName.equals(bluetoothTarget)){
                            toastMessage("bluetooth disconnected from car");
                        }else{
                            toastMessage("bluetooth disconnected from " + device.getName());
                        }
                    }
                    //Get location
                    if (ContextCompat.checkSelfPermission(cordova.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        /*int eventCode = intent.getIntExtra(Constants.BLUETOOTH_CON_UPDATE_EVENT_CODE, Constants.OUTCOME_NONE);
                        mLocationRequest = LocationRequest.create()
                                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                                .setNumUpdates(1)
                                .setInterval(1);

                        LocationServices.FusedLocationApi.requestLocationUpdates(
                                mGoogleApiClient, mLocationRequest, new LocationClientListener(eventCode));

                        if(1==1){//GooglePlayServicesUtil.isGooglePlayServicesAvailable(mGoogleApiClient)){
                            //Remove Activity Listener (just in case
                            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, pendingActivityIntent);
                            //Start Activity Listener
                            activityCounter = 0;
                        }else{
                            toastMessage("Activity Recognition is disabled. Cannot validate parking");
                        }*/
                    }else{
                        toastMessage("Location Services are disabled. Cannot determine parking spot location");
                    }
                } else {
                    Log.d(LOG_TAG, "Failed status change time check");
                }
            }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        toastMessage("Bluetooth was disabled. Stopping parking detector");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        toastMessage("Bluetooth was enabled. Turning starting parking detector");
                        break;
                }
            }
        }
    };
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "activity-detection-response-receiver";
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("dt-test", "on recieve 1");
        }
    }
    //LoocationListener, called on BT change
    public class LocationClientListener implements LocationListener {
        int eventCode;

        public LocationClientListener(int eventCode){
            this.eventCode=eventCode;
            Log.d("dt-test", "Creating location listener");
            if(!btVerificed && askedForConformationCount < askedForConformationMax) {
                askedForConformationCount += 1;
                SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor=mPrefs.edit();
                editor.putInt("askedForConformationCount", askedForConformationCount);
                editor.commit();

                AlertDialog.Builder confirmBT = new AlertDialog.Builder(cordova.getActivity())
                        .setMessage("Is " + lastBluetoothName + " your car's bluetooth?")
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor=mPrefs.edit();
                                bluetoothTarget = lastBluetoothName;
                                btVerificed = true;
                                editor.putString("lastBluetoothName", lastBluetoothName);
                                editor.putBoolean("btVerificed", btVerificed);
                                editor.commit();
                                Log.d(LOG_TAG,"Bluetooth target identified " + bluetoothTarget);
                            }
                        })
                        .setNegativeButton("no", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                notCarSet.add(lastBluetoothName);
                                SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor=mPrefs.edit();
                                editor.putStringSet("notCarSet", notCarSet);
                                editor.commit();
                                Log.d(LOG_TAG,"Bluetooth " + lastBluetoothName + " added to not car list");
                            }
                        });
                AlertDialog alertDialog = confirmBT.create();
                alertDialog.show();
            }else if(askedForConformationCount < askedForConformationMax){
                Log.d(LOG_TAG,"Max conformation count reached, no dialog");
            }
        }
        @Override
        public void onLocationChanged(Location location) {
            Log.d("dt-test", "IN ON LOCATION CHANGE, lat=" + location.getLatitude() + ", lon=" + location.getLongitude());
            pendingBTDetection = new BTPendingDetection(eventCode, location);
        }
    }

    public class ActivityRecognitionIntentService2 extends IntentService {
        // TAG for the class;
        @Override
        public void onCreate() {
            super.onCreate();

            Log.d("dt-test", "Creating activity handeler");
        }

        public ActivityRecognitionIntentService2() {
            // Set the label for the service's background thread
            super("ActivityRecognitionIntentService2");
        }

        /**
         * Called when a new activity detection update is available.
         */
        @Override
        protected void onHandleIntent(Intent intent) {
            Log.d("dt-test", "Handling activity update");
            if (ActivityRecognitionResult.hasResult(intent)) {
                activityCounter += 1;

                if(activityCountMax <= activityCounter){
                    //ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, pendingActivityIntent);
                }

                // Get the update
                ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

                // Get the most probable activity from the list of activities in the update
                DetectedActivity mostProbableActivity = result.getMostProbableActivity();

                // Get the type of activity
                float mostLikelyActivityConfidence = mostProbableActivity.getConfidence();
                float onFootConfidence = result.getActivityConfidence(DetectedActivity.ON_FOOT);
                float inVehicleConfidence = result.getActivityConfidence(DetectedActivity.IN_VEHICLE);

                int mostLikelyActivityType = mostProbableActivity.getType();

                if (mostLikelyActivityType == DetectedActivity.UNKNOWN) {
                    if (inVehicleConfidence > 100 - inVehicleConfidence - mostLikelyActivityConfidence)
                        mostLikelyActivityType = DetectedActivity.IN_VEHICLE;
                    else {
                        if (onFootConfidence > 100 - onFootConfidence - mostLikelyActivityConfidence)
                            mostLikelyActivityType = DetectedActivity.ON_FOOT;
                    }
                }
                if (currentTransportationMode != mostLikelyActivityType) {
                    prevTransportationMode = currentTransportationMode;
                }
                if(pendingBTDetection != null){
                    validateParking(pendingBTDetection.eventCode(), pendingBTDetection.location());
                }
            }
        }
    }
    public void validateParking(int eventCode, Location location) {

        if(eventCode==Constants.OUTCOME_UNPARKING){
            if (currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                //Looks like we've got an open spot!!!
                actionsOnBTDetection(eventCode, location, null);
            } else {
                toastMessage("Waiting for vehicle to begin driving " + (activityCountMax - activityCounter));
            }
        }else{
            if (prevTransportationMode == DetectedActivity.IN_VEHICLE || lastBluetoothName.equals(bluetoothTarget)) {
                actionsOnBTDetection(eventCode, location, null);
            } else {
                if(currentTransportationMode == DetectedActivity.IN_VEHICLE){
                    toastMessage("Waiting for vehicle to stop " + (activityCountMax - activityCounter));
                }
            }
        }
    }

    // actions taken when a parking/unparking event is detected and the location of the event is retrieved
    private void actionsOnBTDetection(int eventCode, Location location, String address){
        long curTime = System.currentTimeMillis() / 1000;
        lastStatusChangeTime = curTime;
        //Stop activity listener
        pendingBTDetection = null;

        //ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, pendingActivityIntent);

        if(eventCode==Constants.OUTCOME_PARKING){
            toastMessage("Parking detected");
            SendParkReport sendPark = new SendParkReport(location, -1, lastBluetoothName, btVerificed);
            sendPark.execute();
        }else{
            toastMessage("New space detected");
            SendParkReport sendDePark = new SendParkReport(location,1,lastBluetoothName, btVerificed);
            sendDePark.execute();
            isParked = false;
        }
    }

    public void onResult(Status status) {

    }

    public static void toastMessage(final String message) {
        if (message != null && message.length() > 0) {
            if(!showMessages){
                Log.i(LOG_TAG,message);
                return;
            }
            Toast.makeText(mcordova.getActivity(), message, Toast.LENGTH_LONG).show();
        }
    }
}
