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


package org.graalvm.compiler.replacements.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;

import static org.graalvm.compiler.core.common.GraalOptions.RemoveNeverExecutedCode;

/**
 * Tests compareTo method intrinsic.
 */
public class StringCompareToTest extends StringSubstitutionTestBase {

    // The compareTo() implementation in java.lang.String has 4 calls to compareTo implementation.
    private static final int EXPECT_NODE_COUNT = 4;
    private static final String DISABLE_COMPACTSTRINGS_FLAG = "-XX:-CompactStrings";

    public StringCompareToTest() {
        initSubstitution(
                        getResolvedJavaMethod(String.class, "compareTo", String.class),
                        getResolvedJavaMethod("stringCompareTo"),
                        ArrayCompareToNode.class);
    }

    private int countNode(ResolvedJavaMethod method, Class<?> expectedNode, OptionValues options) {
        StructuredGraph graph = parseForCompile(method, options);
        applyFrontEnd(graph);

        int c = 0;
        for (Node node : graph.getNodes()) {
            if (expectedNode.isInstance(node)) {
                c += 1;
            }
        }

        return c;
    }

    @Override
    protected void initSubstitution(ResolvedJavaMethod theRealMethod,
                    ResolvedJavaMethod theTestMethod, Class<?> expectedNode) {
        Assume.assumeTrue((getTarget().arch instanceof AMD64) || (getTarget().arch instanceof AArch64));

        realMethod = theRealMethod;
        testMethod = theTestMethod;

        StructuredGraph graph = testGraph(testMethod.getName());

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realMethod, -1, false, null);
        if (replacement == null) {
            assertInGraph(graph, expectedNode);
        }

        OptionValues options;
        boolean needCheckNode = true;

        if (GraalServices.Java8OrEarlier) {
            needCheckNode = false;
        } else {
            List<String> vmArgs = GraalServices.getInputArguments();
            Assume.assumeTrue(vmArgs != null);
            for (String vmArg : vmArgs) {
                if (vmArg.equals(DISABLE_COMPACTSTRINGS_FLAG)) {
                    needCheckNode = false;
                }
            }
        }

        if (needCheckNode) {
            options = new OptionValues(getInitialOptions(), RemoveNeverExecutedCode, false);
            Assert.assertEquals(EXPECT_NODE_COUNT, countNode(testMethod, expectedNode, options));
        } else {
            options = getInitialOptions();
        }

        // Force compilation.
        testCode = getCode(testMethod, options);
        Assert.assertNotNull(testCode);
    }

    public static int stringCompareTo(String a, String b) {
        return a.compareTo(b);
    }

    @Test
    @Override
    public void testEqualString() {
        super.testEqualString();
    }

    @Test
    @Override
    public void testDifferentString() {
        super.testDifferentString();
    }

    @Test
    @Override
    public void testAllStrings() {
        super.testAllStrings();
    }
}
