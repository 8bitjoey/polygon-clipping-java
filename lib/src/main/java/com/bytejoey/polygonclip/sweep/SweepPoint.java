package com.bytejoey.polygonclip.sweep;

import java.util.List;

/**
 * Mutable identity-carrying point of the sweep (port of the JS point literals the rounder
 * produces). Coordinates never change after construction; only linkage state does. The
 * events list is null until the first SweepEvent attaches — null IS the "not yet linked"
 * state upstream reads as {@code point.events === undefined} (segment.js:351).
 */
public final class SweepPoint {
  public final double x;
  public final double y;
  public List<SweepEvent> events;

  public SweepPoint(double x, double y) {
    this.x = x;
    this.y = y;
  }
}
