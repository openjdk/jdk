/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8181493
 * @summary Verify that nanosecond precision is maintained for file timestamps
 * @requires (os.family == "linux") | (os.family == "mac") | (os.family == "solaris")
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class SetTimesNanos {
    public static void main(String[] args) throws IOException,
        InterruptedException {

        Path dirPath = Path.of("test");
        Path dir = Files.createDirectory(dirPath);
        FileStore store = Files.getFileStore(dir);
        System.out.format("FileStore: %s on %s (%s)%n", dir, store.name(),
            store.type());
        if (System.getProperty("os.name").toLowerCase().startsWith("mac") &&
            store.type().equalsIgnoreCase("hfs")) {
            System.err.println
                ("HFS on macOS does not have nsec timestamps: skipping test");
            return;
        }
        testNanos(dir);

        Path file = Files.createFile(dir.resolve("test.dat"));
        testNanos(file);
    }

    private static void testNanos(Path path) throws IOException {
        // Set modification and access times
        // Time stamp = "2017-01-01 01:01:01.123456789";
        long timeNanos = 1_483_261_261L*1_000_000_000L + 123_456_789L;
        FileTime pathTime = FileTime.from(timeNanos, TimeUnit.NANOSECONDS);
        BasicFileAttributeView view =
            Files.getFileAttributeView(path, BasicFileAttributeView.class);
        view.setTimes(pathTime, pathTime, null);

        // Read attributes
        BasicFileAttributes attrs =
            Files.readAttributes(path, BasicFileAttributes.class);

        // Check timestamps
        String[] timeNames = new String[] {"modification", "access"};
        FileTime[] times = new FileTime[] {attrs.lastModifiedTime(),
            attrs.lastAccessTime()};
        for (int i = 0; i < timeNames.length; i++) {
            long nanos = times[i].to(TimeUnit.NANOSECONDS);
            if (nanos != timeNanos) {
                throw new RuntimeException("Expected " + timeNames[i] +
                    " timestamp to be '" + timeNanos + "', but was '" +
                    nanos + "'");
            }
        }
    }
}
