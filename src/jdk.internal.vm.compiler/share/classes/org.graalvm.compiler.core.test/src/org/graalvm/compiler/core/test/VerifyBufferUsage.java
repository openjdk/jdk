/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.serviceprovider.BufferUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * See {@link BufferUtil}.
 */
public class VerifyBufferUsage extends VerifyPhase<CoreProviders> {

    private final Set<String> bufferTypes = new HashSet<>(Arrays.asList(
                    "Ljava/nio/Buffer;",
                    "Ljava/nio/ByteBuffer;",
                    "Ljava/nio/ShortBuffer;",
                    "Ljava/nio/CharBuffer;",
                    "Ljava/nio/IntBuffer;",
                    "Ljava/nio/LongBuffer;",
                    "Ljava/nio/FloatBuffer;",
                    "Ljava/nio/DoubleBuffer;",
                    "Ljava/nio/MappedByteBuffer;"));

    private final Set<String> bufferMethods = new HashSet<>(Arrays.asList(
                    "position",
                    "limit",
                    "mark",
                    "reset",
                    "clear",
                    "flip",
                    "rewind"));

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        ResolvedJavaMethod caller = graph.method();
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String calleeClassName = callee.getDeclaringClass().getName();
            String calleeName = callee.getName();
            if (bufferTypes.contains(calleeClassName) &&
                            bufferMethods.contains(calleeName) &&
                            !callee.getSignature().getReturnKind().isPrimitive()) {
                StackTraceElement e = caller.asStackTraceElement(t.invoke().bci());
                ResolvedJavaType receiverType = ((ObjectStamp) t.arguments().get(0).stamp(NodeView.DEFAULT)).type();
                if (!receiverType.getName().equals("Ljava/nio/Buffer;")) {
                    throw new VerificationError(
                                    "%s: Cast receiver of type %s to java.nio.Buffer for call to %s to avoid problems with co-variant overloads added by https://bugs.openjdk.java.net/browse/JDK-4774077",
                                    e, receiverType.toJavaName(),
                                    callee.format("%H.%n(%p)"));
                }
            }
        }
    }
}
