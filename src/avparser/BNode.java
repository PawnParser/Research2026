package avparser;

import java.util.*;

// b tree node: a row of keys and below it, its child nodes in order
public class BNode {
    public List<String> keys = new ArrayList<>();
    public List<BNode> children = new ArrayList<>();
    public BNode parent;
    public double x, y;// box top-left, used for ordering
    public int shapeId;

    public String keysJoined() { return String.join(" ", keys); }

    public int countNodes() {
        int n = 1;
        for (BNode c : children) n += c.countNodes();
        return n;
    }

    //every key in subtree - used to spot which key is new between two snapshots
    public List<String> allKeys() {
        List<String> out = new ArrayList<>(keys);
        for (BNode c : children) out.addAll(c.allKeys());
        return out;
    }
}
