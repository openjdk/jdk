/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test diagnostic option for detecting a virtual thread using thread locals
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm -Djdk.traceVirtualThreadLocals TraceVirtualThreadLocals
 * @run junit/othervm -Djdk.traceVirtualThreadLocals=true TraceVirtualThreadLocals
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceVirtualThreadLocals {

    @Test
    void testInitialValue() throws Exception {
        String output = run(() -> {
            ThreadLocal<String> name = new ThreadLocal<>() {
                @Override
                protected String initialValue() {
                    return "<unnamed>";
                }
            };
            name.get();
        });
        assertContains(output, "java.lang.ThreadLocal.setInitialValue");
    }

    @Test
    void testSet() throws Exception {
        String output = run(() -> {
            ThreadLocal<String> name = new ThreadLocal<>();
            name.set("duke");
        });
        assertContains(output, "java.lang.ThreadLocal.set");
    }

    /**
     * Run a task in a virtual thread, returning a String with any output printed
     * to standard output.
     */
    private static String run(Runnable task) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(baos, true));
        try {
            VThreadRunner.run(task::run);
        } finally {
            System.setOut(original);
        }
        String output = new String(baos.toByteArray());
        System.out.println(output);
        return output;
    }

    /**
     * Tests that s1 contains s2.
     */
    private static void assertContains(String s1, String s2) {
        assertTrue(s1.contains(s2), s2 + " not found!!!");
    }
}
