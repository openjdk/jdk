/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jrtfs;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
final class SystemImages {
    private SystemImages() {}


    static final String RUNTIME_HOME;
    // "modules" jimage file Path
    private static final Path moduleImageFile;
    // "modules" jimage exists or not?
    private static final boolean modulesImageExists;
    // <JAVA_HOME>/modules directory Path
    private static final Path explodedModulesDir;

    static {
        PrivilegedAction<String> pa = SystemImages::findHome;
        RUNTIME_HOME = AccessController.doPrivileged(pa);

        FileSystem fs = FileSystems.getDefault();
        moduleImageFile = fs.getPath(RUNTIME_HOME, "lib", "modules");
        explodedModulesDir = fs.getPath(RUNTIME_HOME, "modules");

        modulesImageExists = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return Files.isRegularFile(moduleImageFile);
                }
            });
    }

    static boolean hasModulesImage() {
        return modulesImageExists;
    }

    static Path moduleImageFile() {
        return moduleImageFile;
    }

    static Path explodedModulesDir() {
        return explodedModulesDir;
    }

    /**
     * Returns the appropriate JDK home for this usage of the FileSystemProvider.
     * When the CodeSource is null (null loader) then jrt:/ is the current runtime,
     * otherwise the JDK home is located relative to jrt-fs.jar.
     */
    private static String findHome() {
        CodeSource cs = SystemImages.class.getProtectionDomain().getCodeSource();
        if (cs == null)
            return System.getProperty("java.home");

        // assume loaded from $TARGETJDK/jrt-fs.jar
        URL url = cs.getLocation();
        if (!url.getProtocol().equalsIgnoreCase("file"))
            throw new RuntimeException(url + " loaded in unexpected way");
        try {
            return Paths.get(url.toURI()).getParent().toString();
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }
    }
}
