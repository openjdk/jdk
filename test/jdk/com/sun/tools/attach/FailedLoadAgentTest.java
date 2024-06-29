/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8325530
 * @summary Test that failed VirtualMachine.loadAgentPath/loadAgentLibrary reports detailed reason
 * @requires vm.jvmti
 * @modules jdk.attach
 * @library /test/lib
 * @run driver FailedLoadAgentTest
 */

import java.nio.file.Path;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;

public class FailedLoadAgentTest {
    private static final String jvmtiAgentLib = "FailedLoadAgentTestNotExists";
    private static final String jvmtiAgentPath = getLibPath(jvmtiAgentLib);

    private static String getLibPath(String libName) {
        String fullName = Platform.buildSharedLibraryName(libName);
        return Path.of(Utils.TEST_NATIVE_PATH, fullName).toAbsolutePath().toString();
    }

    private interface TestAction {
        void test() throws Exception;
    }

    private static void test(TestAction action) throws Exception {
        try {
            action.test();
            throw new RuntimeException("AgentLoadException not thrown");
        } catch (AgentLoadException ex) {
            System.out.println("AgentLoadException thrown as expected:");
            ex.printStackTrace(System.out);
            String msg = ex.getMessage();
            // Attach agent prints general "<agent> was not loaded." message on error.
            // But additionally we expect detailed message with the reason.
            String parts[] = msg.split("was not loaded.");
            if (parts.length < 2 || parts[1].isEmpty()) {
                throw new RuntimeException("AgentLoadException message is vague");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new LingeredApp();
            LingeredApp.startApp(theApp, "-XX:+EnableDynamicAgentLoading");

            VirtualMachine vm = VirtualMachine.attach(Long.toString(theApp.getPid()));

            // absolute path
            test(() -> vm.loadAgentPath(jvmtiAgentPath));
            // relative path
            test(() -> vm.loadAgentLibrary(jvmtiAgentLib));

        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

}
