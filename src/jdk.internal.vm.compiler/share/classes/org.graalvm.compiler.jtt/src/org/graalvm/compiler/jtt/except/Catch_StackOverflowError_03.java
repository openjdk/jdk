/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.except;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/**
 * Some basic checking of the stack trace produced after a StackOverflowError.
 */
public class Catch_StackOverflowError_03 extends JTTTest {

    private static final int PASS = 0;
    private static final int FAIL = 1;

    private static void recurseA() {
        recurseB();
    }

    private static void recurseB() {
        recurseA();
    }

    public static int test() {
        try {
            recurseA();
        } catch (StackOverflowError stackOverflowError) {
            // Check that a method does not appear to be calling itself in the stack trace
            // and check that recurse* is only called by either recurse* or test
            StackTraceElement[] elements = null;
            elements = stackOverflowError.getStackTrace();
            if (elements.length == 0) {
                // Not much we can do about this perfectly legal situation
                return PASS;
            }
            String lastMethodName = elements[0].getMethodName();
            for (int i = 1; i < elements.length; ++i) {
                String methodName = elements[i].getMethodName();

                // Skip top-of-stack until we find a method with name "recurse*".
                if (!methodName.startsWith("recurse")) {
                    continue;
                }

                // We reached the test method => done.
                if (methodName.equals("test")) {
                    break;
                }

                // Stack elements must alternate between recurseA and recurseB
                if (lastMethodName.equals(methodName) || (!methodName.equals("recurseA") && !methodName.equals("recurseB"))) {
                    return FAIL;
                }

                lastMethodName = methodName;
            }

            return PASS;
        }

        return FAIL;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
