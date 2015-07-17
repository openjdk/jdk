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
package jdk.internal.jimage;

import jdk.internal.jimage.decompressor.CompressedResourceHeader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pool of resources. This class contain the content of a jimage file in the
 * matter of Resource.
 */
public interface ResourcePool {

    /**
     * Resources visitor
     */
    public interface Visitor {

        /**
         * Called for each visited Resource.
         *
         * @param resource The resource to deal with.
         * @param order Byte order
         * @param strings
         * @return A resource or null if the passed resource is to be removed
         * from the jimage.
         * @throws Exception
         */
        public Resource visit(Resource resource, ByteOrder order,
                StringTable strings) throws Exception;
    }

    /**
     * A JImage Resource. Fully identified by its path.
     */
    public static class Resource {

        private final String path;
        private final ByteBuffer content;

        private final String module;

        public Resource(String path, ByteBuffer content) {
            Objects.requireNonNull(path);
            Objects.requireNonNull(content);
            this.path = path;
            this.content = content.asReadOnlyBuffer();
            String[] split = ImageFileCreator.splitPath(path);
            module = split[0];
        }

        public String getPath() {
            return path;
        }

        public String getModule() {
            return module;
        }

        /**
         * The resource content.
         *
         * @return A read only buffer.
         */
        public ByteBuffer getContent() {
            return content;
        }

        public int getLength() {
            return content.limit();
        }

        public byte[] getByteArray() {
            content.rewind();
            byte[] array = new byte[content.remaining()];
            content.get(array);
            return array;
        }

        @Override
        public String toString() {
            return getPath();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Resource)) {
                return false;
            }
            Resource res = (Resource) obj;
            return res.path.equals(path);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + Objects.hashCode(this.path);
            return hash;
        }
    }

    /**
     * A resource that has been compressed.
     */
    public static final class CompressedResource extends Resource {

        private final long uncompressed_size;

        private CompressedResource(String path, ByteBuffer content,
                long uncompressed_size) {
            super(path, content);
            this.uncompressed_size = uncompressed_size;
        }

        public long getUncompressedSize() {
            return uncompressed_size;
        }

        public static CompressedResource newCompressedResource(Resource original,
                ByteBuffer compressed,
                String plugin, String pluginConfig, StringTable strings,
                ByteOrder order) throws Exception {
            Objects.requireNonNull(original);
            Objects.requireNonNull(compressed);
            Objects.requireNonNull(plugin);

            boolean isTerminal = !(original instanceof CompressedResource);
            long uncompressed_size = original.getLength();
            if (original instanceof CompressedResource) {
                CompressedResource comp = (CompressedResource) original;
                uncompressed_size = comp.getUncompressedSize();
            }
            int nameOffset = strings.addString(plugin);
            int configOffset = -1;
            if (pluginConfig != null) {
                configOffset = strings.addString(plugin);
            }
            CompressedResourceHeader rh =
                    new CompressedResourceHeader(compressed.limit(), original.getLength(),
                    nameOffset, configOffset, isTerminal);
            // Merge header with content;
            byte[] h = rh.getBytes(order);
            ByteBuffer bb = ByteBuffer.allocate(compressed.limit() + h.length);
            bb.order(order);
            bb.put(h);
            bb.put(compressed);
            ByteBuffer contentWithHeader = ByteBuffer.wrap(bb.array());

            CompressedResource compressedResource =
                    new CompressedResource(original.getPath(),
                    contentWithHeader, uncompressed_size);
            return compressedResource;
        }
    }

    /**
     * Read only state.
     *
     * @return true if readonly false otherwise.
     */
    public boolean isReadOnly();

    /**
     * The byte order
     *
     * @return
     */
    public ByteOrder getByteOrder();

    /**
     * Add a resource.
     *
     * @param resource The Resource to add.
     * @throws java.lang.Exception If the pool is read only.
     */
    public void addResource(Resource resource) throws Exception;

    /**
     * Check if a resource is contained in the pool.
     *
     * @param res The resource to check.
     * @return true if res is contained, false otherwise.
     */
    public boolean contains(Resource res);

    /**
     * Get all resources contained in this pool instance.
     *
     * @return The collection of resources;
     */
    public Collection<Resource> getResources();

    /**
     * Get the resource for the passed path.
     *
     * @param path A resource path
     * @return A Resource instance or null if the resource is not found
     */
    public Resource getResource(String path);

    /**
     * The Image modules. It is computed based on the resources contained by
     * this ResourcePool instance.
     *
     * @return The Image Modules.
     */
    public Map<String, Set<String>> getModulePackages();

    /**
     * Check if this pool contains some resources.
     *
     * @return True if contains some resources.
     */
    public boolean isEmpty();

    /**
     * Visit the resources contained in this ResourcePool.
     *
     * @param visitor The visitor
     * @param output The pool to store resources.
     * @param strings
     * @throws Exception
     */
    public void visit(Visitor visitor, ResourcePool output, StringTable strings)
            throws Exception;

    public void addTransformedResource(Resource original, ByteBuffer transformed)
            throws Exception;
}
