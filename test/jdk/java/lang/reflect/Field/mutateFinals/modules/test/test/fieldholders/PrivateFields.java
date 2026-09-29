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
package test.fieldholders;

import java.lang.reflect.Field;

/**
 * public class with private final fields.
 */
public class PrivateFields {
    private final Object obj;
    private final boolean z;
    private final byte b;
    private final char c;
    private final short s;
    private final int i;
    private final long l;
    private final float f;
    private final double d;

    public PrivateFields() {
        obj = new Object();
        z = false;
        b = 0;
        c = 0;
        s = 0;
        i = 0;
        l = 0;
        f = 0.0f;
        d = 0.0d;
    }

    public static Field objectField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("obj");
    }

    public static Field booleanField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("z");
    }

    public static Field byteField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("b");
    }

    public static Field charField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("c");
    }

    public static Field shortField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("s");
    }

    public static Field intField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("i");
    }

    public static Field longField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("l");
    }

    public static Field floatField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("f");
    }

    public static Field doubleField() throws NoSuchFieldException {
        return PrivateFields.class.getDeclaredField("d");
    }

    public Object objectValue() {
        return obj;
    }

    public boolean booleanValue() {
        return z;
    }

    public byte byteValue() {
        return b;
    }

    public char charValue() {
        return c;
    }

    public short shortValue() {
        return s;
    }

    public int intValue() {
        return i;
    }

    public long longValue() {
        return l;
    }

    public float floatValue() {
        return f;
    }

    public double doubleValue() {
        return d;
    }
}
