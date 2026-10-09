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
 * @bug 8392135
 * @summary Equivalent paths must configure a single log file output
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver TestEquivalentLogFilePaths
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestEquivalentLogFilePaths {
    public static void main(String[] args) throws Exception {
        String name = "test-8392135.log";
        Path log = Path.of(name);
        List<Path> logs;
        try (Stream<Path> files = Files.list(Path.of("."))) {
            logs = files.filter(p -> p.getFileName().toString().startsWith(name)).toList();
        }
        for (Path file : logs) {
            Files.delete(file);
        }

        String quote = Platform.isWindows() ? "\\\"" : "\"";
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xlog:gc=info:" + name,
                "-Xlog:class+load=info:" + quote + log.toAbsolutePath() + quote,
                "-Xlog:safepoint=info:./" + name,
                "-version");
        new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);

        try (Stream<Path> files = Files.list(Path.of("."))) {
            logs = files.filter(p -> p.getFileName().toString().startsWith(name)).toList();
        }
        Asserts.assertEQ(logs.size(), 1, "The paths should not create rotated log files");
        String contents = Files.readString(log);
        Asserts.assertTrue(contents.contains("[gc]"), "GC messages are missing");
        Asserts.assertTrue(contents.contains("[class,load]"), "Class load messages are missing");
    }
}
