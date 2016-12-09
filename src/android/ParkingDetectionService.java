package cordova.plugin.parking.detector;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by davet on 11/15/2016.
 */
public class ParkingDetectionService extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public static GoogleApiClient mGoogleApiClient;

    public static boolean isParked = false;
    public static String lastBluetoothName = "";
    public static String bluetoothTarget = "";
    public static String mostLikelyActivity = "";
    public static int mostLikelyActivityType;
    public static Set<String> notCarSet = new HashSet<String>();
    public static int activityCounter = 0;
    public static int activityCountMax = 20;
    public static int activityRecognitionFrequency = 60000;

    public static int askedForConformationCount = 0;
    public static int askedForConformationMax = 0;
    public static String endpoint = "http://streetsmartdemo.cloudapp.net/newParkingActivity";
    public static String version = "";

    private static boolean isGoogleLoading = false;
    public static boolean isVerified = false;
    public static boolean firstTime = true;
    public static boolean isPDEnabled = true;
    public static boolean isActivityEnabled = false;
    public static boolean isBkLocEnabled = false;
    public static String curAudioPort = "No Valid Port";
    public static String initiatedBy;
    public static String[] geofences;
    public static Context context;
    public static Location lastLocationProxy;
    public static float lastParkLat;
    public static float lastParkLng;
    public static long lastParkDate;
    public static boolean countdownCalled = false;

    protected LocationRequest mLocationRequest;
    public static String userID;
    private static final String LOCK_TAG="ACCELEROMETER_MONITOR";

    public static BTPendingDetection pendingBTDetection = null;
    private static int currentTransportationMode = DetectedActivity.UNKNOWN;
    private static int prevTransportationMode = DetectedActivity.UNKNOWN;

    public static long lastStatusChangeTime = 0;
    public static String showMessages = "callback";

    private static final String LOG_TAG = "SS Parking Detector";
    private static BluetoothAdapter bluetoothAdapter;
    public static Callbacks pd;
    public static BluetoothHeadset mBluetoothHeadset;


    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        ParkingDetectionService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ParkingDetectionService.this;
        }
    }
    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = (BluetoothHeadset) proxy;
                List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
                for ( final BluetoothDevice dev : devices ) {
                    Log.d(LOG_TAG, "C Port found: "+dev.getName());
                    curAudioPort = dev.getName();
                }
                Log.i(LOCK_TAG,"");
                if(!isVerified && devices.size() > 0 && askedForConformationCount < askedForConformationMax && pd != null) {
                    askedForConformationCount += 1;
                    SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor=mPrefs.edit();
                    editor.putInt("askedForConformationCount", askedForConformationCount);
                    editor.commit();
                    pd.confirmBluetoothDialog();
                }
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                mBluetoothHeadset = null;
            }
        }
    };

    public void registerClient(ParkingDetector pd){
        this.pd = (Callbacks)pd;
    }
    public void unregisterClient(){
        this.pd = null;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        userID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        notCarSet = mPrefs.getStringSet("notCarSet", notCarSet);
        bluetoothTarget = mPrefs.getString("bluetoothTarget", "");
        isVerified = mPrefs.getBoolean("isVerified", false);
        askedForConformationCount = mPrefs.getInt("askedForConformationCount", 0);
        isPDEnabled = mPrefs.getBoolean("isPDEnabled", true);
        isBkLocEnabled = mPrefs.getBoolean("isBkLocEnabled", false);
        isActivityEnabled = mPrefs.getBoolean("isActivityEnabled", false);
        lastParkDate = mPrefs.getLong("lastParkDate",-9999);
        lastParkLat = mPrefs.getFloat("lastParkLat",-9999);
        lastParkLng = mPrefs.getFloat("lastParkLng",-9999);
        firstTime  = mPrefs.getBoolean("firstTime", true);

        try{
            version = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException e){
            version = "Unknown";
        }
    }
    @Override
    public void onDestroy() {
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient,
                getActivityDetectionPendingIntent());
        Log.i(LOG_TAG, "Stopping activity updates");
        // finally Close proxy connection after use.
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        super.onDestroy();
    }
    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Connected to google services");
        isGoogleLoading = false;
        if(mBluetoothHeadset == null){
            // Establish connection to the proxy.
            bluetoothAdapter.getProfileProxy(context, mProfileListener, BluetoothProfile.HEADSET);
        }
        //Start activity recognition updates if bt is not verified
        if(!isVerified){
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                                    mGoogleApiClient,
                                    getActivityDetectionPendingIntent());

                            activityRecognitionFrequency = 10000; //
                            Log.i(LOG_TAG, "Starting Activity Updates at Frequency: " + activityRecognitionFrequency);
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                                    mGoogleApiClient,
                                    activityRecognitionFrequency,
                                    getActivityDetectionPendingIntent());
                        }
                    },
                    5000);
        }

        // Register for broadcasts on BluetoothAdapter state change
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));

        // Register for broadcasts on Activities
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver2,
                new IntentFilter(Constants.BROADCAST_ACTION));

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Connecting to google services fail - "
                + connectionResult.toString());
        //Google Play services can resolve some errors it detects. If the error
        //has a resolution, try sending an Intent to start a Google Play
        //services activity that can resolve error.

        isGoogleLoading = false;
        mGoogleApiClient = null;

        if (connectionResult.hasResolution()) {

            // If no resolution is available, display an error dialog
        } else {

        }
    }
    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "GoogleApiClient connection has been suspend. Trying to reconnect");
        isGoogleLoading = true;
        mGoogleApiClient.connect();
    }

    public void startParkingDetector(){
        if(!isPDEnabled){
            toastMessage("Parking detector is disabled");
            return;
        }
        else {
            if (mGoogleApiClient == null && !isGoogleLoading) {
                isGoogleLoading = true;
                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(ActivityRecognition.API)
                        .addApi(LocationServices.API)
                        .build();
            }
            if(!mGoogleApiClient.isConnected()){
                mGoogleApiClient.connect();
            }
            if (bluetoothAdapter == null) {
                toastMessage("Bluetooth is not supported. Starting invalidated parking detector");
            } else {
                Log.d(LOG_TAG, "Bluetooth is supported");
                //test if BT enabled
                if (bluetoothAdapter.isEnabled()) {
                    if (bluetoothTarget != "") {
                        toastMessage("Starting validated parking detector");
                    } else {
                        toastMessage("Starting invalidated parking detector");
                    }
                } else {
                    toastMessage("Bluetooth is disabled. Starting invalidated parking detector");
                }
            }
        }
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, ActivityRecognitionIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    //broadcast recieve for Activity Recognition
    private final BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            activityCounter += 1;
            Log.d(LOG_TAG, "Activity counter: "+activityCounter);

            // Get the update
            ActivityRecognitionResult result = intent.getParcelableExtra(Constants.ACTIVITY_EXTRA);

            // Get the most probable activity from the list of activities in the update
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();

            boolean didActivityChange = false;
            // Get the type of activity
            float mostLikelyActivityConfidence = mostProbableActivity.getConfidence();
            float onFootConfidence = result.getActivityConfidence(DetectedActivity.ON_FOOT);
            float inVehicleConfidence = result.getActivityConfidence(DetectedActivity.IN_VEHICLE);
            float stillConfidence = result.getActivityConfidence(DetectedActivity.STILL);
            mostLikelyActivityType = mostProbableActivity.getType();

            if (mostLikelyActivityType == DetectedActivity.ON_FOOT
                    || mostLikelyActivityType == DetectedActivity.IN_VEHICLE
                    || mostLikelyActivityType == DetectedActivity.STILL) {
                //Do nothing for now
            }else if (inVehicleConfidence > 100 - inVehicleConfidence - mostLikelyActivityConfidence) {
                mostLikelyActivityType = DetectedActivity.IN_VEHICLE;
            }else if(onFootConfidence > 100 - onFootConfidence - mostLikelyActivityConfidence){
                mostLikelyActivityType = DetectedActivity.ON_FOOT;
            }else if(onFootConfidence > 100 - onFootConfidence - mostLikelyActivityConfidence){
                mostLikelyActivityType = DetectedActivity.STILL;
            }else{
                mostLikelyActivityType = DetectedActivity.UNKNOWN;
            }
            mostLikelyActivity = getNameFromType(mostLikelyActivityType);

            Log.d(LOG_TAG, "On Foot: "+ onFootConfidence);
            Log.d(LOG_TAG, "In Vehicle: "+ inVehicleConfidence);
            Log.d(LOG_TAG, "Still: "+ stillConfidence);
            Log.d(LOG_TAG, "Most likely: "+ getNameFromType(mostLikelyActivityType));

            //Make sure its been at least 5 seconds since bt connect / disconnect. This will help filter out lost connections
            if(pendingBTDetection != null && pendingBTDetection.timeSince() > 5){
                if (currentTransportationMode != mostLikelyActivityType && DetectedActivity.UNKNOWN != mostLikelyActivityType) {
                    if(((mostLikelyActivityType == DetectedActivity.ON_FOOT || mostLikelyActivityType == DetectedActivity.STILL)
                            && currentTransportationMode == DetectedActivity.IN_VEHICLE) ||
                            ((currentTransportationMode == DetectedActivity.ON_FOOT || currentTransportationMode == DetectedActivity.STILL)
                                    && mostLikelyActivityType == DetectedActivity.IN_VEHICLE)){
                        prevTransportationMode = currentTransportationMode;
                    }
                    currentTransportationMode = mostLikelyActivityType;
                }
                int cd = 90 - pendingBTDetection.timeSince();
                if(85 >= cd){
                    validateParking(pendingBTDetection.eventCode(), pendingBTDetection.location());
                }
            }
            else if(pendingBTDetection == null && !isVerified) {
                Location curLocationProxy = null;
                int newActivityRecognitionFrequency = 5*60000;
                if (mostLikelyActivityType != DetectedActivity.UNKNOWN) {
                    toastMessage(mostLikelyActivity + " detected");
                }
                if (DetectedActivity.UNKNOWN != mostLikelyActivityType) {

                    curLocationProxy = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    if (mostLikelyActivityType == DetectedActivity.ON_FOOT && currentTransportationMode == DetectedActivity.IN_VEHICLE) {

                        prevTransportationMode = currentTransportationMode;
                        currentTransportationMode = mostLikelyActivityType;
                        newActivityRecognitionFrequency = 60000;
                        if (curLocationProxy != null) {
                            lastLocationProxy = curLocationProxy;
                        }
                        if(lastParkLat != -9999 && lastParkLng != -9999){
                            lastLocationProxy = new Location("Last Park");
                            lastLocationProxy.setLatitude(lastParkLat);
                            lastLocationProxy.setLongitude(lastParkLng);
                        }
                        if (lastLocationProxy != null) {
                            parkingDetected(lastLocationProxy, "activity change");
                        }

                    }else if (mostLikelyActivityType == DetectedActivity.IN_VEHICLE && (currentTransportationMode == DetectedActivity.ON_FOOT || currentTransportationMode == DetectedActivity.STILL)) {

                        currentTransportationMode = mostLikelyActivityType;
                        prevTransportationMode = currentTransportationMode;
                        if (curLocationProxy != null) {
                            lastLocationProxy = curLocationProxy;
                        }

                        newActivityRecognitionFrequency = 10000;

                        if (curLocationProxy != null) {
                            parkingDetected(curLocationProxy, "activity change");
                        }
                    }else if (mostLikelyActivityType == DetectedActivity.IN_VEHICLE || currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                        currentTransportationMode = DetectedActivity.IN_VEHICLE;
                        newActivityRecognitionFrequency = 10000;
                    }else if (mostLikelyActivityType == DetectedActivity.ON_FOOT) {
                        currentTransportationMode = mostLikelyActivityType;
                        newActivityRecognitionFrequency = 30000;
                    }else if (currentTransportationMode == DetectedActivity.STILL && mostLikelyActivityType == DetectedActivity.STILL) {
                        newActivityRecognitionFrequency = 5*60000;
                    }else{
                        currentTransportationMode = mostLikelyActivityType;
                        newActivityRecognitionFrequency = -1;
                    }
                    if(newActivityRecognitionFrequency != activityRecognitionFrequency && newActivityRecognitionFrequency >= 0){
                        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                                mGoogleApiClient,
                                getActivityDetectionPendingIntent());

                        activityRecognitionFrequency = newActivityRecognitionFrequency;
                        Log.i(LOG_TAG, "Changing activity Frequency: " + activityRecognitionFrequency);
                        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                                mGoogleApiClient,
                                activityRecognitionFrequency,
                                getActivityDetectionPendingIntent());
                    }
                }
            }
        }
    };

    //broadcast receiver for Bluetooth
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int eventCode = Constants.OUTCOME_NONE;

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        toastMessage("Bluetooth was disabled");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        toastMessage("Bluetooth was enabled");
                        break;
                }
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                    curAudioPort = device.getName();
                }
                if(!isPDEnabled){
                    Log.d(LOG_TAG, "Ignoring change: PD disabled");
                    return;
                }
                if(isVerified && !device.getName().equals(bluetoothTarget)){
                    Log.d(LOG_TAG, "Ignoring non-car bluetooth change 1");
                    return;
                }
                if (!notCarSet.isEmpty() && notCarSet.contains(device.getName())) {
                    Log.d(LOG_TAG, "Ignoring non-car bluetooth change 2");
                    return;
                }
                //Start new decection sequence
                pendingBTDetection = null;
                long curTime = System.currentTimeMillis() / 1000;
                //else if (curTime - lastStatusChangeTime > Constants.STATUS_CHANGE_INTERVAL_THRESHOLD && pendingBTDetection == null) {
                lastBluetoothName = device.getName();
                //Log.d(LOG_TAG, "Passed parking status time check");
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    eventCode = Constants.OUTCOME_UNPARKING;
                    if (lastBluetoothName.equals(bluetoothTarget)) {
                        toastMessage("bluetooth connected to car");
                    } else {
                        toastMessage("bluetooth connected to " + device.getName());
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    curAudioPort = "No Valid Port";
                    eventCode = Constants.OUTCOME_PARKING;
                    if (lastBluetoothName.equals(bluetoothTarget)) {
                        toastMessage("bluetooth disconnected from car");
                    } else {
                        toastMessage("bluetooth disconnected from " + device.getName());
                    }
                }
                //Get location
                if (ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

                    if(isVerified){
                        //Do nothing
                    }
                    else if(!curAudioPort.equals("No Valid Port") && askedForConformationCount < askedForConformationMax) {
                        askedForConformationCount += 1;
                        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                        SharedPreferences.Editor editor=mPrefs.edit();
                        editor.putInt("askedForConformationCount", askedForConformationCount);
                        editor.commit();
                        if(pd != null){
                            pd.confirmBluetoothDialog();
                        }
                    }else if(askedForConformationCount < askedForConformationMax){
                        Log.d(LOG_TAG,"Max conformation count reached, no dialog");
                    }

                    mLocationRequest = LocationRequest.create()
                            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                            .setNumUpdates(1)
                            .setInterval(1);

                    LocationServices.FusedLocationApi.requestLocationUpdates(
                            mGoogleApiClient, mLocationRequest, new LocationClientListener(eventCode));

                    ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                            mGoogleApiClient,
                            getActivityDetectionPendingIntent());

                    activityRecognitionFrequency = 1000;
                    Log.i(LOG_TAG, "Changing activity Frequency: " + activityRecognitionFrequency);
                    ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                            mGoogleApiClient,
                            activityRecognitionFrequency,
                            getActivityDetectionPendingIntent());

                    activityCounter = 0;
                } else {
                    toastMessage("Location Services are disabled. Cannot determine parking spot location");
                }
            }
        }
    };

    public void countdown(){
        int cdTimeOut = 10000;
        if(pendingBTDetection != null && isPDEnabled) {
            int cd = 90 - pendingBTDetection.timeSince();
            String activityString = "";
            if(cd >= 85){
                cdTimeOut = 1000;
            }
            else if(cd >= 0) {
                if (mostLikelyActivity != null && !mostLikelyActivity.equals("")) {
                    activityString = "<br>Last Activity: " + mostLikelyActivity;
                }
                if (pendingBTDetection.eventCode() == Constants.OUTCOME_UNPARKING && currentTransportationMode != DetectedActivity.IN_VEHICLE) {
                    toastMessage("Waiting for vehicle to begin driving: " + cd + activityString);
                }else if (pendingBTDetection.eventCode() == Constants.OUTCOME_PARKING && currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                    toastMessage("Waiting for vehicle to stop: " + cd + activityString);
                }else if (!isVerified && pendingBTDetection.eventCode() == Constants.OUTCOME_PARKING && prevTransportationMode != DetectedActivity.IN_VEHICLE){
                    toastMessage("No previous driving. Parking not detected");
                    pendingBTDetection = null;
                    return;
                }
            }else{
                if(mostLikelyActivity != null && pendingBTDetection.eventCode() == Constants.OUTCOME_PARKING){
                    toastMessage("Stopping. No spot detected");
                }else if(mostLikelyActivity != null) {
                    toastMessage("Stopping. Parking not detected");
                }
                pendingBTDetection = null;
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                        mGoogleApiClient,
                        getActivityDetectionPendingIntent());
                if(!isVerified){

                    activityRecognitionFrequency = 5*60000; //Check every 5 minutes, does not use sensors
                    Log.i(LOG_TAG, "Changing activity Frequency: " + activityRecognitionFrequency);
                    ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                            mGoogleApiClient,
                            activityRecognitionFrequency,
                            getActivityDetectionPendingIntent());
                }else{
                    Log.i(LOG_TAG, "Stopping activity updates");
                }
                return;
            }
            new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        countdown();
                    }
                },
                cdTimeOut);
        }
    }

    //LoocationListener, called on BT change
    public class LocationClientListener implements LocationListener {
        int eventCode;

        public LocationClientListener(int eventCode){
            this.eventCode=eventCode;
            Log.d(LOG_TAG, "Creating location listener");
        }
        @Override
        public void onLocationChanged(Location location) {
            Log.d(LOG_TAG, "IN ON LOCATION CHANGE, lat=" + location.getLatitude() + ", lon=" + location.getLongitude());
            if(!isPDEnabled){
                return;
            }
            pendingBTDetection = new BTPendingDetection(eventCode, location);
            countdown();
        }
    }

    public void validateParking(int eventCode, Location location) {
        Log.d(LOG_TAG, "in validate parking");
        if(eventCode == Constants.OUTCOME_UNPARKING){
            Log.d(LOG_TAG, "In unparking");
            if (currentTransportationMode == DetectedActivity.IN_VEHICLE && (prevTransportationMode != DetectedActivity.UNKNOWN || isVerified)) {
                //Looks like we've got an open spot!!!
                actionsOnValidatedParking(eventCode, location, null);
            }
        }else{
            Log.d(LOG_TAG, "In parking");
            if ((currentTransportationMode == DetectedActivity.ON_FOOT ||currentTransportationMode == DetectedActivity.STILL ) && (prevTransportationMode != DetectedActivity.UNKNOWN || isVerified)) {
                actionsOnValidatedParking(eventCode, location, null);
            }
        }
    }

    private void actionsOnValidatedParking(int eventCode, Location location, String address){
        if(pendingBTDetection != null) {
            pendingBTDetection = null;
            long curTime = System.currentTimeMillis() / 1000;
            lastStatusChangeTime = curTime;
            //Stop activity listener

            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    mGoogleApiClient,
                    getActivityDetectionPendingIntent());

            if(!isVerified){

                activityRecognitionFrequency = 5*60000;
                Log.i(LOG_TAG, "Changing activity Frequency: " + activityRecognitionFrequency);
                ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                        mGoogleApiClient,
                        activityRecognitionFrequency,
                        getActivityDetectionPendingIntent());
            }else{
                Log.i(LOG_TAG, "Stopping activity updates");
            }
            if (eventCode == Constants.OUTCOME_PARKING) {
                parkingDetected(location,"BT disconnect");
            } else {
                deparkingDetected(location, "BT connect");
            }
        }
    }

    public static void parkingDetected(Location location, String initiatedBy){
        toastMessage("Parking detected");
        saveLastPark(location);
        SendParkReport sendPark = new SendParkReport(location, -1, lastBluetoothName, isVerified, userID, endpoint, initiatedBy, version);
        sendPark.execute();
        isParked = true;
        pendingBTDetection = null;
        if(pd != null){
            pd.parkedEvent(location);
        }
    }

    public static void deparkingDetected(Location location, String initiatedBy){
        toastMessage("New space detected");
        clearLastPark();
        SendParkReport sendDePark = new SendParkReport(location, 1, lastBluetoothName, isVerified, userID, endpoint, initiatedBy, version);
        sendDePark.execute();
        isParked = false;
        pendingBTDetection = null;
        if(pd != null){
            pd.deparkedEvent(location);
        }
    }
    public String getNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "In Vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On Bicycle";
            case DetectedActivity.ON_FOOT:
                return "On Foot";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.UNKNOWN:
                return "Unknown";
            case DetectedActivity.TILTING:
                return "Tilting";
        }
        return "unknown";
    }
    public static JSONObject buildSettingsJSON(){
        JSONObject settings = new JSONObject();
        if (ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            isBkLocEnabled = true;
            isActivityEnabled = true;
        }else{
            isBkLocEnabled = false;
            isActivityEnabled = false;
        }
        if (BluetoothAdapter.getDefaultAdapter().isEnabled() && mBluetoothHeadset != null){
            List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
            for ( final BluetoothDevice dev : devices ){
                curAudioPort = dev.getName();
            }
        }
        try {
            settings.put("isPDEnabled",isPDEnabled);
            settings.put("isBkLocEnabled",isBkLocEnabled);
            settings.put("isActivityEnabled",isActivityEnabled);
            settings.put("isBTVerified",isVerified);
            settings.put("verifiedBT", bluetoothTarget);
            settings.put("curAudioPort",curAudioPort);
            settings.put("firstTime",firstTime);
            settings.put("geofences",geofences);
            if(lastParkLat != -9999) {
                settings.put("lastParkLat", lastParkLat);
                settings.put("lastParkLng",lastParkLng);
                settings.put("lastParkDate",lastParkDate);
            }
            if(firstTime){
                SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = mPrefs.edit();
                firstTime = false;
                editor.putBoolean("firstTime", firstTime);
                editor.commit();
            }
        }
        catch (JSONException e) {

        }
        return settings;
    }

    public void disableParkingDetector(){
        isPDEnabled = false;
        pendingBTDetection = null;
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient,
                getActivityDetectionPendingIntent());
        Log.i(LOG_TAG, "Stopping activity updates");

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean("isPDEnabled", isPDEnabled);
        editor.commit();

    }
    public void enableParkingDetector(){
        isPDEnabled = true;
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean("isPDEnabled", isPDEnabled);
        editor.commit();
        if(!isVerified){
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                                    mGoogleApiClient,
                                    getActivityDetectionPendingIntent());

                            activityRecognitionFrequency = 10000; //
                            Log.i(LOG_TAG, "Starting Activity Updates at Frequency: " + activityRecognitionFrequency);
                            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                                    mGoogleApiClient,
                                    activityRecognitionFrequency,
                                    getActivityDetectionPendingIntent());
                        }
                    },
                    5000);
        }
    }
    public static void confirmAudioPort(){
        isVerified = true;
        bluetoothTarget = curAudioPort;
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString("bluetoothTarget", bluetoothTarget);
        editor.putBoolean("isVerified", isVerified);
        editor.commit();
    }
    public void resetBluetooth(){
        askedForConformationCount = 0;
        isVerified = false;
        bluetoothTarget = "";
        pendingBTDetection = null;
        notCarSet = new HashSet<String>();
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt("askedForConformationCount", askedForConformationCount);
        editor.putStringSet("notCarSet", notCarSet);
        editor.putString("bluetoothTarget", bluetoothTarget);
        editor.putBoolean("isVerified", isVerified);
        editor.commit();
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                                mGoogleApiClient,
                                getActivityDetectionPendingIntent());

                        activityRecognitionFrequency = 10000; //
                        Log.i(LOG_TAG, "Starting Activity Updates at Frequency: " + activityRecognitionFrequency);
                        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                                mGoogleApiClient,
                                activityRecognitionFrequency,
                                getActivityDetectionPendingIntent());
                    }
                },
                5000);
    }

    public static void saveLastPark(Location loc) {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = mPrefs.edit();
        lastParkLat = (float) loc.getLatitude();
        lastParkLng = (float) loc.getLongitude();
        lastParkDate = new Date().getTime();
        editor.putFloat("lastParkLat",lastParkLat);
        editor.putFloat("lastParkLng",lastParkLng);
        editor.putLong("lastParkDate", lastParkDate);
        editor.commit();
    }

    public static void clearLastPark() {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor=mPrefs.edit();
        lastParkDate = -9999;
        lastParkLat = -9999;
        lastParkLng  = -9999;
        editor.putFloat("lastParkLat",lastParkLat);
        editor.putFloat("lastParkLng",lastParkLng);
        editor.putLong("lastParkDate", lastParkDate);
        editor.commit();
    }

    public interface Callbacks{
        public void updateMessage(String message);
        public void parkedEvent(Location location);
        public void deparkedEvent(Location location);
        public void confirmBluetoothDialog();
    }
    public static void toastMessage(final String message) {
        if(pd != null){
            try {
                pd.updateMessage(message);
            }  catch (Exception ex) {
                Log.e(LOG_TAG, "Could not send message: ", ex);
            }
        }
    }
}
