/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.data;

import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.ChangedEventProvider;
import com.sun.hotspot.igv.data.Properties;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Group extends Properties.Entity implements ChangedEventProvider<Group> {

    private List<InputGraph> graphs;
    private transient ChangedEvent<Group> changedEvent;
    private GraphDocument document;
    private InputMethod method;
    private String assembly;

    public Group() {
        graphs = new ArrayList<InputGraph>();
        init();
    }

    private void init() {
        changedEvent = new ChangedEvent<Group>(this);
    }

    public void fireChangedEvent() {
        changedEvent.fire();
    }

    public void setAssembly(String s) {
        this.assembly = s;
    }

    public String getAssembly() {
        return assembly;
    }

    public void setMethod(InputMethod method) {
        this.method = method;
    }

    public InputMethod getMethod() {
        return method;
    }

    void setDocument(GraphDocument document) {
        this.document = document;
    }

    public GraphDocument getDocument() {
        return document;
    }

    public ChangedEvent<Group> getChangedEvent() {
        return changedEvent;
    }

    public List<InputGraph> getGraphs() {
        return Collections.unmodifiableList(graphs);
    }

    public void addGraph(InputGraph g) {
        assert g != null;
        assert !graphs.contains(g);
        graphs.add(g);
        changedEvent.fire();
    }

    public void removeGraph(InputGraph g) {
        int index = graphs.indexOf(g);
        if (index != -1) {
            graphs.remove(g);
            changedEvent.fire();
        }
    }

    public Set<Integer> getAllNodes() {
        Set<Integer> result = new HashSet<Integer>();
        for (InputGraph g : graphs) {
            Set<Integer> ids = g.getNodesAsSet();
            result.addAll(g.getNodesAsSet());
            for (Integer i : ids) {
                result.add(-i);
            }
        }
        return result;
    }

    public InputGraph getLastAdded() {
        if (graphs.size() == 0) {
            return null;
        }
        return graphs.get(graphs.size() - 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Group " + getProperties().toString() + "\n");
        for (InputGraph g : graphs) {
            sb.append(g.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getName() {
        return getProperties().get("name");
    }
}
