/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.serviceprovider.JavaVersionUtil.Java8OrEarlier;

import java.lang.annotation.Annotation;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies a method is annotated with CallerSensitive iff it calls Reflection#getCallerClass().
 */
public class VerifyCallerSensitiveMethods extends VerifyPhase<PhaseContext> {

    Class<? extends Annotation> callerSensitiveClass;
    Class<?> reflectionClass;

    @Override
    public boolean checkContract() {
        return false;
    }

    @SuppressWarnings("unchecked")
    public VerifyCallerSensitiveMethods() {
        try {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            if (Java8OrEarlier) {
                reflectionClass = classLoader.loadClass("sun.reflect.Reflection");
                callerSensitiveClass = (Class<? extends Annotation>) classLoader.loadClass("sun.reflect.ConstantPool");
            } else {
                reflectionClass = classLoader.loadClass("jdk.internal.reflect.Reflection");
                callerSensitiveClass = (Class<? extends Annotation>) classLoader.loadClass("jdk.internal.reflect.ConstantPool");
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected void verify(StructuredGraph graph, PhaseContext context) {
        Invoke invoke = callsReflectionGetCallerClass(graph, context);
        Annotation annotation = graph.method().getAnnotation(callerSensitiveClass);
        if (invoke != null) {
            if (annotation == null) {
                StackTraceElement e = graph.method().asStackTraceElement(invoke.bci());
                throw new VerificationError(String.format("%s: method that calls Reflection.getCallerClass() must be annotated with @CallerSensitive", e));
            }

        } else if (annotation != null) {
            throw new VerificationError(String.format("%s: method annotated with @CallerSensitive does not call Reflection.getCallerClass()", graph.method().format("%H.%n(%p)")));
        }
    }

    private Invoke callsReflectionGetCallerClass(StructuredGraph graph, PhaseContext context) {
        ResolvedJavaType reflectionType = context.getMetaAccess().lookupJavaType(reflectionClass);
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(reflectionType)) {
                if (callee.getName().equals("getCallerClass")) {
                    return t.invoke();
                }
            }
        }
        return null;
    }
}
