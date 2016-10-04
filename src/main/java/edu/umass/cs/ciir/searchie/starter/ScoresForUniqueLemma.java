package edu.umass.cs.ciir.searchie.starter;

import ciir.jfoley.chai.math.StreamingStats;

/**
 * @author jfoley
 */
public class ScoresForUniqueLemma {
  final String lemma;
  StreamingStats scores = new StreamingStats();
  int numTrue = 0;
  int numTotal = 0;

  public ScoresForUniqueLemma(String lemma) {
    this.lemma = lemma;
  }

  public void score(double score, boolean truth) {
    scores.push(score);
    if (truth) {
      numTrue++;
    }
    numTotal++;
  }

  public double bestScore() {
    return scores.getMax();
  }

  public double fractionTrue() {
    return numTrue / (double) numTotal;
  }
}
