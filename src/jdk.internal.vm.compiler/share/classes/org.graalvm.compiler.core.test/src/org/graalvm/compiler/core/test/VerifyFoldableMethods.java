/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that all {@link Fold} annotated methods have at least one caller.
 */
public class VerifyFoldableMethods extends VerifyPhase<PhaseContext> {

    @Override
    public boolean checkContract() {
        return false;
    }

    private final Map<ResolvedJavaMethod, Boolean> foldables = new ConcurrentHashMap<>();
    ResolvedJavaType generatedInvocationPluginType;

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        ResolvedJavaMethod method = graph.method();
        if (method.getAnnotation(Fold.class) != null) {
            foldables.putIfAbsent(method, false);
        } else {
            if (generatedInvocationPluginType == null) {
                generatedInvocationPluginType = context.getMetaAccess().lookupJavaType(GeneratedInvocationPlugin.class);
            }
            if (!generatedInvocationPluginType.isAssignableFrom(method.getDeclaringClass())) {
                for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    ResolvedJavaMethod callee = t.targetMethod();
                    if (callee.getAnnotation(Fold.class) != null) {
                        foldables.put(callee, true);
                    }
                }
            }
        }
        return true;
    }

    public void finish() {
        String uncalled = foldables.entrySet().stream().filter(e -> e.getValue() == false).map(e -> e.getKey().format("%H.%n(%p)")).collect(Collectors.joining(System.lineSeparator() + "  "));
        if (uncalled.length() != 0) {
            throw new VerificationError(String.format("Methods annotated with @" + Fold.class.getSimpleName() + " appear to have no usages:%n  %s", uncalled));
        }
    }
}
