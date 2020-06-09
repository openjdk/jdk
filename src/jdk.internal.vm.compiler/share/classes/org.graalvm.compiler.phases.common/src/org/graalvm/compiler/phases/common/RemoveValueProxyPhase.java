/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.FrameStateVerification;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

public class RemoveValueProxyPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (LoopExitNode exit : graph.getNodes(LoopExitNode.TYPE)) {
            exit.removeProxies();
            FrameState frameState = exit.stateAfter();
            if (frameState != null && frameState.isExceptionHandlingBCI()) {
                // The parser will create loop exits with such BCIs on the exception handling path.
                // Loop optimizations must avoid duplicating such exits
                // We clean them up here otherwise they could survive until code generation
                exit.setStateAfter(null);
                GraphUtil.tryKillUnused(frameState);
            }
        }
        graph.setHasValueProxies(false);
        graph.weakenFrameStateVerification(FrameStateVerification.ALL_EXCEPT_LOOP_EXIT);
    }
}
