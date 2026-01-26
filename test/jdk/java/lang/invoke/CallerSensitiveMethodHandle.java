/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm CallerSensitiveMethodHandle
 * @summary Check Lookup findVirtual, findStatic and unreflect behavior with
 *          caller sensitive methods with focus on AccessibleObject.setAccessible
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;

import static java.lang.invoke.MethodType.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CallerSensitiveMethodHandle {
    private static int field = 0;
    @Test
    public void privateField() throws Throwable {
        Lookup l = MethodHandles.lookup();
        Field f = CallerSensitiveMethodHandle.class.getDeclaredField("field");
        MethodHandle mh = l.findVirtual(Field.class, "setInt", methodType(void.class, Object.class, int.class));
        int newValue = 5;
        mh.invokeExact(f, (Object) null, newValue);
        assertEquals(newValue, field);
    }

    @Test
    public void lookupItself() throws Throwable {
        Lookup lookup = MethodHandles.lookup();
        MethodHandle MH_lookup2 = lookup.findStatic(MethodHandles.class, "lookup", methodType(Lookup.class));
        Lookup lookup2 = (Lookup) MH_lookup2.invokeExact();
        System.out.println(lookup2 + " original lookup class " + lookup.lookupClass());
        assertSame(lookup.lookupClass(), lookup2.lookupClass());
    }
}
