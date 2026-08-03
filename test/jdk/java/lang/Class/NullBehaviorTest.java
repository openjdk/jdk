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
 * @bug 8371953
 * @summary Check null argument behaviors for java.lang.Class APIs.
 * @run junit NullBehaviorTest
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NullBehaviorTest {
    @Test
    void nullChecks() {
        assertThrows(NullPointerException.class, () -> Class.forName(null));
        assertDoesNotThrow(() -> Class.forName("java.lang.Object", false, null));
        assertThrows(NullPointerException.class, () -> Class.forName(null, false, null));
        assertThrows(NullPointerException.class, () -> Class.forName(null, "java.lang.Object"));
        assertThrows(NullPointerException.class, () -> Class.forName(Object.class.getModule(), null));
        assertThrows(NullPointerException.class, () -> Class.forPrimitiveName(null));
        assertFalse(Object.class.isInstance(null));
        assertThrows(NullPointerException.class, () -> Object.class.isAssignableFrom(null));
        assertThrows(NullPointerException.class, () -> Object.class.getField(null));
        assertThrows(NullPointerException.class, () -> Object.class.getDeclaredField(null));
        assertThrows(NullPointerException.class, () -> Object.class.getMethod(null));
        assertThrows(NullPointerException.class, () -> Object.class.getDeclaredMethod(null));
        assertDoesNotThrow(() -> Object.class.getMethod("hashCode", (Class<?>[]) null));
        assertDoesNotThrow(() -> Object.class.getDeclaredMethod("hashCode", (Class<?>[]) null));
        assertThrows(NoSuchMethodException.class, () -> Object.class.getMethod("hashCode", new Class[]{null}));
        assertThrows(NoSuchMethodException.class, () -> Object.class.getDeclaredMethod("hashCode", new Class[]{null}));
        assertDoesNotThrow(() -> Object.class.getConstructor((Class<?>[]) null));
        assertDoesNotThrow(() -> Object.class.getDeclaredConstructor((Class<?>[]) null));
        assertThrows(NoSuchMethodException.class, () -> Object.class.getConstructor(new Class[]{null}));
        assertThrows(NoSuchMethodException.class, () -> Object.class.getDeclaredConstructor(new Class[]{null}));
        assertThrows(NullPointerException.class, () -> Object.class.getResourceAsStream(null));
        assertThrows(NullPointerException.class, () -> Object.class.getResource(null));
        assertDoesNotThrow(() -> Object.class.cast(null));
        assertThrows(NullPointerException.class, () -> Object.class.asSubclass(null));
        assertThrows(NullPointerException.class, () -> Object.class.isNestmateOf(null));
        assertThrows(NullPointerException.class, () -> int.class.isNestmateOf(null));
    }
}
