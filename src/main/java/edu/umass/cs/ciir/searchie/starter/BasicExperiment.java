package edu.umass.cs.ciir.searchie.starter;

import ciir.jfoley.chai.classifier.AUC;
import ciir.jfoley.chai.classifier.BinaryClassifierInfo;
import ciir.jfoley.chai.collections.Pair;
import ciir.jfoley.chai.io.TemporaryDirectory;
import ciir.jfoley.chai.random.ReservoirSampler;
import gnu.trove.map.hash.TObjectFloatHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicExperiment {
  public static String defaultCRFSuiteBinary = "/home/jfoley/bin/crfsuite-0.12/bin/crfsuite";
  public static void main(String[] args) throws IOException {

    // galago's Parameters class gives us argument parsing:
    // defaults: java ... BasicExperiment --class=PER --trainingStart=3
    // other: java ... BasicExperiment --class=LOC --trainingStart=20
    Parameters argp = Parameters.parseArgs(args);
    // parameters looks in args for a key or uses the default value:
    String etype = argp.get("class", "PER");
    int numTraining = argp.get("trainingStart", 3);

    File trainFile = new File(argp.get("train", "data/train.snlpl.all.crfsuite"));
    File testFile = new File(argp.get("input", "data/testb.snlpl.all.crfsuite"));

    // list of sentences, sentences are list of tokens
    List<List<SimpleToken>> fullConllTrain = SimpleToken.loadCRFSuiteInputFormat(trainFile);
    System.out.println("Training data loaded: " + fullConllTrain.size() + " sentences.");
    List<List<SimpleToken>> fullConllTest = SimpleToken.loadCRFSuiteInputFormat(testFile);
    System.out.println("Testing data loaded: " + fullConllTest.size() + " sentences.");

    // split into positive and negative sentences based on whether they have "etype"
    List<List<SimpleToken>> fullPositives = new ArrayList<>();
    List<List<SimpleToken>> fullNegatives = new ArrayList<>();
    for (List<SimpleToken> sent : fullConllTrain) {
      boolean isPositive = false;
      for (SimpleToken token : sent) {
        if (token.getLabel().equals(etype)) {
          isPositive = true;
          break;
        }
      }
      if (isPositive) {
        fullPositives.add(sent);
      } else {
        fullNegatives.add(sent);
      }
    }

    // I didn't find negatives to be helpful in my experiments, so only sample from positives:
    // delete all temporary files we create as we go
    try (TemporaryDirectory tmpdir = new TemporaryDirectory()) {
      // CRFSuite learner class (basically, where do we save tmp files?)
      CRFSuiteLearner learner = new CRFSuiteLearner(tmpdir, argp.get("crfsuite", defaultCRFSuiteBinary));

      // set up a CRFSuite model (lbfgs is typically the best).
      learner.setModel(argp.get("model", "lbfgs"));

      // Randomly pick out a few positives:
      List<List<SimpleToken>> positives = ReservoirSampler.take(numTraining, fullPositives);

      // Train a model and read it in from CRFSuite:
      Parameters info = Parameters.create();
      TObjectFloatHashMap<String> weights = learner.learnFeatureWeights(positives, etype, info);
      System.out.println(info); // print out any debug information

      // Build a classifier object from our weights:
      LinearTokenClassifier tokenClassifier = new LinearTokenClassifier(weights);

      // score all of fullConllTest
      // evaluate and stick our measures into this map
      final Map<String, Double> measures = evaluateModel(tokenClassifier, fullConllTest, etype);

      // Print information about performance.
      System.out.println(etype+"\t"+tokenClassifier.getSize());
      System.out.println("AP: " + measures.get("AP"));
      System.out.println("uAP: " + measures.get("uAP"));
      System.out.println("F1: " + measures.get("F1"));
    }
  }

  public static Map<String, Double> evaluateModel(LinearTokenClassifier model, List<List<SimpleToken>> testData, String etype) {
    // collect correct, prediction score pairs
    List<Pair<Boolean, Double>> rankedPred = new ArrayList<>();
    Map<String, ScoresForUniqueLemma> bestScoreByLemma = new HashMap<>();

    // collect unique tokens in "ScoresForUniqueLemma" as we score
    // collect non-unique in rankedPred (we don't actually need to sort it).
    for (List<SimpleToken> tokens : testData) {
      for (SimpleToken token : tokens) {
        boolean truth = token.truthLabel.equals(etype);
        double score = model.score(token.getFeatures());
        rankedPred.add(Pair.of(truth, score));
        bestScoreByLemma.computeIfAbsent(token.lemma, ScoresForUniqueLemma::new).score(score, truth);
      }
    }

    // rank the TokenInfo classes by the highest scoring one (create our "unique" ranking).
    List<Pair<Boolean, Double>> uniqueRankedPred = new ArrayList<>();
    for (ScoresForUniqueLemma scoresForUniqueLemma : bestScoreByLemma.values()) {
      uniqueRankedPred.add(Pair.of(scoresForUniqueLemma.fractionTrue() > 0, scoresForUniqueLemma.bestScore()));
    }

    // calculate p,r,f1,acc, etc.
    BinaryClassifierInfo uinfo = new BinaryClassifierInfo();
    uinfo.update(uniqueRankedPred, model.getIntercept());

    BinaryClassifierInfo info = new BinaryClassifierInfo();
    info.update(rankedPred, model.getIntercept());

    Map<String, Double> measures = new HashMap<>();

    // regular-measures
    measures.put("AUC", AUC.compute(rankedPred));
    measures.put("P10", AUC.computePrec(rankedPred, 10));
    measures.put("P100", AUC.computePrec(rankedPred, 100));
    measures.put("P1000", AUC.computePrec(rankedPred, 1000));
    measures.put("AP", AUC.computeAP(rankedPred));
    measures.put("P", (double) info.getPositivePrecision());
    measures.put("R", (double) info.getPositiveRecall());
    measures.put("F1", (double) info.getPositiveF1());
    measures.put("Accuracy", (double) info.getAccuracy());
    measures.put("TP", (double) info.numPredTruePositive);
    measures.put("FP", (double) info.getNumFalsePositives());
    measures.put("TN", (double) info.numPredTrueNegative);
    measures.put("FN", (double) info.getNumFalseNegatives());

    // unique-measures:
    measures.put("uAUC", AUC.compute(uniqueRankedPred));
    measures.put("uP10", AUC.computePrec(uniqueRankedPred, 10));
    measures.put("uP100", AUC.computePrec(uniqueRankedPred, 100));
    measures.put("uP1000", AUC.computePrec(uniqueRankedPred, 1000));
    measures.put("uAP", AUC.computeAP(uniqueRankedPred));
    measures.put("uP", (double) uinfo.getPositivePrecision());
    measures.put("uR", (double) uinfo.getPositiveRecall());
    measures.put("uF1", (double) uinfo.getPositiveF1());
    measures.put("uAccuracy", (double) uinfo.getAccuracy());
    measures.put("uTP", (double) uinfo.numPredTruePositive);
    measures.put("uFP", (double) uinfo.getNumFalsePositives());
    measures.put("uTN", (double) uinfo.numPredTrueNegative);
    measures.put("uFN", (double) uinfo.getNumFalseNegatives());
    return measures;
  }

}
