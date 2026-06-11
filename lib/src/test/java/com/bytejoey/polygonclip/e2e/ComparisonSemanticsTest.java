package com.bytejoey.polygonclip.e2e;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins harness comparison semantics: Arrays.equals on double[] uses
 * Double.equals semantics, which matches jest toEqual (Object.is):
 * NaN equals NaN; -0.0 differs from 0.0.
 */
class ComparisonSemanticsTest {

  @Test
  void nanEqualsNan() {
    assertTrue(Arrays.equals(new double[] {Double.NaN}, new double[] {Double.NaN}));
  }

  @Test
  void negativeZeroDiffersFromPositiveZero() {
    assertFalse(Arrays.equals(new double[] {-0.0}, new double[] {0.0}));
  }
}
