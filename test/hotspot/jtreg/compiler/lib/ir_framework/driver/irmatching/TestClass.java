/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the user defined test class with which the IR framework was started (the class containing all
 * the {@link Test @Test}/{@link IR @IR} annotated methods).
 *
 * @see Test
 * @see TestClassResult
 */
public class TestClass implements Matchable {
    /**
     * List of all test methods which contain at least one applicable @IR annotation.
     */
    private final List<IRMethod> irMethods;

    /**
     * Constructor for classes without @IR annotations.
     */
    public TestClass() {
        this.irMethods = new ArrayList<>();
    }

    public TestClass(List<IRMethod> irMethods) {
        TestFramework.check(!irMethods.isEmpty(), "must not be empty");
        this.irMethods = irMethods;
    }

    @Override
    public TestClassResult match() {
        TestClassResult result = new TestClassResult();
        for (IRMethod irMethod : irMethods) {
            IRMethodMatchResult IRMethodMatchResult = irMethod.match();
            if (IRMethodMatchResult.fail()) {
                result.addResult(IRMethodMatchResult);
            }
        }
        return result;
    }
}
