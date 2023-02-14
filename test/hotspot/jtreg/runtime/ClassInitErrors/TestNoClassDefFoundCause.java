/*
 * Copyright (c) 2023, Azul Systems. All rights reserved.
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
 * @bug 1234567
 * @summary Test that StackOverflowError is correctly reporting in stack trace
 *          as underlying cause of NoClassDefFoundError
 * @run main/othervm -Xcomp -Xss256k TestNoClassDefFoundCause
 */
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;


public class TestNoClassDefFoundCause {

    static class CrashWithSOE {

        private static ClassLoader cl = ClassLoader.getSystemClassLoader();
        private static String className = "TestNoClassDefFoundCause$CantBeLoaded";
        private static CrashWithSOE b;

        static {
            try {
                b = new CrashWithSOE();
            } catch (Throwable tt) {
                b = null;
            }
        }

        public CrashWithSOE() throws Throwable {
            try {
                new CrashWithSOE();
            } catch (StackOverflowError se) {
                try {
                    Object inst = cl.loadClass(className).newInstance();
                } catch (Throwable e) {
                    throw e;
                }
            }
        }
    }

    private static void verify_stack(Throwable e, String cause) throws Exception {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteOS);
        e.printStackTrace(printStream);
        printStream.close();
        String stackTrace = byteOS.toString("ASCII");
        if (!stackTrace.contains(cause)) {
            throw new RuntimeException(" \"" + cause + "\" missing from stacktrace");
        }
    }

    public static void main(String args[]) throws Exception{
        try {
            CrashWithSOE b = new CrashWithSOE();
            throw new RuntimeException("Error: Expected exception wasn't thrown.");
        }catch (Throwable t){
            System.err.println("Check results:");
            verify_stack(t, "Caused by: java.lang.StackOverflowError");
            System.err.println("Exception stack trace for " + t.toString() + " is ok");
        }
    }
}
