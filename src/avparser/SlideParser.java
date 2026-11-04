package avparser;

import org.w3c.dom.*;
import java.util.*;
import java.util.regex.*;

public class SlideParser {

    static final double EMU_PER_PX = 9525.0;   // PowerPoint stores positions in EMUs

    public List<AvShape> parse(Element sld) {
        List<AvShape> shapes = new ArrayList<>();
        Element spTree = Xml.child(Xml.child(sld, "cSld"), "spTree");
        if (spTree == null) return shapes;

        for (Element el : Xml.childElements(spTree)) {
            switch (Xml.local(el)) {
                case "sp": case "cxnSp": case "graphicFrame": case "pic":
                    AvShape s = readShape(el, Xml.local(el));
                    if (s != null) shapes.add(s);
                    break;
                default: // group/background bookkeeping, skip it
            }
        }
        dedupNodes(shapes);
        associateLabels(shapes);
        return shapes;
    }

    private static boolean isBlackFill(String fill) {
        return "000000".equalsIgnoreCase(fill);
    }

    private void dedupNodes(List<AvShape> shapes) {
        List<AvShape> nodes = new ArrayList<>();
        for (AvShape s : shapes) if (s.kind == AvShape.Kind.NODE) nodes.add(s);
        Set<AvShape> drop = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            AvShape a = nodes.get(i);
            if (drop.contains(a)) continue;
            for (int j = i + 1; j < nodes.size(); j++) {
                AvShape b = nodes.get(j);
                if (drop.contains(b)) continue;
                double gap = Math.hypot(a.cx() - b.cx(), a.cy() - b.cy());
                if (gap <= Math.max(a.w, a.h) * 0.35) {   // centres almost on top of each other

                    if (!b.fill.isEmpty()) {
                        a.fill = b.fill; // b is on top, so b's colour is what the student sees
                    }
                    if (a.text.isEmpty() && !b.text.isEmpty()) a.text = b.text;
                    drop.add(b);
                }
            }
        }
        shapes.removeAll(drop);
    }

    private void associateLabels(List<AvShape> shapes) {
        List<AvShape> allNodes = new ArrayList<>(), labels = new ArrayList<>();
        for (AvShape s : shapes) {
            if (s.kind == AvShape.Kind.NODE) allNodes.add(s);
            else if (s.kind == AvShape.Kind.KEY) labels.add(s);
        }
        // each label picks the node it's closest to (within a generous reach of that node)
        Map<AvShape, AvShape> labelNearest = new HashMap<>();
        for (AvShape l : labels) {
            AvShape best = null; double bd = Double.MAX_VALUE;
            for (AvShape n : allNodes) {
                double d = Math.hypot(l.cx() - n.cx(), l.cy() - n.cy());
                if (d < bd && d <= Math.max(n.w, n.h) * 1.7) { bd = d; best = n; }
            }
            if (best != null) labelNearest.put(l, best);
        }
        // each empty node takes the closest label that pointed back at it
        for (AvShape n : allNodes) {
            if (!n.text.isEmpty()) continue;
            AvShape best = null; double bd = Double.MAX_VALUE;
            for (Map.Entry<AvShape, AvShape> e : labelNearest.entrySet()) {
                if (e.getValue() != n) continue;
                double d = Math.hypot(e.getKey().cx() - n.cx(), e.getKey().cy() - n.cy());
                if (d < bd) { bd = d; best = e.getKey(); }
            }
            if (best != null) { n.text = best.text; labelNearest.remove(best); }
        }
    }

    private AvShape readShape(Element el, String tag) {
        Element cNvPr = Xml.firstDescendant(el, "cNvPr");
        if (cNvPr == null) return null;

        AvShape s = new AvShape();
        try { s.id = Integer.parseInt(Xml.attr(cNvPr, "id", "0")); } catch (Exception ignore) {}
        s.name = Xml.attr(cNvPr, "name", "");
        s.text = Xml.text(el);
        s.isPlaceholder = !Xml.descendants(el, "ph").isEmpty();   // title/body text, not a diagram part

        Element xfrm = Xml.firstDescendant(el, "xfrm");
        if (xfrm != null) {
            s.flipH = "1".equals(Xml.attr(xfrm, "flipH", "0"));
            s.flipV = "1".equals(Xml.attr(xfrm, "flipV", "0"));
            Element off = Xml.child(xfrm, "off");
            Element ext = Xml.child(xfrm, "ext");
            if (off != null) { s.x = toPx(Xml.attr(off, "x", "0")); s.y = toPx(Xml.attr(off, "y", "0")); }
            if (ext != null) { s.w = toPx(Xml.attr(ext, "cx", "0")); s.h = toPx(Xml.attr(ext, "cy", "0")); }
        }

        Element prst = Xml.firstDescendant(el, "prstGeom");
        // a picture carries a rect frame geometry, so key off the tag, not the geom
        if (tag.equals("pic")) s.geom = "pic";
        else s.geom = prst != null ? Xml.attr(prst, "prst", "") : (tag.equals("cxnSp") ? "line" : tag);

        // B-tree node boxes are tables - keep their cell text, that's where the keys live
        Element tbl = Xml.firstDescendant(el, "tbl");
        if (tbl != null) {
            s.tableCells = new ArrayList<>();
            for (Element tc : Xml.descendants(tbl, "tc")) s.tableCells.add(Xml.text(tc));
        }

        // fill colour of the shape itself (spPr's own solidFill), used to name red/black nodes
        Element spPr = Xml.firstDescendant(el, "spPr");
        Element solid = spPr != null ? Xml.child(spPr, "solidFill") : null;
        if (solid != null) {
            Element srgb = Xml.child(solid, "srgbClr");
            if (srgb != null) s.fill = Xml.attr(srgb, "val", "").toUpperCase();
            else {
                Element scheme = Xml.child(solid, "schemeClr");
                String v = scheme != null ? Xml.attr(scheme, "val", "") : "";
                if (v.equals("dk1") || v.equals("tx1")) s.fill = "000000";   // theme black
            }
        }

        if ("line".equals(s.geom) || "cxnSp".equals(tag)) {
            String headType = "";
            String tailType = "";
            for (org.w3c.dom.Element he : Xml.descendants(el, "headEnd"))
                headType = he.getAttribute("type");
            for (org.w3c.dom.Element te : Xml.descendants(el, "tailEnd"))
                tailType = te.getAttribute("type");
            boolean hasHead = !headType.isEmpty() && !headType.equals("none");
            boolean hasTail = !tailType.isEmpty() && !tailType.equals("none");
            s.headArrow = hasHead;
            s.tailArrow = hasTail;
        }

        s.kind = classify(s, tag);
        return s;
    }

    private AvShape.Kind classify(AvShape s, String tag) {
        if (s.isPlaceholder) return AvShape.Kind.OTHER;
        if (tag.equals("cxnSp") || s.geom.equals("line")
                || s.geom.startsWith("straightConnector") || s.geom.startsWith("bentConnector"))
            return AvShape.Kind.EDGE;

        if (s.tableCells != null) return AvShape.Kind.NODEBOX;        // B-tree node
        if (tag.equals("graphicFrame")) return AvShape.Kind.MULTIKEY; // grouped object, no table


        boolean roundish = s.geom.equals("ellipse") || s.geom.equals("circle") || s.geom.equals("roundRect");
        boolean square = s.w > 0 && s.h > 0 && Math.max(s.w, s.h) / Math.min(s.w, s.h) < 2.5;
        boolean bigEnough = s.w >= 14 && s.h >= 14;
        // A node label is short (a value, a letter). Anything longer is a callout
        // box, description, or annotation — not a diagram node.
        boolean notLongText = s.text.length() <= 20;
        if (roundish && square && bigEnough && notLongText) return AvShape.Kind.NODE;


        if (tag.equals("sp") && !s.text.isEmpty() && s.text.length() <= 8
                && !s.name.toLowerCase().startsWith("title"))
            return AvShape.Kind.KEY;

        return AvShape.Kind.OTHER;
    }

    private double toPx(String emu) {
        try { return Long.parseLong(emu) / EMU_PER_PX; }
        catch (Exception e) { return 0; }
    }

    // one shape moving by (dx,dy) px on a given click
    public static class Move {
        public int spid; public double dx, dy;
        public Move(int spid, double dx, double dy) { this.spid = spid; this.dx = dx; this.dy = dy; }
    }

    public List<List<Move>> parseSteps(Element sld, double slideW, double slideH) {
        List<List<Move>> steps = new ArrayList<>();
        Element timing = Xml.firstDescendant(sld, "timing");
        if (timing == null) return steps;

        Element mainSeq = null;
        for (Element ctn : Xml.descendants(timing, "cTn"))
            if ("mainSeq".equals(ctn.getAttribute("nodeType"))) { mainSeq = ctn; break; }
        if (mainSeq == null) return steps;
        Element childList = Xml.child(mainSeq, "childTnLst");
        if (childList == null) return steps;

        for (Element par : Xml.children(childList, "par")) {
            List<Move> moves = new ArrayList<>();
            for (Element am : Xml.descendants(par, "animMotion")) {
                Element tgt = Xml.firstDescendant(am, "spTgt");
                if (tgt == null) continue;
                int spid;
                try { spid = Integer.parseInt(tgt.getAttribute("spid")); } catch (Exception e) { continue; }
                double[] d = lastLineDelta(am.getAttribute("path"));
                if (d != null) moves.add(new Move(spid, d[0] * slideW, d[1] * slideH));  // fraction -> px
            }
            steps.add(moves);
        }
        return steps;
    }

    // a motion path looks like "M 0 0 L 0.126 0.002"; we want that last L, the end offset
    private double[] lastLineDelta(String path) {
        if (path == null || path.isEmpty()) return null;
        Matcher m = Pattern.compile("L\\s+(-?[\\d.eE-]+)\\s+(-?[\\d.eE-]+)").matcher(path);
        double[] last = null;
        while (m.find()) {
            try { last = new double[]{ Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) }; }
            catch (NumberFormatException ignore) {}
        }
        return last;
    }
}
