/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code AccessIndexedNode} class is the base class of instructions that read or write elements
 * of an array.
 */
@NodeInfo
public abstract class AccessIndexedNode extends AccessArrayNode implements Lowerable {

    public static final NodeClass<AccessIndexedNode> TYPE = NodeClass.create(AccessIndexedNode.class);
    @Input protected ValueNode index;
    @OptionalInput(InputType.Guard) private GuardingNode boundsCheck;
    protected final JavaKind elementKind;

    public ValueNode index() {
        return index;
    }

    /**
     * Create an new AccessIndexedNode.
     *
     * @param stamp the result kind of the access
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param boundsCheck the explicit array bounds check already performed before the access, or
     *            null if no check was performed yet
     * @param elementKind the kind of the elements of the array
     */
    protected AccessIndexedNode(NodeClass<? extends AccessIndexedNode> c, Stamp stamp, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        super(c, stamp, array);
        this.index = index;
        this.boundsCheck = boundsCheck;
        this.elementKind = elementKind;
    }

    public GuardingNode getBoundsCheck() {
        return boundsCheck;
    }

    /**
     * Gets the element type of the array.
     *
     * @return the element type
     */
    public JavaKind elementKind() {
        return elementKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }
}
