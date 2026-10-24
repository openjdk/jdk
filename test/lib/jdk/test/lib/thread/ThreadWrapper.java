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


package jdk.test.lib.thread;

import java.time.Duration;
import java.util.Map;

/*
    The ThreadWrapper is a helper class that allows to extend
    coverage of virtual threads testing for existing tests with threads.

    Specifically, it is useful for the pattern where Thread is extended
    by some class. Example:

    class resumethrd02Thread extends Thread {...}
    ...
    resumethrd02Thread thr = new resumethrd02Thread();

    The test can be updated to use this wrapper:
    class resumethrd02Thread extends ThreadWrapper {...}
    ...
    resumethrd02Thread thr = new resumethrd02Thread();

    So resumethrd02Thread can be run with platform or virtual threads.

    Method getThread() is used to get instance of Thread.

    It is not expected to use this wrapper for new tests or classes that
    are not extending Thread. The TestThreadFactory should be used to
    create threads in such cases.
 */

public class ThreadWrapper implements Runnable {
    private final Thread thread;

    @SuppressWarnings("this-escape")
    public ThreadWrapper() {
        // thread is a platform or virtual thread
        thread = TestThreadFactory.newThread(this);
    }

    @SuppressWarnings("this-escape")
    public ThreadWrapper(String name) {
        // thread is a platform or virtual thread
        thread = TestThreadFactory.newThread(this, name);
    }

    public Thread getThread() {
        return thread;
    }

    public static Thread currentThread() {
        return Thread.currentThread();
    }

    public static void yield() {
        Thread.yield();
    }

    public static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    public static void sleep(long millis, int nanos) throws InterruptedException {
        Thread.sleep(millis, nanos);
    }

    public static void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public static void onSpinWait() {
        Thread.onSpinWait();
    }

    public static Thread.Builder.OfPlatform ofPlatform() {
        return Thread.ofPlatform();
    }

    public static Thread.Builder.OfVirtual ofVirtual() {
        return Thread.ofVirtual();
    }

    public static Thread startVirtualThread(Runnable task) {
        return Thread.startVirtualThread(task);
    }

    public boolean isVirtual() {
        return thread.isVirtual();
    }

    public void start() {
        thread.start();
    }

    public void run() {
    }

    public void interrupt() {
        thread.interrupt();
    }

    public static boolean interrupted() {
        return Thread.interrupted();
    }

    public boolean isInterrupted() {
        return thread.isInterrupted();
    }

    public boolean isAlive() {
        return thread.isAlive();
    }

    public void setPriority(int newPriority) {
        thread.setPriority(newPriority);
    }

    public int getPriority() {
        return thread.getPriority();
    }

    public void setName(String name) {
        thread.setName(name);
    }

    public String getName() {
        return thread.getName();
    }

    public ThreadGroup getThreadGroup() {
        return thread.getThreadGroup();
    }

    public static int activeCount() {
        return Thread.activeCount();
    }

    public static int enumerate(Thread[] tarray) {
        return Thread.enumerate(tarray);
    }

    public void join(long millis) throws InterruptedException {
        thread.join(millis);
    }

    public void join(long millis, int nanos) throws InterruptedException {
        thread.join(millis, nanos);
    }

    public void join() throws InterruptedException {
        thread.join();
    }

    public boolean join(Duration duration) throws InterruptedException {
        return thread.join(duration);
    }

    public static void dumpStack() {
        Thread.dumpStack();
    }

    public void setDaemon(boolean on) {
        thread.setDaemon(on);
    }

    public boolean isDaemon() {
        return thread.isDaemon();
    }

    @Override
    public String toString() {
        return thread.toString();
    }

    public ClassLoader getContextClassLoader() {
        return thread.getContextClassLoader();
    }

    public void setContextClassLoader(ClassLoader cl) {
        thread.setContextClassLoader(cl);
    }

    public static boolean holdsLock(Object obj) {
        return Thread.holdsLock(obj);
    }

    public StackTraceElement[] getStackTrace() {
        return thread.getStackTrace();
    }

    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return Thread.getAllStackTraces();
    }

    @Deprecated(since = "19")
    public long getId() {
        return thread.getId();
    }

    public long threadId() {
        return thread.threadId();
    }

    public Thread.State getState() {
        return thread.getState();
    }

    public static void setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler ueh) {
        Thread.setDefaultUncaughtExceptionHandler(ueh);
    }

    public static Thread.UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return Thread.getDefaultUncaughtExceptionHandler();
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return thread.getUncaughtExceptionHandler();
    }

    public void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler ueh) {
        thread.setUncaughtExceptionHandler(ueh);
    }
}
