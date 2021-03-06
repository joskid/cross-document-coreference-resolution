package edu.oregonstate.search;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import Jama.Matrix;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.Feature;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.training.Train;

/**
 * 
 * According to the description in section 3.4 of the paper
 * 
 * we use a single linear regression to model cluster merge oeprations 
 * between both verbal and nominal clusters. Intuitively, the liner regression 
 * models the quality of the merge operation, i.e., a score larger than 0.5 indicates that more 
 * than half of the mention pairs introduced by this merge are correct. 
 * <b>NOTE</b>
 * In each iteration, we perform the merge operation that has the highest score. Once two clusters
 * are merged, we regenerate all the mention features to reflect the current clusters.
 * We stop when no merging operation with an overall benefit is found.
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class IterativeResolution {

	protected List<CorefCluster> clusters;
	protected Document mdocument;
	protected Dictionaries mDictionary;
	protected Matrix mModel;
	
	public IterativeResolution(Document document, Matrix model) {
		mdocument = document;
		mDictionary = ExperimentConstructor.mdictionary;
		clusters = new ArrayList<CorefCluster>();
		mModel = model;
		initialize();
	}
	
	/** initialize the clusters */
	protected void initialize() {
		for (Integer key : mdocument.corefClusters.keySet()) {
			CorefCluster cluster = mdocument.corefClusters.get(key);
			clusters.add(cluster);
		}
	}
	
	/*Compare HashMap to get the index with the maximum value*/
	protected String compare_hashMap(Map<String, Double> scores) {
		Collection<Double> c = scores.values();
		Double maxvalue = Collections.max(c);
		String maxIndex = "";
		
		Set<String> scores_set = scores.keySet();
		Iterator<String> scores_it = scores_set.iterator();
		while(scores_it.hasNext()) {
			String id = scores_it.next();
			Double value = scores.get(id);
			if (value == maxvalue) {
				maxIndex = id;
				break;
			}
		}
		return maxIndex;
	}
	
	// according to the how many features not how many value is larger than 0
	protected double calculateScore(Counter<String> features) {
		double sum = 0.0;
		for (int i = 0; i < mModel.getRowDimension(); i++) {
			if (i == 0) {
				sum += mModel.get(i, 0);
			} else {
				sum += features.getCount(Feature.featuresName[i-1]) * mModel.get(i, 0);
			}
		}
		return sum;
	}

	private void fillScore(Map<String, Double> scoreMap) {
		// compute the pair of the entities
		for (int i = 0; i < (clusters.size() - 1); i++) {
			for (int j = 0; j < i; j++) {
				CorefCluster c1 = clusters.get(i);
				CorefCluster c2 = clusters.get(j);
				Mention formerRep = c1.getRepresentativeMention();
				Mention latterRep = c2.getRepresentativeMention();
				if (formerRep.isPronominal() == true || latterRep.isPronominal() == true) continue;
				Counter<String> features = Feature.getFeatures(mdocument, c1, c2, false, mDictionary); // get the feature size
				double value = calculateScore(features);
				scoreMap.put(Integer.toString(i) + "-" + Integer.toString(j), value);
			}
		}
	}
	
	/** return max value of the score map */
	protected double maximumValue(Map<String, Double> scores) {
		Collection<Double> c = scores.values();
		Double maxvalue = Collections.max(c);
		return maxvalue;
	}

	
	/**
	 * iterative entity/event resolution
	 */
	public void merge() {
		Map<String, Double> scoreMap = new HashMap<String, Double>();
		fillScore(scoreMap);
		boolean remainLoop = true;
		while(remainLoop && scoreMap.size() > 0) {
			// generate the training examples
			double maximumValue = maximumValue(scoreMap);
			if (maximumValue <= 0.5) {
				break;
			}
			
			List<String> keys = new ArrayList<String>();
			for (String key : scoreMap.keySet()) {
				keys.add(key);
			}
			for (int i = 0; i < keys.size(); i++) {
				String[] key = keys.get(i).split("-");
				CorefCluster ci = clusters.get(Integer.parseInt(key[0]));
				CorefCluster cj = clusters.get(Integer.parseInt(key[1]));
				Mention formerRep = ci.getRepresentativeMention();
				Mention latterRep = cj.getRepresentativeMention();
				if (formerRep.isPronominal() == true || latterRep.isPronominal() == true) continue;
				Counter<String> features = Feature.getFeatures(mdocument, ci, cj, false, mDictionary); // get the feature
				Set<Mention> toMentions = ci.getCorefMentions();
				Set<Mention> fromMentions = cj.getCorefMentions();
				double correct = 0.0;
				double total = toMentions.size() * fromMentions.size();
				Map<Integer, Mention> goldCorefClusters = mdocument.allGoldMentions;
				for (Mention toMention : toMentions) {
					for (Mention fromMention : fromMentions) {
						if (goldCorefClusters.containsKey(toMention.mentionID) && goldCorefClusters.containsKey(fromMention.mentionID)) {
							if (goldCorefClusters.get(toMention.mentionID).goldCorefClusterID == goldCorefClusters.get(fromMention.mentionID).goldCorefClusterID) {
				    			correct += 1.0;
				    		}
						}
					}
				}
				
				double quality = correct/total;
				String record = ResultOutput.buildString(features, quality);
				ResultOutput.writeTextFilewithoutNewline(Train.currentOutputFileName, record);
			}
			
			String index = compare_hashMap(scoreMap);
			String[] indexs = index.split("-");
			CorefCluster c1 = clusters.get(Integer.parseInt(indexs[0]));
			CorefCluster c2 = clusters.get(Integer.parseInt(indexs[1]));
			ResultOutput.writeTextFile(ExperimentConstructor.logFile, "another merge----" + c1.getClusterID() + "---->" + c2.getClusterID());
			int removeID = c1.getClusterID();
			CorefCluster.mergeClusters(mdocument, c2, c1, mDictionary);
			mdocument.corefClusters.remove(removeID);
			for (Integer id : mdocument.corefClusters.keySet()) {
            	CorefCluster cluster = mdocument.corefClusters.get(id);
            	cluster.regenerateFeature();
            }
			clusters = new ArrayList<CorefCluster>();
			for (Integer key : mdocument.corefClusters.keySet()) {
				CorefCluster cluster = mdocument.corefClusters.get(key);
				clusters.add(cluster);
			}
			scoreMap = new HashMap<String, Double>();
			fillScore(scoreMap);
		}
	}
	
}
