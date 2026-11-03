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

/*
 * @test
 * @summary Agent startup failures identify the library and preserve loader diagnostics
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native TestAgentLibraryLoadErrors
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAgentLibraryLoadErrors {
    public static void main(String[] args) throws Exception {
        testMissingAgentOnLoad();
        testInvalidAgentLibrary();
    }

    // Command-line startup rejects an agent library without Agent_OnLoad.
    private static void testMissingAgentOnLoad() throws Exception {
        String attachOnly = AgentTestSupport.library("OnAttachAgent");
        launch(attachOnly)
            .shouldNotHaveExitValue(0)
            .shouldContain("no Agent_OnLoad entry point")
            .shouldContain(attachOnly)
            .shouldNotContain("%s");
    }

    // An invalid agent library is rejected with its path and the native loader's explanation.
    private static void testInvalidAgentLibrary() throws Exception {
        Path textFile = Files.createTempFile(Path.of("."), "not-an-agent-", ".bin").toAbsolutePath();
        try {
            Files.writeString(textFile, "not executable");
            String prefix = "Could not find agent library " + textFile + " in absolute path, with error: ";
            launch(textFile.toString()).shouldNotHaveExitValue(0).shouldMatch(Pattern.quote(prefix) + "[^\\r\\n]+");
        } finally {
            Files.deleteIfExists(textFile);
        }
    }

    private static OutputAnalyzer launch(String library) throws Exception {
        return ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder("-agentpath:" + library, "-version"));
    }
}
