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

package jdk.tools.jlink.internal;

import jdk.tools.jlink.internal.JarArchive;
import java.nio.file.Path;
import java.util.Objects;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;

/**
 * An Archive backed by a jmod file.
 */
public class JmodArchive extends JarArchive {

    private static final String JMOD_EXT = ".jmod";
    private static final String MODULE_NAME = "module";
    private static final String MODULE_INFO = "module-info.class";
    private static final String CLASSES     = "classes";
    private static final String NATIVE_LIBS = "native";
    private static final String NATIVE_CMDS = "bin";
    private static final String CONFIG      = "conf";

    public JmodArchive(String mn, Path jmod) {
        super(mn, jmod);
        String filename = Objects.requireNonNull(jmod.getFileName()).toString();
        if (!filename.endsWith(JMOD_EXT))
            throw new UnsupportedOperationException("Unsupported format: " + filename);
    }

    @Override
    EntryType toEntryType(String entryName) {
        String section = getSection(entryName.replace('\\', '/'));
        switch (section) {
            case CLASSES:
                return EntryType.CLASS_OR_RESOURCE;
            case NATIVE_LIBS:
                return EntryType.NATIVE_LIB;
            case NATIVE_CMDS:
                return EntryType.NATIVE_CMD;
            case CONFIG:
                return EntryType.CONFIG;
            case MODULE_NAME:
                return EntryType.MODULE_NAME;
            default:
                //throw new InternalError("unexpected entry: " + name + " " + zipfile.toString()); //TODO
                throw new InternalError("unexpected entry: " + section);
        }
    }

    private static String getSection(String entryName) {
        int i = entryName.indexOf('/');
        // Unnamed section.
        String section = "";
        if (i > 0) {
            section = entryName.substring(0, entryName.indexOf('/'));
        }
        return section;
    }

    @Override
    String getFileName(String entryName) {
        entryName = entryName.replace('\\', '/');
        return entryName.substring(entryName.indexOf('/') + 1);
    }
}
