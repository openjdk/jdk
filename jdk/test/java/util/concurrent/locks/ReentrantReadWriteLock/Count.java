/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6207928 6328220 6378321
 * @summary Recursive lock invariant sanity checks
 * @author Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// I am the Cownt, and I lahve to cownt.
public class Count {
    private static void realMain(String[] args) throws Throwable {
        final ReentrantLock rl = new ReentrantLock();
        final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
        final int depth = 10;
        check(! rl.isLocked());
        check(! rwl.isWriteLocked());
        check(! rl.isHeldByCurrentThread());
        check(! rwl.isWriteLockedByCurrentThread());
        check(! rwl.writeLock().isHeldByCurrentThread());

        for (int i = 0; i < depth; i++) {
            equal(rl.getHoldCount(), i);
            equal(rwl.getReadLockCount(), i);
            equal(rwl.getReadHoldCount(), i);
            equal(rwl.getWriteHoldCount(), i);
            equal(rwl.writeLock().getHoldCount(), i);
            switch (i%4) {
            case 0:
                rl.lock();
                rwl.writeLock().lock();
                rwl.readLock().lock();
                break;
            case 1:
                rl.lockInterruptibly();
                rwl.writeLock().lockInterruptibly();
                rwl.readLock().lockInterruptibly();
                break;
            case 2:
                check(rl.tryLock());
                check(rwl.writeLock().tryLock());
                check(rwl.readLock().tryLock());
                break;
            case 3:
                check(rl.tryLock(454, TimeUnit.MILLISECONDS));
                check(rwl.writeLock().tryLock(454, TimeUnit.NANOSECONDS));
                check(rwl.readLock().tryLock(454, TimeUnit.HOURS));
                break;
            }
        }

        for (int i = depth; i > 0; i--) {
            check(! rl.hasQueuedThreads());
            check(! rwl.hasQueuedThreads());
            check(! rl.hasQueuedThread(Thread.currentThread()));
            check(! rwl.hasQueuedThread(Thread.currentThread()));
            check(rl.isLocked());
            check(rwl.isWriteLocked());
            check(rl.isHeldByCurrentThread());
            check(rwl.isWriteLockedByCurrentThread());
            check(rwl.writeLock().isHeldByCurrentThread());
            equal(rl.getQueueLength(), 0);
            equal(rwl.getQueueLength(), 0);
            equal(rwl.getReadLockCount(), i);
            equal(rl.getHoldCount(), i);
            equal(rwl.getReadHoldCount(), i);
            equal(rwl.getWriteHoldCount(), i);
            equal(rwl.writeLock().getHoldCount(), i);
            rwl.readLock().unlock();
            rwl.writeLock().unlock();
            rl.unlock();
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
