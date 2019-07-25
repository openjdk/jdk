/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

public class ConditionalEliminationPiTest extends ConditionalEliminationTestBase {

    static int SideEffect;

    static double oracleValue1 = -0.0;
    static double oracleValue2;

    public static double testSnippet1(int a) {
        double phi;
        if (a > 0) {
            double oracle = oracleValue1;
            if (oracle == 0.0) {
                SideEffect = 1;
            } else {
                return 123;
            }
            phi = oracle;
        } else {
            double oracle = oracleValue2;
            if (oracle == 0.0) {
                SideEffect = 1;
                phi = oracle;
            } else {
                return 0;
            }
        }
        if (Double.doubleToRawLongBits(phi) == Double.doubleToRawLongBits(-0.0)) {
            return 12;
        }
        return 2;
    }

    @Test
    public void test1() {
        test("testSnippet1", 1);
    }
}
