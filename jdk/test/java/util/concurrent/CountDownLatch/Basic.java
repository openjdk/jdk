/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6332435
 * @summary Basic tests for CountDownLatch
 * @author Seetharam Avadhanam, Martin Buchholz
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

interface AwaiterFactory {
    Awaiter getAwaiter();
}

abstract class Awaiter extends Thread {
    private volatile Throwable result = null;
    protected void result(Throwable result) { this.result = result; }
    public Throwable result() { return this.result; }
}

public class Basic {

    private void toTheStartingGate(CountDownLatch gate) {
        try {
            gate.await();
        }
        catch (Throwable t) { fail(t); }
    }

    private Awaiter awaiter(final CountDownLatch latch,
                            final CountDownLatch gate) {
        return new Awaiter() { public void run() {
            System.out.println("without millis: " + latch.toString());
            gate.countDown();

            try {
                latch.await();
                System.out.println("without millis - ComingOut");
            }
            catch (Throwable result) { result(result); }}};
    }

    private Awaiter awaiter(final CountDownLatch latch,
                            final CountDownLatch gate,
                            final long millis) {
        return new Awaiter() { public void run() {
            System.out.println("with millis: "+latch.toString());
            gate.countDown();

            try {
                latch.await(millis, TimeUnit.MILLISECONDS);
                System.out.println("with millis - ComingOut");
            }
            catch (Throwable result) { result(result); }}};
    }

    private AwaiterFactory awaiterFactories(final CountDownLatch latch,
                                            final CountDownLatch gate,
                                            final int i) {
        if (i == 1)
            return new AwaiterFactory() { public Awaiter getAwaiter() {
                return awaiter(latch, gate); }};

        return new AwaiterFactory() { public Awaiter getAwaiter() {
            return awaiter(latch, gate, 10000); }};
    }

    //----------------------------------------------------------------
    // Normal use
    //----------------------------------------------------------------
    public static void normalUse() throws Throwable {
        int count = 0;
        Basic test = new Basic();
        CountDownLatch latch = new CountDownLatch(3);
        Awaiter a[] = new Awaiter[12];

        for (int i = 0; i < 3; i++) {
            CountDownLatch gate = new CountDownLatch(4);
            AwaiterFactory factory1 = test.awaiterFactories(latch, gate, 1);
            AwaiterFactory factory2 = test.awaiterFactories(latch, gate, 0);
            a[count] = factory1.getAwaiter(); a[count++].start();
            a[count] = factory1.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            test.toTheStartingGate(gate);
            System.out.println("Main Thread: " + latch.toString());
            latch.countDown();
            checkCount(latch, 2-i);
        }
        for (int i = 0; i < 12; i++)
            a[i].join();

        for (int i = 0; i < 12; i++)
            checkResult(a[i], null);
    }

    //----------------------------------------------------------------
    // One thread interrupted
    //----------------------------------------------------------------
    public static void threadInterrupted() throws Throwable {
        int count = 0;
        Basic test = new Basic();
        CountDownLatch latch = new CountDownLatch(3);
        Awaiter a[] = new Awaiter[12];

        for (int i = 0; i < 3; i++) {
            CountDownLatch gate = new CountDownLatch(4);
            AwaiterFactory factory1 = test.awaiterFactories(latch, gate, 1);
            AwaiterFactory factory2 = test.awaiterFactories(latch, gate, 0);
            a[count] = factory1.getAwaiter(); a[count++].start();
            a[count] = factory1.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            a[count-1].interrupt();
            test.toTheStartingGate(gate);
            System.out.println("Main Thread: " + latch.toString());
            latch.countDown();
            checkCount(latch, 2-i);
        }
        for (int i = 0; i < 12; i++)
            a[i].join();

        for (int i = 0; i < 12; i++)
            checkResult(a[i],
                        (i % 4) == 3 ? InterruptedException.class : null);
    }

    //----------------------------------------------------------------
    // One thread timed out
    //----------------------------------------------------------------
    public static void timeOut() throws Throwable {
        int count =0;
        Basic test = new Basic();
        CountDownLatch latch = new CountDownLatch(3);
        Awaiter a[] = new Awaiter[12];

        long[] timeout = { 0L, 5L, 10L };

        for (int i = 0; i < 3; i++) {
            CountDownLatch gate = new CountDownLatch(4);
            AwaiterFactory factory1 = test.awaiterFactories(latch, gate, 1);
            AwaiterFactory factory2 = test.awaiterFactories(latch, gate, 0);
            a[count] = test.awaiter(latch, gate, timeout[i]); a[count++].start();
            a[count] = factory1.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            a[count] = factory2.getAwaiter(); a[count++].start();
            test.toTheStartingGate(gate);
            System.out.println("Main Thread: " + latch.toString());
            latch.countDown();
            checkCount(latch, 2-i);
        }
        for (int i = 0; i < 12; i++)
            a[i].join();

        for (int i = 0; i < 12; i++)
            checkResult(a[i], null);
    }

    public static void main(String[] args) throws Throwable {
        normalUse();
        threadInterrupted();
        timeOut();
        if (failures.get() > 0L)
            throw new AssertionError(failures.get() + " failures");
    }

    private static final AtomicInteger failures = new AtomicInteger(0);

    private static void fail(String msg) {
        fail(new AssertionError(msg));
    }

    private static void fail(Throwable t) {
        t.printStackTrace();
        failures.getAndIncrement();
    }

    private static void checkCount(CountDownLatch b, int expected) {
        if (b.getCount() != expected)
            fail("Count = " + b.getCount() +
                 ", expected = " + expected);
    }

    private static void checkResult(Awaiter a, Class c) {
        Throwable t = a.result();
        if (! ((t == null && c == null) || c.isInstance(t))) {
            System.out.println("Mismatch: " + t + ", " + c.getName());
            failures.getAndIncrement();
        }
    }
}
