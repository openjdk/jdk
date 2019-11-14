/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.ea.EATestBase.TestClassInt;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UnsafeVirtualizationTest extends GraalCompilerTest {

    public static int unsafeSnippet1(double i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        return UNSAFE.getInt(a, TestClassInt.fieldOffset1) + UNSAFE.getInt(a, TestClassInt.fieldOffset2);
    }

    public static long unsafeSnippet2a(int i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        a.setFirstField(i1);
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet2b(int i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        a.setSecondField(i1);
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet3a(int i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putInt(a, TestClassInt.fieldOffset1, i1);
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet3b(int i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putInt(a, TestClassInt.fieldOffset2, i1);
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static int unsafeSnippet4(double i1) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        return UNSAFE.getInt(a, TestClassInt.fieldOffset1) + UNSAFE.getInt(a, TestClassInt.fieldOffset2);
    }

    @Test
    public void testUnsafePEA01() {
        testPartialEscapeReadElimination("unsafeSnippet1", false, 1.0);
        testPartialEscapeReadElimination("unsafeSnippet1", true, 1.0);
    }

    @Test
    public void testUnsafePEA02() {
        testPartialEscapeReadElimination("unsafeSnippet2a", false, 1);
        testPartialEscapeReadElimination("unsafeSnippet2a", true, 1);

        testPartialEscapeReadElimination("unsafeSnippet2b", false, 1);
        testPartialEscapeReadElimination("unsafeSnippet2b", true, 1);
    }

    @Test
    public void testUnsafePEA03() {
        testPartialEscapeReadElimination("unsafeSnippet3a", false, 1);
        testPartialEscapeReadElimination("unsafeSnippet3a", true, 1);

        testPartialEscapeReadElimination("unsafeSnippet3b", false, 1);
        testPartialEscapeReadElimination("unsafeSnippet3b", true, 1);
    }

    @Test
    public void testUnsafePEA04() {
        testPartialEscapeReadElimination("unsafeSnippet4", false, 1.0);
        testPartialEscapeReadElimination("unsafeSnippet4", true, 1.0);
    }

    public void testPartialEscapeReadElimination(String snippet, boolean canonicalizeBefore, Object... args) {
        assert TestClassInt.fieldOffset1 % 8 == 0 : "First of the two int-fields must be 8-byte aligned";

        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        OptionValues options = graph.getOptions();
        CoreProviders context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        if (canonicalizeBefore) {
            canonicalizer.apply(graph, context);
        }
        Result r = executeExpected(method, null, args);
        new PartialEscapePhase(true, true, canonicalizer, null, options).apply(graph, context);
        try {
            InstalledCode code = getCode(method, graph);
            Object result = code.executeVarargs(args);
            assertEquals(r, new Result(result, null));
        } catch (Throwable e) {
            assertFalse(true, e.toString());
        }
    }
}
