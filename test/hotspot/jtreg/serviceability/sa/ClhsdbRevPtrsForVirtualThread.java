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
 */

/**
 * @test
 * @bug 8261848
 * @summary Test that clhsdb 'revptrs' finds references from a virtual thread's stack chunk
 * @requires vm.hasSA
 * @requires vm.continuations
 * @requires vm.gc != "Z"
 * @requires vm.gc != "Shenandoah"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox LingeredAppWithUnmountedVirtualThread
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=1200 ClhsdbRevPtrsForVirtualThread
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

public class ClhsdbRevPtrsForVirtualThread {

    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach();
        LingeredApp theApp = new LingeredAppWithUnmountedVirtualThread();
        Path addressFile = Path.of(System.getProperty("java.io.tmpdir"),
                                   theApp.getLockFileName() + ".properties").toAbsolutePath();
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();

            LingeredApp.startApp(theApp,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-Xbootclasspath/a:.",
                "-XX:InitialHeapSize=100M",
                "-D" + LingeredAppWithUnmountedVirtualThread.ADDR_FILE_PROPERTY + "=" + addressFile);
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            // the app writes the address after its System.gc()
            ProcessHandle app = ProcessHandle.of(theApp.getPid()).orElseThrow();
            if (!Utils.waitForCondition(() -> Files.exists(addressFile) || !app.isAlive(),
                                        Utils.adjustTimeout(120_000))) {
                throw new RuntimeException("Timed out waiting for " + addressFile);
            }
            if (!Files.exists(addressFile)) {
                throw new RuntimeException("LingeredApp exited before writing " + addressFile);
            }
            Properties addresses = new Properties();
            try (var in = Files.newInputStream(addressFile)) {
                addresses.load(in);
            }
            String addr = addresses.getProperty("chunkReferenced");
            if (addr == null) {
                throw new RuntimeException("No chunkReferenced address in " + addressFile);
            }

            String output = test.run(theApp.getPid(), List.of("revptrs " + addr), null, null);
            OutputAnalyzer out = new OutputAnalyzer(output);
            out.shouldNotContain("ReversePtrs: WARNING");
            out.shouldContain("jdk/internal/vm/StackChunk");
        } finally {
            try {
                LingeredApp.stopApp(theApp);
            } finally {
                Files.deleteIfExists(addressFile);
            }
        }
    }
}
