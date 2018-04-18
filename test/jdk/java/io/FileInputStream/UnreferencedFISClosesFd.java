/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @test
 * @modules java.base/java.io:open
 * @bug 6524062
 * @summary Test to ensure that FIS.finalize() invokes the close() method as per
 * the specification.
 * @run main/othervm UnreferencedFISClosesFd
 */
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.management.UnixOperatingSystemMXBean;

/**
 * Tests for FIS unreferenced.
 *  - Not subclassed - cleaner cleanup
 *  - Subclassed no finalize or close - cleaner cleanup
 *  - Subclassed close overridden - AltFinalizer cleanup
 *  - Subclasses finalize overridden - cleaner cleanup
 *  - Subclasses finalize and close overridden - AltFinalizer cleanup
 */
public class UnreferencedFISClosesFd {

    enum CleanupType {
        CLOSE,      // Cleanup is handled via calling close
        CLEANER}    // Cleanup is handled via Cleaner

    static final String FILE_NAME = "empty.txt";

    /**
     * Subclass w/ no overrides; not finalize or close.
     * Cleanup should be via the Cleaner (not close).
     */
    public static class StreamOverrides extends FileInputStream {

        protected final AtomicInteger closeCounter;

        public StreamOverrides(String name) throws FileNotFoundException {
            super(name);
            closeCounter = new AtomicInteger(0);
        }

        final AtomicInteger closeCounter() {
            return closeCounter;
        }
    }

    /**
     * Subclass overrides close.
     * Cleanup should be via AltFinalizer calling close().
     */
    public static class StreamOverridesClose extends StreamOverrides {

        public StreamOverridesClose(String name) throws FileNotFoundException {
            super(name);
        }

        public void close() throws IOException {
            closeCounter.incrementAndGet();
            super.close();
        }
    }

    /**
     * Subclass overrides finalize.
     * Cleanup should be via the Cleaner (not close).
     */
    public static class StreamOverridesFinalize extends StreamOverrides {

        public StreamOverridesFinalize(String name) throws FileNotFoundException {
            super(name);
        }

        @SuppressWarnings({"deprecation","removal"})
        protected void finalize() throws IOException {
            super.finalize();
        }
    }

    /**
     * Subclass overrides finalize and close.
     * Cleanup should be via AltFinalizer calling close().
     */
    public static class StreamOverridesFinalizeClose extends StreamOverridesClose {

        public StreamOverridesFinalizeClose(String name) throws FileNotFoundException {
            super(name);
        }

        @SuppressWarnings({"deprecation","removal"})
        protected void finalize() throws IOException {
            super.finalize();
        }
    }

    /**
     * Main runs each test case and reports number of failures.
     */
    public static void main(String argv[]) throws Exception {

        File inFile = new File(System.getProperty("test.dir", "."), FILE_NAME);
        inFile.createNewFile();
        inFile.deleteOnExit();

        String name = inFile.getPath();

        long fdCount0 = getFdCount();
        System.out.printf("initial count of open file descriptors: %d%n", fdCount0);

        int failCount = 0;
        failCount += test(new FileInputStream(name), CleanupType.CLEANER);

        failCount += test(new StreamOverrides(name), CleanupType.CLEANER);

        failCount += test(new StreamOverridesClose(name), CleanupType.CLOSE);

        failCount += test(new StreamOverridesFinalize(name), CleanupType.CLEANER);

        failCount += test(new StreamOverridesFinalizeClose(name), CleanupType.CLOSE);

        if (failCount > 0) {
            throw new AssertionError("Failed test count: " + failCount);
        }

        // Check the final count of open file descriptors
        long fdCount = getFdCount();
        System.out.printf("final count of open file descriptors: %d%n", fdCount);
        if (fdCount != fdCount0) {
            listProcFD();
            throw new AssertionError("raw fd count wrong: expected: " + fdCount0
                    + ", actual: " + fdCount);
        }
    }

    // Get the count of open file descriptors, or -1 if not available
    private static long getFdCount() {
        OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
        return  (mxBean instanceof UnixOperatingSystemMXBean)
                ? ((UnixOperatingSystemMXBean) mxBean).getOpenFileDescriptorCount()
                : -1L;
    }

    private static int test(FileInputStream fis, CleanupType cleanType) throws Exception {

        try {
            System.out.printf("%nTesting %s%n", fis.getClass().getName());

            // Prepare to wait for FIS to be reclaimed
            ReferenceQueue<Object> queue = new ReferenceQueue<>();
            HashSet<Reference<?>> pending = new HashSet<>();
            WeakReference<FileInputStream> msWeak = new WeakReference<>(fis, queue);
            pending.add(msWeak);

            FileDescriptor fd = fis.getFD();
            WeakReference<FileDescriptor> fdWeak = new WeakReference<>(fd, queue);
            pending.add(fdWeak);

            Field fdField = FileDescriptor.class.getDeclaredField("fd");
            fdField.setAccessible(true);
            int ffd = fdField.getInt(fd);

            Field altFinalizerField = FileInputStream.class.getDeclaredField("altFinalizer");
            altFinalizerField.setAccessible(true);
            Object altFinalizer = altFinalizerField.get(fis);
            if ((altFinalizer != null) ^ (cleanType == CleanupType.CLOSE)) {
                throw new RuntimeException("Unexpected AltFinalizer: " + altFinalizer
                + ", for " + cleanType);
            }

            Field cleanupField = FileDescriptor.class.getDeclaredField("cleanup");
            cleanupField.setAccessible(true);
            Object cleanup = cleanupField.get(fd);
            System.out.printf("  cleanup: %s, alt: %s, ffd: %d, cf: %s%n",
                    cleanup, altFinalizer, ffd, cleanupField);
            if ((cleanup != null) ^ (cleanType == CleanupType.CLEANER)) {
                throw new Exception("unexpected cleanup: "
                + cleanup + ", for " + cleanType);
            }
            if (cleanup != null) {
                WeakReference<Object> cleanupWeak = new WeakReference<>(cleanup, queue);
                pending.add(cleanupWeak);
                System.out.printf("    fdWeak: %s%n    msWeak: %s%n    cleanupWeak: %s%n",
                        fdWeak, msWeak, cleanupWeak);
            }
            if (altFinalizer != null) {
                WeakReference<Object> altFinalizerWeak = new WeakReference<>(altFinalizer, queue);
                pending.add(altFinalizerWeak);
                System.out.printf("    fdWeak: %s%n    msWeak: %s%n    altFinalizerWeak: %s%n",
                        fdWeak, msWeak, altFinalizerWeak);
            }

            AtomicInteger closeCounter = fis instanceof StreamOverrides
                    ? ((StreamOverrides)fis).closeCounter() : null;

            Reference<?> r;
            while (((r = queue.remove(1000L)) != null)
                    || !pending.isEmpty()) {
                System.out.printf("    r: %s, pending: %d%n",
                        r, pending.size());
                if (r != null) {
                    pending.remove(r);
                } else {
                    fis = null;
                    fd = null;
                    cleanup = null;
                    altFinalizer = null;
                    System.gc();  // attempt to reclaim them
                }
            }
            Reference.reachabilityFence(fd);
            Reference.reachabilityFence(fis);
            Reference.reachabilityFence(cleanup);
            Reference.reachabilityFence(altFinalizer);

            // Confirm the correct number of calls to close depending on the cleanup type
            switch (cleanType) {
                case CLEANER:
                    if (closeCounter != null && closeCounter.get() > 0) {
                        throw new RuntimeException("Close should not have been called: count: "
                                + closeCounter);
                    }
                    break;
                case CLOSE:
                    if (closeCounter == null || closeCounter.get() == 0) {
                        throw new RuntimeException("Close should have been called: count: 0");
                    }
                    break;
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
            return 1;
        }
        return 0;
    }

    /**
     * Method to list the open file descriptors (if supported by the 'lsof' command).
     */
    static void listProcFD() {
        List<String> lsofDirs = List.of("/usr/bin", "/usr/sbin");
        Optional<Path> lsof = lsofDirs.stream()
                .map(s -> Paths.get(s, "lsof"))
                .filter(f -> Files.isExecutable(f))
                .findFirst();
        lsof.ifPresent(exe -> {
            try {
                System.out.printf("Open File Descriptors:%n");
                long pid = ProcessHandle.current().pid();
                ProcessBuilder pb = new ProcessBuilder(exe.toString(), "-p", Integer.toString((int) pid));
                pb.inheritIO();
                Process p = pb.start();
                p.waitFor(10, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ie) {
                ie.printStackTrace();
            }
        });
    }
}
