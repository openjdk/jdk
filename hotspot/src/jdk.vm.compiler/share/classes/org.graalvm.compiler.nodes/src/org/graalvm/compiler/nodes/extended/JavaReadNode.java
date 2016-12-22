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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Read a raw memory location according to Java field or array read semantics. It will perform read
 * barriers, implicit conversions and optionally oop uncompression.
 */
@NodeInfo(nameTemplate = "JavaRead#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public final class JavaReadNode extends FixedAccessNode implements Lowerable, GuardingNode, Canonicalizable {

    public static final NodeClass<JavaReadNode> TYPE = NodeClass.create(JavaReadNode.class);
    protected final JavaKind readKind;
    protected final boolean compressible;

    public JavaReadNode(JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, boolean compressible) {
        super(TYPE, address, location, StampFactory.forKind(readKind), barrierType);
        this.readKind = readKind;
        this.compressible = compressible;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    public JavaKind getReadKind() {
        return readKind;
    }

    public boolean isCompressible() {
        return compressible;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return ReadNode.canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
    }
}
