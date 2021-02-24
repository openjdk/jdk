/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

class MyLoader extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    public Class loadClass(String name) throws ClassNotFoundException {
        synchronized(getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            byte[] b = loadClassData(name);
            if (b != null) {
                return defineClass(name, b, 0, b.length);
            } else {
                return super.loadClass(name);
            }
        }
    }

    private static boolean waitForSuper = false;
    private static boolean concurrent = false;
    private static boolean okSuper = false;
    private static boolean first = true;
    private Object sync = new Object();
    private Object thread_sync = new Object();

    private void makeThreadWait() {
         first = false;  // second thread gets a different A
         if (waitForSuper) {
            // Wake up the first thread here.
            synchronized (thread_sync) {
                thread_sync.notify();
            }
        }
        if (isRegisteredAsParallelCapable()) {
            synchronized(sync) {
                try {
                    System.out.println("t1 waits parallelCapable loader");
                    sync.wait(100);  // Give up lock after request to load B
                } catch (InterruptedException e) {}
             }
         } else {
             try {
                System.out.println("t1 waits non-parallelCapable loader");
                wait(100);  // Give up lock after request to load B
              } catch (InterruptedException e) {}
         }
    }

    private byte[] loadClassData(String name) {
        // load the class data from the connection
        if (name.equals("A")) {
            if (first) {
                System.out.println("loading A extends B");
                return getClassData("A");
            } else {
                System.out.println("loading A extends C");
                byte[] data;
                try {
                    data = AsmClasses.dumpA();
                } catch (Exception e) {
                    data = null;
                }
                return data;
            }
        } else if (name.equals("B")) {
            byte[] data;
            if (okSuper) {
                data = getClassData("B");
            } else {
                try {
                    data = AsmClasses.dumpB();
                } catch (Exception e) {
                    data = null;
                    e.printStackTrace();
                }
            }
            if (first) makeThreadWait();
            return data;
        } else if (name.equals("C")) {
            byte[] data = getClassData("C");
            makeThreadWait();
            return data;
        } else {
            return getClassData(name);
        }
    }

    byte[] getClassData(String name) {
        try {
           String TempName = name;
           String currentDir = System.getProperty("test.classes");
           String filename = currentDir + File.separator + TempName + ".class";

           FileInputStream fis = new FileInputStream(filename);
           byte[] b = new byte[5000];
           int cnt = fis.read(b, 0, 5000);
           byte[] c = new byte[cnt];
           for (int i=0; i<cnt; i++) c[i] = b[i];
             return c;
        } catch (IOException e) {
           return null;
        }
    }

    ClassLoadingThread[] threads = new ClassLoadingThread[2];
    private boolean success = true;

    public boolean report_success() {
        for (int i = 0; i < 2; i++) {
          try {
            threads[i].join();
            if (!threads[i].report_success()) success = false;
          } catch (InterruptedException e) {}
        }
        return success;
    }

    void startLoading() {

        for (int i = 0; i < 2; i++) {
            threads[i] = new ClassLoadingThread(this, thread_sync, okSuper);
            threads[i].setName("Loading Thread #" + (i + 1));
            threads[i].start();
            System.out.println("Thread " + (i + 1) + " was started...");
            // wait to start the second thread if not concurrent
            if (!concurrent && i == 0) {
                synchronized(thread_sync) {
                    try {
                        System.out.println("t2 waits");
                        thread_sync.wait();
                    } catch (InterruptedException e) {}
                }
            }
        }
    }

    MyLoader(boolean load_in_parallel, boolean wait_for_super, boolean load_different_super) {
       concurrent = load_in_parallel;
       waitForSuper = wait_for_super;
       okSuper = load_different_super;
    }
}
