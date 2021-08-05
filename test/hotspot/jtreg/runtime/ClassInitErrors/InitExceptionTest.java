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
 * @summary Test that the NCDFE saves original exception during class initialization.
 * @run main InitExceptionTest
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

class InitNPE {
    static {
        if (true) throw new NullPointerException();
    }
}

class InitOOM {
    static {
        if (true) foo();
    }
    static void foo() {
        ArrayList<byte[]> l = new ArrayList<>();
        while (true) {
            l.add(new byte[16*1024]);
        }
    }
}

public class InitExceptionTest {

  private static void verify_stack(Throwable e, String expected, String cause) throws Exception {
    ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(byteOS);
    e.printStackTrace(printStream);
    printStream.close();
    String stackTrace = byteOS.toString("ASCII");
    if (!stackTrace.contains(expected) || (cause != null && !stackTrace.contains(cause))) {
      throw new RuntimeException(expected + " and " + cause + " missing from stacktrace: " + stackTrace);
    }
  }

  public static void main(java.lang.String[] unused) throws Exception {
    try {
      InitNPE c = new InitNPE();
    } catch (Error err) {
      System.out.println("Error thrown: " + err);
      verify_stack(err, "java.lang.ExceptionInInitializerError", "Caused by: java.lang.NullPointerException");
      err.printStackTrace();
    }
    try {
      InitNPE c = new InitNPE();
    } catch (Error err) {
      System.out.println("Error thrown: " + err);
      verify_stack(err, "java.lang.NoClassDefFoundError", "Caused by: java.lang.NullPointerException");
      err.printStackTrace();
    }
    try {
      InitOOM e = new InitOOM();
    } catch (Error err) {
      System.out.println("Error thrown: " + err);
      verify_stack(err, "java.lang.OutOfMemoryError", "Java heap space");
      err.printStackTrace();
    }
    try {
      InitOOM e = new InitOOM();
    } catch (Error err) {
      System.out.println("Error thrown: " + err);
      verify_stack(err, "java.lang.NoClassDefFoundError", "Caused by: java.lang.OutOfMemoryError");
      err.printStackTrace();
    }
  }
}
