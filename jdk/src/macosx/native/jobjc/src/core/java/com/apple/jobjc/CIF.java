/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

import com.apple.jobjc.Coder.PrimitivePointerCoder;

class CIF {
    private static native int getSizeofCIF();
    private static final int SIZEOF = getSizeofCIF();
    private static native boolean prepCIF(long cifPtr, int nargs, long retFFITypePtr, long argsPtr);

    public static CIF createCIFFor(final NativeArgumentBuffer args, final Coder returnCoder, final Coder ... argCoders) {
        NativeBuffer cifBuf = new NativeBuffer(SIZEOF + (argCoders.length * JObjCRuntime.PTR_LEN));
        final long argsPtr = cifBuf.bufferPtr + SIZEOF;

        {
            long argsIterPtr = argsPtr;
            for(final Coder coder : argCoders){
                PrimitivePointerCoder.INST.push(args.runtime, argsIterPtr, coder.getFFITypePtr());
                argsIterPtr += JObjCRuntime.PTR_LEN;
            }
        }

        boolean ok = prepCIF(cifBuf.bufferPtr, argCoders.length, returnCoder.getFFITypePtr(), argsPtr);
        if(!ok)
            throw new RuntimeException("ffi_prep_cif failed.");

        return new CIF(cifBuf, returnCoder, argCoders);
    }

    final NativeBuffer cif;
    // CIF needs to keep refs to the Coders, so they don't get finalized and their FFITypes freed.
    final Coder returnCoder;
    final Coder[] argCoders;

    private CIF(final NativeBuffer cif, Coder returnCoder, Coder... argCoders) {
        this.cif = cif;
        this.returnCoder = returnCoder;
        this.argCoders = argCoders;
    }
}
