/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /runtime/testlibrary
 * @library classes
 * @build test.Empty ClassUnloadCommon
 * @run main/othervm/timeout=200 FragmentMetaspaceSimple
 */

import java.util.ArrayList;

/**
 * Test that tries to fragment the native memory used by class loaders.
 * Keeps every other class loader alive in order to fragment the memory space
 * used to store classes and meta data. Since the memory is probably allocated in
 * chunks per class loader this will cause a lot of fragmentation if not handled
 * properly since every other chunk will be unused.
 */
public class FragmentMetaspaceSimple {
    public static void main(String... args) {
        runSimple(Long.valueOf(System.getProperty("time", "80000")));
        System.gc();
    }

    private static void runSimple(long time) {
        long startTime = System.currentTimeMillis();
        ArrayList<ClassLoader> cls = new ArrayList<>();
        for (int i = 0; System.currentTimeMillis() < startTime + time; ++i) {
            ClassLoader ldr = ClassUnloadCommon.newClassLoader();
            if (i % 1000 == 0) {
                cls.clear();
            }
            // only keep every other class loader alive
            if (i % 2 == 1) {
                cls.add(ldr);
            }
            Class<?> c = null;
            try {
                c = ldr.loadClass("test.Empty");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
            c = null;
        }
        cls = null;
    }
}
