/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8147615
 * @summary Test whether an unreferenced FileChannel is actually cleaned
 * @requires (os.family == "linux") | (os.family == "mac") | (os.family == "solaris") | (os.family == "aix")
 * @modules java.management
 * @run main/othervm CleanerTest
 */

import com.sun.management.UnixOperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class CleanerTest {
    public static void main(String[] args) throws Throwable {
        OperatingSystemMXBean mxBean =
            ManagementFactory.getOperatingSystemMXBean();
        UnixOperatingSystemMXBean unixMxBean = null;
        if (mxBean instanceof UnixOperatingSystemMXBean) {
            unixMxBean = (UnixOperatingSystemMXBean)mxBean;
        } else {
            System.out.println("Non-Unix system: skipping test.");
            return;
        }

        Path path = Paths.get(System.getProperty("test.dir", "."), "junk");
        try {
            FileChannel fc = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);

            ReferenceQueue refQueue = new ReferenceQueue();
            Reference fcRef = new PhantomReference(fc, refQueue);

            long fdCount0 = unixMxBean.getOpenFileDescriptorCount();
            fc = null;

            // Perform repeated GCs until the reference has been enqueued.
            do {
                Thread.sleep(1);
                System.gc();
            } while (refQueue.poll() == null);

            // Loop until the open file descriptor count has been decremented.
            while (unixMxBean.getOpenFileDescriptorCount() > fdCount0 - 1) {
                Thread.sleep(1);
            }

            long fdCount = unixMxBean.getOpenFileDescriptorCount();
            if (fdCount != fdCount0 - 1) {
                throw new RuntimeException("FD count expected " +
                    (fdCount0 - 1) + "; actual " + fdCount);
            }
        } finally {
            Files.delete(path);
        }
    }
}
