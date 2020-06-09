/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Test;

/**
 * Test extracted from reproducer in https://github.com/oracle/graal/issues/2493.
 */
public class IfCanonicalizerSwapTest extends GraalCompilerTest {
    public static String testSnippet1(long value) {
        if (injectBranchProbability(0.50, value >= 0L) && injectBranchProbability(0.00, value <= 35L)) {
            return "JustRight";
        } else {
            if (injectBranchProbability(0.50, value > 35L)) {
                return "TooHot";
            } else {
                return "TooCold";
            }
        }
    }

    @Test
    public void test1() {
        test("testSnippet1", -1L);
        test("testSnippet1", 100L);
        test("testSnippet1", 10L);
    }
}
