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

import java.io.StringWriter;
import java.lang.reflect.Method;

import com.apple.jobjc.JObjCRuntime.Width;
import com.apple.jobjc.PrimitiveCoder.BoolCoder;
import com.apple.jobjc.PrimitiveCoder.DoubleCoder;
import com.apple.jobjc.PrimitiveCoder.FloatCoder;
import com.apple.jobjc.PrimitiveCoder.SCharCoder;
import com.apple.jobjc.PrimitiveCoder.SIntCoder;
import com.apple.jobjc.PrimitiveCoder.SLongLongCoder;
import com.apple.jobjc.PrimitiveCoder.SShortCoder;

public abstract class Coder<T> {
    private static native long getNativeFFITypePtrForCode(final int code);

    static final int FFI_VOID        = 0;
    static final int FFI_PTR        = FFI_VOID+1;

    static final int FFI_SINT8        = FFI_PTR+1;
    static final int FFI_UINT8        = FFI_SINT8+1;
    static final int FFI_SINT16        = FFI_UINT8+1;
    static final int FFI_UINT16        = FFI_SINT16+1;
    static final int FFI_SINT32        = FFI_UINT16+1;
    static final int FFI_UINT32        = FFI_SINT32+1;
    static final int FFI_SINT64        = FFI_UINT32+1;
    static final int FFI_UINT64        = FFI_SINT64+1;

    static final int FFI_FLOAT        = FFI_UINT64+1;
    static final int FFI_DOUBLE        = FFI_FLOAT+1;
    static final int FFI_LONGDOUBLE    = FFI_DOUBLE+1;

    private static long[] ffiCodesToFFITypePtrs;
    static{
        System.loadLibrary("JObjC");
        ffiCodesToFFITypePtrs = new long[FFI_LONGDOUBLE + 1];
        for (int i = 0; i < FFI_LONGDOUBLE + 1; i++) ffiCodesToFFITypePtrs[i] = getNativeFFITypePtrForCode(i);
    }

    long getFFITypePtr() {
        return ffiCodesToFFITypePtrs[getTypeCode()];
    }

    // runtime coding
    public abstract void push(final JObjCRuntime runtime, final long addr, final T x);
    public abstract T pop(final JObjCRuntime runtime, final long addr);

    public void push(final NativeArgumentBuffer args, final T x){
        push(args.runtime, args.argValuesPtr, x);
        args.didPutArgValue(sizeof());
    }

    public T pop(final NativeArgumentBuffer args){
        return pop(args.runtime, args.retValPtr);
    }

    public abstract int sizeof(Width w);
    final public int sizeof(){ return sizeof(JObjCRuntime.WIDTH); }

    //

    public Coder(int ffiTypeCode, String objCEncoding, Class jclass, Class jprim) {
        this.ffiTypeCode = ffiTypeCode;
        this.objCEncoding = objCEncoding;
        this.jclass = jclass;
        this.jprim = jprim;
    }

    public Coder(int ffiTypeCode, String objCEncoding, Class jclass) {
        this(ffiTypeCode, objCEncoding, jclass, null);
    }

    private final int ffiTypeCode;
    private final String objCEncoding;
    private final Class jclass;
    private final Class jprim;

    final int getTypeCode() { return ffiTypeCode; }
    final String getObjCEncoding(){ return objCEncoding; }
    public final Class getJavaClass() { return jclass; }
    public final Class getJavaPrimitive() { return jprim; }

    // runtime coding

    private static Coder[] runtimeCoders;
    static public Coder getCoderAtRuntimeForType(Class cls){
        if(runtimeCoders == null) runtimeCoders = new Coder[]{
            NSClassCoder.INST, IDCoder.INST, PointerCoder.INST,
            DoubleCoder.INST, FloatCoder.INST, SLongLongCoder.INST,
            SIntCoder.INST, SShortCoder.INST, SCharCoder.INST, BoolCoder.INST,
            VoidCoder.INST
        };

        for(Coder c : runtimeCoders)
            if((c.getJavaClass() != null && c.getJavaClass().isAssignableFrom(cls)) ||
                    (c.getJavaPrimitive() != null && c.getJavaPrimitive().isAssignableFrom(cls)))
                return c;

        if(Struct.class.isAssignableFrom(cls)){
            try {
                Method m = cls.getDeclaredMethod("getStructCoder");
                m.setAccessible(true);
                return (Coder) m.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("Could not find suitable coder for " + cls);
    }

    static public Coder getCoderAtRuntime(Object inst){
        if(inst == null)              return PointerCoder.INST;
        if(inst instanceof Struct)    return ((Struct) inst).getCoder();
        return getCoderAtRuntimeForType(inst.getClass());
    }

    //

    public static final class VoidCoder extends Coder<Object>{
        public static final VoidCoder INST = new VoidCoder();
        public VoidCoder(){ super(FFI_VOID, "v", Void.class, void.class); }
        @Override public int sizeof(Width w) { return -1; }
        @Override public Object pop(JObjCRuntime runtime, long addr) { throw new RuntimeException("Trying to pop a Void."); }
        @Override public void push(JObjCRuntime runtime, long addr, Object x) { throw new RuntimeException("Trying to push a Void."); }
    }

    public static final class UnknownCoder extends Coder<Object> {
        public static final UnknownCoder INST = new UnknownCoder();
        public UnknownCoder(){ super(-1, "?", null, null); }
        @Override public int sizeof(Width w) { return -1; }
        @Override public void push(JObjCRuntime runtime, long addr, Object x) { throw new RuntimeException("Coder not implemented");}
        @Override public Object pop(JObjCRuntime runtime, long addr) { throw new RuntimeException("Coder not implemented"); }
    }

    public static final class PrimitivePointerCoder extends Coder<Long> {
        public static final PrimitivePointerCoder INST = new PrimitivePointerCoder();
        public PrimitivePointerCoder(){ super(Coder.FFI_PTR, "^?", Long.class, long.class); }
        @Override public int sizeof(Width w) { return JObjCRuntime.PTR_LEN; }

        public void push(JObjCRuntime runtime, long addr, long x) {
            if(JObjCRuntime.IS64)
                runtime.unsafe.putLong(addr, x);
            else
                runtime.unsafe.putInt(addr, (int) x);
        }

        public void push(final JObjCRuntime runtime, final NativeArgumentBuffer argBuf, final long ptr) {
            push(runtime, argBuf.argValuesPtr, ptr);
            argBuf.didPutArgValue(sizeof());
        }

        public long popPtr(final JObjCRuntime runtime, final long addr) {
            return JObjCRuntime.IS64 ? runtime.unsafe.getLong(addr) : runtime.unsafe.getInt(addr);
        }

        public long popPtr(final JObjCRuntime runtime, final NativeArgumentBuffer argBuf) {
            return popPtr(runtime, argBuf.retValPtr);
        }

        @Override public Long pop(JObjCRuntime runtime, long addr) { return popPtr(runtime, addr); }
        @Override public void push(JObjCRuntime runtime, long addr, Long x) { push(runtime, addr, (long) x); }
    }

    public static final class PointerCoder extends Coder<Pointer> {
        public static final PointerCoder INST = new PointerCoder();
        public PointerCoder(){ super(FFI_PTR, "^?", Pointer.class); }
        @Override public int sizeof(Width w) { return PrimitivePointerCoder.INST.sizeof(w); }

        @Override public Pointer pop(JObjCRuntime runtime, long addr) {
            return new Pointer(PrimitivePointerCoder.INST.popPtr(runtime, addr));
        }
        @Override public void push(JObjCRuntime runtime, long addr, Pointer x) {
            PrimitivePointerCoder.INST.push(runtime, addr, x == null ? 0 : x.ptr);
        }
    }

    public static final class SELCoder extends Coder<SEL> {
        public static final SELCoder INST = new SELCoder();
        public SELCoder(){ super(FFI_PTR, ":", SEL.class); }
        @Override public int sizeof(Width w) { return PrimitivePointerCoder.INST.sizeof(w); }

        @Override public void push(JObjCRuntime runtime, long addr, SEL x) {
            PrimitivePointerCoder.INST.push(runtime, addr, x == null ? 0 : x.selPtr);
        }
        @Override public SEL pop(JObjCRuntime runtime, long addr) {
            return new SEL(PrimitivePointerCoder.INST.popPtr(runtime, addr));
        }
    }

    public static abstract class StructCoder extends Coder<Struct> {
        private final FFIType ffiType;
        final int sizeof;

        public StructCoder(final int sizeof, final Coder... elementCoders){
            super(-1, objCEncoding(elementCoders), null);
            this.ffiType = new FFIType(elementCoders);
            this.sizeof = sizeof;
        }

        @Override public int sizeof(Width w) { return sizeof; }

        private static String objCEncoding(final Coder[] elementCoders) {
            StringWriter str = new StringWriter();
            str.append("{?=");
            for(Coder c : elementCoders)
                str.append(c.getObjCEncoding());
            str.append("}");
            return str.toString();
        }

        @Override long getFFITypePtr() { return ffiType.getPtr(); }

        @Override public void push(NativeArgumentBuffer argBuf, Struct x) {
            // Just point to the instance on the heap instead of copying it onto the arg buf.
            argBuf.doPutArgPtr(x.raw.bufferPtr);
        }

        @Override public void push(JObjCRuntime rt, long addr, Struct x) {
            rt.unsafe.copyMemory(x.raw.bufferPtr, addr, sizeof);
        }

        protected abstract Struct newInstance(JObjCRuntime runtime);

        @Override public Struct pop(final JObjCRuntime runtime, final long addr) {
            Struct s = newInstance(runtime);
            runtime.unsafe.copyMemory(addr, s.raw.bufferPtr, sizeof);
            return s;
        }
    }

    public static final class IDCoder extends Coder<ID>{
        public static final IDCoder INST = new IDCoder();
        public IDCoder(){ super(FFI_PTR, "@", ID.class); }
        @Override public int sizeof(Width w) { return PrimitivePointerCoder.INST.sizeof(w); }

        public <T extends ID> T newID(final JObjCRuntime runtime, final long objPtr) {
            return (T) ID.getObjCObjectFor(runtime, objPtr);
        }

        @Override public ID pop(final JObjCRuntime runtime, final long addr) {
            return newID(runtime, PrimitivePointerCoder.INST.popPtr(runtime, addr));
        }

        @Override public void push(final JObjCRuntime runtime, final long addr, final ID x) {
            PointerCoder.INST.push(runtime, addr, x);
        }
    }

    public static final class NSClassCoder extends Coder<NSClass>{
        public static final NSClassCoder INST = new NSClassCoder();
        public NSClassCoder(){ super(FFI_PTR, "#", NSClass.class); }
        @Override public int sizeof(Width w) { return PrimitivePointerCoder.INST.sizeof(w); }

        @Override public NSClass pop(JObjCRuntime runtime, long addr) {
            final long clsPtr = PrimitivePointerCoder.INST.popPtr(runtime, addr);
            if (clsPtr == 0) return null;
            return NSClass.getObjCClassFor(runtime, clsPtr);
        }
        @Override public void push(JObjCRuntime runtime, long addr, NSClass x) {
            PointerCoder.INST.push(runtime, addr, x);
        }
    }
}
