/*
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8330077
 * @summary Tests WatchService behavior with more entries in a watched directory
 *     than the default event limit
 * @library ..
 * @run main/othervm LotsOfEntries 600 fail
 * @run main/othervm -Dsun.nio.fs.maxWatchEvents=700 LotsOfEntries 600 pass
 */

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LotsOfEntries {

    static void testCreateLotsOfEntries(Path dir, int numEvents, boolean fail) throws Exception {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            System.out.format("register %s for events\n", dir);
            WatchKey key = dir.register(watcher, ENTRY_CREATE);

            System.out.format("create %d entries\n", numEvents);
            Set<Path> entries = new HashSet<>();
            for (int i = 0; i < numEvents; i++) {
                Path entry = dir.resolve("entry" + i);
                entries.add(entry);
                Files.createFile(entry);
            }

            // wait for key to be signalled - the timeout is long to allow for
            // polling implementations
            System.out.println("poll watcher...");
            WatchKey signalledKey = watcher.poll(15, TimeUnit.SECONDS);
            if (key != signalledKey) {
                throw new RuntimeException("Unexpected key returned from poll");
            }

            if (fail) {
                System.out.println("poll expecting overflow...");
                var events = key.pollEvents();
                if (events.size() != 1) {
                    throw new RuntimeException("Expected a single event");
                }
                if (!events.getFirst().kind().equals(OVERFLOW)) {
                    throw new RuntimeException("Expected overflow event");
                }
            } else {
                System.out.println("poll not expecting overflow...");
                Set<Path> contexts = key.pollEvents().stream()
                    .map(WatchEvent::context)
                    .map(Path.class::cast)
                    .map(entry -> dir.resolve(entry))
                    .collect(Collectors.toSet());
                if (!entries.equals(contexts)) {
                    throw new RuntimeException(
                        "Expected events on: " + entries + ", got: " + contexts);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        int numEvents = Integer.parseInt(args[0]);
        boolean fail = args[1].equals("fail");
        try {
            testCreateLotsOfEntries(dir, numEvents, fail);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
