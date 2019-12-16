/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.foreign.unsafe;

import jdk.incubator.foreign.MemoryAddress;
import jdk.internal.foreign.MemoryAddressImpl;

/**
 * Unsafe methods to allow interop between sun.misc.unsafe and memory access API.
 */
public final class ForeignUnsafe {

    private ForeignUnsafe() {
        //just the one, please
    }

    // The following methods can be used in conjunction with the java.foreign API.

    /**
     * Obtain the base object (if any) associated with this address. This can be used in conjunction with
     * {@link #getUnsafeOffset(MemoryAddress)} in order to obtain a base/offset addressing coordinate pair
     * to be used with methods like {@link sun.misc.Unsafe#getInt(Object, long)} and the likes.
     *
     * @param address the address whose base object is to be obtained.
     * @return the base object associated with the address, or {@code null}.
     */
    public static Object getUnsafeBase(MemoryAddress address) {
        return ((MemoryAddressImpl)address).unsafeGetBase();
    }

    /**
     * Obtain the offset associated with this address. If {@link #getUnsafeBase(MemoryAddress)} returns {@code null} on the passed
     * address, then the offset is to be interpreted as the (absolute) numerical value associated said address.
     * Alternatively, the offset represents the displacement of a field or an array element within the containing
     * base object. This can be used in conjunction with {@link #getUnsafeBase(MemoryAddress)} in order to obtain a base/offset
     * addressing coordinate pair to be used with methods like {@link sun.misc.Unsafe#getInt(Object, long)} and the likes.
     *
     * @param address the address whose offset is to be obtained.
     * @return the offset associated with the address.
     */
    public static long getUnsafeOffset(MemoryAddress address) {
        return ((MemoryAddressImpl)address).unsafeGetOffset();
    }
}
