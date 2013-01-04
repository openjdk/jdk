/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * Tests for JSType methods.
 */
public class JSTypeTest {
    /**
     * Test of isPrimitive method, of class Runtime.
     */
    @Test
    public void testIsPrimitive() {
        assertEquals(true,  JSType.isPrimitive(null));
        assertEquals(true,  JSType.isPrimitive(ScriptRuntime.UNDEFINED));
        assertEquals(true,  JSType.isPrimitive(Double.NaN));
        assertEquals(true,  JSType.isPrimitive(Double.NEGATIVE_INFINITY));
        assertEquals(true,  JSType.isPrimitive(Double.POSITIVE_INFINITY));
        assertEquals(true,  JSType.isPrimitive(0.0));
        assertEquals(true,  JSType.isPrimitive(3.14));
        assertEquals(true,  JSType.isPrimitive("hello"));
        assertEquals(true,  JSType.isPrimitive(""));
        assertEquals(false, JSType.isPrimitive(new Object()));
    }

    /**
     * Test of toBoolean method, of class Runtime.
     */
    @Test
    public void testToBoolean() {
        assertEquals(false, JSType.toBoolean(ScriptRuntime.UNDEFINED));
        assertEquals(false, JSType.toBoolean(null));
        assertEquals(false, JSType.toBoolean(Boolean.FALSE));
        assertEquals(true,  JSType.toBoolean(Boolean.TRUE));
        assertEquals(false, JSType.toBoolean(-0.0));
        assertEquals(false, JSType.toBoolean(0.0));
        assertEquals(false, JSType.toBoolean(Double.NaN));
        assertEquals(true,  JSType.toBoolean(3.14));
        assertEquals(false, JSType.toBoolean(""));
        assertEquals(true,  JSType.toBoolean("javascript"));
        assertEquals(true,  JSType.toBoolean(new Object()));
    }

    /**
     * Test of toNumber method, of class Runtime.
     */
    @Test
    public void testToNumber_Object() {
        assertTrue(Double.isNaN(JSType.toNumber(ScriptRuntime.UNDEFINED)));
        assertEquals(0.0,  JSType.toNumber((Object)null), 0.0);
        assertEquals(1.0,  JSType.toNumber(Boolean.TRUE), 0.0);
        assertEquals(0.0,  JSType.toNumber(Boolean.FALSE), 0.0);
        assertEquals(3.14, JSType.toNumber(3.14), 0.0);
        // FIXME: add more assertions for specific String to number cases
        // FIXME: add case for Object type (JSObject with getDefaultValue)
    }

    /**
     * Test of toString method, of class Runtime.
     */
    @Test
    public void testToString_Object() {
        assertEquals("undefined", JSType.toString(ScriptRuntime.UNDEFINED));
        assertEquals("null", JSType.toString(null));
        assertEquals("true", JSType.toString(Boolean.TRUE));
        assertEquals("false", JSType.toString(Boolean.FALSE));
        assertEquals("", JSType.toString(""));
        assertEquals("nashorn", JSType.toString("nashorn"));
        assertEquals("NaN", JSType.toString(Double.NaN));
        assertEquals("Infinity", JSType.toString(Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", JSType.toString(Double.NEGATIVE_INFINITY));
        assertEquals("0", JSType.toString(0.0));
        // FIXME: add more number-to-string test cases
        // FIXME: add case for Object type (JSObject with getDefaultValue)
    }
}
