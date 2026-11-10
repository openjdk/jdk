/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8396624
 * @summary C2 support for SHA3 doubleKeccak/quadKeccak
 *
 * @run main/othervm/timeout=600 -Xbatch
 *      -XX:CompileThreshold=500 -XX:+UnlockDiagnosticVMOptions
 *      --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *      -XX:-UseSHA3Intrinsics -Dmethod=doubleKeccak
 *      ${test.main.class}
 * @run main/othervm/timeout=600 -Xbatch
 *      -XX:CompileThreshold=500 -XX:+UnlockDiagnosticVMOptions
 *      --add-opens java.base/sun.security.provider=ALL-UNNAMED
 *      -XX:-UseSHA3Intrinsics -Dmethod=quadKeccak
 *      ${test.main.class}
 */

package compiler.intrinsics.sha;

import java.lang.reflect.Method;

public class TestInvoke {
    private static final String DOUBLE_KECCAK_METHOD_ID = "doubleKeccak"; /* SHA3 doubleKeccak */
    private static final String QUAD_KECCAK_METHOD_ID = "quadKeccak"; /* SHA3 quadKeccak */
    private static volatile int valSave;    /* store return value from method */

    public static void main(String[] args) throws Exception {
        String klass = "sun.security.provider.SHA3Parallel";
        String method = System.getProperty("method", DOUBLE_KECCAK_METHOD_ID);
        int iters = 20000;

        testInvoke(klass, method, 1, iters);
    }

    public static void testInvoke(String klass, String method, int expected,
                                  int iters) throws Exception {
        System.out.println("class = " + klass);
        System.out.println("method = " + method);

        try {
            Method m;
            if (method.equals(DOUBLE_KECCAK_METHOD_ID) == true) {
                m = Class.forName(klass).getDeclaredMethod(method,
                        long[].class, long[].class);
            } else if (method.equals(QUAD_KECCAK_METHOD_ID) == true) {
                m = Class.forName(klass).getDeclaredMethod(method,
                        long[].class, long[].class, long[].class, long[].class);
            } else {
                System.out.println("TestInvoke Error: invalid method provided!");
                throw new Exception("TestInvoke Error");
            }
            m.setAccessible(true);

            long[] buf0 = new long[25];
            int actual = 0;

            /* repeatedly get result */
            for (int i = 0; i < iters; i++) {
                if (method.equals(DOUBLE_KECCAK_METHOD_ID) == true) {
                    actual = (int) m.invoke(null, buf0, buf0);
                } else if (method.equals(QUAD_KECCAK_METHOD_ID) == true) {
                    actual = (int) m.invoke(null, buf0, buf0, buf0, buf0);
                }
                valSave = actual;
            }

            /* check result */
            if (actual != expected) {
                System.out.println("TestInvoke Error: ");
                System.out.println("expected = " + expected);
                System.out.println("actual = " + actual);
                throw new Exception("TestInvoke Error");
            }
        } catch (Exception e) {
            throw new Exception("Exception: " + e, e);
        }
    }
}
