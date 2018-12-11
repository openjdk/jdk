/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Access the value of a specific register.
 */
@NodeInfo(nameTemplate = "ReadRegister %{p#register}", cycles = CYCLES_0, size = SIZE_0)
public final class ReadRegisterNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<ReadRegisterNode> TYPE = NodeClass.create(ReadRegisterNode.class);
    /**
     * The fixed register to access.
     */
    protected final Register register;

    /**
     * When true, subsequent uses of this node use the fixed register; when false, the value is
     * moved into a new virtual register so that the fixed register is not seen by uses.
     */
    protected final boolean directUse;

    /**
     * When true, this node is also an implicit definition of the value for the register allocator,
     * i.e., the register is an implicit incoming value; when false, the register must be defined in
     * the same method or must be an register excluded from register allocation.
     */
    protected final boolean incoming;

    public ReadRegisterNode(Register register, JavaKind kind, boolean directUse, boolean incoming) {
        super(TYPE, StampFactory.forKind(kind));
        assert register != null;
        this.register = register;
        this.directUse = directUse;
        this.incoming = incoming;
    }

    public ReadRegisterNode(@InjectedNodeParameter Stamp stamp, Register register, boolean directUse, boolean incoming) {
        super(TYPE, stamp);
        assert register != null;
        this.register = register;
        this.directUse = directUse;
        this.incoming = incoming;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        Value result = register.asValue(kind);
        if (incoming) {
            generator.getLIRGeneratorTool().emitIncomingValues(new Value[]{result});
        }
        if (!directUse) {
            result = generator.getLIRGeneratorTool().emitMove(result);
        }
        generator.setResult(this, result);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "%" + register;
        } else {
            return super.toString(verbosity);
        }
    }
}
