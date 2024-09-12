/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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
 * @bug 8334492
 * @summary Test to verify jcmd accepts %p in output filenames and substitutes for PID
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestJcmdPIDSubstitution
 *
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.Platform;

public class TestJcmdPIDSubstitution {

    private static final String FILENAME = "myfile%p";

    public static void main(String[] args) throws Exception {
        verifyOutputFilenames("Thread.dump_to_file", FILENAME);
        verifyOutputFilenames("GC.heap_dump", FILENAME);
        if (Platform.isLinux()) {
            verifyOutputFilenames("Compiler.perfmap", FILENAME);
            verifyOutputFilenames("System.dump_map", "-F=%s".formatted(FILENAME));
        }
    }

    private static void verifyOutputFilenames(String... args) throws Exception {
        long pid = ProcessTools.getProcessId();
        String test_dir = System.getProperty("test.dir", ".");
        Path path = Paths.get("%s/myfile%d".formatted(test_dir, pid));
        OutputAnalyzer output = JcmdBase.jcmd(args);
        output.shouldHaveExitValue(0);
        if (Files.exists(path)) {
            Files.delete(path);
        } else {
            throw new Exception("File %s was not created as expected for diagnostic cmd %s".formatted(path, args[0]));
        }
    }
}
