/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import jdk.tools.jlink.plugin.ModuleEntry;

/**
 * A ModuleEntry backed by a given Archive Entry.
 */
final class ArchiveEntryModuleEntry extends AbstractModuleEntry {
    private final Archive.Entry entry;

    /**
     * Create a new ArchiveModuleEntry.
     *
     * @param module The module name.
     * @param path The data path identifier.
     * @param entry The archive Entry.
     */
    ArchiveEntryModuleEntry(String module, String path, Archive.Entry entry) {
        super(module, path, getImageFileType(Objects.requireNonNull(entry)));
        this.entry = entry;
    }

    @Override
    public InputStream stream() {
        try {
            return entry.stream();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public long getLength() {
        return entry.size();
    }

    private static ModuleEntry.Type getImageFileType(Archive.Entry entry) {
        switch(entry.type()) {
            case CLASS_OR_RESOURCE:
                return ModuleEntry.Type.CLASS_OR_RESOURCE;
            case CONFIG:
                return ModuleEntry.Type.CONFIG;
            case NATIVE_CMD:
                return ModuleEntry.Type.NATIVE_CMD;
            case NATIVE_LIB:
                return ModuleEntry.Type.NATIVE_LIB;
            default:
                return ModuleEntry.Type.OTHER;
        }
    }
}
