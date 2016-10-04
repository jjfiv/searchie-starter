package edu.umass.cs.ciir.searchie.starter;

import ciir.jfoley.chai.Spawn;
import ciir.jfoley.chai.io.IO;
import ciir.jfoley.chai.io.LinesIterable;
import ciir.jfoley.chai.io.TemporaryDirectory;
import ciir.jfoley.chai.string.StrUtil;
import gnu.trove.map.hash.TObjectFloatHashMap;
import org.lemurproject.galago.utility.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * @author jfoley
 */
public class CRFSuiteLearner {
  public final TemporaryDirectory tmpdir;
  private final String crfsuite;
  public String model;
  public boolean quiet = false;

  public CRFSuiteLearner(TemporaryDirectory tmpdir, String crfsuite) {
    this.tmpdir = tmpdir;
    this.crfsuite = crfsuite;
    this.model = "lbfgs";
  }

  public void setModel(String algorithm) {
    this.model = algorithm;
  }

  public TObjectFloatHashMap<String> learnFeatureWeights(List<List<SimpleToken>> sentences, String etype, Parameters cfg) {
    if(sentences.isEmpty()) {
      return new TObjectFloatHashMap<>();
    }

    File forTrain = tmpdir.newOrderedFile(".crfsuite");
    File forModel = tmpdir.newOrderedFile(".model");
    File forEvalErr = tmpdir.newOrderedFile(".err");
    File forEvalOut = tmpdir.newOrderedFile(".out");
    File forTrainOut = tmpdir.newOrderedFile(".out");

    createTrainingFile(etype, sentences, forTrain);

    try {
      // Train a model:
      trainModel(cfg, forTrain, forModel, forEvalErr, forTrainOut);
      TObjectFloatHashMap<String> featureWeights = readWeightsFromModel(etype, cfg, forModel, forEvalErr, forEvalOut);

      // delete temporary files:
      boolean success = forTrain.delete() && forModel.delete() && forEvalErr.delete() && forEvalOut.delete() && forTrainOut.delete();
      if (!success) {
        throw new RuntimeException("Couldn't delete something: " + tmpdir.children());
      }

      // finish up:
      return featureWeights;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void trainModel(Parameters cfg, File forTrain, File forModel, File stderr, File stdout) throws IOException, InterruptedException {
    if(!quiet) System.out.println("\t\tBEGIN TRAIN: " + forTrain + " -> " + stdout + ", " + stderr);
    long startTime = System.currentTimeMillis();
    int rc = Spawn.doProcess(Arrays.asList(crfsuite, "learn",
        "-a", model,
        //"-p", "feature.minfreq="+minFreq,
        "-m", forModel.getAbsolutePath(),
        forTrain.getAbsolutePath()), stdout, stderr);
    if (rc != 0) throw new RuntimeException(IO.slurp(stderr));
    long endTime = System.currentTimeMillis();
    cfg.put("trainingTime", (endTime - startTime) / 1000.0);
  }

  public void createTrainingFile(String etype, List<List<SimpleToken>> sentences, File forTrain) {
    try (PrintWriter out = IO.openPrintWriter(forTrain)) {
      for (List<SimpleToken> sent : sentences) {
        for (SimpleToken token : sent) {
          StringBuilder line = new StringBuilder();
          if(etype == null) {
            line.append(token.getLabel());
          } else {
            if(token.getLabel().equals(etype)) {
              line.append(etype);
            } else {
              line.append("O");
            }
          }
          for (String s : token.getFeatures()) {
            line.append('\t').append(s);
          }
          out.println(line.toString());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public TObjectFloatHashMap<String> readWeightsFromModel(String etype, Parameters cfg, File forModel, File forEvalErr, File forEvalOut) throws IOException, InterruptedException {
    long startTime;
    int rc;
    long endTime;// dump and parse model:
    if(!quiet) System.out.println("\t\tBEGIN DUMP: " + forModel + " -> " + forEvalOut + ", " + forEvalErr);
    startTime = System.currentTimeMillis();
    rc = Spawn.doProcess(Arrays.asList(crfsuite, "dump",
        forModel.getAbsolutePath()), forEvalOut, forEvalErr);

    if (rc != 0) throw new RuntimeException(IO.slurp(forEvalErr));

    final TObjectFloatHashMap<String> featureWeights = parseCRFSuiteModelDump(forEvalOut, etype);
    endTime = System.currentTimeMillis();
    cfg.put("extractTime", (endTime - startTime) / 1000.0);
    return featureWeights;
  }

  /**
   * Real model will be (PER - O); subtracting background type ....
   * @param inputFile model dump file
   * @param etype
   * @return
   * @throws IOException
   */
  public static TObjectFloatHashMap<String> parseCRFSuiteModelDump(File inputFile, String etype) throws IOException {
    final TObjectFloatHashMap<String> positiveWeights = CRFSuiteLearner.parseCRFSuiteModelDumpInner(inputFile, etype);
    final TObjectFloatHashMap<String> negativeWeights = CRFSuiteLearner.parseCRFSuiteModelDumpInner(inputFile, "O");
    if(positiveWeights.getNoEntryValue() != 0) throw new AssertionError("weights.get(missing) should equal 0");

    // flatten model:
    final TObjectFloatHashMap<String> flattenedModel = new TObjectFloatHashMap<>();
    positiveWeights.forEachEntry((ft, val) -> {
      flattenedModel.adjustOrPutValue(ft, val, val);
      return true;
    });
    negativeWeights.forEachEntry((ft, val) -> {
      flattenedModel.adjustOrPutValue(ft, -val, -val);
      return true;
    });

    return flattenedModel;
  }

  private static TObjectFloatHashMap<String> parseCRFSuiteModelDumpInner(File forEvalOut, String etype) throws IOException {
    // Class Numbers:
    //TObjectIntHashMap<String> labels = new TObjectIntHashMap<>();
    // Class Transitions:
    //TObjectFloatHashMap<Pair<String,String>> transitionWeights = new TObjectFloatHashMap<>();
    // Features by String:
    TObjectFloatHashMap<String> featureWeights = new TObjectFloatHashMap<>();

    boolean inLabels = false;
    boolean inAttributes = false;
    boolean inTransitions = false;
    boolean inFeatures = false;

    try (LinesIterable lines = LinesIterable.fromFile(forEvalOut)) {
      for (String line : lines) {
        if (line.startsWith("LABELS")) {
          inLabels = true;
          continue;
        } else if (line.startsWith("ATTRIBUTES")) {
          inAttributes = true;
          continue;
        } else if (line.startsWith("TRANSITIONS")) {
          inTransitions = true;
          continue;
        } else if (line.startsWith("STATE_FEATURES")) {
          inFeatures = true;
          continue;
        }
        if (line.startsWith("}")) {
          inLabels = false;
          inFeatures = false;
          inTransitions = false;
          inAttributes = false;
          continue;
        }

        if (inLabels) {
        /*int spl = line.indexOf(':');
        if(spl < 0) continue;
        int id = Integer.parseInt(line.substring(0,spl).trim());
        String name = line.substring(spl+1).trim().intern();
        labels.put(name, id);*/
        } else if (inAttributes) {
          // CRFSuite internal ids are not needed.
        /*if(line.startsWith("}")) break;
        int spl = line.indexOf(':');
        if(spl < 0) continue;
        int id = Integer.parseInt(line.substring(0,spl).trim());
        String name = line.substring(spl+1).trim().intern();
        attributes.put(name, id);*/
        } else if (inTransitions) {
        /*String mapping = StrUtil.takeAfter(line, "(1)");
        int spl = mapping.indexOf(':');
        if(spl < 0) continue;
        float weight = Float.parseFloat(mapping.substring(spl+1).trim());
        String names = mapping.substring(0,spl).trim().intern();
        int arrow = names.indexOf("-->");
        transitionWeights.put(Pair.of(names.substring(0,arrow).trim(), names.substring(arrow+3).trim()), weight);*/
        } else if (inFeatures) {
          //TODO
          String mapping = StrUtil.takeAfter(line, "(0) ");
          //System.out.println(mapping);
          int spl = mapping.indexOf(':');
          if (spl < 0) continue;
          float weight = Float.parseFloat(mapping.substring(spl + 1).trim());
          String names = mapping.substring(0, spl).trim().intern();
          int arrow = names.indexOf("-->");
          String className = names.substring(arrow + 3).trim();
          String feature = names.substring(0, arrow).trim();
          //System.out.println("\t"+className+":"+feature+"\t"+weight);
          if (!etype.equals(className)) continue;

          featureWeights.put(feature, weight);
          //System.out.println(className+":"+feature+"\t"+weight);
        }
      }
    }

    //System.out.println(labels);
    //System.out.println(transitionWeights);
    //System.out.println(featureWeights);
    return featureWeights;
  }
}