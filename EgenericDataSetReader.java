package edu.oregonstate;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ie.machinereading.common.NoPunctuationHeadFinder;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;

/**
 * 
 * @author xie
 *
 */
public class EgenericDataSetReader {
	protected Logger logger;
	
	/** Finds the syntactic head of a syntactic constituent*/
	protected final HeadFinder headFinder = new NoPunctuationHeadFinder();
	
	/** Stanford CoreNLP processor to use for pre-processing*/
	protected StanfordCoreNLP processor;
	
	/** 
	 * Additional processor that implements only syntactic parsing (needed for head detection)
	 * We need this processor to detect heads of predicted entities that can not be matched to an existing constituent
	 * This is created on demand, not necessary
	 */
	protected Annotator parseProcessor;
	
	/** If true, we perform syntactic analysis of the dataset sentences and annotations*/
	protected final boolean preProcessSentences;
	
	/**
	 * If true, sets the head span to match the syntactic head of the extent.
	 * Otherwise, the head span is not modified.
	 * This is enabled for the NFL domain, where head spans are not given.
	 */
	protected final boolean calculateHeadSpan;
	
	/** If true, it regenerates the index spans for all tree nodes (useful for KBP) */
	protected final boolean forceGenerationofIndexSpans;
	
	/** Only around for legacy results */
	protected boolean useNewHeadFinder = true;
	
	public EgenericDataSetReader() {
		this(null, false, false, false);
	}
	
	public EgenericDataSetReader(StanfordCoreNLP processor, boolean preProcessSentences, boolean calculateHeadSpan, boolean forceGenerationIndexSpans) {
		this.logger = Logger.getLogger(EgenericDataSetReader.class.getName());
		this.logger.setLevel(Level.SEVERE);
		
		if (processor != null) setProcessor(processor);
		parseProcessor = null;
		this.preProcessSentences = preProcessSentences;
		this.calculateHeadSpan = calculateHeadSpan;
		this.forceGenerationofIndexSpans = forceGenerationIndexSpans;
	}

	public void setProcessor(StanfordCoreNLP processor) {
		this.processor = processor;
	}
	
	public void setUseNewHeadFinder(boolean useNewHeadFinder) {
		this.useNewHeadFinder = useNewHeadFinder;
	}
	
	public Annotator getParse() {
		if (parseProcessor == null) {
			parseProcessor = StanfordCoreNLP.getExistingAnnotator("parse");
			assert(parseProcessor != null);
		}
		return parseProcessor;
	}
	
	public void setLoggerLevel(Level level) {
		logger.setLevel(level);
	}
	
	public Level getLoggerLevel() {
		return logger.getLevel();
	}	
	
	/**
	 * Converts the tree labels to CoreLabels.
	 * We need this because we store additional info in the CoreLabel, like token span.
	 * @param tree
	 */
	public static void convertToCoreLabels(Tree tree) {
		Label l = tree.label();
		if (! (l instanceof CoreLabel)) {
			CoreLabel cl = new CoreLabel();
			cl.setValue(l.value());
			tree.setLabel(cl);
		}
		
		for (Tree kid : tree.children())
			convertToCoreLabels(kid);
	}
	
	/**
	 * For EECB topic
	 * 
	 * @param files
	 * @param topic
	 * @return
	 * @throws Exception
	 */
	public Annotation read(List<String> files, String topic) throws Exception {
		return null;
	}
	
	/**
	 * For EECB document
	 * 
	 * @param documentIdentifier
	 * @return
	 * @throws Exception
	 */
	public Annotation read(String documentIdentifier) throws Exception {
		return null;
	}
	
}
