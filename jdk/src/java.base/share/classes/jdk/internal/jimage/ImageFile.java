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
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import jdk.internal.jimage.ImageModules.Loader;
import jdk.internal.jimage.ImageModules.ModuleIndex;

/**
 * An image (native endian.)
 * <pre>{@code
 * {
 *   u4 magic;
 *   u2 major_version;
 *   u2 minor_version;
 *   u4 location_count;
 *   u4 location_attributes_size;
 *   u4 strings_size;
 *   u4 redirect[location_count];
 *   u4 offsets[location_count];
 *   u1 location_attributes[location_attributes_size];
 *   u1 strings[strings_size];
 *   u1 content[if !EOF];
 * }
 * }</pre>
 */
public final class ImageFile {
    private static final String JAVA_BASE = "java.base";
    private static final String IMAGE_EXT = ".jimage";
    private static final String JAR_EXT = ".jar";
    private final Path root;
    private final Path mdir;
    private final Map<String, List<Resource>> resourcesForModule = new HashMap<>();

    private ImageFile(Path path) {
        this.root = path;
        this.mdir = root.resolve(path.getFileSystem().getPath("lib", "modules"));
    }

    public static ImageFile open(Path path) throws IOException {
        ImageFile lib = new ImageFile(path);
        return lib.open();
    }

    private ImageFile open() throws IOException {
        Path path = mdir.resolve("bootmodules" + IMAGE_EXT);

        ImageReader reader = new ImageReader(path.toString());
        ImageHeader header = reader.getHeader();

        if (header.getMagic() != ImageHeader.MAGIC) {
            if (header.getMagic() == ImageHeader.BADMAGIC) {
                throw new IOException(path + ": Image may be not be native endian");
            } else {
                throw new IOException(path + ": Invalid magic number");
            }
        }

        if (header.getMajorVersion() > ImageHeader.MAJOR_VERSION ||
            (header.getMajorVersion() == ImageHeader.MAJOR_VERSION &&
             header.getMinorVersion() > ImageHeader.MINOR_VERSION)) {
            throw new IOException("invalid version number");
        }

        return this;
    }

    public static ImageFile create(Path output,
                                   Set<Archive> archives,
                                   ImageModules modules)
        throws IOException
    {
        return ImageFile.create(output, archives, modules, ByteOrder.nativeOrder());
    }

    public static ImageFile create(Path output,
                                   Set<Archive> archives,
                                   ImageModules modules,
                                   ByteOrder byteOrder)
        throws IOException
    {
        ImageFile lib = new ImageFile(output);
        // get all resources
        lib.readModuleEntries(modules, archives);
        // write to modular image
        lib.writeImage(modules, archives, byteOrder);
        return lib;
    }

    private void writeImage(ImageModules modules,
                            Set<Archive> archives,
                            ByteOrder byteOrder)
        throws IOException
    {
        // name to Archive file
        Map<String, Archive> nameToArchive =
            archives.stream()
                  .collect(Collectors.toMap(Archive::moduleName, Function.identity()));

        Files.createDirectories(mdir);
        for (Loader l : Loader.values()) {
            Set<String> mods = modules.getModules(l);

            try (OutputStream fos = Files.newOutputStream(mdir.resolve(l.getName() + IMAGE_EXT));
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    DataOutputStream out = new DataOutputStream(bos)) {
                // store index in addition of the class loader map for boot loader
                BasicImageWriter writer = new BasicImageWriter(byteOrder);
                Set<String> duplicates = new HashSet<>();

                // build package map for modules and add as resources
                ModuleIndex mindex = modules.buildModuleIndex(l, writer);
                long offset = mindex.size();

                // the order of traversing the resources and the order of
                // the module content being written must be the same
                for (String mn : mods) {
                    for (Resource res : resourcesForModule.get(mn)) {
                        String path = res.name();
                        long uncompressedSize = res.size();
                        long compressedSize = res.csize();
                        long onFileSize = compressedSize != 0 ? compressedSize : uncompressedSize;

                        if (duplicates.contains(path)) {
                            System.err.format("duplicate resource \"%s\", skipping%n", path);
                            // TODO Need to hang bytes on resource and write from resource not zip.
                            // Skipping resource throws off writing from zip.
                            offset += onFileSize;
                            continue;
                        }
                        duplicates.add(path);
                        writer.addLocation(path, offset, compressedSize, uncompressedSize);
                        offset += onFileSize;
                    }
                }

                // write header and indices
                byte[] bytes = writer.getBytes();
                out.write(bytes, 0, bytes.length);

                // write module table and packages
                mindex.writeTo(out);

                // write module content
                for (String mn : mods) {
                    writeModule(nameToArchive.get(mn), out);
                }
            }
        }
    }

    private void readModuleEntries(ImageModules modules,
                                   Set<Archive> archives)
        throws IOException
    {
        for (Archive archive : archives) {
            List<Resource> res = new ArrayList<>();
            archive.visitResources(x-> res.add(x));

            String mn = archive.moduleName();
            resourcesForModule.put(mn, res);

            Set<String> pkgs = res.stream().map(Resource::name)
                    .filter(n -> n.endsWith(".class"))
                    .map(this::toPackage)
                    .distinct()
                    .collect(Collectors.toSet());
            modules.setPackages(mn, pkgs);
        }
    }

    private String toPackage(String name) {
        int index = name.lastIndexOf('/');
        if (index > 0) {
            return name.substring(0, index).replace('/', '.');
        } else {
            // ## unnamed package
            System.err.format("Warning: %s in unnamed package%n", name);
            return "";
        }
    }

    private void writeModule(Archive archive,
                             OutputStream out)
        throws IOException
    {
          Consumer<Archive.Entry> consumer = archive.defaultImageWriter(root, out);
          archive.visitEntries(consumer);
    }


    static class Compressor {
        public static byte[] compress(byte[] bytesIn) {
            Deflater deflater = new Deflater();
            deflater.setInput(bytesIn);
            ByteArrayOutputStream stream = new ByteArrayOutputStream(bytesIn.length);
            byte[] buffer = new byte[1024];

            deflater.finish();
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                stream.write(buffer, 0, count);
            }

            try {
                stream.close();
            } catch (IOException ex) {
                return bytesIn;
            }

            byte[] bytesOut = stream.toByteArray();
            deflater.end();

            return bytesOut;
        }

        public static byte[] decompress(byte[] bytesIn) {
            Inflater inflater = new Inflater();
            inflater.setInput(bytesIn);
            ByteArrayOutputStream stream = new ByteArrayOutputStream(bytesIn.length);
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int count;

                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException ex) {
                    return null;
                }

                stream.write(buffer, 0, count);
            }

            try {
                stream.close();
            } catch (IOException ex) {
                return null;
            }

            byte[] bytesOut = stream.toByteArray();
            inflater.end();

            return bytesOut;
        }
    }
}
