/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

/**
 * Base class for memory segment var handle view implementations.
 */
final class VarHandleSegmentViewBase extends VarHandle {

    /** endianness **/
    final boolean be;
    /** The layout the accessed segment must be compatible with. */
    final MemoryLayout enclosing;
    /** The fixed offset value, if exists */
    final long offset;

    VarHandleSegmentViewBase(VarForm form, boolean be, MemoryLayout enclosing, long offset, boolean exact) {
        super(form, exact);
        this.be = be;
        this.enclosing = enclosing;
        this.offset = offset;
    }

    @Override
    final MethodType accessModeTypeUncached(VarHandle.AccessType accessType) {
        var getType = vform.methodType_table[0]; // erased, but we our value type is erase-compatible
        return getType.parameterCount() == 2
                ? accessType.accessModeType(MemorySegment.class, getType.returnType(), long.class)
                : accessType.accessModeType(MemorySegment.class, getType.returnType(), long.class, long.class);
    }

    @Override
    public VarHandleSegmentViewBase withInvokeExactBehavior() {
        return hasInvokeExactBehavior() ?
                this :
                new VarHandleSegmentViewBase(vform, be, enclosing, offset, true);
    }

    @Override
    public VarHandleSegmentViewBase withInvokeBehavior() {
        return !hasInvokeExactBehavior() ?
                this :
                new VarHandleSegmentViewBase(vform, be, enclosing, offset, false);
    }
}
