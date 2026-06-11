# polygonclip

Faithful Java port of [mfogel/polygon-clipping](https://github.com/mfogel/polygon-clipping) — boolean operations (union, intersection, difference, xor) on polygons and multipolygons using the Martínez–Rueda–Feito sweep-line algorithm.

## Parity

Passes the entire upstream end-to-end fixture suite (170 operation cases across 87 fixtures) bit-for-bit, plus ~300 ported upstream unit tests. Numeric behavior matches upstream exactly (IEEE-754 doubles ≡ JS numbers; same snapping/robustness stack).

## Requirements

Java 17+, zero runtime dependencies. Maven module under `lib/` (`com.bytejoey:polygonclip`).

## Usage

```java
import com.bytejoey.polygonclip.PolygonClip;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.geom.Polygon;

Polygon subject = new Polygon(new double[][][] {
    {{0, 0}, {10, 0}, {10, 10}, {0, 10}}      // exterior ring (auto-closed)
});
Polygon clip = new Polygon(new double[][][] {
    {{5, 5}, {15, 5}, {15, 15}, {5, 15}}
});

MultiPolygon result = PolygonClip.intersection(subject, clip);
double[][][][] coords = result.coordinates();   // GeoJSON MultiPolygon shape
```

Coordinate shapes mirror GeoJSON (`Polygon` = rings × points × [x, y], first ring exterior; `MultiPolygon` adds the polygon dimension). Operations are n-ary (`union(a, b, c, ...)`). `difference(subject, clips...)` subtracts every clip from the subject. Output rings are closed (last point repeats the first). All methods are thread-safe (per-call context, no shared state — see ConcurrencyTest).

## Building / tests

```
mvn -f lib/pom.xml test
```

Full suite including the upstream e2e fixtures under `lib/src/test/resources/end-to-end/`.

## Performance

Faster than the upstream JS on every measured workload — 2.6–2.9× on intersection-heavy
synthetic inputs, 3.7–3.8× on small real fixtures (JMH vs Node 22). Methodology, numbers,
and the optimization log: `bench/RESULTS.md`.

## Provenance & internals

Ported from [mfogel/polygon-clipping](https://github.com/mfogel/polygon-clipping) v0.15.7.
Deliberate behavior-relevant deviations from the upstream implementation are documented in
`DEVIATIONS.md`; everything else is a verbatim transliteration, down to floating-point
operation order.

Differential debugging harness: `TraceLog`/`TraceRunner` on the Java side and `oracle/`
(an instrumented copy of the upstream source) on the JS side. Both expect a clone of the
upstream repo at `upstream/` (gitignored):

```
git clone https://github.com/mfogel/polygon-clipping upstream
npm --prefix upstream install
node oracle/instrument.mjs && node oracle/run.mjs <fixture> <op> <outFile>
```

The JS side of `bench/bench.mjs` additionally needs `npm --prefix upstream run build`.

## License

MIT — original work © Mike Fogel, Java port © Alexey Starshinov. See `LICENSE`.
