/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumRecursiveInlining;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.replacements.nodes.MethodHandleNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodHandlePlugin implements NodePlugin {
    private final MethodHandleAccessProvider methodHandleAccess;
    private final boolean safeForDeoptimization;

    public MethodHandlePlugin(MethodHandleAccessProvider methodHandleAccess, boolean safeForDeoptimization) {
        this.methodHandleAccess = methodHandleAccess;
        this.safeForDeoptimization = safeForDeoptimization;
    }

    private static int countRecursiveInlining(GraphBuilderContext b, ResolvedJavaMethod method) {
        int count = 0;
        for (GraphBuilderContext c = b.getParent(); c != null; c = c.getParent()) {
            if (method.equals(c.getMethod())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        IntrinsicMethod intrinsicMethod = methodHandleAccess.lookupMethodHandleIntrinsic(method);
        if (intrinsicMethod != null) {
            InvokeKind invokeKind = b.getInvokeKind();
            if (invokeKind != InvokeKind.Static) {
                args[0] = b.nullCheckedValue(args[0]);
            }
            StampPair invokeReturnStamp = b.getInvokeReturnStamp(b.getAssumptions());
            MethodHandleNode.GraphAdder adder = new MethodHandleNode.GraphAdder(b.getGraph()) {
                @Override
                public <T extends ValueNode> T add(T node) {
                    return b.add(node);
                }
            };
            InvokeNode invoke = MethodHandleNode.tryResolveTargetInvoke(adder, methodHandleAccess, intrinsicMethod, method, b.bci(), invokeReturnStamp, args);
            if (invoke == null) {
                MethodHandleNode methodHandleNode = new MethodHandleNode(intrinsicMethod, invokeKind, method, b.bci(), invokeReturnStamp, args);
                if (invokeReturnStamp.getTrustedStamp().getStackKind() == JavaKind.Void) {
                    b.add(methodHandleNode);
                } else {
                    b.addPush(invokeReturnStamp.getTrustedStamp().getStackKind(), methodHandleNode);
                }
            } else {
                CallTargetNode callTarget = invoke.callTarget();
                NodeInputList<ValueNode> argumentsList = callTarget.arguments();
                for (int i = 0; i < argumentsList.size(); ++i) {
                    argumentsList.initialize(i, b.append(argumentsList.get(i)));
                }

                boolean inlineEverything = false;
                if (safeForDeoptimization) {
                    // If a MemberName suffix argument is dropped, the replaced call cannot
                    // deoptimized since the necessary frame state cannot be reconstructed.
                    // As such, it needs to recursively inline everything.
                    inlineEverything = args.length != argumentsList.size();
                }
                ResolvedJavaMethod targetMethod = callTarget.targetMethod();
                if (inlineEverything && !targetMethod.hasBytecodes()) {
                    // we need to force-inline but we can not, leave the invoke as-is
                    return false;
                }

                int recursionDepth = countRecursiveInlining(b, targetMethod);
                int maxRecursionDepth = MaximumRecursiveInlining.getValue(b.getOptions());
                if (recursionDepth > maxRecursionDepth) {
                    return false;
                }

                b.handleReplacedInvoke(invoke.getInvokeKind(), targetMethod, argumentsList.toArray(new ValueNode[argumentsList.size()]), inlineEverything);
            }
            return true;
        }
        return false;
    }
}
