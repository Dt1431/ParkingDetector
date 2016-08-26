package cordova.plugin.parking.detector;

import android.content.Context;
import android.content.res.Resources;

import com.google.android.gms.location.DetectedActivity;

import java.util.HashMap;
import java.util.HashSet;


public final class Constants {
	
	/************************************************
     * Constants Constants
     **************************************************/
		
		/**
		 * Time related
		 */
		// Formats the timestamp in the log
		
		public static final int ONE_SECOND_IN_MILLISECOND=1000;
    	public static final int THREE_SECONDS= ONE_SECOND_IN_MILLISECOND * 3;
		public static final long TEN_SECONDS = ONE_SECOND_IN_MILLISECOND * 10;
		public static final long ONE_MINUTE = TEN_SECONDS * 6;
		public static final long FIVE_MINUTE = ONE_MINUTE * 5;
		public static final long THIRTY_MINUTE = ONE_MINUTE * 30;

		//Only used for testing
		public static String SYSTEM_IP = "http://192.168.0.111:4567/";

		//StreetSmart Server
		public static String SERVER_URL = "http://streetsmartdemo.cloudapp.net";


	/************************************************
     * Parking Application related
     **************************************************/
    	/**
		 * Enums for outcomes
		 */
		public static final int OUTCOME_NONE=100;
		public static final int OUTCOME_PARKING=101;
	    public static final int OUTCOME_UNPARKING=102;

	//Enum for Park Search

	public static final int SEARCH_PARKING = 7000;
	        
	    /**
	     *motion states 
	     */
	    public static final int STATE_UNKOWNN=200;
	    public static final int STATE_WALKING=201;
	    public static final int STATE_JOGGING=202;
	    public static final int STATE_SITTING=203;
	    public static final int STATE_DRIVING=204;
	    public static final int STATE_STANDING=205;
	    public static final int STATE_ON_BUS=206;
	    public static final int STATE_STILL=207;
	    	public static final double ACCL_THRESHOLD_FOR_STILL=0.15;
	    public static final int STATE_UPSTAIRS=208;
	    public static final int STATE_DOWNSTAIRS=209;
	     
	    /**
	     * acoustic events
	     */
	    public static final int EVENT_DOOR_OPEN=300;
	    public static final int EVENT_DOOR_CLOSE=301;
	    public static final int EVENT_DOOR_OPEN_AND_CLOSE=302;
	    public static final int EVENT_ENGINE_ON=303;
	    public static final int EVENT_ENGINE_OFF=304;
	    
	    /**
	     * Enums for environments
	     */
	    public static final int ENVIRON_UNKNOWN=500;
	    public static final int ENVIRON_INDOOR=501;
	    public static final int ENVIRON_OUTDOOR=502;
	    public static final int ENVIRON_SEMI_OUTDOOR=503;
	    
	    /**
	     * Enums for CIV indicator
	     */
	    public static final int CIV_NO_SIGNI_CHANGE=600;
	    public static final int CIV_SIGNI_INCREASE=601;
	    public static final int CIV_SIGNI_DECREASE=602;

	    
	    /**
	     * Enums for sensors
	     */
	    public static final int SENSOR_ACCELEROMETER=900;
	    public static final int SENSOR_MICROPHONE=901;
	    public static final int SENSOR_BLUETOOTH=902;
	    public static final int SENSOR_BAROMETER=903;
	    public static final int SENSOR_GPS=909;
	    
	    /*
	     * Constants for park search and Navigation
	     * ****Added by Sandeep******
	     * */
	    public static final int MY_DATA_CHECK_CODE =0;
	    
	    
	    
	    /**
	     * Enums for indicators
	     */
	    public static final int HIGH_LEVEL_ACTIVITY_UPARKING=9009;
	    public static final int HIGH_LEVEL_ACTIVITY_IODOOR=9008;
	    
	    public static final int INDICATOR_CIV=1001;
	    public static final int INDICATOR_IODOOR=1002;
		public static final int INDICATOR_BLUETOOTH=1003;
		public static final int INDICATOR_ENGINE_START=1004;
		public static final int INDICATOR_MST=1005;
	    public static final int[] INDICATORS={INDICATOR_CIV, INDICATOR_BLUETOOTH,
	    	INDICATOR_IODOOR, INDICATOR_ENGINE_START, INDICATOR_MST};
	    
	    
	    /***
	     * indicators for IO environment
	     */
	    public static final int INDICATOR_LIGHT_DAY=1009;
	    public static final int INDICATOR_LIGHT_NIGHT=1008;
	    public static final int INDICATOR_RSS=1007;
	    public static final int INDICATOR_MAGNETIC=1006;
	    
	    
	   /**
	    *Constants for Combining/Fusion
	    */
	public static final long FUSION_INDICATOR_TIME_INTERVAL=ONE_MINUTE; // in milliseconds 
	//public static HashMap<Integer, List<Double>> EMISSION_LIKELIHOOD = new HashMap<Integer, List<Double>>();
	public static HashMap<Integer, Double> PRIOR_PROBABILITY;
	

	//OutcomeID, IndicatorID: mean, std
	public static HashMap<String, ConditionalProbability> CONDITIONAL_PROBABILITY;
	public static HashMap<Integer, HashSet<Integer>> NOT_USED_FEATURES_IDX;
		
		
	public static double DEFAULT_DETECTION_THRESHOLD=0.8;
	static {
  		/**
  		 * Parking/Unparking likelihoods
  		 */
		PRIOR_PROBABILITY=new HashMap<Integer, Double>();
		PRIOR_PROBABILITY.put(OUTCOME_PARKING, 0.1);
		PRIOR_PROBABILITY.put(OUTCOME_UNPARKING, 0.1);
		PRIOR_PROBABILITY.put(OUTCOME_NONE, 0.8);
		

		/**
		 * Provide a layer of flexiability to select features for some indicator 
		 */
		NOT_USED_FEATURES_IDX=new HashMap<Integer,HashSet<Integer>>();
		for(Integer indicator: INDICATORS) NOT_USED_FEATURES_IDX.put(indicator, new HashSet<Integer>());		
		/**
		 * for CIV vectors
		 */
		NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(0);NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(1);
		NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(3);NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(5);
		NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(6);NOT_USED_FEATURES_IDX.get(INDICATOR_CIV).add(8);
		
		int[] outcomes={OUTCOME_NONE, OUTCOME_PARKING, OUTCOME_UNPARKING};
  		int[] indicators={INDICATOR_CIV, INDICATOR_IODOOR,
  				INDICATOR_BLUETOOTH,INDICATOR_ENGINE_START
  				,INDICATOR_MST};
		
  		double[][][][] observedMinAndMax={
  			//none
  				{ 
  					//CIV
  					{{-6.515 ,6.715 },	{0.000 ,363.417 },	{0.000 ,25.238 },	{0.000 ,134.836 },	{-41.293 ,58.983 },	{-7.297 ,6.715 },	{0.000 ,363.417 },	{0.000 ,25.238 },	{0.000 ,134.836 }}
  					//{{-6.515 ,6.715 },	{-41.293 ,58.983 },	{-7.297 ,6.715 }}
  					//Indoor/Outdoor
  					,{{0.000 ,10403.000 },	{0.000 ,0.000 },	{-1.000 ,1.000 }}
  					//bluetooth
  					,{{-1,1}}
  				//engine_start
  					,{{2.242 ,25.880 },	{0.012 ,0.235 },	{0.000 ,0.015 },	{168.200 ,376.900 },	{0.000 ,0.005 },	{0.013 ,0.188 },	{0.000 ,0.045 },	{5.681 ,41.690 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{10.120 ,146.400 },	{2.645 ,8.421 },	{1.669 ,4.579 },	{1.415 ,3.004 },	{1.431 ,3.755 },	{1.235 ,2.268 },	{1.267 ,2.528 },	{1.069 ,2.658 },	{1.091 ,2.627 },	{1.054 ,1.545 },	{0.950 ,2.506 },	{0.840 ,1.900 },	{0.876 ,1.290 },	{0.062 ,0.187 },	{0.138 ,0.418 },	{0.118 ,0.217 },	{0.070 ,0.205 },	{0.067 ,0.155 },	{0.054 ,0.150 },	{0.052 ,0.104 },	{0.053 ,0.149 },	{0.053 ,0.121 },	{0.000 ,0.000 },	{0.019 ,0.437 },	{6.257 ,21.940 },	{391.100 ,1329.000 },	{38300.000 ,125200.000 },	{6827000.000 ,22640000.000 },	{8.780 ,42.440 },	{0.047 ,0.343 },	{0.000 ,0.010 },	{1382.000 ,1535.000 },	{0.000 ,0.007 },	{0.010 ,0.259 },	{0.000 ,0.891 },	{20.180 ,109.200 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{-186.300 ,-108.000 },	{0.247 ,19.810 },	{-2.461 ,4.957 },	{-3.318 ,1.516 },	{-2.231 ,1.538 },	{-3.273 ,1.030 },	{-1.821 ,0.607 },	{-2.242 ,0.378 },	{-1.328 ,1.360 },	{-1.887 ,0.036 },	{-1.121 ,-0.046 },	{-1.222 ,0.811 },	{-1.427 ,0.129 },	{-0.977 ,-0.728 },	{-0.094 ,0.763 },	{-0.245 ,0.189 },	{0.026 ,0.304 },	{-0.189 ,0.122 },	{-0.099 ,0.142 },	{-0.171 ,0.077 },	{-0.010 ,0.133 },	{-0.123 ,0.051 },	{0.000 ,0.000 },	{0.020 ,0.514 },	{22.330 ,78.060 },	{1267.000 ,4809.000 },	{139700.000 ,372800.000 },	{22710000.000 ,69170000.000 }}
  					
  					//MST //does not matter much
  					//,{{0.000 ,0.800 },	{0.000 ,1.000 },	{0.000 ,0.800 },	{0.000 ,1.000 }}
  					//,{{0.000 ,0.700 },	{0.000 ,1.000 },	{0.000 ,0.700 },	{0.000 ,1.000 }}
  					,{{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 }}
  					
  				},
  				//parking
  				{
  					//CIV
  					{{-0.259 ,0.135 },	{0.000 ,0.414 },	{0.001 ,0.467 },	{0.000 ,0.178 },	{1.005 ,7.324 },	{-0.971 ,5.559 },	{0.449 ,132.937 },	{0.706 ,19.986 },	{0.350 ,95.262 }}
  					//{{-0.259 ,0.135 },	{1.005 ,7.324 },	{-0.971 ,5.559 }}
  					//Indoor/Outdoor
  					,{{0.000 ,10403.000 },	{0.000 ,0.000 },	{-1.000 ,1.000 }}
  				//bluetooth
  					,{{-1,1}}
  				//engine_start
  					,{{19.190 ,46.270 },	{0.160 ,0.267 },	{0.000 ,0.001 },	{167.400 ,312.200 },	{0.000 ,0.001 },	{0.007 ,0.031 },	{0.000 ,0.109 },	{27.080 ,69.420 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{12.260 ,114.600 },	{3.386 ,6.181 },	{2.183 ,3.117 },	{1.839 ,2.839 },	{1.840 ,2.387 },	{1.582 ,2.440 },	{1.627 ,2.323 },	{1.400 ,2.914 },	{1.290 ,2.020 },	{1.239 ,1.624 },	{1.205 ,2.120 },	{1.245 ,2.012 },	{1.027 ,1.972 },	{0.160 ,0.425 },	{0.177 ,0.475 },	{0.155 ,0.264 },	{0.108 ,0.175 },	{0.097 ,0.152 },	{0.090 ,0.197 },	{0.085 ,0.145 },	{0.076 ,0.135 },	{0.070 ,0.134 },	{0.000 ,0.000 },	{0.023 ,0.154 },	{13.340 ,28.690 },	{627.100 ,1311.000 },	{76310.000 ,154100.000 },	{7208000.000 ,16810000.000 },	{35.210 ,74.100 },	{0.250 ,0.472 },	{0.000 ,0.000 },	{1455.000 ,1526.000 },	{0.000 ,0.001 },	{0.006 ,0.023 },	{0.000 ,0.796 },	{91.480 ,152.200 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{-180.400 ,-162.100 },	{-2.617 ,1.613 },	{-0.800 ,2.656 },	{-1.464 ,1.131 },	{-2.109 ,-0.446 },	{-1.363 ,-0.607 },	{-0.387 ,1.456 },	{-1.835 ,0.856 },	{-1.048 ,0.864 },	{-0.971 ,-0.171 },	{-0.985 ,-0.046 },	{-0.577 ,0.988 },	{-0.906 ,0.447 },	{-0.801 ,-0.452 },	{0.017 ,0.261 },	{-0.245 ,-0.028 },	{0.026 ,0.187 },	{-0.175 ,-0.066 },	{-0.020 ,0.198 },	{-0.224 ,0.013 },	{0.027 ,0.200 },	{-0.130 ,-0.027 },	{0.000 ,0.000 },	{0.020 ,0.102 },	{72.040 ,92.680 },	{3836.000 ,4634.000 },	{116600.000 ,242600.000 },	{42020000.000 ,53930000.000 }}
  				
  					//MST
  					//,{{0.200 ,0.700 },	{0.000 ,0.300 },	{0.000 ,0.600 },	{0.000 ,0.800 }}
  					//,{{0.000 ,0.700 },	{0.000 ,0.400 },	{0.000 ,0.300 },	{0.100 ,0.700 }}
  					,{{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 }}
  				},
  				//unparking
  				{
  					//CIV
  					{{-2.371 ,3.029 },	{1.466 ,361.423 },	{0.913 ,18.554 },	{0.300 ,95.261 },	{-10.081 ,-1.037 },	{-0.540 ,0.104 },	{0.000 ,2.901 },	{0.001 ,0.720 },	{0.000 ,0.762 }}
  					//{{-2.371 ,3.029 },	{-10.081 ,-1.037 },	{-0.540 ,0.104 }}
  					//Indoor/Outdoor
  					,{{0.000 ,10403.000 },	{0.000 ,0.000 },	{-1.000 ,1.000 }}
  				//bluetooth
  					,{{-1,1}}
  				//engine_start
  					,{{2.242 ,23.780 },	{0.012 ,0.235 },	{0.000 ,0.015 },	{180.400 ,376.900 },	{0.000 ,0.005 },	{0.013 ,0.188 },	{0.000 ,0.045 },	{5.681 ,41.690 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{23.010 ,146.400 },	{2.645 ,8.421 },	{1.669 ,4.579 },	{1.415 ,3.004 },	{1.431 ,2.487 },	{1.235 ,2.268 },	{1.267 ,1.791 },	{1.069 ,1.801 },	{1.091 ,1.763 },	{1.054 ,1.545 },	{0.950 ,1.458 },	{0.840 ,1.465 },	{0.876 ,1.290 },	{0.062 ,0.187 },	{0.138 ,0.274 },	{0.118 ,0.217 },	{0.070 ,0.181 },	{0.067 ,0.134 },	{0.054 ,0.120 },	{0.052 ,0.104 },	{0.053 ,0.104 },	{0.053 ,0.095 },	{0.000 ,0.000 },	{0.035 ,0.437 },	{6.257 ,21.940 },	{391.100 ,1308.000 },	{38300.000 ,125200.000 },	{6827000.000 ,22640000.000 },	{8.780 ,42.440 },	{0.047 ,0.343 },	{0.000 ,0.010 },	{1382.000 ,1504.000 },	{0.000 ,0.007 },	{0.010 ,0.259 },	{0.000 ,0.891 },	{20.180 ,109.200 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{0.000 ,0.000 },	{-186.300 ,-108.000 },	{0.247 ,19.810 },	{-2.461 ,4.957 },	{-3.318 ,1.516 },	{-2.091 ,1.538 },	{-3.273 ,1.030 },	{-1.821 ,0.607 },	{-1.164 ,0.378 },	{-1.328 ,0.136 },	{-1.887 ,0.036 },	{-1.066 ,-0.046 },	{-1.222 ,0.131 },	{-1.427 ,0.129 },	{-0.977 ,-0.728 },	{-0.094 ,0.763 },	{-0.245 ,0.189 },	{0.026 ,0.304 },	{-0.107 ,0.122 },	{-0.099 ,0.142 },	{-0.171 ,0.077 },	{-0.010 ,0.133 },	{-0.123 ,0.051 },	{0.000 ,0.000 },	{0.028 ,0.514 },	{22.330 ,78.060 },	{1267.000 ,4809.000 },	{139700.000 ,372800.000 },	{22710000.000 ,69170000.000 }}
  					
  					//MST
  					//,{{0.000 ,0.700 },	{0.000 ,0.800 },	{0.100 ,0.700 },	{0.000 ,0.400 }}
  					//,{{0.000 ,0.500 },	{0.100 ,0.700 },	{0.000 ,0.700 },	{0.000 ,0.800 }}
  					,{{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 },	{0.0 ,1.0 }}
  				}		
  		};	
  		
  		
  		double[][][][] meansAndStds={
  			//none
  				{ 
  					//CIV
  					{ {0.042, 1.004},	{7.700, 23.441},	{1.808, 3.116},	{4.387, 13.485},	{0.044, 2.941},	{0.028, 1.045},	{8.012, 24.184},	{1.885, 3.260},	{4.780, 14.264}}
  					//{{0.042, 1.004},	{0.044, 2.941},	{0.028, 1.045}}
  					
  					//Indoor/Outdoor
  					,{{402.161, 1152.593},	{0.020, 0.02},	{-0.801, 0.599}}
  					//bluetooth
  					,{{0, 0.32}}
  					//engine_start
  					,{{9.932, 5.787},	{0.079, 0.049},	{0.004, 0.004},	{288.400, 46.927},	{0.002, 0.001},	{0.069, 0.036},	{0.005, 0.012},	{22.568, 8.697},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{107.190, 29.523},	{4.686, 1.617},	{2.759, 0.783},	{2.181, 0.415},	{1.938, 0.420},	{1.635, 0.190},	{1.519, 0.235},	{1.407, 0.267},	{1.379, 0.258},	{1.275, 0.137},	{1.236, 0.256},	{1.157, 0.178},	{1.092, 0.099},	{0.123, 0.025},	{0.218, 0.053},	{0.164, 0.028},	{0.113, 0.029},	{0.093, 0.020},	{0.083, 0.021},	{0.075, 0.014},	{0.075, 0.018},	{0.069, 0.014},	{0.000, 0.000},	{0.183, 0.099},	{14.768, 3.574},	{1008.126, 229.294},	{81757.353, 20264.732},	{14364970.588, 3746182.473},	{20.412, 8.703},	{0.130, 0.068},	{0.002, 0.002},	{1451.206, 30.078},	{0.001, 0.002},	{0.061, 0.067},	{0.280, 0.369},	{54.560, 22.439},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{-160.474, 22.244},	{8.044, 4.745},	{0.976, 2.182},	{-0.035, 1.071},	{0.304, 0.907},	{-0.199, 0.876},	{-0.255, 0.543},	{-0.580, 0.463},	{-0.477, 0.461},	{-0.713, 0.449},	{-0.574, 0.292},	{-0.641, 0.432},	{-0.706, 0.310},	{-0.907, 0.056},	{0.346, 0.202},	{0.007, 0.114},	{0.115, 0.061},	{0.029, 0.072},	{0.043, 0.052},	{-0.032, 0.048},	{0.038, 0.027},	{-0.012, 0.031},	{0.000, 0.000},	{0.140, 0.140},	{49.585, 15.142},	{3233.471, 1060.064},	{277470.588, 68800.716},	{51232941.176, 13647687.678}}
  					
  					//MST
  					//,{{0.508, 0.154},	{0.088, 0.126},	{0.508, 0.153},	{0.088, 0.126}}//windowsize=2
  					,{{0.432, 0.191},	{0.148, 0.187},	{0.434, 0.190},	{0.147, 0.186}}//windowsize=5
  					//,{{0.416, 0.189},	{0.145, 0.190},	{0.418, 0.189},	{0.145, 0.189}}//windowsize=10
  				},
  				//parking
  				{
  					//CIV
  					{ {0.017, 0.065},	{0.060, 0.080},	{0.068, 0.082},	{0.020, 0.035},	{2.490, 1.502},	{0.941, 1.212},	{21.832, 22.699},	{5.308, 3.361},	{10.549, 16.561}}
  					//{{0.017, 0.065},		{2.490, 1.502},	{0.941, 1.212}}
  					
  					//Indoor/Outdoor
  					,{{2498.867, 2833.061},	{0.01, 0.01},	{0.600, 0.814}}
  					//bluetooth
  					,{{-0.3, 0.48}}
  					//engine_start
  					,{{9.449, 5.133},	{0.077, 0.048},	{0.004, 0.004},	{292.042, 42.494},	{0.002, 0.001},	{0.071, 0.035},	{0.004, 0.012},	{22.140, 8.460},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{110.131, 24.402},	{4.718, 1.631},	{2.789, 0.775},	{2.173, 0.418},	{1.883, 0.275},	{1.640, 0.191},	{1.488, 0.156},	{1.369, 0.152},	{1.341, 0.136},	{1.273, 0.139},	{1.197, 0.125},	{1.135, 0.122},	{1.088, 0.099},	{0.121, 0.024},	{0.212, 0.040},	{0.163, 0.028},	{0.110, 0.024},	{0.091, 0.018},	{0.081, 0.017},	{0.075, 0.014},	{0.072, 0.012},	{0.068, 0.010},	{0.000, 0.000},	{0.188, 0.096},	{14.788, 3.627},	{998.403, 225.619},	{80634.848, 19475.964},	{14256939.394, 3750098.223},	{19.872, 8.241},	{0.128, 0.068},	{0.002, 0.002},	{1448.667, 26.588},	{0.002, 0.002},	{0.063, 0.067},	{0.270, 0.369},	{53.613, 22.087},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{-159.867, 22.301},	{8.147, 4.780},	{1.005, 2.210},	{-0.046, 1.085},	{0.381, 0.801},	{-0.162, 0.863},	{-0.275, 0.539},	{-0.530, 0.363},	{-0.533, 0.332},	{-0.724, 0.452},	{-0.557, 0.280},	{-0.685, 0.353},	{-0.697, 0.311},	{-0.909, 0.055},	{0.343, 0.204},	{0.008, 0.115},	{0.114, 0.062},	{0.036, 0.061},	{0.041, 0.051},	{-0.028, 0.043},	{0.037, 0.027},	{-0.012, 0.032},	{0.000, 0.000},	{0.144, 0.140},	{49.269, 15.263},	{3241.152, 1075.539},	{279406.061, 68921.082},	{51623939.394, 13664539.633}}
  				
  					//MST
  					//,{{0.378, 0.192},	{0.122, 0.139},	{0.300, 0.141},	{0.178, 0.156}}
  					,{{0.514, 0.131},	{0.047, 0.094},	{0.197, 0.163},	{0.344, 0.255}}
  					//,{ {0.474, 0.155},	{0.064, 0.106},	{0.151, 0.102},	{0.418, 0.188}}
  				},
  				//unparking
  				{
  					//CIV
  					{{-0.312, 1.376},	{33.108, 62.395},	{5.522, 3.727},	{13.903, 22.833},	{-3.162, 2.434},	{-0.049, 0.095},	{0.120, 0.449},	{0.066, 0.113},	{0.032, 0.118}}
  					//{{-0.312, 1.376},	{-3.162, 2.434},	{-0.049, 0.095}}
  					
  					//Indoor/Outdoor
  					,{{2498.867, 2833.061},	{0.01, 0.01},	{0.600, 0.814}}
  					//bluetooth
  					,{{0.3, 0.48}}
  					//engine_start
  					,{{28.860, 9.178},	{0.207, 0.039},	{0.000, 0.000},	{225.075, 45.855},	{0.000, 0.000},	{0.015, 0.007},	{0.039, 0.034},	{46.334, 13.615},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{35.736, 40.507},	{4.737, 0.995},	{2.573, 0.354},	{2.297, 0.377},	{2.156, 0.232},	{1.864, 0.262},	{1.964, 0.276},	{2.091, 0.600},	{1.754, 0.304},	{1.469, 0.137},	{1.635, 0.322},	{1.549, 0.249},	{1.241, 0.306},	{0.254, 0.093},	{0.302, 0.114},	{0.198, 0.034},	{0.138, 0.027},	{0.114, 0.018},	{0.130, 0.035},	{0.105, 0.021},	{0.109, 0.021},	{0.097, 0.022},	{0.000, 0.000},	{0.053, 0.043},	{19.295, 5.166},	{1000.838, 264.629},	{105728.750, 28006.173},	{12287625.000, 3238794.482},	{52.424, 11.648},	{0.368, 0.070},	{0.000, 0.000},	{1478.000, 25.049},	{0.000, 0.000},	{0.012, 0.005},	{0.522, 0.327},	{121.373, 18.735},	{0.000, 0.000},	{0.000, 0.000},	{0.000, 0.000},	{-170.638, 6.238},	{-0.527, 1.394},	{0.827, 1.239},	{-0.323, 0.790},	{-1.013, 0.608},	{-0.830, 0.237},	{0.239, 0.565},	{-0.720, 1.071},	{-0.279, 0.545},	{-0.599, 0.261},	{-0.387, 0.283},	{0.227, 0.447},	{-0.458, 0.424},	{-0.656, 0.104},	{0.170, 0.082},	{-0.136, 0.076},	{0.133, 0.055},	{-0.132, 0.040},	{0.094, 0.069},	{-0.118, 0.080},	{0.116, 0.057},	{-0.096, 0.034},	{0.000, 0.000},	{0.040, 0.027},	{80.993, 6.868},	{4253.625, 256.105},	{187212.500, 39050.386},	{46065000.000, 4241627.046}}
  					
  					//MST
  					//,{{0.330, 0.157},	{0.200, 0.183},	{0.330, 0.226},	{0.150, 0.158}}
  					,{{0.230, 0.158},	{0.338, 0.205},	{0.462, 0.172},	{0.084, 0.117}}
  					//{{0.167, 0.123},	{0.397, 0.161},	{0.388, 0.188},	{0.120, 0.203}}
  				}
  			}; 
  		
  		/*double[][][][] histograms={
  				//none
  				{ 
  					//CIV
  					{{0.01,0.05,0.89,0.04,0.01},	{0.98,0.02,0.00,0.00,0.00},	{0.00,0.10,0.90,0.00,0.00},	{0.01,0.03,0.89,0.06,0.01},	{0.97,0.02,0.00,0.00,0.00}}
  					//Indoor/Outdoor
  					,{{0.93,0.05,0.01,0.01,0.00},	{1.00,0.00,0.00,0.00,0.00},	{0.90,0.00,0.00,0.00,0.10}}
  				},
  				//parking
  				{
  					//CIV
  					{{0.02,0.02,0.08,0.64,0.24},	{0.68,0.24,0.06,0.00,0.02},	{0.62,0.18,0.10,0.06,0.04},	{0.28,0.56,0.12,0.00,0.04},	{0.70,0.26,0.02,0.00,0.02}}
  					//Indoor/Outdoor
  					,{{0.50,0.27,0.10,0.10,0.03},	{1.00,0.00,0.00,0.00,0.00},	{0.20,0.00,0.00,0.00,0.80}}
  				},
  				//unparking
  				{
  					//CIV
  					{{0.29,0.26,0.26,0.07,0.12},	{0.95,0.00,0.02,0.00,0.02},	{0.10,0.02,0.02,0.26,0.60},	{0.02,0.00,0.05,0.48,0.45},	{0.98,0.00,0.00,0.00,0.02}}
  					//Indoor/Outdoor
  					,{{0.50,0.27,0.10,0.10,0.03},	{1.00,0.00,0.00,0.00,0.00},	{0.20,0.00,0.00,0.00,0.80}}
  				}  				
  		};
  		 */	
			
	

		CONDITIONAL_PROBABILITY=new HashMap<String, ConditionalProbability>();
		for(int i=0;i<outcomes.length;i++){
			int outcome=outcomes[i];
			for(int j=0;j<indicators.length;j++){
				int indicator=indicators[j];
				for(int k=0;k<meansAndStds[i][j].length;k++){
					int featureIdx=k;
					CONDITIONAL_PROBABILITY.put(outcome+"-"+indicator+"-"+featureIdx, 
						new ConditionalProbability(outcome, indicator, featureIdx, 
						//histograms[i][j][k], upperAndLowerBounds[i][j][k][0], upperAndLowerBounds[i][j][k][1]
						meansAndStds[i][j][k][0], meansAndStds[i][j][k][1]
					));
				}
			}
		}
		
		
		/**
		 * set for environment
		 */
		PRIOR_PROBABILITY.put(ENVIRON_INDOOR, 0.6);
		PRIOR_PROBABILITY.put(ENVIRON_OUTDOOR, 0.4);
		System.out.println(PRIOR_PROBABILITY);
		
		
		outcomes=new int[]{ENVIRON_INDOOR, ENVIRON_OUTDOOR};
  		indicators=new int[]{INDICATOR_LIGHT_DAY,INDICATOR_LIGHT_NIGHT, INDICATOR_RSS,INDICATOR_MAGNETIC};
  		
  		observedMinAndMax=new double[][][][]{
  				//indoor
  				{
  					//light-day
  					{{0, 300}},
  					//light-night
  					{{0, 150}},
  					//RSS
  					{{0, 70}},
  					//magnetic
  					{{0,1}}
  				},  				
  				//outdoor
  				{
  					//light-day
  					{{0, 3000}},
  					//light-night
  					{{0, 30}},
  					//RSS
  					{{30, 100}},
  					//magnetic
  					{{0,1}}
  				}
  		};
  		
  		meansAndStds=new double[][][][]{
  				//indoor
  				{
  					//light-day
  					{{40, 40}},
  					//light-night
  					{{30, 30}},
  					//RSS
  					{{55, 10}},
  					//magnetic
  					{{0, 1}}
  				},  				
  				//outdoor
  				{
  					//light-day
  					{{150, 100}},
  					//light-night
  					{{10, 10}},
  					//RSS
  					{{65, 10}},
  					//magnetic
  					{{0, 1}}
  				}
  		};
  		for(int i=0;i<outcomes.length;i++){
			int outcome=outcomes[i];
			for(int j=0;j<indicators.length;j++){
				int indicator=indicators[j];
				for(int k=0;k<meansAndStds[i][j].length;k++){
					int featureIdx=k;
					CONDITIONAL_PROBABILITY.put(outcome+"-"+indicator+"-"+featureIdx, 
						new ConditionalProbability(outcome, indicator, featureIdx, 
						//histograms[i][j][k], upperAndLowerBounds[i][j][k][0], upperAndLowerBounds[i][j][k][1]
						meansAndStds[i][j][k][0], meansAndStds[i][j][k][1]
					));
				}
			}
		}
		
		System.out.println(CONDITIONAL_PROBABILITY.keySet());
	}
	//public static int NO_OF_BINS=CONDITIONAL_PROBABILITY.get(OUTCOME_NONE+"-"+INDICATOR_CIV+"-"+"0").binProbs.length;	
    
	/************************************************
     * Android APP related
     **************************************************/

		//From android example
		public static final String PACKAGE_NAME = "cordova.plugin.parking.detector";
		public static final String BROADCAST_ACTION = PACKAGE_NAME + ".BROADCAST_ACTION";
		public static final String ACTIVITY_EXTRA = PACKAGE_NAME + ".ACTIVITY_EXTRA";

	    /**
	     * Names of the app
	     */
		public static final String APP_NAME = "UPDetector";
	    public static final String DEFAULT_PACKAGE_NAME="com."+APP_NAME.toLowerCase();
	    public static final String SENSOR_PACKAGE_NAME=DEFAULT_PACKAGE_NAME+".sensors";
	    
	    /************************************************
	     * App paras
	     **************************************************/	
	    public static final boolean IS_DEBUG=false;
	  
	    public static final int MAX_LOG_SIZE = 100000; //GAME mode: about 37.5*60 records per second
	    
	    public static final int DETECTION_INTERVAL_DEFAULT_VALUE=10; //secs, any two parking/unparking activities detection should at least be this apart
	    
	    public static final String PARKING_NOTIFICATION="Parking at";
	    public static final String UNPARKING_NOTIFICATION="Unparking at";
		    /**
		     * Directory settings
		     */
	    
	    
	    // Used to track what type of request is in process
	    public enum REQUEST_TYPE {ADD, REMOVE}


		    /**
		     * Define a request code to send to Google Play services
		     * This code is returned in Activity.onActivityResult
		     */
		    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
		
		    // Intent actions and extras for sending information from the IntentService to the Activity
		    public static final String ACTION_CONNECTION_ERROR =
		            "com.example.android.activityrecognition.ACTION_CONNECTION_ERROR";
		
		    public static final String ACTION_REFRESH_STATUS_LIST =
		                    "com.example.android.activityrecognition.ACTION_REFRESH_STATUS_LIST";
		
		    public static final String CATEGORY_LOCATION_SERVICES =
		            "com.example.android.activityrecognition.CATEGORY_LOCATION_SERVICES";
		
		    public static final String EXTRA_CONNECTION_ERROR_CODE =
		            "com.example.android.activityrecognition.EXTRA_CONNECTION_ERROR_CODE";
		
		    public static final String EXTRA_CONNECTION_ERROR_MESSAGE =
		            "com.example.android.activityrecognition.EXTRA_CONNECTION_ERROR_MESSAGE";
	    
	    /**
	     * shared preference settings
	     */
		public static final String LOGGING_ON="LOG ON";
		public static final String LOGGING_ACCL_RAW_SWITCH="ACCL RAW";
		public static final String LOGGING_ACCL_FEATURES_SWITCH="ACCL FEATURES";
		public static final String LOGGING_ERROR_SWITCH="ERROR REPORT";
		public static final String LOGGING_DETECTION_SWITCH="DETECTION REPORT";
		public static final String LOGGING_ENVIRON_SWITCH="ENVIRON REPORT";
		
		public static final String PREFERENCE_KEY_CIV_CLASSIFIER_ON="USE CLASSIFIER FOR CIV INDICATOR";
		public static final String PREFERENCE_KEY_CIV_DELTA_CONDITIONAL_PROBABILITY="DELTA FOR CONDITIONAL PROBABILITY";

	
		public static final String PREFERENCE_KEY_NOTIFICATION_THRESHOLD="DETECTION THRESHOLD";
		public static final String PREFERENCE_KEY_DETECTION_INTERVAL="DETECTION INTERVAL";
		
	    // Shared Preferences repository name
		public static final String SHARED_PREFERENCES =DEFAULT_PACKAGE_NAME+".SHARED_PREFERENCES";
		
		// the name of car bluetooth 
		public static final String BLUETOOTH_CAR_DEVICE_NAME=DEFAULT_PACKAGE_NAME+".BLUETOOTH_CAR_DEVICE_NAME";
		public static final String NO_BT_DEVICES = DEFAULT_PACKAGE_NAME+".NO_BT_DEVICES"; 
		
		// variables for different log file type
		public static final int LOG_TYPE_ERROR_REPORT=0;
		public static final int LOG_TYPE_ACCEL_RAW=1;
		public static final int LOG_TYPE_ACCEL_FEATURE=2;
		public static final int LOG_TYPE_ENVIRONMENT=3;
		public static final int LOG_TYPE_DETECTION_REPORT=4;
		public static final String[] LOG_FILE_TYPE={
			"ERROR_REPORT",
			"ACCELEROMETER_RAW", 
			"ACCELEROMETER_FEATURE",
			"ENVIRONMENT",
			"DETECTION_REPORTS"};
		
		public static final String[] PREFERENCE_KEY_TYPE={"_FILE_SEQUENCE_NUMBER", "_FILE_NAME"};
	       	    	
		// Keys in the repository for the motion state transition detection
		public static final String KEY_PREVIOUS_ACTIVITY_TYPE =DEFAULT_PACKAGE_NAME+".KEY_PREVIOUS_ACTIVITY_TYPE";
		public static final String KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT=DEFAULT_PACKAGE_NAME+".KEY_PREVIOUS_CONSECUTIVE_ON_FOOT_COUNT";
		
		
		
		
		/*****************************************
		 * Strings for communications between services and activities    
		 ***********************************************/
		public static final String BLUETOOTH_CONNECTION_UPDATE = "BLUETOOTH_CONNECTION_ACK";
		public static final String BLUETOOTH_CON_UPDATE_EVENT_CODE = "EVENT_CODE";
		
		
		/********************************
		 * Google Activity Recognition Related
		 ******************************/
//shared preference
		public static final String PREFERENCE_KEY_GOOGLE_ACTIVITY_UPDATE_INTERVAL="PREFERENCE_KEY_GOOGLE_ACTIVITY_UPDATE_INTERVAL"; 
		public static final int GOOGLE_ACTIVITY_UPDATE_INTERVAL_DEFAULT_VALUE=1; //seconds
		public static final String PREFERENCE_KEY_USE_GOOGLE_ACTIVITY_IN_FUSION="PREFERENCE_KEY_GOOGLE_ACTIVITY_IN_FUSION";
		
		//communicate btw activity service and main activity 
		public static final String GOOGLE_ACTIVITY_RECOGNITION_UPDATE="GOOGLE_ACTIVITY_RECOGNITION_UPDATE";
		public static final String GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE="MOST_LIKELY_ACTIVITY_TYPE";
		public static final String GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_TYPE_INT="MOST_LIKELY_ACTIVITY_TYPE_INT";
		public static final String GOOGLE_ACT_UPDATE_MOST_LIKELY_ACTIVITY_CONFIDENCE="MOST_LIKELY_ACTIVITY_CONFIDENCE";
		public static final String GOOGLE_ACT_UPDATE_ON_FOOT_ACTIVITY_CONFIDENCE="GOOGLE_ACT_UPDATE_ON_FOOT_ACTIVITY_CONFIDENCE";
		public static final String GOOGLE_ACT_UPDATE_IN_VEHICLE_ACTIVITY_CONFIDENCE="GOOGLE_ACT_UPDATE_IN_VEHICLE_ACTIVITY_CONFIDENCE";
		
		//
		public static final int GOOGLE_ACTIVITY_LAST_STATE_NO=27;
		
	
		
		/************************************************
	     * IODetector Related
	     **************************************************/
		public static final double IODETECTOR_WEIGHT_LIGHT=0.4;
		public static final double IODETECTOR_WEIGHT_MAGNET=0.25;
		public static final double IODETECTOR_WEIGHT_CELLULAR=0.35;
		
		public static final String PREFERENCE_KEY_IS_OUTDOOR="SET CURRENT ENVIRONMENT TO BE OUTDOOR";
		
		/************************************************
	     * Classifier Related
	     **************************************************/
		    /************************************************
		     * Classifiers settings
		     **************************************************/		
	    	public static boolean IS_TRAINING_MODE=false; //set to true if need to train a new model 
			
	    	public static final int ACCEL_CHANGE_IN_VAR=SENSOR_ACCELEROMETER*10+1;
	    	public static final int ACCEL_MOTION_STATE=SENSOR_ACCELEROMETER*10+2;
	    	
	    	public static final int[] CLASSIFIER_NAME={ACCEL_CHANGE_IN_VAR, ACCEL_MOTION_STATE,  SENSOR_MICROPHONE};
			public static final String[] CLASSIFIER_FILTER_NAME={"weka.filters.unsupervised.attribute.Remove", "", ""};
			public static final String[] CLASSIFIER_FILTER_OPTION={"-R 1,4,5,9,10", "", ""};			
			public static final String[][] CLASSIFIER_CLASS={
				{"n", "p", "u"}
				,{"Driving", "Walking" , "Sitting" , "Standing", "Still"}
				,{"on", "off", "openclose", "open", "close", "bus"}
			};			
			public static final int[][] CLASSIFIER_EVENT={
				{CIV_NO_SIGNI_CHANGE, CIV_SIGNI_INCREASE, CIV_SIGNI_DECREASE}
				,{STATE_DRIVING, STATE_WALKING, STATE_JOGGING, STATE_UPSTAIRS, STATE_DOWNSTAIRS, STATE_SITTING, STATE_STANDING, STATE_STILL}
				,{EVENT_ENGINE_OFF, EVENT_ENGINE_OFF, EVENT_DOOR_OPEN_AND_CLOSE, EVENT_DOOR_OPEN, EVENT_DOOR_CLOSE, STATE_ON_BUS}
			};
			
		    /************************************************
		     * Accelerometer
		     **************************************************/
			/*
			 * For accelerometer signal
			 */
			public static final int AXIS_NUMBER=4;//triaxies: x, y, z and the total 
			public static final int AXIS_X=0;
			public static final int AXIS_Y=1;
			public static final int AXIS_Z=2;
			public static final int AXIS_AGG=3;
			
	        
			
			public static final int NO_OF_PAST_STATES_STORED=10;
			    
			public static final double ALPHA=0.8; //used to compute linear acceleration
	        
				
			/**
			 * Microphone setting
			 */
			public static final int AUDIO_SAMPLE_DURATION_IN_SECONDS=10;
			    
			
		    /************************************************
		     * Location Settings
		     **************************************************/
			//  the minimum time interval for notifications, in milliseconds. This field is only used as a hint to conserve power, a
			// nd actual time between location updates may be greater or lesser than this value.
			public static final long LOCATION_INTERVAL_MINTIME=ONE_MINUTE*5; 
			// the minimum distance interval for notifications, in meters
			public static final float GPS_INTERVAL_MINDISTANCE=100;
			
			public static int XPS_PERIOD =5000;
			public static int XPS_ACCURACY=20;
	
			/**
			 * Bluetooth Settings
			 */
			public static final long STATUS_CHANGE_INTERVAL_THRESHOLD = 60*2; //seconds
}



