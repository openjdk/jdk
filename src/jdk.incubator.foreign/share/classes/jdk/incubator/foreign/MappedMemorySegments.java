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

package jdk.incubator.foreign;

import jdk.internal.foreign.MappedMemorySegmentImpl;

import java.nio.MappedByteBuffer;
import java.util.Objects;

/**
 * This class provides capabilities to manipulate mapped memory segments, such as {@link #force(MemorySegment)},
 * and {@link #load(MemorySegment)}. The methods in these class are suitable replacements for some of the
 * functionality in the {@link java.nio.MappedByteBuffer} class. Note that, while it is possible to map a segment
 * into a byte buffer (see {@link MemorySegment#asByteBuffer()}), and call e.g. {@link MappedByteBuffer#force()} that way,
 * this can only be done when the source segment is small enough, due to the size limitation inherent to the
 * ByteBuffer API.
 * <p>
 * Clients requiring sophisticated, low-level control over mapped memory segments, should consider writing
 * custom mapped memory segment factories; using JNI, e.g. on Linux, it is possible to call {@code mmap}
 * with the desired parameters; the returned address can be easily wrapped into a memory segment, using
 * {@link MemoryAddress#ofLong(long)} and {@link MemoryAddress#asSegmentRestricted(long, Runnable, Object)}.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @implNote
 * The behavior of some the methods in this class (see {@link #load(MemorySegment)}, {@link #unload(MemorySegment)} and
 * {@link #isLoaded(MemorySegment)}) is highly platform-dependent; as a result, calling these methods might
 * be a no-op on certain platforms.
 */
public final class MappedMemorySegments {
    private MappedMemorySegments() {
        // no thanks
    }

    /**
     * Tells whether or not the contents of the given segment is resident in physical
     * memory.
     *
     * <p> A return value of {@code true} implies that it is highly likely
     * that all of the data in the given segment is resident in physical memory and
     * may therefore be accessed without incurring any virtual-memory page
     * faults or I/O operations.  A return value of {@code false} does not
     * necessarily imply that the segment's content is not resident in physical
     * memory.
     *
     * <p> The returned value is a hint, rather than a guarantee, because the
     * underlying operating system may have paged out some of the segment's data
     * by the time that an invocation of this method returns.  </p>
     *
     * @param segment the segment whose contents are to be tested.
     * @return  {@code true} if it is likely that the contents of the given segment
     *          is resident in physical memory
     *
     * @throws IllegalStateException if the given segment is not alive, or if the given segment is confined
     * and this method is called from a thread other than the segment's owner thread.
     * @throws UnsupportedOperationException if the given segment is not a mapped memory segment, e.g. if
     * {@code segment.isMapped() == false}.
     */
    public static boolean isLoaded(MemorySegment segment) {
        return toMappedSegment(segment).isLoaded();
    }

    /**
     * Loads the contents of the given segment into physical memory.
     *
     * <p> This method makes a best effort to ensure that, when it returns,
     * this contents of the given segment is resident in physical memory.  Invoking this
     * method may cause some number of page faults and I/O operations to
     * occur. </p>
     *
     * @param segment the segment whose contents are to be loaded.
     *
     * @throws IllegalStateException if the given segment is not alive, or if the given segment is confined
     * and this method is called from a thread other than the segment's owner thread.
     * @throws UnsupportedOperationException if the given segment is not a mapped memory segment, e.g. if
     * {@code segment.isMapped() == false}.
     */
    public static void load(MemorySegment segment) {
        toMappedSegment(segment).load();
    }

    /**
     * Unloads the contents of the given segment from physical memory.
     *
     * <p> This method makes a best effort to ensure that the contents of the given segment are
     * are no longer resident in physical memory. Accessing this segment's contents
     * after invoking this method may cause some number of page faults and I/O operations to
     * occur (as this segment's contents might need to be paged back in). </p>
     *
     * @param segment the segment whose contents are to be unloaded.
     *
     * @throws IllegalStateException if the given segment is not alive, or if the given segment is confined
     * and this method is called from a thread other than the segment's owner thread.
     * @throws UnsupportedOperationException if the given segment is not a mapped memory segment, e.g. if
     * {@code segment.isMapped() == false}.
     */
    public static void unload(MemorySegment segment) {
        toMappedSegment(segment).unload();
    }

    /**
     * Forces any changes made to the contents of the given segment to be written to the
     * storage device described by the mapped segment's file descriptor.
     *
     * <p> If this mapping's file descriptor resides on a local storage
     * device then when this method returns it is guaranteed that all changes
     * made to the segment since it was created, or since this method was last
     * invoked, will have been written to that device.
     *
     * <p> If this mapping's file descriptor does not reside on a local device then no such guarantee
     * is made.
     *
     * <p> If the given segment was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then
     * invoking this method may have no effect. In particular, the
     * method has no effect for segments mapped in read-only or private
     * mapping modes. This method may or may not have an effect for
     * implementation-specific mapping modes.
     * </p>
     *
     * @param segment the segment whose contents are to be written to the storage device described by the
     *                segment's file descriptor.
     *
     * @throws IllegalStateException if the given segment is not alive, or if the given segment is confined
     * and this method is called from a thread other than the segment's owner thread.
     * @throws UnsupportedOperationException if the given segment is not a mapped memory segment, e.g. if
     * {@code segment.isMapped() == false}.
     */
    public static void force(MemorySegment segment) {
        toMappedSegment(segment).force();
    }

    static MappedMemorySegmentImpl toMappedSegment(MemorySegment segment) {
        Objects.requireNonNull(segment);
        if (segment instanceof MappedMemorySegmentImpl) {
            return (MappedMemorySegmentImpl)segment;
        } else {
            throw new UnsupportedOperationException("Not a mapped memory segment");
        }
    }
}
