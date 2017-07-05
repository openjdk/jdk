/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.hotspot;

//@formatter:off

/**
 * @test
 * @bug 7005594
 * @summary Array overflow not handled correctly with loop optimzations
 *
 * @run shell Test7005594.sh
 */
public class Test7005594 {

    private static int test0(byte[] a) {
        int result = 0;
        for (int i = 0; i < a.length; i += ((0x7fffffff >> 1) + 1)) {
            result += a[i];
        }
        return result;
    }

    public static int test() {
        byte[] a = new byte[(0x7fffffff >> 1) + 2];
        try {
            test0(a);
        } catch (ArrayIndexOutOfBoundsException e) {
            return 95;
        }
        return 97;
    }

}
