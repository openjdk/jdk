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

package compiler.lib.ir_framework.test;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.shared.TestRunException;

import java.lang.reflect.Method;

/**
 * This class represents a @Test method.
 */
public class DeclaredTest {
    private final Method testMethod;
    private final ArgumentsProvider argumentsProvider;
    private final int warmupIterations;
    private final CompLevel compLevel;
    private Method attachedMethod;

    public DeclaredTest(Method testMethod, ArgumentsProvider argumentsProvider, CompLevel compLevel, int warmupIterations) {
        // Make sure we can also call non-public or public methods in package private classes
        testMethod.setAccessible(true);
        this.testMethod = testMethod;
        this.compLevel = compLevel;
        this.argumentsProvider = argumentsProvider;
        this.warmupIterations = warmupIterations;
        this.attachedMethod = null;
    }

    public Method getTestMethod() {
        return testMethod;
    }

    public CompLevel getCompLevel() {
        return compLevel;
    }

    public int getWarmupIterations() {
        return warmupIterations;
    }

    public Object[] getArguments(Object invocationTarget, int invocationCounter) {
        return argumentsProvider.getArguments(invocationTarget, invocationCounter);
    }

    public void setAttachedMethod(Method m) {
        attachedMethod = m;
    }

    public Method getAttachedMethod() {
        return attachedMethod;
    }

    /**
     * Format an array of arguments to string for error reporting.
     */
    public String formatArguments(Object[] arguments) {
        if (arguments == null) {
            return "<null>";
        }
        if (arguments.length == 0) {
            return "<void>";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            builder.append("arg ").append(i).append(": ").append(arguments[i]).append(", ");
        }
        builder.setLength(builder.length() - 2);
        return builder.toString();
    }

    public Object invoke(Object obj, Object... args) {
        try {
            return testMethod.invoke(obj, args);
        } catch (Exception e) {
            throw new TestRunException("There was an error while invoking @Test method " + testMethod, e);
        }
    }
}

