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
package java.nio.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A file-based lines spliterator, leveraging a shared mapped byte buffer and
 * associated file channel, covering lines of a file for character encodings
 * where line feed characters can be easily identified from character encoded
 * bytes.
 *
 * <p>
 * When the root spliterator is first split a mapped byte buffer will be created
 * over the file for it's size that was observed when the stream was created.
 * Thus a mapped byte buffer is only required for parallel stream execution.
 * Sub-spliterators will share that mapped byte buffer.  Splitting will use the
 * mapped byte buffer to find the closest line feed characters(s) to the left or
 * right of the mid-point of covered range of bytes of the file.  If a line feed
 * is found then the spliterator is split with returned spliterator containing
 * the identified line feed characters(s) at the end of it's covered range of
 * bytes.
 *
 * <p>
 * Traversing will create a buffered reader, derived from the file channel, for
 * the range of bytes of the file.  The lines are then read from that buffered
 * reader.  Once traversing commences no further splitting can be performed and
 * the reference to the mapped byte buffer will be set to null.
 */
final class FileChannelLinesSpliterator implements Spliterator<String> {

    static final Set<String> SUPPORTED_CHARSET_NAMES;
    static {
        SUPPORTED_CHARSET_NAMES = new HashSet<>();
        SUPPORTED_CHARSET_NAMES.add(StandardCharsets.UTF_8.name());
        SUPPORTED_CHARSET_NAMES.add(StandardCharsets.ISO_8859_1.name());
        SUPPORTED_CHARSET_NAMES.add(StandardCharsets.US_ASCII.name());
    }

    private final FileChannel fc;
    private final Charset cs;
    private int index;
    private final int fence;

    // Null before first split, non-null when splitting, null when traversing
    private ByteBuffer buffer;
    // Non-null when traversing
    private BufferedReader reader;

    FileChannelLinesSpliterator(FileChannel fc, Charset cs, int index, int fence) {
        this.fc = fc;
        this.cs = cs;
        this.index = index;
        this.fence = fence;
    }

    private FileChannelLinesSpliterator(FileChannel fc, Charset cs, int index, int fence, ByteBuffer buffer) {
        this.fc = fc;
        this.buffer = buffer;
        this.cs = cs;
        this.index = index;
        this.fence = fence;
    }

    @Override
    public boolean tryAdvance(Consumer<? super String> action) {
        String line = readLine();
        if (line != null) {
            action.accept(line);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void forEachRemaining(Consumer<? super String> action) {
        String line;
        while ((line = readLine()) != null) {
            action.accept(line);
        }
    }

    private BufferedReader getBufferedReader() {
        /**
         * A readable byte channel that reads bytes from an underlying
         * file channel over a specified range.
         */
        ReadableByteChannel rrbc = new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
                int bytesToRead = fence - index;
                if (bytesToRead == 0)
                    return -1;

                int bytesRead;
                if (bytesToRead < dst.remaining()) {
                    // The number of bytes to read is less than remaining
                    // bytes in the buffer
                    // Snapshot the limit, reduce it, read, then restore
                    int oldLimit = dst.limit();
                    dst.limit(dst.position() + bytesToRead);
                    bytesRead = fc.read(dst, index);
                    dst.limit(oldLimit);
                } else {
                    bytesRead = fc.read(dst, index);
                }
                if (bytesRead == -1) {
                    index = fence;
                    return bytesRead;
                }

                index += bytesRead;
                return bytesRead;
            }

            @Override
            public boolean isOpen() {
                return fc.isOpen();
            }

            @Override
            public void close() throws IOException {
                fc.close();
            }
        };
        return new BufferedReader(Channels.newReader(rrbc, cs.newDecoder(), -1));
    }

    private String readLine() {
        if (reader == null) {
            reader = getBufferedReader();
            buffer = null;
        }

        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ByteBuffer getMappedByteBuffer() {
        // TODO can the mapped byte buffer be explicitly unmapped?
        // It's possible, via a shared-secret mechanism, when either
        // 1) the spliterator starts traversing, although traversal can
        //    happen concurrently for mulitple spliterators, so care is
        //    needed in this case; or
        // 2) when the stream is closed using some shared holder to pass
        //    the mapped byte buffer when it is created.
        try {
            return fc.map(FileChannel.MapMode.READ_ONLY, 0, fence);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Spliterator<String> trySplit() {
        // Cannot split after partial traverse
        if (reader != null)
            return null;

        ByteBuffer b;
        if ((b = buffer) == null) {
            b = buffer = getMappedByteBuffer();
        }

        final int hi = fence, lo = index;

        // Check if line separator hits the mid point
        int mid = (lo + hi) >>> 1;
        int c =  b.get(mid);
        if (c == '\n') {
            mid++;
        } else if (c == '\r') {
            // Check if a line separator of "\r\n"
            if (++mid < hi && b.get(mid) == '\n') {
                mid++;
            }
        } else {
            // TODO give up after a certain distance from the mid point?
            // Scan to the left and right of the mid point
            int midL = mid - 1;
            int midR = mid + 1;
            mid = 0;
            while (midL > lo && midR < hi) {
                // Sample to the left
                c = b.get(midL--);
                if (c == '\n' || c == '\r') {
                    // If c is "\r" then no need to check for "\r\n"
                    // since the subsequent value was previously checked
                    mid = midL + 2;
                    break;
                }

                // Sample to the right
                c = b.get(midR++);
                if (c == '\n' || c == '\r') {
                    mid = midR;
                    // Check if line-separator is "\r\n"
                    if (c == '\r' && mid < hi && b.get(mid) == '\n') {
                        mid++;
                    }
                    break;
                }
            }
        }

        // The left spliterator will have the line-separator at the end
        return (mid > lo && mid < hi)
               ? new FileChannelLinesSpliterator(fc, cs, lo, index = mid, b)
               : null;
    }

    @Override
    public long estimateSize() {
        // Use the number of bytes as an estimate.
        // We could divide by a constant that is the average number of
        // characters per-line, but that constant will be factored out.
        return fence - index;
    }

    @Override
    public long getExactSizeIfKnown() {
        return -1;
    }

    @Override
    public int characteristics() {
        return Spliterator.ORDERED | Spliterator.NONNULL;
    }
}
