/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.ref.CleanerFactory;
import sun.nio.cs.ISO_8859_1;
import sun.nio.cs.UTF_8;
import sun.nio.cs.US_ASCII;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A file-based lines spliterator, leveraging a shared mapped memory segment and
 * associated file channel, covering lines of a file for character encodings
 * where line feed characters can be easily identified from character encoded
 * bytes.
 *
 * <p>
 * When the root spliterator is first split a mapped memory segment will be created
 * over the file for its size that was observed when the stream was created.
 * Thus, a mapped memory segment is only required for parallel stream execution.
 * Sub-spliterators will share that mapped memory segment.  Splitting will use the
 * mapped memory segment to find the closest line feed characters(s) to the left or
 * right of the mid-point of covered range of bytes of the file.  If a line feed
 * is found then the spliterator is split with returned spliterator containing
 * the identified line feed characters(s) at the end of its covered range of
 * bytes.
 *
 * <p>
 * Traversing will create a buffered reader, derived from the file channel, for
 * the range of bytes of the file.  The lines are then read from that buffered
 * reader.  Once traversing commences no further splitting can be performed and
 * the reference to the mapped byte buffer will be set to null.
 */
final class FileChannelLinesSpliterator implements Spliterator<String> {

    static final Set<Charset> SUPPORTED_CHARSETS = Set.of(
        UTF_8.INSTANCE,
        ISO_8859_1.INSTANCE,
        US_ASCII.INSTANCE
    );

    // This state is shared across all splits
    private final SharedState ss;
    private final int fence;
    private int index;

    // Non-null when traversing
    private BufferedReader reader;

    FileChannelLinesSpliterator(FileChannel fc,
                                Charset cs,
                                int fence) {
        this.ss = new SharedState(this, fc, cs);
        this.index = 0;
        this.fence = fence;
    }

    private FileChannelLinesSpliterator(SharedState ss,
                                        int index,
                                        int fence) {
        this.ss = ss;
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
        /*
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
                    bytesRead = ss.fc.read(dst, index);
                    dst.limit(oldLimit);
                } else {
                    bytesRead = ss.fc.read(dst, index);
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
                return ss.fc.isOpen();
            }

            @Override
            public void close() throws IOException {
                ss.fc.close();
            }
        };
        return new BufferedReader(Channels.newReader(rrbc, ss.cs.newDecoder(), -1));
    }

    private String readLine() {
        if (reader == null) {
            reader = getBufferedReader();
        }

        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Override
    public Spliterator<String> trySplit() {
        // Cannot split after partial traverse
        if (reader != null)
            return null;

        try {
            // Only unmapped for the original Spliterator
            MemorySegment s = ss.mapIfUnmapped(fence);
            final int hi = fence, lo = index;

            // Check if line separator hits the mid point
            int mid = (lo + hi) >>> 1;
            int c = s.get(ValueLayout.JAVA_BYTE, mid);
            if (c == '\n') {
                mid++;
            } else if (c == '\r') {
                // Check if a line separator of "\r\n"
                if (++mid < hi && s.get(ValueLayout.JAVA_BYTE, mid) == '\n') {
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
                    c = s.get(ValueLayout.JAVA_BYTE, midL--);
                    if (c == '\n' || c == '\r') {
                        // If c is "\r" then no need to check for "\r\n"
                        // since the subsequent value was previously checked
                        mid = midL + 2;
                        break;
                    }

                    // Sample to the right
                    c = s.get(ValueLayout.JAVA_BYTE, midR++);
                    if (c == '\n' || c == '\r') {
                        mid = midR;
                        // Check if line-separator is "\r\n"
                        if (c == '\r' && mid < hi && s.get(ValueLayout.JAVA_BYTE, mid) == '\n') {
                            mid++;
                        }
                        break;
                    }
                }
            }

            // The left spliterator will have the line-separator at the end
            return (mid > lo && mid < hi)
                    ? new FileChannelLinesSpliterator(ss, lo, index = mid)
                    : null;
        } finally {
            // Make sure the underlying `original` remains strongly referenced until the segment is fully used
            Reference.reachabilityFence(ss.original);
        }
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

    // Only called on the original root Spliterator and not on splits
    void close() {
        ss.cleanupAction.clean();
    }

    private static final class SharedState {
        // Holds a reference to the original (top-most) spliterator to ensure the original's Cleaner
        // is invoked only once itself _and all its splits_ are no longer phantom reachable.
        final FileChannelLinesSpliterator original;
        final Cleaner.Cleanable cleanupAction;
        final FileChannel fc;
        final Charset cs;
        final Arena arena;

        // Null before first split, non-null otherwise
        MemorySegment segment;

        SharedState(FileChannelLinesSpliterator original,
                    FileChannel fc,
                    Charset cs) {
            this.original = original;
            this.fc = fc;
            this.cs = cs;
            this.arena = Arena.ofShared();
            this.cleanupAction = cleanupAction();
        }

        MemorySegment mapIfUnmapped(int fence) {
            if (segment != null) {
                return segment;
            }
            try {
                return segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, fence, arena);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Cleaner.Cleanable cleanupAction() {
            record Cleanup(Arena arena) implements Runnable {
                @Override public void run() { arena.close(); }
            }
            return CleanerFactory.cleaner().register(original, new Cleanup(arena));
        }

    }

}
