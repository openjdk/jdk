/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;

/**
 * Base class for nodes that modify a range of an array.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public abstract class ArrayRangeWriteNode extends AbstractMemoryCheckpoint {

    public static final NodeClass<ArrayRangeWriteNode> TYPE = NodeClass.create(ArrayRangeWriteNode.class);

    protected ArrayRangeWriteNode(NodeClass<? extends ArrayRangeWriteNode> c, Stamp stamp) {
        super(c, stamp);
    }

    /**
     * The array that is written to.
     */
    public abstract ValueNode getArray();

    /**
     * The first modified index.
     */
    public abstract ValueNode getIndex();

    /**
     * The length of the modified range.
     */
    public abstract ValueNode getLength();

    /**
     * Return true if the written array is an object array, false if it is a primitive array.
     */
    public abstract boolean isObjectArray();

    /**
     * Returns whether this write is the initialization of the written location. If it is true, the
     * old value of the memory location is either uninitialized or zero. If it is false, the memory
     * location is guaranteed to contain a valid value or zero.
     */
    public abstract boolean isInitialization();
}
