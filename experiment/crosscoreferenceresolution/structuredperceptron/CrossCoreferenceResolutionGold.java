package edu.oregonstate.experiment.crosscoreferenceresolution.structuredperceptron;

import java.util.*;

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
 * do within coreference first, do not use search to guide the within coreference resolution, 
 * combine the within coreference resolution result produced by the Stanford System together,
 * and then do cross corefernce resolution on the combined document, produce the final result
 * 
 * This experiment is configured as follows:
 * addParas(EecbConstants.CLASSIFIER, "model", "StructuredPerceptronConsideringBeam");
 * addParas(EecbConstants.SEARCHMETHOD, "model", "BeamSearch");
 * addParas(EecbConstants.LOSSFUNCTION, "model", "MetricLossFunction");
 * addParas(EecbConstants.LOSSFUNCTION, "scoreType", ScoreType.Pairwise);
 * addParas(EecbConstants.COSTFUNCTION, "model", "LinearCostFunction");
 * 
 * 
 * @author xie
 */
public class CrossCoreferenceResolutionGold extends ExperimentConstructor {

	@Override
	protected void performExperiment() {
		configureExperiment();
		
		// get the parameters
		int iteration = (Integer) getParameter(EecbConstants.CLASSIFIER, "noOfIteration");
		int noOfFeature = (Integer) getParameter(EecbConstants.CLASSIFIER, "noOfFeature");
		double[] weight = new double[noOfFeature];
		double[] totalWeight = new double[noOfFeature];
		int mTotalViolations = 0;
		
		if (mDebug) {
			/**
			 * given initial weight and document, do search, output an initial weight,
			 * and then do testing
			 */
			for (int i = 0; i < iteration; i++) {
				
				ResultOutput.writeTextFile(logFile, "The " + i + "th iteration....");
				int mviolations = 0;
				
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
					mSearchMethod.setWeight(weight);
					mSearchMethod.setTotalWeight(totalWeight);
					mSearchMethod.setDocument(document);
					mSearchMethod.trainingSearch();
					weight = mSearchMethod.getWeight();
					totalWeight = mSearchMethod.getTotalWeight();
					mviolations += mSearchMethod.getViolations();
					
					// after search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail after search");
					printParameters(document, topic);
				}
					
				// print weight information
				ResultOutput.writeTextFile(logFile, "weight vector : " + DoubleOperation.printArray(weight));
				ResultOutput.writeTextFile(logFile, "total weight vector : " + DoubleOperation.printArray(totalWeight));
				ResultOutput.writeTextFile(violatedFile, mviolations + "");
				mTotalViolations += mviolations;
				ResultOutput.writeTextFile(logFile, "total violation :" + mTotalViolations);
				double[] averageWeight = DoubleOperation.divide(totalWeight, mTotalViolations);
				ResultOutput.writeTextFile(logFile, "average weight vector : " + DoubleOperation.printArray(averageWeight));
				ResultOutput.writeTextFile(logFile, "\n");
				
				// training part
				for (int j = 0; j < trainingTopics.length; j++) {
					updateWeight = false;
					String topic = trainingTopics[j];
					ResultOutput.writeTextFile(logFile, "Starting to do training on " + topic);
					Document document = ResultOutput.deserialize(topic, serializedOutput, false);
					
					// before search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail before search");
					printParameters(document, topic);
					
					// configure dynamic file and folder path
					currentExperimentFolder = experimentResultFolder + "/" + topic;
					Command.createDirectory(currentExperimentFolder);
					mscorePath = currentExperimentFolder + "/" + "costtrain-iteration" + (i + 1) + "-" + topic;
					mScoreDetailPath = currentExperimentFolder + "/" + "costtrain-iteration" + (i + 1) + "-" + topic + "-scoredetail";
					
					// use search to update weight
					ISearch mSearchMethod = createSearchMethod((String) getParameter(EecbConstants.SEARCHMETHOD, "model"));
					mSearchMethod.setWeight(averageWeight);
					mSearchMethod.setDocument(document);
 					mSearchMethod.trainingSearch();
					
					// after search parameters
					ResultOutput.writeTextFile(logFile, "topic " + topic + "'s detail after search");
					printParameters(document, topic);
				}
				
				// testing part
				for (int j = 0; j < testingTopics.length; j++) {
					String topic = testingTopics[j];
					ResultOutput.writeTextFile(logFile, "Starting to do check training on " + topic);
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
		
		String finalResultPath = experimentResultFolder + "/finalresult";
		Command.createDirectory(finalResultPath);
		printFinalScore(iteration);
		ResultOutput.printTime();
	}
	
	/** 
	 * Experiment Configuration
	 * classifier: StructuredPerceptronConsideringBeam (iteration no: 10)
	 * cost function: linear function
	 * loss function: pairwise
	 * search method: beam search(beam width: 5, search step: 300)
	 * clustering method: none
	 * debug: true
	 * 
	 */
	private void configureExperiment() {
		// configure topics and dataSet
		setDebugMode(true);
		
		//TODO
		boolean debugExperiment = true;
		if (debugExperiment) {
			experimentTopics = debugTopics;
			splitTopics(2);
			corpusPath = "../";
		} else {
			experimentTopics = totalTopics;
			splitTopics(12);
			corpusPath = "/nfs/guille/xfern/users/xie/Experiment/";
		}
		
		// define dataset 
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
		//TODO
		addParas(EecbConstants.CLASSIFIER, "model", "StructuredPerceptronConsideringBeam");
		
		// define search parameter
		addParas(EecbConstants.SEARCHMETHOD, "beamWidth", 1);
		addParas(EecbConstants.SEARCHMETHOD, "searchStep", 300);
		addParas(EecbConstants.SEARCHMETHOD, "model", "BeamSearch");
		
		// define cost function parameter
		addParas(EecbConstants.LOSSFUNCTION, "scoreType", ScoreType.Pairwise);
		addParas(EecbConstants.LOSSFUNCTION, "model", "MetricLossFunction");
		
		// define cost function
		addParas(EecbConstants.COSTFUNCTION, "model", "LinearCostFunction");
		
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
		outputText = false;
		enableNull = false;
		incorporateTopicSRLResult = false;
		incorporateDocumentSRLResult = false;
		
		//TODO
		goldOnly = true;
		stoppingCriterion = true;
		normalizeWeight = false;
		outputFeature = false;
		
		// print configure information
		ResultOutput.printTime();
		ResultOutput.writeTextFile(logFile, "corpus path : " + corpusPath);
		ResultOutput.writeTextFile(logFile, "classification : " + classifierLearningModel + "-" + classifierNoOfIteration);
		ResultOutput.writeTextFile(logFile, "search model :" + searchModel + "-" + searchWidth + "-" + searchStep);
		ResultOutput.writeTextFile(logFile, "stopping criterion : " + stoppingRate);
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
	 * Experiment Entry Point
	 * 
	 * @param args
	 */
	public static void main(String[] args){
		CrossCoreferenceResolutionGold ccr = new CrossCoreferenceResolutionGold();
		ccr.performExperiment();
	}
	
}
