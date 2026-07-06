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
 * @bug 8386334
 * @summary Check that `lib/jrt-fs.jar` and `lib/modules` are properly closed while
 *          jdeps is invoked with `--system` option.
 * @requires os.family == "mac" | os.family == "linux"
 * @modules jdk.jdeps
 * @library lib
 * @build JdepsRunner
 * @run junit ${test.main.class}
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class SystemFilesClosed {

    private Path base;

    @Test
    void testSystemFilesClosed() throws Exception {
        // Probe lsof availability before doing the jlink/jdeps work
        if (!lsofCommand().isPresent()) {
            Assumptions.abort("lsof command is not available on this system");
        }

        String targetSystem = base.toString();
        int ret = java.util.spi.ToolProvider.findFirst("jlink")
                .orElseThrow()
                .run(System.out, System.err, "--add-modules", "java.base", "--output", targetSystem);
        if (ret != 0) {
            System.out.println("It is most probably an exploded build. Skip testing.");
            return;
        }

        JdepsRunner jdeps = new JdepsRunner("--check", "java.base", "--system", targetSystem);
        Assertions.assertEquals(0, jdeps.run(true), "Jdeps task failed");

        Process process = new ProcessBuilder()
                .command(lsofCommand().orElseThrow(() -> new RuntimeException("lsof command is not available on this system")),
                        "-p", String.valueOf(ProcessHandle.current().pid()))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        List<String> lines;
        String realPath = base.toRealPath().toString();
        try (InputStream stdout = process.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
            lines = reader.lines().filter(line -> line.contains(realPath)).toList();
        }
        process.waitFor();
        Assertions.assertEquals(0, lines.size(), "File(s) remain opened: " + lines);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }

    static Optional<String> lsofCommandCache = Arrays.stream(new String[] {
            "/usr/bin/lsof",
            "/usr/sbin/lsof",
            "/bin/lsof",
            "/sbin/lsof",
            "/usr/local/bin/lsof"})
        .filter(args -> new File(args).exists())
        .findFirst();

    static Optional<String> lsofCommand() {
        return lsofCommandCache;
    }
}
