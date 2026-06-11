package com.bytejoey.polygonclip.e2e;

import com.bytejoey.polygonclip.PolygonClip;
import com.bytejoey.polygonclip.TraceLog;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Phase-6 trace harness: mvn -Dtest=TraceRunner -Dpcj.fixture=<dir> -Dpcj.op=<op> -Dpcj.out=<file> */
class TraceRunner {

  @Test
  void trace() throws Exception {
    String fixture = System.getProperty("pcj.fixture");
    assumeTrue(fixture != null, "trace harness is prop-gated");
    String op = System.getProperty("pcj.op");
    String outFile = System.getProperty("pcj.out");

    Path dir =
        Path.of(TraceRunner.class.getClassLoader().getResource("end-to-end").toURI())
            .resolve(fixture);
    List<Geom> args = GeoJson.readArgs(dir.resolve("args.geojson"));
    Geom subject = args.get(0);
    Geom[] others = args.subList(1, args.size()).toArray(new Geom[0]);

    TraceLog.out = new PrintStream(Files.newOutputStream(Path.of(outFile)), true);
    TraceLog.enabled = true;
    try {
      MultiPolygon result =
          switch (op) {
            case "union" -> PolygonClip.union(subject, others);
            case "intersection" -> PolygonClip.intersection(subject, others);
            case "xor" -> PolygonClip.xor(subject, others);
            case "difference" -> PolygonClip.difference(subject, others);
            default -> throw new IllegalArgumentException(op);
          };
      TraceLog.line("RESULT " + Arrays.deepToString(result.coordinates()));
    } catch (RuntimeException e) {
      TraceLog.line("THREW " + e.getMessage());
    } finally {
      TraceLog.enabled = false;
      TraceLog.out.close();
    }
  }
}
