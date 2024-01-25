/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4670071 8295278
 * @summary Duplicate class loader deadlock referenced in https://openjdk.org/groups/core-libs/ClassLoaderProposal.html
 *          One thread loads (A, CL1) extends (B, CL2), while the second loads (C, CL2) extends (D, CL1)
 * @library /test/lib
 * @compile test-classes/A.java test-classes/B.java test-classes/C.java test-classes/D.java ../share/ThreadPrint.java
 * @run main/othervm SuperWaitTest
 */

import jdk.test.lib.classloader.ClassUnloadCommon;
import java.util.concurrent.Semaphore;

public class SuperWaitTest {

    // Loads classes A and D, delegates for A's super class B
    private static class MyLoaderOne extends ClassLoader {

        private static boolean dIsLoading = false;

        ClassLoader parent;
        ClassLoader baseLoader;

        MyLoaderOne(ClassLoader parent) {
            this.parent = parent;
            this.baseLoader = null;
        }

        public void setBaseLoader(ClassLoader ldr) {
            this.baseLoader = ldr;
        }

        public synchronized Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) return loadedClass;
            if (name.equals("A") || name.equals("D")) {
                ThreadPrint.println("Loading " + name);
                if (name.equals("A")) {
                    ThreadPrint.println("Waiting for " + name);
                    while (!dIsLoading) {  // guard against spurious wakeup
                        try {
                            wait(); // let the other thread have this lock.
                        } catch (InterruptedException ie) {}
                    }
                } else {
                    dIsLoading = true;
                    notify(); // notify lock when superclass loading is done
                }
                byte[] classfile = ClassUnloadCommon.getClassData(name);
                return defineClass(name, classfile, 0, classfile.length);
            } else if (name.equals("B")) {
                return baseLoader.loadClass(name);
            } else {
                assert (!name.equals("C"));
                return parent.loadClass(name);
            }
        }
    }

    // Loads classes C and B, delegates for C's super class D
    private static class MyLoaderTwo extends ClassLoader {

        ClassLoader parent;
        ClassLoader baseLoader;

        MyLoaderTwo(ClassLoader parent) {
            this.parent = parent;
            this.baseLoader = null;
        }

        public void setBaseLoader(ClassLoader ldr) {
            this.baseLoader = ldr;
        }

        public synchronized Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) return loadedClass;
            if (name.equals("C") || name.equals("B")) {
                ThreadPrint.println("Loading " + name);
                byte[] classfile = ClassUnloadCommon.getClassData(name);
                return defineClass(name, classfile, 0, classfile.length);
            } else if (name.equals("D")) {
                return baseLoader.loadClass(name);
            } else {
                assert (!name.equals("A"));
                return parent.loadClass(name);
            }
        }
    }

    private static ClassLoadingThread[] threads = new ClassLoadingThread[2];
    private static boolean success = true;

    private static boolean report_success() {
        for (int i = 0; i < 2; i++) {
          try {
            threads[i].join();
            if (!threads[i].report_success()) success = false;
          } catch (InterruptedException e) {}
        }
        return success;
    }

    public static void main(java.lang.String[] unused) {
        // t1 loads (A,CL1) extends (B,CL2); t2 loads (C,CL2) extends (D,CL1)

        ClassLoader appLoader = SuperWaitTest.class.getClassLoader();
        MyLoaderOne ldr1 = new MyLoaderOne(appLoader);
        MyLoaderTwo ldr2 = new MyLoaderTwo(appLoader);
        ldr1.setBaseLoader(ldr2);
        ldr2.setBaseLoader(ldr1);

        threads[0] = new ClassLoadingThread("A", ldr1);
        threads[1] = new ClassLoadingThread("C", ldr2);
        for (int i = 0; i < 2; i++) {
            threads[i].setName("Loading Thread #" + (i + 1));
            threads[i].start();
            System.out.println("Thread " + (i + 1) + " was started...");
        }

        if (report_success()) {
           System.out.println("PASSED");
        } else {
            throw new RuntimeException("FAILED");
        }
    }
}
