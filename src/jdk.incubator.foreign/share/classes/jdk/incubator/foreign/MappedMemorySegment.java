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

import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * A mapped memory segment, that is, a memory segment backed by memory-mapped file.
 *
 * <p> Mapped memory segments are created via the {@link MemorySegment#mapFromPath(Path, long, FileChannel.MapMode)}.
 * Mapped memory segments behave like ordinary segments, but provide additional capabilities to manipulate memory-mapped
 * memory regions, such as {@link #force()} and {@link #load()}.
 * <p>
 * All implementations of this interface must be <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>;
 * use of identity-sensitive operations (including reference equality ({@code ==}), identity hash code, or synchronization) on
 * instances of {@code MemoryLayout} may have unpredictable results and should be avoided. The {@code equals} method should
 * be used for comparisons.
 * <p>
 * Non-platform classes should not implement {@linkplain MappedMemorySegment} directly.
 *
 * <p> The content of a mapped memory segment can change at any time, for example
 * if the content of the corresponding region of the mapped file is changed by
 * this (or another) program.  Whether or not such changes occur, and when they
 * occur, is operating-system dependent and therefore unspecified.
 *
 * All or part of a mapped memory segment may become
 * inaccessible at any time, for example if the backing mapped file is truncated.  An
 * attempt to access an inaccessible region of a mapped memory segment will not
 * change the segment's content and will cause an unspecified exception to be
 * thrown either at the time of the access or at some later time.  It is
 * therefore strongly recommended that appropriate precautions be taken to
 * avoid the manipulation of a mapped file by this (or another) program, except to read or write
 * the file's content.
 *
 * @apiNote In the future, if the Java language permits, {@link MemorySegment}
 * may become a {@code sealed} interface, which would prohibit subclassing except by
 * explicitly permitted subtypes.
 */
public interface MappedMemorySegment extends MemorySegment {

    @Override
    MappedMemorySegment withAccessModes(int accessModes);

    @Override
    MappedMemorySegment asSlice(long offset, long newSize);

    /**
     * Forces any changes made to this segment's content to be written to the
     * storage device containing the mapped file.
     *
     * <p> If the file mapped into this segment resides on a local storage
     * device then when this method returns it is guaranteed that all changes
     * made to the segment since it was created, or since this method was last
     * invoked, will have been written to that device.
     *
     * <p> If the file does not reside on a local device then no such guarantee
     * is made.
     *
     * <p> If this segment was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then
     * invoking this method may have no effect. In particular, the
     * method has no effect for segments mapped in read-only or private
     * mapping modes. This method may or may not have an effect for
     * implementation-specific mapping modes.
     * </p>
     */
    void force();

    /**
     * Loads this segment's content into physical memory.
     *
     * <p> This method makes a best effort to ensure that, when it returns,
     * this segment's contents is resident in physical memory.  Invoking this
     * method may cause some number of page faults and I/O operations to
     * occur. </p>
     */
    void load();

    /**
     * Unloads this segment's content from physical memory.
     *
     * <p> This method makes a best effort to ensure that this segment's contents are
     * are no longer resident in physical memory. Accessing this segment's contents
     * after invoking this method may cause some number of page faults and I/O operations to
     * occur (as this segment's contents might need to be paged back in). </p>
     */
    void unload();

    /**
     * Tells whether or not this segment's content is resident in physical
     * memory.
     *
     * <p> A return value of {@code true} implies that it is highly likely
     * that all of the data in this segment is resident in physical memory and
     * may therefore be accessed without incurring any virtual-memory page
     * faults or I/O operations.  A return value of {@code false} does not
     * necessarily imply that the segment's content is not resident in physical
     * memory.
     *
     * <p> The returned value is a hint, rather than a guarantee, because the
     * underlying operating system may have paged out some of the segment's data
     * by the time that an invocation of this method returns.  </p>
     *
     * @return  {@code true} if it is likely that this segment's content
     *          is resident in physical memory
     */
    boolean isLoaded();
}
