/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4652922
 *
 * @summary synopsis: ReliableLog.update should pad records to 4-byte
 * boundaries
 * @author Ann Wollrath
 *
 * @run main/othervm/timeout=240 LogTest
 */

import sun.rmi.log.LogHandler;
import sun.rmi.log.ReliableLog;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

public class LogTest {

    private static int crashPoint = 0;
    private static boolean spansBoundary = false;
    private static ReliableLog log =  null;
    private static Counter counter = new Counter();

    public static void main(String[] args) throws Exception {

        System.err.println("\nRegression test for bug 4652922\n");

        System.setProperty("sun.rmi.log.class", MyLogFile.class.getName());
        //System.setProperty("sun.rmi.log.debug", "true");

        log = new ReliableLog(".", new TestLogHandler(counter), false);

        writeUpdatesCrashAndRecover(10, 1, false, 10);
        writeUpdatesCrashAndRecover(10, 1, true, 20);
        writeUpdatesCrashAndRecover(10, 2, true, 30);
        writeUpdatesCrashAndRecover(9, 2, false, 40);
        writeUpdatesCrashAndRecover(9, 3, true, 50);
        log.close();
    }

    private static void writeUpdatesCrashAndRecover(int updates,
                                                    int crashValue,
                                                    boolean spans,
                                                    int expectedCount)
        throws IOException
    {
        /*
         * Write updates
         */
        System.err.println("\nwrite updates: " + updates);
        for (int i = 0; i < updates; i++) {
            counter.increment();
            log.update(counter);
        }

        /*
         * Crash
         */
        crashPoint = crashValue;
        spansBoundary = spans;
        System.err.println("crash during next update on sync #" +
                           crashPoint +
                           " (spansBoundary = " + spansBoundary + ")");
        try {
            System.err.println(
                "write one update (update record should " +
                ((counter.value() + 1 == expectedCount) ? "" : "not ") +
                "be complete)");
            counter.increment();
            log.update(counter);
            throw new RuntimeException("failed to reach crashpoint " +
                                       crashPoint);
        } catch (IOException e) {
            System.err.println("caught IOException; message: " +
                               e.getMessage());
            log.close();
        }

        /*
         * Recover
         */
        log = new ReliableLog(".", new TestLogHandler(null), false);
        try {
            System.err.println("recover log");
            counter = (Counter) log.recover();
            System.err.println("recovered counter value: " + counter.value());
            if (counter.value() != expectedCount) {
                throw new RuntimeException("unexpected counter value " +
                                           counter.value());
            }
            System.err.println("log recovery successful");

        } catch (IOException e) {
            System.err.println("log should recover after crash point");
            e.printStackTrace();
            throw new RuntimeException(
                "log should recover after crash point");
        }

    }

    private static class Counter implements Serializable {
        private static long serialVersionUID = 1;
        private int count = 0;

        Counter() {}

        int increment() {
            return ++count;
        }

        int value() {
            return count;
        }

        void update(Counter value) {
            if (value.value() < count) {
                throw new IllegalStateException(
                    "bad update (count = " + count + ", value = " + value + ")");
            } else {
                count = value.value();
            }
        }
    }

    static class TestLogHandler extends LogHandler {

        private final Counter initialState;

        TestLogHandler(Counter initialState) {
            this.initialState = initialState;
        }

        public Object initialSnapshot() {
            if (initialState == null) {
                throw new IllegalStateException(
                    "attempting initialSnapshot with null");
            }
            return initialState;
        }

        public Object applyUpdate(Object update, Object state) {
            ((Counter) state).update((Counter) update);
            return state;
        }
    }

    public static class MyLogFile extends ReliableLog.LogFile {

        public MyLogFile(String name, String mode)
            throws FileNotFoundException, IOException
        {
            super(name, mode);
        }

        protected void sync() throws IOException {
            if (crashPoint != 0) {
                if (--crashPoint == 0) {
                    throw new IOException("crash point reached");
                }
            }
            super.sync();
        }

        protected boolean checkSpansBoundary(long fp) {
            return
                crashPoint > 0 ? spansBoundary : super.checkSpansBoundary(fp);
        }
    }
}
