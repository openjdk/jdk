/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Google and/or its affiliates. All rights reserved.
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

package profiling.sanity;

/**
 * @test
 * @summary Verifies that AsyncGetStackTrace is call-able and provides sane information.
 * @compile ASGSTBaseTest.java
 * @requires os.family == "linux"
 * @requires os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64" | os.arch=="arm" | os.arch=="aarch64" | os.arch=="ppc64" | os.arch=="s390" | os.arch=="riscv64"
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:AsyncGetStackTraceTest profiling.sanity.ASGSTBaseTest
 */

public class ASGSTBaseTest {
  static {
    try {
      System.loadLibrary("AsyncGetStackTraceTest");
    } catch (UnsatisfiedLinkError ule) {
      System.err.println("Could not load AsyncGetStackTrace library");
      System.err.println("java.library.path: " + System.getProperty("java.library.path"));
      throw ule;
    }
  }

  /** check a simple native call which calls ASGST */
  private static native boolean checkAsyncGetStackTraceCall();

  public static void main(String[] args) throws Exception {
    if (!checkAsyncGetStackTraceCall()) {
      throw new RuntimeException("Basic AsyncGetStackTrace calls failed.");
    }
  }
}
