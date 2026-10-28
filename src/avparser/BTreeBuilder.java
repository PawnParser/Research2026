package avparser;

import java.util.*;

// Rebuilds the B-tree insertion sequence from an animated slide.
// slides aren't one tree animating in place. 
// each insertion is drawn as its own snapshot a bit lower down the slide, and the new key slides in from the left margin. The node boxes are actual <a:tbl> tables joined by lines. So:
//   - apply the animation motion so each key ends up where it really lands
//   - read a node's keys from the table cells + any keys that slid on top of it
//   - join boxes with lines; each connected group of boxes is one snapshot
//   - order the snapshots top to bottom = the order keys were inserted
public class BTreeBuilder {

    public static class State {
        public BNode root;
        public String insertedKey;   // the key that's new compared to the snapshot before it
        public int index;            // 1-based step number
        public int maxKeys;          // most keys a node can hold (the B-tree order), from the box size
        public boolean split;        // did a node split going into this step?
        public String note = "";     // plain-English explanation of what happened this step
    }

    private List<AvShape> boxes, keys, lines;
    private Map<Integer, double[]> pos = new HashMap<>();   // where each shape ends up, top-left

    public List<State> build(List<AvShape> shapes, List<List<SlideParser.Move>> steps) {
        boxes = new ArrayList<>(); keys = new ArrayList<>(); lines = new ArrayList<>();
        pos.clear();
        for (AvShape s : shapes) {
            pos.put(s.id, new double[]{ s.x, s.y });
            switch (s.kind) {
                case NODEBOX: boxes.add(s); break;
                case KEY:     keys.add(s);  break;
                case EDGE:    lines.add(s); break;
                default: // anything else doesn't matter here
            }
        }

        // move every animated key to its final resting spot
        if (steps != null)
            for (List<SlideParser.Move> step : steps)
                for (SlideParser.Move mv : step) {
                    double[] p = pos.get(mv.spid);
                    if (p != null) { p[0] += mv.dx; p[1] += mv.dy; }
                }
        if (boxes.isEmpty()) return Collections.emptyList();

        // join boxes that a line connects, so each cluster of boxes is one snapshot
        TreeBuilder.DisjointSet ds = new TreeBuilder.DisjointSet(boxes.size());
        List<int[]> conn = new ArrayList<>();
        for (AvShape ln : lines) {
            int[] pair = matchLine(ln);
            if (pair != null) { conn.add(pair); ds.union(pair[0], pair[1]); }
        }
        Map<Integer, List<Integer>> comps = new LinkedHashMap<>();
        for (int i = 0; i < boxes.size(); i++)
            comps.computeIfAbsent(ds.find(i), k -> new ArrayList<>()).add(i);

        List<State> states = new ArrayList<>();
        for (List<Integer> comp : comps.values()) states.add(buildState(comp, conn));
        states.sort(Comparator.comparingDouble(s -> s.root.y));   // top to bottom = insertion order

        // most keys any node can hold = the number of cells in a box (the B-tree order).
        int maxKeys = 0;
        for (AvShape b : boxes)
            if (b.tableCells != null) maxKeys = Math.max(maxKeys, b.tableCells.size());

        // figure out which key is new at each step by comparing it to the step before,
        // and whether a node split (the tree gained nodes) so we can explain why.
        List<String> prev = new ArrayList<>();
        int prevCount = 0;
        for (int i = 0; i < states.size(); i++) {
            State st = states.get(i);
            st.index = i + 1;
            st.maxKeys = maxKeys;
            List<String> cur = st.root.allKeys();
            st.insertedKey = firstNew(prev, cur);
            int curCount = st.root.countNodes();
            st.split = i > 0 && curCount > prevCount;    // more nodes than before = a split happened
            if (st.insertedKey != null && !st.split)
                st.note = "Inserted " + st.insertedKey + ". It fits in the node, which now has room to spare.";
            else if (st.split)
                st.note = "Inserting " + (st.insertedKey == null ? "a key" : st.insertedKey)
                        + " would have given a node more than " + maxKeys
                        + " keys (the maximum), so the node split in two and its middle key moved up to the parent.";
            prev = cur; prevCount = curCount;
        }
        return states;
    }

    private State buildState(List<Integer> comp, List<int[]> conn) {
        Map<Integer, BNode> nodes = new HashMap<>();
        Set<Integer> set = new HashSet<>(comp);
        for (int i : comp) {
            AvShape b = boxes.get(i);
            BNode n = new BNode();
            double[] p = pos.get(b.id);
            n.x = p[0]; n.y = p[1]; n.shapeId = b.id;
            n.keys = nodeKeys(b);
            nodes.put(i, n);
        }
        for (int[] c : conn) {
            if (!set.contains(c[0]) || !set.contains(c[1])) continue;
            BNode a = nodes.get(c[0]), b = nodes.get(c[1]);
            BNode parent = a.y <= b.y ? a : b;          // higher box is the parent
            BNode child  = parent == a ? b : a;
            if (child.parent == null && !isAncestor(child, parent)) {
                child.parent = parent;
                parent.children.add(child);
            }
        }
        for (BNode n : nodes.values())
            n.children.sort(Comparator.comparingDouble(c -> c.x));   // children left to right
        BNode root = null;
        for (BNode n : nodes.values())
            if (n.parent == null && (root == null || n.y < root.y)) root = n;
        State st = new State();
        st.root = root;
        return st;
    }

    // a node's keys: the filled table cells plus any key boxes that slid on top of it,
    // read left to right. the dedupe drops a doubled-up key where the slide overlapped shapes.
    private List<String> nodeKeys(AvShape box) {
        double[] bp = pos.get(box.id);
        double bx = bp[0], by = bp[1], bw = box.w, bh = box.h;
        int ncol = (box.tableCells != null && !box.tableCells.isEmpty()) ? box.tableCells.size() : 4;

        List<double[]> items = new ArrayList<>();   // (x position, index into vals)
        List<String> vals = new ArrayList<>();
        if (box.tableCells != null)
            for (int c = 0; c < box.tableCells.size(); c++) {
                String t = box.tableCells.get(c).trim();
                if (!t.isEmpty()) { items.add(new double[]{ bx + (c + 0.5) * bw / ncol, vals.size() }); vals.add(t); }
            }
        for (AvShape k : keys) {
            double[] kp = pos.get(k.id);
            double kx = kp[0] + k.w / 2.0, ky = kp[1] + k.h / 2.0;
            if (kx >= bx - 6 && kx <= bx + bw + 6 && ky >= by - 14 && ky <= by + bh + 14) {
                items.add(new double[]{ kx, vals.size() }); vals.add(k.text.trim());
            }
        }
        items.sort(Comparator.comparingDouble(a -> a[0]));
        List<String> out = new ArrayList<>();
        double lastX = -1e9; String lastV = null;
        for (double[] it : items) {
            String v = vals.get((int) it[1]);
            if (v.equals(lastV) && it[0] - lastX < bw / ncol * 0.8) continue;
            out.add(v); lastX = it[0]; lastV = v;
        }
        return out;
    }

    // which two boxes does a line join - same end-point idea as the binary case, but
    // measured to the nearest box rectangle since B-tree boxes are wide
    private int[] matchLine(AvShape ln) {
        double[] lp = pos.get(ln.id);
        double lx = lp[0], ly = lp[1];
        double upX = ln.flipH ? lx + ln.w : lx;
        double loX = ln.flipH ? lx : lx + ln.w;
        double upY = ly, loY = ly + ln.h;
        if (ln.flipV) { double t = upX; upX = loX; loX = t; double ty = upY; upY = loY; loY = ty; }

        int up = nearestBox(upX, upY, 40);
        int lo = nearestBox(loX, loY, 40);
        if (up < 0 || lo < 0 || up == lo) return null;
        return new int[]{ up, lo };
    }

    private int nearestBox(double px, double py, double max) {
        int best = -1; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            AvShape b = boxes.get(i);
            double[] p = pos.get(b.id);
            double dx = Math.max(Math.max(p[0] - px, 0), px - (p[0] + b.w));
            double dy = Math.max(Math.max(p[1] - py, 0), py - (p[1] + b.h));
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < bestD) { bestD = d; best = i; }
        }
        return bestD <= max ? best : -1;
    }

    private boolean isAncestor(BNode maybe, BNode node) {
        for (BNode p = node; p != null; p = p.parent) if (p == maybe) return true;
        return false;
    }

    // first key in the new snapshot that wasn't in the previous one (counts duplicates)
    private String firstNew(List<String> prev, List<String> cur) {
        Map<String, Integer> count = new HashMap<>();
        for (String s : prev) count.merge(s, 1, Integer::sum);
        for (String s : cur) {
            Integer c = count.get(s);
            if (c == null || c == 0) return s;
            count.put(s, c - 1);
        }
        return null;
    }
}
