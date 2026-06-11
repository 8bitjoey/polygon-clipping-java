package com.bytejoey.polygonclip.e2e;

import com.bytejoey.polygonclip.PolygonClip;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port of upstream test/end-to-end.test.js. The 87 live fixture dirs expand to
 * 170 operation cases; broken-* dirs are skipped exactly like upstream.
 */
class EndToEndTest {

  private static final List<String> ALL_OPS =
      List.of("union", "intersection", "xor", "difference");

  @TestFactory
  Stream<DynamicTest> endToEnd() throws IOException, URISyntaxException {
    Path root =
        Path.of(EndToEndTest.class.getClassLoader().getResource("end-to-end").toURI());
    List<DynamicTest> tests = new ArrayList<>();
    List<Path> dirs;
    try (Stream<Path> s = Files.list(root)) {
      dirs = s.sorted().toList();
    }
    for (Path dir : dirs) {
      String name = dir.getFileName().toString();
      if (name.startsWith(".") || name.startsWith("broken-")) continue;
      List<Geom> args = GeoJson.readArgs(dir.resolve("args.geojson"));
      List<Path> files;
      try (Stream<Path> s = Files.list(dir)) {
        files = s.sorted().toList();
      }
      for (Path file : files) {
        String fn = file.getFileName().toString();
        if (!fn.endsWith(".geojson") || fn.equals("args.geojson")) continue;
        String stem = fn.substring(0, fn.length() - ".geojson".length());
        List<String> ops = stem.equals("all") ? ALL_OPS : List.of(stem);
        for (String op : ops) {
          tests.add(DynamicTest.dynamicTest(name + "/" + op, () -> runCase(op, args, file)));
        }
      }
    }
    return tests.stream();
  }

  private static void runCase(String op, List<Geom> args, Path expectedFile)
      throws IOException {
    double[][][][] expected = GeoJson.readExpected(expectedFile);
    MultiPolygon result =
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> apply(op, args));
    assertTrue(
        Arrays.deepEquals(expected, result.coordinates()),
        () -> "coordinates mismatch for " + expectedFile);
  }

  private static MultiPolygon apply(String op, List<Geom> args) {
    Geom subject = args.get(0);
    Geom[] others = args.subList(1, args.size()).toArray(new Geom[0]);
    return switch (op) {
      case "union" -> PolygonClip.union(subject, others);
      case "intersection" -> PolygonClip.intersection(subject, others);
      case "xor" -> PolygonClip.xor(subject, others);
      case "difference" -> PolygonClip.difference(subject, others);
      default -> throw new IllegalArgumentException("unknown operation: " + op);
    };
  }
}
