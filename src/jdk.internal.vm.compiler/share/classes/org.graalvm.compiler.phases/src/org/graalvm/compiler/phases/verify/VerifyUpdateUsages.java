/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.verify;

import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.Input;
import org.graalvm.compiler.graph.Node.OptionalInput;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Try to ensure that methods which update {@link Input} or {@link OptionalInput} fields also
 * include a call to {@link Node#updateUsages} or {@link Node#updateUsagesInterface}.
 */
public class VerifyUpdateUsages extends VerifyPhase<PhaseContext> {

    @Override
    public boolean checkContract() {
        return false;
    }

    public VerifyUpdateUsages() {
    }

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        if (graph.method().isConstructor()) {
            return true;
        }
        /*
         * There are only two acceptable patterns for methods which update Node inputs, either a
         * single StoreField node and invoke of updateUsages or updateUsagesInterface, or 2
         * StoreFields that come from LoadFields on the same object. Other patterns can be added as
         * needed but it would be best to keep things simple so that verification can be simple.
         */
        List<StoreFieldNode> stores = graph.getNodes().filter(StoreFieldNode.class).snapshot();
        ResolvedJavaType declaringClass = graph.method().getDeclaringClass();
        ResolvedJavaType nodeInputList = context.getMetaAccess().lookupJavaType(NodeInputList.class);
        StoreFieldNode storeField1 = null;
        StoreFieldNode storeField2 = null;
        for (StoreFieldNode store : stores) {
            if (isNodeInput(store.field(), declaringClass, nodeInputList)) {
                if (storeField1 == null) {
                    storeField1 = store;
                } else if (storeField2 == null) {
                    storeField2 = store;
                } else {
                    return false;
                }
            }
        }
        if (storeField1 == null) {
            return true;
        }
        if (storeField2 == null) {
            // Single input field update so just check for updateUsages or updateUsagesInterface
            // call
            ResolvedJavaType node = context.getMetaAccess().lookupJavaType(Node.class);
            for (MethodCallTargetNode call : graph.getNodes().filter(MethodCallTargetNode.class)) {
                ResolvedJavaMethod callee = call.targetMethod();
                if (callee.getDeclaringClass().equals(node) && (callee.getName().equals("updateUsages") || callee.getName().equals("updateUsagesInterface"))) {
                    return true;
                }
            }
        } else {
            if (storeField1.value() instanceof LoadFieldNode && storeField2.value() instanceof LoadFieldNode) {
                LoadFieldNode load1 = (LoadFieldNode) storeField1.value();
                LoadFieldNode load2 = (LoadFieldNode) storeField2.value();
                // Check for swapping values within the same object
                if (load1.object() == storeField1.object() && load2.object() == storeField2.object() && storeField1.object() == storeField2.object() &&
                                load1.field().equals(storeField2.field()) && load2.field().equals(storeField1.field())) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isNodeInput(ResolvedJavaField field, ResolvedJavaType declaringClass, ResolvedJavaType nodeInputList) {
        return declaringClass.isAssignableFrom(field.getDeclaringClass()) && (field.getAnnotation(Input.class) != null || field.getAnnotation(OptionalInput.class) != null) &&
                        !field.getType().equals(nodeInputList);
    }
}
