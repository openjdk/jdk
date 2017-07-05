/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jdk.tools.jlink.internal.ImageFileCreator;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin;

/**
 * Pool of module data.
 *
 */
public abstract class Pool {

    /**
     * Interface to visit the content of a Pool.
     */
    public interface Visitor {

        /**
         * Called for each visited ModuleData.
         *
         * @param content A ModuleData
         * @return A ModuleData instance or null if the passed ModuleData is to
         * be removed from the image.
         * @throws PluginException
         */
        public ModuleData visit(ModuleData content);
    }

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
    public static enum ModuleDataType {

        CLASS_OR_RESOURCE,
        CONFIG,
        NATIVE_CMD,
        NATIVE_LIB,
        OTHER;
    }

    /**
     * A module in the pool.
     */
    public interface Module {

        /**
         * The module name.
         *
         * @return The name.
         */
        public String getName();

        /**
         * Retrieves a ModuleData from a path (e.g:
         * /mymodule/com.foo.bar/MyClass.class)
         *
         * @param path The piece of data path.
         * @return A ModuleData or null if the path doesn't identify a
         * ModuleData.
         */
        public ModuleData get(String path);

        /**
         * The module descriptor of this module.
         *
         * @return The module descriptor.
         */
        public ModuleDescriptor getDescriptor();

        /**
         * Add a ModuleData to this module.
         *
         * @param data The ModuleData to add.
         */
        public void add(ModuleData data);

        /**
         * Retrieves all the packages located in this module.
         *
         * @return The set of packages.
         */
        public Set<String> getAllPackages();

        /**
         * Retrieves the collection of ModuleData.
         *
         * @return The ModuleData collection.
         */
        public Collection<ModuleData> getContent();

    }

    private class ModuleImpl implements Module {

        private final Map<String, ModuleData> moduleContent = new LinkedHashMap<>();
        private ModuleDescriptor descriptor;
        private final String name;

        private ModuleImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ModuleData get(String path) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.startsWith("/" + name)) {
                path = "/" + name + path;
            }
            return moduleContent.get(path);
        }

        @Override
        public ModuleDescriptor getDescriptor() {
            if (descriptor == null) {
                String p = "/" + name + "/module-info.class";
                ModuleData content = moduleContent.get(p);
                if (content == null) {
                    throw new PluginException("No module-info for " + name
                            + " module");
                }
                ByteBuffer bb = ByteBuffer.wrap(content.getBytes());
                descriptor = ModuleDescriptor.read(bb);
            }
            return descriptor;
        }

        @Override
        public void add(ModuleData data) {
            if (isReadOnly()) {
                throw new PluginException("pool is readonly");
            }
            Objects.requireNonNull(data);
            if (!data.getModule().equals(name)) {
                throw new PluginException("Can't add resource " + data.getPath()
                        + " to module " + name);
            }
            Pool.this.add(data);
        }

        @Override
        public Set<String> getAllPackages() {
            Set<String> pkgs = new HashSet<>();
            moduleContent.values().stream().filter(m -> m.getType().
                    equals(ModuleDataType.CLASS_OR_RESOURCE)).forEach((res) -> {
                // Module metadata only contains packages with .class files
                if (ImageFileCreator.isClassPackage(res.getPath())) {
                    String[] split = ImageFileCreator.splitPath(res.getPath());
                    String pkg = split[1];
                    if (pkg != null && !pkg.isEmpty()) {
                        pkgs.add(pkg);
                    }
                }
            });
            return pkgs;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public Collection<ModuleData> getContent() {
            return Collections.unmodifiableCollection(moduleContent.values());
        }
    }

    /**
     * A ModuleData is the elementary unit of data inside an image. It is
     * generally a file. e.g.: a java class file, a resource file, a shared
     * library, ...
     * <br>
     * A ModuleData is identified by a path of the form:
     * <ul>
     * <li>For jimage content: /{module name}/{package1}/.../{packageN}/{file
     * name}</li>
     * <li>For other files (shared lib, launchers, config, ...):/{module name}/
     * {@literal bin|conf|native}/{dir1}>/.../{dirN}/{file name}</li>
     * </ul>
     */
    public static class ModuleData {

        private final ModuleDataType type;
        private final String path;
        private final String module;
        private final long length;
        private final InputStream stream;
        private byte[] buffer;

        /**
         * Create a new ModuleData.
         *
         * @param module The module name.
         * @param path The data path identifier.
         * @param type The data type.
         * @param stream The data content stream.
         * @param length The stream length.
         */
        public ModuleData(String module, String path, ModuleDataType type,
                InputStream stream, long length) {
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
         * The ModuleData module name.
         *
         * @return The module name.
         */
        public final String getModule() {
            return module;
        }

        /**
         * The ModuleData path.
         *
         * @return The module path.
         */
        public final String getPath() {
            return path;
        }

        /**
         * The ModuleData type.
         *
         * @return The data type.
         */
        public final ModuleDataType getType() {
            return type;
        }

        /**
         * The ModuleData content as an array of byte.
         *
         * @return An Array of bytes.
         */
        public byte[] getBytes() {
            if (buffer == null) {
                try {
                    buffer = stream.readAllBytes();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            return buffer;
        }

        /**
         * The ModuleData content length.
         *
         * @return The length.
         */
        public long getLength() {
            return length;
        }

        /**
         * The ModuleData stream.
         *
         * @return The module data stream.
         */
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
            if (!(other instanceof ModuleData)) {
                return false;
            }
            ModuleData f = (ModuleData) other;
            return f.path.equals(path);
        }

        @Override
        public String toString() {
            return getPath();
        }
    }

    private final Map<String, ModuleData> resources = new LinkedHashMap<>();
    private final Map<String, ModuleImpl> modules = new LinkedHashMap<>();
    private final ModuleImpl fileCopierModule = new ModuleImpl(FileCopierPlugin.FAKE_MODULE);

    private final ByteOrder order;

    protected Pool() {
        this(ByteOrder.nativeOrder());
    }

    protected Pool(ByteOrder order) {
        Objects.requireNonNull(order);
        this.order = order;
    }

    /**
     * Read only state. No data can be added to a ReadOnly Pool.
     *
     * @return true if readonly false otherwise.
     */
    public abstract boolean isReadOnly();

    /**
     * Add a ModuleData.
     *
     * @param data The ModuleData to add.
     */
    public void add(ModuleData data) {
        if (isReadOnly()) {
            throw new PluginException("pool is readonly");
        }
        Objects.requireNonNull(data);
        if (resources.get(data.getPath()) != null) {
            throw new PluginException("Resource " + data.getPath()
                    + " already present");
        }
        String modulename = data.getModule();
        ModuleImpl m = modules.get(modulename);
        // ## TODO: FileCopierPlugin should not add content to a module
        // FAKE_MODULE is not really a module to be added in the image
        if (FileCopierPlugin.FAKE_MODULE.equals(modulename)) {
            m = fileCopierModule;
        }
        if (m == null) {
            m = new ModuleImpl(modulename);
            modules.put(modulename, m);
        }
        resources.put(data.getPath(), data);
        m.moduleContent.put(data.getPath(), data);
    }

    /**
     * Retrieves the module for the provided name.
     *
     * @param name The module name
     * @return the module or null if the module doesn't exist.
     */
    public Module getModule(String name) {
        Objects.requireNonNull(name);
        return modules.get(name);
    }

    /**
     * The collection of modules contained in this pool.
     *
     * @return The collection of modules.
     */
    public Collection<Module> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * Get all ModuleData contained in this pool instance.
     *
     * @return The collection of resources;
     */
    public Collection<ModuleData> getContent() {
        return Collections.unmodifiableCollection(resources.values());
    }

    /**
     * Get the ModuleData for the passed path.
     *
     * @param path A data path
     * @return A ModuleData instance or null if the data is not found
     */
    public ModuleData get(String path) {
        Objects.requireNonNull(path);
        return resources.get(path);
    }

    /**
     * Check if the pool contains this data.
     *
     * @param data The module data to check existence for.
     * @return The module data or null if not found.
     */
    public boolean contains(ModuleData data) {
        Objects.requireNonNull(data);
        return get(data.getPath()) != null;
    }

    /**
     * Check if the Pool contains some content.
     *
     * @return True, no content, false otherwise.
     */
    public boolean isEmpty() {
        return resources.isEmpty();
    }

    /**
     * Visit the pool.
     *
     * @param visitor The Visitor called for each ModuleData found in the pool.
     * @param output The pool to be filled with Visitor returned ModuleData.
     */
    public void visit(Visitor visitor, Pool output) {
        for (ModuleData resource : getContent()) {
            ModuleData res = visitor.visit(resource);
            if (res != null) {
                output.add(res);
            }
        }
    }

    /**
     * The ByteOrder currently in use when generating the jimage file.
     *
     * @return The ByteOrder.
     */
    public ByteOrder getByteOrder() {
        return order;
    }

    /**
     * Create a ModuleData located inside a jimage file. Such ModuleData has a
     * ModuleDataType being equals to CLASS_OR_RESOURCE.
     *
     * @param path The complete resource path (contains the module radical).
     * @param content The resource content.
     * @param size The content size.
     * @return A new ModuleData.
     */
    public static ModuleData newResource(String path, InputStream content, long size) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        String[] split = ImageFileCreator.splitPath(path);
        String module = split[0];
        return new ModuleData(module, path, ModuleDataType.CLASS_OR_RESOURCE, content, size);
    }

    /**
     * Create a ModuleData for a file that will be located inside a jimage file.
     *
     * @param path The resource path.
     * @param content The resource content.
     * @return A new ModuleData.
     */
    public static ModuleData newResource(String path, byte[] content) {
        return newResource(path, new ByteArrayInputStream(content),
                content.length);
    }

    /**
     * Create a ModuleData for a file that will be located outside a jimage
     * file.
     *
     * @param module The module in which this files is located.
     * @param path The file path locator (doesn't contain the module name).
     * @param type The ModuleData type.
     * @param content The file content.
     * @param size The content size.
     * @return A new ModuleData.
     */
    public static ModuleData newImageFile(String module, String path, ModuleDataType type,
            InputStream content, long size) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);
        return new ModuleData(module, path, type, content, size);
    }

}
