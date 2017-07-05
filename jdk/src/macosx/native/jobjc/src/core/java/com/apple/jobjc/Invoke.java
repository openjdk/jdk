/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.apple.jobjc.Coder.IDCoder;
import com.apple.jobjc.Coder.NSClassCoder;
import com.apple.jobjc.Coder.PrimitivePointerCoder;
import com.apple.jobjc.Coder.SELCoder;
import com.apple.jobjc.Coder.StructCoder;

public abstract class Invoke {
    public abstract void invoke(NativeArgumentBuffer argBuf);
    public abstract void invoke(NativeArgumentBuffer buffer, Struct retvalStruct);

    //

    public static final class FunCall extends Invoke{
        static native void invoke(long cifPtr, long fxnPtr, long retValPtr, long argsPtr);

        final long fxnPtr;
        final CIF cif;

        FunCall(long fxnPtr, CIF cif) {
            this.fxnPtr = fxnPtr;
            this.cif = cif;
        }

        public FunCall(final JObjCRuntime runtime, final String name, final Coder returnCoder, final Coder ... argCoders) {
            this(Function.getFxnPtr(name), CIF.createCIFFor(runtime.getThreadLocalState(), returnCoder, argCoders));
        }

        public FunCall(final MacOSXFramework framework, final String name, final Coder returnCoder, final Coder ... argCoders) {
            this(Function.getFxnPtr(name, framework), CIF.createCIFFor(framework.getRuntime().getThreadLocalState(), returnCoder, argCoders));
        }

        public void init(final NativeArgumentBuffer argBuf) {
            argBuf.reset();
        }

        @Override public void invoke(final NativeArgumentBuffer argBuf) {
            invoke(argBuf, argBuf.retValPtr);
        }

        @Override public void invoke(final NativeArgumentBuffer buffer, final Struct retvalStruct) {
            invoke(buffer, retvalStruct.raw.bufferPtr);
        }

        void invoke(final NativeArgumentBuffer argBuf, final long retValPtr) {
            invoke(cif.cif.bufferPtr, fxnPtr, retValPtr, argBuf.buffer.bufferPtr);
        }
    }

    public static final class MsgSend extends Invoke{
        static{ System.load("/usr/lib/libobjc.dylib"); }

        private static final long OBJC_MSG_SEND_FXN_PTR = new Function("objc_msgSend").fxnPtr;
        private static final long OBJC_MSG_SEND_FPRET_FXN_PTR = new Function("objc_msgSend_fpret").fxnPtr;
        private static final long OBJC_MSG_SEND_STRET_FXN_PTR = new Function("objc_msgSend_stret").fxnPtr;

        final FunCall funCall;
        final long selPtr;

        public MsgSend(final JObjCRuntime runtime, final String name, final Coder returnCoder, final Coder ... argCoders) {
            this.funCall = new FunCall(getMsgSendFxnPtr(returnCoder),
                    CIF.createCIFFor(runtime.getThreadLocalState(), returnCoder, getSelCoders(argCoders)));
            this.selPtr = SEL.getSelectorPtr(name);
        }

        public void init(final NativeArgumentBuffer nativeBuffer, final ID obj) {
            funCall.init(nativeBuffer);
            IDCoder.INST.push(nativeBuffer, obj);
            PrimitivePointerCoder.INST.push(nativeBuffer.runtime, nativeBuffer, selPtr);
        }

        @Override public void invoke(final NativeArgumentBuffer argBuf) {
            funCall.invoke(argBuf);
        }

        @Override public void invoke(final NativeArgumentBuffer buffer, final Struct retvalStruct) {
            funCall.invoke(buffer, retvalStruct);
        }

        // support

        static Coder[] getSelCoders(final Coder[] argCoders) {
            final Coder[] selArgCoders = new Coder[argCoders.length + 2];
            selArgCoders[0] = IDCoder.INST;
            selArgCoders[1] = SELCoder.INST;
            for (int i = 0; i < argCoders.length; i++)
                selArgCoders[i + 2] = argCoders[i];
            return selArgCoders;
        }

        static long getMsgSendFxnPtr(final Coder returnCoder) {
            if(returnCoder instanceof StructCoder){
                StructCoder scoder = (StructCoder) returnCoder;

                switch(JObjCRuntime.ARCH){
                case ppc:
                    return OBJC_MSG_SEND_STRET_FXN_PTR;
                case i386:
                    switch(scoder.sizeof){
                    case 1: case 2: case 4: case 8:
                        return OBJC_MSG_SEND_FXN_PTR;
                    }
                    return OBJC_MSG_SEND_STRET_FXN_PTR;
                case x86_64:
                    if(scoder.sizeof > 16)
                        return OBJC_MSG_SEND_STRET_FXN_PTR;
                    else
                        return OBJC_MSG_SEND_FXN_PTR;
                default:
                    throw new RuntimeException();
                }
            }

            final int typeCode = returnCoder.getTypeCode();

            switch(JObjCRuntime.ARCH){
            case ppc:
                return OBJC_MSG_SEND_FXN_PTR;
            case i386:
                switch(typeCode) {
                case Coder.FFI_FLOAT: case Coder.FFI_DOUBLE: case Coder.FFI_LONGDOUBLE:
                    return OBJC_MSG_SEND_FPRET_FXN_PTR;
                }
                return OBJC_MSG_SEND_FXN_PTR;
            case x86_64:
                if(typeCode == Coder.FFI_LONGDOUBLE)
                    return OBJC_MSG_SEND_FPRET_FXN_PTR;
                return OBJC_MSG_SEND_FXN_PTR;
            default:
                throw new RuntimeException();
            }
        }
    }

    public static final class MsgSendSuper extends Invoke{
        static{ System.load("/usr/lib/libobjc.dylib"); }

        private static final long OBJC_MSG_SEND_SUPER_FXN_PTR = new Function("objc_msgSendSuper").fxnPtr;
        private static final long OBJC_MSG_SEND_SUPER_STRET_FXN_PTR = new Function("objc_msgSendSuper_stret").fxnPtr;

        final FunCall funCall;
        final long selPtr;

        public MsgSendSuper(final JObjCRuntime runtime, final String name, final Coder returnCoder, final Coder ... argCoders) {
            this.funCall = new FunCall(getMsgSendSuperFxnPtr(returnCoder),
                    CIF.createCIFFor(runtime.getThreadLocalState(), returnCoder, getSuperSelCoders(argCoders)));
            this.selPtr = SEL.getSelectorPtr(name);
        }

        public void init(final NativeArgumentBuffer argBuf, final ID obj, final NSClass cls) {
            funCall.init(argBuf);

            // Instead of mallocing a struct, or keeping another thread local,
            // let's write objc_super out to the argbuf, and then point an argument
            // to the data.

            final long valPtr = argBuf.argValuesPtr;
            final int ptrLen = JObjCRuntime.PTR_LEN;

            IDCoder     .INST.push(argBuf.runtime, valPtr,          obj);
            NSClassCoder.INST.push(argBuf.runtime, valPtr + ptrLen, cls);
            argBuf.argValuesPtr += ptrLen + ptrLen;

            PrimitivePointerCoder.INST.push(argBuf.runtime, argBuf, valPtr);
            PrimitivePointerCoder.INST.push(argBuf.runtime, argBuf, selPtr);
        }

        @Override public void invoke(final NativeArgumentBuffer argBuf) {
            funCall.invoke(argBuf);
        }

        @Override public void invoke(final NativeArgumentBuffer buffer, final Struct retvalStruct) {
            funCall.invoke(buffer, retvalStruct);
        }

        //

        private final static StructCoder objc_super_coder = new StructCoder(JObjCRuntime.PTR_LEN*2, IDCoder.INST, NSClassCoder.INST){
            @Override protected Struct newInstance(JObjCRuntime runtime) { return null; }};

        static Coder[] getSuperSelCoders(final Coder[] argCoders) {
            final Coder[] selArgCoders = new Coder[argCoders.length + 2];
            selArgCoders[0] = objc_super_coder;
            selArgCoders[1] = SELCoder.INST;
            for (int i = 0; i < argCoders.length; i++)
                selArgCoders[i + 2] = argCoders[i];
            return selArgCoders;
        }

        static long getMsgSendSuperFxnPtr(final Coder returnCoder){
            long normal = MsgSend.getMsgSendFxnPtr(returnCoder);
            if(normal == MsgSend.OBJC_MSG_SEND_STRET_FXN_PTR)
                return OBJC_MSG_SEND_SUPER_STRET_FXN_PTR;
            else
                return OBJC_MSG_SEND_SUPER_FXN_PTR;
        }
    }
}
