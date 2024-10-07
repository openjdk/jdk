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

import java.io.*;
import java.util.Formatter;
import java.util.Locale;
import java.util.UnknownFormatConversionException;

public class Basic {

    private static int fail = 0;

    private static int pass = 0;

    private static Throwable first;

    protected static void test(String fs, String exp, Object ... args) {
        Formatter f = new Formatter(new StringBuilder(), Locale.US);
        f.format(fs, args);
        ck(fs, exp, f.toString());

        f = new Formatter(new StringBuilder(), Locale.US);
        f.format("foo " + fs + " bar", args);
        ck(fs, "foo " + exp + " bar", f.toString());
    }

    protected static void test(Locale l, String fs, String exp, Object ... args)
    {
        Formatter f = new Formatter(new StringBuilder(), l);
        f.format(fs, args);
        ck(fs, exp, f.toString());

        f = new Formatter(new StringBuilder(), l);
        f.format("foo " + fs + " bar", args);
        ck(fs, "foo " + exp + " bar", f.toString());
    }

    protected static void test(String fs, Object ... args) {
        Formatter f = new Formatter(new StringBuilder(), Locale.US);
        f.format(fs, args);
        ck(fs, "fail", f.toString());
    }

    protected static void test(String fs) {
        Formatter f = new Formatter(new StringBuilder(), Locale.US);
        f.format(fs, "fail");
        ck(fs, "fail", f.toString());
    }

    protected static void testSysOut(String fs, String exp, Object ... args) {
        FileOutputStream fos = null;
        FileInputStream fis = null;
        try {
            PrintStream saveOut = System.out;
            fos = new FileOutputStream("testSysOut");
            System.setOut(new PrintStream(fos));
            System.out.format(Locale.US, fs, args);
            fos.close();

            fis = new FileInputStream("testSysOut");
            byte [] ba = new byte[exp.length()];
            int len = fis.read(ba);
            String got = new String(ba);
            if (len != ba.length)
                fail(fs, exp, got);
            ck(fs, exp, got);

            System.setOut(saveOut);
        } catch (FileNotFoundException ex) {
            fail(fs, ex.getClass());
        } catch (IOException ex) {
            fail(fs, ex.getClass());
        } finally {
            try {
                if (fos != null)
                    fos.close();
                if (fis != null)
                    fis.close();
            } catch (IOException ex) {
                fail(fs, ex.getClass());
            }
        }
    }

    protected static void tryCatch(String fs, Class<?> ex) {
        boolean caught = false;
        try {
            test(fs);
        } catch (Throwable x) {
            if (ex.isAssignableFrom(x.getClass()))
                caught = true;
        }
        if (!caught)
            fail(fs, ex);
        else
            pass();
    }

    protected static void tryCatch(String fs, Class<?> ex, Object ... args) {
        boolean caught = false;
        try {
            test(fs, args);
        } catch (Throwable x) {
            if (ex.isAssignableFrom(x.getClass()))
                caught = true;
        }
        if (!caught)
            fail(fs, ex);
        else
            pass();
    }

    private static void pass() {
        pass++;
    }

    private static void fail(String fs, Class ex) {
        String message = "'%s': %s not thrown".formatted(fs, ex.getName());
        if (first == null) {
            setFirst(message);
        }
        System.err.printf("FAILED: %s%n", message);
        fail++;
    }

    private static void fail(String fs, String exp, String got) {
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

    private static void ck(String fs, String exp, String got) {
        if (!exp.equals(got)) {
            fail(fs, exp, got);
        } else {
            pass();
        }
    }

    public static void main(String[] args) {
        common();

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

    private static void common() {
        // non-conversion
        tryCatch("%12", UnknownFormatConversionException.class);
        tryCatch("% ", UnknownFormatConversionException.class);
        tryCatch("%,", UnknownFormatConversionException.class);
        tryCatch("%03.2", UnknownFormatConversionException.class);
    }
}
