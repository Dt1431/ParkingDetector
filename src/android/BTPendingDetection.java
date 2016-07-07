package cordova.plugin.parking.detector;
import android.location.Location;
public class BTPendingDetection {
	private Location location;
	private int eventCode;
	private long startTime;
	public BTPendingDetection(int eventCode, Location location) {
		this.location = location;
		this.eventCode = eventCode;
		this.startTime = System.currentTimeMillis();
	}
	public Location location() {
		return location;
	}
	public int eventCode() {
		return eventCode;
	}
	public long startTime() {
		return startTime;
	}
	public int timeSince(){
		long currentTime = System.currentTimeMillis();
		return (int)((currentTime - startTime)/1000);
	}
}
