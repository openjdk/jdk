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


package org.graalvm.compiler.nodes.java;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an atomic compare-and-swap operation. The result is the current value of the memory
 * location that was compared.
 */
@NodeInfo
public final class UnsafeCompareAndExchangeNode extends AbstractUnsafeCompareAndSwapNode {

    public static final NodeClass<UnsafeCompareAndExchangeNode> TYPE = NodeClass.create(UnsafeCompareAndExchangeNode.class);

    public UnsafeCompareAndExchangeNode(ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue, JavaKind valueKind, LocationIdentity locationIdentity) {
        super(TYPE, meetInputs(expected.stamp(NodeView.DEFAULT), newValue.stamp(NodeView.DEFAULT)), object, offset, expected, newValue, valueKind, locationIdentity);
    }

    private static Stamp meetInputs(Stamp expected, Stamp newValue) {
        assert expected.isCompatible(newValue);
        return expected.unrestricted().meet(newValue.unrestricted());
    }

    @Override
    protected void finishVirtualize(VirtualizerTool tool, LogicNode equalsNode, ValueNode currentValue) {
        tool.replaceWith(currentValue);
    }
}
