/*
 * Copyright (c) 2023, Datadog, Inc. All rights reserved.
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
 * @bug 8313816
 * @summary Test that a sequence of method retransformation and stacktrace capture while the old method
 *          version is still on stack does not lead to a crash when that method's jmethodID is used as
 *          an argument for JVMTI functions.
 * @requires vm.jvmti
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.instrument
 *          java.compiler
 * @compile GetStackTraceAndRetransformTest.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main RedefineClassHelper
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -javaagent:redefineagent.jar -agentlib:GetStackTraceAndRetransformTest GetStackTraceAndRetransformTest
 */

import jdk.test.whitebox.WhiteBox;

class Transformable {
  static final String newClass = """
    class Transformable {
      static final String newClass = "";
      static void redefineAndStacktrace() throws Exception {}
      static void stacktrace() throws Exception {
        capture(Thread.currentThread());
      }
      public static native void capture(Thread thread);
    }
  """;
  static void redefineAndStacktrace() throws Exception {
    // This call will cause the class to be retransformed.
    // However, this method is still on stack so the subsequent attempt to capture the stacktrace
    // will result into this frame being identified by the jmethodID of the previous method version.
    RedefineClassHelper.redefineClass(Transformable.class, newClass);
    capture(Thread.currentThread());
  }

  static void stacktrace() throws Exception {
  }

  public static native void capture(Thread thread);
}

public class GetStackTraceAndRetransformTest {
    public static void main(String args[]) throws Throwable {
        initialize(Transformable.class);

        Transformable.redefineAndStacktrace();
        Transformable.stacktrace();

        WhiteBox.getWhiteBox().cleanMetaspaces();
        check(2);
    }

    public static native void initialize(Class<?> target);
    public static native void check(int expected);
}
