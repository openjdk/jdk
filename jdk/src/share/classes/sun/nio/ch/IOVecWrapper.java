/*
 * Copyright 2000-2002 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.ch;

import sun.misc.*;


/**
 * Manipulates a native array of iovec structs on Solaris:
 *
 * typedef struct iovec {
 *    caddr_t  iov_base;
      int      iov_len;
 * } iovec_t;
 *
 * @author Mike McCloskey
 * @since 1.4
 */

class IOVecWrapper {

    // Miscellaneous constants
    static int BASE_OFFSET = 0;
    static int LEN_OFFSET;
    static int SIZE_IOVEC;

    // The iovec array
    private AllocatedNativeObject vecArray;

    // Base address of this array
    long address;

    // Address size in bytes
    static int addressSize;

    IOVecWrapper(int newSize) {
        newSize = (newSize + 1) * SIZE_IOVEC;
        vecArray = new AllocatedNativeObject(newSize, false);
        address = vecArray.address();
    }

    void putBase(int i, long base) {
        int offset = SIZE_IOVEC * i + BASE_OFFSET;
        if (addressSize == 4)
            vecArray.putInt(offset, (int)base);
        else
            vecArray.putLong(offset, base);
    }

    void putLen(int i, long len) {
        int offset = SIZE_IOVEC * i + LEN_OFFSET;
        if (addressSize == 4)
            vecArray.putInt(offset, (int)len);
        else
            vecArray.putLong(offset, len);
    }

    void free() {
        vecArray.free();
    }

    static {
        addressSize = Util.unsafe().addressSize();
        LEN_OFFSET = addressSize;
        SIZE_IOVEC = (short) (addressSize * 2);
    }
}
