/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;

/**
 * SPARC only supports 32 and 64 bit integer compare.
 *
 * This phase turns {@link CompareNode}s which have {@link IntegerStamp} as input and its bit-width
 * is not 32 or 64 bit into either a 32 or 64 bit compare by sign extending the input values.
 *
 * Why we do this in the HIR instead in the LIR? This enables the pattern matcher
 * {@link SPARCNodeMatchRules#signExtend(SignExtendNode, org.graalvm.compiler.nodes.memory.Access)}
 * to do it's job and replace loads with sign extending ones.
 */
public class SPARCIntegerCompareCanonicalizationPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof CompareNode) {
                CompareNode enode = (CompareNode) n;
                min32(enode, enode.getX());
                min32(enode, enode.getY());
            }
        }
    }

    private static void min32(CompareNode enode, ValueNode v) {
        Stamp s = v.stamp();
        if (s instanceof IntegerStamp) {
            int bits = ((IntegerStamp) s).getBits();
            if (bits != 32 && bits != 64) {
                if (bits <= 32) {
                    bits = 32;
                } else {
                    bits = 64;
                }
                ValueNode replacement;
                if (v instanceof ConstantNode) {
                    JavaConstant newConst;
                    if (bits == 32) {
                        newConst = JavaConstant.forInt(v.asJavaConstant().asInt());
                    } else if (bits == 64) {
                        newConst = JavaConstant.forLong(v.asJavaConstant().asLong());
                    } else {
                        throw GraalError.shouldNotReachHere();
                    }
                    long mask = CodeUtil.mask(bits);
                    replacement = v.graph().addOrUnique(new ConstantNode(newConst, IntegerStamp.stampForMask(bits, newConst.asLong() & mask, newConst.asLong() & mask)));
                } else {
                    replacement = v.graph().addOrUnique(new SignExtendNode(v, bits));
                }
                v.replaceAtUsages(replacement, x -> x == enode);
            }
        }
    }
}
