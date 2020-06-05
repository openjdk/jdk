/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "Deopt", nameTemplate = "Deopt {p#reason/s}")
public final class DeoptimizeNode extends AbstractDeoptimizeNode implements Lowerable, LIRLowerable, StaticDeoptimizingNode {
    public static final int DEFAULT_DEBUG_ID = 0;

    public static final NodeClass<DeoptimizeNode> TYPE = NodeClass.create(DeoptimizeNode.class);
    protected DeoptimizationAction action;
    protected DeoptimizationReason reason;
    protected int debugId;
    protected final Speculation speculation;

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason) {
        this(action, reason, DEFAULT_DEBUG_ID, SpeculationLog.NO_SPECULATION, null);
    }

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason, Speculation speculation) {
        this(action, reason, DEFAULT_DEBUG_ID, speculation, null);
    }

    public DeoptimizeNode(DeoptimizationAction action, DeoptimizationReason reason, int debugId, Speculation speculation, FrameState stateBefore) {
        super(TYPE, stateBefore);
        assert action != null;
        assert reason != null;
        this.action = action;
        this.reason = reason;
        this.debugId = debugId;
        assert speculation != null;
        this.speculation = speculation;
    }

    @Override
    public DeoptimizationAction getAction() {
        return action;
    }

    @Override
    public void setAction(DeoptimizationAction action) {
        this.action = action;
    }

    @Override
    public DeoptimizationReason getReason() {
        return reason;
    }

    @Override
    public void setReason(DeoptimizationReason reason) {
        this.reason = reason;
    }

    @SuppressWarnings("deprecation")
    public int getDebugId() {
        int deoptDebugId = debugId;
        if (deoptDebugId == DEFAULT_DEBUG_ID) {
            DebugContext debug = getDebug();
            if ((debug.isDumpEnabledForMethod() || debug.isLogEnabledForMethod())) {
                deoptDebugId = this.getId();
            }
        }
        return deoptDebugId;
    }

    public void setDebugId(int debugId) {
        assert debugId != DEFAULT_DEBUG_ID;
        this.debugId = debugId;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value actionAndReason = tool.emitJavaConstant(tool.getMetaAccess().encodeDeoptActionAndReason(action, reason, getDebugId()));
        Value speculationValue = tool.emitJavaConstant(tool.getMetaAccess().encodeSpeculation(speculation));
        gen.getLIRGeneratorTool().emitDeoptimize(actionAndReason, speculationValue, gen.state(this));
    }

    @Override
    public ValueNode getActionAndReason(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(metaAccess.encodeDeoptActionAndReason(action, reason, getDebugId()), metaAccess, graph());
    }

    @Override
    public ValueNode getSpeculation(MetaAccessProvider metaAccess) {
        return ConstantNode.forConstant(metaAccess.encodeSpeculation(speculation), metaAccess, graph());
    }

    @Override
    public Speculation getSpeculation() {
        return speculation;
    }

    @NodeIntrinsic
    public static native void deopt(@ConstantNodeParameter DeoptimizationAction action, @ConstantNodeParameter DeoptimizationReason reason);
}
