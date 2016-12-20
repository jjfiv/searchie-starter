package edu.umass.cs.ciir.searchie.starter;

import ciir.jfoley.chai.io.LinesIterable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author jfoley
 */
public class SimpleToken {
  final String truthLabel;
  final String lemma;
  public final HashSet<String> features;

  public SimpleToken(String truthLabel, String lemma, HashSet<String> features) {
    this.truthLabel = truthLabel;
    this.lemma = lemma;
    this.features = features;
  }

  /** If you've already got CRFSuite files, load it into this simple representation. */
  public static List<List<SimpleToken>> loadCRFSuiteInputFormat(File where) throws IOException {
    List<SimpleToken> tokens = new ArrayList<>();
    try (LinesIterable lines = LinesIterable.fromFile(where)) {
      for (String line : lines) {
        String what = line.trim();
        if (what.isEmpty()) {
          tokens.add(null); // sentence split marker
          continue;
        }
        String[] data = line.split("\t");
        String label = data[0];
        String token = data[1]; // first feature is token name; also keep as feature!
        HashSet<String> features = new HashSet<>(data.length);
        for (int i = 1; i < data.length; i++) {
          features.add(data[i].intern());
        }
        tokens.add(new SimpleToken(label, token, features));
      }
    }

    List<List<SimpleToken>> dataBySentence = new ArrayList<>();
    List<SimpleToken> cur = new ArrayList<>();
    for (SimpleToken token : tokens) {
      if(token == null) {
        if(cur.isEmpty()) {
          dataBySentence.add(cur);
          cur = new ArrayList<>();
        }
      } else {
        cur.add(token);
      }
    }
    if(!cur.isEmpty()) {
      dataBySentence.add(cur);
    }
    return dataBySentence;
  }

  public String getLabel() {
    return truthLabel;
  }

  public Set<String> getFeatures() {
    return features;
  }
}