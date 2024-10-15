/*
 *  Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import java.nio.ByteBuffer;
import java.util.Optional;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Implementation for native memory segments. A native memory segment is essentially a wrapper around
 * a native long address.
 */
sealed class NativeMemorySegmentImpl extends AbstractMemorySegmentImpl permits MappedMemorySegmentImpl {

    final long min;

    @ForceInline
    NativeMemorySegmentImpl(long min, long length, boolean readOnly, MemorySessionImpl scope) {
        super(length, readOnly, scope);
        this.min = (Unsafe.getUnsafe().addressSize() == 4)
                // On 32-bit systems, normalize the upper unused 32-bits to zero
                ? min & 0x0000_0000_FFFF_FFFFL
                // On 64-bit systems, all the bits are used
                : min;
    }

    @Override
    public long address() {
        return min;
    }

    @Override
    public Optional<Object> heapBase() {
        return Optional.empty();
    }

    public final long maxByteAlignment() {
        return address() == 0
                ? 1L << 62
                : Long.lowestOneBit(address());
    }

    @ForceInline
    @Override
    NativeMemorySegmentImpl dup(long offset, long size, boolean readOnly, MemorySessionImpl scope) {
        return new NativeMemorySegmentImpl(min + offset, size, readOnly, scope);
    }

    @Override
    ByteBuffer makeByteBuffer() {
        return NIO_ACCESS.newDirectByteBuffer(min, (int) this.length, null, this);
    }

    @Override
    public boolean isNative() {
        return true;
    }

    @Override
    public long unsafeGetOffset() {
        return min;
    }

    @Override
    public Object unsafeGetBase() {
        return null;
    }

    @Override
    public long maxAlignMask() {
        return 0;
    }
}
