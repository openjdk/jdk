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
package jdk.tools.jlink.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.internal.jimage.decompressor.CompressedResourceHeader;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.LinkModule;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.internal.plugins.FileCopierPlugin;

/**
 * Pool of module data.
 */
public class ModulePoolImpl implements ModulePool {

    private class ModuleImpl implements LinkModule {

        final Map<String, ModuleEntry> moduleContent = new LinkedHashMap<>();
        private ModuleDescriptor descriptor;
        final String name;

        private ModuleImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<ModuleEntry> findEntry(String path) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!path.startsWith("/" + name)) {
                path = "/" + name + path;
            }
            return Optional.ofNullable(moduleContent.get(path));
        }

        @Override
        public ModuleDescriptor getDescriptor() {
            if (descriptor == null) {
                String p = "/" + name + "/module-info.class";
                Optional<ModuleEntry> content = findEntry(p);
                if (!content.isPresent()) {
                    throw new PluginException("No module-info for " + name
                            + " module");
                }
                ByteBuffer bb = ByteBuffer.wrap(content.get().getBytes());
                descriptor = ModuleDescriptor.read(bb);
            }
            return descriptor;
        }

        @Override
        public void add(ModuleEntry data) {
            if (isReadOnly()) {
                throw new PluginException("ModulePool is readonly");
            }
            Objects.requireNonNull(data);
            if (!data.getModule().equals(name)) {
                throw new PluginException("Can't add resource " + data.getPath()
                        + " to module " + name);
            }
            ModulePoolImpl.this.add(data);
        }

        @Override
        public Set<String> getAllPackages() {
            Set<String> pkgs = new HashSet<>();
            moduleContent.values().stream().filter(m -> m.getType().
                    equals(ModuleEntry.Type.CLASS_OR_RESOURCE)).forEach(res -> {
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
        public Stream<? extends ModuleEntry> entries() {
            return moduleContent.values().stream();
        }

        @Override
        public int getEntryCount() {
            return moduleContent.values().size();
        }
    }

    private final Map<String, ModuleEntry> resources = new LinkedHashMap<>();
    private final Map<String, ModuleImpl> modules = new LinkedHashMap<>();
    private final ModuleImpl fileCopierModule = new ModuleImpl(FileCopierPlugin.FAKE_MODULE);
    private Map<String, String> releaseProps = new HashMap<>();

    private final ByteOrder order;

    private boolean isReadOnly;
    private final StringTable table;

    public ModulePoolImpl() {
        this(ByteOrder.nativeOrder());
    }

    public ModulePoolImpl(ByteOrder order) {
        this(order, new StringTable() {

            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                return null;
            }
        });
    }

    public ModulePoolImpl(ByteOrder order, StringTable table) {
        this.order = order;
        this.table = table;
    }

    /**
     * Add a ModuleEntry.
     *
     * @param data The ModuleEntry to add.
     */
    @Override
    public void add(ModuleEntry data) {
        if (isReadOnly()) {
            throw new PluginException("ModulePool is readonly");
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
     * @return the module of matching name, if found
     */
    @Override
    public Optional<LinkModule> findModule(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(modules.get(name));
    }

    /**
     * The stream of modules contained in this ModulePool.
     *
     * @return The stream of modules.
     */
    @Override
    public Stream<? extends LinkModule> modules() {
        return modules.values().stream();
    }

    /**
     * Return the number of LinkModule count in this ModulePool.
     *
     * @return the module count.
     */
    @Override
    public int getModuleCount() {
        return modules.size();
    }

    /**
     * Get all ModuleEntry contained in this ModulePool instance.
     *
     * @return The stream of LinkModuleEntries.
     */
    @Override
    public Stream<? extends ModuleEntry> entries() {
        return resources.values().stream();
    }

    /**
     * Return the number of ModuleEntry count in this ModulePool.
     *
     * @return the entry count.
     */
    @Override
    public int getEntryCount() {
        return resources.values().size();
    }

    /**
     * Get the ModuleEntry for the passed path.
     *
     * @param path A data path
     * @return A ModuleEntry instance or null if the data is not found
     */
    @Override
    public Optional<ModuleEntry> findEntry(String path) {
        Objects.requireNonNull(path);
        return Optional.ofNullable(resources.get(path));
    }

    /**
     * Check if the ModulePool contains the given ModuleEntry.
     *
     * @param data The module data to check existence for.
     * @return The module data or null if not found.
     */
    @Override
    public boolean contains(ModuleEntry data) {
        Objects.requireNonNull(data);
        return findEntry(data.getPath()).isPresent();
    }

    /**
     * Check if the ModulePool contains some content at all.
     *
     * @return True, no content, false otherwise.
     */
    @Override
    public boolean isEmpty() {
        return resources.isEmpty();
    }

    /**
     * Visit each ModuleEntry in this ModulePool to transform it and
     * copy the transformed ModuleEntry to the output ModulePool.
     *
     * @param transform The function called for each ModuleEntry found in
     * the ModulePool. The transform function should return a
     * ModuleEntry instance which will be added to the output or it should
     * return null if the passed ModuleEntry is to be ignored for the
     * output.
     *
     * @param output The ModulePool to be filled with Visitor returned
     * ModuleEntry.
     */
    @Override
    public void transformAndCopy(Function<ModuleEntry, ModuleEntry> transform,
            ModulePool output) {
        entries().forEach(resource -> {
            ModuleEntry res = transform.apply(resource);
            if (res != null) {
                output.add(res);
            }
        });
    }

    /**
     * The ByteOrder currently in use when generating the jimage file.
     *
     * @return The ByteOrder.
     */
    @Override
    public ByteOrder getByteOrder() {
        return order;
    }

    @Override
    public Map<String, String> getReleaseProperties() {
        return isReadOnly()? Collections.unmodifiableMap(releaseProps) : releaseProps;
    }

    public StringTable getStringTable() {
        return table;
    }

    /**
     * Make this Resources instance read-only. No resource can be added.
     */
    public void setReadOnly() {
        isReadOnly = true;
    }

    /**
     * Read only state.
     *
     * @return true if readonly false otherwise.
     */
    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * A resource that has been compressed.
     */
    public static final class CompressedModuleData extends ByteArrayModuleEntry {

        final long uncompressed_size;

        private CompressedModuleData(String module, String path,
                byte[] content, long uncompressed_size) {
            super(module, path, ModuleEntry.Type.CLASS_OR_RESOURCE, content);
            this.uncompressed_size = uncompressed_size;
        }

        public long getUncompressedSize() {
            return uncompressed_size;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CompressedModuleData)) {
                return false;
            }
            CompressedModuleData f = (CompressedModuleData) other;
            return f.getPath().equals(getPath());
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    public static CompressedModuleData newCompressedResource(ModuleEntry original,
            ByteBuffer compressed,
            String plugin, String pluginConfig, StringTable strings,
            ByteOrder order) {
        Objects.requireNonNull(original);
        Objects.requireNonNull(compressed);
        Objects.requireNonNull(plugin);

        boolean isTerminal = !(original instanceof CompressedModuleData);
        long uncompressed_size = original.getLength();
        if (original instanceof CompressedModuleData) {
            CompressedModuleData comp = (CompressedModuleData) original;
            uncompressed_size = comp.getUncompressedSize();
        }
        int nameOffset = strings.addString(plugin);
        int configOffset = -1;
        if (pluginConfig != null) {
            configOffset = strings.addString(plugin);
        }
        CompressedResourceHeader rh
                = new CompressedResourceHeader(compressed.limit(), original.getLength(),
                        nameOffset, configOffset, isTerminal);
        // Merge header with content;
        byte[] h = rh.getBytes(order);
        ByteBuffer bb = ByteBuffer.allocate(compressed.limit() + h.length);
        bb.order(order);
        bb.put(h);
        bb.put(compressed);
        byte[] contentWithHeader = bb.array();

        CompressedModuleData compressedResource
                = new CompressedModuleData(original.getModule(), original.getPath(),
                        contentWithHeader, uncompressed_size);
        return compressedResource;
    }

}
