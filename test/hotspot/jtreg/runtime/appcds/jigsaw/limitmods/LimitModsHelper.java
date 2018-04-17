/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * Used with -p or --upgrade-module-path to exercise the replacement
 * of classes in modules that are linked into the runtime image.
 */

import java.lang.*;
import java.lang.reflect.*;
import sun.hotspot.WhiteBox;


public class LimitModsHelper {
    static final ClassLoader PLATFORM_LOADER = ClassLoader.getPlatformClassLoader();
    static final ClassLoader SYS_LOADER      = ClassLoader.getSystemClassLoader();

    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 4);
        String[] classNames = new String[3];
        for (int i = 0; i < 3; i++) {
            classNames[i] = args[i].replace('/', '.');
        }
        int excludeModIdx = Integer.parseInt(args[3]);

        ClassLoader expectedLoaders[] = {null, PLATFORM_LOADER, SYS_LOADER};

        WhiteBox wb = WhiteBox.getWhiteBox();

        Class<?> clazz = null;
        for (int i = 0; i < 3; i++) {
            try {
                // Load the class with the default ClassLoader.
                clazz = Class.forName(classNames[i]);
            } catch (Exception e) {
                if (i == excludeModIdx) {
                    System.out.println(classNames[i] + " not found as expected because the module isn't in the --limit-modules - PASSED");
                } else {
                    throw(e);
                }
            }

            if (clazz != null && i != excludeModIdx) {
                // Make sure we got the expected defining ClassLoader
                testLoader(clazz, expectedLoaders[i]);

                // Make sure the class is not in the shared space
                // because CDS is disabled with --limit-modules during run time.
                if (excludeModIdx != -1) {
                    if (wb.isSharedClass(clazz)) {
                        throw new RuntimeException(clazz.getName() +
                            ".class should not be in the shared space. " +
                             "loader=" + clazz.getClassLoader() + " module=" + clazz.getModule().getName());
                    }
                } else {
                    // class should be in the shared space if --limit-modules
                    // isn't specified during run time
                    if (!wb.isSharedClass(clazz)) {
                        throw new RuntimeException(clazz.getName() +
                            ".class should be in the shared space. " +
                             "loader=" + clazz.getClassLoader() + " module=" + clazz.getModule().getName());
                    }
                }
            }
            clazz = null;
        }
    }

    /**
     * Asserts that given class has the expected defining loader.
     */
    static void testLoader(Class<?> clazz, ClassLoader expected) {
        ClassLoader loader = clazz.getClassLoader();
        if (loader != expected) {
            throw new RuntimeException(clazz + " loaded by " + loader + ", expected " + expected);
        }
    }

    static void assertTrue(boolean expr) {
        if (!expr)
            throw new RuntimeException("assertion failed");
    }
}
