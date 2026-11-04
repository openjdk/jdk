/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public final class AgentTestSupport {
    public static String library(String name) {
        Path path = Path.of(Utils.TEST_NATIVE_PATH, System.mapLibraryName(name)).toAbsolutePath();
        Asserts.assertTrue(Files.isRegularFile(path), "Missing library: " + path);
        return path.toString();
    }

    // Self-attach using the options string <operation>, <slot>, <value> and check the Agent_OnAttach return code.
    // Value is the expected status, except that visit-environment-success expects zero.
    public static void attach(String library, String options) throws Exception {
        String[] fields = options.split(",\\s*");
        int status = fields[0].equals("visit-environment-success") ? 0 : Integer.parseInt(fields[2]);
        VirtualMachine vm = VirtualMachine.attach(Long.toString(ProcessHandle.current().pid()));
        try {
            vm.loadAgentPath(library, options);
            Asserts.assertEquals(status, 0, "Expected failure for " + options);
        } catch (AgentInitializationException e) {
            Asserts.assertNE(status, 0, "Unexpected failure for " + options);
            Asserts.assertEquals(e.returnValue(), status, "Status for " + options);
        } finally {
            vm.detach();
        }
    }

    // Launch a fresh JVM and wait for completion, returning its captured output and exit code.
    // The caller checks whether the process succeeded.
    public static OutputAnalyzer launch(Class<?> main, List<String> startup, String... args) throws Exception {
        List<String> command = new ArrayList<>(startup);
        command.add("-XX:+EnableDynamicAgentLoading");
        return launchWithoutAgentLoadingOptIn(main, command, args);
    }

    // Unlike launch(), do not add EnableDynamicAgentLoading:
    // leave the VM default available for testing warning output.
    public static OutputAnalyzer launchWithoutAgentLoadingOptIn(Class<?> main,
                                                                List<String> startup,
                                                                String... args) throws Exception {
        List<String> command = new ArrayList<>(startup);
        command.add("-Djdk.attach.allowAttachSelf=true");
        command.add("-Dtest.nativepath=" + Utils.TEST_NATIVE_PATH);
        command.add("-Dtest.timeout.factor=" + System.getProperty("test.timeout.factor", "1.0"));
        command.add(main.getName());
        command.addAll(List.of(args));
        return ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder(command));
    }

    // Make test agent libraries discoverable by the platform loader for -agentlib and -Xrun.
    public static void addNativeLibraryPath(ProcessBuilder process) {
        String variable = Platform.sharedLibraryPathVariableName();
        String previous = process.environment().get(variable);
        String nativePath = Path.of(Utils.TEST_NATIVE_PATH).toAbsolutePath().toString();
        process.environment().put(variable, previous == null || previous.isEmpty()
                ? nativePath : nativePath + File.pathSeparator + previous);
    }

    // Check for exactly one stdout marker from the native test library's Agent_OnUnload.
    // This verifies the notification, not whether the OS unmapped the library.
    public static void unloadedOnce(OutputAnalyzer output, String library) {
        long count = output.getStdout().lines().filter(("unloaded:" + library)::equals).count();
        Asserts.assertEquals(count, 1L, "Unload count for " + library + "\n" + output.getOutput());
    }

    private AgentTestSupport() { }
}
