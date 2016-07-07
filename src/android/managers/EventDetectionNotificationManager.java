package com.uic.sandeep.streetsmart.managers;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.uic.sandeep.streetsmart.R;

import android.app.NotificationManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class EventDetectionNotificationManager {
	public static final String LOG_TAG=EventDetectionNotificationManager.class.getCanonicalName();
	
	private Context mContext;
	
	public static EventDetectionNotificationManager mNotificationManagerInstanceManager;
	
	
	
	private EventDetectionNotificationManager(Context ctxt){
		mContext=ctxt;
	}
	
	public static EventDetectionNotificationManager getInstance(Context ctxt) {
		if(mNotificationManagerInstanceManager==null){
			mNotificationManagerInstanceManager=new EventDetectionNotificationManager(ctxt);
		}
		return mNotificationManagerInstanceManager;
	}
	
	/**
     * Post a parking/unparking detected notification to the user. 
     */
    public void sendTextNotification(String notificationText) {

        // Create a notification builder that's compatible with platforms >= version 4
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(mContext);

        // Set the title, text, and icon
        builder.setContentTitle(mContext.getString(R.string.app_name))
               .setContentText(notificationText)
               .setSmallIcon(R.drawable.ic_notification);
               
        // Get an instance of the Notification Manager
        NotificationManager notifyManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // Build the notification and post it
        notifyManager.notify(0, builder.build());
    }
    /**
     * 
     * @param resId: the int id that points to the voice resource
     */
    public void playVoiceNotification(int resID)
	{
		if(resID==0){
			Log.d(LOG_TAG, "this event is neither a parking nor unparking event.");
			return;
		}
		MediaPlayer mediaPlayer = MediaPlayer.create(mContext, resID);
		mediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener() {
			@Override
			public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
			}
		});
		mediaPlayer.setOnPreparedListener(new OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
			}
		});
		
		float volume = 10.0f;
		mediaPlayer.setVolume(volume, volume);
		mediaPlayer.start();
		//mediaPlayer.release();
		//mediaPlayer = null;
	}
    
    public Marker addMarkersToMap(GoogleMap mMap, String curTime, String title, double lat, double lon, double alt, float color) {
    	Log.e(LOG_TAG, "add a new marker on the map");
    	LatLng latLng=new LatLng(lat, lon);
        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet("Time: "+curTime
                		+"\nLatitude: "+lat
                		+"\nLongitude: "+lon
                		+"\nAltitude: "+alt
                		)
                .icon(BitmapDescriptorFactory.defaultMarker(color)));

        return marker;
    }
	
}
