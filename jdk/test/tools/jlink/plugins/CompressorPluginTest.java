/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary Test zip compressor
 * @author Jean-Francois Denise
 * @modules java.base/jdk.internal.jimage.decompressor
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 * @run main CompressorPluginTest
 */
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.jimage.decompressor.CompressedResourceHeader;
import jdk.internal.jimage.decompressor.ResourceDecompressor;
import jdk.internal.jimage.decompressor.ResourceDecompressorFactory;
import jdk.internal.jimage.decompressor.StringSharingDecompressorFactory;
import jdk.internal.jimage.decompressor.ZipDecompressorFactory;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.internal.plugins.DefaultCompressPlugin;
import jdk.tools.jlink.internal.plugins.StringSharingPlugin;
import jdk.tools.jlink.internal.plugins.ZipPlugin;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class CompressorPluginTest {

    private static int strID = 1;

    public static void main(String[] args) throws Exception {
        new CompressorPluginTest().test();
    }

    public void test() throws Exception {
        FileSystem fs;
        try {
            fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
            System.err.println("Not an image build, test skipped.");
            return;
        }
        Path javabase = fs.getPath("/modules/java.base");

        checkCompress(gatherResources(javabase), new ZipPlugin(), null,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory()
                });

        Pool classes = gatherClasses(javabase);
        // compress = String sharing
        checkCompress(classes, new StringSharingPlugin(), null,
                new ResourceDecompressorFactory[]{
                    new StringSharingDecompressorFactory()});

        // compress == ZIP + String sharing
        Properties options = new Properties();
        options.setProperty(ZipPlugin.NAME, "");
        checkCompress(classes, new DefaultCompressPlugin(), options,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory(),
                    new StringSharingDecompressorFactory()
                });

        // compress == ZIP + String sharing + filter
        options.setProperty(DefaultCompressPlugin.FILTER,
                "*Exception.class,^*IOException.class");
        checkCompress(classes, new DefaultCompressPlugin(), options,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory(),
                    new StringSharingDecompressorFactory()
                }, Collections.singletonList(".*Exception.class"),
                Collections.singletonList(".*IOException.class"));

        // compress level 1 == ZIP
        Properties options1 = new Properties();
        options1.setProperty(DefaultCompressPlugin.NAME,
                "1");
        checkCompress(classes, new DefaultCompressPlugin(),
                options1,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory()
                });

        // compress level 1 == ZIP
        options1.setProperty(DefaultCompressPlugin.FILTER,
                "*Exception.class,^*IOException.class");
        checkCompress(classes, new DefaultCompressPlugin(),
                options1,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory()
                }, Collections.singletonList(".*Exception.class"),
                Collections.singletonList(".*IOException.class"));

        // compress level 2 == ZIP + String sharing
        Properties options2 = new Properties();
        options2.setProperty(DefaultCompressPlugin.NAME,
                "2");
        checkCompress(classes, new DefaultCompressPlugin(),
                options2,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory(),
                    new StringSharingDecompressorFactory()
                });

        // compress level 2 == ZIP + String sharing + filter
        options2.setProperty(DefaultCompressPlugin.FILTER,
                "*Exception.class,^*IOException.class");
        checkCompress(classes, new DefaultCompressPlugin(),
                options2,
                new ResourceDecompressorFactory[]{
                    new ZipDecompressorFactory(),
                    new StringSharingDecompressorFactory()
                }, Collections.singletonList(".*Exception.class"),
                Collections.singletonList(".*IOException.class"));

        // compress level 0 == String sharing
        Properties options0 = new Properties();
        options0.setProperty(DefaultCompressPlugin.NAME, "0");
        checkCompress(classes, new DefaultCompressPlugin(),
                options0,
                new ResourceDecompressorFactory[]{
                    new StringSharingDecompressorFactory()
                });

        // compress level 0 == String sharing + filter
        options0.setProperty(DefaultCompressPlugin.FILTER,
                "*Exception.class,^*IOException.class");
        checkCompress(classes, new DefaultCompressPlugin(),
                options0,
                new ResourceDecompressorFactory[]{
                    new StringSharingDecompressorFactory()
                }, Collections.singletonList(".*Exception.class"),
                Collections.singletonList(".*IOException.class"));
    }

    private Pool gatherResources(Path module) throws Exception {
        Pool pool = new PoolImpl(ByteOrder.nativeOrder(), new StringTable() {

            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                return null;
            }
        });
        try (Stream<Path> stream = Files.walk(module)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext();) {
                Path p = iterator.next();
                if (Files.isRegularFile(p)) {
                    byte[] content = Files.readAllBytes(p);
                    pool.add(Pool.newResource(p.toString(), content));
                }
            }
        }
        return pool;
    }

    private Pool gatherClasses(Path module) throws Exception {
        Pool pool = new PoolImpl(ByteOrder.nativeOrder(), new StringTable() {

            @Override
            public int addString(String str) {
                return -1;
            }

            @Override
            public String getString(int id) {
                return null;
            }
        });
        try (Stream<Path> stream = Files.walk(module)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext();) {
                Path p = iterator.next();
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    byte[] content = Files.readAllBytes(p);
                    pool.add(Pool.newResource(p.toString(), content));
                }
            }
        }
        return pool;
    }

    private void checkCompress(Pool resources, Plugin prov,
            Properties config,
            ResourceDecompressorFactory[] factories) throws Exception {
        checkCompress(resources, prov, config, factories, Collections.emptyList(), Collections.emptyList());
    }

    private void checkCompress(Pool resources, Plugin prov,
            Properties config,
            ResourceDecompressorFactory[] factories,
            List<String> includes,
            List<String> excludes) throws Exception {
        long original = 0;
        long compressed = 0;
        for (ModuleData resource : resources.getContent()) {
            List<Pattern> includesPatterns = includes.stream()
                    .map(Pattern::compile)
                    .collect(Collectors.toList());
            List<Pattern> excludesPatterns = excludes.stream()
                    .map(Pattern::compile)
                    .collect(Collectors.toList());

            Map<String, String> props = new HashMap<>();
            if (config != null) {
                for (String p : config.stringPropertyNames()) {
                    props.put(p, config.getProperty(p));
                }
            }
            prov.configure(props);
            final Map<Integer, String> strings = new HashMap<>();
            PoolImpl inputResources = new PoolImpl(ByteOrder.nativeOrder(), new StringTable() {
                @Override
                public int addString(String str) {
                    int id = strID;
                    strID += 1;
                    strings.put(id, str);
                    return id;
                }

                @Override
                public String getString(int id) {
                    return strings.get(id);
                }
            });
            inputResources.add(resource);
            Pool compressedResources = applyCompressor(prov, inputResources, resource, includesPatterns, excludesPatterns);
            original += resource.getLength();
            compressed += compressedResources.get(resource.getPath()).getLength();
            applyDecompressors(factories, inputResources, compressedResources, strings, includesPatterns, excludesPatterns);
        }
        String compressors = Stream.of(factories)
                .map(Object::getClass)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        String size = "Compressed size: " + compressed + ", original size: " + original;
        System.out.println("Used " + compressors + ". " + size);
        if (original <= compressed) {
            throw new AssertionError("java.base not compressed.");
        }
    }

    private Pool applyCompressor(Plugin plugin,
            PoolImpl inputResources,
            ModuleData res,
            List<Pattern> includesPatterns,
            List<Pattern> excludesPatterns) throws Exception {
        TransformerPlugin compressor = (TransformerPlugin) plugin;
        Pool compressedPool = new PoolImpl(ByteOrder.nativeOrder(), inputResources.getStringTable());
        compressor.visit(inputResources, compressedPool);
        String path = res.getPath();
        ModuleData compressed = compressedPool.get(path);
        CompressedResourceHeader header
                = CompressedResourceHeader.readFromResource(ByteOrder.nativeOrder(), compressed.getBytes());
        if (isIncluded(includesPatterns, excludesPatterns, path)) {
            if (header == null) {
                throw new AssertionError("Path should be compressed: " + path);
            }
            if (header.getDecompressorNameOffset() == 0) {
                throw new AssertionError("Invalid plugin offset "
                        + header.getDecompressorNameOffset());
            }
            if (header.getResourceSize() <= 0) {
                throw new AssertionError("Invalid compressed size "
                        + header.getResourceSize());
            }
        } else if (header != null) {
            throw new AssertionError("Path should not be compressed: " + path);
        }
        return compressedPool;
    }

    private void applyDecompressors(ResourceDecompressorFactory[] decompressors,
            Pool inputResources,
            Pool compressedResources,
            Map<Integer, String> strings,
            List<Pattern> includesPatterns,
            List<Pattern> excludesPatterns) throws Exception {
        for (ModuleData compressed : compressedResources.getContent()) {
            CompressedResourceHeader header = CompressedResourceHeader.readFromResource(
                    ByteOrder.nativeOrder(), compressed.getBytes());
            String path = compressed.getPath();
            ModuleData orig = inputResources.get(path);
            if (!isIncluded(includesPatterns, excludesPatterns, path)) {
                continue;
            }
            byte[] decompressed = compressed.getBytes();
            for (ResourceDecompressorFactory factory : decompressors) {
                ResourceDecompressor decompressor = factory.newDecompressor(new Properties());
                decompressed = decompressor.decompress(
                        strings::get, decompressed,
                        CompressedResourceHeader.getSize(), header.getUncompressedSize());
            }

            if (decompressed.length != orig.getLength()) {
                throw new AssertionError("Invalid uncompressed size "
                        + header.getUncompressedSize());
            }
            byte[] origContent = orig.getBytes();
            for (int i = 0; i < decompressed.length; i++) {
                if (decompressed[i] != origContent[i]) {
                    throw new AssertionError("Decompressed and original differ at index " + i);
                }
            }
        }
    }

    private boolean isIncluded(List<Pattern> includesPatterns, List<Pattern> excludesPatterns, String path) {
        return !excludesPatterns.stream().anyMatch((pattern) -> pattern.matcher(path).matches())
                && (includesPatterns.isEmpty()
                || includesPatterns.stream().anyMatch((pattern) -> pattern.matcher(path).matches()));
    }
}
