/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     8349206
 * @summary j.u.l.Handler classes create deadlock risk via synchronized publish() method.
 * @modules java.base/sun.util.logging
 *          java.logging
 * @build java.logging/java.util.logging.TestStreamHandler
 * @run main/othervm StreamHandlerLockingTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.TestStreamHandler;

public class StreamHandlerLockingTest {
    static class TestFormatter extends Formatter {
        final Handler handler;

        TestFormatter(Handler handler) {
            this.handler = handler;
        }

        @Override
        public String format(LogRecord record) {
            if (Thread.holdsLock(handler)) {
                throw new AssertionError("format() was called with handler locked (bad).");
            }
            return record.getMessage() + "\n";
        }

        @Override
        public String getHead(Handler h) {
            // This is currently true, and not easy to make unsynchronized.
            if (!Thread.holdsLock(handler)) {
                throw new AssertionError("getHead() expected to be called with handler locked.");
            }
            return "--HEAD--\n";
        }

        @Override
        public String getTail(Handler h) {
            // This is currently true, and not easy to make unsynchronized.
            if (!Thread.holdsLock(handler)) {
                throw new AssertionError("getTail() expected to be called with handler locked.");
            }
            return "--TAIL--\n";
        }
    }

    private static final String EXPECTED_LOG =
            String.join("\n", "--HEAD--", "Hello World", "Some more logging...", "And we're done!", "--TAIL--", "");

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TestStreamHandler handler = new TestStreamHandler(out);
        TestFormatter formatter = new TestFormatter(handler);
        handler.setFormatter(formatter);

        handler.publish(log("Hello World"));
        handler.publish(log("Some more logging..."));
        handler.publish(log("And we're done!"));
        handler.close();

        // Post write callback should have happened once per publish call (with lock held).
        if (handler.callbackCount != 3) {
            throw new AssertionError("Unexpected callback count: " + handler.callbackCount);
        }

        String logged = out.toString("UTF-8");
        if (!EXPECTED_LOG.equals(logged)) {
            throw new AssertionError("Unexpected log contents: " + logged);
        }
    }

    static LogRecord log(String msg) {
        return new LogRecord(Level.INFO, msg);
    }
}
