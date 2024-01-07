/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8281412
 * @summary Test FileChannel::map to MemorySegment with custom file channel
 * @run testng/othervm MapToMemorySegmentTest
 */

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class MapToMemorySegmentTest {

    static Path tempPath;

    static {
        try {
            File file = File.createTempFile("foo", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testCustomFileChannel() throws IOException {
        var arena = Arena.ofConfined();
        var fc = FileChannel.open(tempPath, StandardOpenOption.WRITE, StandardOpenOption.READ);
        var fileChannel = new CustomFileChannel(fc);
        try (arena; fileChannel){
            fileChannel.map(FileChannel.MapMode.READ_WRITE, 1L, 10L, arena);
        }
    }

    @Test
    public void testCustomFileChannelOverride() throws IOException {
        var arena = Arena.ofConfined();
        var fc = FileChannel.open(tempPath, StandardOpenOption.WRITE, StandardOpenOption.READ);
        var fileChannel = new CustomFileChannelOverride(fc);
        try (arena; fileChannel){
            fileChannel.map(FileChannel.MapMode.READ_WRITE, 1L, 10L, arena);
        }
    }

    static class CustomFileChannel extends FileChannel {
        FileChannel fc;

        public CustomFileChannel(FileChannel fc) {
            this.fc = fc;
        }

        public int read(ByteBuffer dst) throws IOException {
            return fc.read(dst);
        }

        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return fc.read(dsts, offset, length);
        }

        public int write(ByteBuffer src) throws IOException {
            return fc.write(src);
        }

        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return fc.write(srcs, offset, length);
        }

        public long position() throws IOException {
            return fc.position();
        }

        public FileChannel position(long newPosition) throws IOException {
            return fc.position(newPosition);
        }

        public long size() throws IOException {
            return fc.size();
        }

        public FileChannel truncate(long size) throws IOException {
            return fc.truncate(size);
        }

        public void force(boolean metaData) throws IOException {
            this.fc.force(metaData);
        }

        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            return fc.transferTo(position, count, target);
        }

        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            return fc.transferFrom(src, position, count);
        }

        public int read(ByteBuffer dst, long position) throws IOException {
            return fc.read(dst, position);
        }

        public int write(ByteBuffer src, long position) throws IOException {
            return fc.write(src, position);
        }

        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return fc.map(mode, position, size);
        }

        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return fc.lock(position, size, shared);
        }

        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return fc.tryLock(position , size, shared);
        }

        protected void implCloseChannel() throws IOException {
            fc.close();
        }
    }

    static class CustomFileChannelOverride extends CustomFileChannel {

        public CustomFileChannelOverride(FileChannel fc) { super(fc); }

        @Override
        public MemorySegment map(MapMode mode, long offset, long size, Arena arena)
                throws IOException, UnsupportedOperationException
        {
            return fc.map(mode, offset, size, arena);
        }
    }
}
