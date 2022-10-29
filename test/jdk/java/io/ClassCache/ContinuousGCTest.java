/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

import java.io.NameClassCache;

/**
 * @test
 * @bug 8280041
 * @summary Sanity test for ClassCache under continuous GC
 * @compile/module=java.base java/io/NameClassCache.java
 * @run main ContinuousGCTest
 */
public class ContinuousGCTest {
    static final NameClassCache CACHE = new NameClassCache();
    static final String VALUE = "ClassCache-ContinuousGCTest";

    public static void main(String... args) throws Throwable {
        for (int c = 0; c < 1000; c++) {
            test();
            System.gc();
        }
    }

    public static void test() {
        String cached = CACHE.get(ContinuousGCTest.class);
        if (!cached.equals(VALUE)) {
            throw new IllegalStateException("Cache failure, got: " + cached);
        }
    }
}
