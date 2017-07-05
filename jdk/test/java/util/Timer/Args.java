/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6571655 6571881 6574585 6571297
 * @summary Test various args to task scheduling methods
 */

import java.util.*;
import java.util.concurrent.*;

public class Args {
    void schedule(final Timer t, final TimerTask task, final Date d) {
        t.schedule(task, d);
        THROWS(IllegalStateException.class,
               new F(){void f(){ t.schedule(task, d); }});
    }

    void schedule(final Timer t, final TimerTask task, final Date d, final long period) {
        t.schedule(task, d, period);
        THROWS(IllegalStateException.class,
               new F(){void f(){ t.schedule(task, d, period); }});
    }

    void scheduleAtFixedRate(final Timer t, final TimerTask task, final Date d, final long period) {
        t.scheduleAtFixedRate(task, d, period);
        THROWS(IllegalStateException.class,
               new F(){void f(){ t.scheduleAtFixedRate(task, d, period); }});
    }

    TimerTask counter(final CountDownLatch latch) {
        return new TimerTask() { public void run() {
            check(latch.getCount() > 0);
            latch.countDown();
        }};
    }

    void test(String[] args) throws Throwable {
        final Timer t = new Timer();
        final TimerTask x = new TimerTask() { public void run() {}};
        THROWS(IllegalArgumentException.class,
               new F(){void f(){ t.schedule(x, -42); }},
               new F(){void f(){ t.schedule(x, new Date(-42)); }},

               new F(){void f(){ t.schedule(x, Long.MAX_VALUE); }},
               new F(){void f(){ t.schedule(x, -42, 42); }},
               new F(){void f(){ t.schedule(x, new Date(-42), 42); }},
               new F(){void f(){ t.schedule(x, Long.MAX_VALUE, 42); }},
               new F(){void f(){ t.schedule(x, 42, 0); }},
               new F(){void f(){ t.schedule(x, new Date(42), 0); }},
               new F(){void f(){ t.schedule(x, 42, -42); }},
               new F(){void f(){ t.schedule(x, new Date(42), -42); }},

               new F(){void f(){ t.scheduleAtFixedRate(x, -42, 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, new Date(-42), 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, Long.MAX_VALUE, 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, 42, 0); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, new Date(42), 0); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, 42, -42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, new Date(42), -42); }}
               );

        THROWS(NullPointerException.class,
               new F(){void f(){ t.schedule(null, 42); }},
               new F(){void f(){ t.schedule(x, (Date)null); }},

               new F(){void f(){ t.schedule(null, 42, 42); }},
               new F(){void f(){ t.schedule(x, (Date)null, 42); }},

               new F(){void f(){ t.scheduleAtFixedRate(null, 42, 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, (Date)null, 42); }}
               );

        final CountDownLatch y1 = new CountDownLatch(1);
        final CountDownLatch y2 = new CountDownLatch(1);
        final CountDownLatch y3 = new CountDownLatch(11);
        final long start = System.currentTimeMillis();
        final Date past = new Date(start - 10500);

        schedule(           t, counter(y1), past);
        schedule(           t, counter(y2), past, 1000);
        scheduleAtFixedRate(t, counter(y3), past, 1000);
        y3.await();
        y1.await();
        y2.await();

        final long elapsed = System.currentTimeMillis() - start;
        System.out.printf("elapsed=%d%n", elapsed);
        check(elapsed < 500);

        t.cancel();

        THROWS(IllegalStateException.class,
               new F(){void f(){ t.schedule(x, 42); }},
               new F(){void f(){ t.schedule(x, past); }},
               new F(){void f(){ t.schedule(x, 42, 42); }},
               new F(){void f(){ t.schedule(x, past, 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, 42, 42); }},
               new F(){void f(){ t.scheduleAtFixedRate(x, past, 42); }});

    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new Args().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    abstract class F {abstract void f() throws Throwable;}
    void THROWS(Class<? extends Throwable> k, F... fs) {
        for (F f : fs)
            try {f.f(); fail("Expected " + k.getName() + " not thrown");}
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
}
