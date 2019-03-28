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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.services.Services;

/**
 * Checks against calls to {@link System#getProperty(String)},
 * {@link System#getProperty(String, String)} and {@link System#getProperties()}. System properties
 * can be modified by application code so {@link Services#getSavedProperties()} should be used
 * instead.
 */
public class VerifySystemPropertyUsage extends VerifyPhase<PhaseContext> {

    static final Class<?>[] BOXES = {Integer.class, Long.class, Boolean.class, Float.class, Double.class};
    static final int JVMCI_VERSION_MAJOR;
    static final int JVMCI_VERSION_MINOR;
    static {
        int major = -1;
        int minor = -1;
        String vmVersion = System.getProperty("java.vm.version");
        if (System.getProperty("java.specification.version").compareTo("1.9") < 0) {
            Pattern re = Pattern.compile(".*-jvmci-(\\d+)\\.(\\d+).*");
            Matcher matcher = re.matcher(vmVersion);
            if (matcher.matches()) {
                major = Integer.parseInt(matcher.group(1));
                minor = Integer.parseInt(matcher.group(2));
            }
        }
        JVMCI_VERSION_MAJOR = major;
        JVMCI_VERSION_MINOR = minor;
    }

    @Override
    protected void verify(StructuredGraph graph, PhaseContext context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        final ResolvedJavaType systemType = metaAccess.lookupJavaType(System.class);
        final ResolvedJavaType[] boxTypes = new ResolvedJavaType[BOXES.length];
        for (int i = 0; i < boxTypes.length; i++) {
            boxTypes[i] = metaAccess.lookupJavaType(BOXES[i]);
        }

        ResolvedJavaMethod caller = graph.method();
        String holderQualified = caller.format("%H");
        String holderUnqualified = caller.format("%h");
        String packageName = holderQualified.equals(holderUnqualified) ? "" : holderQualified.substring(0, holderQualified.length() - holderUnqualified.length() - 1);
        if (packageName.startsWith("jdk.vm.ci")) {
            if (JVMCI_VERSION_MAJOR >= 0 && JVMCI_VERSION_MINOR > 56) {
                // This JVMCI version should not use non-saved system properties
            } else {
                // This JVMCI version still has some calls that need to be removed
                return;
            }
        } else if (holderQualified.equals("org.graalvm.compiler.hotspot.JVMCIVersionCheck") && caller.getName().equals("main")) {
            // The main method in JVMCIVersionCheck is only called from the shell
            return;
        } else if (packageName.startsWith("com.oracle.truffle") || packageName.startsWith("org.graalvm.polyglot")) {
            // Truffle and Polyglot do not depend on JVMCI so cannot use
            // Services.getSavedProperties()
            return;
        } else if (packageName.startsWith("com.oracle.svm")) {
            // SVM must read system properties in:
            // * its JDK substitutions to mimic required JDK semantics
            // * native-image for config info
            return;
        }
        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            if (callee.getDeclaringClass().equals(systemType)) {
                if (callee.getName().equals("getProperty") || callee.getName().equals("getProperties")) {
                    throw new VerificationError("Call to %s at callsite %s is prohibited. Call Services.getSavedProperties().get(String) instead.",
                                    callee.format("%H.%n(%p)"),
                                    caller.format("%H.%n(%p)"));
                }
            } else {
                for (int i = 0; i < boxTypes.length; i++) {
                    ResolvedJavaType boxType = boxTypes[i];
                    if (callee.getDeclaringClass().equals(boxType)) {
                        String simpleName = boxType.toJavaName(false);
                        if (callee.getName().equals("get" + simpleName)) {
                            throw new VerificationError("Call to %s at callsite %s is prohibited. Call %s.parse%s(Services.getSavedProperties().get(String)) instead.",
                                            callee.format("%H.%n(%p)"),
                                            caller.format("%H.%n(%p)"),
                                            simpleName, simpleName);
                        }
                    }
                }
            }
        }
    }

}
