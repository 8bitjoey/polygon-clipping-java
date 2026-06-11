package com.bytejoey.polygonclip.e2e;

import com.bytejoey.polygonclip.PolygonClip;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Smoke-tests that concurrent invocations of PolygonClip produce bit-identical
 * results — verifying the zero-shared-mutable-state design claim.
 */
class ConcurrencyTest {

  /** Describes a single test case: fixture dir, op name, and expected result. */
  private record Case(
      String label,
      List<Geom> args,
      String op,
      double[][][][] expected) {}

  @Test
  void parallelOpsProduceIdenticalResults() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
      List<Case> cases = buildCases();

      // Compute baselines on the main thread first.
      double[][][][][] baselines = new double[cases.size()][][][][];
      for (int i = 0; i < cases.size(); i++) {
        Case c = cases.get(i);
        baselines[i] = apply(c.op(), c.args()).coordinates();
      }

      // Assert baselines equal the fixture expectations (windmill cases included).
      for (int i = 0; i < cases.size(); i++) {
        Case c = cases.get(i);
        assertTrue(
            Arrays.deepEquals(c.expected(), baselines[i]),
            "baseline mismatch for " + c.label());
      }

      // 400 tasks across 8 threads: task index % numCases picks the case.
      int numTasks = 400;
      ExecutorService pool = Executors.newFixedThreadPool(8);
      List<Future<String>> futures = new ArrayList<>(numTasks);
      int numCases = cases.size();

      for (int t = 0; t < numTasks; t++) {
        final int idx = t % numCases;
        final double[][][][] baseline = baselines[idx];
        Case c = cases.get(idx);
        futures.add(pool.submit(() -> {
          double[][][][] result = apply(c.op(), c.args()).coordinates();
          if (!Arrays.deepEquals(baseline, result)) {
            return "MISMATCH: " + c.label() + " task=" + idx;
          }
          return null;
        }));
      }

      pool.shutdown();

      // Collect results — any exception or mismatch fails the test.
      List<String> mismatches = new ArrayList<>();
      for (Future<String> f : futures) {
        try {
          String msg = f.get();
          if (msg != null) mismatches.add(msg);
        } catch (ExecutionException ex) {
          fail("ExecutionException in concurrent task: " + ex.getCause(), ex.getCause());
        }
      }

      assertTrue(mismatches.isEmpty(),
          "Concurrency mismatches detected:\n" + String.join("\n", mismatches));
    });
  }

  // -----------------------------------------------------------------

  private List<Case> buildCases() throws Exception {
    Path root = fixtureRoot();

    // windmill-4-blades: all 4 ops
    Path windmill = root.resolve("windmill-4-blades");
    List<Geom> windmillArgs = GeoJson.readArgs(windmill.resolve("args.geojson"));
    List<Case> cases = new ArrayList<>();
    for (String op : List.of("union", "intersection", "xor", "difference")) {
      cases.add(new Case(
          "windmill-4-blades/" + op,
          windmillArgs,
          op,
          GeoJson.readExpected(windmill.resolve(op + ".geojson"))));
    }

    // Heavy case 1: island-in-hole-4x / union
    Path islandDir = root.resolve("island-in-hole-4x");
    cases.add(new Case(
        "island-in-hole-4x/union",
        GeoJson.readArgs(islandDir.resolve("args.geojson")),
        "union",
        GeoJson.readExpected(islandDir.resolve("union.geojson"))));

    // Heavy case 2: high-coincidence / union
    Path highDir = root.resolve("high-coincidence");
    cases.add(new Case(
        "high-coincidence/union",
        GeoJson.readArgs(highDir.resolve("args.geojson")),
        "union",
        GeoJson.readExpected(highDir.resolve("union.geojson"))));

    return cases;
  }

  private static Path fixtureRoot() throws URISyntaxException {
    return Path.of(
        ConcurrencyTest.class.getClassLoader().getResource("end-to-end").toURI());
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
