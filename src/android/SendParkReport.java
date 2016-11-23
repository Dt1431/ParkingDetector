package cordova.plugin.parking.detector;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

/**
 * Created by Sandeep Sasidharan on 2/3/2016.
 */
public class SendParkReport extends AsyncTask<Void, Void, Void> {
    Location loc;
    String time;
    String userId;
    String endpoint;
    int activity;
    String curBT;
    String initiatedBy;
    String version;
    boolean isVerified;
    boolean activityVerified;

    SendParkReport(Location location, int activity, String curBT, boolean isVerified, String userId, String endpoint, String initiatedBy, String version){
        this.loc = location;
        this.time = "";
        this.curBT = curBT;
        this.activity = activity;
        this.isVerified = isVerified;
        this.userId = userId;
        this.endpoint = endpoint;
        this.initiatedBy = initiatedBy;
        this.activityVerified = true;
        this.version = version;
        if(initiatedBy.equals("user")){
            this.activityVerified = false;
            this.isVerified = false;
            this.curBT = "";
        }
    }

    protected void onPreExecute(Void aVoid) {

    }

    protected Void doInBackground(Void... voids) {
        try {
            StringBuilder urlString = new StringBuilder();
            urlString.append("userId=");
            urlString.append(userId);
            urlString.append("&parkLat=");
            urlString.append(loc.getLatitude());
            urlString.append("&parkLng=");
            urlString.append(loc.getLongitude());
            urlString.append("&activity=");
            urlString.append(activity);
            urlString.append("&audioPort=");
            urlString.append(curBT);
            urlString.append("&isVerified=");
            urlString.append(isVerified);
            urlString.append("&os=android");
            urlString.append("&version=");
            urlString.append(version);
            urlString.append("&initiatedBy=");
            urlString.append(initiatedBy);
            urlString.append("&activityVerified=");
            urlString.append(activityVerified);

            URL url = new URL(endpoint);
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