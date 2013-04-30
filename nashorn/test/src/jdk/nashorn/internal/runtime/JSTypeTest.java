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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

/**
 * Tests for JSType methods.
 *
 * @test
 * @run testng jdk.nashorn.internal.runtime.JSTypeTest
 */
public class JSTypeTest {
    /**
     * Test of isPrimitive method, of class Runtime.
     */
    @Test
    public void testIsPrimitive() {
        assertTrue(JSType.isPrimitive(null));
        assertTrue(JSType.isPrimitive(ScriptRuntime.UNDEFINED));
        assertTrue(JSType.isPrimitive(Double.NaN));
        assertTrue(JSType.isPrimitive(Double.NEGATIVE_INFINITY));
        assertTrue(JSType.isPrimitive(Double.POSITIVE_INFINITY));
        assertTrue(JSType.isPrimitive(0.0));
        assertTrue(JSType.isPrimitive(3.14));
        assertTrue(JSType.isPrimitive("hello"));
        assertTrue(JSType.isPrimitive(""));
        assertFalse(JSType.isPrimitive(new Object()));
    }

    /**
     * Test of toBoolean method, of class Runtime.
     */
    @Test
    public void testToBoolean() {
        assertFalse(JSType.toBoolean(ScriptRuntime.UNDEFINED));
        assertFalse(JSType.toBoolean(null));
        assertFalse(JSType.toBoolean(Boolean.FALSE));
        assertTrue(JSType.toBoolean(Boolean.TRUE));
        assertFalse(JSType.toBoolean(-0.0));
        assertFalse(JSType.toBoolean(0.0));
        assertFalse(JSType.toBoolean(Double.NaN));
        assertTrue(JSType.toBoolean(3.14));
        assertFalse(JSType.toBoolean(""));
        assertTrue(JSType.toBoolean("javascript"));
        assertTrue(JSType.toBoolean(new Object()));
    }

    /**
     * Test of toNumber method, of class Runtime.
     */
    @Test
    public void testToNumber_Object() {
        assertTrue(Double.isNaN(JSType.toNumber(ScriptRuntime.UNDEFINED)));
        assertEquals(JSType.toNumber((Object)null), 0.0, 0.0);
        assertEquals(JSType.toNumber(Boolean.TRUE), 1.0, 0.0);
        assertEquals(JSType.toNumber(Boolean.FALSE), 0.0, 0.0);
        assertEquals(JSType.toNumber(3.14), 3.14, 0.0);
        // FIXME: add more assertions for specific String to number cases
        // FIXME: add case for Object type (JSObject with getDefaultValue)
    }

    /**
     * Test of toString method, of class Runtime.
     */
    @Test
    public void testToString_Object() {
        assertEquals(JSType.toString(ScriptRuntime.UNDEFINED), "undefined");
        assertEquals(JSType.toString(null), "null");
        assertEquals(JSType.toString(Boolean.TRUE), "true");
        assertEquals(JSType.toString(Boolean.FALSE), "false");
        assertEquals(JSType.toString(""), "");
        assertEquals(JSType.toString("nashorn"), "nashorn");
        assertEquals(JSType.toString(Double.NaN), "NaN");
        assertEquals(JSType.toString(Double.POSITIVE_INFINITY), "Infinity");
        assertEquals(JSType.toString(Double.NEGATIVE_INFINITY), "-Infinity");
        assertEquals(JSType.toString(0.0), "0");
        // FIXME: add more number-to-string test cases
        // FIXME: add case for Object type (JSObject with getDefaultValue)
    }

    /**
     * Test of JSType.toUint32(double)
     */
    @Test
    public void testToUint32() {
        assertEquals(JSType.toUint32(+0.0), 0);
        assertEquals(JSType.toUint32(-0.0), 0);
        assertEquals(JSType.toUint32(Double.NaN), 0);
        assertEquals(JSType.toUint32(Double.POSITIVE_INFINITY), 0);
        assertEquals(JSType.toUint32(Double.NEGATIVE_INFINITY), 0);
        assertEquals(JSType.toUint32(9223372036854775807.0d), 0);
        assertEquals(JSType.toUint32(-9223372036854775807.0d), 0);
        assertEquals(JSType.toUint32(1099511627776.0d), 0);
        assertEquals(JSType.toUint32(-1099511627776.0d), 0);
        assertEquals(JSType.toUint32(4294967295.0d), 4294967295l);
        assertEquals(JSType.toUint32(4294967296.0d), 0);
        assertEquals(JSType.toUint32(4294967297.0d), 1);
        assertEquals(JSType.toUint32(-4294967295.0d), 1);
        assertEquals(JSType.toUint32(-4294967296.0d), 0);
        assertEquals(JSType.toUint32(-4294967297.0d), 4294967295l);
        assertEquals(JSType.toUint32(4294967295.6d), 4294967295l);
        assertEquals(JSType.toUint32(4294967296.6d), 0);
        assertEquals(JSType.toUint32(4294967297.6d), 1);
        assertEquals(JSType.toUint32(-4294967295.6d), 1);
        assertEquals(JSType.toUint32(-4294967296.6d), 0);
        assertEquals(JSType.toUint32(-4294967297.6d), 4294967295l);
    }

    /**
     * Test of JSType.toInt32(double)
     */
    @Test
    public void testToInt32() {
        assertEquals(JSType.toInt32(+0.0), 0);
        assertEquals(JSType.toInt32(-0.0), 0);
        assertEquals(JSType.toInt32(Double.NaN), 0);
        assertEquals(JSType.toInt32(Double.POSITIVE_INFINITY), 0);
        assertEquals(JSType.toInt32(Double.NEGATIVE_INFINITY), 0);
        assertEquals(JSType.toInt32(9223372036854775807.0d), 0);
        assertEquals(JSType.toInt32(-9223372036854775807.0d), 0);
        assertEquals(JSType.toInt32(1099511627776.0d), 0);
        assertEquals(JSType.toInt32(-1099511627776.0d), 0);
        assertEquals(JSType.toInt32(4294967295.0d), -1);
        assertEquals(JSType.toInt32(4294967296.0d), 0);
        assertEquals(JSType.toInt32(4294967297.0d), 1);
        assertEquals(JSType.toInt32(-4294967295.0d), 1);
        assertEquals(JSType.toInt32(-4294967296.0d), 0);
        assertEquals(JSType.toInt32(-4294967297.d), -1);
        assertEquals(JSType.toInt32(4294967295.6d), -1);
        assertEquals(JSType.toInt32(4294967296.6d), 0);
        assertEquals(JSType.toInt32(4294967297.6d), 1);
        assertEquals(JSType.toInt32(-4294967295.6d), 1);
        assertEquals(JSType.toInt32(-4294967296.6d), 0);
        assertEquals(JSType.toInt32(-4294967297.6d), -1);
    }

    /**
     * Test of JSType.toUint16(double)
     */
    @Test
    public void testToUint16() {
        assertEquals(JSType.toUint16(+0.0), 0);
        assertEquals(JSType.toUint16(-0.0), 0);
        assertEquals(JSType.toUint16(Double.NaN), 0);
        assertEquals(JSType.toUint16(Double.POSITIVE_INFINITY), 0);
        assertEquals(JSType.toUint16(Double.NEGATIVE_INFINITY), 0);
        assertEquals(JSType.toUint16(9223372036854775807.0d), 0);
        assertEquals(JSType.toUint16(-9223372036854775807.0d), 0);
        assertEquals(JSType.toUint16(1099511627776.0d), 0);
        assertEquals(JSType.toUint16(-1099511627776.0d), 0);
        assertEquals(JSType.toUint16(4294967295.0d), 65535);
        assertEquals(JSType.toUint16(4294967296.0d), 0);
        assertEquals(JSType.toUint16(4294967297.0d), 1);
        assertEquals(JSType.toUint16(-4294967295.0d), 1);
        assertEquals(JSType.toUint16(-4294967296.0d), 0);
        assertEquals(JSType.toUint16(-4294967297.0d), 65535);
        assertEquals(JSType.toUint16(4294967295.6d), 65535);
        assertEquals(JSType.toUint16(4294967296.6d), 0);
        assertEquals(JSType.toUint16(4294967297.6d), 1);
        assertEquals(JSType.toUint16(-4294967295.6d), 1);
        assertEquals(JSType.toUint16(-4294967296.6d), 0);
        assertEquals(JSType.toUint16(-4294967297.6d), 65535);
    }

}
