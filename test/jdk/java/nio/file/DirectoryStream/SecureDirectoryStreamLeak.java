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

/* @test
 * @bug 8392711
 * @summary SecureDirectoryStream.newDirectoryStream should not leak file descriptors
 * @requires (os.family == "linux" | os.family == "mac" | os.family == "aix")
 * @modules jdk.management
 * @run junit/othervm SecureDirectoryStreamLeak
 */

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;

import com.sun.management.UnixOperatingSystemMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SecureDirectoryStreamLeak {
    @Test
    void testNoFileDescriptorLeak(@TempDir Path dir) throws IOException {
        Path entry = Path.of("file");
        Files.createFile(dir.resolve(entry));

        UnixOperatingSystemMXBean bean = ManagementFactory.getPlatformMXBean(
                UnixOperatingSystemMXBean.class);
        assumeTrue(bean != null);

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            assumeTrue(ds instanceof SecureDirectoryStream<?>);

            SecureDirectoryStream<Path> stream = (SecureDirectoryStream<Path>) ds;

            // Warm up exception and native paths before taking the baseline.
            assertThrows(IOException.class, () -> stream.newDirectoryStream(entry));

            long before = bean.getOpenFileDescriptorCount();
            assertThrows(IOException.class, () -> stream.newDirectoryStream(entry));
            long after = bean.getOpenFileDescriptorCount();

            assertEquals(before, after);
        }
    }
}