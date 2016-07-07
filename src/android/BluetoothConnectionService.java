package cordova.plugin.parking.detector;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.DetectedActivity;

public class BluetoothConnectionService extends Service {
	public static final String LOG_TAG=BluetoothConnectionService.class.getCanonicalName();

	private int parkingStatus = Constants.OUTCOME_NONE;
	private long lastStatusChangeTime = 0;
	private boolean toReport = false;


	private Location parkingPlace = null;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(LOG_TAG, "Service created");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(LOG_TAG, "Service destroyed");
		unregisterReceiver(mReceiver);
	}

	/** Callback when Bluetooth is connected or disconnected */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d("dt-test", "In Bluetooth Broadcast Reciever");
			String action = intent.getAction();
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (!ParkingDetector.bluetoothTarget.equals("") && !device.getName().equals(ParkingDetector.bluetoothTarget))
				//the connected bt device needs to be the car if identified by the user
				return;

			ParkingDetector.lastBluetoothName = device.getName();

			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "bluetooth connected to "+device.getName(), Toast.LENGTH_LONG).show();
				onBluetoothConnectionChange(true);
			}
			else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "bluetooth disconnected to "+device.getName(), Toast.LENGTH_LONG).show();
				onBluetoothConnectionChange(false);
			}
		}
	};


	/**
	 *
	 * @param connected: true if connected false in disconnected
	 */
	private void onBluetoothConnectionChange(boolean connected){
		if(connected) Log.e(LOG_TAG, "status: connected");
		else Log.e(LOG_TAG, "status: disconnected");
		Log.d("dt-test", "in bt change");

		long curTime = System.currentTimeMillis() / 1000;
		if (curTime - lastStatusChangeTime >Constants.STATUS_CHANGE_INTERVAL_THRESHOLD) {
			Log.d("dt-test", "in bt change, passed time check");
			ParkingDetector.pendingBTDetection = null;
			if(connected) parkingStatus = Constants.OUTCOME_UNPARKING;
			else parkingStatus=Constants.OUTCOME_PARKING;

			toReport = true;
			lastStatusChangeTime = curTime;

			//locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
			sendConnectionChangeNotificationToMainActivity(parkingStatus);
		}
	}

	/** Called when the service is started */
	public int onStartCommand(Intent intent, int flags, int startId) {
		// setup a broadcast receiver that monitors the bluetooth connection
		IntentFilter filter_connected = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
		IntentFilter filter_disconnected = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mReceiver, filter_connected);
		registerReceiver(mReceiver, filter_disconnected);

		Log.e(LOG_TAG, "Service started for device. ");
		//writeToMainActivity("Bluetooth connection service started.\n\n");

		//createLocationListener();
		return START_STICKY;
	}

	private void sendConnectionChangeNotificationToMainActivity(int eventCode){
		Log.e(LOG_TAG, "Send out the connection change notice ");
		Intent ackIntent = new Intent(Constants.BLUETOOTH_CONNECTION_UPDATE);
		ackIntent.putExtra(Constants.BLUETOOTH_CON_UPDATE_EVENT_CODE, eventCode);
		LocalBroadcastManager.getInstance(this).sendBroadcast(ackIntent);
	}
}
