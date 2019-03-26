/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;

public class DerivedConvertedInductionVariable extends DerivedInductionVariable {

    private final Stamp stamp;
    private final ValueNode value;

    public DerivedConvertedInductionVariable(LoopEx loop, InductionVariable base, Stamp stamp, ValueNode value) {
        super(loop, base);
        this.stamp = stamp;
        this.value = value;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public Direction direction() {
        return base.direction();
    }

    @Override
    public ValueNode initNode() {
        return IntegerConvertNode.convert(base.initNode(), stamp, graph(), NodeView.DEFAULT);
    }

    @Override
    public ValueNode strideNode() {
        return IntegerConvertNode.convert(base.strideNode(), stamp, graph(), NodeView.DEFAULT);
    }

    @Override
    public boolean isConstantInit() {
        return base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return base.constantInit();
    }

    @Override
    public long constantStride() {
        return base.constantStride();
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp s) {
        return base.extremumNode(assumeLoopEntered, s);
    }

    @Override
    public ValueNode exitValueNode() {
        return IntegerConvertNode.convert(base.exitValueNode(), stamp, graph(), NodeView.DEFAULT);
    }

    @Override
    public boolean isConstantExtremum() {
        return base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return base.constantExtremum();
    }

    @Override
    public void deleteUnusedNodes() {
    }

    @Override
    public String toString() {
        return String.format("DerivedConvertedInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), stamp);
    }
}
