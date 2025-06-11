/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8287008
 * @summary Basic test for jcmd Thread.dump_to_file
 * @modules jdk.jcmd
 * @library /test/lib
 * @run junit/othervm ThreadDumpToFileTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.threaddump.ThreadDump;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadDumpToFileTest {

    /**
     * Test thread dump, should be in plain text format.
     */
    @Test
    void testThreadDump() throws IOException {
        Path file = genThreadDumpPath(".txt");
        testPlainThreadDump(file);
    }

    /**
     * Test thread dump in plain text format.
     */
    @Test
    void testPlainThreadDump() throws IOException {
        Path file = genThreadDumpPath(".txt");
        testPlainThreadDump(file, "-format=plain");
    }

    /**
     * Test thread dump in JSON format.
     */
    @Test
    void testJsonThreadDump() throws IOException {
        Path file = genThreadDumpPath(".json");
        jcmdThreadDumpToFile(file, "-format=json")
                .shouldMatch("Created");

        // parse the JSON text
        String jsonText = Files.readString(file);
        ThreadDump threadDump = ThreadDump.parse(jsonText);

        // test that the process id is this process
        assertTrue(threadDump.processId() == ProcessHandle.current().pid());

        // test that the current thread is in the root thread container
        var rootContainer = threadDump.rootThreadContainer();
        var tid = Thread.currentThread().threadId();
        rootContainer.findThread(tid).orElseThrow();
    }

    /**
     * Test that an existing file is not overwritten.
     */
    @Test
    void testDoNotOverwriteFile() throws IOException {
        Path file = genThreadDumpPath(".txt");
        Files.writeString(file, "xxx");

        jcmdThreadDumpToFile(file, "")
                .shouldMatch("exists");

        // file should not be overridden
        assertEquals("xxx", Files.readString(file));
    }

    /**
     * Test overwriting an existing file.
     */
    @Test
    void testOverwriteFile() throws IOException {
        Path file = genThreadDumpPath(".txt");
        Files.writeString(file, "xxx");
        jcmdThreadDumpToFile(file, "-overwrite")
                .shouldMatch("Created");
    }

    /**
     * Test output file cannot be created.
     */
    @Test
    void testFileCreateFails() throws IOException {
        Path badFile = Path.of(".").toAbsolutePath()
                .resolve("does-not-exist")
                .resolve("does-not-exist")
                .resolve("threads.bad");
        jcmdThreadDumpToFile(badFile, "-format=plain")
                .shouldMatch("Failed");
        jcmdThreadDumpToFile(badFile, "-format=json")
                .shouldMatch("Failed");
    }

    /**
     * Test thread dump in plain text format.
     */
    private void testPlainThreadDump(Path file, String... options) throws IOException {
        jcmdThreadDumpToFile(file, options).shouldMatch("Created");

        // test that thread dump contains the name and id of the current thread
        String name = Thread.currentThread().getName();
        long tid = Thread.currentThread().threadId();
        String expected = "#" + tid + " \"" + name + "\"";
        assertTrue(find(file, expected), expected + " not found in " + file);
    }

    /**
     * Generate a file path with the given suffix to use for the thread dump.
     */
    private Path genThreadDumpPath(String suffix) throws IOException {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "threads-", suffix);
        Files.delete(file);
        return file;
    }

    /**
     * Launches jcmd Thread.dump_to_file to obtain a thread dump of this VM.
     */
    private OutputAnalyzer jcmdThreadDumpToFile(Path file, String... options) {
        String cmd = "Thread.dump_to_file";
        for (String option : options) {
            cmd += " " + option;
        }
        return new PidJcmdExecutor().execute(cmd + " " + file);
    }

    /**
     * Returns true if the given file contains a line with the string.
     */
    private boolean find(Path file, String text) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            return  stream.anyMatch(line -> line.indexOf(text) >= 0);
        }
    }
}
