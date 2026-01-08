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
 * @summary verifies that failure in AttachListener::dequeue does not crash the VM
 * @bug 8356177
 * @library /test/lib
 * @modules jdk.attach/sun.tools.attach
 *
 * @run driver FailedDequeueTest
 */

import java.io.IOException;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import jdk.test.lib.apps.LingeredApp;

public class FailedDequeueTest {

    public static void main(String[] args) throws Exception {
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp("-Xlog:attach=trace");
            test(app);
        } finally {
            LingeredApp.stopApp(app);
        }
    }

    // The test uses HotSpotVirtualMachine.setFlag method with long flag value (longer than 256K).
    private static String flagName = "HeapDumpPath";
    private static String flagValue = "X" + "A".repeat(256 * 1024) + "X";

    private static void test(LingeredApp app) throws Exception {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine)VirtualMachine.attach(String.valueOf(app.getPid()));
        try {
            // Should throw IOException and don't crash
            vm.setFlag(flagName, flagValue);

            throw new RuntimeException("expected IOException is not thrown");
        } catch (IOException ex) {
            System.out.println("OK: setFlag thrown expected exception:");
            ex.printStackTrace(System.out);
        } finally {
            vm.detach();
        }
    }
}
