/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package build.tools.jdwpgen;

import java.util.*;
import java.io.*;

class ConstantSetNode extends AbstractNamedNode {

    /**
     * The mapping between a constant and its value.
     */
    protected static final Map<String, String> constantMap = new HashMap<String, String>();

    void prune() {
        List<Node> addons = new ArrayList<Node>();

        for (Iterator it = components.iterator(); it.hasNext(); ) {
            Node node = (Node)it.next();
        }
        if (!addons.isEmpty()) {
            components.addAll(addons);
        }
        super.prune();
    }

    void constrainComponent(Context ctx, Node node) {
        if (node instanceof ConstantNode) {
            node.constrain(ctx);
            constantMap.put(name + "_" + ((ConstantNode) node).getName(), node.comment());
        } else {
            error("Expected 'Constant', got: " + node);
        }
    }

    void document(PrintWriter writer) {
        writer.println("<h4><a name=\"" + context.whereC + "\">" + name +
                       " Constants</a></h4>");
        writer.println(comment());
        writer.println("<dd><table border=1 cellpadding=3 cellspacing=0 width=\"90%\" summary=\"\"><tr>");
        writer.println("<th width=\"20%\"><th width=\"5%\"><th width=\"65%\">");
        ConstantNode n;
        for (Iterator it = components.iterator(); it.hasNext();) {
            n = ((ConstantNode)it.next());
            writer.println("<a NAME=\"" + name + "_" + n.name + "\"></a>");
            n.document(writer);
        }
        writer.println("</table>");
    }

    void documentIndex(PrintWriter writer) {
        writer.print("<li><a href=\"#" + context.whereC + "\">");
        writer.println(name() + "</a> Constants");
//        writer.println("<ul>");
//        for (Iterator it = components.iterator(); it.hasNext();) {
//            ((Node)it.next()).documentIndex(writer);
//        }
//        writer.println("</ul>");
    }

    void genJavaClassSpecifics(PrintWriter writer, int depth) {
    }

    void genJava(PrintWriter writer, int depth) {
        genJavaClass(writer, depth);
    }

    public static String getConstant(String key){
        String com = constantMap.get(key);
        if(com == null){
            return "";
        } else {
            return com;
        }
    }

}
