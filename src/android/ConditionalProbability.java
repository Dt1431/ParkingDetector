package cordova.plugin.parking.detector;

import org.apache.commons.math3.distribution.NormalDistribution;

public class ConditionalProbability {
	public int outcomeID;
	public int indicatorID;
	public int featureIdx; //idx of the feature in the features of this indicator
	
	//normal distribution
	public double mean;
	public double std;
	
	
	//histograms 
	public double[] binProbs;
	public double observedMax;
	public double observedMin;
	
	public ConditionalProbability(int outcome, int indicator, int featureIndex){
		indicatorID=indicator;
		outcomeID=outcome;
		featureIdx=featureIndex;
	}
	
	public ConditionalProbability(int outcome, int indicator,int featureIndex, double[] probs, double lowerBound, double upperBound){
		this(outcome, indicator, featureIndex);
		this.binProbs=probs;
		this.observedMax=upperBound;
		this.observedMin=lowerBound;
	}
	
	public ConditionalProbability(int outcome, int indicator,int featureIndex, double mean, double std){
		this(outcome, indicator, featureIndex);
		this.mean=mean;
		this.std=std;	
		//if(std==0) System.out.println(outcome+" "+indicator+" "+featureIndex+" "+mean+" "+std);
	}
	
	public ConditionalProbability(int outcome, int indicator,int featureIndex, double mean, double std
			,double obMin, double obMax){
		this(outcome, indicator, featureIndex);
		this.mean=mean;
		this.std=std;
		this.observedMax=obMax;
		this.observedMin=obMin;
	}
	
	public double getProbHistogram(double normalized){
		if(normalized==1) normalized-=0.001;
		return binProbs[(int)(normalized*binProbs.length)];
	}
	
	public double getProbNormalDistr(double value, double delta){
		if(mean==0&&std==0) return 0.001;
				
		NormalDistribution nd=new NormalDistribution(mean, std);
		
		//double delta=(observedMax-observedMin)/20;
		/*switch(indicatorID){
		case ProcessingForFusion.INDICATOR_BLUETOOTH:
			delta=0.2;
			break;
		case ProcessingForFusion.INDICATOR_CIV:
			delta=5;
			break;
		case ProcessingForFusion.INDICATOR_ENGINE_START:
			delta=10000;
			break;
		}*/
		
		double prob=nd.cumulativeProbability(value+delta)-nd.cumulativeProbability(value-delta);
		if(prob==0) return Math.pow(10, -20);
		return prob;
	}
	
	public static double getDelta(ConditionalProbability cp, int highLevelActivity){
		//getSmallestIntervalBetweenObservedMaxAndMinOfTheSameFeature
		int[] outcomes=null;
		switch (highLevelActivity) {
		case Constants.HIGH_LEVEL_ACTIVITY_UPARKING:
			outcomes=new int[]{Constants.OUTCOME_NONE, Constants.OUTCOME_PARKING, Constants.OUTCOME_UNPARKING};
			break;
		case Constants.HIGH_LEVEL_ACTIVITY_IODOOR:
			outcomes=new int[]{Constants.ENVIRON_INDOOR, Constants.ENVIRON_OUTDOOR};
		default:
			break;
		}
				
		double interval=cp.observedMax-cp.observedMin;
		String identifier=cp.indicatorID+"-"+cp.featureIdx;
		for(int outcome:outcomes){
			if(outcome==cp.outcomeID) continue;
			ConditionalProbability counterpart=
					Constants.CONDITIONAL_PROBABILITY.get(outcome+"-"+cp.indicatorID+"-"+cp.featureIdx);
			interval=Math.min(interval, counterpart.observedMax-counterpart.observedMin);
		}
		//System.out.println(identifier+" "+interval/5);
		//return 0.1;
		return Math.max(interval/20, 0.1);
	}
	
}
