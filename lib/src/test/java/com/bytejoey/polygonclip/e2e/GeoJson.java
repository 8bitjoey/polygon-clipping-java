package com.bytejoey.polygonclip.e2e;

import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.geom.Polygon;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads the upstream GeoJSON fixture files. Test-only. */
final class GeoJson {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GeoJson() {}

  /** args.geojson: FeatureCollection; each feature is a Polygon or MultiPolygon. */
  static List<Geom> readArgs(Path argsFile) throws IOException {
    JsonNode root = MAPPER.readTree(argsFile.toFile());
    List<Geom> args = new ArrayList<>();
    for (JsonNode feature : root.get("features")) {
      JsonNode geometry = feature.get("geometry");
      String type = geometry.get("type").asText();
      JsonNode coords = geometry.get("coordinates");
      switch (type) {
        case "Polygon" -> args.add(new Polygon(readPolygonCoords(coords)));
        case "MultiPolygon" -> args.add(new MultiPolygon(readMultiPolygonCoords(coords)));
        default -> throw new IllegalStateException(
            argsFile + ": unexpected args geometry type " + type);
      }
    }
    return args;
  }

  /** Expected-result file: a single Feature whose geometry is a MultiPolygon. */
  static double[][][][] readExpected(Path expectedFile) throws IOException {
    JsonNode root = MAPPER.readTree(expectedFile.toFile());
    JsonNode geometry = root.get("geometry");
    if (geometry == null || geometry.isNull()) {
      throw new IllegalStateException(expectedFile + ": no geometry");
    }
    String type = geometry.get("type").asText();
    if (!"MultiPolygon".equals(type)) {
      throw new IllegalStateException(
          expectedFile + ": expected MultiPolygon geometry, got " + type);
    }
    return readMultiPolygonCoords(geometry.get("coordinates"));
  }

  private static double[][][][] readMultiPolygonCoords(JsonNode node) {
    double[][][][] polys = new double[node.size()][][][];
    for (int i = 0; i < node.size(); i++) {
      polys[i] = readPolygonCoords(node.get(i));
    }
    return polys;
  }

  private static double[][][] readPolygonCoords(JsonNode node) {
    double[][][] rings = new double[node.size()][][];
    for (int i = 0; i < node.size(); i++) {
      rings[i] = readRing(node.get(i));
    }
    return rings;
  }

  private static double[][] readRing(JsonNode node) {
    double[][] ring = new double[node.size()][];
    for (int i = 0; i < node.size(); i++) {
      JsonNode position = node.get(i);
      ring[i] = new double[] {position.get(0).asDouble(), position.get(1).asDouble()};
    }
    return ring;
  }
}
