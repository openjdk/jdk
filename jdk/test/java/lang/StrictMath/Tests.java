/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *
 * Shared static test method for StrictMath tests.
 */


public class Tests {
    private Tests(){}

    static int test(String testName,
                    double input,
                    double result,
                    double expected) {
        if (Double.compare(expected, result ) != 0) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input "   + input    + "\t(" + Double.toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\t(" + Double.toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + Double.toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    static int test(String testName, double input1,  double input2,
                    double result, double expected) {
        if (Double.compare(expected, result ) != 0) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input "   + input1   + "\t(" + Double.toHexString(input1) + "), " +
                                                + input2   + "\t(" + Double.toHexString(input2) + ")\n" +
                               "\texpected  " + expected + "\t(" + Double.toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + Double.toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }



}
