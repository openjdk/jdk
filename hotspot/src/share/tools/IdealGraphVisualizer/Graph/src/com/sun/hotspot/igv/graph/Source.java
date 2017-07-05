/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.hotspot.igv.graph;

import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Source {

    private List<InputNode> sourceNodes;
    private Set<Integer> set;

    public Source() {
        sourceNodes = new ArrayList<InputNode>(1);
    }

    public List<InputNode> getSourceNodes() {
        return Collections.unmodifiableList(sourceNodes);
    }

    public Set<Integer> getSourceNodesAsSet() {
        if (set == null) {
            set = new HashSet<Integer>();
            for (InputNode n : sourceNodes) {
                int id = n.getId();
                //if(id < 0) id = -id;
                set.add(id);
            }
        }
        return set;
    }

    public void addSourceNode(InputNode n) {
        sourceNodes.add(n);
        set = null;
    }

    public void removeSourceNode(InputNode n) {
        sourceNodes.remove(n);
        set = null;
    }

    public interface Provider {

        public Source getSource();
    }

    public void setSourceNodes(List<InputNode> sourceNodes) {
        this.sourceNodes = sourceNodes;
        set = null;
    }

    public void addSourceNodes(Source s) {
        for (InputNode n : s.getSourceNodes()) {
            sourceNodes.add(n);
        }
        set = null;
    }

    public boolean isInBlock(InputGraph g, InputBlock blockNode) {

        for (InputNode n : this.getSourceNodes()) {
            if (g.getBlock(n) == blockNode) {
                return true;
            }
        }
        return false;
    }
}
