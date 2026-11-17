/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Sanity test for ForceEarlyReturn with value classes.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueForceEarlyReturn ValueForceEarlyReturn
 */

import java.util.Objects;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class ValueForceEarlyReturn {

    private static final String agentLib = "ValueForceEarlyReturn";

    @LooselyConsistentValue
    private static value class ValueClass {
        public int f1;
        public int f2;

        public ValueClass(int v1, int v2) { f1 = v1; f2 = v2; }
    }

    private static value class ValueHolder {
        public ValueClass f1;
        @NullRestricted
        public ValueClass f2;

        public static ValueClass s1 = new ValueClass(0, 1);

        public ValueHolder(int v) {
            f1 = new ValueClass(v, v + 100);
            f2 = new ValueClass(v + 1, v + 200);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        ValueClass testObj1 = new ValueClass(4, 5);
        ValueHolder testObj2 = new ValueHolder(6);

        testForceEarlyReturn(testObj1);
        testForceEarlyReturn(testObj2);
    }

    private static void testForceEarlyReturn(Object retObject) throws Exception {
        String className = retObject.getClass().getName();
        log(">> Testing ForceEarlyReturn for " + className);

        TestTask task = new TestTask();
        Thread thread = new Thread(task, "testThread");
        thread.start();

        task.ensureReady();

        nSuspendThread(thread);
        nForceEarlyReturn(thread, retObject);
        nResumeThread(thread);

        task.finish();
        thread.join();

        Object result = task.getResult();

        if (!Objects.equals(result, retObject)) {
            throw new RuntimeException("ERROR: unexpected result (" + result + ", expected " + retObject + ")");
        }
        log("<< Testing " + className + " - OK");
        log("");
    }

    private static class TestTask implements Runnable {

        private volatile boolean ready = false;
        private volatile boolean doLoop = true;
        private volatile Object result = null;

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interruption in TestTask.sleep: " + e);
            }
        }

        public void ensureReady() {
            while (!ready) {
                sleep(1);
            }
        }

        public void finish() {
            doLoop = false;
        }

        public Object getResult() {
            return result;
        }

        public void run() {
            result = meth();
        }

        // Method is busy in a while loop.
        private Object meth() {
            ready = true;
            while (doLoop) {
            }
            return null;
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    static native void nSuspendThread(Thread thread);
    static native void nResumeThread(Thread thread);
    static native void nForceEarlyReturn(Thread thread, Object obj);

}
