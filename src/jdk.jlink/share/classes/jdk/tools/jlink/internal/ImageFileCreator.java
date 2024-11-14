/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.tools.jlink.internal.LinkableRuntimeImage.DIFF_PATTERN;
import static jdk.tools.jlink.internal.LinkableRuntimeImage.RESPATH_PATTERN;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.tools.jlink.internal.Archive.Entry;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;
import jdk.tools.jlink.internal.JRTArchive.ResourceFileEntry;
import jdk.tools.jlink.internal.ResourcePoolManager.CompressedModuleData;
import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator;
import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator.ImageResource;
import jdk.tools.jlink.internal.runtimelink.ResourceDiff;
import jdk.tools.jlink.internal.runtimelink.ResourcePoolReader;
import jdk.tools.jlink.internal.runtimelink.RuntimeImageLinkException;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolModule;

/**
 * An image (native endian.)
 * <pre>{@code
 * {
 *   u4 magic;
 *   u2 major_version;
 *   u2 minor_version;
 *   u4 resource_count;
 *   u4 table_length;
 *   u4 location_attributes_size;
 *   u4 strings_size;
 *   u4 redirect[table_length];
 *   u4 offsets[table_length];
 *   u1 location_attributes[location_attributes_size];
 *   u1 strings[strings_size];
 *   u1 content[if !EOF];
 * }
 * }</pre>
 */
public final class ImageFileCreator {
    private static final byte[] EMPTY_RESOURCE_BYTES = new byte[] {};

    private static final String JLINK_MOD_NAME = "jdk.jlink";
    private static final String RESPATH = "/" + JLINK_MOD_NAME + "/" + RESPATH_PATTERN;
    private static final String DIFF_PATH = "/" + JLINK_MOD_NAME + "/" + DIFF_PATTERN;
    private final Map<String, List<Entry>> entriesForModule = new HashMap<>();
    private final ImagePluginStack plugins;
    private final boolean generateRuntimeImage;
    private final TaskHelper helper;

    private ImageFileCreator(ImagePluginStack plugins,
                             boolean generateRuntimeImage,
                             TaskHelper taskHelper) {
        this.plugins = Objects.requireNonNull(plugins);
        this.generateRuntimeImage = generateRuntimeImage;
        this.helper = taskHelper;
    }

    /**
     * Create an executable image based on a set of input archives and a given
     * plugin stack for a given byte order. It optionally generates a runtime
     * that can be used for linking from the run-time image if
     * {@code generateRuntimeImage} is set to {@code true}.
     *
     * @param archives The set of input archives
     * @param byteOrder The desired byte order of the result
     * @param plugins The plugin stack to apply to the input
     * @param generateRuntimeImage if a runtime suitable for linking from the
     *        run-time image should get created.
     * @return The executable image.
     * @throws IOException
     */
    public static ExecutableImage create(Set<Archive> archives,
            ByteOrder byteOrder,
            ImagePluginStack plugins,
            boolean generateRuntimeImage,
            TaskHelper taskHelper)
            throws IOException
    {
        ImageFileCreator image = new ImageFileCreator(plugins,
                                                      generateRuntimeImage,
                                                      taskHelper);
        try {
            image.readAllEntries(archives);
            // write to modular image
            image.writeImage(archives, byteOrder);
        } catch (RuntimeImageLinkException e) {
            // readAllEntries() might throw this exception.
            // Propagate as IOException with appropriate message for
            // jlink runs from the run-time image. This handles better
            // error messages for the case of modified files in the run-time
            // image.
            throw image.newIOException(e);
        } finally {
            // Close all archives
            for (Archive a : archives) {
                a.close();
            }
        }

        return plugins.getExecutableImage();
    }

    private void readAllEntries(Set<Archive> archives) {
        archives.forEach((archive) -> {
            Map<Boolean, List<Entry>> es;
            try (Stream<Entry> entries = archive.entries()) {
                es = entries.collect(Collectors.partitioningBy(n -> n.type()
                        == EntryType.CLASS_OR_RESOURCE));
            }
            String mn = archive.moduleName();
            List<Entry> all = new ArrayList<>();
            all.addAll(es.get(false));
            all.addAll(es.get(true));
            entriesForModule.put(mn, all);
        });
    }

    public static void recreateJimage(Path jimageFile,
            Set<Archive> archives,
            ImagePluginStack pluginSupport,
            boolean generateRuntimeImage)
            throws IOException {
        try {
            Map<String, List<Entry>> entriesForModule
                    = archives.stream().collect(Collectors.toMap(
                                    Archive::moduleName,
                                    a -> {
                                        try (Stream<Entry> entries = a.entries()) {
                                            return entries.toList();
                                        }
                                    }));
            ByteOrder order = ByteOrder.nativeOrder();
            BasicImageWriter writer = new BasicImageWriter(order);
            ResourcePoolManager pool = createPoolManager(archives, entriesForModule, order, writer);
            try (OutputStream fos = Files.newOutputStream(jimageFile);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    DataOutputStream out = new DataOutputStream(bos)) {
                generateJImage(pool, writer, pluginSupport, out, generateRuntimeImage);
            }
        } finally {
            //Close all archives
            for (Archive a : archives) {
                a.close();
            }
        }
    }

    private void writeImage(Set<Archive> archives,
            ByteOrder byteOrder)
            throws IOException {
        BasicImageWriter writer = new BasicImageWriter(byteOrder);
        ResourcePoolManager allContent = createPoolManager(archives,
                entriesForModule, byteOrder, writer);
        ResourcePool result = null;
        try (DataOutputStream out = plugins.getJImageFileOutputStream()) {
            result = generateJImage(allContent, writer, plugins, out, generateRuntimeImage);
        } catch (RuntimeImageLinkException e) {
            // Propagate as IOException with appropriate message for
            // jlink runs from the run-time image. This handles better
            // error messages for the case of --patch-module.
            throw newIOException(e);
        }

        //Handle files.
        try {
            plugins.storeFiles(allContent.resourcePool(), result, writer);
        } catch (Exception ex) {
            if (JlinkTask.DEBUG) {
                ex.printStackTrace();
            }
            throw new IOException(ex);
        }
    }

    private IOException newIOException(RuntimeImageLinkException e) throws IOException {
        if (JlinkTask.DEBUG) {
            e.printStackTrace();
        }
        String message = switch (e.getReason()) {
            case PATCH_MODULE -> helper.getMessage("err.runtime.link.patched.module", e.getFile());
            case MODIFIED_FILE -> helper.getMessage("err.runtime.link.modified.file", e.getFile());
            default -> throw new AssertionError("Unexpected value: " + e.getReason());
        };
        throw new IOException(message);
    }

    /**
     * Create a jimage based on content of the given ResourcePoolManager,
     * optionally creating a runtime that can be used for linking from the
     * run-time image
     *
     * @param allContent The content that needs to get added to the resulting
     *                   lib/modules (jimage) file.
     * @param writer The writer for the jimage file.
     * @param pluginSupport The stack of all plugins to apply.
     * @param out The output stream to write the jimage to.
     * @param generateRuntimeImage if a runtime suitable for linking from the
     *        run-time image should get created.
     * @return A pool of the actual result resources.
     * @throws IOException
     */
    private static ResourcePool generateJImage(ResourcePoolManager allContent,
            BasicImageWriter writer,
            ImagePluginStack pluginSupport,
            DataOutputStream out,
            boolean generateRuntimeImage
    ) throws IOException {
        ResourcePool resultResources;
        try {
            resultResources = pluginSupport.visitResources(allContent);
            if (generateRuntimeImage) {
                // Keep track of non-modules resources for linking from a run-time image
                resultResources = addNonClassResourcesTrackFiles(resultResources,
                                                                 writer);
                // Generate the diff between the input resources from packaged
                // modules in 'allContent' to the plugin- or otherwise
                // generated-content in 'resultResources'
                resultResources = addResourceDiffFiles(allContent.resourcePool(),
                                                       resultResources,
                                                       writer);
            }
        } catch (PluginException pe) {
            if (JlinkTask.DEBUG) {
                pe.printStackTrace();
            }
            throw pe;
        } catch (Exception ex) {
            if (JlinkTask.DEBUG) {
                ex.printStackTrace();
            }
            throw new IOException(ex);
        }
        Set<String> duplicates = new HashSet<>();
        long[] offset = new long[1];

        List<ResourcePoolEntry> content = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        // the order of traversing the resources and the order of
        // the module content being written must be the same
        resultResources.entries().forEach(res -> {
            if (res.type().equals(ResourcePoolEntry.Type.CLASS_OR_RESOURCE)) {
                String path = res.path();
                content.add(res);
                long uncompressedSize = res.contentLength();
                long compressedSize = 0;
                if (res instanceof CompressedModuleData) {
                    CompressedModuleData comp
                            = (CompressedModuleData) res;
                    compressedSize = res.contentLength();
                    uncompressedSize = comp.getUncompressedSize();
                }
                long onFileSize = res.contentLength();

                if (duplicates.contains(path)) {
                    System.err.format("duplicate resource \"%s\", skipping%n",
                            path);
                    // TODO Need to hang bytes on resource and write
                    // from resource not zip.
                    // Skipping resource throws off writing from zip.
                    offset[0] += onFileSize;
                    return;
                }
                duplicates.add(path);
                writer.addLocation(path, offset[0], compressedSize, uncompressedSize);
                paths.add(path);
                offset[0] += onFileSize;
            }
        });

        ImageResourcesTree tree = new ImageResourcesTree(offset[0], writer, paths);

        // write header and indices
        byte[] bytes = writer.getBytes();
        out.write(bytes, 0, bytes.length);

        // write module content
        content.forEach((res) -> {
            res.write(out);
        });

        tree.addContent(out);

        out.close();

        return resultResources;
    }

    /**
     * Support for creating a runtime suitable for linking from the run-time
     * image.
     *
     * Generates differences between the packaged modules "view" in
     * {@code jmodContent} to the optimized image in {@code resultContent} and
     * adds the result to the returned resource pool.
     *
     * @param jmodContent The resource pool view of packaged modules
     * @param resultContent The optimized result generated from the jmodContent
     *                      input by applying the plugin stack.
     * @param writer The image writer.
     * @return The resource pool with the difference file resources added to
     *         the {@code resultContent}
     */
    @SuppressWarnings("try")
    private static ResourcePool addResourceDiffFiles(ResourcePool jmodContent,
                                                     ResourcePool resultContent,
                                                     BasicImageWriter writer) {
        JimageDiffGenerator generator = new JimageDiffGenerator();
        List<ResourceDiff> diff;
        try (ImageResource jmods = new ResourcePoolReader(jmodContent);
             ImageResource jimage = new ResourcePoolReader(resultContent)) {
            diff = generator.generateDiff(jmods, jimage);
        } catch (Exception e) {
            throw new AssertionError("Failed to generate the runtime image diff", e);
        }
        Set<String> modules = resultContent.moduleView().modules()
                                                        .map(a -> a.name())
                                                        .collect(Collectors.toSet());
        // Add resource diffs for the resource files we are about to add
        modules.stream().forEach(m -> {
            String resourceName = String.format(DIFF_PATH, m);
            ResourceDiff.Builder builder = new ResourceDiff.Builder();
            ResourceDiff d = builder.setKind(ResourceDiff.Kind.ADDED)
                                    .setName(resourceName)
                                    .build();
            diff.add(d);
        });
        Map<String, List<ResourceDiff>> perModDiffs = preparePerModuleDiffs(diff,
                                                                            modules);
        return addDiffResourcesFiles(modules, perModDiffs, resultContent, writer);
    }

    private static Map<String, List<ResourceDiff>> preparePerModuleDiffs(List<ResourceDiff> resDiffs,
                                                                         Set<String> modules) {
        Map<String, List<ResourceDiff>> modToDiff = new HashMap<>();
        resDiffs.forEach(d -> {
            int secondSlash = d.getName().indexOf("/", 1);
            if (secondSlash == -1) {
                throw new AssertionError("Module name not present");
            }
            String module = d.getName().substring(1, secondSlash);
            List<ResourceDiff> perModDiff = modToDiff.computeIfAbsent(module,
                                                                      a -> new ArrayList<>());
            perModDiff.add(d);
        });
        Map<String, List<ResourceDiff>> allModsToDiff = new HashMap<>();
        modules.stream().forEach(m -> {
            List<ResourceDiff> d = modToDiff.get(m);
            if (d == null) {
                // Not all modules will have a diff
                allModsToDiff.put(m, Collections.emptyList());
            } else {
                allModsToDiff.put(m, d);
            }
        });
        return allModsToDiff;
    }

    private static ResourcePool addDiffResourcesFiles(Set<String> modules,
                                                      Map<String, List<ResourceDiff>> perModDiffs,
                                                      ResourcePool resultResources,
                                                      BasicImageWriter writer) {
        ResourcePoolManager mgr = createPoolManager(resultResources, writer);
        ResourcePoolBuilder out = mgr.resourcePoolBuilder();
        modules.stream().sorted().forEach(module -> {
            String mResource = String.format(DIFF_PATH, module);
            List<ResourceDiff> diff = perModDiffs.get(module);
            // Note that for modules without diff to the packaged modules view
            // we create resource diff files with just the header and no content.
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                ResourceDiff.write(diff, bout);
            } catch (IOException e) {
                throw new AssertionError("Failed to write resource diff file" +
                                         " for module " + module, e);
            }
            out.add(ResourcePoolEntry.create(mResource, bout.toByteArray()));
        });
        return out.build();
    }

    /**
     * Support for creating runtimes that can be used for linking from the
     * run-time image. Adds meta-data files for resources not in the lib/modules
     * file of the JDK. That is, mapping files for which on-disk files belong to
     * which module.
     *
     * @param resultResources
     *            The original resources which serve as the basis for generating
     *            the meta-data files.
     * @param writer
     *            The image writer.
     *
     * @return An amended resource pool which includes meta-data files.
     */
    private static ResourcePool addNonClassResourcesTrackFiles(ResourcePool resultResources,
                                                               BasicImageWriter writer) {
        // Only add resources if jdk.jlink module is present in the target image
        Optional<ResourcePoolModule> jdkJlink = resultResources.moduleView()
                                                               .findModule(JLINK_MOD_NAME);
        if (jdkJlink.isPresent()) {
            Map<String, List<String>> nonClassResources = recordAndFilterEntries(resultResources);
            return addModuleResourceEntries(resultResources, nonClassResources, writer);
        } else {
            return resultResources; // No-op
        }
    }

    /**
     * Support for creating runtimes that can be used for linking from the
     * run-time image. Adds the given mapping of files as a meta-data file to
     * the given resource pool.
     *
     * @param resultResources
     *            The resource pool to add files to.
     * @param nonClassResEntries
     *            The per module mapping for which to create the meta-data files
     *            for.
     * @param writer
     *            The image writer.
     *
     * @return A resource pool with meta-data files added.
     */
    private static ResourcePool addModuleResourceEntries(ResourcePool resultResources,
                                                         Map<String, List<String>> nonClassResEntries,
                                                         BasicImageWriter writer) {
        Set<String> inputModules = resultResources.moduleView().modules()
                                                  .map(rm -> rm.name())
                                                  .collect(Collectors.toSet());
        ResourcePoolManager mgr = createPoolManager(resultResources, writer);
        ResourcePoolBuilder out = mgr.resourcePoolBuilder();
        inputModules.stream().sorted().forEach(module -> {
            String mResource = String.format(RESPATH, module);
            List<String> mResources = nonClassResEntries.get(module);
            if (mResources == null) {
                // We create empty resource files for modules in the resource
                // pool view that don't themselves contain native resources
                // or config files.
                out.add(ResourcePoolEntry.create(mResource, EMPTY_RESOURCE_BYTES));
            } else {
                String mResContent = mResources.stream().sorted()
                                               .collect(Collectors.joining("\n"));
                out.add(ResourcePoolEntry.create(mResource,
                                                 mResContent.getBytes(StandardCharsets.UTF_8)));
            }
        });
        return out.build();
    }

    /**
     * Support for creating runtimes that can be used for linking from the
     * run-time image. Generates a per module mapping of files not part of the
     * modules image (jimage). This mapping is needed so as to know which files
     * of the installed JDK belong to which module.
     *
     * @param resultResources
     *            The resources from which the mapping gets generated
     * @return A mapping with the module names as keys and the list of files not
     *         part of the modules image (jimage) as values.
     */
    private static Map<String, List<String>> recordAndFilterEntries(ResourcePool resultResources) {
        Map<String, List<String>> nonClassResEntries = new HashMap<>();
        Platform platform = getTargetPlatform(resultResources);
        resultResources.entries().forEach(entry -> {
            // Note that the fs_$module_files file is a resource file itself, so
            // we cannot add fs_$module_files themselves due to the
            // not(class_or_resources) condition. However, we also don't want
            // to track 'release' file entries (not(top) condition) as those are
            // handled by the release info plugin.
            if (entry.type() != ResourcePoolEntry.Type.CLASS_OR_RESOURCE &&
                    entry.type() != ResourcePoolEntry.Type.TOP) {
                List<String> mRes = nonClassResEntries.computeIfAbsent(entry.moduleName(),
                                                                       a -> new ArrayList<>());
                ResourceFileEntry rfEntry = ResourceFileEntry.toResourceFileEntry(entry,
                                                                                  platform);
                mRes.add(rfEntry.encodeToString());
            }
        });
        return nonClassResEntries;
    }

    private static Platform getTargetPlatform(ResourcePool in) {
        String platform = in.moduleView().findModule("java.base")
                .map(ResourcePoolModule::targetPlatform)
                .orElseThrow(() -> new AssertionError("java.base not found"));
        return Platform.parsePlatform(platform);
    }

    private static ResourcePoolManager createPoolManager(Set<Archive> archives,
            Map<String, List<Entry>> entriesForModule,
            ByteOrder byteOrder,
            BasicImageWriter writer) throws IOException {
        ResourcePoolManager resources = createBasicResourcePoolManager(byteOrder, writer);
        archives.stream()
                .map(Archive::moduleName)
                .sorted()
                .flatMap(mn ->
                    entriesForModule.get(mn).stream()
                            .map(e -> new ArchiveEntryResourcePoolEntry(mn,
                                    e.getResourcePoolEntryName(), e)))
                .forEach(resources::add);
        return resources;
    }

    private static ResourcePoolManager createBasicResourcePoolManager(ByteOrder byteOrder,
                                                                      BasicImageWriter writer) {
        return new ResourcePoolManager(byteOrder, new StringTable() {

            @Override
            public int addString(String str) {
                return writer.addString(str);
            }

            @Override
            public String getString(int id) {
                return writer.getString(id);
            }
        });
    }

    /**
     * Creates a ResourcePoolManager from existing resources so that more
     * resources can be appended.
     *
     * @param resultResources The existing resources to initially add.
     * @param writer The basic image writer.
     * @return An appendable ResourcePoolManager.
     */
    private static ResourcePoolManager createPoolManager(ResourcePool resultResources,
                                                         BasicImageWriter writer) {
        ResourcePoolManager resources = createBasicResourcePoolManager(resultResources.byteOrder(),
                                                                       writer);
        // Note that resources are already sorted in the correct order.
        // The underlying ResourcePoolManager keeps track of entries via
        // LinkedHashMap, which keeps values in insertion order. Therefore
        // adding resources here, preserving that same order is OK.
        resultResources.entries().forEach(resources::add);
        return resources;
    }

    /**
     * Helper method that splits a Resource path onto 3 items: module, parent
     * and resource name.
     *
     * @param path
     * @return An array containing module, parent and name.
     */
    public static String[] splitPath(String path) {
        Objects.requireNonNull(path);
        String noRoot = path.substring(1);
        int pkgStart = noRoot.indexOf("/");
        String module = noRoot.substring(0, pkgStart);
        List<String> result = new ArrayList<>();
        result.add(module);
        String pkg = noRoot.substring(pkgStart + 1);
        String resName;
        int pkgEnd = pkg.lastIndexOf("/");
        if (pkgEnd == -1) { // No package.
            resName = pkg;
        } else {
            resName = pkg.substring(pkgEnd + 1);
        }

        pkg = toPackage(pkg, false);
        result.add(pkg);
        result.add(resName);

        String[] array = new String[result.size()];
        return result.toArray(array);
    }

    /**
     * Returns the path of the resource.
     */
    public static String resourceName(String path) {
        Objects.requireNonNull(path);
        String s = path.substring(1);
        int index = s.indexOf("/");
        return s.substring(index + 1);
    }

    public static String toPackage(String name) {
        return toPackage(name, false);
    }

    private static String toPackage(String name, boolean log) {
        int index = name.lastIndexOf('/');
        if (index > 0) {
            return name.substring(0, index).replace('/', '.');
        } else {
            // ## unnamed package
            if (log) {
                System.err.format("Warning: %s in unnamed package%n", name);
            }
            return "";
        }
    }
}
