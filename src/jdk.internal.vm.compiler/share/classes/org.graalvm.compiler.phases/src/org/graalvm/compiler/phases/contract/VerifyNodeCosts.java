/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.contract;

import java.lang.reflect.Modifier;
import java.util.function.Predicate;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.phases.VerifyPhase;

/**
 * Utility class that verifies that every {@link Class} extending {@link Node} specifies non default
 * values for {@link NodeCycles} and {@link NodeSize} in its {@link NodeInfo} annotation.
 */
public class VerifyNodeCosts {

    public static void verifyNodeClass(Class<?> clazz) {
        Class<?> nodeClass = Node.class;
        if (nodeClass.isAssignableFrom(clazz)) {
            if (!clazz.isAnnotationPresent(NodeInfo.class)) {
                throw new VerifyPhase.VerificationError("%s.java extends Node.java but does not specify a NodeInfo annotation.", clazz.getName());
            }

            if (!Modifier.isAbstract(clazz.getModifiers())) {
                boolean cyclesSet = walkCHUntil(getType(clazz), getType(nodeClass), cur -> {
                    return cur.cycles() != NodeCycles.CYCLES_UNSET;
                });
                boolean sizeSet = walkCHUntil(getType(clazz), getType(nodeClass), cur -> {
                    return cur.size() != NodeSize.SIZE_UNSET;
                });
                if (!cyclesSet) {
                    throw new VerifyPhase.VerificationError("%s.java does not specify a NodeCycles value in its class hierarchy.", clazz.getName());
                }
                if (!sizeSet) {
                    throw new VerifyPhase.VerificationError("%s.java does not specify a NodeSize value in its class hierarchy.", clazz.getName());
                }
            }
        }
    }

    private static NodeClass<?> getType(Class<?> c) {
        try {
            return NodeClass.get(c);
        } catch (Throwable t) {
            throw new VerifyPhase.VerificationError("%s.java does not specify a TYPE field.", c.getName());
        }
    }

    private static boolean walkCHUntil(NodeClass<?> start, NodeClass<?> until, Predicate<NodeClass<?>> p) {
        NodeClass<?> cur = start;
        while (cur != until && cur != null) {
            if (p.test(cur)) {
                return true;
            }
            cur = cur.getSuperNodeClass();
        }
        return false;
    }

}
