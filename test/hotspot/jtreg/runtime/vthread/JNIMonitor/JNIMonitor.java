/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @test JNIMonitor
 * @summary Tests that JNI monitors work correctly with virtual threads
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} JNIMonitor.java
 * @run main/native/othervm --enable-preview JNIMonitor
 */

public class JNIMonitor {

    // straight-forward interface to JNI monitor functions
    static native int monitorEnter(Object o);
    static native int monitorExit(Object o);
    static {
        System.loadLibrary("JNIMonitor");
    }

    public static void main(String[] args) throws Throwable {
        final Object monitor = new Object();
        final AtomicReference<Throwable> exception = new AtomicReference();
        Thread.ofVirtual().start(() -> {
            try {
                int res = monitorEnter(monitor);
                Asserts.assertTrue(res == 0, "monitorEnter should return 0.");
                Thread.yield();
                res = monitorExit(monitor);
                Asserts.assertTrue(res == 0, "monitorExit should return 0.");
            } catch (Throwable t) {
                exception.set(t);
            }
        }).join();
        if (exception.get() != null) {
            throw exception.get();
        }
    }
}
