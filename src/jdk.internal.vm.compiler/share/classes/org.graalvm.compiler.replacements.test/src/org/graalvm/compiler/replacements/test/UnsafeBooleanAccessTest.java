/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.lang.reflect.Field;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class UnsafeBooleanAccessTest extends GraalCompilerTest {

    private static short onHeapMemory;

    private static final Object onHeapMemoryBase;
    private static final long onHeapMemoryOffset;

    static {
        try {
            Field staticField = UnsafeBooleanAccessTest.class.getDeclaredField("onHeapMemory");
            onHeapMemoryBase = UNSAFE.staticFieldBase(staticField);
            onHeapMemoryOffset = UNSAFE.staticFieldOffset(staticField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean testGetBooleanSnippet() {
        UNSAFE.putShort(onHeapMemoryBase, onHeapMemoryOffset, (short) 0x0204);
        return UNSAFE.getBoolean(onHeapMemoryBase, onHeapMemoryOffset);
    }

    @Test
    public void testGetBoolean() {
        test("testGetBooleanSnippet");
    }

    public static short testPutBooleanSnippet() {
        UNSAFE.putShort(onHeapMemoryBase, onHeapMemoryOffset, (short) 0x0204);
        boolean bool = UNSAFE.getBoolean(onHeapMemoryBase, onHeapMemoryOffset);
        UNSAFE.putBoolean(onHeapMemoryBase, onHeapMemoryOffset, bool);
        return onHeapMemory;
    }

    @Test
    public void testPutBoolean() {
        test("testPutBooleanSnippet");
    }

    public static boolean testAndBooleanSnippet() {
        UNSAFE.putShort(onHeapMemoryBase, onHeapMemoryOffset, (short) 0x0204);
        boolean bool0 = UNSAFE.getBoolean(onHeapMemoryBase, onHeapMemoryOffset);
        boolean bool1 = UNSAFE.getBoolean(onHeapMemoryBase, onHeapMemoryOffset + 1);
        return bool0 & bool1;
    }

    @Test
    public void testAndBoolean() {
        test("testAndBooleanSnippet");
    }

}
