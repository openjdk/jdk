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
 * @bug 8334492 8204681
 * @summary Test to verify jcmd accepts %p and %t in output filenames and substitutes for PID and timestamp respectively.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestJcmdArgumentSubstitution
 *
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Platform;

public class TestJcmdArgumentSubstitution {

    private static final String PID_FILENAME = "myfile%p";
    private static final String TIMESTAMP_FILENAME = "myfile%t";

    public static void main(String[] args) throws Exception {
        verifyFilenamePidSusbtitution("Thread.dump_to_file", PID_FILENAME);
        verifyFilenamePidSusbtitution("GC.heap_dump", PID_FILENAME);
        verifyFilenameTimestampSubstitution("Thread.dump_to_file", TIMESTAMP_FILENAME);
        verifyFilenameTimestampSubstitution("GC.heap_dump", TIMESTAMP_FILENAME);
        if (Platform.isLinux()) {
            verifyFilenamePidSusbtitution("Compiler.perfmap", PID_FILENAME);
            verifyFilenamePidSusbtitution("System.dump_map", "-F=%s".formatted(PID_FILENAME));
            verifyFilenameTimestampSubstitution("Compiler.perfmap", TIMESTAMP_FILENAME);
            verifyFilenameTimestampSubstitution("System.dump_map", "-F=%s".formatted(TIMESTAMP_FILENAME));
        }
    }

    private static void verifyFilenamePidSusbtitution(String... args) throws Exception {
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

    private static void verifyFilenameTimestampSubstitution(String... args) throws Exception {
        String test_dir = System.getProperty("test.dir", ".");
        OutputAnalyzer output = JcmdBase.jcmd(args);
        output.shouldHaveExitValue(0);

        boolean found = false;
        Pattern pattern = Pattern.compile("myfile\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}");
        File[] files = new File(test_dir).listFiles();
        if(files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    found = true;
                }
                Files.delete(file.toPath());
            }
        }

        if (!found) {
            throw new Exception("File %s was not created as expected for diagnostic cmd %s".formatted(args[1], args[0]));
        }
    }
}
