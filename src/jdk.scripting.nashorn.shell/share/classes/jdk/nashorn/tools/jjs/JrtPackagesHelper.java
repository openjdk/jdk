/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import jdk.nashorn.internal.runtime.Context;

/**
 * A java packages helper that uses jrt file system.
 */
final class JrtPackagesHelper extends PackagesHelper {
    private final FileSystem jrtfs;

    /**
     * Construct a new JrtPackagesHelper.
     *
     * @param context the current Nashorn Context
     */
    JrtPackagesHelper(final Context context) throws IOException {
        super(context);
        jrtfs = FileSystems.getFileSystem(URI.create("jrt:/"));
    }

    @Override
    void close() throws IOException {
    }

    @Override
    Set<String> listPackage(final String pkg) throws IOException {
        final Set<String> props = new HashSet<>();
        // look for the /packages/<package_name> directory
        Path pkgDir = jrtfs.getPath("/packages/" + pkg);
        if (Files.exists(pkgDir)) {
            String pkgSlashName = pkg.replace('.', '/');
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(pkgDir)) {
                // it has module links under which this package occurs
                for (Path mod : ds) {
                    // get the package directory under /modules
                    Path pkgUnderMod = jrtfs.getPath(mod.toString() + "/" + pkgSlashName);
                    try (DirectoryStream<Path> ds2 = Files.newDirectoryStream(pkgUnderMod)) {
                        for (Path p : ds2) {
                            String str = p.getFileName().toString();
                            // get rid of ".class", if any
                            if (str.endsWith(".class")) {
                                final String clsName = str.substring(0, str.length() - ".class".length());
                                if (clsName.indexOf('$') == -1 && isClassAccessible(pkg + "." + clsName)) {
                                    props.add(str);
                                }
                            } else if (isPackageAccessible(pkg + "." + str)) {
                                props.add(str);
                            }
                        }
                    }
                }
            }
        }
        return props;
    }
}
