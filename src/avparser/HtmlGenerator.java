package avparser;

import java.util.*;

public class HtmlGenerator {

    public String generateTreeSequence(String pageTitle,
                                       java.util.List<TreeBuilder.Diagram> steps,
                                       String context) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) arr.append(",");
            arr.append(toJson(steps.get(i).root));
        }
        arr.append("]");
        return TREE_SEQ_TEMPLATE
                .replace("__TITLE__", esc(pageTitle))
                .replace("__CONTEXT__", escJs(context == null ? "" : context))
                .replace("__STEPS_JSON__", arr.toString());
    }

    private static final String TREE_SEQ_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>__TITLE__</title>\n<style>\n" +
        "  body { font-family:monospace; font-size:14px; max-width:520px; margin:3em auto;\n" +
        "         background:#fff; color:#111; line-height:1.8; }\n" +
        "  #box { border:2px solid #333; padding:1.4em; margin-top:1.2em; outline:none; }\n" +
        "  #box:focus { border-color:#000; }\n" +
        "  .val  { font-size:22px; font-weight:bold; margin:0.2em 0; }\n" +
        "  .info { font-size:13px; margin:0.15em 0; }\n" +
        "  .step { font-size:12px; color:#555; margin-bottom:0.4em; }\n" +
        "  .keys { font-size:12px; color:#444; margin-top:1.2em; }\n" +
        "  .keys b { display:inline-block; min-width:160px; }\n" +
        "  .warn  { font-size:13px; background:#fffbe6; border:1px solid #ccc;\n" +
        "           padding:0.7em; margin-bottom:1em; }\n" +
        "</style>\n</head>\n<body>\n<h1>__TITLE__</h1>\n" +
        "<div class=\"warn\"><strong>NVDA users:</strong> press <strong>Insert + Space</strong>\n" +
        "  for Focus Mode, then use the keys below.</div>\n" +
        "<div class=\"keys\">\n" +
        "  <div><b>Page Down / Page Up</b> &mdash; next / previous step</div>\n" +
        "  <div><b>Up arrow</b> &mdash; go to parent node</div>\n" +
        "  <div><b>Left / Right arrow</b> &mdash; go to left / right child</div>\n" +
        "  <div><b>Down arrow</b> &mdash; return to root</div>\n" +
        "  <div><b>H</b> &mdash; help and slide context</div>\n" +
        "</div>\n" +
        "<div id=\"box\" tabindex=\"0\" aria-live=\"assertive\" aria-atomic=\"true\"\n" +
        "     role=\"region\" aria-label=\"Tree step display\"></div>\n" +
        "<script>\n" +
        "const STEPS = __STEPS_JSON__;\n" +
        "const CONTEXT = \"__CONTEXT__\";\n" +
        "let si = 0;\n" +
        "function link(n, par){ if(!n) return null; n.parent=par;\n" +
        "  link(n.left,n); link(n.right,n); return n; }\n" +
        "STEPS.forEach(function(s){ link(s,null); });\n" +
        "let current = STEPS[0];\n" +
        "function speak(html){ const b=document.getElementById('box');\n" +
        "  b.innerHTML=''; setTimeout(()=>{ b.innerHTML=html; },50); }\n" +
        "function nodeView(n){\n" +
        "  const label = n.c ? n.c.charAt(0).toUpperCase()+n.c.slice(1)+' node '+n.v\n" +
        "                     : 'Node '+n.v;\n" +
        "  const parent = n.parent ? 'Node '+n.parent.v+'.' : 'none. This is the root.';\n" +
        "  const left   = n.left   ? 'Node '+n.left.v+'.'  : 'empty.';\n" +
        "  const right  = n.right  ? 'Node '+n.right.v+'.' : 'empty.';\n" +
        "  return '<div class=\"step\">Step '+(si+1)+' of '+STEPS.length+'.</div>'+\n" +
        "         '<div class=\"val\">'+label+'.</div>'+\n" +
        "         '<div class=\"info\">Parent: '+parent+'</div>'+\n" +
        "         '<div class=\"info\">Left child: '+left+'</div>'+\n" +
        "         '<div class=\"info\">Right child: '+right+'</div>'; }\n" +
        "function stepAnnounce(){\n" +
        "  current = STEPS[si];\n" +
        "  speak('<div class=\"step\">Step '+(si+1)+' of '+STEPS.length+\n" +
        "        '. Press arrow keys to explore the tree. Page Down for next step.</div>'); }\n" +
        "document.getElementById('box').addEventListener('keydown',function(e){\n" +
        "  const keys=['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','PageUp','PageDown'];\n" +
        "  if(keys.includes(e.key)) e.preventDefault();\n" +
        "  if(e.key==='h'||e.key==='H'){\n" +
        "    speak('<p>Step '+(si+1)+' of '+STEPS.length+'. Arrow keys navigate nodes, Page Down/Up moves between steps.</p>'); return; }\n" +
        "  if(e.key==='c'||e.key==='C'){\n" +
        "    if(CONTEXT&&CONTEXT.length>0){ var t=CONTEXT.length<=400?CONTEXT:CONTEXT.slice(0,400)+'...'; speak('<p>Slide context: '+t+'</p>'); }\n" +
        "    else{ speak('<p>No additional context on this slide.</p>'); }\n" +
        "    return; }\n" +
        "  switch(e.key){\n" +
        "    case 'PageDown': if(si<STEPS.length-1){ si++; stepAnnounce(); } break;\n" +
        "    case 'PageUp':   if(si>0){ si--; stepAnnounce(); } break;\n" +
        "    case 'ArrowUp':\n" +
        "      if(current.parent){ current=current.parent; speak(nodeView(current)); } break;\n" +
        "    case 'ArrowLeft':\n" +
        "      if(current.left){ current=current.left; speak(nodeView(current)); } break;\n" +
        "    case 'ArrowRight':\n" +
        "      if(current.right){ current=current.right; speak(nodeView(current)); } break;\n" +
        "    case 'ArrowDown':\n" +
        "      current=STEPS[si]; speak(nodeView(current)); break;\n" +
        "  }\n" +
        "});\n" +
        "window.onload=()=>{ document.getElementById('box').focus(); stepAnnounce(); };\n" +
        "</script>\n</body>\n</html>\n";

    public String generate(String pageTitle, TreeBuilder.Diagram d, String context,
                           String validationNote) {
        String treeJson = toJson(d.root);
        String summary = "Tree with " + d.nodeCount + " node" + (d.nodeCount == 1 ? "" : "s")
                + ". Root is node " + esc(d.root.value) + ". Height " + d.root.height() + ".";

        return TEMPLATE
                .replace("__TITLE__", esc(pageTitle))
                .replace("__SUMMARY__", esc(summary))
                .replace("__CONTEXT__", escJs(context == null ? "" : context))
                .replace("__VALIDATION__", esc(validationNote == null ? "" : validationNote))
                .replace("__TREE_JSON__", treeJson);
    }

    // turn the tree into a little JS object the page can walk: {"v":"G","c":"red","left":...,"right":...}
    private String toJson(TreeNode n) {
        if (n == null) return "null";
        String c = n.color == null ? "" : n.color;
        return "{\"v\":\"" + escJs(n.value) + "\",\"c\":\"" + escJs(c) + "\",\"left\":" + toJson(n.left)
                + ",\"right\":" + toJson(n.right) + "}";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static String escJs(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // same idea but for B-trees: each state becomes one entry in a JS array

    public String generateBTree(String pageTitle, java.util.List<BTreeBuilder.State> states,
                                String context, String validationNote) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < states.size(); i++) {
            BTreeBuilder.State st = states.get(i);
            if (i > 0) arr.append(",");
            arr.append("{\"inserted\":")
               .append(st.insertedKey == null ? "null" : "\"" + escJs(st.insertedKey) + "\"")
               .append(",\"max\":").append(st.maxKeys)
               .append(",\"split\":").append(st.split)
               .append(",\"note\":\"").append(escJs(st.note)).append("\"")
               .append(",\"root\":").append(bjson(st.root)).append("}");
        }
        arr.append("]");
        return BTREE_TEMPLATE
                .replace("__TITLE__", esc(pageTitle))
                .replace("__CONTEXT__", escJs(context == null ? "" : context))
                .replace("__VALIDATION__", esc(validationNote == null ? "" : validationNote))
                .replace("__STATES_JSON__", arr.toString());
    }

    private String bjson(BNode n) {
        if (n == null) return "null";
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < n.keys.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escJs(n.keys.get(i))).append("\"");
        }
        sb.append("],\"children\":[");
        for (int i = 0; i < n.children.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(bjson(n.children.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }


    public String generateTreapInfo(String pageTitle, String nodeText, String slideContext) {
        String body =
            "<p>A <strong>treap</strong> (tree + heap) is a binary tree in which each node " +
            "holds both a <em>value</em> (satisfying the BST ordering property) and a " +
            "<em>priority</em> (satisfying the min-heap property). This combination keeps " +
            "the tree balanced on average without requiring the explicit rotations of an AVL " +
            "or red-black tree.</p>" +
            "<p><strong>Navigation note:</strong> the treap diagram in this slide is drawn " +
            "as a composite image that cannot be reconstructed node by node. The node values " +
            "from the slide are shown below. Please refer to the original slide for the full " +
            "tree structure.</p>" +
            "<p><strong>Node values from slide:</strong> " + esc(nodeText) + "</p>";
        if (!slideContext.isEmpty())
            body += "<p><strong>Slide notes:</strong> " + esc(slideContext) + "</p>";
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">" +
            "<title>" + esc(pageTitle) + "</title><style>" +
            "body{font-family:monospace;font-size:14px;max-width:520px;margin:3em auto;color:#111;}" +
            "h1{font-size:20px;}p{line-height:1.7;margin:0.8em 0;}" +
            "strong{color:#000;}" +
            "</style></head><body><h1>" + esc(pageTitle) + "</h1>" + body +
            "</body></html>\n";
    }

    // ---- graph explorer -------------------------------------------------

    public String generateGraph(String pageTitle, GraphBuilder.Graph g, String context) {
        Map<GraphBuilder.GNode, Integer> id = new HashMap<>();
        for (int i = 0; i < g.nodes.size(); i++) id.put(g.nodes.get(i), i);
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < g.nodes.size(); i++) {
            GraphBuilder.GNode n = g.nodes.get(i);
            if (i > 0) arr.append(",");
            arr.append("{\"l\":\"").append(escJs(n.label))
               .append("\",\"dir\":").append(n.hasDirectedEdges)
               .append(",\"nb\":[");
            List<Integer> nb = new ArrayList<>();
            for (GraphBuilder.GNode m : n.neighbours) nb.add(id.get(m));
            for (int j = 0; j < nb.size(); j++) { if (j > 0) arr.append(","); arr.append(nb.get(j)); }
            arr.append("],\"in\":[");
            List<Integer> in = new ArrayList<>();
            for (GraphBuilder.GNode m : n.incoming) in.add(id.get(m));
            for (int j = 0; j < in.size(); j++) { if (j > 0) arr.append(","); arr.append(in.get(j)); }
            arr.append("]}");
        }
        arr.append("]");
        return GRAPH_TEMPLATE
                .replace("__TITLE__", esc(pageTitle))
                .replace("__CONTEXT__", escJs(context == null ? "" : context))
                .replace("__NODES_JSON__", arr.toString());
    }

    private static final String GRAPH_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>__TITLE__</title>\n<style>\n" +
        "  body { font-family: monospace; font-size:14px; max-width:480px; margin:3em auto;\n" +
        "         background:#fff; color:#111; line-height:1.8; }\n" +
        "  #box { border:2px solid #333; padding:1.4em; margin-top:1.2em; outline:none; }\n" +
        "  #box:focus { border-color:#000; }\n" +
        "  .val  { font-size:22px; font-weight:bold; margin:0.2em 0; }\n" +
        "  .info { font-size:13px; margin:0.15em 0; }\n" +
        "  .keys { font-size:12px; color:#444; margin-top:1.2em; }\n" +
        "  .keys b { display:inline-block; min-width:150px; }\n" +
        "  .warn { font-size:13px; background:#fffbe6; border:1px solid #ccc; padding:0.7em; margin-bottom:1em; }\n" +
        "</style>\n</head>\n<body>\n<h1>__TITLE__</h1>\n" +
        "<div class=\"warn\"><strong>NVDA users:</strong> press <strong>Insert + Space</strong> for Focus Mode,\n" +
        "  then use the keys below. For directed graphs, Left and Right cycle outgoing connections only.</div>\n" +
        "<div class=\"keys\">\n" +
        "  <div><b>Left / Right arrow</b> &mdash; cycle through the current node's connections</div>\n" +
        "  <div><b>Enter</b> &mdash; move to the selected connected node</div>\n" +
        "  <div><b>Backspace</b> &mdash; go back to the previous node</div>\n" +
        "  <div><b>H</b> &mdash; help: this node's connections and the slide's notes</div>\n" +
        "  <div><b>Page Down / Page Up</b> &mdash; step through every node in turn</div>\n" +
        "</div>\n" +
        "<div id=\"box\" tabindex=\"0\" aria-live=\"assertive\" aria-atomic=\"true\"\n" +
        "     role=\"region\" aria-label=\"Graph node display\"></div>\n" +
        "<script>\n" +
        "const NODES = __NODES_JSON__;\n" +
        "const CONTEXT = \"__CONTEXT__\";\n" +
        "let cur = 0, sel = 0, hist = [];\n" +
        "function speak(html){ const b=document.getElementById('box'); b.innerHTML='';\n" +
        "  setTimeout(()=>{ b.innerHTML=html; }, 50); }\n" +
        "function nbNames(n){ return n.nb.length ? n.nb.map(i=>NODES[i].l).join(', ') : 'none'; }\n" +
        "function inNames(n){ return n.in.length ? n.in.map(i=>NODES[i].l).join(', ') : 'none'; }\n" +
        "function view(){ const n=NODES[cur];\n" +
        "  let s=`<div class=\"val\">${n.l}.</div>`;\n" +
        "  if(n.dir){\n" +
        "    s+=`<div class=\"info\">Outgoing connections (\\u2192): ${nbNames(n)}.</div>`;\n" +
        "    if(n.in.length) s+=`<div class=\"info\">Incoming connections (\\u2190): ${inNames(n)}.</div>`;\n" +
        "  } else {\n" +
        "    s+=`<div class=\"info\">Connected to: ${nbNames(n)}.</div>`+\n" +
        "       `<div class=\"info\">${n.nb.length} connection${n.nb.length===1?'':'s'}.</div>`;\n" +
        "  }\n" +
        "  if(n.nb.length) s+=`<div class=\"info\">Selected: ${NODES[n.nb[sel]].l}. Press Enter to move there.</div>`;\n" +
        "  speak(s); }\n" +
        "document.getElementById('box').addEventListener('keydown', function(e){\n" +
        "  if(['ArrowLeft','ArrowRight','Enter','Backspace','PageUp','PageDown'].includes(e.key)) e.preventDefault();\n" +
        "  if(e.key==='h'||e.key==='H'){ const n=NODES[cur];\n" +
        "    let t='Node '+n.l+' has '+n.nb.length+' connection'+(n.nb.length===1?'':'s')+': '+nbNames(n)+'. '+\n" +
        "          'Use Left and Right to pick a connection, Enter to move to it, Backspace to go back.';\n" +
        "    if(CONTEXT) t+=' From the slide: '+CONTEXT;\n" +
        "    speak('<div class=\"info\">'+t+'</div>'); return; }\n" +
        "  const n=NODES[cur];\n" +
        "  switch(e.key){\n" +
        "    case 'ArrowRight': if(n.nb.length){ sel=(sel+1)%n.nb.length; view(); } break;\n" +
        "    case 'ArrowLeft':  if(n.nb.length){ sel=(sel-1+n.nb.length)%n.nb.length; view(); } break;\n" +
        "    case 'Enter': if(n.nb.length){ hist.push(cur); cur=n.nb[sel]; sel=0; view(); } break;\n" +
        "    case 'Backspace': if(hist.length){ cur=hist.pop(); sel=0; view(); }\n" +
        "      else speak('<div class=\"info\">No previous node.</div>'); break;\n" +
        "    case 'PageDown': cur=(cur+1)%NODES.length; sel=0; view(); break;\n" +
        "    case 'PageUp': cur=(cur-1+NODES.length)%NODES.length; sel=0; view(); break;\n" +
        "  }\n" +
        "});\n" +
        "window.onload=()=>{ document.getElementById('box').focus();\n" +
        "  speak('<div class=\"info\">Graph explorer ready, '+NODES.length+' nodes. Press Insert and Space for Focus Mode, then use the arrow keys to explore connections.</div>'); };\n" +
        "</script>\n</body>\n</html>\n";

    private static final String BTREE_TEMPLATE =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n" +
        "<title>__TITLE__</title>\n<style>\n" +
        "  body { font-family: monospace; font-size:14px; max-width:480px; margin:3em auto;\n" +
        "         background:#fff; color:#111; line-height:1.8; }\n" +
        "  #box { border:2px solid #333; padding:1.4em; margin-top:1.2em; outline:none; }\n" +
        "  #box:focus { border-color:#000; }\n" +
        "  .val  { font-size:24px; font-weight:bold; margin:0.2em 0; }\n" +
        "  .info { font-size:13px; margin:0.15em 0; }\n" +
        "  .keys { font-size:12px; color:#444; margin-top:1.2em; }\n" +
        "  .keys b { display:inline-block; min-width:120px; }\n" +
        "  .warn  { font-size:13px; background:#fffbe6; border:1px solid #ccc; padding:0.7em; margin-bottom:1em; }\n" +
        "  .vnote { font-size:13px; background:#e8f4fd; border:1px solid #90c8e8; padding:0.7em; margin-bottom:1em; }\n" +
        "</style>\n</head>\n<body>\n<h1>__TITLE__</h1>\n" +
        "<div class=\"warn\"><strong>NVDA users:</strong> press <strong>Insert + Space</strong> to switch to\n" +
        "  Focus Mode, then press <strong>Page Down</strong> to begin. Arrow keys move within the tree.</div>\n" +
        "<script>var __VN2__=\"__VALIDATION__\";" +
        "if(__VN2__)document.write('<div class=\"vnote\">'+__VN2__+'</div>');</script>\n" +
        "<div class=\"keys\">\n" +
        "  <div><b>Page Down</b> &mdash; next insertion step</div>\n" +
        "  <div><b>Page Up</b> &mdash; previous insertion step</div>\n" +
        "  <div><b>Up arrow</b> &mdash; go to parent node</div>\n" +
        "  <div><b>Down arrow</b> &mdash; go to first child</div>\n" +
        "  <div><b>Left arrow</b> &mdash; previous sibling</div>\n" +
        "  <div><b>Right arrow</b> &mdash; next sibling</div>\n" +
        "  <div><b>H</b> &mdash; help: explain this step in more detail</div>\n" +
        "</div>\n" +
        "<div id=\"box\" tabindex=\"0\" aria-live=\"assertive\" aria-atomic=\"true\"\n" +
        "     role=\"region\" aria-label=\"B-tree node display\"></div>\n" +
        "<script>\n" +
        "const STATES = __STATES_JSON__;\n" +
        "const CONTEXT = \"__CONTEXT__\";\n" +
        "function link(n,parent,siblings){ if(!n) return null; n.parent=parent; n.siblings=siblings;\n" +
        "  n.children.forEach(c=>link(c,n,n.children)); return n; }\n" +
        "STATES.forEach(s=>link(s.root,null,[s.root]));\n" +
        "let si=-1, current=null;\n" +
        "function speak(html){ const b=document.getElementById('box'); b.innerHTML='';\n" +
        "  setTimeout(()=>{ b.innerHTML=html; }, 50); }\n" +
        "function kv(n){ return n.keys.length? n.keys.join(', ') : 'empty'; }\n" +
        "function nodeView(n){\n" +
        "  const parent = n.parent ? 'keys '+kv(n.parent)+'.' : 'none. This is the root.';\n" +
        "  const nch = n.children.length;\n" +
        "  const childInfo = nch ? ('Down arrow goes to first child, keys '+kv(n.children[0])+'. '+nch+' child node'+(nch===1?'':'ren')+'.') : 'No children. This is a leaf.';\n" +
        "  const sibs = n.siblings ? n.siblings.length : 1;\n" +
        "  const sibInfo = sibs>1 ? ('Sibling '+(n.siblings.indexOf(n)+1)+' of '+sibs+'. Left and Right arrows move between siblings.') : 'No siblings.';\n" +
        "  return `<div class=\"val\">Node with keys: ${kv(n)}.</div>`+\n" +
        "         `<div class=\"info\">Parent: ${parent}</div>`+\n" +
        "         `<div class=\"info\">${childInfo}</div>`+\n" +
        "         `<div class=\"info\">${sibInfo}</div>`;\n" +
        "}\n" +
        "function stepIntro(){ const s=STATES[si];\n" +
        "  const ins = s.note ? (s.note+' ') : (s.inserted ? ('Inserted key '+s.inserted+'. ') : '');\n" +
        "  return `<p>Step ${si+1} of ${STATES.length}. ${ins}</p>`+nodeView(s.root); }\n" +
        "function helpText(){ const s=STATES[si]; if(si<0) return 'Press Page Down to load the first step.';\n" +
        "  let t='A B-tree node can hold up to '+s.max+' keys. When an insert would make a node exceed that, '+\n" +
        "        'the node splits in two and its middle key moves up to the parent - that is how the tree grows a level. ';\n" +
        "  if(s.split) t+='That is what happened at this step. '; \n" +
        "  t+='You are on step '+(si+1)+' of '+STATES.length+'. '+(s.note||'');\n" +
        "  if(CONTEXT) t+=' From the slide: '+CONTEXT;\n" +
        "  return t; }\n" +
        "document.getElementById('box').addEventListener('keydown', function(e){\n" +
        "  if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','PageUp','PageDown'].includes(e.key)) e.preventDefault();\n" +
        "  if(e.key==='h'||e.key==='H'){ speak('<div class=\"info\">'+helpText()+'</div>'); return; }\n" +
        "  if(si===-1 && e.key!=='PageDown'){ speak('<div class=\"info\">Press Page Down to load the first step.</div>'); return; }\n" +
        "  switch(e.key){\n" +
        "    case 'PageDown': if(si<STATES.length-1){ si++; current=STATES[si].root; speak(stepIntro()); }\n" +
        "      else speak('<div class=\"info\">Already on the last step.</div>'); break;\n" +
        "    case 'PageUp': if(si>0){ si--; current=STATES[si].root; speak(stepIntro()); }\n" +
        "      else speak('<div class=\"info\">Already on the first step.</div>'); break;\n" +
        "    case 'ArrowUp': if(current.parent){ current=current.parent; speak(nodeView(current)); }\n" +
        "      else speak('<div class=\"info\">You are at the root. No parent.</div>'); break;\n" +
        "    case 'ArrowDown': if(current.children.length){ current=current.children[0]; speak(nodeView(current)); }\n" +
        "      else speak('<div class=\"info\">No children. This is a leaf.</div>'); break;\n" +
        "    case 'ArrowLeft': { const s=current.siblings||[current]; const i=s.indexOf(current);\n" +
        "      if(i>0){ current=s[i-1]; speak(nodeView(current)); } else speak('<div class=\"info\">No previous sibling.</div>'); break; }\n" +
        "    case 'ArrowRight': { const s=current.siblings||[current]; const i=s.indexOf(current);\n" +
        "      if(i<s.length-1){ current=s[i+1]; speak(nodeView(current)); } else speak('<div class=\"info\">No next sibling.</div>'); break; }\n" +
        "  }\n" +
        "});\n" +
        "window.onload=()=>{ document.getElementById('box').focus();\n" +
        "  speak('<div class=\"info\">B-tree explorer ready, '+STATES.length+' steps. Press Insert and Space for Focus Mode, then Page Down to begin.</div>'); };\n" +
        "</script>\n</body>\n</html>\n";

    private static final String TEMPLATE =
        "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head>\n" +
        "<meta charset=\"UTF-8\">\n" +
        "<title>__TITLE__</title>\n" +
        "<style>\n" +
        "  body { font-family: monospace; font-size: 14px; max-width: 480px;\n" +
        "         margin: 3em auto; background:#fff; color:#111; line-height:1.8; }\n" +
        "  #box { border:2px solid #333; padding:1.4em; margin-top:1.2em; outline:none; }\n" +
        "  #box:focus { border-color:#000; }\n" +
        "  .val  { font-size:26px; font-weight:bold; margin:0.2em 0; }\n" +
        "  .info { font-size:13px; margin:0.15em 0; }\n" +
        "  .keys { font-size:12px; color:#444; margin-top:1.2em; }\n" +
        "  .keys b { display:inline-block; min-width:110px; }\n" +
        "  .warn  { font-size:13px; background:#fffbe6; border:1px solid #ccc;\n" +
        "           padding:0.7em; margin-bottom:1em; }\n" +
        "  .vnote { font-size:13px; background:#e8f4fd; border:1px solid #90c8e8;\n" +
        "           padding:0.7em; margin-bottom:1em; }\n" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "<h1>__TITLE__</h1>\n" +
        "<div class=\"warn\">\n" +
        "  <strong>NVDA users:</strong> After opening this page, press\n" +
        "  <strong>Insert + Space</strong> to switch NVDA to Focus Mode before\n" +
        "  using the arrow keys. You will hear \"Focus mode\" announced. The arrow\n" +
        "  keys will then control the tree instead of NVDA.\n" +
        "</div>\n" +
        "<!-- validation note: only rendered when non-empty -->\n" +
        "<script>var __VN__=\"__VALIDATION__\";" +
        "if(__VN__)document.write('<div class=\"vnote\">'+__VN__+'</div>');</script>\n" +
        "<div class=\"keys\">\n" +
        "  <div><b>Up arrow</b> &mdash; go to parent node</div>\n" +
        "  <div><b>Left arrow</b> &mdash; go to left child</div>\n" +
        "  <div><b>Right arrow</b> &mdash; go to right child</div>\n" +
        "  <div><b>Down arrow</b> &mdash; return to root node</div>\n" +
        "  <div><b>H</b> &mdash; help: describe this node in more detail</div>\n" +
        "</div>\n" +
        "<div id=\"box\" tabindex=\"0\" aria-live=\"assertive\" aria-atomic=\"true\"\n" +
        "     role=\"region\" aria-label=\"Tree node display\"></div>\n" +
        "<script>\n" +
        "const TREE = __TREE_JSON__;\n" +
        "const SUMMARY = \"__SUMMARY__\";\n" +
        "const CONTEXT = \"__CONTEXT__\";\n" +
        "function link(n, parent){ if(!n) return null; n.parent=parent;\n" +
        "  link(n.left,n); link(n.right,n); return n; }\n" +
        "const root = link(TREE, null);\n" +
        "let current = root;\n" +
        "function speak(html){ const b=document.getElementById('box');\n" +
        "  b.innerHTML=''; setTimeout(()=>{ b.innerHTML=html; }, 50); }\n" +
        "function nodeView(n){\n" +
        "  const parent = n.parent ? 'Node '+n.parent.v+'.' : 'none. This is the root.';\n" +
        "  const left   = n.left   ? 'Node '+n.left.v+'.'   : 'empty.';\n" +
        "  const right  = n.right  ? 'Node '+n.right.v+'.'  : 'empty.';\n" +
        "  return `<div class=\"val\">${n.c? n.c.charAt(0).toUpperCase()+n.c.slice(1)+' node ' : 'Node '}${n.v}.</div>`+\n" +
        "         `<div class=\"info\">Parent: ${parent}</div>`+\n" +
        "         `<div class=\"info\">Left child: ${left}</div>`+\n" +
        "         `<div class=\"info\">Right child: ${right}</div>`;\n" +
        "}\n" +
        "function helpView(n){\n" +
        "  const kids = (n.left?1:0)+(n.right?1:0);\n" +
        "  const role = n.parent ? (n===n.parent.left?'the left child of node '+n.parent.v:'the right child of node '+n.parent.v) : 'the root of the tree';\n" +
        "  const col = n.c ? ('This is a '+n.c+' node. ') : '';\n" +
        "  const ctx = CONTEXT ? ('<div class=\"info\">From the slide: '+CONTEXT+'</div>') : '';\n" +
        "  return `<div class=\"info\">${col}Node ${n.v} is ${role}. It has ${kids} child${kids===1?'':'ren'}. `+\n" +
        "         `Up arrow goes to the parent, Left and Right go to the children, Down returns to the root.</div>`+ctx;\n" +
        "}\n" +
        "document.getElementById('box').addEventListener('keydown', function(e){\n" +
        "  if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)) e.preventDefault();\n" +
        "  if(e.key==='h'||e.key==='H'){ speak(helpView(current)); return; }\n" +
        "  if(e.key==='c'||e.key==='C'){\n" +
        "    // C key: read slide context text, but only if present and concise\n" +
        "    if(CONTEXT && CONTEXT.length > 0 && CONTEXT.length <= 400){\n" +
        "      speak('<div class=\"info\">Slide context: '+CONTEXT+'</div>'); }\n" +
        "    else if(CONTEXT && CONTEXT.length > 400){\n" +
        "      speak('<div class=\"info\">Slide context: '+CONTEXT.slice(0,400)+'... (continues on slide)</div>'); }\n" +
        "    else{ speak('<div class=\"info\">No additional context available for this slide.</div>'); }\n" +
        "    return; }\n" +
        "  switch(e.key){\n" +
        "    case 'ArrowUp':\n" +
        "      if(current.parent){ current=current.parent; speak(nodeView(current)); }\n" +
        "      else speak(`<div class=\"val\">Node ${current.v}.</div><div class=\"info\">You cannot go higher. You are at the root.</div>`);\n" +
        "      break;\n" +
        "    case 'ArrowLeft':\n" +
        "      if(current.left){ current=current.left; speak(nodeView(current)); }\n" +
        "      else speak(`<div class=\"val\">Node ${current.v}.</div><div class=\"info\">No left child.</div>`);\n" +
        "      break;\n" +
        "    case 'ArrowRight':\n" +
        "      if(current.right){ current=current.right; speak(nodeView(current)); }\n" +
        "      else speak(`<div class=\"val\">Node ${current.v}.</div><div class=\"info\">No right child.</div>`);\n" +
        "      break;\n" +
        "    case 'ArrowDown':\n" +
        "      current=root; speak(nodeView(current)); break;\n" +
        "  }\n" +
        "});\n" +
        "window.onload = () => {\n" +
        "  document.getElementById('box').focus();\n" +
        "  speak(`<div class=\"info\">${SUMMARY} Press Insert and Space to enable Focus Mode in NVDA, then use the arrow keys. Down arrow announces the root.</div>`);\n" +
        "};\n" +
        "</script>\n" +
        "</body>\n" +
        "</html>\n";
}
