/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng TestSharedAccess
 */

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.MemoryLayouts;
import org.testng.annotations.*;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class TestSharedAccess {

    static final VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);

    @Test
    public void testShared() throws Throwable {
        try (MemorySegment s = MemorySegment.allocateNative(4)) {
            setInt(s, 42);
            assertEquals(getInt(s), 42);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0 ; i < 1000 ; i++) {
                threads.add(new Thread(() -> {
                    try (MemorySegment local = s.acquire()) {
                        assertEquals(getInt(local), 42);
                    }
                }));
            }
            threads.forEach(Thread::start);
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testBadCloseWithPendingAcquire() {
        try (MemorySegment segment = MemorySegment.allocateNative(8)) {
            segment.acquire();
        } //should fail here!
    }

    static int getInt(MemorySegment handle) {
        return (int)intHandle.getVolatile(handle.baseAddress());
    }

    static void setInt(MemorySegment handle, int value) {
        intHandle.setVolatile(handle.baseAddress(), value);
    }
}
