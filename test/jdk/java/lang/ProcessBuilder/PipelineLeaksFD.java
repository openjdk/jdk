/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test id=FORK
 * @bug 8289643 8291760 8291986
 * @requires os.family == "mac" | os.family == "linux"
 * @summary File descriptor leak detection with ProcessBuilder.startPipeline
 * @library /test/lib
 * @run junit/othervm/timeout=240 -Djdk.lang.Process.launchMechanism=FORK PipelineLeaksFD
 */

/*
 * @test id=POSIX_SPAWN
 * @bug 8289643 8291760 8291986 8379182
 * @requires os.family == "mac" | os.family == "linux"
 * @summary File descriptor leak detection with ProcessBuilder.startPipeline
 * @library /test/lib
 * @run junit/othervm/native/timeout=240 -Djdk.lang.Process.launchMechanism=POSIX_SPAWN PipelineLeaksFD
 */

public class PipelineLeaksFD {

    private static final String TEST_JDK = System.getProperty("test.jdk");

    private static final String JAVA_LIBRARY_PATH = System.getProperty("java.library.path");

    private static final long MY_PID =  ProcessHandle.current().pid();

    // Maximum file descriptor to probe for being a pipe,
    private static final int MAX_FD = 100;

    // Test cases for pipelines with a number of pipeline sequences
    public static Object[][] builders() {
        return new Object[][]{
                {List.of(new ProcessBuilder("cat"))},
                {List.of(new ProcessBuilder("cat"),
                        new ProcessBuilder("cat"))},
                {List.of(new ProcessBuilder("cat"),
                        new ProcessBuilder("cat"),
                        new ProcessBuilder("cat"),
                        new ProcessBuilder("cat"),
                        new ProcessBuilder("cat"))},
        };
    }

    @ParameterizedTest
    @MethodSource("builders")
    void checkForLeaks(List<ProcessBuilder> builders) throws IOException {

        System.out.println("Using:" + System.getProperty("jdk.lang.Process.launchMechanism"));
        Set<PipeRecord> beforePipes = pipesFromSelf();
        if (beforePipes.size() < 3) {
            fail("There should be at least 3 pipes before, (0, 1, 2): is " +
                    beforePipes.size());
        }
        printPipes(beforePipes, "Before start");

        List<Process> processes = ProcessBuilder.startPipeline(builders);

        // Write something through the pipeline
        final String text = "xyz";
        try (Writer out = processes.getFirst().outputWriter()) {
            out.write(text);
        }

        // Read, check, and close all streams
        for (int i = 0; i < processes.size(); i++) {
            final Process p = processes.get(i);
            String expectedOut = (i == processes.size() - 1) ? text : null;
            String expectedErr = null;      // EOF
            try (BufferedReader inputStream = p.inputReader();
                 BufferedReader errorStream = p.errorReader()) {
                String outActual = inputStream.readLine();
                assertEquals(expectedOut, outActual, "stdout, process[ " + i + "]: " + p);

                String errActual = errorStream.readLine();
                assertEquals(expectedErr, errActual, "stderr, process[ " + i + "]: " + p);
            }
        }

        processes.forEach(PipelineLeaksFD::waitForQuiet);

        Set<PipeRecord> afterPipes = pipesFromSelf();
        if (!beforePipes.equals(afterPipes)) {
            Set<PipeRecord> missing = new HashSet<>(beforePipes);
            missing.removeAll(afterPipes);
            printPipes(missing, "Missing from beforePipes()");
            Set<PipeRecord> extra = new HashSet<>(afterPipes);
            extra.removeAll(beforePipes);
            printPipes(extra, "Extra pipes in afterPipes()");
            fail("More or fewer pipes than expected");
        }
    }

    // Test redirectErrorStream, both true and false
    public static Object[][] redirectCases() {
        return new Object[][] {
                {true},
                {false},
        };
    }

    // Test redirectErrorStream  (true/false) has the right number of pipes in use.
    // Spawn the child to report its pipe inode info.
    @ParameterizedTest()
    @MethodSource("redirectCases")
    void checkRedirectErrorStream(boolean redirectError) {

        System.out.println("Using:" + System.getProperty("jdk.lang.Process.launchMechanism"));

        try (Process p = new ProcessBuilder(TEST_JDK + "/bin/java",
                "--enable-preview",
                "-Djava.library.path=" + JAVA_LIBRARY_PATH,
                "--enable-native-access=ALL-UNNAMED", "LinuxFDInfo")
                .redirectErrorStream(redirectError)
                .start()) {

            final Set<PipeRecord> pipes = pipesFromSelf();
            printPipes(pipes, "Parent pipes");

            List<String> lines = p.inputReader().readAllLines();
            Set<PipeRecord> childPipes = lines.stream().map(s -> {
                var fdAndInode = LinuxFDInfo.parseFdAndInode(s);
                return PipeRecord.lookup(fdAndInode.fd(), "0x%08x".formatted(fdAndInode.inode()), p.pid());
            })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            printPipes(childPipes, "child pipes");
            int uniquePipes = childPipes.stream().map(PipeRecord::myKey).collect(Collectors.toSet()).size();
            // Number of pipe references depending on whether stderr is redirected to stdout
            long expected = redirectError ? 2 : 3;
            assertEquals(expected, uniquePipes, "Wrong number of unique pipes");

            String expectedTypeName = redirectError
                    ? "java.lang.ProcessBuilder$NullInputStream"
                    : "java.lang.ProcessImpl$ProcessPipeInputStream";
            assertEquals(expectedTypeName, p.getErrorStream().getClass().getName(),
                    "errorStream type is incorrect");

            final Set<PipeRecord> afterPipes = pipesFromSelf();
            if (!pipes.equals(afterPipes)) {
                printPipes(afterPipes, "Parent pipes after are different");
            }
        } catch (IOException ioe) {
            fail("Process start", ioe);
        }
    }

    static void printPipes(Set<PipeRecord> pipes, String label) {
        System.err.printf("%s: [%d]%n", label, pipes.size());
        pipes.forEach(System.err::println);
    }

    static void waitForQuiet(Process p) {
        try {
            int st = p.waitFor();
            if (st != 0) {
                System.err.println("non-zero exit status: " + p);
            }
        } catch (InterruptedException ignore) {
        }
    }

    static Set<PipeRecord> pipesFromSelf() {
        Set<PipeRecord> pipes = new LinkedHashSet<>(MAX_FD);
        for (int fd = 0; fd < MAX_FD; fd++) {
            long inode = LinuxFDInfo.getPipeInodeNum(fd);
            if (inode != 0) {
                pipes.add(PipeRecord.lookup(fd, "0x%08x".formatted(inode), MY_PID));
            }
        }
        return pipes;
    }

    // Identify a pipe by pid, fd, and a key (unique across processes)
    // Mac OS X has separate keys for read and write sides, both are matched to the same "name"
    record PipeRecord(long pid, int fd, KeyedString myKey) {
        static PipeRecord lookup(int fd, String myKey, long pid) {
            return new PipeRecord(pid, fd, KeyedString.getKey(myKey));
        }
    }

    // A unique name for a string with a count of uses
    // Used to associate pipe between parent and child.
    static class KeyedString {
        private static final HashMap<String, KeyedString> map = new HashMap<>();
        private static int nextInt = 1;
        private final String key;
        private final String name;
        KeyedString(String key, String name) {
            this.key = key;
            this.name = name;
        }

        KeyedString(String s) {
            String k = "p" + nextInt++;
            this(s, k);
        }

        static KeyedString getKey(String key) {
            return map.computeIfAbsent(key, KeyedString::new);
        }

        public String toString() {
            return name + ": osInfo: " + key;
        }
    }
}
