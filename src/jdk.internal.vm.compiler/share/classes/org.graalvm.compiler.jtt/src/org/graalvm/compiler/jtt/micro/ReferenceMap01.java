/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.micro;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class ReferenceMap01 extends JTTTest {

    public static Integer val1 = new Integer(3);
    public static Integer val2 = new Integer(4);

    @SuppressWarnings("unused")
    private static String foo(String[] a) {
        String[] args = new String[]{"78"};
        Integer i1 = new Integer(1);
        Integer i2 = new Integer(2);
        Integer i3 = val1;
        Integer i4 = val2;
        Integer i5 = new Integer(5);
        Integer i6 = new Integer(6);
        Integer i7 = new Integer(7);
        Integer i8 = new Integer(8);
        Integer i9 = new Integer(9);
        Integer i10 = new Integer(10);
        Integer i11 = new Integer(11);
        Integer i12 = new Integer(12);

        System.gc();
        int sum = i1 + i2 + i3 + i4 + i5 + i6 + i7 + i8 + i9 + i10 + i11 + i12;
        return args[0] + sum;
    }

    public static int test() {
        return Integer.valueOf(foo(new String[]{"asdf"}));
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
