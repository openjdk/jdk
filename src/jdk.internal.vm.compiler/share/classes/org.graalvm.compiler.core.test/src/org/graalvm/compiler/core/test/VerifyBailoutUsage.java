/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VerifyBailoutUsage extends VerifyPhase<PhaseContext> {

    private static final String[] AllowedPackagePrefixes;

    private static String getPackageName(Class<?> c) {
        String classNameWithPackage = c.getName();
        String simpleName = c.getSimpleName();
        return classNameWithPackage.substring(0, classNameWithPackage.length() - simpleName.length() - 1);
    }

    static {
        try {
            AllowedPackagePrefixes = new String[]{
                            getPackageName(PermanentBailoutException.class),
                            "jdk.vm.ci",

                            // Allows GraalTruffleRuntime.handleAnnotationFailure to throw
                            // a BailoutException since the org.graalvm.compiler.truffle.runtime
                            // project can not see the PermanentBailoutException or
                            // RetryableBailoutException types.
                            "org.graalvm.compiler.truffle.runtime"
            };
        } catch (Throwable t) {
            throw new GraalError(t);
        }
    }

    private static boolean matchesPrefix(String packageName) {
        for (String allowedPackagePrefix : AllowedPackagePrefixes) {
            if (packageName.startsWith(allowedPackagePrefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, PhaseContext context) {
        final ResolvedJavaType bailoutType = context.getMetaAccess().lookupJavaType(BailoutException.class);
        ResolvedJavaMethod caller = graph.method();
        String holderQualified = caller.format("%H");
        String holderUnqualified = caller.format("%h");
        String packageName = holderQualified.substring(0, holderQualified.length() - holderUnqualified.length() - 1);
        if (!matchesPrefix(packageName)) {
            for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = t.targetMethod();
                if (callee.getDeclaringClass().equals(bailoutType)) {
                    // we only allow the getter
                    if (!callee.getName().equals("isPermanent")) {
                        throw new VerificationError("Call to %s at callsite %s is prohibited. Consider using %s for permanent bailouts or %s for retryables.", callee.format("%H.%n(%p)"),
                                        caller.format("%H.%n(%p)"), PermanentBailoutException.class.getName(),
                                        RetryableBailoutException.class.getName());
                    }
                }
            }
        }
    }

}
