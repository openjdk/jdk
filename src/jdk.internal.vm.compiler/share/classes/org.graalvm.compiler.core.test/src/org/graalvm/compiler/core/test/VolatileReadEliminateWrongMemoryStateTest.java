/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Test;

// See https://bugs.openjdk.java.net/browse/JDK-8247832
public class VolatileReadEliminateWrongMemoryStateTest extends GraalCompilerTest {

    private static volatile int volatileField;
    private static int field;

    @SuppressWarnings("unused")
    public static int testMethod() {
        field = 0;
        int v = volatileField;
        field += 1;
        v = volatileField;
        field += 1;
        return field;
    }

    @Test
    public void test1() {
        test("testMethod");
    }

    public static void testMethod2(Object obj) {
        synchronized (obj) {
            volatileField++;
        }
    }

    @Test
    public void test2() {
        test("testMethod2", new Object());
    }
}
