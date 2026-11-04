package avparser;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public class PptxAccessibilityTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java avparser.PptxAccessibilityTool <input.pptx> <outputDir>");
            System.exit(1);
        }
        Path pptx = Paths.get(args[0]);
        Path outDir = Paths.get(args[1]);
        Files.createDirectories(outDir);

        Map<String, byte[]> entries = readZip(pptx);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        SlideParser sp = new SlideParser();
        TreeBuilder tb = new TreeBuilder();
        BTreeBuilder bt = new BTreeBuilder();
        GraphBuilder gb = new GraphBuilder();
        HtmlGenerator hg = new HtmlGenerator();

        List<String> slidePaths = orderedSlidePaths(dbf, entries);

        double[] dim = slideSize(dbf, entries.get("ppt/presentation.xml"));
        double slideW = dim[0], slideH = dim[1];

        int filesWritten = 0;
        int catTree = 0, catGraph = 0, catBtree = 0, catArray = 0, catImage = 0, catNone = 0;
        System.out.println("Parsing " + pptx.getFileName() + " (" + slidePaths.size() + " slides)\n");

        int pos = 0;
        for (String slidePath : slidePaths) {
            int sNum = ++pos;   
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(entries.get(slidePath)));
            Element sld = doc.getDocumentElement();

            List<AvShape> shapes = sp.parse(sld);
            String title = slideTitle(shapes, sNum);
            StringBuilder ctx = new StringBuilder();
            for (AvShape s : shapes)
                if (s.isPlaceholder && !s.name.toLowerCase().startsWith("title") && !s.text.isEmpty())
                    ctx.append(s.text).append(" ");
            String slideText = ctx.toString().trim();
            String tl = title.toLowerCase();
            long nodes  = shapes.stream().filter(s -> s.kind == AvShape.Kind.NODE).count();
            long boxes  = shapes.stream().filter(s -> s.kind == AvShape.Kind.NODEBOX).count();
            boolean hasImage = shapes.stream().anyMatch(s -> "pic".equals(s.geom));

            System.out.printf("Slide %d  \"%s\"%n", sNum, title);

            boolean titleGraph = titleHas(tl, "graph", "digraph", "multigraph", "spanning",
                    "shortest path", "topological", "connectiv", "biconnectiv", "network",
                    "vertex", "vertices", "dijkstra", "weighted", "directed", "adjacen");

            boolean titleTree  = titleHas(tl, "tree", "insert", "rotation", "avl",
                    "splay", "huffman", "red", "black", "heap", "disjoint");
            boolean titleBtree = titleHas(tl, "b-tree", "btree", "b tree");

            boolean hasTreapLabel = shapes.stream()
                .anyMatch(s -> s.kind == AvShape.Kind.KEY && "treap".equalsIgnoreCase(s.text));
            boolean hasTreapFormat = shapes.stream()
                .anyMatch(s -> s.text.matches("[A-Za-z]/\\d+.*")); 
            if (hasTreapLabel || hasTreapFormat) {
                System.out.println("   -> TREAP: informational page generated");
                String body = shapes.stream()
                    .filter(s -> !s.text.isEmpty() && s.text.length() > 2
                        && !s.name.toLowerCase().startsWith("title"))
                    .map(s -> s.text).distinct()
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + " | " + b);
                String html = hg.generateTreapInfo(title, body.isEmpty() ? slideText : body, slideText);
                String fname = String.format("slide%d_treap.html", sNum);
                Files.writeString(outDir.resolve(fname), html);
                System.out.printf("      written -> %s%n%n", fname);
                filesWritten++; catNone++; continue;
            }

            // 1) circles present -> a tree or a graph
            if (nodes >= 1) {
                if ((titleGraph || gb.looksLikeGraph(shapes)) && !titleTree) {
                    List<GraphBuilder.Graph> graphs = gb.build(shapes);
                    graphs.removeIf(g -> g.nodes.size() < 2);
                    if (graphs.isEmpty()) { System.out.println("   -> graph: nothing connected.\n"); catNone++; continue; }
                    System.out.println("   -> GRAPH: " + graphs.size() + " connected component(s)");
                    int gi = 1;
                    for (GraphBuilder.Graph g : graphs) {
                        String pageTitle = title + (graphs.size() > 1 ? " (graph " + gi + ")" : "");
                        Files.writeString(outDir.resolve(String.format("slide%d_graph%d.html", sNum, gi)),
                                hg.generateGraph(pageTitle, g, slideText));
                        System.out.printf("      graph %d: %d nodes, %d edges -> slide%d_graph%d.html%n",
                                gi, g.nodes.size(), g.edgeCount, sNum, gi);
                        filesWritten++; gi++;
                    }
                    catGraph++; System.out.println(); continue;
                }
                List<TreeBuilder.Diagram> diagrams = tb.build(shapes);

                int maxFound = diagrams.stream().mapToInt(d -> d.nodeCount).max().orElse(0);
                int minNodes = (hasImage || maxFound >= 2) ? 2 : 1;
                diagrams.removeIf(d -> d.nodeCount < minNodes);
                if (diagrams.isEmpty()) {
                    if (hasImage) {
                        System.out.println("   WARNING: flattened image, nothing to read. "
                            + "Skipping - the lecturer needs to describe it by hand.\n");
                        catImage++;
                    } else {
                        System.out.println("   -> no tree reconstructed.\n");
                        catNone++;
                    }
                    continue;
                }

                java.util.LinkedHashMap<String, List<TreeBuilder.Diagram>> groups =
                    new java.util.LinkedHashMap<>();
                final double ROW_BAND_PX = 120.0;
                for (TreeBuilder.Diagram d : diagrams) {
                    int band = (int)(d.root.y / ROW_BAND_PX);
                    String sig = band + ":" + d.nodeCount;
                    groups.computeIfAbsent(sig, k -> new ArrayList<>()).add(d);
                }
                System.out.println("   -> TREE: " + diagrams.size() + " diagram(s), "
                    + groups.size() + " group(s)");
                int idx = 1;
                for (Map.Entry<String, List<TreeBuilder.Diagram>> entry : groups.entrySet()) {
                    List<TreeBuilder.Diagram> group = entry.getValue();
                    if (group.size() >= 2) {
                        // step-navigator: same tree, multiple transformation states
                        String pageTitle = title + " (step sequence " + idx + ")";
                        String fname = String.format("slide%d_sequence%d.html", sNum, idx);
                        Files.writeString(outDir.resolve(fname),
                                hg.generateTreeSequence(pageTitle, group, slideText));
                        System.out.printf("      sequence %d: %d steps, labels=[%s] -> %s%n",
                                idx, group.size(), entry.getKey(), fname);
                        filesWritten++; idx++;
                    } else {
                        TreeBuilder.Diagram d = group.get(0);
                        DiagramValidator.Report vr = DiagramValidator.validateTree(d, title);
                        if (vr.action == DiagramValidator.Action.SKIP) {
                            System.out.printf("      diagram %d: SKIPPED — %s%n", idx, vr.reason);
                            idx++; continue;
                        }
                        String pageTitle = title + (diagrams.size() > 1 ? " (diagram " + idx + ")" : "");
                        String validationNote = vr.action == DiagramValidator.Action.ANNOTATE ? vr.note : "";
                        String fname = String.format("slide%d_diagram%d.html", sNum, idx);
                        Files.writeString(outDir.resolve(fname),
                                hg.generate(pageTitle, d, slideText, validationNote));
                        System.out.printf("      diagram %d: root=%s, %d nodes%s -> %s%n",
                                idx, d.root.value, d.nodeCount,
                                vr.action == DiagramValidator.Action.ANNOTATE ? " [annotated]" : "",
                                fname);
                        filesWritten++; idx++;
                    }
                }
                catTree++; System.out.println(); continue;
            }

            // 2) table boxes, no circles -> a B-tree (if the title says so) or an array/hash/sort table
            if (boxes >= 1) {
                if (titleBtree) {
                    List<List<SlideParser.Move>> steps = sp.parseSteps(sld, slideW, slideH);
                    List<BTreeBuilder.State> states = bt.build(shapes, steps);
                    if (states.isEmpty()) { System.out.println("   -> B-tree: nothing reconstructed.\n"); catNone++; continue; }
                    System.out.println("   -> B-TREE: " + states.size() + " insertion state(s)");
                    // validate each state; collect any notes to embed in the page
                    int maxK = states.isEmpty() ? 0 : states.get(0).maxKeys;
                    StringBuilder btreeNotes = new StringBuilder();
                    for (BTreeBuilder.State st : states) {
                        DiagramValidator.Report vr = DiagramValidator.validateBTreeState(st, maxK);
                        if (vr.action == DiagramValidator.Action.ANNOTATE && !vr.note.isEmpty()) {
                            btreeNotes.append(vr.note).append(" ");
                        }
                    }
                    Files.writeString(outDir.resolve(String.format("slide%d_btree.html", sNum)),
                            hg.generateBTree(title, states, slideText,
                                             btreeNotes.toString().trim()));
                    for (BTreeBuilder.State st : states)
                        System.out.printf("      step %d: inserted %s -> [%s]%n",
                                st.index, st.insertedKey == null ? "(initial)" : st.insertedKey, st.root.keysJoined());
                    System.out.println("      written -> slide" + sNum + "_btree.html");
                    filesWritten++; catBtree++; System.out.println(); continue;
                }
                System.out.println("   -> TABLE / ARRAY structure (hash table, array or sort), " + boxes
                        + " table(s). A list/array explorer is the next piece to build; not generated yet.\n");
                catArray++; continue;
            }

            // 3) only a picture
            if (hasImage) {
                System.out.println("   WARNING: flattened image, nothing to read. Skipping - describe by hand.\n");
                catImage++; continue;
            }
            System.out.println("   -> no diagram found.\n");
            catNone++;
        }
        System.out.printf("%nDone. %d HTML file(s) written to %s%n", filesWritten, outDir.toAbsolutePath());
        System.out.printf("Slide categories: %d tree, %d graph, %d B-tree, %d array/hash/sort, %d image, %d none%n",
                catTree, catGraph, catBtree, catArray, catImage, catNone);
    }

    private static boolean titleHas(String tl, String... kws) {
        for (String k : kws) if (tl.contains(k)) return true;
        return false;
    }


    private static String labelSignature(TreeNode n) {
        List<String> labels = new ArrayList<>();
        collectLabels(n, labels);
        java.util.Collections.sort(labels);
        return String.join(",", labels);
    }

    private static void collectLabels(TreeNode n, List<String> out) {
        if (n == null) return;
        String v = n.value == null ? "" : n.value.trim();
        if (!v.isEmpty()) out.add(v);
        collectLabels(n.left, out);
        collectLabels(n.right, out);
    }


    private static List<String> orderedSlidePaths(DocumentBuilderFactory dbf, Map<String, byte[]> entries) {
        List<String> ordered = new ArrayList<>();
        try {
            Document pres = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(entries.get("ppt/presentation.xml")));
            Document rels = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(entries.get("ppt/_rels/presentation.xml.rels")));

            Map<String, String> relTarget = new HashMap<>();
            for (Element rel : Xml.descendants(rels.getDocumentElement(), "Relationship")) {
                String target = rel.getAttribute("Target");
                if (!target.contains("slide") || target.contains("Master") || target.contains("Layout"))
                    continue;
                String path = target.startsWith("/") ? target.substring(1) : normalize("ppt/" + target);
                relTarget.put(rel.getAttribute("Id"), path);
            }

            Element lst = Xml.firstDescendant(pres.getDocumentElement(), "sldIdLst");
            if (lst != null)
                for (Element sldId : Xml.children(lst, "sldId")) {
                    String path = relTarget.get(ridOf(sldId));
                    if (path != null && entries.containsKey(path)) ordered.add(path);
                }
        } catch (Exception ignore) {}

        if (ordered.isEmpty()) {
            for (String name : entries.keySet())
                if (name.matches("ppt/slides/slide\\d+\\.xml")) ordered.add(name);
            ordered.sort(Comparator.comparingInt(PptxAccessibilityTool::slideNum));
        }
        return ordered;
    }

    // the r:id on a <p:sldId> - read it namespace-aware, since that's where the order points
    private static String ridOf(Element sldId) {
        String rNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        String v = sldId.getAttributeNS(rNs, "id");
        return (v == null || v.isEmpty()) ? sldId.getAttribute("r:id") : v;
    }

    private static String normalize(String p) {
        Deque<String> stack = new ArrayDeque<>();
        for (String part : p.split("/")) {
            if (part.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); }
            else if (!part.equals(".") && !part.isEmpty()) stack.addLast(part);
        }
        return String.join("/", stack);
    }

    private static double[] slideSize(DocumentBuilderFactory dbf, byte[] presXml) {
        try {
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(presXml));
            Element sz = Xml.firstDescendant(doc.getDocumentElement(), "sldSz");
            if (sz != null) {
                double w = Long.parseLong(Xml.attr(sz, "cx", "9144000")) / 9525.0;
                double h = Long.parseLong(Xml.attr(sz, "cy", "6858000")) / 9525.0;
                return new double[]{ w, h };
            }
        } catch (Exception ignore) {}
        return new double[]{ 960, 720 };
    }

    private static int slideNum(String path) {
        Matcher m = Pattern.compile("slide(\\d+)\\.xml").matcher(path);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String slideTitle(List<AvShape> shapes, int sNum) {
        for (AvShape s : shapes)
            if (s.name != null && s.name.toLowerCase().startsWith("title") && !s.text.isEmpty())
                return s.text;
        return "Slide " + sNum;
    }

    private static Map<String, byte[]> readZip(Path zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int r;
                while ((r = zis.read(buf)) > 0) bos.write(buf, 0, r);
                out.put(e.getName(), bos.toByteArray());
            }
        }
        return out;
    }
}
