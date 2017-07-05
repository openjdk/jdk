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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class GraphDocument extends Properties.Entity implements ChangedEventProvider<GraphDocument> {

    private List<Group> groups;
    private ChangedEvent<GraphDocument> changedEvent;

    public GraphDocument() {
        groups = new ArrayList<Group>();
        changedEvent = new ChangedEvent<GraphDocument>(this);
    }

    public void clear() {
        groups.clear();
        getChangedEvent().fire();
    }

    public ChangedEvent<GraphDocument> getChangedEvent() {
        return changedEvent;
    }

    public List<Group> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public void addGroup(Group group) {
        group.setDocument(this);
        groups.add(group);
        getChangedEvent().fire();
    }

    public void removeGroup(Group group) {
        if (groups.contains(group)) {
            group.setDocument(null);
            groups.remove(group);
            getChangedEvent().fire();
        }
    }

    public void addGraphDocument(GraphDocument document) {
        for (Group g : document.groups) {
            this.addGroup(g);
        }
        document.clear();
        getChangedEvent().fire();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("GraphDocument: " + getProperties().toString() + " \n\n");
        for (Group g : getGroups()) {
            sb.append(g.toString());
            sb.append("\n\n");
        }

        return sb.toString();
    }
}
