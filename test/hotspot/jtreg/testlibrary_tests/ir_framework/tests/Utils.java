/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests;

import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.driver.IRMatcher;
import compiler.lib.ir_framework.driver.TestVMProcess;
import jdk.test.lib.Asserts;

import java.util.Arrays;

public class Utils {
    public static void shouldHaveThrownException(String s) {
        // Do not throw an exception if we hit a safepoint while printing which could possibly let the IR matching fail.
        // This happens very rarely. If there is a problem with the test, then we will catch that on the next test invocation.
        if (!s.contains(IRMatcher.SAFEPOINT_WHILE_PRINTING_MESSAGE)) {
            Asserts.fail("Should have thrown exception");
        }
    }

    /**
     * Is there at least one scenario which hit a safepoint while printing (i.e. a bailout)?
     */
    public static boolean anyBailedOut(Scenario... scenarios) {
        return Arrays.stream(scenarios).anyMatch(s -> s.getTestVMOutput().contains(IRMatcher.SAFEPOINT_WHILE_PRINTING_MESSAGE));
    }

    /**
     * Is there at least one scenario which did not hit a safepoint while printing (i.e. a bailout)?
     */
    public static boolean notAllBailedOut(Scenario... scenarios) {
        return Arrays.stream(scenarios).anyMatch(s -> !s.getTestVMOutput().contains(IRMatcher.SAFEPOINT_WHILE_PRINTING_MESSAGE));
    }
}
