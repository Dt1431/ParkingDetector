package cordova.plugin.parking.detector;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Sandeep Sasidharan on 2/3/2016.
 */
public class SendParkReport extends AsyncTask<Void, Void, Void> {
    LatLng loc;
    String time;
    int activity;
    String curBT;
    boolean isVerified;

    SendParkReport(Location location, int activity, String curBT, boolean isVerified){
        this.loc = new LatLng(location.getLatitude(), location.getLongitude());
        this.time = "";
        this.curBT = curBT;
        this.activity = activity;
        this.isVerified = false;
    }

    SendParkReport(Location location, String curTimeString, int activity){
        this.loc = new LatLng(location.getLatitude(), location.getLongitude());
        this.time = curTimeString;
        this.activity = activity;
        this.curBT = "";
        this.isVerified = false;
    }
    SendParkReport(LatLng location, int activity){
        this.loc = location;
        this.time = "";
        this.curBT = "";
        this.activity = activity;
        this.isVerified = false;
    }

    StringBuffer chaine = new StringBuffer("");

    protected void onPreExecute(Void aVoid) {

    }

    protected Void doInBackground(Void... voids) {
        try {
            StringBuilder urlString = new StringBuilder();
            urlString.append("userId=");
            urlString.append("99999999");
            urlString.append("&userLat=");
            urlString.append(loc.latitude);
            urlString.append("&userLng=");
            urlString.append(loc.longitude);
            urlString.append("&activity=");
            urlString.append(activity);
            urlString.append("&curBT=");
            urlString.append(curBT);
            urlString.append("&isVerified=");
            urlString.append(isVerified);


            URL url = new URL(Constants.SERVER_URL + "/newParkingActivity");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setDoInput(true);
            connection.setDoOutput(true);

            DataOutputStream dStream = new DataOutputStream(connection.getOutputStream());
            dStream.writeBytes(urlString.toString());
            Log.d("dt-test","url sring for parking activity: " + urlString.toString());
            dStream.flush();
            dStream.close();
            int responseCode = connection.getResponseCode();
            Log.d("dt-test","response code for add activity: " + responseCode);
            if(responseCode == 200){
                //Success
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        ///delete above

        return null;
    }

    protected void onPostExecute(Void aVoid) {
        /*MainActivity.text_navigation.setText(chaine);
        new ProbMapAsyncTask(loc).execute();*/
    }

}