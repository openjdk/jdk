/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.stream.IntStream;
import jdk.internal.jimage.decompressor.Decompressor;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class BasicImageReader implements AutoCloseable {
    private static boolean isSystemProperty(String key, String value, String def) {
        // No lambdas during bootstrap
        return AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return value.equals(System.getProperty(key, def));
                }
            });
    }

    static private final boolean IS_64_BIT =
            isSystemProperty("sun.arch.data.model", "64", "32");
    static private final boolean USE_JVM_MAP =
            isSystemProperty("jdk.image.use.jvm.map", "true", "true");
    static private final boolean MAP_ALL =
            isSystemProperty("jdk.image.map.all", "true", IS_64_BIT ? "true" : "false");

    private final String name;
    private final ByteOrder byteOrder;
    private final Path imagePath;
    private final ByteBuffer memoryMap;
    private final FileChannel channel;
    private final ImageHeader header;
    private final long indexSize;
    private final IntBuffer redirect;
    private final IntBuffer offsets;
    private final ByteBuffer locations;
    private final ByteBuffer strings;
    private final ImageStringsReader stringsReader;
    private final Decompressor decompressor;

    protected BasicImageReader(Path path, ByteOrder byteOrder)
            throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(byteOrder);
        this.name = path.toString();
        this.byteOrder = byteOrder;
        imagePath = path;

        ByteBuffer map;

        if (USE_JVM_MAP && BasicImageReader.class.getClassLoader() == null) {
            // Check to see if the jvm has opened the file using libjimage
            // native entry when loading the image for this runtime
            map = NativeImageBuffer.getNativeMap(name);
         } else {
            map = null;
        }

        // Open the file only if no memory map yet or is 32 bit jvm
        channel = map != null && MAP_ALL ? null :
                  FileChannel.open(imagePath, StandardOpenOption.READ);

        // If no memory map yet and 64 bit jvm then memory map entire file
        if (MAP_ALL && map == null) {
            map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        // Assume we have a memory map to read image file header
        ByteBuffer headerBuffer = map;
        int headerSize = ImageHeader.getHeaderSize();

        // If no memory map then read header from image file
        if (map == null) {
            headerBuffer = ByteBuffer.allocateDirect(headerSize);
            channel.read(headerBuffer, 0L);
            headerBuffer.rewind();
        }

        // Interpret the image file header
        header = readHeader(intBuffer(headerBuffer, 0, headerSize));
        indexSize = header.getIndexSize();

        // If no memory map yet then must be 32 bit jvm not previously mapped
        if (map == null) {
            // Just map the image index
            map = channel.map(FileChannel.MapMode.READ_ONLY, 0, indexSize);
        }

        memoryMap = map.asReadOnlyBuffer();

        // Interpret the image index
        redirect = intBuffer(memoryMap, header.getRedirectOffset(), header.getRedirectSize());
        offsets = intBuffer(memoryMap, header.getOffsetsOffset(), header.getOffsetsSize());
        locations = slice(memoryMap, header.getLocationsOffset(), header.getLocationsSize());
        strings = slice(memoryMap, header.getStringsOffset(), header.getStringsSize());

        stringsReader = new ImageStringsReader(this);
        decompressor = new Decompressor();
    }

    protected BasicImageReader(Path imagePath) throws IOException {
        this(imagePath, ByteOrder.nativeOrder());
    }

    public static BasicImageReader open(Path imagePath) throws IOException {
        return new BasicImageReader(imagePath, ByteOrder.nativeOrder());
    }

    public ImageHeader getHeader() {
        return header;
    }

    private ImageHeader readHeader(IntBuffer buffer) throws IOException {
        ImageHeader result = ImageHeader.readFrom(buffer);

        if (result.getMagic() != ImageHeader.MAGIC) {
            throw new IOException("\"" + name + "\" is not an image file");
        }

        if (result.getMajorVersion() != ImageHeader.MAJOR_VERSION ||
            result.getMinorVersion() != ImageHeader.MINOR_VERSION) {
            throw new IOException("The image file \"" + name + "\" is not the correct version");
        }

        return result;
    }

    private static ByteBuffer slice(ByteBuffer buffer, int position, int capacity) {
        // Note that this is the only limit and position manipulation of
        // BasicImageReader private ByteBuffers.  The synchronize could be avoided
        // by cloning the buffer to make a local copy, but at the cost of creating
        // a new object.
        synchronized(buffer) {
            buffer.limit(position + capacity);
            buffer.position(position);
            return buffer.slice();
        }
    }

    private IntBuffer intBuffer(ByteBuffer buffer, int offset, int size) {
        return slice(buffer, offset, size).order(byteOrder).asIntBuffer();
    }

    public static void releaseByteBuffer(ByteBuffer buffer) {
        if (!MAP_ALL) {
            ImageBufferCache.releaseBuffer(buffer);
        }
    }

    public String getName() {
        return name;
    }

    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public Path getImagePath() {
        return imagePath;
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    public ImageStringsReader getStrings() {
        return stringsReader;
    }

    public ImageLocation findLocation(String mn, String rn) {
        return findLocation("/" + mn + "/" + rn);
    }

    public synchronized ImageLocation findLocation(String name) {
        // Details of the algorithm used here can be found in
        // jdk.tools.jlink.internal.PerfectHashBuilder.
        byte[] bytes = ImageStringsReader.mutf8FromString(name);
        int count = header.getTableLength();
        int index = redirect.get(ImageStringsReader.hashCode(bytes) % count);

        if (index < 0) {
            // index is twos complement of location attributes index.
            index = -index - 1;
        } else if (index > 0) {
            // index is hash seed needed to compute location attributes index.
            index = ImageStringsReader.hashCode(bytes, index) % count;
        } else {
            // No entry.
            return null;
        }

        long[] attributes = getAttributes(offsets.get(index));

        ImageLocation imageLocation = new ImageLocation(attributes, stringsReader);

        if (!imageLocation.verify(name)) {
            return null;
        }

        return imageLocation;
    }

    public String[] getEntryNames() {
        int[] attributeOffsets = new int[offsets.capacity()];
        offsets.get(attributeOffsets);
        return IntStream.of(attributeOffsets)
                        .filter(o -> o != 0)
                        .mapToObj(o -> ImageLocation.readFrom(this, o).getFullName())
                        .sorted()
                        .toArray(String[]::new);
    }

    ImageLocation getLocation(int offset) {
        return ImageLocation.readFrom(this, offset);
    }

    public long[] getAttributes(int offset) {
        ByteBuffer buffer = slice(locations, offset, locations.limit() - offset);
        return ImageLocation.decompress(buffer);
    }

    public String getString(int offset) {
        ByteBuffer buffer = slice(strings, offset, strings.limit() - offset);
        return ImageStringsReader.stringFromByteBuffer(buffer);
    }

    private byte[] getBufferBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);

        return bytes;
    }

    private ByteBuffer readBuffer(long offset, long size) {
        if (offset < 0 || Integer.MAX_VALUE <= offset) {
            throw new IndexOutOfBoundsException("offset");
        }

        if (size < 0 || Integer.MAX_VALUE <= size) {
            throw new IndexOutOfBoundsException("size");
        }

        if (MAP_ALL) {
            ByteBuffer buffer = slice(memoryMap, (int)offset, (int)size);
            buffer.order(ByteOrder.BIG_ENDIAN);

            return buffer;
        } else {
            if (channel == null) {
                throw new InternalError("Image file channel not open");
            }

            ByteBuffer buffer = ImageBufferCache.getBuffer(size);
            int read;
            try {
                read = channel.read(buffer, offset);
                buffer.rewind();
            } catch (IOException ex) {
                ImageBufferCache.releaseBuffer(buffer);
                throw new RuntimeException(ex);
            }

            if (read != size) {
                ImageBufferCache.releaseBuffer(buffer);
                throw new RuntimeException("Short read: " + read +
                                           " instead of " + size + " bytes");
            }

            return buffer;
        }
    }

    public byte[] getResource(String name) {
        ImageLocation location = findLocation(name);

        return location != null ? getResource(location) : null;
    }

    public byte[] getResource(ImageLocation loc) {
        ByteBuffer buffer = getResourceBuffer(loc);

        if (buffer != null) {
            byte[] bytes = getBufferBytes(buffer);
            ImageBufferCache.releaseBuffer(buffer);

            return bytes;
        }

        return null;
    }

    public ByteBuffer getResourceBuffer(ImageLocation loc) {
        long offset = loc.getContentOffset() + indexSize;
        long compressedSize = loc.getCompressedSize();
        long uncompressedSize = loc.getUncompressedSize();

        if (compressedSize < 0 || Integer.MAX_VALUE < compressedSize) {
            throw new IndexOutOfBoundsException("Compressed size");
        }

        if (uncompressedSize < 0 || Integer.MAX_VALUE < uncompressedSize) {
            throw new IndexOutOfBoundsException("Uncompressed size");
        }

        if (compressedSize == 0) {
            return readBuffer(offset, uncompressedSize);
        } else {
            ByteBuffer buffer = readBuffer(offset, compressedSize);

            if (buffer != null) {
                byte[] bytesIn = getBufferBytes(buffer);
                ImageBufferCache.releaseBuffer(buffer);
                byte[] bytesOut;

                try {
                    bytesOut = decompressor.decompressResource(byteOrder,
                            (int strOffset) -> getString(strOffset), bytesIn);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                return ByteBuffer.wrap(bytesOut);
            }
        }

        return null;
    }

    public InputStream getResourceStream(ImageLocation loc) {
        byte[] bytes = getResource(loc);

        return new ByteArrayInputStream(bytes);
    }
}
