/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt;

import java.util.Collections;
import java.util.Set;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for the JTT tests.
 * <p>
 * These tests are executed twice: once with arguments passed to the execution and once with the
 * arguments bound to the test's parameters during compilation. The latter is a good test of
 * canonicalization.
 */
public class JTTTest extends GraalCompilerTest {

    public static final class DummyTestClass {
    }

    protected static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();
    /**
     * The arguments which, if non-null, will replace the Locals in the test method's graph.
     */
    Object[] argsToBind;

    public JTTTest() {
        Assert.assertNotNull(getCodeCache());
    }

    @Override
    protected Object[] getArgumentToBind() {
        return argsToBind;
    }

    /**
     * If non-null, then this is a test for a method returning a {@code double} value that must be
     * within {@code ulpDelta}s of the expected value.
     */
    protected Double ulpDelta;

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        if (ulpDelta != null) {
            double expectedDouble = (double) expected;
            double actualDouble = (Double) actual;
            double ulp = Math.ulp(expectedDouble);
            double delta = ulpDelta * ulp;
            try {
                Assert.assertEquals(expectedDouble, actualDouble, delta);
            } catch (AssertionError e) {
                double diff = Math.abs(expectedDouble - actualDouble);
                double diffUlps = diff / ulp;
                throw new AssertionError(e.getMessage() + " // " + diffUlps + " ulps");
            }
        } else {
            super.assertDeepEquals(expected, actual);
        }
    }

    protected void runTest(String name, Object... args) {
        runTest(getInitialOptions(), name, args);
    }

    protected void runTest(OptionValues options, String name, Object... args) {
        runTest(options, EMPTY, true, false, name, args);
    }

    protected void runTest(Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        runTest(getInitialOptions(), shouldNotDeopt, true, false, name, args);
    }

    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, boolean bind, boolean noProfile, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        if (noProfile) {
            method.reprofile();
        }

        testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
        if (args.length > 0 && bind) {
            if (noProfile) {
                method.reprofile();
            }

            this.argsToBind = args;
            testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
            this.argsToBind = null;
        }
    }
}
