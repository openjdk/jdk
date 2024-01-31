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

import java.util.concurrent.Semaphore;

class ClassLoadingThread extends Thread {

    private ClassLoader ldr = null;
    private int which;
    private Semaphore syncOrder;

    public ClassLoadingThread(ClassLoader loader, int i, Semaphore sem) {
        ldr = loader;
        which = i;
        syncOrder = sem;
    }

    private boolean success = true;
    public boolean report_success() { return success; }

    public void callForName() {
        try {
            ThreadPrint.println("Starting forName thread ...");
            // Initiate class loading using specified type
            Class<?> a = Class.forName("A", true, ldr);
            Object obj = a.getConstructor().newInstance();
        } catch (Throwable e) {
            ThreadPrint.println("Exception is caught: " + e);
            e.printStackTrace();
            success = false;
        }
    }

    public void callLoadClass() {
        try {
            ThreadPrint.println("Starting loadClass thread ...");
            Class<?> a = ldr.loadClass("A");
            Object obj = a.getConstructor().newInstance();
            success = false; // Should have thrown LinkageError
        } catch (Throwable e) {
            // If you call loadClass directly, this will result in LinkageError
            ThreadPrint.println("Exception is caught: " + e);
            e.printStackTrace();
            success = (e instanceof LinkageError);
        }
    }

    public void run() {
       if (which == 0) {
           callLoadClass();
       } else {
           try {
               syncOrder.acquire();  // wait until loadClass is waiting.
           } catch (InterruptedException idc) {}
           callForName();
       }
    }
}
