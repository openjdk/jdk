/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.sparc;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.sparc.SPARCIndexedAddressValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;

/**
 * Represents an address of the form [base + index].
 */
@NodeInfo
public class SPARCIndexedAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<SPARCIndexedAddressNode> TYPE = NodeClass.create(SPARCIndexedAddressNode.class);

    @Input private ValueNode base;
    @Input private ValueNode index;

    public SPARCIndexedAddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        SPARCLIRGenerator tool = (SPARCLIRGenerator) gen.getLIRGeneratorTool();

        AllocatableValue baseValue = tool.asAllocatable(gen.operand(base));
        AllocatableValue indexValue = tool.asAllocatable(gen.operand(index));

        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        AllocatableValue indexReference = LIRKind.derivedBaseFromValue(indexValue);

        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp()), baseReference, indexReference);
        gen.setResult(this, new SPARCIndexedAddressValue(kind, baseValue, indexValue));
    }

    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
    }

    public ValueNode getIndex() {
        return index;
    }

    public void setIndex(ValueNode index) {
        updateUsages(this.index, index);
        this.index = index;
    }
}
