package edu.oregonstate.experiment.crosscoreferenceresolution.stochasticgradient;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.Feature;
import edu.oregonstate.general.DoubleOperation;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.search.ISearch;
import edu.oregonstate.util.Command;
import edu.oregonstate.util.EecbConstants;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.CorefScorer.ScoreType;

/**
 * Cross Coreference Resolution with Stochastic Gradient by Average Weight
 * 
 * Experiment Configuration
 * classifier: StochasticGradientConsideringBeam (iteration no: 10)
 * cost function: linear function
 * loss function: pairwise
 * search method: beam search(beam width: 1, search step: 300)
 * clustering method: none
 * debug: true
 * 
 * This experiment is conducted as follows, which are different from the previous experiments:
 * 1. learning rate is dynamic each time
 * 2. the overall weight is not averaged over the total weight. It should be a linear combination of all the pairwise and singleton 
 * weights. So the intermediate results should be stored and average according to different weights
 * For example:
 * 
 * w_{1} = (1 - \eta_{1}) w_{0} + \eta_{1} * \delta_{1}
 * w_{2} = (1 - \eta_{2}) w_{1} + \eta_{2} * \delta_{2}
 * 
 * so those intermediate results: w_{1}, \delta_{1} should be recorded
 * 
 * and average different as follows:
 * 
 * average weight = 1 / T \sum_{T} (1 - \eta_{t}) * w_{t} + 1 / no of violations * \sum_{n} * \detal_{t} 
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class CrossCoreferenceResolutionStochasticGradientGoldAverageWeight extends ExperimentConstructor {

	@Override
	protected void performExperiment() {
		configureExperiment();
		
		// get the parameters
		int iteration = (Integer) getParameter(EecbConstants.CLASSIFIER, "noOfIteration");
		int noOfFeature = (Integer) getParameter(EecbConstants.CLASSIFIER, "noOfFeature");
		double[] initialWeight = new double[noOfFeature];
		double[] totalWeight = new double[noOfFeature];
		int mTotalViolations = 0;
		List<double[]> weights = new ArrayList<double[]>();
		weights.add(initialWeight);
		List<double[]> averageWeights = new ArrayList<double[]>();
		List<double[]> deltas = new ArrayList<double[]>();
		double[] learningRates = DoubleOperation.createDescendingArray(1.0 , iteration);
		
		if (mDebug) {
			/**
			 * given initial weight and document, do search, output an initial weight,
			 * and then do testing
			 */
			for (int i = 0; i < iteration; i++) {
				//TODO
				double learningRate = learningRates[i];
				addParas(EecbConstants.CLASSIFIER, "learningRate", learningRate);
				ResultOutput.writeTextFile(logFile, "The " + i + "th iteration.... with learning rate " + learningRate);
				int mviolations = 0;
				double[] currentWeight = new double[noOfFeature];
				System.arraycopy(weights.get(i), 0, currentWeight, 0, noOfFeature);
				
				
				// training part
				for (int j = 0; j < trainingTopics.length; j++) {
					updateWeight = true;
					String topic = trainingTopics[j];
					ResultOutput.writeTextFile(logFile, "Starting to do training on " + topic);
					Document document = ResultOutput.deserialize(topic, serializedOutput, false);
					
					// before search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail before search");
					printParameters(document, topic);
					
					// configure dynamic file and folder path
					currentExperimentFolder = experimentResultFolder + "/" + topic;
					Command.createDirectory(currentExperimentFolder);
					mscorePath = currentExperimentFolder + "/" + "train-iteration" + (i + 1) + "-" + topic;
					mScoreDetailPath = currentExperimentFolder + "/" + "train-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					
					// use search to update weight
					ISearch mSearchMethod = createSearchMethod((String) getParameter(EecbConstants.SEARCHMETHOD, "model"));
					mSearchMethod.setWeight(currentWeight);
					mSearchMethod.setTotalWeight(totalWeight);
					mSearchMethod.setDocument(document);
					mSearchMethod.trainingSearch();
					currentWeight = mSearchMethod.getWeight();
					totalWeight = mSearchMethod.getTotalWeight();
					mviolations += mSearchMethod.getViolations();
					
					// after search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail after search");
					printParameters(document, topic);
				}
					
				// print weight information
				ResultOutput.writeTextFile(logFile, "weight vector : " + DoubleOperation.printArray(currentWeight));
				ResultOutput.writeTextFile(logFile, "total weight vector : " + DoubleOperation.printArray(totalWeight));
				ResultOutput.writeTextFile(violatedFile, mviolations + "");
				mTotalViolations += mviolations;
				ResultOutput.writeTextFile(logFile, "total violation :" + mTotalViolations);
				double[] weightedPreviousWeight = DoubleOperation.time(weights.get(i), 1 - learningRate);
				double[] weightedDelta = DoubleOperation.minus(currentWeight, weightedPreviousWeight);
				double[] delta = DoubleOperation.divide(weightedDelta, learningRate);
				deltas.add(delta);
				
				// form the average weight
				double[] formerPart = new double[noOfFeature];
				double[] laterPart = new double[noOfFeature];
				for (int k = 0; k <= i; k++ ) {
					double[] weightedWeight = DoubleOperation.time(weights.get(k), 1 - learningRates[k]);
					double[] weighteddelta = DoubleOperation.time(deltas.get(k), learningRates[k]);
					formerPart = DoubleOperation.add(formerPart, weightedWeight);
					laterPart = DoubleOperation.add(laterPart, weighteddelta);
				}
				
				double[] averageFormerPart = DoubleOperation.divide(formerPart, i + 1);
				double[] averageLaterPart = DoubleOperation.divide(laterPart, mTotalViolations);				
				double[] averageWeight = DoubleOperation.add(averageFormerPart, averageLaterPart);
				ResultOutput.writeTextFile(logFile, "average weight vector : " + DoubleOperation.printArray(averageWeight));
				ResultOutput.writeTextFile(logFile, "\n");
				weights.add(currentWeight);
				averageWeights.add(averageWeight);
				
				// training validation part
				for (int j = 0; j < trainingTopics.length; j++) {
					updateWeight = false;
					String topic = trainingTopics[j];
					ResultOutput.writeTextFile(logFile, "Starting to do validation training on " + topic);
					Document document = ResultOutput.deserialize(topic, serializedOutput, false);
					
					// before search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail before search");
					printParameters(document, topic);
					
					// configure dynamic file and folder path
					currentExperimentFolder = experimentResultFolder + "/" + topic;
					Command.createDirectory(currentExperimentFolder);
					mscorePath = currentExperimentFolder + "/" + "validationtrain-iteration" + (i + 1) + "-" + topic;
					mScoreDetailPath = currentExperimentFolder + "/" + "validationtrain-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mMUCScoreDetailPath = currentExperimentFolder + "/MUC-" + "validationtrain-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mBcubedScoreDetailPath = currentExperimentFolder + "/Bcubed-" + "validationtrain-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mCEAFScoreDetailPath = currentExperimentFolder + "/CEAF-" + "validationtrain-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					
					// use search to update weight
					ISearch mSearchMethod = createSearchMethod((String) getParameter(EecbConstants.SEARCHMETHOD, "model"));
					mSearchMethod.setWeight(averageWeight);
					mSearchMethod.setDocument(document);
					mSearchMethod.testingSearch();
					
					// after search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail after search");
					printParameters(document, topic);
				}
				
				// testing part
				for (int j = 0; j < testingTopics.length; j++) {
					updateWeight = false;
					String topic = testingTopics[j];
					ResultOutput.writeTextFile(logFile, "Starting to do testing on " + topic);
					Document document = ResultOutput.deserialize(topic, serializedOutput, false);
					
					// before search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail before search");
					printParameters(document, topic);
					
					// configure dynamic file and folder path
					currentExperimentFolder = experimentResultFolder + "/" + topic;
					Command.createDirectory(currentExperimentFolder);
					mscorePath = currentExperimentFolder + "/" + "test-iteration" + (i + 1) + "-" + topic;
					mScoreDetailPath = currentExperimentFolder + "/Pairwise-" + "test-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mMUCScoreDetailPath = currentExperimentFolder + "/MUC-" + "test-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mBcubedScoreDetailPath = currentExperimentFolder + "/Bcubed-" + "test-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					mCEAFScoreDetailPath = currentExperimentFolder + "/CEAF-" + "test-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					
					// use search to do testing
					ISearch mSearchMethod = createSearchMethod((String) getParameter(EecbConstants.SEARCHMETHOD, "model"));
					mSearchMethod.setWeight(averageWeight);
					mSearchMethod.setDocument(document);
					mSearchMethod.testingSearch();
					
					// after search parameters
					ResultOutput.writeTextFile(logFile, topic +  "'s detail after search");
					printParameters(document, topic);
				}
			}
			
		} else {	
		}
		
		
		calcualateWeightDifference(averageWeights);
		String finalResultPath = experimentResultFolder + "/finalresult";
		Command.createDirectory(finalResultPath);
		printFinalScore(iteration);
		
		// delete serialized objects
		ResultOutput.deleteResult(serializedOutput);
		
		ResultOutput.printTime();
	}
	
	/** 
	 * Experiment Configuration
	 * classifier: StructuredPerceptronConsideringBeam (iteration no: 10)
	 * cost function: linear function
	 * loss function: pairwise
	 * search method: beam search(beam width: 1, search step: 300)
	 * clustering method: none
	 * debug: true
	 * 
	 */
	private void configureExperiment() {
		// configure topics and dataSet
		setDebugMode(true);
		
		//TODO
		boolean debugExperiment = false;
		if (debugExperiment) {
			experimentTopics = debugTopics;
			splitTopics(2);
			corpusPath = "../";
		} else {
			experimentTopics = totalTopics;
			splitTopics(12);
			corpusPath = "/nfs/guille/xfern/users/xie/Experiment/";
		}
		
		// define Dataset
		addParas(EecbConstants.DATASET, "corpusPath", corpusPath + "corpus/EECB1.0/data/");
		addParas(EecbConstants.DATASET, "srlpath", corpusPath + "corpus/tokenoutput/");
		addParas(EecbConstants.DATASET, "sieve", "MarkRole, DiscourseMatch, ExactStringMatch, RelaxedExactStringMatch, PreciseConstructs, StrictHeadMatch1, StrictHeadMatch2, StrictHeadMatch3, StrictHeadMatch4, RelaxedHeadMatch");
		addParas(EecbConstants.DATASET, "annotationPath", corpusPath + "corpus/mentions.txt");
		addParas(EecbConstants.DATASET, "wordnetConfigurationPath", corpusPath + "corpus/file_properties.xml");
		addParas(EecbConstants.DATASET, "wordsimilaritypath", corpusPath + "corpus/sims.lsp");
		addParas(EecbConstants.DATASET, "outputPath", corpusPath + "corpus/TEMPORYRESUT/");
		addParas(EecbConstants.DATASET, "aligned", false);
		
		// define classifier parameter
		addParas(EecbConstants.CLASSIFIER, "noOfIteration", 10);
		addParas(EecbConstants.CLASSIFIER, "noOfFeature", Feature.featuresName.length);
		addParas(EecbConstants.CLASSIFIER, "model", "StochasticGradientConsideringBeam");
		
		// define search parameter
		addParas(EecbConstants.SEARCHMETHOD, "beamWidth", 1);
		addParas(EecbConstants.SEARCHMETHOD, "searchStep", 300);
		addParas(EecbConstants.SEARCHMETHOD, "model", "BeamSearch");
		
		// define cost function parameter
		addParas(EecbConstants.LOSSFUNCTION, "scoreType", ScoreType.Pairwise);
		addParas(EecbConstants.LOSSFUNCTION, "model", "MetricLossFunction");
		
		// define cost function
		addParas(EecbConstants.COSTFUNCTION, "model", "LinearCostFunction");
		
		// configure Word Net and Lin's dictionary
		configureJWordNet();
		configureWordSimilarity();
		
		// get the according configuration parameters
		String classifierLearningModel = (String) getParameter(EecbConstants.CLASSIFIER, "model");
		int classifierNoOfIteration = (Integer) getParameter(EecbConstants.CLASSIFIER, "noOfIteration");
		String searchModel = (String) getParameter(EecbConstants.SEARCHMETHOD, "model");
		int searchWidth = (Integer) getParameter(EecbConstants.SEARCHMETHOD, "beamWidth");
		int searchStep = (Integer) getParameter(EecbConstants.SEARCHMETHOD, "searchStep");
		
		//TODO
		stoppingRate = 2.5;
		
		// create a folder to contain all log information
		String outputPath = (String) getParameter(EecbConstants.DATASET, "outputPath");
		String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");
		experimentResultFolder = outputPath + timeStamp + "-" + classifierLearningModel + "-" + classifierNoOfIteration + "-" + searchModel + "-" + searchWidth + "-" + searchStep + "-" + stoppingRate;
		Command.createDirectory(experimentResultFolder);
		
		// create mention result folder to store the mention serialization object
		mentionResultPath = experimentResultFolder + "/mentionResult";
		Command.createDirectory(mentionResultPath);
		
		/** configure other parameters */
		logFile = experimentResultFolder + "/" + "experimentlog";
		violatedFile = experimentResultFolder + "/violatedFile";
		weightFile = experimentResultFolder + "/weights";
		outputText = false;
		enableNull = false;
		incorporateTopicSRLResult = false;
		incorporateDocumentSRLResult = false;
		
		//TODO
		goldOnly = true;
		stoppingCriterion = false;
		normalizeWeight = false;
		outputFeature = false;
		
		// print configure information
		ResultOutput.printTime();
		ResultOutput.writeTextFile(logFile, "corpus path : " + corpusPath);
		ResultOutput.writeTextFile(logFile, "classification : " + classifierLearningModel + "-" + classifierNoOfIteration);
		ResultOutput.writeTextFile(logFile, "search model :" + searchModel + "-" + searchWidth + "-" + searchStep);
		if (stoppingCriterion) {
			ResultOutput.writeTextFile(logFile, "stopping criterion : " + stoppingRate);
		}
		ResultOutput.writeTextFile(logFile, buildString(Feature.featuresName));
		ResultOutput.writeTextFile(logFile, "experiment topics : " + buildString(experimentTopics));

		/** create document serialization folder which store document serialization object */
		serializedOutput = experimentResultFolder + "/documentobject";
		Command.createDirectory(serializedOutput);
		
		// define dataset model
		mDatasetMode = createDataSetModel("WithinCross");
		createDataSet();
	}
	
	/**
	 * entry point for the experiment
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		CrossCoreferenceResolutionStochasticGradientGoldAverageWeight ccrs = new CrossCoreferenceResolutionStochasticGradientGoldAverageWeight();
		// perform the experiment
		ccrs.performExperiment();
	}
	
}
