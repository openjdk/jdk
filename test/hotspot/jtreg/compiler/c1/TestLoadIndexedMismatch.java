/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test
 * @bug 8313402
 * @summary C1: Incorrect LoadIndexed value numbering
 * @requires vm.compiler1.enabled
 * @library /compiler/patches /test/lib
 * @build java.base/java.lang.Helper
 * @run main/othervm -Xbatch -XX:CompileThreshold=100
 *                   -XX:TieredStopAtLevel=1
 *                   compiler.c1.TestLoadIndexedMismatch
 */

package compiler.c1;

public class TestLoadIndexedMismatch {
    static final byte[] ARR = {42, 42};
    static final char EXPECTED_CHAR = (char)(42*256 + 42);

    public static char work() {
        // LoadIndexed (B)
        byte b = ARR[0];
        // StringUTF16.getChar intrinsic, LoadIndexed (C)
        char c = Helper.getChar(ARR, 0);
        return c;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            char c = work();
            if (c != EXPECTED_CHAR) {
                throw new IllegalStateException("Read: " + (int)c + ", expected: " + (int)EXPECTED_CHAR);
            }
        }
    }
}
