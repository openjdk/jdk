/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test id=tmp
 * @bug 8011536 8151430 8316304 8334339
 * @summary Basic test for creationTime attribute on platforms/file systems
 *     that support it, test directory is /tmp.
 * @library  ../.. /test/lib
 * @build jdk.test.lib.Platform
 * @run main CreationTime
 */

/* @test id=cwd
 * @bug 8011536 8151430 8316304 8334339
 * @summary Basic test for creationTime attribute on platforms/file systems
 *     that support it, test directory is JTwork/scratch. The JTwork/scratch
 *     directory maybe at diff disk partition to /tmp on linux
 * @library  ../.. /test/lib
 * @build jdk.test.lib.Platform
 * @run main CreationTime .
 */

import java.lang.foreign.Linker;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import jdk.test.lib.Platform;
import jtreg.SkippedException;

public class CreationTime {

    private static final java.io.PrintStream err = System.err;

    /**
     * Reads the creationTime attribute
     */
    private static FileTime creationTime(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class).creationTime();
    }

    /**
     * Sets the creationTime attribute
     */
    private static void setCreationTime(Path file, FileTime time) throws IOException {
        BasicFileAttributeView view =
            Files.getFileAttributeView(file, BasicFileAttributeView.class);
        view.setTimes(null, null, time);
    }

    /**
     * read the output of linux command `stat -c "%w" file`, if the output is "-",
     * then the file system doesn't support birth time 
     */
    public static boolean supportBirthTimeOnLinux(Path file) {
        try {
            String filePath = file.toAbsolutePath().toString();
            ProcessBuilder pb = new ProcessBuilder("stat", "-c", "%w", filePath);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l = b.readLine();
            if (l != null && l.equals("-")) { return false; }
        } catch(Exception e) {
        }
        return true;
    }

    static void test(Path top) throws IOException {
        Path file = Files.createFile(top.resolve("foo"));

        /**
         * Check that creationTime reported
         */
        FileTime creationTime = creationTime(file);
        Instant now = Instant.now();
        if (Math.abs(creationTime.toMillis()-now.toEpochMilli()) > 10000L) {
            System.out.println("creationTime.toMillis() == " + creationTime.toMillis());
            if(0 == creationTime.toMillis() && Platform.isLinux() && !supportBirthTimeOnLinux(file) ) {
                throw new SkippedException("birth time not support for: " + file);
            } else {
                err.println("File creation time reported as: " + creationTime);
                throw new RuntimeException("Expected to be close to: " + now);
            }
        }

        /**
         * Is the creationTime attribute supported here?
         */
        boolean supportsCreationTimeRead = false;
        boolean supportsCreationTimeWrite = false;
        if (Platform.isOSX()) {
            String type = Files.getFileStore(file).type();
            if (type.equals("apfs") || type.equals("hfs")) {
                supportsCreationTimeRead = true;
                supportsCreationTimeWrite = true;
            }
        } else if (Platform.isWindows()) {
            String type = Files.getFileStore(file).type();
            if (type.equals("NTFS") || type.equals("FAT")) {
                supportsCreationTimeRead = true;
                supportsCreationTimeWrite = true;
            }
        } else if (Platform.isLinux()) {
            // Creation time read depends on statx system call support
            supportsCreationTimeRead = Linker.nativeLinker().defaultLookup().find("statx").isPresent();
            // Creation time updates are not supported on Linux
            supportsCreationTimeWrite = false;
        }
        System.out.println(top + " supportsCreationTimeRead == " + supportsCreationTimeRead);

        /**
         * If the creation-time attribute is supported then change the file's
         * last modified and check that it doesn't change the creation-time.
         */
        if (supportsCreationTimeRead) {
            // change modified time by +1 hour
            Instant plusHour = Instant.now().plusSeconds(60L * 60L);
            Files.setLastModifiedTime(file, FileTime.from(plusHour));
            FileTime current = creationTime(file);
            if (!current.equals(creationTime))
                throw new RuntimeException("Creation time should not have changed");
        }

        /**
         * If the creation-time attribute is supported and can be changed then
         * check that the change is effective.
         */
        if (supportsCreationTimeWrite) {
            // change creation time by -1 hour
            Instant minusHour = Instant.now().minusSeconds(60L * 60L);
            creationTime = FileTime.from(minusHour);
            setCreationTime(file, creationTime);
            FileTime current = creationTime(file);
            if (Math.abs(creationTime.toMillis()-current.toMillis()) > 1000L)
                throw new RuntimeException("Creation time not changed");
        }
    }

    public static void main(String[] args) throws IOException {
        // create temporary directory to run tests
        Path dir;
        if(0 == args.length) {
            dir = TestUtil.createTemporaryDirectory();
        } else {
            dir = TestUtil.createTemporaryDirectory(args[0]);
        }
        try {
            test(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
