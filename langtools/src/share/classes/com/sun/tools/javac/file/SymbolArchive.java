/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import com.sun.tools.javac.util.List;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.tools.JavaFileObject;

public class SymbolArchive extends ZipArchive {

    final File origFile;
    final String prefix;

    public SymbolArchive(JavacFileManager fileManager, File orig, ZipFile zdir, String prefix) throws IOException {
        super(fileManager, zdir);
        this.origFile = orig;
        this.prefix = prefix;
    }

    @Override
    void addZipEntry(ZipEntry entry) {
        String name = entry.getName();
        if (!name.startsWith(prefix)) {
            return;
        }
        name = name.substring(prefix.length());
        int i = name.lastIndexOf('/');
        String dirname = name.substring(0, i + 1);
        String basename = name.substring(i + 1);
        if (basename.length() == 0) {
            return;
        }
        List<String> list = map.get(dirname);
        if (list == null) {
            list = List.nil();
        }
        list = list.prepend(basename);
        map.put(dirname, list);
    }

    @Override
    public JavaFileObject getFileObject(String subdirectory, String file) {
        return super.getFileObject(prefix + subdirectory, file);
    }
}
