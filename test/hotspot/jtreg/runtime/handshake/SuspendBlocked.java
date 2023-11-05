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
 *
 */

/*
 * @test SuspendBlocked
 * @bug 8270085
 * @library /test/lib /testlibrary
 * @build SuspendBlocked
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI SuspendBlocked
 */

import jvmti.JVMTIUtils;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class SuspendBlocked {

    public static void main(String... args) throws Exception {
        Thread suspend_thread = new Thread(() -> run_loop());
        suspend_thread.start();
        WhiteBox wb = WhiteBox.getWhiteBox();
        for (int i = 0; i < 100; i++) {
            try {
                JVMTIUtils.suspendThread(suspend_thread);
                wb.lockAndBlock(/* suspender= */ true);
                JVMTIUtils.resumeThread(suspend_thread);
                Thread.sleep(1);
            } catch (JVMTIUtils.JvmtiException e) {
                if (e.getCode() != JVMTIUtils.JVMTI_ERROR_THREAD_NOT_ALIVE) {
                    throw e;
                }
            }
        }
        suspend_thread.join();
    }

    public static void run_loop() {
        WhiteBox wb = WhiteBox.getWhiteBox();
        for (int i = 0; i < 100; i++) {
            wb.lockAndBlock(/* suspender= */ false);
        }
    }
}
