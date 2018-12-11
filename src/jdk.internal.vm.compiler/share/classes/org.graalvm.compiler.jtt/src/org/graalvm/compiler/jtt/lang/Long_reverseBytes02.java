/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.lang;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Long_reverseBytes02 extends JTTTest {

    public static long test(long val) {
        return (((val >> 56) & 0xff) << 0) | (((val >> 48) & 0xff) << 8) | (((val >> 40) & 0xff) << 16) | (((val >> 32) & 0xff) << 24) | (((val >> 24) & 0xff) << 32) | (((val >> 16) & 0xff) << 40) |
                        (((val >> 8) & 0xff) << 48) | (((val >> 0) & 0xff) << 56);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0x1122334455667708L);
    }

}
