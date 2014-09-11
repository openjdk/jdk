/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029516
 * @summary Bash on WatchKey.cancel with a view to causing a crash when
 *    an outstanding I/O operation on directory completes after the
 *    directory has been closed
 */

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LotsOfCancels {

    // set to true for any exceptions
    static volatile boolean failed;

    public static void main(String[] args) throws Exception {

        // create a bunch of directories. Create two tasks for each directory,
        // one to bash on cancel, the other to poll the events
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Path top = Files.createTempDirectory("work");
            top.toFile().deleteOnExit();
            for (int i=1; i<=16; i++) {
                Path dir = Files.createDirectory(top.resolve("dir-" + i));
                WatchService watcher = FileSystems.getDefault().newWatchService();
                pool.submit(() -> handle(dir, watcher));
                pool.submit(() -> poll(watcher));
            }
        } finally {
            pool.shutdown();
        }

        // give thread pool lots of time to terminate
        if (!pool.awaitTermination(5L, TimeUnit.MINUTES))
            throw new RuntimeException("Thread pool did not terminate");

        if (failed)
            throw new RuntimeException("Test failed, see log for details");
    }

    /**
     * Stress the given WatchService, specifically the cancel method, in
     * the given directory. Closes the WatchService when done.
     */
    static void handle(Path dir, WatchService watcher) {
        try {
            try {
                Path file = dir.resolve("anyfile");
                for (int i=0; i<2000; i++) {
                    WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
                    Files.createFile(file);
                    Files.delete(file);
                    key.cancel();
                }
            } finally {
                watcher.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

    /**
     * Polls the given WatchService in a tight loop. This keeps the event
     * queue drained, it also hogs a CPU core which seems necessary to
     * tickle the original bug.
     */
    static void poll(WatchService watcher) {
        try {
            for (;;) {
                WatchKey key = watcher.take();
                if (key != null) {
                    key.pollEvents();
                    key.reset();
                }
            }
        } catch (ClosedWatchServiceException expected) {
            // nothing to do
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }
    }

}

