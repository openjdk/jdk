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
 * @bug 8295278
 * @summary Call unlocked version loadClass directly, with another thread calling forName
 *          One class goes through the ClassLoader path and the other goes through JVM path
 * @library /test/lib
 * @compile test-classes/A.java ../share/ThreadPrint.java
 * @run main/othervm CallLoadClassTest
 */

import jdk.test.lib.classloader.ClassUnloadCommon;
import java.util.concurrent.Semaphore;

public class CallLoadClassTest {

    private static Semaphore mainSync = null;

    private static class MyLoader extends ClassLoader {

        ClassLoader parent;
        int count;

        MyLoader(ClassLoader parent) {
            this.parent = parent;
            this.count = 0;
        }

        public synchronized Class<?> loadClass(String name) throws ClassNotFoundException {
            Class<?> loadedClass = findLoadedClass(name);
            if (name.equals("A") && loadedClass == null) {
                ThreadPrint.println("Loading A");
                if (count == 0) {
                    count++;
                    ThreadPrint.println("Waiting for A");
                    try {
                        mainSync.release(); // Let t2 start
                        wait();  // let the other thread load A instead.
                    } catch (InterruptedException ie) {
                    }
                } else {
                    notify(); // notify any waiting threads.
                }
                byte[] classfile = ClassUnloadCommon.getClassData("A");
                return defineClass(name, classfile, 0, classfile.length);
            } else {
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
        mainSync = new Semaphore(0);

        // t1 does loadClass directly, t2 does class.ForName()
        ClassLoader appLoader = CallLoadClassTest.class.getClassLoader();
        MyLoader ldr = new MyLoader(appLoader);
        for (int i = 0; i < 2; i++) {
            threads[i] = new ClassLoadingThread(ldr, i, mainSync);
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
