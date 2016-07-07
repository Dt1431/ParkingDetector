/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cordova.plugin.parking.detector.googleacitvityrecognition;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import cordova.plugin.parking.detector.Constants;
//import cordova.plugin.parking.detector.managers.EventDetectionNotificationManager;
//import cordova.plugin.parking.detector.managers.LocationManagerWrapper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service that receives ActivityRecognition updates. It receives updates
 * in the background, even if the main Activity is not visible.
 */
public class ActivityRecognitionIntentService extends IntentService {
	// TAG for the class;
	private static final String LOG_TAG=ActivityRecognitionIntentService.class.getSimpleName();
	


    // Delimits the timestamp from the log info
    private static final String LOG_DELIMITER = " ";

    // A date formatter
    private SimpleDateFormat mDateFormat;

    // Store the app's shared preferences repository
    private SharedPreferences mPrefs;
    
    // An instance of the notificaiton manager
    //private EventDetectionNotificationManager mNotificationManagerWrapper;
    
    // The location manager object
    //private LocationManagerWrapper mLocationManagerWrapper;

    public ActivityRecognitionIntentService() {
        // Set the label for the service's background thread
        super("ActivityRecognitionIntentService");
    }

    /**
     * Called when a new activity detection update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        // Get a handle to the repository
        mPrefs = getApplicationContext().getSharedPreferences(
                Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE);

        // Get a date formatter, and catch errors in the returned timestamp
        try {
            mDateFormat = (SimpleDateFormat) DateFormat.getDateTimeInstance();
        } catch (Exception e) {

        }
        
        // Format the timestamp according to the pattern, then localize the pattern
        mDateFormat.applyPattern("yyyy-MM-dd HH:mm:ss:SSS");
        mDateFormat.applyLocalizedPattern(mDateFormat.toLocalizedPattern());

        //mNotificationManagerWrapper=EventDetectionNotificationManager.getInstance(this);
        //mLocationManagerWrapper=LocationManagerWrapper.getInstance(this);
        
        // If the intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {

            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

            // Get the most probable activity from the list of activities in the update
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();
           
            // Get the confidence percentage for the most probable activity
            int confidenceOfMostProbableActivity = mostProbableActivity.getConfidence();

            int confidenceOfOnFoot=result.getActivityConfidence(DetectedActivity.ON_FOOT);
            int confidenceOfInVehicle=result.getActivityConfidence(DetectedActivity.IN_VEHICLE);
            
            // Get the type of activity
            int mostProbableActivityType = mostProbableActivity.getType();

            Location curLocation=null;
            
        	/*if (
                   // If the current type is "moving"
                   isMoving(activityType)
                   &&
                   // The activity has changed from the previous activity
                   activityChanged(activityType)
                   // The confidence level for the current activity is > 50%
                   && (confidence >= 50)) {

                // Notify the user
                mNotificationManagerWrapper.sendTextNotification("Please enable the GPS.");
        	}
        	if(isFromOnFoottoInVehicle(mostProbableActivity)){
        		//request a location fix
        		curLocation=mLocationManagerWrapper.getLatestLocation();
        	}
        	if(isFromInVehicletoOnFoot(mostProbableActivity)){
        		//request a location fix
        		curLocation= mLocationManagerWrapper.getLatestLocation();
        	}
        	
            // Save the current state 
            Editor editor = mPrefs.edit();
            editor.putInt(Constants.KEY_PREVIOUS_ACTIVITY_TYPE, activityType);
            editor.commit();
            
            // Log the update
            logActivityRecognitionResult(result, curLocation);*/
            
           sendActivityUpdateToMainActivity(mostProbableActivityType, getNameFromType(mostProbableActivityType), confidenceOfMostProbableActivity
        		   , confidenceOfOnFoot, confidenceOfInVehicle);
        }
    }
    
    private void sendActivityUpdateToMainActivity(int mostProbableActivityTypeInt, String mobilityState, int confidenceOfMostProbableActivity, int confidenceOfOnFoot, int confidenceOfInVehicle){
		/*Log.e(LOG_TAG, "Send out the google activity update back to main activity\n " +
				"most likely activity:  "+mobilityState+"   confidence: "+confidenceOfMostProbableActivity);*/ //Commented by Sandeep
		Intent ackIntent = new Intent(Constants.GOOGLE_ACTIVITY_RECOGNITION_UPDATE);
		ackIntent.putExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE, mobilityState);
		ackIntent.putExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE_INT, mostProbableActivityTypeInt);
		ackIntent.putExtra(Constants.GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_CONFIDENCE, (float)confidenceOfMostProbableActivity);
		ackIntent.putExtra(Constants.GOOGLE_ACT_UPDATE_ON_FOOT_ACTIVITY_CONFIDENCE, (float)confidenceOfOnFoot);
		ackIntent.putExtra(Constants.GOOGLE_ACT_UPDATE_IN_VEHICLE_ACTIVITY_CONFIDENCE, (float)confidenceOfInVehicle);
		
		LocalBroadcastManager.getInstance(this).sendBroadcast(ackIntent);
	}
    
    /**
     * Tests to see if the activity has changed
     *
     * @param currentType The current activity type
     * @return true if the user's current activity is different from the previous most probable
     * activity; otherwise, false.
     */
    private boolean activityChanged(int currentType) {
    	if(!mPrefs.contains(Constants.KEY_PREVIOUS_ACTIVITY_TYPE)) return false;
    	
        // Get the previous type, otherwise return the "unknown" type
        int previousType = mPrefs.getInt(Constants.KEY_PREVIOUS_ACTIVITY_TYPE,
                DetectedActivity.UNKNOWN);

        // If the previous type isn't the same as the current type, the activity has changed
        if (previousType != currentType) {
            return true;

        // Otherwise, it hasn't.
        } else {
            return false;
        }
    }
    
    private boolean isFromOnFoottoInVehicle(DetectedActivity newDetectedActivity){
        // Get the previous type, otherwise return the "unknown" type
        int previousType = mPrefs.getInt(Constants.KEY_PREVIOUS_ACTIVITY_TYPE,
                DetectedActivity.UNKNOWN);

        // If the previous type isn't the same as the current type, the activity has changed
        if (previousType==DetectedActivity.ON_FOOT && newDetectedActivity.getType()==DetectedActivity.IN_VEHICLE){
            return true;
        }

    	return false;
    }
    
    private boolean isFromInVehicletoOnFoot(DetectedActivity newDetectedActivity){
        
    	int curType=newDetectedActivity.getType();

    	Editor editor = mPrefs.edit();
    	//in_vehicle to on_foot
    	if(curType==DetectedActivity.ON_FOOT && mPrefs.getInt(Constants.KEY_PREVIOUS_ACTIVITY_TYPE,DetectedActivity.UNKNOWN)==DetectedActivity.IN_VEHICLE)
    	{    		
            editor.putInt(Constants.KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT, 1);          
    	}else{
    		//two consecutive on_foot
    		if(mPrefs.contains(Constants.KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT)
    		&& curType==DetectedActivity.ON_FOOT 
    		&& mPrefs.getInt(Constants.KEY_PREVIOUS_ACTIVITY_TYPE,DetectedActivity.UNKNOWN)==DetectedActivity.ON_FOOT){
	    		
    			int cnt=mPrefs.getInt(Constants.KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT, 1);
	    	    editor.putInt(Constants.KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT, cnt+1);
	    	    //found 3 consecutive on_foot activities
	    	    if(cnt+1==3) return true;
    		}
    	}
    	editor.commit();
    	
    	return false;
    }

    /**
     * Get a content Intent for the notification
     *
     * @return A PendingIntent that starts the device's Location Settings panel.
     */
    private PendingIntent getContentIntent() {

        // Set the Intent action to open Location Settings
        Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);

        // Create a PendingIntent to start an Activity
        return PendingIntent.getActivity(getApplicationContext(), 0, gpsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }


    /**
     * Determine if an activity means that the user is moving.
     *
     * @param type The type of activity the user is doing (see DetectedActivity constants)
     * @return true if the user seems to be moving from one location to another, otherwise false
     */
    private boolean isMoving(int type) {
        switch (type) {
            // These types mean that the user is probably not moving
            case DetectedActivity.STILL :
            case DetectedActivity.TILTING :
            case DetectedActivity.UNKNOWN :
                return false;
            default:
                return true;
        }
    }

    /**
     * Write the activity recognition update to the log file

     * @param result The result extracted from the incoming Intent
     */
    private void logActivityRecognitionResult(ActivityRecognitionResult result, Location curLocation) {
        // Get all the probably activities from the updated result

    	DetectedActivity mostLikelyActivity=result.getMostProbableActivity();
        /*for (DetectedActivity detectedActivity : result.getProbableActivities()) {

            // Get the activity type, confidence level, and human-readable name
            int confidence = detectedActivity.getConfidence();
            if(confidence>maxConfidence){
            	confidence=maxConfidence;
            	mostLikelyActivity=detectedActivity;
            }
        }*/
        // Make a timestamp
        String timeStamp = mDateFormat.format(new Date());

        String activityName = getNameFromType(mostLikelyActivity.getType());
        
    }

    /**
     * Map detected activity types to strings
     *
     * @param activityType The detected activity type
     * @return A user-readable name for the type
     */
    private String getNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
        }
        return "unknown";
    }
}
