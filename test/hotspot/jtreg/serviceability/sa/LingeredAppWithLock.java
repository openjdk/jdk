/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.apps.LingeredApp;


public class LingeredAppWithLock extends LingeredApp {
    private static Object lockObj = new Object();

    private static class NestedLock {}

    public static void lockMethod(Object lock) {
        synchronized (lock) {
            try {
                Thread.sleep(300000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void waitMethod() {
        synchronized (lockObj) {
            try {
                lockObj.wait(300000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String args[]) {
        Thread classLock1 = new Thread(() -> lockMethod(LingeredAppWithLock.class));
        Thread classLock2 = new Thread(() -> lockMethod(LingeredAppWithLock.class));
        Thread nestedClassLock = new Thread(() -> lockMethod(new NestedLock()));
        Thread objectLock = new Thread(() -> lockMethod(classLock1));
        Thread primitiveLock = new Thread(() -> lockMethod(int.class));
        Thread objectWait = new Thread(() -> waitMethod());

        classLock1.start();
        classLock2.start();
        nestedClassLock.start();
        objectLock.start();
        primitiveLock.start();
        objectWait.start();

        // Wait until all threads have reached their blocked or timed wait state
        while ((classLock1.getState() != Thread.State.BLOCKED &&
                classLock1.getState() != Thread.State.TIMED_WAITING) ||
               (classLock2.getState() != Thread.State.BLOCKED &&
                classLock2.getState() != Thread.State.TIMED_WAITING) ||
               (nestedClassLock.getState() != Thread.State.BLOCKED &&
                nestedClassLock.getState() != Thread.State.TIMED_WAITING) ||
               (objectLock.getState() != Thread.State.TIMED_WAITING) ||
               (primitiveLock.getState() != Thread.State.TIMED_WAITING) ||
               (objectWait.getState() != Thread.State.TIMED_WAITING)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
            }
        }

        LingeredApp.main(args);
    }
 }
