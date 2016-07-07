package cordova.plugin.parking.detector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.uic.sandeep.streetsmart.Constants;
import com.uic.sandeep.streetsmart.managers.LogManager;

public class FusionManager {
	Context context;
	public StringBuilder fusionProcessLog;//log it only for parking/unparking activities
	
	private static String LOG_TAG=FusionManager.class.getCanonicalName();
	
	
	SharedPreferences mPrefs;
	boolean logOn;
	boolean logDetectionOn;
	
	public FusionManager(Context ctxt){
		this.context=ctxt;
		fusionProcessLog=new StringBuilder();
		
		/**
  		 * Get the parameter values from the prefereneces
  		 */
		mPrefs=context.getSharedPreferences(Constants.SHARED_PREFERENCES, 0);
		logOn=mPrefs.getBoolean(Constants.LOGGING_ON, false);
		logDetectionOn=mPrefs.getBoolean(Constants.LOGGING_DETECTION_SWITCH, false);
	}
	
	/**
  	 * 
  	 * @param lastIndicators: last value of recent updated indicators  
  	 *          Integer: indicator ID
  	 *ArrayList<Double>: features
  	 * @param updates:
  	 * 		 Integer: indicator ID
  	 * 	     Double:  scalars;
  	 * @param timestamp: in milliseconds 
  	 * @return
  	 */
  	//TODO fusion method
  	public int fuse(HashMap<Integer, ArrayList<Double>> lastVectors, 
  			HashMap<Integer, ArrayList<Double>> updates, long timestamp, int highLevelActivity, LogManager mLogManager){
  		/**
  		 * first update the lastIndicators by first removing stale values 
  		 * and then adding the new values 
  		 */
  		//remove stale indicator values
  		ArrayList<Integer> indicatorIDsToBeRemoved=new ArrayList<Integer>();
  		int idxOfTimestamp=0; //the first field in arraylist represents the timestamp of the vector
  		for(Integer indicatorID: lastVectors.keySet()){
  			if(timestamp-lastVectors.get(indicatorID).get(idxOfTimestamp)>Constants.FUSION_INDICATOR_TIME_INTERVAL)
  				indicatorIDsToBeRemoved.add(indicatorID);
  		}
  		for(Integer indicatorID: indicatorIDsToBeRemoved){
  			lastVectors.remove(indicatorID);
  		}
  		
  		//add new indicator values
  		for(Integer indicatorID: updates.keySet()){
  			ArrayList<Double> values=new ArrayList<Double>();
  			values.add(timestamp*1.0);  			
  			values.addAll(updates.get(indicatorID));
  			lastVectors.put(indicatorID, values);
  		}
  		
  		/**
  		 * calculate the most likely outcome
  		 */
  		
	  		
	  		int[] outcomes={Constants.OUTCOME_NONE, Constants.OUTCOME_PARKING, Constants.OUTCOME_UNPARKING};
	  		
	  		double[] outcomeLikelihood=BayesianFusion(outcomes, lastVectors, highLevelActivity, mLogManager);
  		
	  	return generateOutcomeNotification(outcomeLikelihood);
  	}
  	
  	
  	/**
  	 * 
  	 * @param outcomes
  	 * @param vectorsToBeFused:
  	 *   Integer: IndicatorID:
  	 *   ArrayList<Double>: first number is timestamp, then features
  	 * @return
  	 */
  	public double[] BayesianFusion(int[] outcomes, HashMap<Integer, ArrayList<Double>> vectorsToBeFused, int highLevelActivity, LogManager mLogManager){
  		
  		double[] outcomeLikelihood=new double[outcomes.length]; 
  		double curProb;  		
  		
  		fusionProcessLog.delete(0, fusionProcessLog.length());
  		fusionProcessLog.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())+"\n");
  		  		
  		for(int i=0;i<outcomes.length;i++){
  			int outcome=outcomes[i];
  			curProb=Constants.PRIOR_PROBABILITY.get(outcome);
  			//Log.d(LOG_TAG,"curProb="+curProb);
  			for(Integer indicatorID: vectorsToBeFused.keySet()){
  				Log.e(LOG_TAG,"vector is "+vectorsToBeFused.get(indicatorID));
  				for(int featureIdx=1;featureIdx<vectorsToBeFused.get(indicatorID).size();featureIdx++){
  					HashSet<Integer> notUsedFeatureIdx=Constants.NOT_USED_FEATURES_IDX.get(indicatorID);
  					if(notUsedFeatureIdx!=null&&notUsedFeatureIdx.contains(featureIdx-1)) continue;
  					
  					String identifier=outcome+"-"+indicatorID+"-"+(featureIdx-1);//offset=1 0th is time-stamp
  	  				//Log.e(LOG_TAG, identifier);
  					if(Constants.CONDITIONAL_PROBABILITY.containsKey(identifier)){
  						double featureValue=vectorsToBeFused.get(indicatorID).get(featureIdx);
  						ConditionalProbability cp=Constants.CONDITIONAL_PROBABILITY.get(identifier);
  						
  						double featureCondProb;  						
  						/*double normalizedFeatureValue;  						
  						if(featureValue>=cp.lowerBound&&featureValue<=cp.upperBound){
  							normalizedFeatureValue= ((int)((featureValue-cp.lowerBound)/(cp.upperBound-cp.lowerBound)*1000))/1000.0;
  							featureCondProb=cp.getProb(normalizedFeatureValue );
  							if(featureCondProb==0) featureCondProb=0.009;
  						}else{
  							normalizedFeatureValue=-1;
  							featureCondProb=0.01;
  						}*/
  						double delta=ConditionalProbability.getDelta(cp, highLevelActivity);
  						featureCondProb=cp.getProbNormalDistr(featureValue, delta);
 						curProb*=featureCondProb;
 						
 						String logMsg=identifier+" "+featureValue+" "+featureCondProb+" "+curProb+"\n";
 						if(outcomes.length>2) Log.e(LOG_TAG, logMsg);
 						fusionProcessLog.append(logMsg);
 						//System.out.println(logMsg);
  					}
  				}
  			}
  			outcomeLikelihood[i]=curProb; //precision is 3
  		}  		
  		
  		
  		/**
  		 * normalize the likelihood
  		 */
	  	double sum=0;
	  	for(int i=0;i<outcomeLikelihood.length;i++) sum+=outcomeLikelihood[i];
	  	Log.e(LOG_TAG, sum+"  "+ Arrays.toString(outcomeLikelihood));  	
  		fusionProcessLog.append("Before Normalization: "+Arrays.toString(outcomeLikelihood)+"\n");
	  	
	  	for(int i=0;i<outcomeLikelihood.length;i++) outcomeLikelihood[i]= ((int)((outcomeLikelihood[i]/sum)*1000))/1000.0;
	  	Log.e(LOG_TAG, "normalized outcome likelihoods: "+ Arrays.toString(outcomeLikelihood));  		
	  	fusionProcessLog.append("After Normalization: "+Arrays.toString(outcomeLikelihood)+"\n");
  		
	  	if(logOn&&logDetectionOn&&mLogManager!=null){
				//mLogManager.log(fusionProcessLog.toString(), Constants.LOG_FILE_TYPE[Constants.LOG_TYPE_DETECTION_REPORT]);
		}
  		
  		return outcomeLikelihood;
  	}
	
	
	public int generateOutcomeNotification(double[] outcomeLikelihood){
  		int[] outcomes={Constants.OUTCOME_NONE, Constants.OUTCOME_PARKING, Constants.OUTCOME_UNPARKING};
  		boolean probabilistic=false;
  		
  		/**
 		 * return the outcome proportionally to the likelihood
  		 */
  		if(probabilistic){
  	  		Random rand=new Random();
  	  		double randNum=rand.nextDouble();
  	  		if(randNum<outcomeLikelihood[0]) return outcomes[0];
  	  		else{
  	  			if(randNum>=1-outcomeLikelihood[2]) return outcomes[2];
  	  			else return outcomes[1];
  	  		}
  		}
  		
  		/**
  		 * get the outcome with the largest likelihood
  		 */
  		int ret=outcomes[0];
  		double maxLikelihood=outcomeLikelihood[0];
  		for(int i=1;i<outcomeLikelihood.length;i++){
  			if(outcomeLikelihood[i]>maxLikelihood){
  				maxLikelihood=outcomeLikelihood[i];
  				ret=outcomes[i];
  			}
  		}
  		
  		double detectionThreshold=mPrefs.getFloat(Constants.PREFERENCE_KEY_NOTIFICATION_THRESHOLD, (float)Constants.DEFAULT_DETECTION_THRESHOLD);
  		/* if the outcome is parking/unparking it must exceed certain threshold*/
  		if((ret==Constants.OUTCOME_PARKING&&maxLikelihood<detectionThreshold)
  				&&(ret==Constants.OUTCOME_UNPARKING&&maxLikelihood<detectionThreshold-0.1)){
  			ret=Constants.OUTCOME_NONE; 
  		}
  		return ret;  		
  	}
}
