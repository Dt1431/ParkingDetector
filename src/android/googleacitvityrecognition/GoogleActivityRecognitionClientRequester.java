
package cordova.plugin.parking.detector.googleacitvityrecognition;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import cordova.plugin.parking.detector.Constants;


/**
 * Class for connecting to Location Services and activity recognition updates.
 * <b>
 * Note: Clients must ensure that Google Play services is available before requesting updates.
 * </b> Use GooglePlayServicesUtil.isGooglePlayServicesAvailable() to check.
 *
 *
 * To use a DetectionRequester, instantiate it and call requestUpdates(). Everything else is done
 * automatically.
 *
 */
public class GoogleActivityRecognitionClientRequester implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
	
	GoogleApiClient mGoogleApiClient;
	public static final String LOG_TAG=GoogleActivityRecognitionClientRequester.class.getCanonicalName();
	private Context mContext;
	
	 // Stores the PendingIntent used to send activity recognition events back to the app
    private PendingIntent mActivityRecognitionPendingIntent;

    // Stores the current instantiation of the activity recognition client

    public GoogleActivityRecognitionClientRequester(Context context) {
        // Save the context
        mContext = context;

        // Initialize the globals to null
        mActivityRecognitionPendingIntent = null;
        
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
    		.addApi(ActivityRecognition.API)
    		.addConnectionCallbacks(this)
    		.addOnConnectionFailedListener(this)
    		.build();
    }
    /**
     * Returns the current PendingIntent to the caller.
     *
     * @return The PendingIntent used to request activity recognition updates
     */
    public PendingIntent getRequestPendingIntent() {
        return mActivityRecognitionPendingIntent;
    }

    /**
     * Sets the PendingIntent used to make activity recognition update requests
     * @param intent The PendingIntent
     */
    public void setRequestPendingIntent(PendingIntent intent) {
        mActivityRecognitionPendingIntent = intent;
    }

    /**
     * Start the activity recognition update request process by
     * getting a connection.
     */
    public void requestUpdates() {
        requestConnection();
    }
    /**
     * Make the actual update request. This is called from onConnected().
     */
    private void continueRequestActivityUpdates() {
        Log.i(LOG_TAG,"Creating request activity update");

        /*
         * Request updates, using the default detection interval.
         * The PendingIntent sends updates to ActivityRecognitionIntentService
         */
    	final SharedPreferences mPrefs = mContext.getSharedPreferences(Constants.SHARED_PREFERENCES,  Context.MODE_PRIVATE);
    	int updateInterval=mPrefs.getInt(Constants.PREFERENCE_KEY_GOOGLE_ACTIVITY_UPDATE_INTERVAL, 
    			Constants.GOOGLE_ACTIVITY_UPDATE_INTERVAL_DEFAULT_VALUE);
    	
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 
        		updateInterval*Constants.ONE_SECOND_IN_MILLISECOND, createRequestPendingIntent());

        // Disconnect the client
        requestDisconnection();
    }
    
    /**
     * Request a connection to activity recognition Services. This call returns immediately,
     * but the request is not complete until onConnected() or onConnectionFailure() is called.
     */
    private void requestConnection() {
    	mGoogleApiClient.connect();
    }

    /**
     * Get the current activity recognition client and disconnect from Location Services
     */
    private void requestDisconnection() {
    	mGoogleApiClient.disconnect();
    }

	@Override
	public void onConnected(Bundle arg0) {
        Log.i(LOG_TAG,"Connected to google api");

		// TODO Auto-generated method stub
        // If debugging, log the connection
        // If debugging, log the connection
        // Continue the process of requesting activity recognition updates
        continueRequestActivityUpdates();
		
	}


    /**
     * Get a PendingIntent to send with the request to get activity recognition updates. Location
     * Services issues the Intent inside this PendingIntent whenever a activity recognition update
     * occurs.
     *
     * @return A PendingIntent for the IntentService that handles activity recognition updates.
     */
    private PendingIntent createRequestPendingIntent() {
        Log.i(LOG_TAG,"Creating pending intent");

        // If the PendingIntent already exists
        if (null != getRequestPendingIntent()) {

            // Return the existing intent
            return mActivityRecognitionPendingIntent;

        // If no PendingIntent exists
        } else {
            // Create an Intent pointing to the IntentService
            Intent intent = new Intent(mContext, ActivityRecognitionIntentService.class);

            /*
             * Return a PendingIntent to start the IntentService.
             * Always create a PendingIntent sent to Location Services
             * with FLAG_UPDATE_CURRENT, so that sending the PendingIntent
             * again updates the original. Otherwise, Location Services
             * can't match the PendingIntent to requests made with it.
             */
            PendingIntent pendingIntent = PendingIntent.getService(mContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            setRequestPendingIntent(pendingIntent);
            return pendingIntent;
        }

    }
    /*
     * Implementation of OnConnectionFailedListener.onConnectionFailed
     * If a connection or disconnection request fails, report the error
     * connectionResult is passed in from Location Services
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {

            try {
                connectionResult.startResolutionForResult((Activity) mContext,
                    Constants.CONNECTION_FAILURE_RESOLUTION_REQUEST);

            /*
             * Thrown if Google Play services canceled the original
             * PendingIntent
             */
            } catch (SendIntentException e) {
               // display an error or log it here.
            }

        /*
         * If no resolution is available, display Google
         * Play service error dialog. This may direct the
         * user to Google Play Store if Google Play services
         * is out of date.
         */
        } else {
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                    connectionResult.getErrorCode(),
                    (Activity) mContext,
                    Constants.CONNECTION_FAILURE_RESOLUTION_REQUEST);
            if (dialog != null) {
                dialog.show();
            }
        }
    }

	@Override
	public void onConnectionSuspended(int arg0) {
		// TODO Auto-generated method stub
        // In debug mode, log the disconnection
        // Destroy the current activity recognition client
        mGoogleApiClient = null;
		
	}

}