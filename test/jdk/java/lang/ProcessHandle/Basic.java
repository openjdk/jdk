/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @summary Basic tests for ProcessHandler
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.management
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 * @run junit Basic
 */
public class Basic {
    /**
     * Tests of ProcessHandle.current.
     */
    @Test
    public void test1() {
        try {
            ProcessHandle self = ProcessHandle.current();
            ProcessHandle self1 = ProcessHandle.current();
            assertEquals(self1, self); //, "get pid twice should be same %d: %d");
        } finally {
            // Cleanup any left over processes
            ProcessHandle.current().children().forEach(ProcessHandle::destroy);
        }
    }

    /**
     * Tests of ProcessHandle.get.
     */
    @Test
    public void test2() {
        try {
            ProcessHandle self = ProcessHandle.current();
            long pid = self.pid();       // known native process id
            Optional<ProcessHandle> self1 = ProcessHandle.of(pid);
            assertEquals(self, self1.get(),
                    "ProcessHandle.of(x.pid()) should be equal pid() %d: %d");

            Optional<ProcessHandle> ph = ProcessHandle.of(pid);
            assertEquals(ph.get().pid(), pid);
        } finally {
            // Cleanup any left over processes
            ProcessHandle.current().children().forEach(ProcessHandle::destroy);
        }
    }

    @Test
    public void test3() {
        // Test can get parent of current
        ProcessHandle ph = ProcessHandle.current();
        try {
            Optional<ProcessHandle> pph = ph.parent();
            assertTrue(pph.isPresent(), "Current has a Parent");
        } finally {
            // Cleanup any left over processes
            ProcessHandle.current().children().forEach(ProcessHandle::destroy);
        }
    }

    @Test
    public void test4() {
        try {
            Process p = new ProcessBuilder("sleep", "0").start();
            p.waitFor();

            long deadPid = p.pid();
            p = null;               // Forget the process

            Optional<ProcessHandle> t = ProcessHandle.of(deadPid);
            assertFalse(t.isPresent(), "Handle created for invalid pid:" + t);
        } catch (IOException | InterruptedException ex) {
            fail("Unexpected exception", ex);
        } finally {
            // Cleanup any left over processes
            ProcessHandle.current().children().forEach(ProcessHandle::destroy);
        }
    }

    @Test
    public void test5() {
        // Always contains itself.
        ProcessHandle current = ProcessHandle.current();
        List<ProcessHandle> list = ProcessHandle.allProcesses().collect(Collectors.toList());
        if (!list.stream()
                .anyMatch(ph -> ph.equals(ProcessHandle.current()))) {
            System.out.printf("current: %s%n", current);
            System.out.printf("all processes.size: %d%n", list.size());
            list.forEach(p -> ProcessUtil.printProcess(p, "   allProcesses: "));
            fail("current process not found in all processes");
        }
    }

    @Test
    public void test6() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            ProcessHandle.current().onExit();
        });
    }

    @Test
    public void test7() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            ProcessHandle.current().destroyForcibly();
        });
    }
}
