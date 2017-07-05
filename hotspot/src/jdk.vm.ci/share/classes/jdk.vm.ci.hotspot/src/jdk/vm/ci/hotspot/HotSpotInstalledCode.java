/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.inittimer.SuppressFBWarnings;
import sun.misc.Unsafe;

/**
 * Implementation of {@link InstalledCode} for HotSpot.
 */
public abstract class HotSpotInstalledCode extends InstalledCode {

    /**
     * Total size of the code blob.
     */
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private int size;

    /**
     * Start address of the code.
     */
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private long codeStart;

    /**
     * Size of the code.
     */
    @SuppressFBWarnings(value = "UWF_UNWRITTEN_FIELD", justification = "field is set by the native part") private int codeSize;

    public HotSpotInstalledCode(String name) {
        super(name);
    }

    /**
     * @return the total size of this code blob
     */
    public int getSize() {
        return size;
    }

    /**
     * @return a copy of this code blob if it is {@linkplain #isValid() valid}, null otherwise.
     */
    public byte[] getBlob() {
        if (!isValid()) {
            return null;
        }
        byte[] blob = new byte[size];
        UNSAFE.copyMemory(null, getAddress(), blob, Unsafe.ARRAY_BYTE_BASE_OFFSET, size);
        return blob;
    }

    @Override
    public abstract String toString();

    @Override
    public long getStart() {
        return codeStart;
    }

    @Override
    public long getCodeSize() {
        return codeSize;
    }

    @Override
    public byte[] getCode() {
        if (!isValid()) {
            return null;
        }
        byte[] code = new byte[codeSize];
        UNSAFE.copyMemory(null, codeStart, code, Unsafe.ARRAY_BYTE_BASE_OFFSET, codeSize);
        return code;
    }
}
