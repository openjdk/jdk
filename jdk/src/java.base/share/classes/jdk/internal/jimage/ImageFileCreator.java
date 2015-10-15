/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jimage;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.jimage.Archive.Entry;
import jdk.internal.jimage.Archive.Entry.EntryType;
import static jdk.internal.jimage.BasicImageWriter.BOOT_NAME;
import static jdk.internal.jimage.BasicImageWriter.IMAGE_EXT;

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
    private final Path root;
    private final Path mdir;
    private final Map<String, List<Entry>> entriesForModule = new HashMap<>();
    private ImageFileCreator(Path path) {
        this.root = path;
        this.mdir = root.resolve(path.getFileSystem().getPath("lib", "modules"));
    }

    public static ImageFileCreator create(Path output,
            Set<Archive> archives)
            throws IOException {
        return create(output, BOOT_NAME, archives, ByteOrder.nativeOrder());
    }

    public static ImageFileCreator create(Path output,
            Set<Archive> archives,
            ByteOrder byteOrder)
            throws IOException {
        return create(output, BOOT_NAME, archives, byteOrder);
    }

    public static ImageFileCreator create(Path output,
                                   String fileName,
                                   Set<Archive> archives,
                                   ByteOrder byteOrder)
        throws IOException
    {
        ImageFileCreator image = new ImageFileCreator(output);
        // get all entries
        Map<String, Set<String>> modulePackagesMap = new HashMap<>();
        image.readAllEntries(modulePackagesMap, archives);
        // write to modular image
        image.writeImage(fileName, modulePackagesMap, archives, byteOrder);
        return image;
    }

    private void readAllEntries(Map<String, Set<String>> modulePackagesMap,
                                  Set<Archive> archives) {
        archives.stream().forEach((archive) -> {
            Map<Boolean, List<Entry>> es;
            try(Stream<Entry> entries = archive.entries()) {
                es = entries.collect(Collectors.partitioningBy(n -> n.type()
                        == EntryType.CLASS_OR_RESOURCE));
            }
            String mn = archive.moduleName();
            List<Entry> all = new ArrayList<>();
            all.addAll(es.get(false));
            all.addAll(es.get(true));
            entriesForModule.put(mn, all);
            // Extract package names
            Set<String> pkgs = es.get(true).stream().map(Entry::name)
                    .filter(n -> isClassPackage(n))
                    .map(ImageFileCreator::toPackage)
                    .collect(Collectors.toSet());
            modulePackagesMap.put(mn, pkgs);
        });
    }

    public static boolean isClassPackage(String path) {
        return path.endsWith(".class");
    }

    public static boolean isResourcePackage(String path) {
        path = path.substring(1);
        path = path.substring(path.indexOf("/")+1);
        return !path.startsWith("META-INF/");
    }

    public static void recreateJimage(Path jimageFile,
            String jdataName,
            Set<Archive> archives,
            Map<String, Set<String>> modulePackages)
            throws IOException {
        Map<String, List<Entry>> entriesForModule
                = archives.stream().collect(Collectors.toMap(
                                Archive::moduleName,
                                a -> {
                                    try(Stream<Entry> entries = a.entries()) {
                                        return entries.collect(Collectors.toList());
                                    }
                                }));
        Map<String, Archive> nameToArchive
                = archives.stream()
                .collect(Collectors.toMap(Archive::moduleName, Function.identity()));
        ByteOrder order = ByteOrder.nativeOrder();
        ResourcePoolImpl resources = createResources(modulePackages, nameToArchive,
                (Entry t) -> {
            throw new UnsupportedOperationException("Not supported, no external file "
                    + "in a jimage file");
        }, entriesForModule, order);
        generateJImage(jimageFile, jdataName, resources, order);
    }

    private void writeImage(String fileName,
            Map<String, Set<String>> modulePackagesMap,
            Set<Archive> archives,
            ByteOrder byteOrder)
            throws IOException {
        Files.createDirectories(mdir);
        ExternalFilesWriter filesWriter = new ExternalFilesWriter(root);
        // name to Archive file
        Map<String, Archive> nameToArchive
                = archives.stream()
                .collect(Collectors.toMap(Archive::moduleName, Function.identity()));
        ResourcePoolImpl resources = createResources(modulePackagesMap,
                nameToArchive, filesWriter,
                entriesForModule, byteOrder);
        generateJImage(mdir.resolve(fileName + IMAGE_EXT), fileName, resources,
                byteOrder);
    }

    private static void generateJImage(Path img,
            String fileName,
            ResourcePoolImpl resources,
            ByteOrder byteOrder
    ) throws IOException {
        BasicImageWriter writer = new BasicImageWriter(byteOrder);

        Map<String, Set<String>> modulePackagesMap = resources.getModulePackages();

        try (OutputStream fos = Files.newOutputStream(img);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                DataOutputStream out = new DataOutputStream(bos)) {
            Set<String> duplicates = new HashSet<>();
            ImageModuleDataWriter moduleData =
            ImageModuleDataWriter.buildModuleData(writer, modulePackagesMap);
            moduleData.addLocation(fileName, writer);
            long offset = moduleData.size();

            List<ResourcePool.Resource> content = new ArrayList<>();
            List<String> paths = new ArrayList<>();
                 // the order of traversing the resources and the order of
            // the module content being written must be the same
            for (ResourcePool.Resource res : resources.getResources()) {
                String path = res.getPath();
                int index = path.indexOf("/META-INF/");
                if (index != -1) {
                    path = path.substring(index + 1);
                }

                content.add(res);
                long uncompressedSize = res.getLength();
                long compressedSize = 0;
                if (res instanceof ResourcePool.CompressedResource) {
                    ResourcePool.CompressedResource comp =
                            (ResourcePool.CompressedResource) res;
                    compressedSize = res.getLength();
                    uncompressedSize = comp.getUncompressedSize();
                }
                long onFileSize = res.getLength();

                if (duplicates.contains(path)) {
                    System.err.format("duplicate resource \"%s\", skipping%n",
                            path);
                     // TODO Need to hang bytes on resource and write
                    // from resource not zip.
                    // Skipping resource throws off writing from zip.
                    offset += onFileSize;
                    continue;
                }
                duplicates.add(path);
                writer.addLocation(path, offset, compressedSize, uncompressedSize);
                paths.add(path);
                offset += onFileSize;
            }

            ImageResourcesTree tree = new ImageResourcesTree(offset, writer, paths);

            // write header and indices
            byte[] bytes = writer.getBytes();
            out.write(bytes, 0, bytes.length);

            // write module meta data
            moduleData.writeTo(out);

            // write module content
            for(ResourcePool.Resource res : content) {
                byte[] buf = res.getByteArray();
                out.write(buf, 0, buf.length);
            }

            tree.addContent(out);
        }
    }

    private static ResourcePoolImpl createResources(Map<String, Set<String>> modulePackagesMap,
            Map<String, Archive> nameToArchive,
            Consumer<Entry> externalFileHandler,
            Map<String, List<Entry>> entriesForModule,
            ByteOrder byteOrder) throws IOException {
        ResourcePoolImpl resources = new ResourcePoolImpl(byteOrder);
        // Doesn't contain META-INF
        Set<String> mods = modulePackagesMap.keySet();
        for (String mn : mods) {
            for (Entry entry : entriesForModule.get(mn)) {
                String path = entry.name();
                if (entry.type() == EntryType.CLASS_OR_RESOURCE) {
                    if (!entry.path().endsWith(BOOT_NAME)) {
                        try (InputStream stream = entry.stream()) {
                            byte[] bytes = readAllBytes(stream);
                            path = "/" + mn + "/" + path;
                            try {
                                resources.addResource(new ResourcePool.Resource(path,
                                        ByteBuffer.wrap(bytes)));
                            } catch (Exception ex) {
                                throw new IOException(ex);
                            }
                        }
                    }
                } else {
                    externalFileHandler.accept(entry);
                }
            }
            // Done with this archive, close it.
            Archive archive = nameToArchive.get(mn);
            archive.close();
        }
        // Fix for 8136365. Do we have an archive with module name "META-INF"?
        // If yes, we are recreating a jimage.
        // This is a workaround for META-INF being at the top level of resource path
        String mn = "META-INF";
        Archive archive = nameToArchive.get(mn);
        if (archive != null) {
            try {
                for (Entry entry : entriesForModule.get(mn)) {
                    String path = entry.name();
                    try (InputStream stream = entry.stream()) {
                        byte[] bytes = readAllBytes(stream);
                        path = mn + "/" + path;
                        try {
                            resources.addResource(new ResourcePool.Resource(path,
                                    ByteBuffer.wrap(bytes)));
                        } catch (Exception ex) {
                            throw new IOException(ex);
                        }
                    }
                }
            } finally {
                // Done with this archive, close it.
                archive.close();
            }
        }
        return resources;
    }

    private static final int BUF_SIZE = 8192;

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[BUF_SIZE];
        while (true) {
            int n = is.read(buf);
            if (n < 0) {
                break;
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
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

    private static String toPackage(String name) {
        String pkg = toPackage(name, true);
        return pkg;
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
