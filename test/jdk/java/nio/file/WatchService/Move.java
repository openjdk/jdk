/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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
 * @summary Verifies that Files.move() of a directory hierarchy is correctly
 *          reported by WatchService.
 * @requires os.family == "mac"
 * @library ..
 * @run main Move
 */

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.sun.nio.file.ExtendedWatchEventModifier;

public class Move {

    static void checkKey(WatchKey key, Path dir) {
        if (!key.isValid())
            throw new RuntimeException("Key is not valid");
        if (key.watchable() != dir)
            throw new RuntimeException("Unexpected watchable");
    }

    static void takeExpectedKey(WatchService watcher, WatchKey expected) {
        System.out.println("take events...");
        WatchKey key;
        try {
            key = watcher.take();
        } catch (InterruptedException x) {
            // not expected
            throw new RuntimeException(x);
        }
        if (key != expected)
            throw new RuntimeException("removed unexpected key");
    }

    static void dumpEvents(final List<WatchEvent<?>> events) {
        System.out.println("Got events: ");
        for(WatchEvent<?> event : events) {
            System.out.println(event.kind() + " for '" + event.context() + "' count = " + event.count());
        }
    }

    static void assertHasEvent(final List<WatchEvent<?>> events, final Path path, final WatchEvent.Kind<Path> kind) {
        for (final WatchEvent<?> event : events) {
            if (event.context().equals(path) && event.kind().equals(kind)) {
                if (event.count() != 1) {
                    throw new RuntimeException("Expected count 1 for event " + event);
                }
                return;
            }
        }

        throw new RuntimeException("Didn't find event " + kind + " for path '" + path + "'");
    }

    /**
     * Verifies move of a directory sub-tree with and without FILE_TREE option.
     */
    static void testMoveSubtree(final Path dir) throws IOException {
        final FileSystem fs = FileSystems.getDefault();
        final WatchService rootWatcher = fs.newWatchService();
        final WatchService subtreeWatcher = fs.newWatchService();
        try {
            Path path = dir.resolve("root");
            Files.createDirectory(path);
            System.out.println("Created " + path);

            path = dir.resolve("root").resolve("subdir").resolve("1").resolve("2").resolve("3");
            Files.createDirectories(path);
            System.out.println("Created " + path);

            path = dir.resolve("root").resolve("subdir").resolve("1").resolve("file1");
            Files.createFile(path);

            path = dir.resolve("root").resolve("subdir").resolve("1").resolve("2").resolve("3").resolve("file3");
            Files.createFile(path);

            // register with both watch services (different events)
            System.out.println("register for different events");
            final WatchKey rootKey = dir.resolve(dir.resolve("root")).register(rootWatcher,
                    new WatchEvent.Kind<?>[]{ ENTRY_CREATE, ENTRY_DELETE });

            System.out.println("Move root/subdir/1/2 -> root/subdir/2.moved");
            Files.move(dir.resolve("root").resolve("subdir").resolve("1").resolve("2"),
                       dir.resolve("root").resolve("subdir").resolve("2.moved"));

            // Check that changes in a subdirectory were not noticed by the root directory watcher
            {
                final WatchKey key = rootWatcher.poll();
                if (key != null)
                    throw new RuntimeException("key not expected");
            }

            rootKey.reset();

            System.out.println("Move root/subdir/2.moved -> root/2");
            Files.move(dir.resolve("root").resolve("subdir").resolve("2.moved"),
                       dir.resolve("root").resolve("2"));

            // Check that the root directory watcher has noticed one new directory.
            {
                takeExpectedKey(rootWatcher, rootKey);
                final List<WatchEvent<?>> events = rootKey.pollEvents();
                dumpEvents(events);
                assertHasEvent(events, Path.of("2"), ENTRY_CREATE);
                if (events.size() > 1) {
                    throw new RuntimeException("Too many events");
                }
            }
        } finally {
            rootWatcher.close();
        }
    }

    /**
     * Verifies quickly deleting a file and creating a directory with the same name (and back)
     * is recognized by WatchService.
     */
    static void testMoveFileToDirectory(final Path dir) throws IOException {
        final FileSystem fs = FileSystems.getDefault();
        try (final WatchService watcher = fs.newWatchService()) {
            Files.createDirectory(dir.resolve("dir"));
            Files.createFile(dir.resolve("file"));

            final WatchKey key = dir.register(watcher, new WatchEvent.Kind<?>[]{ENTRY_CREATE, ENTRY_DELETE});

            for (int i = 0; i < 4; i++) {
                System.out.println("Iteration " + i);
                Files.delete(dir.resolve("dir"));
                Files.delete(dir.resolve("file"));
                if (i % 2 == 1) {
                    Files.createDirectory(dir.resolve("dir"));
                    Files.createFile(dir.resolve("file"));
                } else {
                    Files.createDirectory(dir.resolve("file"));
                    Files.createFile(dir.resolve("dir"));
                }

                takeExpectedKey(watcher, key);
                final List<WatchEvent<?>> events = key.pollEvents();
                dumpEvents(events);

                final long countDirCreated = events.stream().filter(
                        event -> event.context().equals(Path.of("dir")) && event.kind().equals(ENTRY_CREATE)).count();
                final long countDirDeleted = events.stream().filter(
                        event -> event.context().equals(Path.of("dir")) && event.kind().equals(ENTRY_DELETE)).count();
                final long countFileCreated = events.stream().filter(
                        event -> event.context().equals(Path.of("file")) && event.kind().equals(ENTRY_CREATE)).count();
                final long countFileDeleted = events.stream().filter(
                        event -> event.context().equals(Path.of("file")) && event.kind().equals(ENTRY_DELETE)).count();
                if (countDirCreated != 1) throw new RuntimeException("Not one CREATE for dir");
                if (countDirDeleted != 1) throw new RuntimeException("Not one DELETE for dir");
                if (countFileCreated != 1) throw new RuntimeException("Not one CREATE for file");
                if (countFileDeleted != 1) throw new RuntimeException("Not one DELETE for file");

                key.reset();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            testMoveSubtree(dir);
        } catch (UnsupportedOperationException e) {
            System.out.println("FILE_TREE watching is not supported; test considered passed");
        } finally {
            TestUtil.removeAll(dir);
        }

        dir = TestUtil.createTemporaryDirectory();
        try {
            testMoveFileToDirectory(dir);
        } catch (UnsupportedOperationException e) {
            System.out.println("FILE_TREE watching is not supported; test considered passed");
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
