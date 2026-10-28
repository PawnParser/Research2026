package avparser;

// One shape lifted off a slide, with its position already in pixels.
public class AvShape {
    // what the shape is, as far as the tree-building cares:
    // NODE = circle in a binary tree, EDGE = connecting line, NODEBOX = B-tree table,
    // KEY = a value that slides into a B-tree box, MULTIKEY = grouped object w/o table, OTHER = ignore
    public enum Kind { NODE, EDGE, MULTIKEY, NODEBOX, KEY, OTHER }

    public int id;
    public String name = "";
    public String text = "";
    public String geom = "";          // prstGeom value: ellipse, line, rect...
    public Kind kind = Kind.OTHER;
    public boolean isPlaceholder;     // slide title/body text
    public java.util.List<String> tableCells;   // set when the shape is a B-tree table

    public double x, y, w, h;         // bounding box in px (1px = 9525 EMU)
    public boolean flipH, flipV;      // mirror flags - needed to find a line's real end points
    public String fill = "";          // node fill as hex like "C0504D" (used to name red/black nodes)
    // edge direction: for directed graphs the "up" end is the tail, "lo" end is the head.
    // headArrow=true  means the lo-end  (bottom-right of bounding box) has an arrowhead.
    // tailArrow=true  means the up-end  (top-left)  has an arrowhead.
    // both false = undirected; both true = bidirectional (treated as undirected).
    public boolean headArrow = false;
    public boolean tailArrow = false;

    public double cx() { return x + w / 2.0; }
    public double cy() { return y + h / 2.0; }

    // rough name for the fill colour so a node can be announced as "red" / "black" etc.
    // returns "" when there's nothing worth mentioning (white / no fill).
    public String colorName() {
        if (fill == null || fill.length() != 6) return "";
        int r, g, b;
        try {
            r = Integer.parseInt(fill.substring(0, 2), 16);
            g = Integer.parseInt(fill.substring(2, 4), 16);
            b = Integer.parseInt(fill.substring(4, 6), 16);
        } catch (Exception e) { return ""; }
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max < 70) return "black";
        if (min > 200) return "";                          // white-ish, skip it
        if (r > 140 && g < 120 && b < 120) return "red";
        if (g > 130 && r < 150 && b < 150) return "green";
        if (b > 140 && r < 140 && g < 170) return "blue";
        if (r > 190 && g > 160 && b < 120) return "yellow";
        if (max - min < 45) return "grey";
        return "";
    }

    public double dist(double px, double py) {
        double dx = cx() - px, dy = cy() - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override public String toString() {
        return String.format("#%d %s [%s/%s] '%s' (%.0f,%.0f %.0fx%.0f)",
                id, name, kind, geom, text, x, y, w, h);
    }
}
