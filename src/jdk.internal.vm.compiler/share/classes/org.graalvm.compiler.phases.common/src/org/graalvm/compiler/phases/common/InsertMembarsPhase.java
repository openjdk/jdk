/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.VolatileReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.phases.Phase;

import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;

public class InsertMembarsPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        for (FixedAccessNode access : graph.getNodes(FixedAccessNode.TYPE)) {
            if (access instanceof VolatileReadNode) {
                ReadNode read = (ReadNode) access;
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
                graph.addBeforeFixed(read, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
                graph.addAfterFixed(read, postMembar);
            } else if (access instanceof WriteNode && ((WriteNode) access).isVolatile()) {
                WriteNode write = (WriteNode) access;
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
                graph.addBeforeFixed(write, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
                graph.addAfterFixed(write, postMembar);
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 3f;
    }
}
