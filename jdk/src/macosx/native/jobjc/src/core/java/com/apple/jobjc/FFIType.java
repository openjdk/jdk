/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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


class FFIType{
    private static native void makeFFIType(long ffi_type_buf, long elements_buf);
    private static native int  getFFITypeSizeof();
    private static int FFI_TYPE_SIZEOF = getFFITypeSizeof();
    final NativeBuffer ffi_type;
    final NativeBuffer elements;
    final Coder[] elementCoders;

    public FFIType(final Coder... elementCoders){
        final JObjCRuntime runtime = JObjCRuntime.inst();
        this.elementCoders = elementCoders;
        this.ffi_type = new NativeBuffer(FFI_TYPE_SIZEOF);
        this.elements = new NativeBuffer(JObjCRuntime.PTR_LEN * (elementCoders.length + 1));

        long elIterPtr = elements.bufferPtr;
        for(Coder c : elementCoders){
            PrimitivePointerCoder.INST.push(runtime, elIterPtr, c.getFFITypePtr());
            elIterPtr += PrimitivePointerCoder.INST.sizeof();
        }
        PrimitivePointerCoder.INST.push(runtime, elIterPtr, 0);

        makeFFIType(ffi_type.bufferPtr, elements.bufferPtr);
    }

    public long getPtr(){
        return ffi_type.bufferPtr;
    }
}
