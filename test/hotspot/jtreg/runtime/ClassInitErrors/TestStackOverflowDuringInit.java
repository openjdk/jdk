/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309034 8334545
 * @summary Test that when saving a class initialization failure caused by
 *          a StackOverflowError, that we record the SOE as the underlying
 *          cause, even if we can't create the ExceptionInInitializerError
 * @comment This test could easily be perturbed so don't allow flag settings.
 * @requires vm.flagless
 * @comment Run with the smallest stack possible to limit the execution time.
 *          This is the smallest stack that is supported by all platforms.
 * @run main/othervm -Xss384K -Xint TestStackOverflowDuringInit
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class TestStackOverflowDuringInit {

    // The setup for this is somewhat intricate. We need to trigger a
    // StackOverflowError during execution of the static initializer
    // for a class, but we need there to be insufficient stack left
    // for the creation of the ExceptionInInitializerError that would
    // occur in that case. So we can't just recurse in a static initializer
    // as that would unwind all the way allowing plenty of stack for the
    // EIIE. Instead we recurse outside of a static initializer context
    // and have a finally clause that will trigger class initialization
    // of another class, which is where we will fail to create the EIIE.
    // Even then this is non-trivial, only the use of Long.valueOf from
    // the original reproducer seems to trigger SOE in just the right places.
    // Later changes to the JDK meant that LongCache was initialized before
    // the test even started under jtreg so we define local versions.

    static class LongCache {
        // Must have a static initializer
        static {
            System.out.println("LongCache is initializing");
        }
        static java.lang.Long valueOf(long l) {
            return Long.valueOf(l);
        }
    }

    static class MyLong {
        static java.lang.Long valueOf(long l) {
            if (l > -128 && l < 127) {
                return LongCache.valueOf(l);
            } else {
                return Long.valueOf(l);
            }
        }
    }

    static void recurse() {
        try {
            // This will initialize MyLong but not touch LongCache.
            MyLong.valueOf(1024L);
            recurse();
        } finally {
            // This will require initializing LongCache, which will
            // initially fail due to StackOverflowError and so LongCache
            // will be marked erroneous. As we unwind and again execute this
            // we will throw NoClassDefFoundError due to the erroneous
            // state of LongCache.
            MyLong.valueOf(0);
        }
    }

    public static void main(String[] args) throws Exception {
        String expected = "java.lang.NoClassDefFoundError: Could not initialize class TestStackOverflowDuringInit$LongCache";
        String cause = "Caused by: java.lang.StackOverflowError";

        // Pre-load, but not initialize, LongCache, else we will
        // hit SOE during class loading.
        System.out.println("Pre-loading ...");
        Class<?> c = Class.forName("TestStackOverflowDuringInit$LongCache",
                                   false,
                                   TestStackOverflowDuringInit.class.getClassLoader());
        try {
            recurse();
        } catch (Throwable ex) {
            //            ex.printStackTrace();
            verify_stack(ex, expected, cause);
        }
    }

    private static void verify_stack(Throwable e, String expected, String cause) throws Exception {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(byteOS)) {
            e.printStackTrace(printStream);
        }
        String stackTrace = byteOS.toString("ASCII");
        System.out.println(stackTrace);
        if (!stackTrace.contains(expected) ||
            (cause != null && !stackTrace.contains(cause))) {
            throw new RuntimeException(expected + " and/or " + cause + " missing from stacktrace");
        }
    }
}
