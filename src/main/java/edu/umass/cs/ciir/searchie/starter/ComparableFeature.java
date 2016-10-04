package edu.umass.cs.ciir.searchie.starter;

import javax.annotation.Nonnull;

/**
 * @author jfoley
 */
public class ComparableFeature implements Comparable<ComparableFeature> {
  final float weight;
  final String name;

  public ComparableFeature(float weight, String name) {
    this.weight = weight;
    this.name = name;
  }

  @Override
  public int compareTo(@Nonnull ComparableFeature o) {
    return Float.compare(Math.abs(this.weight), Math.abs(o.weight));
  }
}