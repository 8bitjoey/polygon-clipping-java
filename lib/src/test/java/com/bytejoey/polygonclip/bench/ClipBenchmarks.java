package com.bytejoey.polygonclip.bench;

import com.bytejoey.polygonclip.PolygonClip;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.geom.Polygon;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Methodology matched to bench/bench.mjs (the JS side): avgt, 3x1s warmup,
 * 5x1s measurement, single fork, us/op.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ClipBenchmarks {

  Geom windmillSubject;
  Geom[] windmillOthers;
  Geom gear1kA, gear1kB, gear10kA, gear10kB;

  @Setup
  public void setup() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    // Windmill: load via classpath resource (same URI pattern as EndToEndTest)
    Path windmillArgs = Path.of(
        ClipBenchmarks.class.getClassLoader()
            .getResource("end-to-end/windmill-4-blades/args.geojson")
            .toURI());
    JsonNode fc = mapper.readTree(windmillArgs.toFile());
    JsonNode features = fc.get("features");
    List<Geom> windmillGeoms = new ArrayList<>();
    for (JsonNode feature : features) {
      JsonNode geometry = feature.get("geometry");
      String type = geometry.get("type").asText();
      JsonNode coords = geometry.get("coordinates");
      if ("Polygon".equals(type)) {
        windmillGeoms.add(new Polygon(mapper.treeToValue(coords, double[][][].class)));
      } else if ("MultiPolygon".equals(type)) {
        windmillGeoms.add(
            new com.bytejoey.polygonclip.geom.MultiPolygon(
                mapper.treeToValue(coords, double[][][][].class)));
      }
    }
    windmillSubject = windmillGeoms.get(0);
    windmillOthers = windmillGeoms.subList(1, windmillGeoms.size()).toArray(new Geom[0]);

    // Gear fixtures: resolve relative to lib/ working directory
    Path dataDir = Path.of(System.getProperty("pcj.bench.data", "../bench/data"));
    JsonNode g1 = mapper.readTree(dataDir.resolve("gear-1k.json").toFile());
    gear1kA = new Polygon(mapper.treeToValue(g1.get("a"), double[][][].class));
    gear1kB = new Polygon(mapper.treeToValue(g1.get("b"), double[][][].class));
    JsonNode g10 = mapper.readTree(dataDir.resolve("gear-10k.json").toFile());
    gear10kA = new Polygon(mapper.treeToValue(g10.get("a"), double[][][].class));
    gear10kB = new Polygon(mapper.treeToValue(g10.get("b"), double[][][].class));
  }

  @Benchmark
  public MultiPolygon windmillUnion() {
    return PolygonClip.union(windmillSubject, windmillOthers);
  }

  @Benchmark
  public MultiPolygon windmillDifference() {
    return PolygonClip.difference(windmillSubject, windmillOthers);
  }

  @Benchmark
  public MultiPolygon gear1kUnion() {
    return PolygonClip.union(gear1kA, gear1kB);
  }

  @Benchmark
  public MultiPolygon gear1kIntersection() {
    return PolygonClip.intersection(gear1kA, gear1kB);
  }

  @Benchmark
  public MultiPolygon gear10kUnion() {
    return PolygonClip.union(gear10kA, gear10kB);
  }
}
