package cordova.plugin.parking.detector;
import android.location.Location;
public class BTPendingDetection {
	private Location location;
	private int eventCode;
	public BTPendingDetection(int eventCode, Location location) {
		this.location = location;
		this.eventCode = eventCode;
	}
	public Location location() {
		return location;
	}
	public int eventCode() {
		return eventCode;
	}
}
