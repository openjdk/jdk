/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../ /test/lib
 * @modules java.base/jdk.internal.ref java.base/jdk.internal.foreign
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestDontRelease
 */

import jdk.internal.foreign.MemorySessionImpl;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.assertTrue;

public class TestDontRelease extends NativeTestHelper  {

    static {
        System.loadLibrary("DontRelease");
    }

    @Test
    public void testDontRelease() {
        MethodHandle handle = downcallHandle("test_ptr", FunctionDescriptor.ofVoid(ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(JAVA_INT);
            ((MemorySessionImpl)arena.scope()).whileAlive(() -> {
                Thread t = new Thread(() -> {
                    try {
                        // acquire of the segment should fail here,
                        // due to wrong thread
                        handle.invokeExact(segment);
                    } catch (Throwable e) {
                        // catch the exception.
                        assertTrue(e instanceof WrongThreadException);
                        assertTrue(e.getMessage().matches(".*Attempted access outside owning thread.*"));
                    }
                });
                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // the downcall above should not have called release on the session
                // so doing it here should succeed without error
            });
        }
    }
}
