/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.instrumentation;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.instrumentation.InstrumentationNode;
import org.graalvm.compiler.nodes.debug.instrumentation.MonitorProxyNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

/**
 * The {@code MidTierReconcileInstrumentationPhase} reconciles the InstrumentationNodes right after
 * lowering in the mid tier. Concretely, it attempts to unproxify the targets of the
 * InstrumentationNodes.
 */
public class MidTierReconcileInstrumentationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (InstrumentationNode instrumentationNode : graph.getNodes().filter(InstrumentationNode.class)) {
            ValueNode target = instrumentationNode.getTarget();
            if (target instanceof MonitorProxyNode) {
                RawMonitorEnterNode monitorEnter = ((MonitorProxyNode) target).findFirstMatch();
                if (monitorEnter != null) {
                    instrumentationNode.replaceFirstInput(target, monitorEnter);
                } else {
                    // we cannot find valid AccessMonitorNode for the proxy, detach the
                    // instrumentation
                    graph.removeFixed(instrumentationNode);
                }
            } else if (target instanceof FixedValueAnchorNode) {
                instrumentationNode.replaceFirstInput(target, GraphUtil.unproxify(target));
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
