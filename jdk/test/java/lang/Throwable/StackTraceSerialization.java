/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4202914 4363318
 * @summary Basic test of serialization of stack trace information
 * @author  Josh Bloch
 */

public class StackTraceSerialization {
    public static void main(String args[]) throws Exception {
        Throwable original = null;
        try {
            a();
        } catch(HighLevelException e) {
            original = e;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(original);
        out.flush();
        ByteArrayInputStream bin =
            new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bin);
        Throwable clone = (Throwable) in.readObject();

        if (!equal(original, clone))
            throw new Exception();
    }

    /**
     * Returns true if e1 and e2 have equal stack traces and their causes
     * are recursively equal (by the same definition).  Returns false
     * or throws NullPointerExeption otherwise.
     */
    private static boolean equal(Throwable t1, Throwable t2) {
        return t1==t2 || (Arrays.equals(t1.getStackTrace(), t2.getStackTrace())
                          && equal(t1.getCause(), t2.getCause()));
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
