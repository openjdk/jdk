/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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



package org.graalvm.compiler.core.aarch64;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.VolatileReadNode;
import org.graalvm.compiler.phases.Phase;

/**
 * AArch64-specific phase which substitutes certain read nodes with arch-specific variants in order
 * to allow merging of zero and sign extension into the read operation.
 */

public class AArch64ReadReplacementPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            // don't process nodes we just added
            if (node instanceof AArch64ReadNode) {
                continue;
            }
            if (node instanceof ReadNode && !(node instanceof VolatileReadNode)) {
                ReadNode readNode = (ReadNode) node;
                if (readNode.hasExactlyOneUsage()) {
                    Node usage = readNode.usages().first();
                    if (usage instanceof ZeroExtendNode || usage instanceof SignExtendNode) {
                        AArch64ReadNode.replace(readNode);
                    }
                }
            }
        }
    }
}
