/*
 * Copyright (c) 2024, Red Hat, Inc.
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

package jdk.tools.jlink.internal.plugins;

import static jdk.tools.jlink.internal.JlinkTask.DIFF_PATTERN;
import static jdk.tools.jlink.internal.JlinkTask.RESPATH_PATTERN;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.JRTArchive;
import jdk.tools.jlink.internal.Platform;
import jdk.tools.jlink.internal.ResourceDiff;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolModule;


/**
 * Plugin to produce a runtime-linkable JDK image. It does the following:
 *
 * <ul>
 * <li>
 * It tracks resources per module and saves the list in 'fs_$M_files' in the
 * jdk.jlink module.
 * </li>
 * <li>
 * It adds a serialized resource diff to the jdk.jlink module so as to be able
 * to reconstruct the packaged modules equivalent view when performing runtime
 * links.
 * </li>
 * </ul>
 */
public final class CreateLinkableRuntimePlugin extends AbstractPlugin {
    private static final String PLUGIN_NAME = "create-linkable-runtime";
    private static final String JLINK_MOD_NAME = "jdk.jlink";
    // This resource is being used in JLinkTask which passes its contents to
    // JRTArchive for further processing.
    private static final String RESPATH = "/" + JLINK_MOD_NAME + "/" + RESPATH_PATTERN;
    private static final String DIFF_PATH = "/" + JLINK_MOD_NAME + "/" + DIFF_PATTERN;
    private static final byte[] EMPTY_RESOURCE_BYTES = new byte[] {};

    private final Map<String, List<String>> nonClassResEntries;
    private String diffFile;

    public CreateLinkableRuntimePlugin() {
        super(PLUGIN_NAME);
        this.nonClassResEntries = new ConcurrentHashMap<>();
    }

    @Override
    public void configure(Map<String, String> config) {
        String v = config.get(PLUGIN_NAME);
        if (v == null)
            throw new AssertionError();
        diffFile = v;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        // Only add resources if we have the jdk.jlink module part of the
        // link.
        Optional<ResourcePoolModule> jdkJlink = in.moduleView().findModule(JLINK_MOD_NAME);
        if (jdkJlink.isPresent()) {
            Platform targetPlatform = getTargetPlatform(in);
            in.transformAndCopy(e -> recordAndFilterEntry(e, targetPlatform), out);
            addModuleResourceEntries(in, out);
            addResourceDiffFiles(in, out);
        } else {
            throw new IllegalStateException("jdk.jlink module not in list of modules for target image");
        }
        return out.build();
    }

    private void addResourceDiffFiles(ResourcePool in, ResourcePoolBuilder out) {
        Map<String, List<ResourceDiff>> diffs = readResourceDiffs();
        // Create a resource file with the delta to the packaged-modules view
        in.moduleView().modules().forEach(m -> {
            String modName = m.descriptor().name();
            String resFile = String.format(DIFF_PATH, modName);
            List<ResourceDiff> perModDiff = diffs.get(modName);
            // Not every module will have a diff
            if (perModDiff == null) {
                perModDiff = Collections.emptyList();
            }
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                ResourceDiff.write(perModDiff, bout);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write per module diff");
            }
            out.add(ResourcePoolEntry.create(resFile, bout.toByteArray()));
        });
    }

    private Map<String, List<ResourceDiff>> readResourceDiffs() {
        List<ResourceDiff> resDiffs = null;
        try (FileInputStream fin = new FileInputStream(new File(diffFile))) {
            resDiffs = ResourceDiff.read(fin);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource diff file: " + diffFile);
        }
        Map<String, List<ResourceDiff>> modToDiff = new HashMap<>();
        resDiffs.forEach(d -> {
            int secondSlash = d.getName().indexOf("/", 1);
            if (secondSlash == -1) {
                throw new AssertionError("Module name not present");
            }
            String module = d.getName().substring(1, secondSlash);
            List<ResourceDiff> perModDiff = modToDiff.computeIfAbsent(module, a -> new ArrayList<>());
            perModDiff.add(d);
        });
        return modToDiff;
    }

    private Platform getTargetPlatform(ResourcePool in) {
        String platform = in.moduleView().findModule("java.base")
                .map(ResourcePoolModule::targetPlatform)
                .orElseThrow(() -> new AssertionError("java.base not found"));
        return Platform.parsePlatform(platform);
    }

    private void addModuleResourceEntries(ResourcePool in, ResourcePoolBuilder out) {
        Set<String> inputModules = in.moduleView().modules()
                                                  .map(rm -> rm.name())
                                                  .collect(Collectors.toSet());
        inputModules.stream().sorted().forEach(module -> {
            String mResource = String.format(RESPATH, module);
            List<String> mResources = nonClassResEntries.get(module);
            if (mResources == null) {
                // We create empty resource files for modules in the resource
                // pool view, but which don't themselves contain native resources
                // or config files.
                out.add(ResourcePoolEntry.create(mResource, EMPTY_RESOURCE_BYTES));
            } else {
                String mResContent = mResources.stream().sorted()
                                               .collect(Collectors.joining("\n"));
                out.add(ResourcePoolEntry.create(mResource, mResContent.getBytes(StandardCharsets.UTF_8)));
            }
        });
    }

    private ResourcePoolEntry recordAndFilterEntry(ResourcePoolEntry entry, Platform platform) {
        // Note that the jmod_resources file is a resource file, so we cannot
        // add ourselves due to this condition. However, we want to not add
        // an old version of the resource file again.
        if (entry.type() != ResourcePoolEntry.Type.CLASS_OR_RESOURCE) {
            if (entry.type() == ResourcePoolEntry.Type.TOP) {
                return entry; // Handled by ReleaseInfoPlugin, nothing to do
            }
            List<String> moduleResources = nonClassResEntries.computeIfAbsent(entry.moduleName(), a -> new ArrayList<>());

            JRTArchive.ResourceFileEntry rfEntry = JRTArchive.ResourceFileEntry.toResourceFileEntry(entry, platform);
            moduleResources.add(rfEntry.encodeToString());
        }
        return entry;
    }

    @Override
    public Set<State> getState() {
        return EnumSet.of(State.FUNCTIONAL);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public Category getType() {
        // Ensure we run in a later stage as we need to generate
        // SHA-512 sums for non-(class/resource) files. The fs_$module_files
        // files can be considered meta-info describing the universe we
        // draft from (together with the jimage and respective diff_$module files).
        return Category.METAINFO_ADDER;
    }
}
