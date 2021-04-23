/*
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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
 * @library /test/lib
 * @compile HostNoNestMember.java
 * @compile HostNoNestMember.jcod
 *
 * @run main/othervm TestNestHostErrorWithMultiThread
 */

import java.util.concurrent.CountDownLatch;

public class TestNestHostErrorWithMultiThread {

  public static void main(String args[]) {
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(2);

    new Thread(new Test(latch1, latch2)).start();
    new Thread(new Test(latch1, latch2)).start();

    try {
      // waiting thread creation
      latch2.await();
      latch1.countDown();
    } catch (InterruptedException e) {}
  }

  static class Test implements Runnable {
    private CountDownLatch latch1;
    private CountDownLatch latch2;

    Test(CountDownLatch latch1, CountDownLatch latch2) {
      this.latch1 = latch1;
      this.latch2 = latch2;
    }

    @Override
    public void run() {
      try {
        latch2.countDown();
        // Try to have all threads trigger the nesthost check at the same time
        latch1.await();
        HostNoNestMember h = new HostNoNestMember();
        h.test();
      } catch (IllegalAccessError expected) {
        String msg = "current type is not listed as a nest member";
        if (!expected.getMessage().contains(msg)) {
          throw new Error("Wrong " + expected.getClass().getSimpleName() +": \"" +
                          expected.getMessage() + "\" does not contain \"" +
                          msg + "\"", expected);
        }
        System.out.println("OK - got expected exception: " + expected);
      } catch (InterruptedException e) {}
    }
  }
}
