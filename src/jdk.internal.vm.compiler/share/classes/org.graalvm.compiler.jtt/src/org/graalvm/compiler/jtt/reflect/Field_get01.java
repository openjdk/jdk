/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.reflect;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Field_get01 extends JTTTest {

    public static final byte byteField = 11;
    public static final short shortField = 12;
    public static final char charField = 13;
    public static final int intField = 14;
    public static final long longField = 15;
    public static final float floatField = 16;
    public static final double doubleField = 17;
    public static final boolean booleanField = true;

    public static boolean test(int arg) throws NoSuchFieldException, IllegalAccessException {
        if (arg == 0) {
            return Field_get01.class.getField("byteField").get(null).equals(byteField);
        } else if (arg == 1) {
            return Field_get01.class.getField("shortField").get(null).equals(shortField);
        } else if (arg == 2) {
            return Field_get01.class.getField("charField").get(null).equals(charField);
        } else if (arg == 3) {
            return Field_get01.class.getField("intField").get(null).equals(intField);
        } else if (arg == 4) {
            return Field_get01.class.getField("longField").get(null).equals(longField);
        } else if (arg == 5) {
            return Field_get01.class.getField("floatField").get(null).equals(floatField);
        } else if (arg == 6) {
            return Field_get01.class.getField("doubleField").get(null).equals(doubleField);
        } else if (arg == 7) {
            return Field_get01.class.getField("booleanField").get(null).equals(booleanField);
        }
        return false;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 7);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 8);
    }

}
