/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop
package org.graalvm.compiler.jtt.hotpath;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class HP_field03 extends JTTTest {

    public static byte b;
    public static char c;
    public static short s;
    public static int i;
    public static long l;
    public static float f;
    public static double d;

    public static int test(int count) {
        for (int x = 0; x <= count; x++) {
            b += x;
            c += x;
            s += x;
            i += x;
            l += x;
            f += x;
            d += x;
        }
        return (int) (b + c + s + i + l + f + d);
    }

    @Override
    public void before(ResolvedJavaMethod m) {
        b = 0;
        c = 0;
        s = 0;
        i = 0;
        l = 0L;
        f = 0.0F;
        d = 0.0D;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 1000);
    }

}
