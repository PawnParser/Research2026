# PPTX -> Accessible HTML parser

Native Java only (java.util.zip + javax.xml). No Apache POI, no external jars,
so it runs in locked-down lab/institutional environments (proposal Section 3.6).

## Build & run
    javac -d out src/avparser/*.java
    java -cp out avparser.PptxAccessibilityTool input.pptx outputDir

Per slide it prints what it found and writes one self-contained HTML explorer
per reconstructed diagram / B-tree.

## What it handles now

1. STATIC BINARY TREES (ellipse nodes + line edges, e.g. the AVL example slide)
   - One accessible explorer per tree; arrow keys move parent/left/right/root.

2. ANIMATED B-TREES (table node boxes + lines + key motion, e.g. the insert slides)
   - Reads the <p:timing> main sequence, applies each animMotion's relative path
     to get resting positions, then partitions the slide's vertical "filmstrip"
     of snapshots into ordered insertion STATES via line connectivity.
   - Node keys come from <a:tbl> cells merged with keys that slid into the box.
   - Inserted key per step is found by diffing consecutive states.
   - Explorer: Page Down/Up steps through insertions; Up=parent, Down=first child,
     Left/Right=siblings (proposal Section 4.3 multi-child model).

3. RASTER-IMAGE DIAGRAMS (e.g. the flattened AVL balance-factor JPG on slide 1)
   - No shape/text data exists; logged as a WARNING and skipped (per project decision).

## Pipeline / extension points
- SlideParser  : XML -> classified AvShape list; parseSteps() -> motion per click
- TreeBuilder  : geometry -> binary Diagram(s)
- BTreeBuilder : motion + tables + lines -> ordered B-tree State(s)
- HtmlGenerator: Diagram/State(s) -> self-contained accessible HTML

## Known limitations (next steps)
- Key extraction is best-effort on hand-authored slides that overlap/reuse shapes;
  occasional duplicate key can appear where the source itself overlaps shapes.
- Deletion animations (shapes disappearing/restructuring) are not yet handled.
- AVL rotation (animMotion-driven restructuring) needs motion-diff interpretation.
- Richer natural-language step narration (traversal decisions) can be layered on
  top of the state diffs already computed.
