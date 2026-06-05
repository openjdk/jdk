/*
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8264760
 * @summary JVM crashes when two threads encounter the same resolution error
 *
 * @compile HostNoNestMember.java
 * @compile HostNoNestMember.jcod
 *
 * @run main TestNestHostErrorWithMultiThread
 */

// HostNoNestMember.jcod must be compiled after HostNoNestMember.java
// because the class file from the jcod file must replace the
// HostNoNestMember class file generated from HostNoNestMember.java.

import java.util.concurrent.CountDownLatch;

public class TestNestHostErrorWithMultiThread {

  public static void main(String args[]) throws Throwable {
    CountDownLatch runLatch = new CountDownLatch(1);
    CountDownLatch startLatch = new CountDownLatch(2);

    TestThread t1 = new TestThread(runLatch, startLatch);
    TestThread t2 = new TestThread(runLatch, startLatch);

    t1.start();
    t2.start();

    try {
      // waiting thread creation
      startLatch.await();
      runLatch.countDown();

      t1.join();
      t2.join();

      Throwable threadException = t1.exception() != null ? t1.exception()
                                                         : t2.exception();
      if (threadException != null) {
        Throwable t = threadException;
        try {
          throw new Error("TestThread encountered unexpected exception", t);
        }
        catch (OutOfMemoryError oome) {
          // If we encounter an OOME trying to create the wrapper Error,
          // then just re-throw the original exception so we report it and
          // not the secondary OOME.
          throw t;
        }
      }
    } catch (InterruptedException e) {
      throw new Error("Unexpected interrupt");
    }
  }

  static class TestThread extends Thread {
    private CountDownLatch runLatch;
    private CountDownLatch startLatch;
    private Throwable exception;

    Throwable exception() {
      return exception;
    }

    TestThread(CountDownLatch runLatch, CountDownLatch startLatch) {
      this.runLatch = runLatch;
      this.startLatch = startLatch;
    }

    @Override
    public void run() {
      // Don't allow any exceptions to escape - the main thread will
      // report them.
      try {
        try {
          startLatch.countDown();
          // Try to have all threads trigger the nesthost check at the same time
          runLatch.await();
          HostNoNestMember h = new HostNoNestMember();
          h.test();
          throw new Error("IllegalAccessError was not thrown as expected");
        } catch (IllegalAccessError expected) {
          String msg = "current type is not listed as a nest member";
          if (!expected.getMessage().contains(msg)) {
            throw new Error("Wrong " + expected.getClass().getSimpleName() +": \"" +
                            expected.getMessage() + "\" does not contain \"" +
                            msg + "\"", expected);
          }
          System.out.println("OK - got expected exception: " + expected);
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        }
      } catch (Throwable t) {
        exception = t;
      }
    }
  }
}
