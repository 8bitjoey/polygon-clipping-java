package com.bytejoey.polygonclip.num;

/**
 * Immutable 2D value for the math layer (ports of vector.js/bbox.js point literals).
 * Record equals is bitwise per component — assertion use only; algorithm code compares
 * coordinates with primitive operators.
 */
public record Vec(double x, double y) {}
