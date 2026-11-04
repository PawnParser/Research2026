package avparser;

import java.util.*;

// Reconstructs a graph from circle nodes joined by lines/arrows. Unlike a tree,
// a graph can have cycles and a node can have many connections, so there's no
// parent/child - we just record, for each node, which other nodes it's joined to.
// (This first version treats edges as undirected; arrow direction and weights are
//  future work.) Nodes with no label get numbered so they can still be announced.
public class GraphBuilder {

    public static class GNode {
        public String label;
        public List<GNode> neighbours = new ArrayList<>(); // outgoing (or undirected)
        public List<GNode> incoming  = new ArrayList<>();  // incoming (directed only)
        public boolean hasDirectedEdges = false;           // true if any edge is directed
        public double x, y;
        public int shapeId;
    }

    public static class Graph {
        public List<GNode> nodes = new ArrayList<>();
        public int edgeCount;
    }

    private List<AvShape> nodeShapes, edges;

    public List<Graph> build(List<AvShape> shapes) {
        nodeShapes = new ArrayList<>(); edges = new ArrayList<>();
        for (AvShape s : shapes) {
            if (s.kind == AvShape.Kind.NODE) nodeShapes.add(s);
            else if (s.kind == AvShape.Kind.EDGE) edges.add(s);
        }
        if (nodeShapes.size() < 2) return Collections.emptyList();

        // give every node a label (blank circles get numbered in reading order)
        List<AvShape> ordered = new ArrayList<>(nodeShapes);
        ordered.sort(Comparator.comparingDouble((AvShape a) -> a.y).thenComparingDouble(a -> a.x));
        Map<Integer, GNode> gnodes = new HashMap<>();
        int n = 1;
        for (AvShape s : ordered) {
            GNode g = new GNode();
            g.label = s.text.isEmpty() ? "node " + (n++) : s.text;
            g.x = s.cx(); g.y = s.cy(); g.shapeId = s.id;
            gnodes.put(s.id, g);
        }

        // connect nodes: each line joins the two nodes nearest its ends
        TreeBuilder.DisjointSet ds = new TreeBuilder.DisjointSet(nodeShapes.size());
        Map<Integer, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodeShapes.size(); i++) idx.put(nodeShapes.get(i).id, i);
        int edgeCount = 0;
        for (AvShape e : edges) {
            int[] pair = matchEdge(e);
            if (pair == null) continue;
            GNode a = gnodes.get(nodeShapes.get(pair[0]).id); // up-end
            GNode b = gnodes.get(nodeShapes.get(pair[1]).id); // lo-end

            boolean directed = e.headArrow != e.tailArrow; // exactly one end has arrow
            if (directed) {
                if (e.headArrow) {
                    // arrow at lo-end: a → b
                    if (!a.neighbours.contains(b)) { a.neighbours.add(b); b.incoming.add(a); }
                } else {
                    // arrow at up-end: b → a
                    if (!b.neighbours.contains(a)) { b.neighbours.add(a); a.incoming.add(b); }
                }
                a.hasDirectedEdges = true; b.hasDirectedEdges = true;
            } else {
                // undirected or bidirectional: add to both neighbour lists
                if (!a.neighbours.contains(b)) { a.neighbours.add(b); b.neighbours.add(a); }
            }
            edgeCount++;
            ds.union(pair[0], pair[1]);
        }

        Map<Integer, Graph> comps = new LinkedHashMap<>();
        for (int i = 0; i < nodeShapes.size(); i++)
            comps.computeIfAbsent(ds.find(i), k -> new Graph())
                 .nodes.add(gnodes.get(nodeShapes.get(i).id));

        List<Graph> graphs = new ArrayList<>(comps.values());
        for (Graph g : graphs) {
            g.nodes.sort(Comparator.comparingDouble((GNode a) -> a.y).thenComparingDouble(a -> a.x));
            for (GNode gn : g.nodes) g.edgeCount += gn.neighbours.size();
            g.edgeCount /= 2;
        }
        graphs.sort((a, b) -> Integer.compare(b.nodes.size(), a.nodes.size()));
        return graphs;
    }

    public boolean looksLikeGraph(List<AvShape> shapes) {
     
        List<Graph> gs = build(shapes);
        for (Graph g : gs) {
            if (g.nodes.size() >= 3 && g.edgeCount > g.nodes.size() * 2) return true;
        }
        return false;
    }

    private int[] matchEdge(AvShape e) {
        double upX = e.flipH ? e.x + e.w : e.x;
        double loX = e.flipH ? e.x : e.x + e.w;
        double upY = e.y, loY = e.y + e.h;
        if (e.flipV) { double t = upY; upY = loY; loY = t; double tx = upX; upX = loX; loX = tx; }
        double thr = nodeRadius() * 2.4;
        int a = nearest(upX, upY, -1, thr);
        int b = nearest(loX, loY, -1, thr);
        if (a < 0 || b < 0) return null;
        if (a == b) { b = nearest(loX, loY, a, thr); if (b < 0) return null; }
        if (a == b) return null;
        return new int[]{ a, b };
    }

    private double nodeRadius() {
        double sum = 0;
        for (AvShape s : nodeShapes) sum += Math.max(s.w, s.h);
        return nodeShapes.isEmpty() ? 40 : (sum / nodeShapes.size()) / 2.0;
    }

    private int nearest(double px, double py, int exclude, double max) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (int i = 0; i < nodeShapes.size(); i++) {
            if (i == exclude) continue;
            double d = nodeShapes.get(i).dist(px, py);
            if (d < bd) { bd = d; best = i; }
        }
        return bd <= max ? best : -1;
    }
}
