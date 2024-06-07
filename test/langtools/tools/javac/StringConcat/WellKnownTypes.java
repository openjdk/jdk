/*
 * Copyright (c) 2021, Google LLC. All rights reserved.
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
 * @bug     8273914
 * @summary Indy string concat changes order of operations
 * @enablePreview
 *
 * @compile -XDstringConcat=indy              WellKnownTypes.java
 * @run main WellKnownTypes
 *
 * @compile -XDstringConcat=indyWithConstants WellKnownTypes.java
 * @run main WellKnownTypes
 *
 * @compile -XDstringConcat=inline            WellKnownTypes.java
 * @run main WellKnownTypes
 */

public class WellKnownTypes {
    static int idx = 0;

    static boolean z = true;
    static char c = (char) 42;
    static byte b = (byte) 43;
    static short s = (short) 44;
    static int i = 45;
    static long l = 46L;
    static float f = 47.0f;
    static double d = 48.0;

    public static void main(String[] argv) throws Exception {
        test("" + WellKnownTypes.class, idx++, "class WellKnownTypes");
        test("" + Boolean.valueOf(z), idx++, "true");
        test("" + Character.valueOf(c), idx++, "*");
        test("" + Byte.valueOf(b), idx++, "43");
        test("" + Short.valueOf(s), idx++, "44");
        test("" + Integer.valueOf(i), idx++, "45");
        test("" + Long.valueOf(l), idx++, "46");
        test("" + Float.valueOf(f), idx++, "47.0");
        test("" + Double.valueOf(d), idx++, "48.0");
        test("" + z, idx++, "true");
        test("" + c, idx++, "*");
        test("" + b, idx++, "43");
        test("" + s, idx++, "44");
        test("" + i, idx++, "45");
        test("" + l, idx++, "46");
        test("" + f, idx++, "47.0");
        test("" + d, idx++, "48.0");
    }

    public static void test(String actual, int index, String expected) {
        if (!actual.equals(expected)) {
      throw new IllegalStateException(
          index + " Unexpected: expected = " + expected + ", actual = " + actual);
        }
    }
}
