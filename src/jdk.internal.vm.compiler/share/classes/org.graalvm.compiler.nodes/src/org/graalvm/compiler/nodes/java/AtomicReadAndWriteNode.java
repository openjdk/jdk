/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an atomic read-and-write operation like
 * {@link sun.misc.Unsafe#getAndSetInt(Object, long, int)}.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_2)
public final class AtomicReadAndWriteNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single {

    public static final NodeClass<AtomicReadAndWriteNode> TYPE = NodeClass.create(AtomicReadAndWriteNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    @Input ValueNode newValue;

    protected final JavaKind valueKind;
    protected final LocationIdentity locationIdentity;

    public AtomicReadAndWriteNode(ValueNode object, ValueNode offset, ValueNode newValue, JavaKind valueKind, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forKind(newValue.getStackKind()));
        this.object = object;
        this.offset = offset;
        this.newValue = newValue;
        this.valueKind = valueKind;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public ValueNode newValue() {
        return newValue;
    }

    public JavaKind getValueKind() {
        return valueKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }
}
