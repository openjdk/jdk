/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/**
 * @test
 * @bug 8314120
 * @summary Sanity test for FileDescriptor.sync
 * @library /test/lib
 * @run main Sync
 */

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import jdk.test.lib.thread.VThreadRunner;

public class Sync {

    static final String TEST_DIR = System.getProperty("test.dir", ".");
    static final int TRIES = 10_000;

    public static void testWith(File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            FileDescriptor fd = fos.getFD();
            for (int t = 0; t < TRIES; t++) {
                fd.sync();
            }
        } catch (SyncFailedException sfe) {
            // Can happen on some filesystems, print it in the log
            System.out.println("Sync failed (acceptable)");
            sfe.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // Run on platform threads
        System.out.println("With platform threads");
        run();

        // Run on virtual threads
        System.out.println("With virtual threads");
        VThreadRunner.run(Sync::run);

        System.out.println("Complete");
    }

    private static class AutoDelete implements AutoCloseable {
        private final File file;

        public AutoDelete(File file) {
            this.file = file;
        }

        public File file() {
            return file;
        }

        @Override
        public void close() throws Exception {
            file.delete();
        }
    }

    public static void run() throws Exception {
        try (var w = new AutoDelete(new File(TEST_DIR, "FileDescriptorSync1"))) {
            testWith(w.file());
        }

        try (var w = new AutoDelete(File.createTempFile("FileDescriptorSync2", "tmp"))) {
            testWith(w.file());
        }
    }
}
