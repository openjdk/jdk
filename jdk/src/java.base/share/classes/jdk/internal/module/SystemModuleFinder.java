/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReaderFactory;
import jdk.internal.misc.JavaNetUriAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.ModuleHashes.HashSupplier;
import jdk.internal.perf.PerfCounter;

/**
 * A {@code ModuleFinder} that finds modules that are linked into the
 * run-time image.
 *
 * The modules linked into the run-time image are assumed to have the
 * Packages attribute.
 */

public class SystemModuleFinder implements ModuleFinder {

    private static final JavaNetUriAccess JNUA = SharedSecrets.getJavaNetUriAccess();

    private static final PerfCounter initTime
        = PerfCounter.newPerfCounter("jdk.module.finder.jimage.initTime");
    private static final PerfCounter moduleCount
        = PerfCounter.newPerfCounter("jdk.module.finder.jimage.modules");
    private static final PerfCounter packageCount
        = PerfCounter.newPerfCounter("jdk.module.finder.jimage.packages");
    private static final PerfCounter exportsCount
        = PerfCounter.newPerfCounter("jdk.module.finder.jimage.exports");

    // singleton finder to find modules in the run-time images
    private static final SystemModuleFinder INSTANCE;

    public static SystemModuleFinder getInstance() {
        return INSTANCE;
    }

    /**
     * For now, the module references are created eagerly on the assumption
     * that service binding will require all modules to be located.
     */
    static {
        long t0 = System.nanoTime();

        INSTANCE = new SystemModuleFinder();

        initTime.addElapsedTimeFrom(t0);
    }

    /**
     * Holder class for the ImageReader
     */
    private static class SystemImage {
        static final ImageReader READER;
        static {
            long t0 = System.nanoTime();
            READER = ImageReaderFactory.getImageReader();
            initTime.addElapsedTimeFrom(t0);
        }

        static ImageReader reader() {
            return READER;
        }
    }

    private static boolean isFastPathSupported() {
       return SystemModules.MODULE_NAMES.length > 0;
    }

    private static String[] moduleNames() {
        if (isFastPathSupported())
            // module names recorded at link time
            return SystemModules.MODULE_NAMES;

        // this happens when java.base is patched with java.base
        // from an exploded image
        return SystemImage.reader().getModuleNames();
    }

    // the set of modules in the run-time image
    private final Set<ModuleReference> modules;

    // maps module name to module reference
    private final Map<String, ModuleReference> nameToModule;

    // module name to hashes
    private final Map<String, byte[]> hashes;

    private SystemModuleFinder() {
        String[] names = moduleNames();
        int n = names.length;
        moduleCount.add(n);

        // fastpath is enabled by default.
        // It can be disabled for troubleshooting purpose.
        boolean disabled =
            System.getProperty("jdk.system.module.finder.disabledFastPath") != null;

        ModuleDescriptor[] descriptors;
        ModuleTarget[] targets;
        ModuleHashes[] recordedHashes;
        ModuleResolution[] moduleResolutions;

        // fast loading of ModuleDescriptor of system modules
        if (isFastPathSupported() && !disabled) {
            descriptors = SystemModules.descriptors();
            targets = SystemModules.targets();
            recordedHashes = SystemModules.hashes();
            moduleResolutions = SystemModules.moduleResolutions();
        } else {
            // if fast loading of ModuleDescriptors is disabled
            // fallback to read module-info.class
            descriptors = new ModuleDescriptor[n];
            targets = new ModuleTarget[n];
            recordedHashes = new ModuleHashes[n];
            moduleResolutions = new ModuleResolution[n];
            ImageReader imageReader = SystemImage.reader();
            for (int i = 0; i < names.length; i++) {
                String mn = names[i];
                ImageLocation loc = imageReader.findLocation(mn, "module-info.class");
                ModuleInfo.Attributes attrs =
                    ModuleInfo.read(imageReader.getResourceBuffer(loc), null);
                descriptors[i] = attrs.descriptor();
                targets[i] = attrs.target();
                recordedHashes[i] = attrs.recordedHashes();
                moduleResolutions[i] = attrs.moduleResolution();
            }
        }

        Map<String, byte[]> hashes = null;
        boolean secondSeen = false;
        // record the hashes to build HashSupplier
        for (ModuleHashes mh : recordedHashes) {
            if (mh != null) {
                // if only one module contain ModuleHashes, use it
                if (hashes == null) {
                    hashes = mh.hashes();
                } else {
                    if (!secondSeen) {
                        hashes = new HashMap<>(hashes);
                        secondSeen = true;
                    }
                    hashes.putAll(mh.hashes());
                }
            }
        }
        this.hashes = (hashes == null) ? Map.of() : hashes;

        ModuleReference[] mods = new ModuleReference[n];

        @SuppressWarnings(value = {"rawtypes", "unchecked"})
        Entry<String, ModuleReference>[] map
            = (Entry<String, ModuleReference>[])new Entry[n];

        for (int i = 0; i < n; i++) {
            ModuleDescriptor md = descriptors[i];

            // create the ModuleReference
            ModuleReference mref = toModuleReference(md,
                                                     targets[i],
                                                     recordedHashes[i],
                                                     hashSupplier(names[i]),
                                                     moduleResolutions[i]);
            mods[i] = mref;
            map[i] = Map.entry(names[i], mref);

            // counters
            packageCount.add(md.packages().size());
            exportsCount.add(md.exports().size());
        }

        modules = Set.of(mods);
        nameToModule = Map.ofEntries(map);
    }

    @Override
    public Optional<ModuleReference> find(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(nameToModule.get(name));
    }

    @Override
    public Set<ModuleReference> findAll() {
        return modules;
    }

    private ModuleReference toModuleReference(ModuleDescriptor md,
                                              ModuleTarget target,
                                              ModuleHashes recordedHashes,
                                              HashSupplier hasher,
                                              ModuleResolution mres) {
        String mn = md.name();
        URI uri = JNUA.create("jrt", "/".concat(mn));

        Supplier<ModuleReader> readerSupplier = new Supplier<>() {
            @Override
            public ModuleReader get() {
                return new ImageModuleReader(mn, uri);
            }
        };

        ModuleReference mref = new ModuleReferenceImpl(md,
                                                       uri,
                                                       readerSupplier,
                                                       null,
                                                       target,
                                                       recordedHashes,
                                                       hasher,
                                                       mres);

        // may need a reference to a patched module if --patch-module specified
        mref = ModuleBootstrap.patcher().patchIfNeeded(mref);

        return mref;
    }

    private HashSupplier hashSupplier(String name) {
        if (!hashes.containsKey(name))
            return null;

        return new HashSupplier() {
            @Override
            public byte[] generate(String algorithm) {
                return hashes.get(name);
            }
        };
    }

    /**
     * A ModuleReader for reading resources from a module linked into the
     * run-time image.
     */
    static class ImageModuleReader implements ModuleReader {
        private final String module;
        private volatile boolean closed;

        /**
         * If there is a security manager set then check permission to
         * connect to the run-time image.
         */
        private static void checkPermissionToConnect(URI uri) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                try {
                    URLConnection uc = uri.toURL().openConnection();
                    sm.checkPermission(uc.getPermission());
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            }
        }

        ImageModuleReader(String module, URI uri) {
            checkPermissionToConnect(uri);
            this.module = module;
        }

        /**
         * Returns the ImageLocation for the given resource, {@code null}
         * if not found.
         */
        private ImageLocation findImageLocation(String name) throws IOException {
            Objects.requireNonNull(name);
            if (closed)
                throw new IOException("ModuleReader is closed");
            ImageReader imageReader = SystemImage.reader();
            if (imageReader != null) {
                return imageReader.findLocation(module, name);
            } else {
                // not an images build
                return null;
            }
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            ImageLocation location = findImageLocation(name);
            if (location != null) {
                URI u = URI.create("jrt:/" + module + "/" + name);
                return Optional.of(u);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            return read(name).map(this::toInputStream);
        }

        private InputStream toInputStream(ByteBuffer bb) { // ## -> ByteBuffer?
            try {
                int rem = bb.remaining();
                byte[] bytes = new byte[rem];
                bb.get(bytes);
                return new ByteArrayInputStream(bytes);
            } finally {
                release(bb);
            }
        }

        @Override
        public Optional<ByteBuffer> read(String name) throws IOException {
            ImageLocation location = findImageLocation(name);
            if (location != null) {
                return Optional.of(SystemImage.reader().getResourceBuffer(location));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void release(ByteBuffer bb) {
            Objects.requireNonNull(bb);
            ImageReader.releaseByteBuffer(bb);
        }

        @Override
        public Stream<String> list() throws IOException {
            if (closed)
                throw new IOException("ModuleReader is closed");

            Spliterator<String> s = new ModuleContentSpliterator(module);
            return StreamSupport.stream(s, false);
        }

        @Override
        public void close() {
            // nothing else to do
            closed = true;
        }
    }

    /**
     * A Spliterator for traversing the resources of a module linked into the
     * run-time image.
     */
    static class ModuleContentSpliterator implements Spliterator<String> {
        final String moduleRoot;
        final Deque<ImageReader.Node> stack;
        Iterator<ImageReader.Node> iterator;

        ModuleContentSpliterator(String module) throws IOException {
            moduleRoot = "/modules/" + module;
            stack = new ArrayDeque<>();

            // push the root node to the stack to get started
            ImageReader.Node dir = SystemImage.reader().findNode(moduleRoot);
            if (dir == null || !dir.isDirectory())
                throw new IOException(moduleRoot + " not a directory");
            stack.push(dir);
            iterator = Collections.emptyIterator();
        }

        /**
         * Returns the name of the next non-directory node or {@code null} if
         * there are no remaining nodes to visit.
         */
        private String next() throws IOException {
            for (;;) {
                while (iterator.hasNext()) {
                    ImageReader.Node node = iterator.next();
                    String name = node.getName();
                    if (node.isDirectory()) {
                        // build node
                        ImageReader.Node dir = SystemImage.reader().findNode(name);
                        assert dir.isDirectory();
                        stack.push(dir);
                    } else {
                        // strip /modules/$MODULE/ prefix
                        return name.substring(moduleRoot.length() + 1);
                    }
                }

                if (stack.isEmpty()) {
                    return null;
                } else {
                    ImageReader.Node dir = stack.poll();
                    assert dir.isDirectory();
                    iterator = dir.getChildren().iterator();
                }
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            String next;
            try {
                next = next();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
            if (next != null) {
                action.accept(next);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Spliterator<String> trySplit() {
            return null;
        }

        @Override
        public int characteristics() {
            return Spliterator.DISTINCT + Spliterator.NONNULL + Spliterator.IMMUTABLE;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }
    }
}
