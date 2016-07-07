package com.uic.sandeep.streetsmart.managers;

import com.skyhookwireless.wps.IPLocation;
import com.skyhookwireless.wps.IPLocationCallback;
import com.skyhookwireless.wps.WPSCertifiedLocationCallback;
import com.skyhookwireless.wps.WPSContinuation;
import com.skyhookwireless.wps.WPSLocation;
import com.skyhookwireless.wps.WPSLocationCallback;
import com.skyhookwireless.wps.WPSPeriodicLocationCallback;
import com.skyhookwireless.wps.WPSReturnCode;
import com.skyhookwireless.wps.XPS;
import com.uic.sandeep.streetsmart.Constants;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * A wrapper class that encapsulate the Android's LocationManager class
 * A skeleton class
 * @author Shuo
 *
 */
public class LocationManagerWrapper {
	private static final String LOG_TAG=LocationManagerWrapper.class.getSimpleName();
	
	/**
     * A single callback class that will be used to handle
     * all location notifications sent by WPS to our app.
     */
    private class XPSLocationCallback implements
    IPLocationCallback,
    WPSLocationCallback,
    WPSPeriodicLocationCallback,
    WPSCertifiedLocationCallback
    {
        public void done()
        {           
        }

        public WPSContinuation handleError(final WPSReturnCode error)
        {
        	Log.d(LOG_TAG,error.toString());
            return WPSContinuation.WPS_CONTINUE;
        }

        public WPSContinuation handleWPSPeriodicLocation(final WPSLocation location)
        {
        	latestLocation=getLatestLocationFromIndividualProvider(location);		
        	return WPSContinuation.WPS_CONTINUE;
        }

        public WPSContinuation handleWPSCertifiedLocation(final WPSLocation[] locations)
        {
            // return WPS_STOP if the user pressed the Stop button
            return WPSContinuation.WPS_CONTINUE;
        }

		@Override
		public void handleWPSLocation(WPSLocation arg0) {
			latestLocation=getLatestLocationFromIndividualProvider(arg0);			
		}

		@Override
		public void handleIPLocation(IPLocation arg0) {
		}
    }    
 
	private static final int UPDATE_RATE = (int) (Constants.ONE_MINUTE * 2);
	
	
	private Context mContext;
	private LocationManager locationMgr; 

	private Location latestLocation;
    private XPS mXPSHandler;
	private final XPSLocationCallback mXPSLocationCallback;
	
	private LocationManagerWrapper(Context ctxt) {
		this.mContext = ctxt;
		// latestLocation=null;
		latestLocation = new Location(LocationManager.GPS_PROVIDER);
		// Acquire a reference to the system Location Manager
		locationMgr = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
		
		mXPSLocationCallback = new XPSLocationCallback();
		mXPSHandler=new XPS(mContext);
	}
	
	
	public static LocationManagerWrapper mLocationManager;
	public static LocationManagerWrapper getInstance(Context context){
		if(mLocationManager==null){
			mLocationManager=new LocationManagerWrapper(context);
		}
		return mLocationManager;	
	}
	

	public Location getLatestLocation(){
		//TODO
		//get the location from SkyHook API
		mXPSHandler.getXPSLocation(null,
	            // note we convert _period to seconds
	            (int) (Constants.XPS_PERIOD / 1000),
	            Constants.XPS_ACCURACY,
	            mXPSLocationCallback);
		return latestLocation;
	}
	
	
	/**
	 * 
	 * @param XPSLocation: location by calling the XPS API
	 * @return
	 */
	public Location getLatestLocationFromIndividualProvider(Object WPSLocation) {
		Location bestLocationFromIndividualProvider=null;
		
		String bestProvider="";
		for(String provider: locationMgr.getProviders(true)){
			//get a new location fix from a provider
			bestLocationFromIndividualProvider=locationMgr.getLastKnownLocation(provider);
			if(bestLocationFromIndividualProvider!=null){				
				if(isBetterLocation(bestLocationFromIndividualProvider, latestLocation)){
					latestLocation = bestLocationFromIndividualProvider;
					bestProvider=provider;
				}
			}
		}
		
		if(isBetterLocation((Location)WPSLocation, bestLocationFromIndividualProvider)){
			latestLocation=(Location)WPSLocation;
			Log.d(LOG_TAG, "XPS is the best.");
		}else {
			Log.d(LOG_TAG, bestProvider+ " is the best.");
		}
		Log.d(LOG_TAG,latestLocation.getLatitude()+" "+latestLocation.getLongitude());
		
		// if the lastest location is too stale, the return null;
		if(System.currentTimeMillis()-latestLocation.getTime()>Constants.LOCATION_INTERVAL_MINTIME)
			return null;
		return latestLocation;
	}
	
	public Location getLastLocation(){
		return latestLocation;
	}
	
	public LocationManager getLocationManager(){
		return locationMgr;
	}
		

	/**
	 * Determines whether one Location reading is better than the current
	 * Location fix
	 * 
	 * @param location
	 *            The new Location that you want to evaluate
	 * @param currentBestLocation
	 *            The current Location fix, to which you want to compare the new
	 *            one
	 */
	public boolean isBetterLocation(Location location,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > UPDATE_RATE;
		boolean isSignificantlyOlder = timeDelta < -UPDATE_RATE;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use
		// the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be
			// worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
				.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(),
				currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and
		// accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isLessAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate
				&& isFromSameProvider) {
			return true;
		}
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}

}

