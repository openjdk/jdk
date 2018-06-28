/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.sparc.SPARCJumpOp;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCNodeLIRBuilder extends NodeLIRBuilder {

    public SPARCNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen, SPARCNodeMatchRules nodeMatchRules) {
        super(graph, lirGen, nodeMatchRules);
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    protected JumpOp newJumpOp(LabelRef ref) {
        return new SPARCJumpOp(ref);
    }

    @Override
    public SPARCLIRGenerator getLIRGeneratorTool() {
        return (SPARCLIRGenerator) super.getLIRGeneratorTool();
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        getLIRGeneratorTool().emitLoadConstantTableBase();
        super.emitPrologue(graph);
    }
}
