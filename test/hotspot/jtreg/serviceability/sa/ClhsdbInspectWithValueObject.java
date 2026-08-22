/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.SA.SATestUtils;

import jtreg.SkippedException;

/**
 * @test
 * @bug 8390106
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 *
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @build LingeredAppWithValueObject
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=480 ClhsdbInspectWithValueObject
 */

public class ClhsdbInspectWithValueObject {

    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach();

        LingeredAppWithValueObject theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            theApp = new LingeredAppWithValueObject();
            LingeredApp.startApp(
                theApp,
                "--enable-preview",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-Xbootclasspath/a:.",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseEpsilonGC" // Use Epsilon GC to prevent object migration
            );
            theApp.waitAppReadyOrCrashed();

            String addrInHex = Files.readString(LingeredAppWithValueObject.ADDR_FILE_PATH);
            String cmd = "inspect " + addrInHex;
            var expStrMap = Map.of(cmd, List.of(
              "a: 1",
              "b: 2",
              "rec:",
                "recA: 10",
                "recB: 20",
              "c: 3"
            ));
            test.run(theApp.getPid(), List.of(cmd), expStrMap, null);
        } finally {
            try {
                LingeredApp.stopApp(theApp);
            } catch (IOException e) {
                if (theApp.getOutput().getStderr().contains("OutOfMemoryError")) {
                    throw new SkippedException("No memory to test - LingeredApp for this test requires Epsilon GC", e);
                }
            }
        }
    }
}
