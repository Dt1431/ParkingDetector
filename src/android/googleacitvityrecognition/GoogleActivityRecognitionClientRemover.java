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

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognition;
import cordova.plugin.parking.detector.Constants;

/**
 * Class for connecting to Location Services and removing activity recognition updates.
 * <b>
 * Note: Clients must ensure that Google Play services is available before removing activity 
 * recognition updates.
 * </b> Use GooglePlayServicesUtil.isGooglePlayServicesAvailable() to check.
 *
 *
 * To use a DetectionRemover, instantiate it, then call removeUpdates().
 *
 */
public class GoogleActivityRecognitionClientRemover
        implements ConnectionCallbacks, OnConnectionFailedListener {
	private final String TAG=GoogleActivityRecognitionClientRemover.class.getCanonicalName(); 
	GoogleApiClient mGoogleApiClient;
	
    // Storage for a context from the calling client
    private Context mContext;


    // The PendingIntent sent in removeUpdates()
    private PendingIntent mCurrentIntent;


    /**
     * Construct a DetectionRemover for the current Context
     *
     * @param context A valid Context
     */
    public GoogleActivityRecognitionClientRemover(Context context) {
        // Save the context
        mContext = context;

        // Initialize the globals to null
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
    		.addApi(ActivityRecognition.API)
    		.addConnectionCallbacks(this)
    		.addOnConnectionFailedListener(this)
    		.build();

    }

    /**
     * Remove the activity recognition updates associated with a PendIntent. The PendingIntent is 
     * the one used in the request to add activity recognition updates.
     *
     * @param requestIntent The PendingIntent used to request activity recognition updates
     */
    public void removeUpdates(PendingIntent requestIntent) {

        /*
         * Set the request type, store the List, and request a activity recognition client
         * connection.
         */
        mCurrentIntent = requestIntent;

        // Continue the removal by requesting a connection
        requestConnection();
    }

    /**
     * Request a connection to Location Services. This call returns immediately,
     * but the request is not complete until onConnected() or onConnectionFailure() is called.
     */
    private void requestConnection() {
    	mGoogleApiClient.connect();
    }

    /**
     * Get a activity recognition client and disconnect from Location Services
     */
    private void requestDisconnection() {

        // Disconnect the client
    	mGoogleApiClient.disconnect();

    }

    /*
     * Called by Location Services once the activity recognition client is connected.
     *
     * Continue by removing activity recognition updates.
     */
    @Override
    public void onConnected(Bundle connectionData) {
        // If debugging, log the connection
        // Send a request to Location Services to remove activity recognition updates
        continueRemoveUpdates();
    }

    /**
     * Once the connection is available, send a request to remove activity recognition updates. 
     */
    private void continueRemoveUpdates() {
		try {
			// Remove the updates
			if (mGoogleApiClient != null)
				ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient, mCurrentIntent);

			/*
			 * Cancel the PendingIntent. This stops Intents from arriving at the
			 * IntentService, even if request fails.
			 */
			if (mCurrentIntent != null)
				mCurrentIntent.cancel();

			// Disconnect the client
			requestDisconnection();
		} catch (Exception ex) {
			Log.e(TAG, ex.toString());
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
		
	}
}
