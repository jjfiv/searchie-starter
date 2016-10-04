package edu.umass.cs.ciir.searchie.starter;

import ciir.jfoley.chai.collections.TopKHeap;
import gnu.trove.map.hash.TObjectFloatHashMap;

import java.util.Set;

/**
 * @author jfoley
 */
public class LinearTokenClassifier {
  public final TObjectFloatHashMap<String> featureWeights;

  public LinearTokenClassifier(TObjectFloatHashMap<String> featureWeights) {
    this.featureWeights = featureWeights;
  }

  public double score(Set<String> features) {
    double pred = 0;
    for (String feature : features) {
      pred += featureWeights.get(feature);
    }
    return pred;
  }

  public double getIntercept() {
    return 0;
  }

  public long getSize() {
    return featureWeights.size();
  }

  public LinearTokenClassifier deriveSampled(int k) {
    // limit to however many features...
    TopKHeap<ComparableFeature> features = new TopKHeap<>(Math.min(k, featureWeights.size()));
    featureWeights.forEachEntry((fname, fval) -> {
      features.offer(new ComparableFeature(fval, fname));
      return true;
    });

    TObjectFloatHashMap<String> heaviestFeatures = new TObjectFloatHashMap<>();
    for (ComparableFeature feature : features) {
      heaviestFeatures.put(feature.name, feature.weight);
    }
    return new LinearTokenClassifier(heaviestFeatures);
  }
}