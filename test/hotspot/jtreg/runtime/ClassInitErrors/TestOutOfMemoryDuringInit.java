/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309034
 * @summary Test that when saving a class initialization failure caused by
 *          an OutOfMemoryError, that we record the OOME as the underlying
 *          cause, even if we can't create the ExceptionInInitializerError
 *
 * @comment Enable logging to ease failure diagnosis
 * @run main/othervm -Xms64m -Xmx64m TestOutOfMemoryDuringInit
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedList;

public class TestOutOfMemoryDuringInit {

    static LinkedList<Object> theList = new LinkedList<>();

    static class Nested {
        static void forceInit() { }
        static {
            while (theList != null) {
                // Use the minimal allocation size to push heap occupation to
                // the limit, ensuring there is not enough memory to create the
                // ExceptionInInitializerError that the VM tries to create when
                // the clinit throws the OOM.
                theList.add(new Object());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String expected = "java.lang.NoClassDefFoundError: Could not initialize class TestOutOfMemoryDuringInit$Nested";
        // This cause will match either the shared OOME or the EIIE we get
        // with some GC's.
        String cause = "java.lang.OutOfMemoryError";

        try {
            Nested.forceInit();
        } catch (OutOfMemoryError oome) {
            theList = null; // free memory for verification process
            System.out.println("Trying to access class Nested ...");
            try {
                Nested.forceInit();
                throw new RuntimeException("NoClassDefFoundError was not thrown");
            } catch (NoClassDefFoundError ncdfe) {
                verify_stack(ncdfe, expected, cause);
            }
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
