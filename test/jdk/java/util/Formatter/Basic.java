/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

public class Basic {

    private static int fail = 0;
    private static int pass = 0;

    private static Throwable first;

    static void pass() {
        pass++;
    }

    static void fail(String fs, Class ex) {
        String message = "'%s': %s not thrown".formatted(fs, ex.getName());
        if (first == null) {
            setFirst(message);
        }
        System.err.printf("FAILED: %s%n", message);
        fail++;
    }

    static void fail(String fs, String exp, String got) {
        String message = "'%s': Expected '%s', got '%s'".formatted(fs, exp, got);
        if (first == null) {
            setFirst(message);
        }
        System.err.printf("FAILED: %s%n", message);
        fail++;
    }

    private static void setFirst(String s) {
        try {
            throw new RuntimeException(s);
        } catch (RuntimeException x) {
            first = x;
        }
    }

    static void ck(String fs, String exp, String got) {
        if (!exp.equals(got)) {
            fail(fs, exp, got);
        } else {
            pass();
        }
    }

    public static void main(String[] args) {
        BasicBoolean.test();
        BasicBooleanObject.test();
        BasicByte.test();
        BasicByteObject.test();
        BasicChar.test();
        BasicCharObject.test();
        BasicShort.test();
        BasicShortObject.test();
        BasicInt.test();
        BasicIntObject.test();
        BasicLong.test();
        BasicLongObject.test();
        BasicBigInteger.test();
        BasicFloat.test();
        BasicFloatObject.test();
        BasicDouble.test();
        BasicDoubleObject.test();
        BasicBigDecimal.test();
        BasicDateTime.test();

        if (fail != 0) {
            var tests_message = "%d tests: %d failure(s)%n".formatted(fail + pass, fail);
            var trace_message = "Traceback of the first error located";
            var message = "%s %s".formatted(tests_message, trace_message);
            throw new RuntimeException(message, first);
        } else {
            System.out.printf("All %d tests passed", pass);
        }
    }
}
