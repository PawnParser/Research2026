package avparser;

import org.w3c.dom.*;
import java.util.*;

// Helpers for walking the OOXML tree by local name, so we can ignore all the
// namespace prefixes PowerPoint throws around (p:, a:, r: and friends).
public final class Xml {
    private Xml() {}

    // direct child elements with this local name
    public static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && local(n).equals(localName))
                out.add((Element) n);
        }
        return out;
    }

    // every direct child element, whatever it's called
    public static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++)
            if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) out.add((Element) nl.item(i));
        return out;
    }

    public static Element child(Element parent, String localName) {
        List<Element> c = children(parent, localName);
        return c.isEmpty() ? null : c.get(0);
    }

    // every element underneath, any depth, with this local name
    public static List<Element> descendants(Element root, String localName) {
        List<Element> out = new ArrayList<>();
        if (root == null) return out;
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Element e = stack.pop();
            NodeList nl = e.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    Element ce = (Element) n;
                    if (local(ce).equals(localName)) out.add(ce);
                    stack.push(ce);
                }
            }
        }
        return out;
    }

    public static Element firstDescendant(Element root, String localName) {
        List<Element> d = descendants(root, localName);
        return d.isEmpty() ? null : d.get(0);
    }

    // all the text inside an element (every <a:t> run joined together)
    public static String text(Element root) {
        StringBuilder sb = new StringBuilder();
        for (Element t : descendants(root, "t")) sb.append(t.getTextContent());
        return sb.toString().trim();
    }

    public static String local(Node n) {
        String ln = n.getLocalName();
        if (ln != null) return ln;
        String name = n.getNodeName();
        int idx = name.indexOf(':');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    public static String attr(Element e, String name, String def) {
        if (e == null) return def;
        String v = e.getAttribute(name);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
