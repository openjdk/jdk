/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.memory.address;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an address that points to an element of a Java array.
 */
@NodeInfo(allowedUsageTypes = InputType.Association)
public class IndexAddressNode extends AddressNode implements Lowerable {
    public static final NodeClass<IndexAddressNode> TYPE = NodeClass.create(IndexAddressNode.class);

    @Input ValueNode array;
    @Input ValueNode index;

    private final JavaKind elementKind;

    public IndexAddressNode(ValueNode array, ValueNode index, JavaKind elementKind) {
        super(TYPE);
        this.array = array;
        this.index = index;
        this.elementKind = elementKind;
    }

    @Override
    public ValueNode getBase() {
        return array;
    }

    public ValueNode getArray() {
        return array;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return Long.MAX_VALUE;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }
}
