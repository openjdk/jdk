/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple class to represent the attribute and elements of an XML node.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class XMLNode {
    XMLNode(XMLNode parent, String qname) {
        this.parent = parent;
        name = qname;
        attrs = new HashMap<String,String>();
        children = new ArrayList<XMLNode>();

        if (parent != null)
            parent.children.add(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(name);
        for (Map.Entry<String,String> e: attrs.entrySet())
            sb.append(" " + e.getKey() + "=\"" + e.getValue() + "\"");
        if (children.size() == 0)
            sb.append("/>");
        else {
            sb.append(">");
            for (XMLNode c: children)
                sb.append(c.toString());
            sb.append("</" + name + ">");
        }
        return sb.toString();
    }

    final XMLNode parent;
    final String name;
    final Map<String,String> attrs;
    final List<XMLNode> children;
}
