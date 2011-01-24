/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

/*
 * @test
 * @bug     4202914 4363318 6991528
 * @summary Basic test of serialization of stack trace information
 * @author  Josh Bloch
 */

public class StackTraceSerialization {
    public static void main(String args[]) throws Exception {
        testWithSetStackTrace();
        testWithFillInStackTrace();
    }

    private static void testWithSetStackTrace() throws Exception {
        Throwable t = new Throwable();

        t.setStackTrace(new StackTraceElement[]
            {new StackTraceElement("foo", "bar", "baz", -1)});

        if (!equal(t, reconstitute(t)))
            throw new Exception("Unequal Throwables with set stacktrace");
    }

    private static void assertEmptyStackTrace(Throwable t) {
        if (t.getStackTrace().length != 0)
            throw new AssertionError("Nonempty stacktrace.");
    }

    private static void testWithFillInStackTrace() throws Exception {
        Throwable original = null;
        try {
            a();
        } catch(HighLevelException e) {
            original = e;
        }

        if (!equal(original, reconstitute(original)))
            throw new Exception("Unequal Throwables with filled-in stacktrace");
    }


    /**
     * Serialize the argument and return the deserialized result.
     */
    private static Throwable reconstitute(Throwable t) throws Exception {
        Throwable result = null;

        try(ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout)) {
            out.writeObject(t);
            out.flush();
            try(ByteArrayInputStream bin =
                new ByteArrayInputStream(bout.toByteArray());
                ObjectInputStream in = new ObjectInputStream(bin)) {
                result = (Throwable) in.readObject();
            }
        }

        return result;
    }

    /**
     * Returns true if e1 and e2 have equal stack traces and their
     * causes are recursively equal (by the same definition) and their
     * suppressed exception information is equals.  Returns false or
     * throws NullPointerExeption otherwise.
     */
    private static boolean equal(Throwable t1, Throwable t2) {
        return t1==t2 ||
            (Arrays.equals(t1.getStackTrace(), t2.getStackTrace()) &&
             equal(t1.getCause(), t2.getCause()) &&
             Objects.equals(t1.getSuppressed(), t2.getSuppressed()));
    }

    static void a() throws HighLevelException {
        try {
            b();
        } catch(MidLevelException e) {
            throw new HighLevelException(e);
        }
    }
    static void b() throws MidLevelException {
        c();
    }
    static void c() throws MidLevelException {
        try {
            d();
        } catch(LowLevelException e) {
            throw new MidLevelException(e);
        }
    }
    static void d() throws LowLevelException {
       e();
    }
    static void e() throws LowLevelException {
        throw new LowLevelException();
    }

    private static final String OUR_CLASS  = StackTraceSerialization.class.getName();
    private static final String OUR_FILE_NAME = "StackTraceSerialization.java";

    private static void check(StackTraceElement e, String methodName, int n) {
        if (!e.getClassName().equals(OUR_CLASS))
            throw new RuntimeException("Class: " + e);
        if (!e.getMethodName().equals(methodName))
            throw new RuntimeException("Method name: " + e);
        if (!e.getFileName().equals(OUR_FILE_NAME))
            throw new RuntimeException("File name: " + e);
        if (e.getLineNumber() != n)
            throw new RuntimeException("Line number: " + e);
    }
}

class HighLevelException extends Exception {
    HighLevelException(Throwable cause) { super(cause); }
}

class MidLevelException extends Exception {
    MidLevelException(Throwable cause)  { super(cause); }
}

class LowLevelException extends Exception {
}
