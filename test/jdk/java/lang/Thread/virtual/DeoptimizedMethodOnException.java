/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8389310
 * @summary Test exception propagation when caller nmethod is marked for deoptimization
 * @requires vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -XX:CompileCommand=dontinline,*::bar DeoptimizedMethodOnException
 */

/*
 * @test id=Xcomp
 * @bug 8389310
 * @summary Test exception propagation when caller nmethod is marked for deoptimization
 * @requires vm.continuations
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -XX:CompileCommand=dontinline,*::bar -Xcomp DeoptimizedMethodOnException
 */

import java.util.concurrent.CountDownLatch;

import jdk.test.lib.Asserts;

public class DeoptimizedMethodOnException {
    private static CountDownLatch sync = new CountDownLatch(0);
    private static A receiver = new A();
    private static int resultInt;
    private static String resultStr;

    public static void main(String args[]) throws Exception {
        warmUp();
        Asserts.assertTrue(resultInt == 1, "resultInt=" + resultInt);
        Asserts.assertTrue(resultStr.equals("MyExceptionWithExtraFields"), "resultStr=" + resultStr);

        var started = new CountDownLatch(1);
        Thread vthread = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            foo();
         });

        sync = new CountDownLatch(1);
        vthread.start();
        started.await();
        // wait until vthread blocks in sync
        await(vthread, Thread.State.WAITING);
        receiver = new B();
        sync.countDown();

        vthread.join();
        Asserts.assertTrue(resultInt == 3, "resultInt=" + resultInt);
        Asserts.assertTrue(resultStr.equals("MyExceptionWithoutExtraFields"), "resultStr=" + resultStr);
    }

    public static void foo() {
        try {
            bar();
        } catch (MyException e) {
            resultInt = receiver.m();
            String tmp = e.getExceptionName();
            Asserts.assertTrue(tmp.length() > 0, "length=" + tmp.length());
            resultStr = tmp;
            return;
        }
        throw new RuntimeException("Should not reach here");
    }

    public static void bar() {
        try {
            sync.await();
        } catch (InterruptedException ie) {}
        receiver.throwException();
    }

    private static void warmUp() {
        for (int i = 0; i < 30_000; i++) {
            foo();
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Asserts.assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}

abstract class MyException extends RuntimeException {
    abstract String getExceptionName();
}

class MyExceptionWithExtraFields extends MyException {
    Integer l1, l2, l3, l4, l5, l6, l7, l8, l9, l10, l11, l12, l13, l14, l15;
    String exceptionName;
    MyExceptionWithExtraFields() {
        l1=1;l2=2;l3=3;l4=4;l5=5;l6=6;l7=7;l8=8;l9=9;l10=10;l11=11;l12=12;l13=13;l14=14;l15=15;
        exceptionName = new String("MyExceptionWithExtraFields");
    }
    @Override
    String getExceptionName() { return exceptionName; }
}

class MyExceptionWithoutExtraFields extends MyException {
    String exceptionName;
    MyExceptionWithoutExtraFields() {
        exceptionName = new String("MyExceptionWithoutExtraFields");
    }
    @Override
    String getExceptionName() { return exceptionName; }
}

class A {
    int m() {
        return 1;
    }
    void throwException() {
        throw new MyExceptionWithExtraFields();
    }
}

class B extends A {
    int m() {
        return 3;
    }
    void throwException() {
        throw new MyExceptionWithoutExtraFields();
    }
}
