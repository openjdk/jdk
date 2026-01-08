/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @test
 * @bug 8246774
 * @summary test for varargs record components
 * @run junit VarargsRecordsTest
 */
public class VarargsRecordsTest {
    public record RI(int... xs) { }
    public record RII(int x, int... xs) { }
    public record RX(int[] xs) { }

    RI r1 = new RI();
    RI r2 = new RI(1);
    RI r3 = new RI(1, 2);
    RII r4 = new RII(1);
    RII r5 = new RII(1, 2);
    RII r6 = new RII(1, 2, 3);

    @Test
    public void assertVarargsInstances() {
        assertEquals(0, r1.xs.length);
        assertEquals(1, r2.xs.length);
        assertEquals(2, r3.xs.length);
        assertEquals(0, r4.xs.length);
        assertEquals(1, r5.xs.length);
        assertEquals(2, r6.xs.length);

        assertEquals(1, r2.xs[0]);
        assertEquals(1, r3.xs[0]);
        assertEquals(2, r3.xs[1]);

        assertEquals(2, r5.xs[0]);
        assertEquals(2, r6.xs[0]);
        assertEquals(3, r6.xs[1]);
    }

    @Test
    public void testMembers() throws ReflectiveOperationException {
        Constructor c = RI.class.getConstructor(int[].class);
        assertNotNull(c);
        assertTrue(c.isVarArgs());
        Parameter[] parameters = c.getParameters();
        assertEquals(1, parameters.length);
        assertEquals("xs", parameters[0].getName());

        RI ri = (RI) c.newInstance(new int[]{1, 2});
        assertEquals(1, ri.xs()[0]);
        assertEquals(2, ri.xs()[1]);

        Field xsField = RI.class.getDeclaredField("xs");
        assertEquals(int[].class, xsField.getType());
        assertEquals(0, (xsField.getModifiers() & Modifier.STATIC));
        assertTrue((xsField.getModifiers() & Modifier.PRIVATE) != 0);
        assertTrue((xsField.getModifiers() & Modifier.FINAL) != 0);
        assertEquals(1, ((int[]) xsField.get(ri))[0]);

        Method xsMethod = RI.class.getDeclaredMethod("xs");
        assertEquals(int[].class, xsMethod.getReturnType());
        assertEquals(0, xsMethod.getParameterCount());
        assertEquals(0, (xsMethod.getModifiers() & (Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC | Modifier.ABSTRACT)));
        assertEquals(1, ((int[]) xsMethod.invoke(ri))[0]);
    }

    @Test
    public void testNotVarargs() throws ReflectiveOperationException {
        Constructor c = RX.class.getConstructor(int[].class);
        assertFalse(c.isVarArgs());
    }
}
