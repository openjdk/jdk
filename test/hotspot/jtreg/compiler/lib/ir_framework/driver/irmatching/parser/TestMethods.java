/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.parser;

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.hotspot.HotSpotPidFileParser;

import java.util.Map;

/**
 * This class stores all test methods that need to be IR matched as identified by {@link IREncodingParser}.
 *
 * @see IREncodingParser
 * @see HotSpotPidFileParser
 * @see IRMethod
 */
public class TestMethods {
    /**
     * "Method name" -> TestMethod map created by {@link IREncodingParser} which contains an entry for each method that
     * needs to be IR matched on.
     */
    private final Map<String, TestMethod> testMethods;

    public TestMethods(Map<String, TestMethod> testMethods) {
        this.testMethods = testMethods;
    }

    public Map<String, TestMethod> testMethods() {
        return testMethods;
    }

    public boolean isTestMethod(String method) {
        return testMethods.containsKey(method);
    }

    public boolean hasTestMethods() {
        return !testMethods.isEmpty();
    }
}
