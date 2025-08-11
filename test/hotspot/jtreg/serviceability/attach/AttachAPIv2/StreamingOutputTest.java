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
 * @summary Sanity test for streaming output support
 * @bug 8319055
 * @library /test/lib
 * @modules jdk.attach/sun.tools.attach
 *
 * @run main/othervm -Djdk.attach.allowStreamingOutput=true StreamingOutputTest
 * @run main/othervm -Djdk.attach.allowStreamingOutput=false StreamingOutputTest
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import jdk.test.lib.apps.LingeredApp;

public class StreamingOutputTest {

    public static void main(String[] args) throws Exception {
        boolean clientStreaming = System.getProperty("jdk.attach.allowStreamingOutput").equals("true");
        test(clientStreaming, false);
        test(clientStreaming, true);
    }

    private static void test(boolean clientStreaming, boolean vmStreaming) throws Exception {
        System.out.println("Testing: clientStreaming=" + clientStreaming + ", vmStreaming=" + vmStreaming);
        LingeredApp app = null;
        try {
            app = LingeredApp.startApp("-Xlog:attach=trace",
                                       "-Djdk.attach.vm.streaming=" + String.valueOf(vmStreaming));
            attach(app);
        } finally {
            LingeredApp.stopApp(app);
        }

        verify(clientStreaming, vmStreaming, app.getProcessStdout());

        System.out.println("Testing: end");
        System.out.println();
    }

    private static void attach(LingeredApp app) throws Exception {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine)VirtualMachine.attach(String.valueOf(app.getPid()));
        try {
            try (BufferedReader replyReader = new BufferedReader(
                    new InputStreamReader(vm.setFlag("HeapDumpPath", "the_path")))) {
                System.out.println("vm.setFlag reply:");
                String line;
                while ((line = replyReader.readLine()) != null) {
                    System.out.println("setFlag reply: " + line);
                }
            }
        } finally {
            vm.detach();
        }
    }

    private static void verify(boolean clientStreaming, boolean vmStreaming, String out) throws Exception {
        System.out.println("Target VM output:");
        System.out.println(out);
        String expected = "executing command setflag, streaming output: " + (clientStreaming ? "1" : "0");
        if (!out.contains(expected)) {
            throw new Exception("VM did not logged expected '" + expected + "'");
        }
        expected = "default streaming output: " + (vmStreaming ? "1" : "0");
        if (!out.contains(expected)) {
            throw new Exception("VM did not logged expected '" + expected + "'");
        }
    }
}
