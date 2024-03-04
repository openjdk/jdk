/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic test for Monotonic.
 * @run junit Basic
 */

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

final class Basic {

    @Test
    void testInteger() {
        Monotonic<Integer> m = Monotonic.of(Integer.class);
        assertFalse(m.isBound());
        assertThrows(NoSuchElementException.class, m::get);

        m.bind(42);
        assertTrue(m.isBound());
        assertEquals(42, m.get());
        assertThrows(IllegalStateException.class, () -> m.bind(13));
        assertTrue(m.isBound());
        assertEquals(42, m.get());

        MethodHandle handle = m.getter();
        assertEquals(Object.class, handle.type().returnType());
        assertEquals(1, handle.type().parameterCount());
        assertEquals(Monotonic.class, handle.type().parameterType(0));
        try {
            Integer i = (Integer) handle.invoke(m);
            assertEquals(42, i);
        } catch (Throwable t) {
            fail(t);
        }
    }

}
