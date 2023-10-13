/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;
import java.util.Objects;

public final class ArenaImpl implements Arena {

    private final MemorySessionImpl session;
    private final boolean shouldReserveMemory;
    ArenaImpl(MemorySessionImpl session) {
        this.session = session;
        shouldReserveMemory = session instanceof ImplicitSession;
    }

    @Override
    public Scope scope() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }

    public MemorySegment allocateNoInit(long byteSize, long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        return NativeMemorySegmentImpl.makeNativeSegmentNoZeroing(byteSize, byteAlignment, session, shouldReserveMemory);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        MemorySegment segment = allocateNoInit(byteSize, byteAlignment);
        return segment.fill((byte)0);
    }
}
