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

/*
 * @test
 * @bug 8212155
 * @summary Test concurrent enabling and posting of DynamicCodeGenerated events.
 * @requires vm.jvmti
 * @library /test/lib
 * @run main/othervm/native -agentlib:DynamicCodeGenerated DynamicCodeGeneratedTest
 */

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

public class DynamicCodeGeneratedTest {
    static {
        System.loadLibrary("DynamicCodeGenerated");
    }
    public static native void changeEventNotificationMode();

    public static void main(String[] args) throws Exception {
        // Try to enable DynamicCodeGenerated event while it is posted
        // using JvmtiDynamicCodeEventCollector from VtableStubs::find_stub
        Thread threadChangeENM = new Thread(() -> {
            changeEventNotificationMode();
        });
        threadChangeENM.setDaemon(true);
        threadChangeENM.start();

        for (int i = 0; i < 10; i++) {
            List<Thread> threads = new ArrayList();
            for (int j = 0; j < 200; j++) {
                Thread t = new Thread(() -> {
                        String result = "string" + System.currentTimeMillis();
                        Reference.reachabilityFence(result);
                });
                threads.add(t);
                t.start();
            }
            for (Thread t: threads) {
                t.join();
            }
        }
    }
}
