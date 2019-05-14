/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import java.io.FileDescriptor;
import java.lang.ref.Reference;
import java.util.Objects;
import jdk.internal.misc.Unsafe;


/**
 * A direct byte buffer whose content is a memory-mapped region of a file.
 *
 * <p> Mapped byte buffers are created via the {@link
 * java.nio.channels.FileChannel#map FileChannel.map} method.  This class
 * extends the {@link ByteBuffer} class with operations that are specific to
 * memory-mapped file regions.
 *
 * <p> A mapped byte buffer and the file mapping that it represents remain
 * valid until the buffer itself is garbage-collected.
 *
 * <p> The content of a mapped byte buffer can change at any time, for example
 * if the content of the corresponding region of the mapped file is changed by
 * this program or another.  Whether or not such changes occur, and when they
 * occur, is operating-system dependent and therefore unspecified.
 *
 * <a id="inaccess"></a><p> All or part of a mapped byte buffer may become
 * inaccessible at any time, for example if the mapped file is truncated.  An
 * attempt to access an inaccessible region of a mapped byte buffer will not
 * change the buffer's content and will cause an unspecified exception to be
 * thrown either at the time of the access or at some later time.  It is
 * therefore strongly recommended that appropriate precautions be taken to
 * avoid the manipulation of a mapped file by this program, or by a
 * concurrently running program, except to read or write the file's content.
 *
 * <p> Mapped byte buffers otherwise behave no differently than ordinary direct
 * byte buffers. </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class MappedByteBuffer
    extends ByteBuffer
{

    // This is a little bit backwards: By rights MappedByteBuffer should be a
    // subclass of DirectByteBuffer, but to keep the spec clear and simple, and
    // for optimization purposes, it's easier to do it the other way around.
    // This works because DirectByteBuffer is a package-private class.

    // For mapped buffers, a FileDescriptor that may be used for mapping
    // operations if valid; null if the buffer is not mapped.
    private final FileDescriptor fd;

    // This should only be invoked by the DirectByteBuffer constructors
    //
    MappedByteBuffer(int mark, int pos, int lim, int cap, // package-private
                     FileDescriptor fd)
    {
        super(mark, pos, lim, cap);
        this.fd = fd;
    }

    MappedByteBuffer(int mark, int pos, int lim, int cap) { // package-private
        super(mark, pos, lim, cap);
        this.fd = null;
    }

    // Returns the distance (in bytes) of the buffer start from the
    // largest page aligned address of the mapping less than or equal
    // to the start address.
    private long mappingOffset() {
        return mappingOffset(0);
    }

    // Returns the distance (in bytes) of the buffer element
    // identified by index from the largest page aligned address of
    // the mapping less than or equal to the element address. Computed
    // each time to avoid storing in every direct buffer.
    private long mappingOffset(int index) {
        int ps = Bits.pageSize();
        long indexAddress = address + index;
        long baseAddress = (indexAddress & ~(ps-1));
        return indexAddress - baseAddress;
    }

    // Given an offset previously obtained from calling
    // mappingOffset() returns the largest page aligned address of the
    // mapping less than or equal to the buffer start address.
    private long mappingAddress(long mappingOffset) {
        return mappingAddress(mappingOffset, 0);
    }

    // Given an offset previously otained from calling
    // mappingOffset(index) returns the largest page aligned address
    // of the mapping less than or equal to the address of the buffer
    // element identified by index.
    private long mappingAddress(long mappingOffset, long index) {
        long indexAddress = address + index;
        return indexAddress - mappingOffset;
    }

    // given a mappingOffset previously otained from calling
    // mappingOffset() return that offset added to the buffer
    // capacity.
    private long mappingLength(long mappingOffset) {
        return mappingLength(mappingOffset, (long)capacity());
    }

    // given a mappingOffset previously otained from calling
    // mappingOffset(index) return that offset added to the supplied
    // length.
    private long mappingLength(long mappingOffset, long length) {
        return length + mappingOffset;
    }

    /**
     * Tells whether or not this buffer's content is resident in physical
     * memory.
     *
     * <p> A return value of {@code true} implies that it is highly likely
     * that all of the data in this buffer is resident in physical memory and
     * may therefore be accessed without incurring any virtual-memory page
     * faults or I/O operations.  A return value of {@code false} does not
     * necessarily imply that the buffer's content is not resident in physical
     * memory.
     *
     * <p> The returned value is a hint, rather than a guarantee, because the
     * underlying operating system may have paged out some of the buffer's data
     * by the time that an invocation of this method returns.  </p>
     *
     * @return  {@code true} if it is likely that this buffer's content
     *          is resident in physical memory
     */
    public final boolean isLoaded() {
        if (fd == null) {
            return true;
        }
        if ((address == 0) || (capacity() == 0))
            return true;
        long offset = mappingOffset();
        long length = mappingLength(offset);
        return isLoaded0(mappingAddress(offset), length, Bits.pageCount(length));
    }

    // not used, but a potential target for a store, see load() for details.
    private static byte unused;

    /**
     * Loads this buffer's content into physical memory.
     *
     * <p> This method makes a best effort to ensure that, when it returns,
     * this buffer's content is resident in physical memory.  Invoking this
     * method may cause some number of page faults and I/O operations to
     * occur. </p>
     *
     * @return  This buffer
     */
    public final MappedByteBuffer load() {
        if (fd == null) {
            return this;
        }
        if ((address == 0) || (capacity() == 0))
            return this;
        long offset = mappingOffset();
        long length = mappingLength(offset);
        load0(mappingAddress(offset), length);

        // Read a byte from each page to bring it into memory. A checksum
        // is computed as we go along to prevent the compiler from otherwise
        // considering the loop as dead code.
        Unsafe unsafe = Unsafe.getUnsafe();
        int ps = Bits.pageSize();
        int count = Bits.pageCount(length);
        long a = mappingAddress(offset);
        byte x = 0;
        try {
            for (int i=0; i<count; i++) {
                // TODO consider changing to getByteOpaque thus avoiding
                // dead code elimination and the need to calculate a checksum
                x ^= unsafe.getByte(a);
                a += ps;
            }
        } finally {
            Reference.reachabilityFence(this);
        }
        if (unused != 0)
            unused = x;

        return this;
    }

    /**
     * Forces any changes made to this buffer's content to be written to the
     * storage device containing the mapped file.
     *
     * <p> If the file mapped into this buffer resides on a local storage
     * device then when this method returns it is guaranteed that all changes
     * made to the buffer since it was created, or since this method was last
     * invoked, will have been written to that device.
     *
     * <p> If the file does not reside on a local device then no such guarantee
     * is made.
     *
     * <p> If this buffer was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then invoking this
     * method has no effect. </p>
     *
     * @return  This buffer
     */
    public final MappedByteBuffer force() {
        if (fd == null) {
            return this;
        }
        if ((address != 0) && (capacity() != 0)) {
            long offset = mappingOffset();
            force0(fd, mappingAddress(offset), mappingLength(offset));
        }
        return this;
    }

    /**
     * Forces any changes made to a region of this buffer's content to
     * be written to the storage device containing the mapped
     * file. The region starts at the given {@code index} in this
     * buffer and is {@code length} bytes.
     *
     * <p> If the file mapped into this buffer resides on a local
     * storage device then when this method returns it is guaranteed
     * that all changes made to the selected region buffer since it
     * was created, or since this method was last invoked, will have
     * been written to that device. The force operation is free to
     * write bytes that lie outside the specified region, for example
     * to ensure that data blocks of some device-specific granularity
     * are transferred in their entirety.
     *
     * <p> If the file does not reside on a local device then no such
     * guarantee is made.
     *
     * <p> If this buffer was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then
     * invoking this method has no effect. </p>
     *
     * @param index
     *        The index of the first byte in the buffer region that is
     *        to be written back to storage; must be non-negative
     *        and less than limit()
     *
     * @param length
     *        The length of the region in bytes; must be non-negative
     *        and no larger than limit() - index
     *
     * @throws IndexOutOfBoundsException
     *         if the preconditions on the index and length do not
     *         hold.
     *
     * @return  This buffer
     *
     * @since 13
     */
    public final MappedByteBuffer force(int index, int length) {
        if (fd == null) {
            return this;
        }
        if ((address != 0) && (limit() != 0)) {
            // check inputs
            Objects.checkFromIndexSize(index, length, limit());
            long offset = mappingOffset(index);
            force0(fd, mappingAddress(offset, index), mappingLength(offset, length));
        }
        return this;
    }

    private native boolean isLoaded0(long address, long length, int pageCount);
    private native void load0(long address, long length);
    private native void force0(FileDescriptor fd, long address, long length);

    // -- Covariant return type overrides

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer mark() {
        super.mark();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer reset() {
        super.reset();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer clear() {
        super.clear();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer flip() {
        super.flip();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final MappedByteBuffer rewind() {
        super.rewind();
        return this;
    }
}
