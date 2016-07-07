package cordova.plugin.parking.detector;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import android.Manifest;
import android.app.AlertDialog;
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
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import cordova.plugin.parking.detector.googleacitvityrecognition.GoogleActivityRecognitionClientRequester;
import cordova.plugin.parking.detector.googleacitvityrecognition.GoogleActivityRecognitionClientRemover;
//import cordova.plugin.parking.detector.MotionState.Source;
//import cordova.plugin.parking.detector.MotionState.Type;
import cordova.plugin.parking.detector.SendParkReport;

public abstract class ParkingDetector1 extends CordovaPlugin implements
        ConnectionCallbacks, OnConnectionFailedListener{

    // Google Api Client
    public static GoogleApiClient mGoogleApiClient;

    public static boolean isParked = false;
    public static String lastBluetoothName = "";
    public static String bluetoothTarget = "";

    public static boolean askedForConformation = false;

    public static boolean btVerificed = false;

    //Stores parameters for requests to the FusedLocationProviderApi.

    protected LocationRequest mLocationRequest;
    private static Context mContext;
    protected LocationRequest mParkingLocationRequest;

    // Unique ID for the User
    public static String userID;
    public int MY_PERMISSIONS_REQUEST_FINE_LOCATION;

    private static final String LOCK_TAG="ACCELEROMETER_MONITOR";

    public static BTPendingDetection pendingBTDetection = null;
    private int currentTransportationMode = DetectedActivity.UNKNOWN;
    private int prevTransportationMode = DetectedActivity.UNKNOWN;

    private boolean onCreateCalled = false;

    /**
     * Holds activity recognition data, in the form of
     * strings that can contain markup
     */
    //private ArrayAdapter<Spanned> mStatusAdapter;

    //Instance of a Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter;

    /**
     *  Intent filter for incoming broadcasts from the
     *  IntentService.
     */
    IntentFilter mBroadcastFilter;

    // Instance of a local broadcast manager
    private LocalBroadcastManager mBroadcastManager;

    //Instance of a customized location manager


    /*
    //Google Activity Update Fields
    private GoogleActivityRecognitionClientRequester mGoogleActivityDetectionRequester;
    private GoogleActivityRecognitionClientRemover mGoogleActivityDetectionRemover;
    private double[] probOfOnFootAndInVehicleOfLastUpdate=new double[2];

    //MST
    private PastMotionStates mPastGoogleActivities=new PastMotionStates(Source.Google, Constants.GOOGLE_ACTIVITY_LAST_STATE_NO);
    private PastMotionStates mPastClassifiedMotionStates=new PastMotionStates(Source.Classifier, Constants.NO_OF_PAST_STATES_STORED);
    private CachedDetectionList mCachedUnparkingDetectionList=new CachedDetectionList(CachedDetection.Type.Unparking);
    private CachedDetectionList mCachedParkingDetectionList=new CachedDetectionList(CachedDetection.Type.Parking);

    private double[] lastClassifiedMotionStateDistr=null;
    private double[] lastAccReading;


    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    //private FusionManager mFusionManager;

    //Detection Interval Fields

    private long lastParkingTimestamp=-1;
    private long lastUnparkingTimestamp=-1;
    */

    //New Shit
    private static CordovaWebView mwebView;
    private static CordovaInterface mcordova;
    public static long lastStatusChangeTime = 0;
    public static boolean showMessages = false;

    private static final String LOG_TAG = "BluetoothStatus";
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

    //private CordovaLocationListener mListener;
    private LocationManager mLocationManager;

    //JS Interface
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(action.equals("initPlugin")) {
            showMessages = args.getBoolean(0);
            initPlugin();
            return true;
        }
        return false;
    }
    private void initPlugin() {
        //Do nothing
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        userID = Settings.Secure.getString(cordova.getActivity().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(cordova.getActivity())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mwebView = super.webView;
        mcordova = cordova;

        //test if B supported
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "Bluetooth is not supported");
        } else {
            Log.e(LOG_TAG, "Bluetooth is supported");
            //mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
            //bluetoothManager = (BluetoothManager) webView.getContext().getSystemService(Context.BLUETOOTH_SERVICE);

            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // Register for broadcasts on BluetoothAdapter state change
            mcordova.getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            mcordova.getActivity().registerReceiver(mReceiver, new IntentFilter(Constants.GOOGLE_ACTIVITY_RECOGNITION_UPDATE));
            mcordova.getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));


            //test if BT enabled
            if (bluetoothAdapter.isEnabled()) {
                toastMessage("Bluetooth is enabled. Parking detector starting");
            } else {
                toastMessage("Bluetooth is disabled. Parking Detector cannot start");
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "Location Services connected");
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
        Log.d(LOG_TAG, "GoogleApiClient connection has been suspend");
    }


    //broadcast receiver for BT intent changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                Log.e(LOG_TAG, "Bluetooth action state changed");
                long curTime = System.currentTimeMillis() / 1000;
                if (curTime - lastStatusChangeTime > Constants.STATUS_CHANGE_INTERVAL_THRESHOLD) {
                    Log.d(LOG_TAG, "Passed Bluetooth time check");
                    lastStatusChangeTime = curTime;
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (!bluetoothTarget.equals("") && !device.getName().equals(bluetoothTarget))
                        //the connected bt device needs to be the car if identified by the user
                        return;
                    lastBluetoothName = device.getName();
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                        toastMessage("bluetooth connected to " + device.getName());
                    } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                        toastMessage("bluetooth disconnected from " + device.getName());
                    }
                    //get location
                    if (ContextCompat.checkSelfPermission(cordova.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        int eventCode = intent.getIntExtra(Constants.BLUETOOTH_CON_UPDATE_EVENT_CODE, Constants.OUTCOME_NONE);
                        mLocationRequest = new LocationRequest();
                        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setNumUpdates(1);
                        LocationServices.FusedLocationApi.requestLocationUpdates(
                                mGoogleApiClient, mLocationRequest, new BluetoothLocationClientListener(eventCode));
                    }
                } else {
                    Log.d(LOG_TAG, "Failed Bluetooth time check");
                }
            }
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.e(LOG_TAG, "Bluetooth was disabled");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.e(LOG_TAG, "Bluetooth was enabled");
                        break;
                }
            }
            if (action.equals(Constants.GOOGLE_ACTIVITY_RECOGNITION_UPDATE)) {
                String mostLikelyActivity = intent.getStringExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE);
                float mostLikelyActivityConfidence = intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_CONFIDENCE, 0);
                float onFootConfidence = intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_ON_FOOT_ACTIVITY_CONFIDENCE, 0);
                float inVehicleConfidence = intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_IN_VEHICLE_ACTIVITY_CONFIDENCE, 0);

                int mostLikelyActivityType = intent.getIntExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE_INT, DetectedActivity.UNKNOWN);

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
                currentTransportationMode = mostLikelyActivityType;

                if (currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                    if (pendingBTDetection != null && pendingBTDetection.eventCode() == Constants.OUTCOME_UNPARKING) {
                        actionsOnBTDetection(pendingBTDetection.eventCode(), pendingBTDetection.location(), null);
                    }
                }
                if (prevTransportationMode == DetectedActivity.IN_VEHICLE) {
                    if (pendingBTDetection != null && pendingBTDetection.eventCode() == Constants.OUTCOME_PARKING) {
                        actionsOnBTDetection(pendingBTDetection.eventCode(), pendingBTDetection.location(), null);
                    }
                }

            }
        }
    };

    public class BluetoothLocationClientListener implements LocationListener {
        int eventCode;

        public BluetoothLocationClientListener(int eventCode){
            this.eventCode=eventCode;
            if(!btVerificed && !askedForConformation) {
                askedForConformation = true;
                AlertDialog.Builder confirmBT = new AlertDialog.Builder(cordova.getActivity())
                        .setMessage("Is " + lastBluetoothName + " your car's bluetooth?")
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                SharedPreferences mPrefs = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor=mPrefs.edit();
                                editor.putString(Constants.BLUETOOTH_CAR_DEVICE_NAME, lastBluetoothName);
                                bluetoothTarget = lastBluetoothName;
                                btVerificed = true;
                                editor.commit();
                            }
                        })
                        .setNegativeButton("no", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog
                            }
                        });
                AlertDialog alertDialog = confirmBT.create();
                alertDialog.show();
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            BTParkingLocationReceived(eventCode, location, null);
        }
    }
    public void BTParkingLocationReceived(int eventCode, Location location, String address) {
        Log.d("dt-test", "Parking location recieved");
        if(eventCode==Constants.OUTCOME_UNPARKING){
            if (currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                actionsOnBTDetection(eventCode, location, null);
            } else {
                pendingBTDetection = new BTPendingDetection(eventCode, location);
                toastMessage("waiting for vehicle to begin driving");
            }
        }else{
            Log.d("dt-test", "lastBT: "+ lastBluetoothName + " BTtarget: " + bluetoothTarget + " bool " + lastBluetoothName.equals(bluetoothTarget));
            if (prevTransportationMode == DetectedActivity.IN_VEHICLE || lastBluetoothName.equals(bluetoothTarget)) {
                actionsOnBTDetection(eventCode, location, null);
            } else {
                if(currentTransportationMode == DetectedActivity.IN_VEHICLE){
                    pendingBTDetection = new BTPendingDetection(eventCode, location);
                    toastMessage("waiting for vehicle to stop");
                }
            }
        }
    }
    // actions taken when a parking/unparking event is detected and the location of the event is retrieved
    private void actionsOnBTDetection(int eventCode, Location location, String address){
        pendingBTDetection = null;
        if(eventCode==Constants.OUTCOME_PARKING){
            toastMessage("Parking Detected");
            SendParkReport sendPark = new SendParkReport(location, -1, lastBluetoothName, btVerificed);
            sendPark.execute();
        }else{
            toastMessage("New Space Detected");
            SendParkReport sendDePark = new SendParkReport(location,1,lastBluetoothName, btVerificed);
            sendDePark.execute();
            isParked = false;
        }
    }

    public static void toastMessage(final String message) {
        if (message != null && message.length() > 0) {
            if(!showMessages){
                Log.i(LOG_TAG,message);
                return;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    Toast.makeText(mcordova.getActivity(), message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }


    // End of new

    /*

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("dt-test", "In Parking Detector  Broadcast Reciever");

            String action=intent.getAction();
            if(action.equals(Constants.BLUETOOTH_CONNECTION_UPDATE)) {
                //Check Location Position
                if (ContextCompat.checkSelfPermission(cordova.getActivity(),
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

                    // Should we show an explanation?
                    if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(),
                            Manifest.permission.ACCESS_FINE_LOCATION)) {

                        // Show an expanation to the user *asynchronously* -- don't block
                        // this thread waiting for the user's response! After the user
                        // sees the explanation, try again to request the permission.

                    } else {

                        // No explanation needed, we can request the permission.

                        ActivityCompat.requestPermissions(cordova.getActivity(),
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                MY_PERMISSIONS_REQUEST_FINE_LOCATION);

                        // result of the request.
                    }
                }
                else{
                    int eventCode = intent.getIntExtra(Constants.BLUETOOTH_CON_UPDATE_EVENT_CODE, Constants.OUTCOME_NONE);
                    Log.d("dt-test", "in broadcase Reciever " + eventCode);
                    mLocationRequest = new LocationRequest();
                    mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setNumUpdates(1);
                    LocationServices.FusedLocationApi.requestLocationUpdates(
                            mGoogleApiClient, mLocationRequest, new BluetoothLocationClientListener(eventCode));
                }
            }else{
                //TODO return from Google activity update
                if(action.equals(Constants.GOOGLE_ACTIVITY_RECOGNITION_UPDATE)){
                    String mostLikelyActivity=intent.getStringExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE);
                    float mostLikelyActivityConfidence=intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_CONFIDENCE, 0);
                    float onFootConfidence=intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_ON_FOOT_ACTIVITY_CONFIDENCE, 0);
                    float inVehicleConfidence=intent.getFloatExtra(Constants.GOOGLE_ACT_UPDATE_IN_VEHICLE_ACTIVITY_CONFIDENCE, 0);

                    int mostLikelyActivityType=intent.getIntExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE_INT, DetectedActivity.UNKNOWN);

                    if(mostLikelyActivityType==DetectedActivity.UNKNOWN){
                        if(inVehicleConfidence>100-inVehicleConfidence-mostLikelyActivityConfidence)
                            mostLikelyActivityType=DetectedActivity.IN_VEHICLE;
                        else{
                            if(onFootConfidence>100-onFootConfidence-mostLikelyActivityConfidence)
                                mostLikelyActivityType=DetectedActivity.ON_FOOT;
                        }
                    }

                    if(currentTransportationMode != mostLikelyActivityType){
                        prevTransportationMode = currentTransportationMode;
                    }
                    currentTransportationMode = mostLikelyActivityType;

                    if (currentTransportationMode == DetectedActivity.IN_VEHICLE) {
                        if (pendingBTDetection != null && pendingBTDetection.eventCode() == Constants.OUTCOME_UNPARKING) {
                            actionsOnBTDetection(pendingBTDetection.eventCode(), pendingBTDetection.location(), null);
                        }
                    }
                    if (prevTransportationMode == DetectedActivity.IN_VEHICLE) {
                        if (pendingBTDetection != null && pendingBTDetection.eventCode() == Constants.OUTCOME_PARKING) {
                            actionsOnBTDetection(pendingBTDetection.eventCode(), pendingBTDetection.location(), null);
                        }
                    }

                    MotionState.Type activityType=MotionState.translate(mostLikelyActivityType);
                    mPastGoogleActivities.add(activityType);

                    if(activityType==MotionState.Type.IN_VEHICLE
                            ||activityType==MotionState.Type.ON_FOOT){
                        int outcome;
                        CachedDetection oldestNotExpiredCachedDetection=null;
                        if(activityType==MotionState.Type.IN_VEHICLE){
                            outcome=Constants.OUTCOME_UNPARKING;
                            oldestNotExpiredCachedDetection=mCachedUnparkingDetectionList.get(0);
                        }else{
                            outcome=Constants.OUTCOME_PARKING;
                            oldestNotExpiredCachedDetection=mCachedParkingDetectionList.get(0);
                        }

                        if(mPastGoogleActivities.isTransitionTo(activityType)
                                &&oldestNotExpiredCachedDetection!=null){
                            //Do nothing
                        }

                    }

                    //build the new MST vector
                    double[] probsOfNewUpdate=null;
                    if(probOfOnFootAndInVehicleOfLastUpdate!=null){
                        probsOfNewUpdate=new double[]{onFootConfidence/100, inVehicleConfidence/100};
                        ArrayList<Double> features=new ArrayList<Double>();
                        features.add(probOfOnFootAndInVehicleOfLastUpdate[0]);
                        features.add(probOfOnFootAndInVehicleOfLastUpdate[1]);
                        features.add(probsOfNewUpdate[0]);
                        features.add(probsOfNewUpdate[0]);

                        HashMap<Integer, ArrayList<Double>> mstVector=new HashMap<Integer, ArrayList<Double>>();
                        mstVector.put(Constants.INDICATOR_MST, features);
                        // Log.d(LOG_TAG, "Google MST Vector: "+features.toString());
                    }
                    probOfOnFootAndInVehicleOfLastUpdate=probsOfNewUpdate;
                }
            }
        }
    };
    // actions taken when a parking/unparking event is detected and the location of the event is retrieved

    // Make sure that Bluetooth is enabled

    public void startBTService(){
    //Check Location
        Log.d("dt-test", "In Start BT Service");

        if (ContextCompat.checkSelfPermission(cordova.getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(cordova.getActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(cordova.getActivity(),
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_FINE_LOCATION);

                // result of the request.
            }
        } else {
            Log.d("dt-test", "Bluetooth enabled, kicking off listener");
            //Kick off bluetooth service
            Intent intent = new Intent(cordova.getActivity(), BluetoothConnectionService.class);
            cordova.getActivity().startService(intent);
        }
    }

    public void startDetection() {
        if (onCreateCalled) {
            return;
        } else {
            onCreateCalled = true;
        }
        mContext = cordova.getActivity();
        SharedPreferences sharedPref = cordova.getActivity().getPreferences(Context.MODE_PRIVATE);
        lastBluetoothName = sharedPref.getString(Constants.BLUETOOTH_CAR_DEVICE_NAME, "");
        bluetoothTarget = lastBluetoothName;
        if(lastBluetoothName == ""){
            btVerificed = false;
        }

        userID = Settings.Secure.getString(cordova.getActivity().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        // Set the broadcast receiver intent filer
        mBroadcastManager = LocalBroadcastManager.getInstance(cordova.getActivity());

        // Create a new Intent filter for the broadcast receiver
        mBroadcastFilter = new IntentFilter(Constants.ACTION_REFRESH_STATUS_LIST);
        mBroadcastFilter.addCategory(Constants.CATEGORY_LOCATION_SERVICES);
        mBroadcastFilter.addAction(Constants.BLUETOOTH_CONNECTION_UPDATE);
        mBroadcastFilter.addAction(Constants.GOOGLE_ACTIVITY_RECOGNITION_UPDATE);
        mBroadcastManager.registerReceiver(mBroadcastReceiver, mBroadcastFilter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Start Google Activity Recognition
        mGoogleActivityDetectionRequester = new GoogleActivityRecognitionClientRequester(cordova.getActivity());
        mGoogleActivityDetectionRemover = new GoogleActivityRecognitionClientRemover(cordova.getActivity());
        mGoogleActivityDetectionRequester.requestUpdates();

        startBTService();
    }
    */

}
/*
class PastMotionStates{
    public int capacity;
    public Source source;
    public HashMap<MotionState.Type, Integer> map;
    public ArrayList<MotionState.Type> list;

    public long timestampOfLastInVehicleState;
    public long timestampOfLastOnFootState;
    public static final long EXPIRATION_TIME_IN_MILLISEC=Constants.ONE_MINUTE+Constants.ONE_MINUTE/2;

    public PastMotionStates(Source source, int capacity) {
        this.source = source;
        this.capacity = capacity;
        map = new HashMap<MotionState.Type, Integer>();
        list = new ArrayList<MotionState.Type>();
    }

    public void clear(){
        Log.d("dt-test","function clear called");
        map.clear();
        list.clear();
    }

    public void add(MotionState.Type state) {
        Log.d("dt-test","in add motion state");
        if (list.size() == capacity) {
            MotionState.Type removedMotionType = list.remove(0);// remove the oldest state
            map.put(removedMotionType, map.get(removedMotionType) - 1);
        }
        list.add(state);
        if (!map.containsKey(state))
            map.put(state, 0);
        map.put(state, map.get(state) + 1);
    }

    public void removeAll(MotionState.Type state) {
        while(list.remove(state));
        map.remove(state);
    }

    public boolean isTransitionTo(MotionState.Type state){
        if(state!=MotionState.Type.IN_VEHICLE&&state!=MotionState.Type.ON_FOOT) return false;
        boolean ret=containsAtLeastMOnFootAndAtLeastNInVehicleStates(1, 1)&&containsOnlyOneAndLater(state);
        if(ret){
            if(state==MotionState.Type.IN_VEHICLE) removeAll(MotionState.Type.ON_FOOT);
            else removeAll(MotionState.Type.IN_VEHICLE);
        }
        return ret;
    }

    public boolean containsAtLeastMOnFootAndAtLeastNInVehicleStates(int mOnFoot, int nInVehicle) {
        // return false if the filter fails
        if (!map.containsKey(MotionState.Type.ON_FOOT)
                || !map.containsKey(MotionState.Type.IN_VEHICLE))
            return false;
        int walkingCnt = map.get(MotionState.Type.ON_FOOT);
        int drivingCnt = map.get(MotionState.Type.IN_VEHICLE);
        // Log.e(LOG_TAG,"#Walk="+walkingCnt+" #Drive="+drivingCnt);
        if (walkingCnt < mOnFoot  || drivingCnt < nInVehicle)
            return false;
        return true;
    }

    //Type equals to either On_foot or In_vehicle
    public boolean containsOnlyOneAndLater(MotionState.Type type) {
        if (!map.containsKey(type)||map.get(type)!=1) return false;

        for(int i=list.size()-1;i>=0;i--){
            MotionState.Type curType=list.get(i);
            if(curType!=MotionState.Type.ON_FOOT&&curType!=MotionState.Type.IN_VEHICLE) continue;
            if(curType==type) return true;
            else return false;
        }
        return false;
    }

    public String toString() {
        String ret = list.toString() + "\n";
        for (Type type : map.keySet())
            ret += type.toString() + ":" + map.get(type) + "  ";
        return ret;
    }
}

class MotionState {
    public enum Source {
        Google, Classifier;
    }

    public enum Type {
        ON_FOOT("On_Foot"), IN_VEHICLE("In_Vehicle"), STILL("Still"), UNKNOWN(
                "Unknown"), ON_BIKE("On_Bike"), OTHER("Other");

        private String typeString;

        private Type(String type) {
            this.typeString = type;
        }

        public String toString() {
            return typeString;
        }
    }

    public Source source;
    public Type type;
    public int secondOfDay;

    public static MotionState.Type translate(String predClass) {
        MotionState.Type ret;
        if ("Walking".equals(predClass)) {
            ret=MotionState.Type.ON_FOOT;
        } else {
            if ("Driving".equals(predClass))
                ret=MotionState.Type.IN_VEHICLE;
            else {
                if ("Still".equals(predClass))
                    ret=MotionState.Type.STILL;
                else
                    ret=MotionState.Type.OTHER;
            }
        }
        return ret;
    }

    public static MotionState.Type translate(int activityTypeDefinedByGoogle) {
        MotionState.Type ret;
        switch (activityTypeDefinedByGoogle) {
            case DetectedActivity.ON_FOOT:
                ret=MotionState.Type.ON_FOOT;
                break;
            case DetectedActivity.IN_VEHICLE:
                ret=MotionState.Type.IN_VEHICLE;
                break;
            case DetectedActivity.STILL:
                ret=MotionState.Type.STILL;
                break;
            case DetectedActivity.ON_BICYCLE:
                ret=MotionState.Type.ON_BIKE;
            default:
                ret=MotionState.Type.UNKNOWN;
                break;
        }
        return ret;
    }
}

class CachedDetection{
    public enum Type{
        Parking, Unparking
    }
    public long timestamp;
    public Location location;
    public String address;
    public Type type;
    public static final long EXPIRATION_TIME=Constants.ONE_MINUTE;

    public CachedDetection(Type type, Location loc, long time, String address){
        timestamp=time;
        location=loc;
        this.type=type;
        this.address=address;
    }
}

class CachedDetectionList{
    CachedDetection.Type type;
    ArrayList<CachedDetection> list;
    public CachedDetectionList(CachedDetection.Type type) {
        this.type=type;
        list=new ArrayList<CachedDetection>();
    }

    public void removeExpiredCachedDetection(){
        //remove expired cached detections
        long curtime=System.currentTimeMillis();
        int i;
        ArrayList<CachedDetection> newList=new ArrayList<CachedDetection>();
        for(i=0;i<list.size();i++){
            if(curtime-list.get(i).timestamp<=CachedDetection.EXPIRATION_TIME){
                newList.add(list.get(i));
            }
        }
        list=newList;
    }

    public void add(CachedDetection cd){
        removeExpiredCachedDetection();
        //add the new one
        list.add(cd);
    }

    public CachedDetection get(int index){
        removeExpiredCachedDetection();
        if(index<0||index>=list.size()) return null;
        return list.get(index);
    }
}*/