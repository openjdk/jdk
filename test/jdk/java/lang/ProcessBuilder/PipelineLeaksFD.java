/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * @test
 * @bug 8289643
 * @requires (os.family == "linux" & !vm.musl)
 * @summary file descriptor leak with ProcessBuilder.startPipeline
 * @run testng/othervm PipelineLeaksFD
 */

@Test
public class PipelineLeaksFD {
    @DataProvider
    public Object[][] builders() {
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

    @Test(dataProvider = "builders")
    void checkForLeaks(List<ProcessBuilder> builders) throws IOException {

        Set<PipeRecord> pipesBefore = myPipes();
        if (pipesBefore.size() < 3) {
            System.out.println(pipesBefore);
            Assert.fail("There should be at least 3 pipes before, (0, 1, 2)");
        }

        List<Process> processes = ProcessBuilder.startPipeline(builders);

        OutputStream out = processes.get(0).getOutputStream();
        out.write('a');
        out.close();

        Process last = processes.get(processes.size() - 1);
        try (InputStream inputStream = last.getInputStream();
             InputStream errorStream = last.getErrorStream()) {
            byte[] bytes = inputStream.readAllBytes();
            Assert.assertEquals(bytes.length, 1, "stdout bytes read");
            byte[] errBytes = errorStream.readAllBytes();
            Assert.assertEquals(errBytes.length, 0, "stderr bytes read");
        }

        processes.forEach(p -> waitForQuiet(p));

        Set<PipeRecord> pipesAfter = myPipes();
        printPipes(pipesAfter, "DEBUG: All Pipes After");
        if (!pipesBefore.equals(pipesAfter)) {
            Set<PipeRecord> missing = new HashSet<>(pipesBefore);
            missing.removeAll(pipesAfter);
            printPipes(missing, "Missing from pipesAfter");
            Set<PipeRecord> extra = new HashSet<>(pipesAfter);
            extra.removeAll(pipesBefore);
            printPipes(extra, "Extra pipes in pipesAfter");
            Assert.fail("More or fewer pipes than expected");
        }
    }

    static void printPipes(Set<PipeRecord> pipes, String label) {
        System.out.printf("%s: [%d]%n", label, pipes.size());
        pipes.forEach(r -> System.out.printf("%-20s: %s%n", r.fd(), r.link()));
    }

    static void waitForQuiet(Process p) {
        try {
            int st = p.waitFor();
            if (st != 0) {
                System.out.println("non-zero exit status: " + p);
            }
        } catch (InterruptedException ie) {
        }
    }

    /**
     * Collect a Set of pairs of /proc fd paths and the symbol links that are pipes.
     * @return A set of PipeRecords, possibly empty
     */
    static Set<PipeRecord> myPipes() {
        Path path = Path.of("/proc/" + ProcessHandle.current().pid() + "/fd");
        Set<PipeRecord> pipes = new HashSet<>();
        File[] files = path.toFile().listFiles(f -> Files.isSymbolicLink(f.toPath()));
        if (files != null) {
            for (File file : files) {
                try {
                    Path link = Files.readSymbolicLink(file.toPath());
                    if (link.toString().startsWith("pipe:")) {
                        pipes.add(new PipeRecord(file.toPath(), link));
                    }
                } catch (IOException ioe) {
                }
            }
        }
        return pipes;
    }

    record PipeRecord(Path fd, Path link) { };
}
