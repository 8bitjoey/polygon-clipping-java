package com.bytejoey.polygonclip;

import java.io.PrintStream;

/**
 * Phase-6 differential-trace hooks. Disabled (no-op beyond the flag check) unless a
 * sink is installed. The mirror instrumentation lives in oracle/ on the upstream JS
 * side; both emit the same line schema. Kept behind the flag after
 * phase 6 — removal is a phase-7 decision.
 */
public final class TraceLog {
  private TraceLog() {}

  public static volatile boolean enabled;
  public static PrintStream out;

  public static String bits(double v) {
    return Long.toHexString(Double.doubleToRawLongBits(v));
  }

  public static void line(String s) {
    out.println(s);
  }
}
