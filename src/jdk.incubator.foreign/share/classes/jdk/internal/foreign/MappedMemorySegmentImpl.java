/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import jdk.incubator.foreign.MemorySegment;
import jdk.internal.access.foreign.UnmapperProxy;
import jdk.internal.misc.ScopedMemoryAccess;
import sun.nio.ch.FileChannelImpl;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation for a mapped memory segments. A mapped memory segment is a native memory segment, which
 * additionally features an {@link UnmapperProxy} object. This object provides detailed information about the
 * memory mapped segment, such as the file descriptor associated with the mapping. This information is crucial
 * in order to correctly reconstruct a byte buffer object from the segment (see {@link #makeByteBuffer()}).
 */
public class MappedMemorySegmentImpl extends NativeMemorySegmentImpl {

    private final UnmapperProxy unmapper;

    static ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    MappedMemorySegmentImpl(long min, UnmapperProxy unmapper, long length, int mask, MemoryScope scope) {
        super(min, length, mask, scope);
        this.unmapper = unmapper;
    }

    @Override
    ByteBuffer makeByteBuffer() {
        return nioAccess.newMappedByteBuffer(unmapper, min, (int)length, null, this);
    }

    @Override
    MappedMemorySegmentImpl dup(long offset, long size, int mask, MemoryScope scope) {
        return new MappedMemorySegmentImpl(min + offset, unmapper, size, mask, scope);
    }

    // mapped segment methods


    @Override
    public MappedMemorySegmentImpl asSlice(long offset, long newSize) {
        return (MappedMemorySegmentImpl)super.asSlice(offset, newSize);
    }

    @Override
    public MappedMemorySegmentImpl withAccessModes(int accessModes) {
        return (MappedMemorySegmentImpl)super.withAccessModes(accessModes);
    }

    @Override
    public boolean isMapped() {
        return true;
    }

    // support for mapped segments

    public MemorySegment segment() {
        return MappedMemorySegmentImpl.this;
    }

    public void load() {
        SCOPED_MEMORY_ACCESS.load(scope, min, unmapper.isSync(), length);
    }

    public void unload() {
        SCOPED_MEMORY_ACCESS.unload(scope, min, unmapper.isSync(), length);
    }

    public boolean isLoaded() {
        return SCOPED_MEMORY_ACCESS.isLoaded(scope, min, unmapper.isSync(), length);
    }

    public void force() {
        SCOPED_MEMORY_ACCESS.force(scope, unmapper.fileDescriptor(), min, unmapper.isSync(), 0, length);
    }

    // factories

    public static MemorySegment makeMappedSegment(Path path, long bytesOffset, long bytesSize, FileChannel.MapMode mapMode) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(mapMode);
        if (bytesSize < 0) throw new IllegalArgumentException("Requested bytes size must be >= 0.");
        if (bytesOffset < 0) throw new IllegalArgumentException("Requested bytes offset must be >= 0.");
        FileSystem fs = path.getFileSystem();
        if (fs != FileSystems.getDefault() ||
                fs.getClass().getModule() != Object.class.getModule()) {
            throw new IllegalArgumentException("Unsupported file system");
        }
        try (FileChannel channelImpl = FileChannel.open(path, openOptions(mapMode))) {
            UnmapperProxy unmapperProxy = ((FileChannelImpl)channelImpl).mapInternal(mapMode, bytesOffset, bytesSize);
            int modes = defaultAccessModes(bytesSize);
            if (mapMode == FileChannel.MapMode.READ_ONLY) {
                modes &= ~WRITE;
            }
            if (unmapperProxy != null) {
                MemoryScope scope = MemoryScope.createConfined(null, unmapperProxy::unmap, null);
                return new MappedMemorySegmentImpl(unmapperProxy.address(), unmapperProxy, bytesSize,
                        modes, scope);
            } else {
                return new EmptyMappedMemorySegmentImpl(modes);
            }
        }
    }

    private static OpenOption[] openOptions(FileChannel.MapMode mapMode) {
        if (mapMode == FileChannel.MapMode.READ_ONLY) {
            return new OpenOption[] { StandardOpenOption.READ };
        } else if (mapMode == FileChannel.MapMode.READ_WRITE || mapMode == FileChannel.MapMode.PRIVATE) {
            return new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE };
        } else {
            throw new UnsupportedOperationException("Unsupported map mode: " + mapMode);
        }
    }

    static class EmptyMappedMemorySegmentImpl extends MappedMemorySegmentImpl {

        public EmptyMappedMemorySegmentImpl(int modes) {
            super(0, null, 0, modes,
                    MemoryScope.createConfined(null, MemoryScope.DUMMY_CLEANUP_ACTION, null));
        }

        @Override
        public void load() {
            // do nothing
        }

        @Override
        public void unload() {
            // do nothing
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public void force() {
            // do nothing
        }
    };
}
