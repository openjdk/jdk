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

package com.oracle.java.testlibrary;

import java.io.File;

public final class JDKToolFinder {

    private JDKToolFinder() {
    }

    /**
     * Returns the full path to an executable in jdk/bin based on System
     * property {@code compile.jdk} (set by jtreg test suite)
     *
     * @return Full path to an executable in jdk/bin
     */
    public static String getJDKTool(String tool) {
        String binPath = System.getProperty("compile.jdk");
        if (binPath == null) {
            throw new RuntimeException("System property 'compile.jdk' not set. "
                    + "This property is normally set by jtreg. "
                    + "When running test separately, set this property using "
                    + "'-Dcompile.jdk=/path/to/jdk'.");
        }
        binPath += File.separatorChar + "bin" + File.separatorChar + tool;

        return binPath;
    }
    /**
     * Returns the full path to an executable in &lt;current jdk&gt;/bin based
     * on System property {@code test.jdk} (set by jtreg test suite)
     *
     * @return Full path to an executable in jdk/bin
     */
    public static String getCurrentJDKTool(String tool) {
        String binPath = System.getProperty("test.jdk");
        if (binPath == null) {
            throw new RuntimeException("System property 'test.jdk' not set. "
                + "This property is normally set by jtreg. "
                + "When running test separately, set this property using "
                + "'-Dtest.jdk=/path/to/jdk'.");
        }
        binPath += File.separatorChar + "bin" + File.separatorChar + tool;

        return binPath;
    }
}
