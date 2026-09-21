/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8245302
 * @summary test the relationship between
 * thread id long and int methods
 * @run junit/othervm  ${test.main.class}
 */

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LogRecordThreadIdTest {

    /**
     * Tests threadID setter methods for consistency
     * with longThreadID
     */
    @Test
    public void testSetThreadId() {
        LogRecord record  = new LogRecord(Level.INFO, "record");
        LogRecord record1 = new LogRecord(Level.INFO, "record1");

        record.setThreadID(Integer.MAX_VALUE - 20);
        record1.setThreadID(Integer.MAX_VALUE - 1);
        assertEquals(Integer.MAX_VALUE - 20L, record.getLongThreadID());
        assertEquals(Integer.MAX_VALUE - 20, record.getThreadID());
        assertEquals(Integer.MAX_VALUE - 1, record1.getThreadID());
        assertEquals(Integer.MAX_VALUE - 1, record1.getLongThreadID());
    }

    /**
     * Tests longThreadID methods for consistency
     * with threadID
     */
    @Test
    public void testSetLongThreadId() {
        LogRecord record = new LogRecord(Level.INFO, "record");
        LogRecord record1 = new LogRecord(Level.INFO, "record1");
        LogRecord record2 = new LogRecord(Level.INFO, "record2");

        record.setLongThreadID(Integer.MAX_VALUE - 20L);
        record1.setLongThreadID(Integer.MAX_VALUE + 10L);
        record2.setLongThreadID(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE - 20, record.getThreadID());
        assertEquals(Integer.MAX_VALUE - 20L, record.getLongThreadID());
        assertNotEquals(Integer.MAX_VALUE + 10L, record1.getThreadID());
        assertEquals(Integer.MAX_VALUE + 10L, record1.getLongThreadID());
        assertEquals(Integer.MAX_VALUE, record2.getThreadID());
        assertEquals(Integer.MAX_VALUE, record2.getLongThreadID());
    }
}
