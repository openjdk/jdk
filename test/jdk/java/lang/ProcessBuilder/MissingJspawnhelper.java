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

/*
 * @test
 * @bug 8396825
 * @summary POSIX_SPAWN reports ENOENT when jspawnhelper is missing
 * @requires os.family == "mac"
 * @library /test/lib
 * @run junit MissingJspawnhelper
 */

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;

import org.junit.jupiter.api.Test;

public class MissingJspawnhelper {
    @Test
    void reportsEnoentForMissingHelper() throws Exception {
        Path testJdk = Path.of(Utils.TEST_JDK).toAbsolutePath();
        Path scratch = Path.of("").toRealPath();
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        // When retaining files, jtreg may follow directory symlinks and move files out of the test JDK.
        if (tempDir.startsWith(scratch)) {
            throw new IOException("Temporary directory must be outside jtreg scratch: " + tempDir);
        }
        Path tempJavaHome = Files.createTempDirectory(tempDir, "missing-jspawnhelper-");
        try (Closeable cleanup = () -> FileUtils.deleteFileTreeWithRetry(tempJavaHome)) {
            symlinkEntriesExcept(testJdk, tempJavaHome, "lib");
            Path lib = Files.createDirectory(tempJavaHome.resolve("lib"));
            symlinkEntriesExcept(testJdk.resolve("lib"), lib, "jspawnhelper");

            // Launch a child JVM because the jspawnhelper path uses the java.home value cached at startup.
            ProcessTools.executeTestJava("-Djava.home=" + tempJavaHome,
                    "-Djdk.lang.Process.launchMechanism=POSIX_SPAWN",
                    Child.class.getName()).shouldHaveExitValue(0);
        }
    }

    private static void symlinkEntriesExcept(Path source, Path destination, String excludedName)
            throws IOException {
        try (var entries = Files.newDirectoryStream(source)) {
            for (Path entry : entries) {
                Path name = entry.getFileName();
                if (!name.toString().equals(excludedName)) {
                    Files.createSymbolicLink(destination.resolve(name), entry);
                }
            }
        }
    }

    public static class Child {
        public static void main(String[] args) throws IOException {
            try (Process process = new ProcessBuilder("/usr/bin/true").start()) {
                throw new AssertionError("Process started with jspawnhelper missing");
            } catch (IOException e) {
                if (!e.getMessage().contains("posix_spawn failed, error: 2 (")) {
                    throw new AssertionError("Expected ENOENT from posix_spawn", e);
                }
            }
        }
    }
}
