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
 * @build GeneratedClassLoader
 * @run main/othervm/timeout=200 FragmentMetaspace
 */

import java.io.IOException;

/**
 * Test that tries to fragment the native memory used by class loaders.
 * This test creates class loaders that load classes of increasing size for every
 * iteration. By increasing the size of the class meta data needed for every iteration
 * we stress the subsystem for allocating native memory for meta data.
 */
public class FragmentMetaspace {

    public static void main(String... args) {
        runGrowing(Long.valueOf(System.getProperty("time", "80000")));
        // try to clean up and unload classes to decrease
        // class verification time in debug vm
        System.gc();
    }

    private static void runGrowing(long time) {
        long startTime = System.currentTimeMillis();
        for (int i = 0; System.currentTimeMillis() < startTime + time; ++i) {
            try {
                GeneratedClassLoader gcl = new GeneratedClassLoader();

                Class<?> c = gcl.getGeneratedClasses(i, 100)[0];
                c.newInstance();
                c = null;

                gcl = null;
            } catch (IOException|InstantiationException|IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
