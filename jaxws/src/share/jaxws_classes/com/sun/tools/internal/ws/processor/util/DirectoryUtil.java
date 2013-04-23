/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.internal.ws.processor.util;

import com.sun.tools.internal.ws.processor.generator.GeneratorException;
import com.sun.tools.internal.ws.util.ClassNameInfo;

import java.io.File;
import java.io.IOException;

/**
 * Util provides static utility methods used by other wscompile classes.
 *
 * @author WS Development Team
 */
public class DirectoryUtil  {

    public static File getOutputDirectoryFor(String theClass, File rootDir) throws GeneratorException {

        File outputDir = null;
        String qualifiedClassName = theClass;
        String packagePath = null;
        String packageName = ClassNameInfo.getQualifier(qualifiedClassName);
        if (packageName != null && packageName.length() > 0) {
            packagePath = packageName.replace('.', File.separatorChar);
        }

        // Do we have a root directory?
        if (rootDir != null) {

            // Yes, do we have a package name?
            if (packagePath != null) {

                // Yes, so use it as the root. Open the directory...
                outputDir = new File(rootDir, packagePath);

                // Make sure the directory exists...
                ensureDirectory(outputDir);
            } else {

                // Default package, so use root as output dir...
                outputDir = rootDir;
            }
        } else {

            // No root directory. Get the current working directory...
            String workingDirPath = System.getProperty("user.dir");
            File workingDir = new File(workingDirPath);

            // Do we have a package name?
            if (packagePath == null) {

                // No, so use working directory...
                outputDir = workingDir;
            } else {

                // Yes, so use working directory as the root...
                outputDir = new File(workingDir, packagePath);

                // Make sure the directory exists...
                ensureDirectory(outputDir);
            }
        }

        // Finally, return the directory...
        return outputDir;
    }

    public static String getRelativePathfromCommonBase(File file, File base) throws IOException {
        String basePath = base.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.substring(basePath.length());

    }

    private static void ensureDirectory(File dir) throws GeneratorException {
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created || !dir.exists()) {
                throw new GeneratorException("generator.cannot.create.dir",
                    dir.getAbsolutePath());
            }
        }
    }
}
