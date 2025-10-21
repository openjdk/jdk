/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test verifies that field access/modification events are correctly posted from JNI.
 * @bug 8224852
 * @run main/othervm/native -agentlib:JvmtiFieldEventsFromJNI FieldsEventsFromJNI
 */
public class FieldsEventsFromJNI {

    private String accessField = "accessFieldValue";
    private String modifyField = "modifyFieldValue";

    private native void enableEventsAndAccessField(boolean isEventExpected, Thread eventThread);
    private native void enableEventsAndModifyField(boolean isEventExpected, Thread eventThread);

    void javaMethod(boolean isEventExpected, Thread eventThread) {
        enableEventsAndAccessField(isEventExpected, eventThread);
        enableEventsAndModifyField(isEventExpected, eventThread);
    }

    final static Object lock = new Object();
    volatile static boolean isAnotherThreadStarted = false;

    public static void main(String[] args) throws InterruptedException {
        System.loadLibrary("JvmtiFieldEventsFromJNI");
        FieldsEventsFromJNI c = new FieldsEventsFromJNI();
        // anotherThread doesn't access fields, it is needed only to
        // enable notification somewhere.
        Thread anotherThread = new Thread(() -> {
            isAnotherThreadStarted = true;
            synchronized(lock) {
                lock.notify();
            }
            while(!Thread.currentThread().isInterrupted()) {
                Thread.yield();
            }
        });
        synchronized(lock) {
            anotherThread.start();
            while(!isAnotherThreadStarted) {
                lock.wait();
            }
        }
        // Enable events while the thread is in the same JNI call.
        c.javaMethod(true, Thread.currentThread());
        // Verify that field access from JNI doesn't fail if events are
        // not enaled on this thread.
        c.javaMethod(false, anotherThread);
        anotherThread.interrupt();
        anotherThread.join();
    }
}
