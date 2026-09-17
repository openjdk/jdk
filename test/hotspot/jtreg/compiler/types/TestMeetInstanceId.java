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
 *
 */
package compiler.types;

/*
 * @test
 * @bug 8370914
 * @summary C2 incorrectly computes the instance id of a meet, leading to symmetry assert.
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=1 -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIncrementalInlining -XX:TypeProfileLevel=200
 *                   -XX:CompileOnly=${test.main.class}::test ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileThreshold=1 -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIncrementalInlining -XX:TypeProfileLevel=200 -XX:StressSeed=823469094
 *                   -XX:CompileOnly=${test.main.class}::test ${test.main.class}
 */
public class TestMeetInstanceId {
    static Object getString() {
        return "42";
    }

    static Object profileObject(Object obj) {
        return obj;
    }

    static Object test(boolean b) {
        if (b) {
            return null;
        }
        return profileObject(getString());
    }

    public static void main(String[] args) {
        Object[] array = new Object[0];
        for (int i = 0; i < 20_000; i++) {
            profileObject(array);
        }

        test(true);
        test(true);
    }
}
