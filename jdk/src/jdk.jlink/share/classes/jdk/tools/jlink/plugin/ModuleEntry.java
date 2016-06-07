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
import java.util.Objects;
import jdk.tools.jlink.internal.ImageFileCreator;
import jdk.tools.jlink.internal.ModuleEntryImpl;

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
     * The LinkModuleEntry module name.
     *
     * @return The module name.
     */
    public String getModule();

    /**
     * The LinkModuleEntry path.
     *
     * @return The module path.
     */
    public String getPath();

    /**
     * The LinkModuleEntry's type.
     *
     * @return The data type.
     */
    public Type getType();

    /**
     * The LinkModuleEntry content as an array of byte.
     *
     * @return An Array of bytes.
     */
    public byte[] getBytes();

    /**
     * The LinkModuleEntry content length.
     *
     * @return The length.
     */
    public long getLength();

    /**
     * The LinkModuleEntry stream.
     *
     * @return The module data stream.
     */
    public InputStream stream();


    /**
     * Create a LinkModuleEntry located inside a jimage file. Such
     * LinkModuleEntry has a Type being equals to CLASS_OR_RESOURCE.
     *
     * @param path The complete resource path (contains the module radical).
     * @param content The resource content.
     * @param size The content size.
     * @return A new LinkModuleEntry.
     */
    public static ModuleEntry create(String path, InputStream content, long size) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        String[] split = ImageFileCreator.splitPath(path);
        String module = split[0];
        return new ModuleEntryImpl(module, path, Type.CLASS_OR_RESOURCE, content, size);
    }

    /**
     * Create a LinkModuleEntry for a file that will be located inside a jimage
     * file.
     *
     * @param path The resource path.
     * @param content The resource content.
     * @return A new LinkModuleEntry.
     */
    public static ModuleEntry create(String path, byte[] content) {
        return create(path, new ByteArrayInputStream(content),
                content.length);
    }

    /**
     * Create a LinkModuleEntry for a file that will be located outside a jimage
     * file.
     *
     * @param module The module in which this files is located.
     * @param path The file path locator (doesn't contain the module name).
     * @param type The LinkModuleEntry type.
     * @param content The file content.
     * @param size The content size.
     * @return A new LinkModuleEntry.
     */
    public static ModuleEntry create(String module, String path, ModuleEntry.Type type,
            InputStream content, long size) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        return new ModuleEntryImpl(module, path, type, content, size);
    }
}
