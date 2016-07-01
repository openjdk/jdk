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
package jdk.tools.jlink.plugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.tools.jlink.internal.ModuleEntryFactory;

/**
 * A ModuleEntry is the elementary unit of data inside an image. It is
 * generally a file. e.g.: a java class file, a resource file, a shared library.
 * <br>
 * A ModuleEntry is identified by a path of the form:
 * <ul>
 * <li>For jimage content: /{module name}/{package1}/.../{packageN}/{file
 * name}</li>
 * <li>For other files (shared lib, launchers, config, ...):/{module name}/
 * {@literal bin|conf|native}/{dir1}/.../{dirN}/{file name}</li>
 * </ul>
 */
public interface ModuleEntry {

    /**
     * Type of module data.
     * <li>
     * <ul>CLASS_OR_RESOURCE: A java class or resource file.</ul>
     * <ul>CONFIG: A configuration file.</ul>
     * <ul>NATIVE_CMD: A native process launcher.</ul>
     * <ul>NATIVE_LIB: A native library.</ul>
     * <ul>OTHER: Other kind of file.</ul>
     * </li>
     */
    public enum Type {
        CLASS_OR_RESOURCE,
        CONFIG,
        NATIVE_CMD,
        NATIVE_LIB,
        OTHER
    }
    /**
     * The ModuleEntry module name.
     *
     * @return The module name.
     */
    public String getModule();

    /**
     * The ModuleEntry path.
     *
     * @return The module path.
     */
    public String getPath();

    /**
     * The ModuleEntry's type.
     *
     * @return The data type.
     */
    public Type getType();

    /**
     * The ModuleEntry content as an array of bytes.
     *
     * @return An Array of bytes.
     */
    public default byte[] getBytes() {
        try (InputStream is = stream()) {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * The ModuleEntry content length.
     *
     * @return The length.
     */
    public long getLength();

    /**
     * The ModuleEntry stream.
     *
     * @return The module data stream.
     */
    public InputStream stream();

    /**
     * Write the content of this ModuleEntry to stream.
     *
     * @param out the output stream
     */
    public default void write(OutputStream out) {
        try {
            out.write(getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Create a ModuleEntry with new content but other information
     * copied from this ModuleEntry.
     *
     * @param content The new resource content.
     * @return A new ModuleEntry.
     */
    public default ModuleEntry create(byte[] content) {
        return ModuleEntryFactory.create(this, content);
    }

    /**
     * Create a ModuleEntry with new content but other information
     * copied from this ModuleEntry.
     *
     * @param file The new resource content.
     * @return A new ModuleEntry.
     */
    public default ModuleEntry create(Path file) {
        return ModuleEntryFactory.create(this, file);
    }

    /**
     * Create a ModuleEntry for a resource of the given type.
     *
     * @param path The resource path.
     * @param type The ModuleEntry type.
     * @param content The resource content.
     * @return A new ModuleEntry.
     */
    public static ModuleEntry create(String path,
            ModuleEntry.Type type, byte[] content) {
        return ModuleEntryFactory.create(path, type, content);
    }

    /**
     * Create a ModuleEntry for a resource of type {@link Type#CLASS_OR_RESOURCE}.
     *
     * @param path The resource path.
     * @param content The resource content.
     * @return A new ModuleEntry.
     */
    public static ModuleEntry create(String path, byte[] content) {
        return create(path, Type.CLASS_OR_RESOURCE, content);
    }

    /**
     * Create a ModuleEntry for a resource of the given type.
     *
     * @param path The resource path.
     * @param type The ModuleEntry type.
     * @param file The resource file.
     * @return A new ModuleEntry.
     */
    public static ModuleEntry create(String path,
            ModuleEntry.Type type, Path file) {
        return ModuleEntryFactory.create(path, type, file);
    }

    /**
     * Create a ModuleEntry for a resource of type {@link Type#CLASS_OR_RESOURCE}.
     *
     * @param path The resource path.
     * @param file The resource file.
     * @return A new ModuleEntry.
     */
    public static ModuleEntry create(String path, Path file) {
        return create(path, Type.CLASS_OR_RESOURCE, file);
    }
}
