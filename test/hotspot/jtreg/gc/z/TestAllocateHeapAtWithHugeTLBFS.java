/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/*
 * @test TestAllocateHeapAtWithHugeTLBFS
 * @requires vm.gc.Z & os.family == "linux"
 * @summary Test ZGC with -XX:AllocateHeapAt and -XX:+UseLargePages
 * @library /test/lib
 * @run driver gc.z.TestAllocateHeapAtWithHugeTLBFS true
 * @run driver gc.z.TestAllocateHeapAtWithHugeTLBFS false
 */

import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

public class TestAllocateHeapAtWithHugeTLBFS {
    static String find_hugetlbfs_mountpoint() {
        Pattern pat = Pattern.compile("\\d+ \\d+ \\d+:\\d+ \\S+ (\\S+) [^-]*- hugetlbfs (.+)");
        try (Scanner scanner = new Scanner(new File("/proc/self/mountinfo"))) {
            while (scanner.hasNextLine()) {
                final Matcher mat = pat.matcher(scanner.nextLine());
                if (mat.matches() && mat.group(2).contains("pagesize=2M")) {
                    final Path path = Paths.get(mat.group(1));
                    if (Files.isReadable(path) &&
                        Files.isWritable(path) &&
                        Files.isExecutable(path)) {
                        // Found a usable mount point.
                        return path.toString();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not open /proc/self/mountinfo");
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        final boolean exists = Boolean.parseBoolean(args[0]);
        final String directory = exists ? find_hugetlbfs_mountpoint()
                                        : "non-existing-directory";
        if (directory == null) {
            throw new SkippedException("No valid hugetlbfs mount point found");
        }
        final String heapBackingFile = "Heap Backing File: " + directory;
        final String failedToCreateFile = "Failed to create file " + directory;

        ProcessTools.executeTestJava(
            "-XX:+UseZGC",
            "-Xlog:gc*",
            "-Xms32M",
            "-Xmx32M",
            "-XX:+UseLargePages",
            "-XX:AllocateHeapAt=" + directory,
            "-version")
                .shouldContain(exists ? heapBackingFile : failedToCreateFile)
                .shouldNotContain(exists ? failedToCreateFile : heapBackingFile)
                .shouldHaveExitValue(exists ? 0 : 1);
    }
}
