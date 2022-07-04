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

/**
 * @test
 * @bug 8048190
 * @summary Test that the NCDFE saves the stack trace for the original exception
 *          during class initialization with ExceptionInInitializationError,
 *          and doesn't prevent the classes in the stacktrace to be unloaded.
 * @requires vm.opt.final.ClassUnloading
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmn8m -XX:+UnlockDiagnosticVMOptions -Xlog:class+unload -XX:+WhiteBoxAPI InitExceptionUnloadTest
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import sun.hotspot.WhiteBox;
import jdk.test.lib.classloader.ClassUnloadCommon;

public class InitExceptionUnloadTest {
    static public class ThrowsRuntimeException { static int x = 1/0; }
    static public class ThrowsError { static { if (true) throw new Error(); } }
    static public class SpecialException extends RuntimeException {
        SpecialException(int count, String message) {
            super(message + count);
        }
    }
    static public class ThrowsSpecialException {
        static {
            if (true) throw new SpecialException(3, "Very Special ");
        }
    }

    static public class ThrowsOOM {
        static {
            if (true) {
                // Actually getting an OOM might be fragile but it was tested.
                throw new OutOfMemoryError("Java heap space");
            }
        }
    }

    private static void verify_stack(Throwable e, String expected, String cause) throws Exception {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteOS);
        e.printStackTrace(printStream);
        printStream.close();
        String stackTrace = byteOS.toString("ASCII");
        if (!stackTrace.contains(expected) || (cause != null && !stackTrace.contains(cause))) {
            throw new RuntimeException(expected + " and " + cause + " missing from stacktrace");
        }
    }

    static String[] expected = new String[] {
        "java.lang.ExceptionInInitializerError",
        "Caused by: java.lang.ArithmeticException: / by zero",
        "java.lang.NoClassDefFoundError: Could not initialize class InitExceptionUnloadTest$ThrowsRuntimeException",
        "Caused by: java.lang.ExceptionInInitializerError: Exception java.lang.ArithmeticException: / by zero [in thread",
        "java.lang.Error",
        null,
        "java.lang.NoClassDefFoundError: Could not initialize class InitExceptionUnloadTest$ThrowsError",
        "Caused by: java.lang.ExceptionInInitializerError: Exception java.lang.Error [in thread",
        "java.lang.ExceptionInInitializerError",
        "Caused by: InitExceptionUnloadTest$SpecialException: Very Special 3",
        "java.lang.NoClassDefFoundError: Could not initialize class InitExceptionUnloadTest$ThrowsSpecialException",
        "Caused by: java.lang.ExceptionInInitializerError: Exception InitExceptionUnloadTest$SpecialException: Very Special 3",
        "java.lang.OutOfMemoryError",
        "Java heap space",
        "java.lang.NoClassDefFoundError: Could not initialize class InitExceptionUnloadTest$ThrowsOOM",
        "Caused by: java.lang.ExceptionInInitializerError: Exception java.lang.OutOfMemoryError: Java heap space [in thread"
    };

    static String[] classNames = new String[] {
        "InitExceptionUnloadTest$ThrowsRuntimeException",
        "InitExceptionUnloadTest$ThrowsError",
        "InitExceptionUnloadTest$ThrowsSpecialException",
        "InitExceptionUnloadTest$ThrowsOOM" };

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    static void test() throws Throwable {
        ClassLoader cl = ClassUnloadCommon.newClassLoader();
        int i = 0;
        for (String className : classNames) {
            for (int tries = 2; tries-- > 0; ) {
                System.err.println("--- try to load " + className);
                try {
                    Class<?> c = cl.loadClass(className);
                    Object inst = c.newInstance();
                } catch (Throwable t) {
                    t.printStackTrace();
                    System.err.println();
                    System.err.println("Check results");
                    verify_stack(t, expected[i], expected[i+1]);
                    i += 2;
                    System.err.println();
                }
            }
        }
        cl = null;
        ClassUnloadCommon.triggerUnloading();  // should unload these classes
        for (String className : classNames) {
          ClassUnloadCommon.failIf(wb.isClassAlive(className), "should be unloaded");
        }
    }
    public static void main(java.lang.String[] unused) throws Throwable {
        test();
        test();
    }
}
