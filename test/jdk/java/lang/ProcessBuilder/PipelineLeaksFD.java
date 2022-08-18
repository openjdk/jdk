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
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.ProcessHandle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * @test
 * @bug 8289643 8291760 8291986
 * @requires os.family == "mac" | (os.family == "linux" & !vm.musl)
 * @summary File descriptor leak detection with ProcessBuilder.startPipeline
 * @run junit/othervm PipelineLeaksFD
 */

/*
 * @test
 * @bug 8289643 8291986
 * @requires os.family == "mac" | (os.family == "linux" & !vm.musl)
 * @summary File descriptor leak detection with ProcessBuilder.startPipeline
 * @run junit/othervm -Xint PipelineLeaksFD
 */

public class PipelineLeaksFD {

    private static final String OS_NAME = System.getProperty("os.name", "Unknown");

    private static final long MY_PID =  ProcessHandle.current().pid();

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

        Set<PipeRecord> pipesBefore = pipesForPid(MY_PID);
        if (pipesBefore.size() < 3) {
            System.err.println(pipesBefore);
            fail("There should be at least 3 pipes before, (0, 1, 2)");
        }
        printPipes(pipesBefore, "Before start");

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

        Set<PipeRecord> pipesAfter = pipesForPid(MY_PID);
        if (!pipesBefore.equals(pipesAfter)) {
            Set<PipeRecord> missing = new HashSet<>(pipesBefore);
            missing.removeAll(pipesAfter);
            printPipes(missing, "Missing from pipesAfter");
            Set<PipeRecord> extra = new HashSet<>(pipesAfter);
            extra.removeAll(pipesBefore);
            printPipes(extra, "Extra pipes in pipesAfter");
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

    // Test redirectErrorStream  (true/false) has the right number of pipes in use
    @ParameterizedTest()
    @MethodSource("redirectCases")
    void checkRedirectErrorStream(boolean redirectError) throws IOException {
        try (Process p = new ProcessBuilder("cat")
                .redirectErrorStream(redirectError)
                .start()) {
            System.err.printf("Parent PID; %d, Child Pid: %d\n", MY_PID, p.pid());
            final Set<PipeRecord> pipes = pipesForPid(p.pid());
            printPipes(pipes, "Parent and waiting child pipes");
            int uniquePipes = redirectError ? 8 : 9;
            assertEquals(uniquePipes, pipes.size(),
                    "wrong number of pipes for redirect: " + redirectError);
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
        } catch (InterruptedException ie) {
        }
    }

    /**
     * Collect a Set of file descriptors and identifying information.
     * To identify the pipes in use the `lsof` command is invoked and output scrapped for
     * fd's, pids, unique identities of the pipes (to match with parent).
     * The pipes used by invoking the `lsof` process are removed from set captured
     * for the parent (this test) and the child (waiting).
     * @return A set of PipeRecords, possibly empty
     */
    static Set<PipeRecord> pipesForPid(long pid) throws IOException {
        try (Process p = new ProcessBuilder("lsof")
                .redirectErrorStream(true)
                .start()) {
            long lsofPid = p.pid();
            List<String> lines = p.inputReader().readAllLines();

            // Collect all the pipes for the three processes (parent, waiting child, lsof)
            Set<PipeRecord> pipes = lines.stream()
                    .map(PipelineLeaksFD::pipeFromLSOF)
                    .filter(pr -> pr != null &&
                            (pr.pid() == pid || pr.pid() == MY_PID || pr.pid() == lsofPid))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // Extract the `lsof` pipe keys and remove those pipes from the set
            List<KeyedString> lsofPipeNames = pipes.stream()
                    .filter(pr1 -> pr1.pid() == lsofPid)
                    .map(PipeRecord::myKey)
                    .toList();
            pipes.removeIf(p1 -> lsofPipeNames.contains(p1.myKey()));
            return pipes;
        }
    }

    // Return pipe records by parsing the appropriate platform specific `lsof` output.
    static PipeRecord pipeFromLSOF(String s) {
        return switch (OS_NAME) {
            case "Linux" -> pipeFromLinuxLSOF(s);
            case "Mac OS X" -> pipeFromMacLSOF(s);
            default -> throw new RuntimeException("lsof not supported on platform: " + OS_NAME);
        };
    }

    // Return Pipe from lsof output put, or null (on Mac OS X)
    // lsof      55221 rriggs    0      PIPE 0xc76402237956a5cb      16384  ->0xfcb0c07ae447908c
    // lsof      55221 rriggs    1      PIPE 0xb486e02f86da463e      16384  ->0xf94eacc85896b4e6
    static PipeRecord pipeFromMacLSOF(String s) {
        String[] fields = s.split("\\s+");
        if ("PIPE".equals(fields[4])) {
            final int pid = Integer.parseInt(fields[1]);
            final String myKey = (fields.length > 5) ? fields[5] : "";
            final String otherKey = (fields.length > 7) ? fields[7].substring(2) : "";
            return PipeRecord.lookup(Integer.parseInt(fields[3]), myKey, otherKey, pid);
        }
        return null;
    }

    // Return Pipe from lsof output put, or null (on Linux)
    // java    7612 rriggs   14w  FIFO   0,12       0t0   117662267 pipe
    // java    7612 rriggs   15r  FIFO   0,12       0t0   117662268 pipe
    static PipeRecord pipeFromLinuxLSOF(String s) {
        String[] fields = s.split("\\s+");
        if ("FIFO".equals(fields[4])) {
            final int pid = Integer.parseInt(fields[1]);
            final String key = (fields.length > 7) ? fields[7] : "";
            final int fdNum = Integer.parseInt(fields[3].substring(0, fields[3].length() - 1));
            return PipeRecord.lookup(fdNum, key, null, pid);
        }
        return null;
    }

    // Identify a pipe by pid, fd, and a key (unique across processes)
    // Mac OS X has separate keys for read and write sides, both are matched to the same "name"
    record PipeRecord(long pid, int fd, KeyedString myKey) {
        static PipeRecord lookup(int fd, String myKey, String otherKey, int pid) {
            return new PipeRecord(pid, fd, KeyedString.getKey(myKey, otherKey));
        }
    }

    // A unique name for a string with a count of uses
    // Used to associate pipe between parent and child.
    static class KeyedString {
        private static final HashMap<String, KeyedString> map = new HashMap<>();
        private static int nextInt = 1;
        private final String key;
        private final String name;
        private int count;
        KeyedString(String key, String name) {
            this.key = key;
            this.name = name;
            this.count = 0;
        }

        KeyedString(String s) {
            String k = "p" + nextInt++;
            this(s, k);
        }

        static KeyedString getKey(String key, String otherKey) {
            var k = map.computeIfAbsent(key, KeyedString::new);
            k.count++;
            if (otherKey != null) {
                map.putIfAbsent(otherKey, k);
            }
            return k;
        }

        public String toString() {
            return name + "(" + count + ")";
        }
    }
}
