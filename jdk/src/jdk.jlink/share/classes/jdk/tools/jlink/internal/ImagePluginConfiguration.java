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

import java.io.DataOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import jdk.tools.jlink.plugin.ExecutableImage;
import jdk.tools.jlink.builder.ImageBuilder;
import jdk.tools.jlink.Jlink;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.PluginContext;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Plugin.CATEGORY;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.PostProcessorPlugin;
import jdk.tools.jlink.plugin.TransformerPlugin;

/**
 * Plugins configuration.
 */
public final class ImagePluginConfiguration {

    private static final List<Plugin.CATEGORY> CATEGORIES_ORDER = new ArrayList<>();

    static {
        CATEGORIES_ORDER.add(Plugin.CATEGORY.FILTER);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.TRANSFORMER);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.MODULEINFO_TRANSFORMER);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.SORTER);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.COMPRESSOR);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.VERIFIER);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.PROCESSOR);
        CATEGORIES_ORDER.add(Plugin.CATEGORY.PACKAGER);
    }

    private ImagePluginConfiguration() {
    }

    public static ImagePluginStack parseConfiguration(Jlink.PluginsConfiguration plugins)
            throws Exception {
        return parseConfiguration(plugins, null);
    }

    /*
     * Create a stack of plugins from a a configuration.
     *
     */
    public static ImagePluginStack parseConfiguration(Jlink.PluginsConfiguration pluginsConfiguration,
            String bom)
            throws Exception {
        if (pluginsConfiguration == null) {
            return new ImagePluginStack(bom);
        }
        Map<Plugin.CATEGORY, List<Plugin>> plugins = new LinkedHashMap<>();
        for (Plugin.CATEGORY cat : CATEGORIES_ORDER) {
            plugins.put(cat, new ArrayList<>());
        }

        List<String> seen = new ArrayList<>();
        // split into categories and check for plugin with same name.
        for (Plugin plug : pluginsConfiguration.getPlugins()) {
            if (seen.contains(plug.getName())) {
                throw new Exception("Plugin " + plug.getName()
                        + " added more than once to stack ");
            }
            seen.add(plug.getName());
            CATEGORY category = Utils.getCategory(plug);
            if (category == null) {
                throw new PluginException("Invalid category for "
                        + plug.getName());
            }
            List<Plugin> lst = plugins.get(category);
            lst.add(plug);
        }

        List<TransformerPlugin> transformerPlugins = new ArrayList<>();
        List<PostProcessorPlugin> postProcessingPlugins = new ArrayList<>();
        for (Entry<Plugin.CATEGORY, List<Plugin>> entry : plugins.entrySet()) {
            // Sort according to plugin constraints
            List<Plugin> orderedPlugins = PluginOrderingGraph.sort(entry.getValue());
            CATEGORY category = entry.getKey();
            for (Plugin p : orderedPlugins) {
                if (Utils.isPostProcessor(category)) {
                    @SuppressWarnings("unchecked")
                    PostProcessorPlugin pp = (PostProcessorPlugin) p;
                    postProcessingPlugins.add(pp);
                } else {
                    @SuppressWarnings("unchecked")
                    TransformerPlugin trans = (TransformerPlugin) p;
                    transformerPlugins.add(trans);
                }
            }
        }
        Plugin lastSorter = null;
        for (Plugin plugin : transformerPlugins) {
            if (plugin.getName().equals(pluginsConfiguration.getLastSorterPluginName())) {
                lastSorter = plugin;
                break;
            }
        }
        if (pluginsConfiguration.getLastSorterPluginName() != null && lastSorter == null) {
            throw new IOException("Unknown last plugin "
                    + pluginsConfiguration.getLastSorterPluginName());
        }
        ImageBuilder builder = pluginsConfiguration.getImageBuilder();
        if (builder == null) {
            // This should be the case for jimage only creation or post-install.
            builder = new ImageBuilder() {

                @Override
                public DataOutputStream getJImageOutputStream() {
                    throw new PluginException("No directory setup to store files");
                }

                @Override
                public ExecutableImage getExecutableImage() {
                    throw new PluginException("No directory setup to store files");
                }

                @Override
                public void storeFiles(Pool files, String bom) {
                    throw new PluginException("No directory setup to store files");
                }
            };
        }

        PluginContext ctxt = pluginsConfiguration.getPluginContext();
        return new ImagePluginStack(builder, transformerPlugins,
                lastSorter, postProcessingPlugins, ctxt, bom);
    }
}
