/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.util.TreeSet;
import java.util.Set;
import sun.misc.BootClassLoaderHook;

/* @test
 * @bug     6888802
 * @summary Sanity test of BootClassLoaderHook interface
 *
 * @build TestHook
 * @run main TestHook
 */

public class TestHook extends BootClassLoaderHook {

    private static final TestHook hook = new TestHook();
    private static Set<String> names = new TreeSet<String>();
    private static final String LOGRECORD_CLASS =
        "java.util.logging.LogRecord";
    private static final String NONEXIST_RESOURCE =
        "non.exist.resource";
    private static final String LIBHELLO = "hello";

    public static void main(String[] args) throws Exception {
        BootClassLoaderHook.setHook(hook);
        if (BootClassLoaderHook.getHook() == null) {
           throw new RuntimeException("Null boot classloader hook ");
        }

        testHook();

        if (!names.contains(LOGRECORD_CLASS)) {
           throw new RuntimeException("loadBootstrapClass for " + LOGRECORD_CLASS + " not called");
        }

        if (!names.contains(NONEXIST_RESOURCE)) {
           throw new RuntimeException("getBootstrapResource for " + NONEXIST_RESOURCE + " not called");
        }
        if (!names.contains(LIBHELLO)) {
           throw new RuntimeException("loadLibrary for " + LIBHELLO + " not called");
        }

        Set<String> copy = new TreeSet<String>();
        copy.addAll(names);
        for (String s : copy) {
            System.out.println("  Loaded " + s);
        }

        if (BootClassLoaderHook.getBootstrapPaths().length > 0) {
           throw new RuntimeException("Unexpected returned value from getBootstrapPaths()");
        }
    }

    private static void testHook() throws Exception {
        Class.forName(LOGRECORD_CLASS);
        ClassLoader.getSystemResource(NONEXIST_RESOURCE);
        try {
          System.loadLibrary(LIBHELLO);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public String loadBootstrapClass(String className) {
        names.add(className);
        return null;
    }

    public String getBootstrapResource(String resourceName) {
        names.add(resourceName);
        return null;
    }

    public boolean loadLibrary(String libname) {
        names.add(libname);
        return false;
    }

    public File[] getAdditionalBootstrapPaths() {
        return new File[0];
    }

    public boolean isCurrentThreadPrefetching() {
        return false;
    }

    public boolean prefetchFile(String name) {
        return false;
    }
}
