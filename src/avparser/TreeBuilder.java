package avparser;

import java.util.*;

// Works out the shape of a normal binary tree from just the circles and the lines
// between them - the slide never says "C is G's left child", so we infer it:
//   - each line joins the two circles nearest its ends
//   - circles joined by lines form one tree (separate clusters = separate trees)
//   - the higher circle is the parent, left/right comes from the x positions
public class TreeBuilder {

    public static class Diagram {
        public TreeNode root;
        public int nodeCount;
        public String kind = "Binary tree";
    }

    private final List<AvShape> nodes = new ArrayList<>();
    private final List<AvShape> edges = new ArrayList<>();

    public List<Diagram> build(List<AvShape> shapes) {
        nodes.clear(); edges.clear();
        for (AvShape s : shapes) {
            if (s.kind == AvShape.Kind.NODE) nodes.add(s);
            else if (s.kind == AvShape.Kind.EDGE) edges.add(s);
        }
        if (nodes.isEmpty()) return Collections.emptyList();

        // turn each line into a "these two nodes are connected" pair and union them,
        // so each blob of connected nodes ends up as one tree
        List<int[]> connections = new ArrayList<>();
        DisjointSet ds = new DisjointSet(nodes.size());
        for (AvShape e : edges) {
            int[] pair = matchEdge(e);
            if (pair != null) {
                connections.add(pair);
                ds.union(pair[0], pair[1]);
            }
        }

        Map<Integer, List<Integer>> comps = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++)
            comps.computeIfAbsent(ds.find(i), k -> new ArrayList<>()).add(i);

        List<Diagram> diagrams = new ArrayList<>();
        for (List<Integer> comp : comps.values()) {
            Diagram d = buildTree(comp, connections);
            if (d != null) diagrams.add(d);
        }

        // hand them back in reading order: top to bottom, then left to right.
        // trees whose roots sit at about the same height count as the same "row".
        double band = avgNodeHeight() * 3.0;
        diagrams.sort(Comparator
                .comparingLong((Diagram d) -> Math.round(d.root.y / band))
                .thenComparingDouble(d -> d.root.x));
        return diagrams;
    }

    private double avgNodeHeight() {
        if (nodes.isEmpty()) return 48;
        double sum = 0;
        for (AvShape n : nodes) sum += n.h;
        return Math.max(sum / nodes.size(), 1);
    }

    // which two nodes does this line join? a line shape only gives us a bounding box,
    // so we rebuild its real end points from the box plus the flipH/flipV flags, then
    // grab the nearest node to each end.
    private int[] matchEdge(AvShape e) {
        if (nodes.size() < 2) return null;

        double upX = e.flipH ? e.x + e.w : e.x;
        double loX = e.flipH ? e.x : e.x + e.w;
        double upY = e.y, loY = e.y + e.h;
        if (e.flipV) { double t = upY; upY = loY; loY = t; double tx = upX; upX = loX; loX = tx; }

        // if the nearest node is too far away, treat it as no match - this is what stops
        // a line in one tree from grabbing a node in the tree next to it
        double threshold = nodeRadius() * 2.2;

        int up = nearestWithin(upX, upY, -1, threshold);
        int lo = nearestWithin(loX, loY, -1, threshold);
        if (up < 0 || lo < 0) return null;
        if (up == lo) {
            // both ends landed on the same node - take the next-best for one of them
            int lo2 = nearestWithin(loX, loY, lo, threshold);
            int up2 = nearestWithin(upX, upY, up, threshold);
            double optA = (lo2 < 0 ? 1e9 : dist(lo2, loX, loY)) + dist(up, upX, upY);
            double optB = (up2 < 0 ? 1e9 : dist(up2, upX, upY)) + dist(lo, loX, loY);
            if (optA <= optB && lo2 >= 0) lo = lo2;
            else if (up2 >= 0) up = up2;
        }
        if (up == lo || up < 0 || lo < 0) return null;
        return new int[]{up, lo};
    }

    private double nodeRadius() {
        double sum = 0;
        for (AvShape n : nodes) sum += Math.max(n.w, n.h);
        return nodes.isEmpty() ? 48 : (sum / nodes.size()) / 2.0;
    }

    private int nearestWithin(double px, double py, int exclude, double max) {
        int best = -1; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            if (i == exclude) continue;
            double dd = nodes.get(i).dist(px, py);
            if (dd < bestD) { bestD = dd; best = i; }
        }
        return bestD <= max ? best : -1;
    }

    private double dist(int i, double px, double py) { return nodes.get(i).dist(px, py); }

    private Diagram buildTree(List<Integer> comp, List<int[]> connections) {
        Map<Integer, TreeNode> tn = new HashMap<>();
        Set<Integer> compSet = new HashSet<>(comp);
        for (int i : comp) {
            AvShape s = nodes.get(i);
            TreeNode t = new TreeNode(s.text, s.cx(), s.cy(), s.id);
            t.color = s.colorName();
            tn.put(i, t);
        }
        // for every connection, the higher node is the parent
        for (int[] c : connections) {
            if (!compSet.contains(c[0]) || !compSet.contains(c[1])) continue;
            TreeNode na = tn.get(c[0]), nb = tn.get(c[1]);
            TreeNode parent = na.y <= nb.y ? na : nb;
            TreeNode childN = parent == na ? nb : na;
            attach(parent, childN);
        }
        // root is the one nobody's a parent of (if that somehow fails, use the topmost)
        TreeNode root = null;
        for (TreeNode t : tn.values())
            if (t.parent == null && (root == null || t.y < root.y)) root = t;
        if (root == null)
            for (TreeNode t : tn.values())
                if (root == null || t.y < root.y) root = t;

        Diagram d = new Diagram();
        d.root = root;
        d.nodeCount = comp.size();
        return d;
    }

    private void attach(TreeNode parent, TreeNode child) {
        if (child == parent || isAncestor(child, parent) || child.parent != null) return;
        child.parent = parent;
        if (child.x < parent.x) {            // sits to the left -> left child
            if (parent.left == null) parent.left = child;
            else if (parent.right == null) parent.right = child;
            else child.parent = null;        // both taken, drop it instead of corrupting the tree
        } else {
            if (parent.right == null) parent.right = child;
            else if (parent.left == null) parent.left = child;
            else child.parent = null;
        }
    }

    private boolean isAncestor(TreeNode maybeAncestor, TreeNode node) {
        for (TreeNode p = node; p != null; p = p.parent)
            if (p == maybeAncestor) return true;
        return false;
    }

    // plain union-find, used to group connected nodes
    static class DisjointSet {
        int[] p;
        DisjointSet(int n) { p = new int[n]; for (int i = 0; i < n; i++) p[i] = i; }
        int find(int x) { return p[x] == x ? x : (p[x] = find(p[x])); }
        void union(int a, int b) { p[find(a)] = find(b); }
    }
}
