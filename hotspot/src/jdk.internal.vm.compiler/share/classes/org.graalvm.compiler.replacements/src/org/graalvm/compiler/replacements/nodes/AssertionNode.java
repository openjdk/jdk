/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Assertion nodes will go away as soon as the value evaluates to true. Compile-time assertions will
 * fail if this has not happened by the time the node is lowered to LIR, while runtime assertions
 * may need to insert a check.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class AssertionNode extends FixedWithNextNode implements Lowerable, Canonicalizable, LIRLowerable {

    public static final NodeClass<AssertionNode> TYPE = NodeClass.create(AssertionNode.class);
    @Input ValueNode condition;

    protected final boolean compileTimeAssertion;
    protected final String message;

    public AssertionNode(boolean compileTimeAssertion, ValueNode condition, String message) {
        super(TYPE, StampFactory.forVoid());
        this.condition = condition;
        this.compileTimeAssertion = compileTimeAssertion;
        this.message = message;
    }

    public ValueNode condition() {
        return condition;
    }

    public String message() {
        return message;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (condition.isConstant() && condition.asJavaConstant().asInt() != 0) {
            return null;
        }
        /*
         * Assertions with a constant "false" value do not immediately cause an error, since they
         * may be unreachable and could thus be removed by later optimizations.
         */
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (!compileTimeAssertion) {
            if (GraalOptions.ImmutableCode.getValue(getOptions())) {
                // Snippet assertions are disabled for AOT
                graph().removeFixed(this);
            } else {
                tool.getLowerer().lower(this, tool);
            }
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        assert compileTimeAssertion;
        if (GraalOptions.ImmutableCode.getValue(getOptions())) {
            // Snippet assertions are disabled for AOT
            return;
        }
        if (condition.isConstant()) {
            if (condition.asJavaConstant().asInt() == 0) {
                throw new GraalError("%s: failed compile-time assertion: %s", this, message);
            }
        } else {
            throw new GraalError("%s: failed compile-time assertion (value %s): %s", this, condition, message);
        }
    }

    @NodeIntrinsic
    public static native void assertion(@ConstantNodeParameter boolean compileTimeAssertion, boolean condition, @ConstantNodeParameter String message);
}
