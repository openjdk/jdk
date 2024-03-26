/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test stack traces in exceptions and stack frames walked by the StackWalker
 *     API do not include the carrier stack frames
 * @requires vm.continuations
 * @modules java.management
 * @library /test/lib
 * @run junit StackTraces
 * @run junit/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowCarrierFrames StackTraces
 */

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import static java.lang.StackWalker.Option.*;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StackTraces {

    /**
     * Test that the stack trace in exceptions does not include the carrier thread
     * frames, except when running with -XX:+ShowCarrierFrames.
     */
    @Test
    void testStackTrace() throws Exception {
        VThreadRunner.run(() -> {
            Exception e = new Exception();
            boolean found = Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::getClassName)
                    .anyMatch("java.util.concurrent.ForkJoinPool"::equals);
            assertTrue(found == hasJvmArgument("-XX:+ShowCarrierFrames"));
        });
    }

    /**
     * Test that StackWalker does not include carrier thread frames.
     */
    @Test
    void testStackWalker() throws Exception {
        VThreadRunner.run(() -> {
            StackWalker walker = StackWalker.getInstance(Set.of(RETAIN_CLASS_REFERENCE));
            boolean found = walker.walk(sf ->
                    sf.map(StackWalker.StackFrame::getDeclaringClass)
                            .anyMatch(c -> c == ForkJoinPool.class));
            assertFalse(found);
        });
    }

    private static boolean hasJvmArgument(String arg) {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.equals(arg)) return true;
        }
        return false;
    }
}
