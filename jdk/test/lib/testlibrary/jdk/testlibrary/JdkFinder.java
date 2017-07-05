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

package jdk.testlibrary;

import java.io.File;

public final class JdkFinder {

    private JdkFinder() {
    }

    private static String getExecutable(String executable, String property) {
        String binPath = System.getProperty(property);
        if (binPath == null) {
            throw new RuntimeException(
                    "System property '" + property + "' not set");
        }

        binPath += File.separatorChar + "bin" + File.separatorChar + executable;
        File toolFile = new File(binPath);
        if (!toolFile.exists()) {
            throw new RuntimeException(binPath + " does not exist");
        }

        return binPath;
    }

    /**
     * Returns the full path to a java launcher in jdk/bin based on system
     * property.
     *
     * @param stableJdk
     *            see {@link #getTool(String, boolean)}
     * @return Full path to a java launcher in jdk/bin.
     */
    public static String getJavaLauncher(boolean stableJdk) {
        return getTool("java", stableJdk);
    }

    /**
     * Returns the full path to an executable in jdk/bin based on system
     * property. Depending on value of {@code stableJdk} the method will look for
     * either 'compile.jdk' or 'test.jdk' system properties.
     * 'test.jdk' is normally set by jtreg. When running test separately,
     * set this property using '-Dtest.jdk=/path/to/jdk'.
     *
     * @param stableJdk
     *            If {@code true} the {@code tool} will be retrieved
     *            from the compile (stable) JDK.
     *            If {@code false} the {@code tool} will be retrieved
     *            from the test JDK.
     * @return Full path to an executable in jdk/bin.
     */
    public static String getTool(String tool, boolean stableJdk) {
        if (stableJdk) {
            return getExecutable(tool, "compile.jdk");
        } else {
            return getExecutable(tool, "test.jdk");
        }
    }
}
