/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import jdk.jpackage.internal.model.JPackageException;

/**
 * IOUtils
 *
 * A collection of static utility methods.
 */
final class IOUtils {

    public static void copyFile(Path sourceFile, Path destFile)
            throws IOException {
        Files.createDirectories(destFile.getParent());

        Files.copy(sourceFile, destFile,
                   StandardCopyOption.REPLACE_EXISTING,
                   StandardCopyOption.COPY_ATTRIBUTES);
    }

    static void writableOutputDir(Path outdir) {
        if (!Files.isDirectory(outdir)) {
            try {
                Files.createDirectories(outdir);
            } catch (IOException ex) {
                throw new JPackageException(I18N.format("error.cannot-create-output-dir", outdir.toAbsolutePath()));
            }
        }

        if (!Files.isWritable(outdir)) {
            throw new JPackageException(I18N.format("error.cannot-write-to-output-dir", outdir.toAbsolutePath()));
        }
    }
}
