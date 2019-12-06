/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ModFile {
    private final String filename;
    private final ModType moduleType;

    enum JarType {All, UnnamedJar, ModularJar}
    enum ModType {
            Unknown, UnnamedJar, ModularJar, Jmod, ExplodedModule}

    ModFile(File aFile) {
        super();
        filename = aFile.getPath();
        moduleType = getModType(aFile);
    }

    String getModName() {
        File file = new File(getFileName());
        // do not try to remove extension for directories
        return moduleType == ModType.ExplodedModule ?
                file.getName() : getFileWithoutExtension(file.getName());
    }

    String getFileName() {
        return filename;
    }

    ModType getModType() {
        return moduleType;
    }

    private static ModType getModType(File aFile) {
        ModType result = ModType.Unknown;
        String filename = aFile.getAbsolutePath();

        if (aFile.isFile()) {
            if (filename.endsWith(".jmod")) {
                result = ModType.Jmod;
            }
            else if (filename.endsWith(".jar")) {
                JarType status = isModularJar(filename);

                if (status == JarType.ModularJar) {
                    result = ModType.ModularJar;
                }
                else if (status == JarType.UnnamedJar) {
                    result = ModType.UnnamedJar;
                }
            }
        }
        else if (aFile.isDirectory()) {
            File moduleInfo = new File(
                    filename + File.separator + "module-info.class");

            if (moduleInfo.exists()) {
                result = ModType.ExplodedModule;
            }
        }

        return result;
    }

    private static JarType isModularJar(String FileName) {
        JarType result = JarType.All;

        try (ZipInputStream zip =
                    new ZipInputStream(new FileInputStream(FileName))) {
            result = JarType.UnnamedJar;

            for (ZipEntry entry = zip.getNextEntry(); entry != null;
                    entry = zip.getNextEntry()) {
                if (entry.getName().matches("module-info.class")) {
                    result = JarType.ModularJar;
                    break;
                }
            }
        } catch (IOException ex) {
        }

        return result;
    }

    private static String getFileWithoutExtension(String FileName) {
        return FileName.replaceFirst("[.][^.]+$", "");
    }
}
