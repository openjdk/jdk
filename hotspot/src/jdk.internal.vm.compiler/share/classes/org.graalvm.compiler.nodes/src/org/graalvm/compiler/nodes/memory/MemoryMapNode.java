/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */
package org.graalvm.compiler.nodes.memory;

import static org.graalvm.compiler.nodeinfo.InputType.Extension;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;
import static org.graalvm.word.LocationIdentity.any;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.util.Equivalence;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.MapCursor;
import org.graalvm.word.LocationIdentity;

@NodeInfo(allowedUsageTypes = {Extension, Memory}, cycles = CYCLES_0, size = SIZE_0)
public final class MemoryMapNode extends FloatingNode implements MemoryMap, MemoryNode, LIRLowerable {

    public static final NodeClass<MemoryMapNode> TYPE = NodeClass.create(MemoryMapNode.class);
    protected final List<LocationIdentity> locationIdentities;
    @Input(Memory) NodeInputList<ValueNode> nodes;

    private boolean checkOrder(EconomicMap<LocationIdentity, MemoryNode> mmap) {
        for (int i = 0; i < locationIdentities.size(); i++) {
            LocationIdentity locationIdentity = locationIdentities.get(i);
            ValueNode n = nodes.get(i);
            assertTrue(mmap.get(locationIdentity) == n, "iteration order of keys differs from values in input map");
        }
        return true;
    }

    public MemoryMapNode(EconomicMap<LocationIdentity, MemoryNode> mmap) {
        super(TYPE, StampFactory.forVoid());
        int size = mmap.size();
        locationIdentities = new ArrayList<>(size);
        nodes = new NodeInputList<>(this, size);
        int index = 0;
        MapCursor<LocationIdentity, MemoryNode> cursor = mmap.getEntries();
        while (cursor.advance()) {
            locationIdentities.add(cursor.getKey());
            nodes.initialize(index, (ValueNode) cursor.getValue());
            index++;
        }
        assert checkOrder(mmap);
    }

    public boolean isEmpty() {
        if (locationIdentities.isEmpty()) {
            return true;
        }
        if (locationIdentities.size() == 1) {
            if (nodes.get(0) instanceof StartNode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MemoryNode getLastLocationAccess(LocationIdentity locationIdentity) {
        if (locationIdentity.isImmutable()) {
            return null;
        } else {
            int index = locationIdentities.indexOf(locationIdentity);
            if (index == -1) {
                index = locationIdentities.indexOf(any());
            }
            assert index != -1;
            return (MemoryNode) nodes.get(index);
        }
    }

    @Override
    public Collection<LocationIdentity> getLocations() {
        return locationIdentities;
    }

    public EconomicMap<LocationIdentity, MemoryNode> toMap() {
        EconomicMap<LocationIdentity, MemoryNode> res = EconomicMap.create(Equivalence.DEFAULT, locationIdentities.size());
        for (int i = 0; i < nodes.size(); i++) {
            res.put(locationIdentities.get(i), (MemoryNode) nodes.get(i));
        }
        return res;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // nothing to do...
    }
}
