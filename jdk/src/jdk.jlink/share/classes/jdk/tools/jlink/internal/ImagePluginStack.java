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
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.jimage.decompressor.Decompressor;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.PluginContext;
import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.builder.ImageBuilder;
import jdk.tools.jlink.plugin.TransformerPlugin;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.PostProcessorPlugin;

/**
 * Plugins Stack. Plugins entry point to apply transformations onto resources
 * and files.
 */
public final class ImagePluginStack {

    public interface ImageProvider {

        ExecutableImage retrieve(ImagePluginStack stack) throws IOException;
    }

    public static final class OrderedResourcePool extends PoolImpl {

        private final List<ModuleData> orderedList = new ArrayList<>();

        public OrderedResourcePool(ByteOrder order, StringTable table) {
            super(order, table);
        }

        /**
         * Add a resource.
         *
         * @param resource The Resource to add.
         */
        @Override
        public void add(ModuleData resource) {
            super.add(resource);
            orderedList.add(resource);
        }

        List<ModuleData> getOrderedList() {
            return Collections.unmodifiableList(orderedList);
        }
    }

    private final static class CheckOrderResourcePool extends PoolImpl {

        private final List<ModuleData> orderedList;
        private int currentIndex;

        public CheckOrderResourcePool(ByteOrder order, List<ModuleData> orderedList, StringTable table) {
            super(order, table);
            this.orderedList = orderedList;
        }

        /**
         * Add a resource.
         *
         * @param resource The Resource to add.
         */
        @Override
        public void add(ModuleData resource) {
            ModuleData ordered = orderedList.get(currentIndex);
            if (!resource.equals(ordered)) {
                throw new PluginException("Resource " + resource.getPath() + " not in the right order");
            }
            super.add(resource);
            currentIndex += 1;
        }
    }

    private static final class PreVisitStrings implements StringTable {

        private int currentid = 0;
        private final Map<String, Integer> stringsUsage = new HashMap<>();

        private final Map<String, Integer> stringsMap = new HashMap<>();
        private final Map<Integer, String> reverseMap = new HashMap<>();

        @Override
        public int addString(String str) {
            Objects.requireNonNull(str);
            Integer count = stringsUsage.get(str);
            if (count == null) {
                count = 0;
            }
            count += 1;
            stringsUsage.put(str, count);
            Integer id = stringsMap.get(str);
            if (id == null) {
                id = currentid;
                stringsMap.put(str, id);
                currentid += 1;
                reverseMap.put(id, str);
            }

            return id;
        }

        private List<String> getSortedStrings() {
            Stream<java.util.Map.Entry<String, Integer>> stream
                    = stringsUsage.entrySet().stream();
            // Remove strings that have a single occurence
            List<String> result = stream.sorted(Comparator.comparing(e -> e.getValue(),
                    Comparator.reverseOrder())).filter((e) -> {
                        return e.getValue() > 1;
                    }).map(java.util.Map.Entry::getKey).
                    collect(Collectors.toList());
            return result;
        }

        @Override
        public String getString(int id) {
            return reverseMap.get(id);
        }
    }

    private final Plugin lastSorter;
    private final List<TransformerPlugin> contentPlugins = new ArrayList<>();
    private final List<PostProcessorPlugin> postProcessingPlugins = new ArrayList<>();
    private final List<ResourcePrevisitor> resourcePrevisitors = new ArrayList<>();

    private final ImageBuilder imageBuilder;
    private final Properties release;
    private final String bom;

    public ImagePluginStack(String bom) {
        this(null, Collections.emptyList(), null,
                Collections.emptyList(), null, bom);
    }

    public ImagePluginStack(ImageBuilder imageBuilder,
            List<TransformerPlugin> contentPlugins,
            Plugin lastSorter,
            List<PostProcessorPlugin> postprocessingPlugins,
            String bom) {
        this(imageBuilder, contentPlugins, lastSorter,
            postprocessingPlugins, null, bom);
    }

    public ImagePluginStack(ImageBuilder imageBuilder,
            List<TransformerPlugin> contentPlugins,
            Plugin lastSorter,
            List<PostProcessorPlugin> postprocessingPlugins,
            PluginContext ctxt,
            String bom) {
        Objects.requireNonNull(contentPlugins);
        this.lastSorter = lastSorter;
        for (TransformerPlugin p : contentPlugins) {
            Objects.requireNonNull(p);
            if (p instanceof ResourcePrevisitor) {
                resourcePrevisitors.add((ResourcePrevisitor) p);
            }
            this.contentPlugins.add(p);
        }
        for (PostProcessorPlugin p : postprocessingPlugins) {
            Objects.requireNonNull(p);
            this.postProcessingPlugins.add(p);
        }
        this.imageBuilder = imageBuilder;
        this.release = ctxt != null? ctxt.getReleaseProperties() : new Properties();
        this.bom = bom;
    }

    public void operate(ImageProvider provider) throws Exception {
        ExecutableImage img = provider.retrieve(this);
        List<String> arguments = new ArrayList<>();
        for (PostProcessorPlugin plugin : postProcessingPlugins) {
            List<String> lst = plugin.process(img);
            if (lst != null) {
                arguments.addAll(lst);
            }
        }
        img.storeLaunchArgs(arguments);
    }

    public DataOutputStream getJImageFileOutputStream() throws IOException {
        return imageBuilder.getJImageOutputStream();
    }

    public ImageBuilder getImageBuilder() {
        return imageBuilder;
    }

    /**
     * Resource Plugins stack entry point. All resources are going through all
     * the plugins.
     *
     * @param resources The set of resources to visit
     * @return The result of the visit.
     * @throws IOException
     */
    public PoolImpl visitResources(PoolImpl resources)
            throws Exception {
        Objects.requireNonNull(resources);
        resources.setReadOnly();
        if (resources.isEmpty()) {
            return new PoolImpl(resources.getByteOrder(),
                    resources.getStringTable());
        }
        PreVisitStrings previsit = new PreVisitStrings();
        for (ResourcePrevisitor p : resourcePrevisitors) {
            p.previsit(resources, previsit);
        }

        // Store the strings resulting from the previsit.
        List<String> sorted = previsit.getSortedStrings();
        for (String s : sorted) {
            resources.getStringTable().addString(s);
        }

        PoolImpl current = resources;
        List<Pool.ModuleData> frozenOrder = null;
        for (TransformerPlugin p : contentPlugins) {
            current.setReadOnly();
            PoolImpl output = null;
            if (p == lastSorter) {
                if (frozenOrder != null) {
                    throw new Exception("Order of resources is already frozen. Plugin "
                            + p.getName() + " is badly located");
                }
                // Create a special Resource pool to compute the indexes.
                output = new OrderedResourcePool(current.getByteOrder(),
                        resources.getStringTable());
            } else {// If we have an order, inject it
                if (frozenOrder != null) {
                    output = new CheckOrderResourcePool(current.getByteOrder(),
                            frozenOrder, resources.getStringTable());
                } else {
                    output = new PoolImpl(current.getByteOrder(),
                            resources.getStringTable());
                }
            }
            p.visit(current, output);
            if (output.isEmpty()) {
                throw new Exception("Invalid resource pool for plugin " + p);
            }
            if (output instanceof OrderedResourcePool) {
                frozenOrder = ((OrderedResourcePool) output).getOrderedList();
            }

            current = output;
        }
        current.setReadOnly();
        return current;
    }

    /**
     * This pool wrap the original pool and automatically uncompress moduledata
     * if needed.
     */
    private class LastPool extends Pool {
        private class LastModule implements Module {

            private final Module module;

            LastModule(Module module) {
                this.module = module;
            }

            @Override
            public String getName() {
                return module.getName();
            }

            @Override
            public ModuleData get(String path) {
                ModuleData d = module.get(path);
                return getUncompressed(d);
            }

            @Override
            public ModuleDescriptor getDescriptor() {
                return module.getDescriptor();
            }

            @Override
            public void add(ModuleData data) {
                throw new PluginException("pool is readonly");
            }

            @Override
            public Set<String> getAllPackages() {
                return module.getAllPackages();
            }

            @Override
            public String toString() {
                return getName();
            }

            @Override
            public Collection<ModuleData> getContent() {
                List<ModuleData> lst = new ArrayList<>();
                for(ModuleData md : module.getContent()) {
                    lst.add(getUncompressed(md));
                }
                return lst;
            }
        }
        private final PoolImpl pool;
        Decompressor decompressor = new Decompressor();
        Collection<ModuleData> content;

        LastPool(PoolImpl pool) {
            this.pool = pool;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public void add(ModuleData resource) {
            throw new PluginException("pool is readonly");
        }

        /**
         * Retrieves the module of the provided name.
         *
         * @param name The module name
         * @return the module or null if the module doesn't exist.
         */
        @Override
        public Module getModule(String name) {
            Module module = pool.getModule(name);
            if (module != null) {
                module = new LastModule(module);
            }
            return module;
        }

        /**
         * The collection of modules contained in this pool.
         *
         * @return The collection of modules.
         */
        @Override
        public Collection<Module> getModules() {
            List<Module> modules = new ArrayList<>();
            for (Module m : pool.getModules()) {
                modules.add(new LastModule(m));
            }
            return modules;
        }

        /**
         * Get all resources contained in this pool instance.
         *
         * @return The collection of resources;
         */
        @Override
        public Collection<ModuleData> getContent() {
            if (content == null) {
                content = new ArrayList<>();
                for (ModuleData md : pool.getContent()) {
                    content.add(getUncompressed(md));
                }
            }
            return content;
        }

        /**
         * Get the resource for the passed path.
         *
         * @param path A resource path
         * @return A Resource instance or null if the resource is not found
         */
        @Override
        public ModuleData get(String path) {
            Objects.requireNonNull(path);
            Pool.ModuleData res = pool.get(path);
            return getUncompressed(res);
        }

        @Override
        public boolean contains(ModuleData res) {
            return pool.contains(res);
        }

        @Override
        public boolean isEmpty() {
            return pool.isEmpty();
        }

        @Override
        public void visit(Visitor visitor, Pool output) {
            pool.visit(visitor, output);
        }

        @Override
        public ByteOrder getByteOrder() {
            return pool.getByteOrder();
        }

        private ModuleData getUncompressed(ModuleData res) {
            if (res != null) {
                if (res instanceof PoolImpl.CompressedModuleData) {
                    try {
                        byte[] bytes = decompressor.decompressResource(getByteOrder(),
                                (int offset) -> pool.getStringTable().getString(offset),
                                res.getBytes());
                        res = Pool.newResource(res.getPath(),
                                new ByteArrayInputStream(bytes),
                                bytes.length);
                    } catch (IOException ex) {
                        throw new PluginException(ex);
                    }
                }
            }
            return res;
        }
    }

    /**
     * Make the imageBuilder to store files.
     *
     * @param original
     * @param transformed
     * @param writer
     * @throws java.lang.Exception
     */
    public void storeFiles(PoolImpl original, PoolImpl transformed,
            BasicImageWriter writer)
            throws Exception {
        Objects.requireNonNull(original);
        try {
            // fill release information available from transformed "java.base" module!
            ModuleDescriptor desc = transformed.getModule("java.base").getDescriptor();
            desc.osName().ifPresent(s -> release.put("OS_NAME", s));
            desc.osVersion().ifPresent(s -> release.put("OS_VERSION", s));
            desc.osArch().ifPresent(s -> release.put("OS_ARCH", s));
        } catch (Exception ignored) {
        }

        imageBuilder.storeFiles(new LastPool(transformed), bom, release);
    }

    public ExecutableImage getExecutableImage() throws IOException {
        return imageBuilder.getExecutableImage();
    }
}
