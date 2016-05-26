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
 * A LinkModuleEntry is the elementary unit of data inside an image. It is
 * generally a file. e.g.: a java class file, a resource file, a shared library,
 * ...
 * <br>
 * A LinkModuleEntry is identified by a path of the form:
 * <ul>
 * <li>For jimage content: /{module name}/{package1}/.../{packageN}/{file
 * name}</li>
 * <li>For other files (shared lib, launchers, config, ...):/{module name}/
 * {@literal bin|conf|native}/{dir1}>/.../{dirN}/{file name}</li>
 * </ul>
 */
public class ModuleEntryImpl implements ModuleEntry {

    private final Type type;
    private final String path;
    private final String module;
    private final long length;
    private final InputStream stream;
    private byte[] buffer;

    /**
     * Create a new LinkModuleEntry.
     *
     * @param module The module name.
     * @param path The data path identifier.
     * @param type The data type.
     * @param stream The data content stream.
     * @param length The stream length.
     */
    public ModuleEntryImpl(String module, String path, Type type, InputStream stream, long length) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        Objects.requireNonNull(stream);
        this.path = path;
        this.type = type;
        this.module = module;
        this.stream = stream;
        this.length = length;
    }

    /**
     * The LinkModuleEntry module name.
     *
     * @return The module name.
     */
    @Override
    public final String getModule() {
        return module;
    }

    /**
     * The LinkModuleEntry path.
     *
     * @return The module path.
     */
    @Override
    public final String getPath() {
        return path;
    }

    /**
     * The LinkModuleEntry's type.
     *
     * @return The data type.
     */
    @Override
    public final Type getType() {
        return type;
    }

    /**
     * The LinkModuleEntry content as an array of byte.
     *
     * @return An Array of bytes.
     */
    @Override
    public byte[] getBytes() {
        if (buffer == null) {
            try (InputStream is = stream) {
                buffer = is.readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return buffer;
    }

    /**
     * The LinkModuleEntry content length.
     *
     * @return The length.
     */
    @Override
    public long getLength() {
        return length;
    }

    /**
     * The LinkModuleEntry stream.
     *
     * @return The module data stream.
     */
    @Override
    public InputStream stream() {
        return stream;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.path);
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModuleEntryImpl)) {
            return false;
        }
        ModuleEntryImpl f = (ModuleEntryImpl) other;
        return f.path.equals(path);
    }

    @Override
    public String toString() {
        return getPath();
    }
}