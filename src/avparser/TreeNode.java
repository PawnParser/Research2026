package avparser;

import java.util.*;

// A node in a reconstructed binary tree.
public class TreeNode {
    public String value;
    public TreeNode left, right, parent;
    public double x, y;// center, used to tell left child from right
    public int shapeId;
    public String color = "";// "red"/"black"/... for red-black trees, else ""

    public TreeNode(String value, double x, double y, int shapeId) {
        this.value = value; this.x = x; this.y = y; this.shapeId = shapeId;
    }

    public int height() {
        int l = left == null ? 0 : left.height();
        int r = right == null ? 0 : right.height();
        return 1 + Math.max(l, r);
    }

    public List<String> inorder() {
        List<String> out = new ArrayList<>();
        if (left != null) out.addAll(left.inorder());
        out.add(value);
        if (right != null) out.addAll(right.inorder());
        return out;
    }
}
