package com.bytejoey.polygonclip.geom;

/**
 * A geometry accepted by the boolean operations: a {@link Polygon} or a {@link MultiPolygon}.
 * Rings need not be explicitly closed on input; output rings are closed.
 */
public sealed interface Geom permits Polygon, MultiPolygon {}
