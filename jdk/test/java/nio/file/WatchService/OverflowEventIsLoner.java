/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 6907760
 * @summary Check that the OVERFLOW event is not retrieved with other events
 * @library ..
 */

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKind.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OverflowEventIsLoner {

    static void drainEvents(WatchService watcher,
                            WatchEvent.Kind<?> expectedKind,
                            int count)
        throws IOException, InterruptedException
    {
        // wait for key to be signalled - the timeout is long to allow for
        // polling implementations
        WatchKey key = watcher.poll(15, TimeUnit.SECONDS);
        if (key != null && count == 0)
            throw new RuntimeException("Key was signalled (unexpected)");
        if (key == null && count > 0)
            throw new RuntimeException("Key not signalled (unexpected)");

        int nread = 0;
        boolean gotOverflow = false;
        do {
            List<WatchEvent<?>> events = key.pollEvents();
            for (WatchEvent<?> event: events) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == expectedKind) {
                    // expected event kind
                    if (++nread > count)
                        throw new RuntimeException("More events than expected!!");
                } else if (kind == OVERFLOW) {
                    // overflow event should not be retrieved with other events
                    if (events.size() > 1)
                        throw new RuntimeException("Overflow retrieved with other events");
                    gotOverflow = true;
                } else {
                    throw new RuntimeException("Unexpected event '" + kind + "'");
                }
            }
            if (!key.reset())
                throw new RuntimeException("Key is no longer valid");
            key = watcher.poll(2, TimeUnit.SECONDS);
        } while (key != null);

        // check that all expected events were received or there was an overflow
        if (nread < count && !gotOverflow)
            throw new RuntimeException("Insufficient events");
    }


    static void test(Path dir) throws IOException, InterruptedException {
        WatchService watcher = dir.getFileSystem().newWatchService();
        try {
            WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);

            // create a lot of files
            int n = 1024;
            Path[] files = new Path[n];
            for (int i=0; i<n; i++) {
                files[i] = dir.resolve("foo" + i).createFile();
            }

            // give time for events to accumulate (improve chance of overflow)
            Thread.sleep(1000);

            // check that we see the create events (or overflow)
            drainEvents(watcher, ENTRY_CREATE, n);

            // delete the files
            for (int i=0; i<n; i++) {
                files[i].delete();
            }

            // give time for events to accumulate (improve chance of overflow)
            Thread.sleep(1000);

            // check that we see the delete events (or overflow)
            drainEvents(watcher, ENTRY_DELETE, n);
        } finally {
            watcher.close();
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            test(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
