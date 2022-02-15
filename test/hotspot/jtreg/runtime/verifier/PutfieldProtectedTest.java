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

/*
 * @test
 * @bug 8274714
 * @summary Make sure error message for protected putfield error is correct.
 * @compile putfieldProtected.jasm
 * @run main/othervm -Xverify:remote PutfieldProtectedTest
 */

// Test that an int[] is not assignable to byte[].
public class PutfieldProtectedTest {

    public static void main(String args[]) throws Throwable {
        try {
            Class newClass = Class.forName("other.putfieldProtected");
            throw new RuntimeException("Expected VerifyError exception not thrown");
        } catch (java.lang.VerifyError e) {
            if (!e.getMessage().contains("Bad access to protected data in putfield")) {
                throw new RuntimeException("wrong exception: " + e.getMessage());
            }
        }
    }
}
