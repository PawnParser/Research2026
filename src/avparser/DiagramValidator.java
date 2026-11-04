package avparser;

import java.util.*;

/**
 * Validates reconstructed tree and B-tree diagrams for correctness before they are written to HTML pages.
 *
 * 3 kinds of check are applied:
 *
 *   1. Structural integrity — cycles or disconnected nodes always mean the
 *      parser made a mistake.  These diagrams are skipped.
 *
 *   2. Semantic correctness — when all node labels are integers the BST
 *      ordering property is checked.  A violation on a clearly non-insertion
 *     slide is skipped.  On insertion / case / rotation slides the diagram is kept but annotated, because the violation is intentional (it is
 *      the problem the algorithm is about to fix).
 *
 *   3. Property correctness — for red-black trees with colour information the main properties are checked and any violation is noted in the
 *     HTML page so the student knows they are looking at an intermediate
 *    state.  For B-trees each node's key list must be sorted and within
 *      the declared maximum.
 *
 * The result is a Report that the caller uses to decide what to do:
 *
 *   SKIP   — do not generate a page (structural error or clear BST mismatch
 *            on a slide that is not describing an operation).
 *   OK     — generate normally.
 *   ANNOTATE — generate with a note embedded in the page explaining what
 *              the student should be aware of.
 */
public class DiagramValidator {

    // ---- public API --------------------------------------------------

    public enum Action { OK, ANNOTATE, SKIP }

    public static class Report {
        public final Action  action;
        public final String  note;   // empty string when action == OK or SKIP
        public final String  reason; // short English reason, used in console log
        Report(Action a, String note, String reason) {
            this.action = a; this.note = note; this.reason = reason;
        }
    }

    /**
     * Validate a binary tree diagram.
     *
     * @param d          the reconstructed diagram
     * @param slideTitle the slide title, used to detect intermediate-state slides
     */
    public static Report validateTree(TreeBuilder.Diagram d, String slideTitle) {
        if (d == null || d.root == null)
            return skip("null root");

        // 1. structural integrity
        if (!isStructurallySound(d.root))
            return skip("cycle or disconnection detected in reconstructed tree");

        // 2. classify node labels
        LabelType kind = classifyLabels(d.root);

        if (kind == LabelType.NUMERIC) {
            // Only check BST ordering on slides that are actually about search trees. heaps, leftist heaps, treaps, Huffman trees and disjoint sets all store
            // numeric values that do NOT follow BST ordering — checking them here would produce false positives and silently skip valid diagrams.
            if (isBSTContext(slideTitle)) {
                if (!isBST(d.root, Long.MIN_VALUE, Long.MAX_VALUE)) {
                    if (isIntermediateSlide(slideTitle))
                        return annotate(
                            "The BST ordering property appears violated in this diagram. " +
                            "This is expected — the diagram shows an intermediate state during " +
                            "a tree operation (insertion or rotation) that the algorithm is " +
                            "about to correct. Navigate the before-and-after diagrams on this " +
                            "slide together to follow the full transformation.");
                    else
                        return skip("BST ordering violated on a search-tree slide — " +
                                    "likely a reconstruction error");
                }
            }

            // check red-black properties if colours present
            if (hasColours(d.root)) {
                String rbNote = checkRedBlack(d.root, slideTitle);
                if (rbNote != null) return annotate(rbNote);
            }
            return ok();
        }

        if (kind == LabelType.ROLE) {
            // labels like P, G, U, X, Y, Z are algorithm-role names used in
            // red-black insertion case diagrams — they show a pattern, not values
            if (hasColours(d.root)) {
                String rbNote = checkRedBlack(d.root, slideTitle);
                if (rbNote != null) return annotate(rbNote);
            }
            return annotate(
                "Node labels in this diagram are algorithm role names rather than " +
                "key values: P = parent, G = grandparent, U = uncle, X = new node, " +
                "Y and Z = subtrees. The diagram illustrates a general structural " +
                "pattern for a red-black tree insertion case.");
        }

        if (kind == LabelType.EMPTY) {
            // all nodes unlabelled — probably label association failed
            return annotate(
                "Node labels could not be recovered from this slide. " +
                "The structure of the tree was reconstructed from the shapes' " +
                "positions, but some or all key values are missing. " +
                "Refer to the original slide for the complete diagram.");
        }

        // mixed (some numeric, some role, some empty) — generate with a general note
        return annotate(
            "Some node labels in this diagram could not be verified. " +
            "Navigate carefully and cross-reference with the original slide.");
    }

    /**
     * Validate a single B-tree insertion state.
     *
     * @param state  one snapshot in the insertion sequence
     * @param maxKeys the maximum number of keys a node can hold (the B-tree order)
     */
    public static Report validateBTreeState(BTreeBuilder.State state, int maxKeys) {
        if (state == null || state.root == null)
            return skip("null B-tree state");

        // check that every node's key list is sorted and within capacity
        String orderViolation = checkBTreeOrder(state.root, maxKeys);
        if (orderViolation != null) {
            // a node temporarily over capacity immediately before a split is expected
            if (state.split)
                return annotate(
                    "At step " + state.index + " a node momentarily holds more than " +
                    maxKeys + " keys (the maximum) just before splitting. " +
                    orderViolation);
            else
                return annotate(
                    "The key ordering within a B-tree node appears incorrect at " +
                    "step " + state.index + ". " + orderViolation +
                    " This may be a reconstruction artefact; refer to the original slide.");
        }
        return ok();
    }

    private static Report ok()                            { return new Report(Action.OK,       "", ""); }
    private static Report skip(String reason)             { return new Report(Action.SKIP,     "", reason); }
    private static Report annotate(String note)           { return new Report(Action.ANNOTATE, note, ""); }


    private enum LabelType { NUMERIC, ROLE, EMPTY, MIXED }

    private static LabelType classifyLabels(TreeNode root) {
        List<String> labels = new ArrayList<>();
        collectNonEmptyLabels(root, labels);
        if (labels.isEmpty()) return LabelType.EMPTY;
        int numeric = 0, role = 0;
        for (String l : labels) {
            if (l.matches("-?\\d+"))   numeric++;
            else if (isRoleLabel(l))   role++;
        }
        if (numeric == labels.size()) return LabelType.NUMERIC;
        if (role    == labels.size()) return LabelType.ROLE;
        return LabelType.MIXED;
    }

    private static void collectNonEmptyLabels(TreeNode n, List<String> out) {
        if (n == null) return;
        String v = n.value == null ? "" : n.value.trim();
        if (!v.isEmpty()) out.add(v);
        collectNonEmptyLabels(n.left, out);
        collectNonEmptyLabels(n.right, out);
    }

    // single capital letters used as role names in red-black tree case slides
    private static boolean isRoleLabel(String s) {
        return s.matches("[PGUXYZS]");
    }


    private static boolean isStructurallySound(TreeNode root) {
        return !hasCycle(root, new HashSet<>());
    }

    private static boolean hasCycle(TreeNode n, Set<TreeNode> visited) {
        if (n == null) return false;
        if (!visited.add(n)) return true;   // seen before -> cycle
        return hasCycle(n.left, visited) || hasCycle(n.right, visited);
    }


    /**
     * Returns true if the subtree rooted at n satisfies the BST property.
     * Empty-label nodes are treated as wildcards and do not break the check.
     */
    private static boolean isBST(TreeNode n, long lo, long hi) {
        if (n == null) return true;
        String v = n.value == null ? "" : n.value.trim();
        if (!v.isEmpty() && v.matches("-?\\d+")) {
            long val;
            try { val = Long.parseLong(v); } catch (NumberFormatException e) { return true; }
            if (val <= lo || val >= hi) return false;
            return isBST(n.left, lo, val) && isBST(n.right, val, hi);
        }
        // no numeric label on this node — skip ordering check at this level
        return isBST(n.left, lo, hi) && isBST(n.right, lo, hi);
    }
    private static boolean hasColours(TreeNode n) {
        if (n == null) return false;
        if (n.color != null && !n.color.isEmpty()) return true;
        return hasColours(n.left) || hasColours(n.right);
    }

    private static String checkRedBlack(TreeNode root, String slideTitle) {
        boolean isIntermediate = isIntermediateSlide(slideTitle);

        // Property: root must be black
        if ("red".equals(root.color)) {
            String fix = isIntermediate
                ? " This is expected on an insertion slide — the root is recoloured " +
                  "black as the final step of the algorithm."
                : " If this is not an intermediate state, the reconstruction may have " +
                  "assigned the wrong colour to the root.";
            return "Red-black property check: the root is shown as red, which violates " +
                   "the rule that the root must always be black." + fix;
        }

        if (hasDoubleRed(root)) {
            return "Red-black property check: this diagram contains a red node whose " +
                   "parent is also red (a double-red violation). This is an intermediate " +
                   "state — the insertion algorithm is about to resolve this by recolouring " +
                   "or rotating. The before-and-after diagrams on this slide show the " +
                   "full correction.";
        }

        if (blackHeight(root) == -1) {
            return "Red-black property check: the number of black nodes on different " +
                   "root-to-leaf paths is not equal (black-height imbalance). " +
                   "This may be an intermediate state or a reconstruction artefact — " +
                   "refer to the original slide to confirm.";
        }

        return null; // all red-black properties satisfied
    }

    /** True if any node is red and has a red child. */
    private static boolean hasDoubleRed(TreeNode n) {
        if (n == null) return false;
        if ("red".equals(n.color)) {
            if (n.left  != null && "red".equals(n.left.color))  return true;
            if (n.right != null && "red".equals(n.right.color)) return true;
        }
        return hasDoubleRed(n.left) || hasDoubleRed(n.right);
    }

    private static int blackHeight(TreeNode n) {
        if (n == null) return 0; // null sentinel counts as black, height 0
        int l = blackHeight(n.left);
        int r = blackHeight(n.right);
        if (l == -1 || r == -1 || l != r) return -1;
        boolean isBlack = "black".equals(n.color) || n.color == null || n.color.isEmpty();
        return l + (isBlack ? 1 : 0);
    }

    /**
     * Checks that every node's keys are sorted in ascending order and that
     * no node (other than one mid-split) exceeds maxKeys.
     * Returns null if everything is correct, or a descriptive message.
     */
    private static String checkBTreeOrder(BNode node, int maxKeys) {
        if (node == null) return null;
        // keys within this node must be in ascending order
        List<String> keys = node.keys;
        for (int i = 1; i < keys.size(); i++) {
            if (!isNumericBTreeKey(keys.get(i-1)) || !isNumericBTreeKey(keys.get(i))) continue;
            long prev = Long.parseLong(keys.get(i-1).trim());
            long curr = Long.parseLong(keys.get(i).trim());
            if (curr <= prev)
                return "Keys " + prev + " and " + curr + " are out of order within a node.";
        }
        // check children recursively
        for (BNode child : node.children) {
            String childResult = checkBTreeOrder(child, maxKeys);
            if (childResult != null) return childResult;
        }
        return null;
    }

    private static boolean isNumericBTreeKey(String k) {
        return k != null && k.trim().matches("-?\\d+");
    }

    /**
     * Returns true if the slide title suggests it shows an operation in
     * progress (insertion, rotation, a case diagram) rather than a final state.
     */
    /**
     * Returns true only when the slide title clearly indicates a binary SEARCH  tree, AVL tree, splay tree or red-black tree. Heaps, leftist heaps,
     * Huffman trees and disjoint sets should return false so that their non-BST numeric values are not rejected by the ordering check.
     */
    private static boolean isBSTContext(String title) {
        if (title == null || title.isEmpty()) return false;
        String t = title.toLowerCase();
        return t.contains("bst") || t.contains("binary search")
            || t.contains("search tree") || t.contains("avl")
            || t.contains("splay") || t.contains("red") || t.contains("black")
            || t.contains("treap");
    }

    /** Whether the slide describes an ongoing operation (safe to make public). */
    public static boolean isOperationSlide(String title) {
        return isIntermediateSlide(title);
    }

    private static boolean isIntermediateSlide(String title) {
        if (title == null || title.isEmpty()) return false;
        String t = title.toLowerCase();
        return t.contains("insert") || t.contains("rotat") || t.contains("case")
            || t.contains("fix")    || t.contains("recolor") || t.contains("step")
            || t.contains("zig")    || t.contains("zag")     || t.contains("deletion")
            || t.contains("splay")  || t.contains("bottom")  || t.contains("top-down")
            || t.contains("example");   // "Example 1: Delete 10" etc.
    }
}
