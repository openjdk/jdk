/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.compile_framework;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class, with many helper methods for the Compile Framework.
 */
class Utils {
    private static final boolean VERBOSE = Boolean.getBoolean("CompileFrameworkVerbose");

    /**
     * Verbose printing, enabled with {@code -DCompileFrameworkVerbose=true}.
     */
    public static void printlnVerbose(String s) {
        if (VERBOSE) {
            System.out.println(s);
        }
    }

    /**
     * Create a temporary directory with a unique name to avoid collisions
     * with multi-threading. Used to create the sources and classes directories. Since they
     * are unique even across threads, the Compile Framework is multi-threading safe, i.e.
     * it does not have collisions if two instances generate classes with the same name.
     */
    public static Path makeUniqueDir(String prefix) {
        try {
            return Files.createTempDirectory(Paths.get("."), prefix);
        } catch (Exception e) {
            throw new InternalCompileFrameworkException("Could not set up temporary directory", e);
        }
    }

    /**
     * Get all paths in the classpath.
     */
    public static String[] getClassPaths() {
        String separator = File.pathSeparator;
        return System.getProperty("java.class.path").split(separator);
    }

    /**
     * Return the classpath, appended with the {@code classesDir}.
     */
    public static String getEscapedClassPathAndClassesDir(Path classesDir) {
        String cp = System.getProperty("java.class.path") +
                    File.pathSeparator +
                    classesDir.toAbsolutePath();
        // Escape the backslash for Windows paths. We are using the path in the
        // command-line and Java code, so we always want it to be escaped.
        return cp.replace("\\", "\\\\");
    }
}
