/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8381169
 * @summary Basic com.sun.management.HotSpotDiagnosticMXBean.dumpThreads generating a
 *    format version 1 thread dump
 * @modules jdk.management
 * @library /test/lib
 * @run junit/othervm -Dcom.sun.management.HotSpotDiagnosticMXBean.dumpThreads.format=1 ${test.main.class}
 */

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.HotSpotDiagnosticMXBean.ThreadDumpFormat;
import jdk.test.lib.json.JSONValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class DumpThreadsFormat1 {
    private static JSONValue threadDumpObj;

    @BeforeAll
    static void generateThreadDump() throws Exception {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "dump", ".json");
        Files.delete(file);
        var mbean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        mbean.dumpThreads(file.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.JSON);
        System.err.format("Dumped to %s%n", file.getFileName());
        String json = Files.readString(file);
        threadDumpObj = JSONValue.parse(json).get("threadDump");
    }

    /**
     * Test that "formatVersion" is not present.
     */
    @Test
    void testFormatVersion() {
        threadDumpObj.getOrAbsent("formatVersion")
                .ifPresent(_ -> { fail("formatVersion not expected"); });
    }

    /**
     * Test "processId" is a string.
     */
    @Test
    void testProcessId() {
        String processId = "" + ProcessHandle.current().pid();
        assertEquals(processId, threadDumpObj.get("processId").asString());
    }

    /**
     * Test "tid" for current thread in root container is a string.
     */
    @Test
    void testThreadId() throws Exception {
        JSONValue rootContainerObj = threadDumpObj.get("threadContainers").element(0);
        String name = rootContainerObj.get("container").asString();
        assertEquals("<root>", name);

        String tid = "" + Thread.currentThread().threadId();
        boolean found = rootContainerObj.get("threads")
                .elements()
                .stream()
                .map(t -> t.get("tid").asString())
                .anyMatch(tid::equals);
        assertTrue(found, "Current thread not found");
    }
}
