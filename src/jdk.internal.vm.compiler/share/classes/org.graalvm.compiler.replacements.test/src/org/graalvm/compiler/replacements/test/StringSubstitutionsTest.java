/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests {@link StringSubstitutions}.
 */
public class StringSubstitutionsTest extends MethodSubstitutionTest {

    public void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, boolean optional, Object[] args1, Object[] args2) {
        ResolvedJavaMethod realMethod = getResolvedJavaMethod(holder, methodName);
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(testMethodName);
        StructuredGraph graph = testGraph(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realMethod, -1, false, null);
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        // Force compilation
        InstalledCode code = getCode(testMethod);
        assert optional || code != null;

        for (int i = 0; i < args1.length; i++) {
            Object arg1 = args1[i];
            Object arg2 = args2[i];
            Object expected = invokeSafe(realMethod, arg1, arg2);
            // Verify that the original method and the substitution produce the same value
            assertDeepEquals(expected, invokeSafe(testMethod, null, arg1, arg2));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(expected, executeVarargsSafe(code, arg1, arg2));
        }
    }

    @Test
    public void testEquals() {
        if (!Java8OrEarlier) {
            // StringSubstitutions are disabled in 1.9
            return;
        }

        final int n = 1000;
        Object[] args1 = new Object[n];
        Object[] args2 = new Object[n];

        // equal strings
        String s1 = "";
        String s2 = "";
        for (int i = 0; i < n / 2; i++) {
            args1[i] = s1;
            args2[i] = s2;
            s1 = s1 + "0";
            s2 = s2 + "0";
        }

        // non-equal strings
        s1 = "";
        s2 = "";
        for (int i = n / 2; i < n; i++) {
            args1[i] = s1;
            args2[i] = s2;
            s2 = s1 + "1";
            s1 = s1 + "0";
        }

        testSubstitution("stringEquals", ArrayEqualsNode.class, String.class, "equals", false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean stringEquals(String a, String b) {
        return a.equals(b);
    }

}
