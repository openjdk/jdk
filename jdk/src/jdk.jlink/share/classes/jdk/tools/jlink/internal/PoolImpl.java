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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import jdk.internal.jimage.decompressor.CompressedResourceHeader;
import jdk.tools.jlink.plugin.Pool;

/**
 * Pool of module data.
 */
public class PoolImpl extends Pool {

    /**
     * A resource that has been compressed.
     */
    public static final class CompressedModuleData extends ModuleData {

        private final long uncompressed_size;

        private CompressedModuleData(String module, String path,
                InputStream stream, long size,
                long uncompressed_size) {
            super(module, path, ModuleDataType.CLASS_OR_RESOURCE, stream, size);
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

    private boolean isReadOnly;
    private final StringTable table;

    public PoolImpl() {
        this(ByteOrder.nativeOrder(), new StringTable() {

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

    public PoolImpl(ByteOrder order) {
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

    public PoolImpl(ByteOrder order, StringTable table) {
        super(order);
        this.table = table;
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

    public static CompressedModuleData newCompressedResource(ModuleData original,
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
                        new ByteArrayInputStream(contentWithHeader),
                        contentWithHeader.length, uncompressed_size);
        return compressedResource;
    }

}
