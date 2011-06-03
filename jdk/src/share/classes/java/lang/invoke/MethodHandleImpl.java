/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.VerifyType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import sun.invoke.empty.Empty;
import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;
import sun.misc.Unsafe;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Trusted implementation code for MethodHandle.
 * @author jrose
 */
/*non-public*/ abstract class MethodHandleImpl {
    /// Factory methods to create method handles:

    private static final MemberName.Factory LOOKUP = MemberName.Factory.INSTANCE;

    static void initStatics() {
        // Trigger preceding sequence.
    }

    /** Look up a given method.
     * Callable only from sun.invoke and related packages.
     * <p>
     * The resulting method handle type will be of the given type,
     * with a receiver type {@code rcvc} prepended if the member is not static.
     * <p>
     * Access checks are made as of the given lookup class.
     * In particular, if the method is protected and {@code defc} is in a
     * different package from the lookup class, then {@code rcvc} must be
     * the lookup class or a subclass.
     * @param token Proof that the lookup class has access to this package.
     * @param member Resolved method or constructor to call.
     * @param name Name of the desired method.
     * @param rcvc Receiver type of desired non-static method (else null)
     * @param doDispatch whether the method handle will test the receiver type
     * @param lookupClass access-check relative to this class
     * @return a direct handle to the matching method
     * @throws IllegalAccessException if the given method cannot be accessed by the lookup class
     */
    static
    MethodHandle findMethod(MemberName method,
                            boolean doDispatch, Class<?> lookupClass) throws IllegalAccessException {
        MethodType mtype = method.getMethodType();
        if (!method.isStatic()) {
            // adjust the advertised receiver type to be exactly the one requested
            // (in the case of invokespecial, this will be the calling class)
            Class<?> recvType = method.getDeclaringClass();
            mtype = mtype.insertParameterTypes(0, recvType);
        }
        DirectMethodHandle mh = new DirectMethodHandle(mtype, method, doDispatch, lookupClass);
        if (!mh.isValid())
            throw method.makeAccessException("no direct method handle", lookupClass);
        assert(mh.type() == mtype);
        if (!method.isVarargs())
            return mh;
        int argc = mtype.parameterCount();
        if (argc != 0) {
            Class<?> arrayType = mtype.parameterType(argc-1);
            if (arrayType.isArray())
                return AdapterMethodHandle.makeVarargsCollector(mh, arrayType);
        }
        throw method.makeAccessException("cannot make variable arity", null);
    }

    static
    MethodHandle makeAllocator(MethodHandle rawConstructor) {
        MethodType rawConType = rawConstructor.type();
        Class<?> allocateClass = rawConType.parameterType(0);
        // Wrap the raw (unsafe) constructor with the allocation of a suitable object.
        if (AdapterMethodHandle.canCollectArguments(rawConType, MethodType.methodType(allocateClass), 0, true)) {
            // allocator(arg...)
            // [fold]=> cookedConstructor(obj=allocate(C), arg...)
            // [dup,collect]=> identity(obj, void=rawConstructor(obj, arg...))
            MethodHandle returner = MethodHandles.identity(allocateClass);
            MethodType ctype = rawConType.insertParameterTypes(0, allocateClass).changeReturnType(allocateClass);
            MethodHandle  cookedConstructor = AdapterMethodHandle.makeCollectArguments(returner, rawConstructor, 1, false);
            assert(cookedConstructor.type().equals(ctype));
            ctype = ctype.dropParameterTypes(0, 1);
            cookedConstructor = AdapterMethodHandle.makeCollectArguments(cookedConstructor, returner, 0, true);
            MethodHandle allocator = new AllocateObject(allocateClass);
            // allocate() => new C(void)
            assert(allocator.type().equals(MethodType.methodType(allocateClass)));
            ctype = ctype.dropParameterTypes(0, 1);
            MethodHandle fold = foldArguments(cookedConstructor, ctype, 0, allocator);
            return fold;
        }
        assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
        MethodHandle allocator
            = AllocateObject.make(allocateClass, rawConstructor);
        assert(allocator.type()
               .equals(rawConType.dropParameterTypes(0, 1).changeReturnType(rawConType.parameterType(0))));
        return allocator;
    }

    static final class AllocateObject<C> extends BoundMethodHandle {
        private static final Unsafe unsafe = Unsafe.getUnsafe();

        private final Class<C> allocateClass;
        private final MethodHandle rawConstructor;

        private AllocateObject(MethodHandle invoker,
                               Class<C> allocateClass, MethodHandle rawConstructor) {
            super(invoker);
            this.allocateClass = allocateClass;
            this.rawConstructor = rawConstructor;
            assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
        }
        // for allocation only:
        private AllocateObject(Class<C> allocateClass) {
            super(ALLOCATE.asType(MethodType.methodType(allocateClass, AllocateObject.class)));
            this.allocateClass = allocateClass;
            this.rawConstructor = null;
        }
        static MethodHandle make(Class<?> allocateClass, MethodHandle rawConstructor) {
            assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
            MethodType rawConType = rawConstructor.type();
            assert(rawConType.parameterType(0) == allocateClass);
            MethodType newType = rawConType.dropParameterTypes(0, 1).changeReturnType(allocateClass);
            int nargs = rawConType.parameterCount() - 1;
            if (nargs < INVOKES.length) {
                MethodHandle invoke = INVOKES[nargs];
                MethodType conType = CON_TYPES[nargs];
                MethodHandle gcon = convertArguments(rawConstructor, conType, rawConType, 0);
                if (gcon == null)  return null;
                MethodHandle galloc = new AllocateObject(invoke, allocateClass, gcon);
                assert(galloc.type() == newType.generic());
                return convertArguments(galloc, newType, galloc.type(), 0);
            } else {
                MethodHandle invoke = VARARGS_INVOKE;
                MethodType conType = CON_TYPES[nargs];
                MethodHandle gcon = spreadArgumentsFromPos(rawConstructor, conType, 1);
                if (gcon == null)  return null;
                MethodHandle galloc = new AllocateObject(invoke, allocateClass, gcon);
                return collectArguments(galloc, newType, 1, null);
            }
        }
        @Override
        String debugString() {
            return addTypeString(allocateClass.getSimpleName(), this);
        }
        @SuppressWarnings("unchecked")
        private C allocate() throws InstantiationException {
            return (C) unsafe.allocateInstance(allocateClass);
        }
        private C invoke_V(Object... av) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, av);
            return obj;
        }
        private C invoke_L0() throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj);
            return obj;
        }
        private C invoke_L1(Object a0) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0);
            return obj;
        }
        private C invoke_L2(Object a0, Object a1) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1);
            return obj;
        }
        private C invoke_L3(Object a0, Object a1, Object a2) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2);
            return obj;
        }
        private C invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2, a3);
            return obj;
        }
        private C invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2, a3, a4);
            return obj;
        }
        private C invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2, a3, a4, a5);
            return obj;
        }
        private C invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2, a3, a4, a5, a6);
            return obj;
        }
        private C invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable {
            C obj = allocate();
            rawConstructor.invokeExact((Object)obj, a0, a1, a2, a3, a4, a5, a6, a7);
            return obj;
        }
        static MethodHandle[] makeInvokes() {
            ArrayList<MethodHandle> invokes = new ArrayList<MethodHandle>();
            MethodHandles.Lookup lookup = IMPL_LOOKUP;
            for (;;) {
                int nargs = invokes.size();
                String name = "invoke_L"+nargs;
                MethodHandle invoke = null;
                try {
                    invoke = lookup.findVirtual(AllocateObject.class, name, MethodType.genericMethodType(nargs));
                } catch (ReflectiveOperationException ex) {
                }
                if (invoke == null)  break;
                invokes.add(invoke);
            }
            assert(invokes.size() == 9);  // current number of methods
            return invokes.toArray(new MethodHandle[0]);
        };
        static final MethodHandle[] INVOKES = makeInvokes();
        // For testing use this:
        //static final MethodHandle[] INVOKES = Arrays.copyOf(makeInvokes(), 2);
        static final MethodHandle VARARGS_INVOKE;
        static final MethodHandle ALLOCATE;
        static {
            try {
                VARARGS_INVOKE = IMPL_LOOKUP.findVirtual(AllocateObject.class, "invoke_V", MethodType.genericMethodType(0, true));
                ALLOCATE = IMPL_LOOKUP.findVirtual(AllocateObject.class, "allocate", MethodType.genericMethodType(0));
            } catch (ReflectiveOperationException ex) {
                throw uncaughtException(ex);
            }
        }
        // Corresponding generic constructor types:
        static final MethodType[] CON_TYPES = new MethodType[INVOKES.length];
        static {
            for (int i = 0; i < INVOKES.length; i++)
                CON_TYPES[i] = makeConType(INVOKES[i]);
        }
        static final MethodType VARARGS_CON_TYPE = makeConType(VARARGS_INVOKE);
        static MethodType makeConType(MethodHandle invoke) {
            MethodType invType = invoke.type();
            return invType.changeParameterType(0, Object.class).changeReturnType(void.class);
        }
    }

    static
    MethodHandle accessField(MemberName member, boolean isSetter,
                             Class<?> lookupClass) {
        // Use sun. misc.Unsafe to dig up the dirt on the field.
        MethodHandle mh = new FieldAccessor(member, isSetter);
        return mh;
    }

    static
    MethodHandle accessArrayElement(Class<?> arrayClass, boolean isSetter) {
        if (!arrayClass.isArray())
            throw newIllegalArgumentException("not an array: "+arrayClass);
        Class<?> elemClass = arrayClass.getComponentType();
        MethodHandle[] mhs = FieldAccessor.ARRAY_CACHE.get(elemClass);
        if (mhs == null) {
            if (!FieldAccessor.doCache(elemClass))
                return FieldAccessor.ahandle(arrayClass, isSetter);
            mhs = new MethodHandle[] {
                FieldAccessor.ahandle(arrayClass, false),
                FieldAccessor.ahandle(arrayClass, true)
            };
            if (mhs[0].type().parameterType(0) == Class.class) {
                mhs[0] = mhs[0].bindTo(elemClass);
                mhs[1] = mhs[1].bindTo(elemClass);
            }
            synchronized (FieldAccessor.ARRAY_CACHE) {}  // memory barrier
            FieldAccessor.ARRAY_CACHE.put(elemClass, mhs);
        }
        return mhs[isSetter ? 1 : 0];
    }

    static final class FieldAccessor<C,V> extends BoundMethodHandle {
        private static final Unsafe unsafe = Unsafe.getUnsafe();
        final Object base;  // for static refs only
        final long offset;
        final String name;

        FieldAccessor(MemberName field, boolean isSetter) {
            super(fhandle(field.getDeclaringClass(), field.getFieldType(), isSetter, field.isStatic()));
            this.offset = (long) field.getVMIndex();
            this.name = field.getName();
            this.base = staticBase(field);
        }
        @Override
        String debugString() { return addTypeString(name, this); }

        int getFieldI(C obj) { return unsafe.getInt(obj, offset); }
        void setFieldI(C obj, int x) { unsafe.putInt(obj, offset, x); }
        long getFieldJ(C obj) { return unsafe.getLong(obj, offset); }
        void setFieldJ(C obj, long x) { unsafe.putLong(obj, offset, x); }
        float getFieldF(C obj) { return unsafe.getFloat(obj, offset); }
        void setFieldF(C obj, float x) { unsafe.putFloat(obj, offset, x); }
        double getFieldD(C obj) { return unsafe.getDouble(obj, offset); }
        void setFieldD(C obj, double x) { unsafe.putDouble(obj, offset, x); }
        boolean getFieldZ(C obj) { return unsafe.getBoolean(obj, offset); }
        void setFieldZ(C obj, boolean x) { unsafe.putBoolean(obj, offset, x); }
        byte getFieldB(C obj) { return unsafe.getByte(obj, offset); }
        void setFieldB(C obj, byte x) { unsafe.putByte(obj, offset, x); }
        short getFieldS(C obj) { return unsafe.getShort(obj, offset); }
        void setFieldS(C obj, short x) { unsafe.putShort(obj, offset, x); }
        char getFieldC(C obj) { return unsafe.getChar(obj, offset); }
        void setFieldC(C obj, char x) { unsafe.putChar(obj, offset, x); }
        @SuppressWarnings("unchecked")
        V getFieldL(C obj) { return (V) unsafe.getObject(obj, offset); }
        @SuppressWarnings("unchecked")
        void setFieldL(C obj, V x) { unsafe.putObject(obj, offset, x); }
        // cast (V) is OK here, since we wrap convertArguments around the MH.

        static Object staticBase(final MemberName field) {
            if (!field.isStatic())  return null;
            return AccessController.doPrivileged(new PrivilegedAction<Object>() {
                    public Object run() {
                        try {
                            Class c = field.getDeclaringClass();
                            // FIXME:  Should not have to create 'f' to get this value.
                            java.lang.reflect.Field f = c.getDeclaredField(field.getName());
                            return unsafe.staticFieldBase(f);
                        } catch (NoSuchFieldException ee) {
                            throw uncaughtException(ee);
                        }
                    }
                });
        }

        int getStaticI() { return unsafe.getInt(base, offset); }
        void setStaticI(int x) { unsafe.putInt(base, offset, x); }
        long getStaticJ() { return unsafe.getLong(base, offset); }
        void setStaticJ(long x) { unsafe.putLong(base, offset, x); }
        float getStaticF() { return unsafe.getFloat(base, offset); }
        void setStaticF(float x) { unsafe.putFloat(base, offset, x); }
        double getStaticD() { return unsafe.getDouble(base, offset); }
        void setStaticD(double x) { unsafe.putDouble(base, offset, x); }
        boolean getStaticZ() { return unsafe.getBoolean(base, offset); }
        void setStaticZ(boolean x) { unsafe.putBoolean(base, offset, x); }
        byte getStaticB() { return unsafe.getByte(base, offset); }
        void setStaticB(byte x) { unsafe.putByte(base, offset, x); }
        short getStaticS() { return unsafe.getShort(base, offset); }
        void setStaticS(short x) { unsafe.putShort(base, offset, x); }
        char getStaticC() { return unsafe.getChar(base, offset); }
        void setStaticC(char x) { unsafe.putChar(base, offset, x); }
        V getStaticL() { return (V) unsafe.getObject(base, offset); }
        void setStaticL(V x) { unsafe.putObject(base, offset, x); }

        static String fname(Class<?> vclass, boolean isSetter, boolean isStatic) {
            String stem;
            if (!isStatic)
                stem = (!isSetter ? "getField" : "setField");
            else
                stem = (!isSetter ? "getStatic" : "setStatic");
            return stem + Wrapper.basicTypeChar(vclass);
        }
        static MethodType ftype(Class<?> cclass, Class<?> vclass, boolean isSetter, boolean isStatic) {
            MethodType type;
            if (!isStatic) {
                if (!isSetter)
                    return MethodType.methodType(vclass, cclass);
                else
                    return MethodType.methodType(void.class, cclass, vclass);
            } else {
                if (!isSetter)
                    return MethodType.methodType(vclass);
                else
                    return MethodType.methodType(void.class, vclass);
            }
        }
        static MethodHandle fhandle(Class<?> cclass, Class<?> vclass, boolean isSetter, boolean isStatic) {
            String name = FieldAccessor.fname(vclass, isSetter, isStatic);
            if (cclass.isPrimitive())  throw newIllegalArgumentException("primitive "+cclass);
            Class<?> ecclass = Object.class;  //erase this type
            Class<?> evclass = vclass;
            if (!evclass.isPrimitive())  evclass = Object.class;
            MethodType type = FieldAccessor.ftype(ecclass, evclass, isSetter, isStatic);
            MethodHandle mh;
            try {
                mh = IMPL_LOOKUP.findVirtual(FieldAccessor.class, name, type);
            } catch (ReflectiveOperationException ex) {
                throw uncaughtException(ex);
            }
            if (evclass != vclass || (!isStatic && ecclass != cclass)) {
                MethodType strongType = FieldAccessor.ftype(cclass, vclass, isSetter, isStatic);
                strongType = strongType.insertParameterTypes(0, FieldAccessor.class);
                mh = convertArguments(mh, strongType, 0);
            }
            return mh;
        }

        /// Support for array element access
        static final HashMap<Class<?>, MethodHandle[]> ARRAY_CACHE =
                new HashMap<Class<?>, MethodHandle[]>();
        // FIXME: Cache on the classes themselves, not here.
        static boolean doCache(Class<?> elemClass) {
            if (elemClass.isPrimitive())  return true;
            ClassLoader cl = elemClass.getClassLoader();
            return cl == null || cl == ClassLoader.getSystemClassLoader();
        }
        static int getElementI(int[] a, int i) { return a[i]; }
        static void setElementI(int[] a, int i, int x) { a[i] = x; }
        static long getElementJ(long[] a, int i) { return a[i]; }
        static void setElementJ(long[] a, int i, long x) { a[i] = x; }
        static float getElementF(float[] a, int i) { return a[i]; }
        static void setElementF(float[] a, int i, float x) { a[i] = x; }
        static double getElementD(double[] a, int i) { return a[i]; }
        static void setElementD(double[] a, int i, double x) { a[i] = x; }
        static boolean getElementZ(boolean[] a, int i) { return a[i]; }
        static void setElementZ(boolean[] a, int i, boolean x) { a[i] = x; }
        static byte getElementB(byte[] a, int i) { return a[i]; }
        static void setElementB(byte[] a, int i, byte x) { a[i] = x; }
        static short getElementS(short[] a, int i) { return a[i]; }
        static void setElementS(short[] a, int i, short x) { a[i] = x; }
        static char getElementC(char[] a, int i) { return a[i]; }
        static void setElementC(char[] a, int i, char x) { a[i] = x; }
        static Object getElementL(Object[] a, int i) { return a[i]; }
        static void setElementL(Object[] a, int i, Object x) { a[i] = x; }
        static <V> V getElementL(Class<V[]> aclass, V[] a, int i) { return aclass.cast(a)[i]; }
        static <V> void setElementL(Class<V[]> aclass, V[] a, int i, V x) { aclass.cast(a)[i] = x; }

        static String aname(Class<?> aclass, boolean isSetter) {
            Class<?> vclass = aclass.getComponentType();
            if (vclass == null)  throw new IllegalArgumentException();
            return (!isSetter ? "getElement" : "setElement") + Wrapper.basicTypeChar(vclass);
        }
        static MethodType atype(Class<?> aclass, boolean isSetter) {
            Class<?> vclass = aclass.getComponentType();
            if (!isSetter)
                return MethodType.methodType(vclass, aclass, int.class);
            else
                return MethodType.methodType(void.class, aclass, int.class, vclass);
        }
        static MethodHandle ahandle(Class<?> aclass, boolean isSetter) {
            Class<?> vclass = aclass.getComponentType();
            String name = FieldAccessor.aname(aclass, isSetter);
            Class<?> caclass = null;
            if (!vclass.isPrimitive() && vclass != Object.class) {
                caclass = aclass;
                aclass = Object[].class;
                vclass = Object.class;
            }
            MethodType type = FieldAccessor.atype(aclass, isSetter);
            if (caclass != null)
                type = type.insertParameterTypes(0, Class.class);
            MethodHandle mh;
            try {
                mh = IMPL_LOOKUP.findStatic(FieldAccessor.class, name, type);
            } catch (ReflectiveOperationException ex) {
                throw uncaughtException(ex);
            }
            if (caclass != null) {
                MethodType strongType = FieldAccessor.atype(caclass, isSetter);
                mh = mh.bindTo(caclass);
                mh = convertArguments(mh, strongType, 0);
            }
            return mh;
        }
    }

    /** Bind a predetermined first argument to the given direct method handle.
     * Callable only from MethodHandles.
     * @param token Proof that the caller has access to this package.
     * @param target Any direct method handle.
     * @param receiver Receiver (or first static method argument) to pre-bind.
     * @return a BoundMethodHandle for the given DirectMethodHandle, or null if it does not exist
     */
    static
    MethodHandle bindReceiver(MethodHandle target, Object receiver) {
        if (receiver == null)  return null;
        if (target instanceof AdapterMethodHandle &&
            ((AdapterMethodHandle)target).conversionOp() == MethodHandleNatives.Constants.OP_RETYPE_ONLY
            ) {
            Object info = MethodHandleNatives.getTargetInfo(target);
            if (info instanceof DirectMethodHandle) {
                DirectMethodHandle dmh = (DirectMethodHandle) info;
                if (dmh.type().parameterType(0).isAssignableFrom(receiver.getClass())) {
                    MethodHandle bmh = new BoundMethodHandle(dmh, receiver, 0);
                    MethodType newType = target.type().dropParameterTypes(0, 1);
                    return convertArguments(bmh, newType, bmh.type(), 0);
                }
            }
        }
        if (target instanceof DirectMethodHandle)
            return new BoundMethodHandle((DirectMethodHandle)target, receiver, 0);
        return null;   // let caller try something else
    }

    /** Bind a predetermined argument to the given arbitrary method handle.
     * Callable only from MethodHandles.
     * @param token Proof that the caller has access to this package.
     * @param target Any method handle.
     * @param receiver Argument (which can be a boxed primitive) to pre-bind.
     * @return a suitable BoundMethodHandle
     */
    static
    MethodHandle bindArgument(MethodHandle target, int argnum, Object receiver) {
        return new BoundMethodHandle(target, receiver, argnum);
    }

    static MethodHandle permuteArguments(MethodHandle target,
                                                MethodType newType,
                                                MethodType oldType,
                                                int[] permutationOrNull) {
        assert(oldType.parameterCount() == target.type().parameterCount());
        int outargs = oldType.parameterCount(), inargs = newType.parameterCount();
        if (permutationOrNull.length != outargs)
            throw newIllegalArgumentException("wrong number of arguments in permutation");
        // Make the individual outgoing argument types match up first.
        Class<?>[] callTypeArgs = new Class<?>[outargs];
        for (int i = 0; i < outargs; i++)
            callTypeArgs[i] = newType.parameterType(permutationOrNull[i]);
        MethodType callType = MethodType.methodType(oldType.returnType(), callTypeArgs);
        target = convertArguments(target, callType, oldType, 0);
        assert(target != null);
        oldType = target.type();
        List<Integer> goal = new ArrayList<Integer>();  // i*TOKEN
        List<Integer> state = new ArrayList<Integer>(); // i*TOKEN
        List<Integer> drops = new ArrayList<Integer>(); // not tokens
        List<Integer> dups = new ArrayList<Integer>();  // not tokens
        final int TOKEN = 10; // to mark items which are symbolic only
        // state represents the argument values coming into target
        for (int i = 0; i < outargs; i++) {
            state.add(permutationOrNull[i] * TOKEN);
        }
        // goal represents the desired state
        for (int i = 0; i < inargs; i++) {
            if (state.contains(i * TOKEN)) {
                goal.add(i * TOKEN);
            } else {
                // adapter must initially drop all unused arguments
                drops.add(i);
            }
        }
        // detect duplications
        while (state.size() > goal.size()) {
            for (int i2 = 0; i2 < state.size(); i2++) {
                int arg1 = state.get(i2);
                int i1 = state.indexOf(arg1);
                if (i1 != i2) {
                    // found duplicate occurrence at i2
                    int arg2 = (inargs++) * TOKEN;
                    state.set(i2, arg2);
                    dups.add(goal.indexOf(arg1));
                    goal.add(arg2);
                }
            }
        }
        assert(state.size() == goal.size());
        int size = goal.size();
        while (!state.equals(goal)) {
            // Look for a maximal sequence of adjacent misplaced arguments,
            // and try to rotate them into place.
            int bestRotArg = -10 * TOKEN, bestRotLen = 0;
            int thisRotArg = -10 * TOKEN, thisRotLen = 0;
            for (int i = 0; i < size; i++) {
                int arg = state.get(i);
                // Does this argument match the current run?
                if (arg == thisRotArg + TOKEN) {
                    thisRotArg = arg;
                    thisRotLen += 1;
                    if (bestRotLen < thisRotLen) {
                        bestRotLen = thisRotLen;
                        bestRotArg = thisRotArg;
                    }
                } else {
                    // The old sequence (if any) stops here.
                    thisRotLen = 0;
                    thisRotArg = -10 * TOKEN;
                    // But maybe a new one starts here also.
                    int wantArg = goal.get(i);
                    final int MAX_ARG_ROTATION = AdapterMethodHandle.MAX_ARG_ROTATION;
                    if (arg != wantArg &&
                        arg >= wantArg - TOKEN * MAX_ARG_ROTATION &&
                        arg <= wantArg + TOKEN * MAX_ARG_ROTATION) {
                        thisRotArg = arg;
                        thisRotLen = 1;
                    }
                }
            }
            if (bestRotLen >= 2) {
                // Do a rotation if it can improve argument positioning
                // by at least 2 arguments.  This is not always optimal,
                // but it seems to catch common cases.
                int dstEnd = state.indexOf(bestRotArg);
                int srcEnd = goal.indexOf(bestRotArg);
                int rotBy = dstEnd - srcEnd;
                int dstBeg = dstEnd - (bestRotLen - 1);
                int srcBeg = srcEnd - (bestRotLen - 1);
                assert((dstEnd | dstBeg | srcEnd | srcBeg) >= 0); // no negs
                // Make a span which covers both source and destination.
                int rotBeg = Math.min(dstBeg, srcBeg);
                int rotEnd = Math.max(dstEnd, srcEnd);
                int score = 0;
                for (int i = rotBeg; i <= rotEnd; i++) {
                    if ((int)state.get(i) != (int)goal.get(i))
                        score += 1;
                }
                List<Integer> rotSpan = state.subList(rotBeg, rotEnd+1);
                Collections.rotate(rotSpan, -rotBy);  // reverse direction
                for (int i = rotBeg; i <= rotEnd; i++) {
                    if ((int)state.get(i) != (int)goal.get(i))
                        score -= 1;
                }
                if (score >= 2) {
                    // Improved at least two argument positions.  Do it.
                    List<Class<?>> ptypes = Arrays.asList(oldType.parameterArray());
                    Collections.rotate(ptypes.subList(rotBeg, rotEnd+1), -rotBy);
                    MethodType rotType = MethodType.methodType(oldType.returnType(), ptypes);
                    MethodHandle nextTarget
                            = AdapterMethodHandle.makeRotateArguments(rotType, target,
                                    rotBeg, rotSpan.size(), rotBy);
                    if (nextTarget != null) {
                        //System.out.println("Rot: "+rotSpan+" by "+rotBy);
                        target = nextTarget;
                        oldType = rotType;
                        continue;
                    }
                }
                // Else de-rotate, and drop through to the swap-fest.
                Collections.rotate(rotSpan, rotBy);
            }

            // Now swap like the wind!
            List<Class<?>> ptypes = Arrays.asList(oldType.parameterArray());
            for (int i = 0; i < size; i++) {
                // What argument do I want here?
                int arg = goal.get(i);
                if (arg != state.get(i)) {
                    // Where is it now?
                    int j = state.indexOf(arg);
                    Collections.swap(ptypes, i, j);
                    MethodType swapType = MethodType.methodType(oldType.returnType(), ptypes);
                    target = AdapterMethodHandle.makeSwapArguments(swapType, target, i, j);
                    if (target == null)  throw newIllegalArgumentException("cannot swap");
                    assert(target.type() == swapType);
                    oldType = swapType;
                    Collections.swap(state, i, j);
                }
            }
            // One pass of swapping must finish the job.
            assert(state.equals(goal));
        }
        while (!dups.isEmpty()) {
            // Grab a contiguous trailing sequence of dups.
            int grab = dups.size() - 1;
            int dupArgPos = dups.get(grab), dupArgCount = 1;
            while (grab - 1 >= 0) {
                int dup0 = dups.get(grab - 1);
                if (dup0 != dupArgPos - 1)  break;
                dupArgPos -= 1;
                dupArgCount += 1;
                grab -= 1;
            }
            //if (dupArgCount > 1)  System.out.println("Dup: "+dups.subList(grab, dups.size()));
            dups.subList(grab, dups.size()).clear();
            // In the new target type drop that many args from the tail:
            List<Class<?>> ptypes = oldType.parameterList();
            ptypes = ptypes.subList(0, ptypes.size() - dupArgCount);
            MethodType dupType = MethodType.methodType(oldType.returnType(), ptypes);
            target = AdapterMethodHandle.makeDupArguments(dupType, target, dupArgPos, dupArgCount);
            if (target == null)
                throw newIllegalArgumentException("cannot dup");
            oldType = target.type();
        }
        while (!drops.isEmpty()) {
            // Grab a contiguous initial sequence of drops.
            int dropArgPos = drops.get(0), dropArgCount = 1;
            while (dropArgCount < drops.size()) {
                int drop1 = drops.get(dropArgCount);
                if (drop1 != dropArgPos + dropArgCount)  break;
                dropArgCount += 1;
            }
            //if (dropArgCount > 1)  System.out.println("Drop: "+drops.subList(0, dropArgCount));
            drops.subList(0, dropArgCount).clear();
            List<Class<?>> dropTypes = newType.parameterList()
                    .subList(dropArgPos, dropArgPos + dropArgCount);
            MethodType dropType = oldType.insertParameterTypes(dropArgPos, dropTypes);
            target = AdapterMethodHandle.makeDropArguments(dropType, target, dropArgPos, dropArgCount);
            if (target == null)  throw newIllegalArgumentException("cannot drop");
            oldType = target.type();
        }
        target = convertArguments(target, newType, oldType, 0);
        assert(target != null);
        return target;
    }

    /*non-public*/ static
    MethodHandle convertArguments(MethodHandle target, MethodType newType, int level) {
        MethodType oldType = target.type();
        if (oldType.equals(newType))
            return target;
        assert(level > 1 || oldType.isConvertibleTo(newType));
        MethodHandle retFilter = null;
        Class<?> oldRT = oldType.returnType();
        Class<?> newRT = newType.returnType();
        if (!VerifyType.isNullConversion(oldRT, newRT)) {
            if (oldRT == void.class) {
                Wrapper wrap = newRT.isPrimitive() ? Wrapper.forPrimitiveType(newRT) : Wrapper.OBJECT;
                retFilter = ValueConversions.zeroConstantFunction(wrap);
            } else {
                retFilter = MethodHandles.identity(newRT);
                retFilter = convertArguments(retFilter, retFilter.type().changeParameterType(0, oldRT), level);
            }
            newType = newType.changeReturnType(oldRT);
        }
        MethodHandle res = null;
        Exception ex = null;
        try {
            res = convertArguments(target, newType, oldType, level);
        } catch (IllegalArgumentException ex1) {
            ex = ex1;
        }
        if (res == null) {
            WrongMethodTypeException wmt = new WrongMethodTypeException("cannot convert to "+newType+": "+target);
            wmt.initCause(ex);
            throw wmt;
        }
        if (retFilter != null)
            res = MethodHandles.filterReturnValue(res, retFilter);
        return res;
    }

    static MethodHandle convertArguments(MethodHandle target,
                                                MethodType newType,
                                                MethodType oldType,
                                                int level) {
        assert(oldType.parameterCount() == target.type().parameterCount());
        if (newType == oldType)
            return target;
        if (oldType.parameterCount() != newType.parameterCount())
            throw newIllegalArgumentException("mismatched parameter count", oldType, newType);
        MethodHandle res = AdapterMethodHandle.makePairwiseConvert(newType, target, level);
        if (res != null)
            return res;
        // We can come here in the case of target(int)void => (Object)void,
        // because the unboxing logic for Object => int is complex.
        int argc = oldType.parameterCount();
        assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
        // The JVM can't do it directly, so fill in the gap with a Java adapter.
        // TO DO: figure out what to put here from case-by-case experience
        // Use a heavier method:  Convert all the arguments to Object,
        // then back to the desired types.  We might have to use Java-based
        // method handles to do this.
        MethodType objType = MethodType.genericMethodType(argc);
        MethodHandle objTarget = AdapterMethodHandle.makePairwiseConvert(objType, target, level);
        if (objTarget == null)
            objTarget = FromGeneric.make(target);
        res = AdapterMethodHandle.makePairwiseConvert(newType, objTarget, level);
        if (res != null)
            return res;
        return ToGeneric.make(newType, objTarget);
    }

    static MethodHandle spreadArguments(MethodHandle target, Class<?> arrayType, int arrayLength) {
        MethodType oldType = target.type();
        int nargs = oldType.parameterCount();
        int keepPosArgs = nargs - arrayLength;
        MethodType newType = oldType
                .dropParameterTypes(keepPosArgs, nargs)
                .insertParameterTypes(keepPosArgs, arrayType);
        return spreadArguments(target, newType, keepPosArgs, arrayType, arrayLength);
    }
    static MethodHandle spreadArgumentsFromPos(MethodHandle target, MethodType newType, int spreadArgPos) {
        int arrayLength = target.type().parameterCount() - spreadArgPos;
        return spreadArguments(target, newType, spreadArgPos, Object[].class, arrayLength);
    }
    static MethodHandle spreadArguments(MethodHandle target,
                                               MethodType newType,
                                               int spreadArgPos,
                                               Class<?> arrayType,
                                               int arrayLength) {
        // TO DO: maybe allow the restarg to be Object and implicitly cast to Object[]
        MethodType oldType = target.type();
        // spread the last argument of newType to oldType
        assert(arrayLength == oldType.parameterCount() - spreadArgPos);
        assert(newType.parameterType(spreadArgPos) == arrayType);
        return AdapterMethodHandle.makeSpreadArguments(newType, target, arrayType, spreadArgPos, arrayLength);
    }

    static MethodHandle collectArguments(MethodHandle target,
                                                int collectArg,
                                                MethodHandle collector) {
        MethodType type = target.type();
        Class<?> collectType = collector.type().returnType();
        assert(collectType != void.class);  // else use foldArguments
        if (collectType != type.parameterType(collectArg))
            target = target.asType(type.changeParameterType(collectArg, collectType));
        MethodType newType = type
                .dropParameterTypes(collectArg, collectArg+1)
                .insertParameterTypes(collectArg, collector.type().parameterArray());
        return collectArguments(target, newType, collectArg, collector);
    }
    static MethodHandle collectArguments(MethodHandle target,
                                                MethodType newType,
                                                int collectArg,
                                                MethodHandle collector) {
        MethodType oldType = target.type();     // (a...,c)=>r
        //         newType                      // (a..., b...)=>r
        MethodType colType = collector.type();  // (b...)=>c
        //         oldType                      // (a..., b...)=>r
        assert(newType.parameterCount() == collectArg + colType.parameterCount());
        assert(oldType.parameterCount() == collectArg + 1);
        MethodHandle result = null;
        if (AdapterMethodHandle.canCollectArguments(oldType, colType, collectArg, false)) {
            result = AdapterMethodHandle.makeCollectArguments(target, collector, collectArg, false);
        }
        if (result == null) {
            assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
            MethodHandle gtarget = convertArguments(target, oldType.generic(), oldType, 0);
            MethodHandle gcollector = convertArguments(collector, colType.generic(), colType, 0);
            if (gtarget == null || gcollector == null)  return null;
            MethodHandle gresult = FilterGeneric.makeArgumentCollector(gcollector, gtarget);
            result = convertArguments(gresult, newType, gresult.type(), 0);
        }
        return result;
    }

    static MethodHandle filterArgument(MethodHandle target,
                                       int pos,
                                       MethodHandle filter) {
        MethodType ttype = target.type();
        MethodType ftype = filter.type();
        assert(ftype.parameterCount() == 1);
        MethodType rtype = ttype.changeParameterType(pos, ftype.parameterType(0));
        MethodType gttype = ttype.generic();
        if (ttype != gttype) {
            target = convertArguments(target, gttype, ttype, 0);
            ttype = gttype;
        }
        MethodType gftype = ftype.generic();
        if (ftype != gftype) {
            filter = convertArguments(filter, gftype, ftype, 0);
            ftype = gftype;
        }
        MethodHandle result = null;
        if (AdapterMethodHandle.canCollectArguments(ttype, ftype, pos, false)) {
            result = AdapterMethodHandle.makeCollectArguments(target, filter, pos, false);
        }
        if (result == null) {
            assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
            if (ftype == ttype) {
            // simple unary case
                result = FilterOneArgument.make(filter, target);
            } else {
                result = FilterGeneric.makeArgumentFilter(pos, filter, target);
            }
        }
        if (result.type() != rtype)
            result = result.asType(rtype);
        return result;
    }

    static MethodHandle foldArguments(MethodHandle target,
                                      MethodType newType,
                                      int foldPos,
                                      MethodHandle combiner) {
        MethodType oldType = target.type();
        MethodType ctype = combiner.type();
        if (AdapterMethodHandle.canCollectArguments(oldType, ctype, foldPos, true)) {
            MethodHandle res = AdapterMethodHandle.makeCollectArguments(target, combiner, foldPos, true);
            if (res != null)  return res;
        }
        assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
        if (foldPos != 0)  return null;
        MethodHandle gtarget = convertArguments(target, oldType.generic(), oldType, 0);
        MethodHandle gcombiner = convertArguments(combiner, ctype.generic(), ctype, 0);
        if (ctype.returnType() == void.class) {
            gtarget = dropArguments(gtarget, oldType.generic().insertParameterTypes(foldPos, Object.class), foldPos);
        }
        if (gtarget == null || gcombiner == null)  return null;
        MethodHandle gresult = FilterGeneric.makeArgumentFolder(gcombiner, gtarget);
        return convertArguments(gresult, newType, gresult.type(), 0);
    }

    static
    MethodHandle dropArguments(MethodHandle target,
                               MethodType newType, int argnum) {
        int drops = newType.parameterCount() - target.type().parameterCount();
        MethodHandle res = AdapterMethodHandle.makeDropArguments(newType, target, argnum, drops);
        if (res != null)
            return res;
        throw new UnsupportedOperationException("NYI");
    }

    private static class GuardWithTest extends BoundMethodHandle {
        private final MethodHandle test, target, fallback;
        private GuardWithTest(MethodHandle invoker,
                              MethodHandle test, MethodHandle target, MethodHandle fallback) {
            super(invoker);
            this.test = test;
            this.target = target;
            this.fallback = fallback;
        }
        static boolean preferRicochetFrame(MethodType type) {
            return (type.parameterCount() >= INVOKES.length || type.hasPrimitives());
        }
        static MethodHandle make(MethodHandle test, MethodHandle target, MethodHandle fallback) {
            MethodType type = target.type();
            int nargs = type.parameterCount();
            if (nargs < INVOKES.length) {
                if (preferRicochetFrame(type))
                    assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
                MethodHandle invoke = INVOKES[nargs];
                MethodType gtype = type.generic();
                assert(invoke.type().dropParameterTypes(0,1) == gtype);
                MethodHandle gtest = convertArguments(test, gtype.changeReturnType(boolean.class), test.type(), 0);
                MethodHandle gtarget = convertArguments(target, gtype, type, 0);
                MethodHandle gfallback = convertArguments(fallback, gtype, type, 0);
                if (gtest == null || gtarget == null || gfallback == null)  return null;
                MethodHandle gguard = new GuardWithTest(invoke, gtest, gtarget, gfallback);
                return convertArguments(gguard, type, gtype, 0);
            } else {
                assert(MethodHandleNatives.workaroundWithoutRicochetFrames());  // this code is deprecated
                MethodHandle invoke = VARARGS_INVOKE;
                MethodType gtype = MethodType.genericMethodType(1);
                assert(invoke.type().dropParameterTypes(0,1) == gtype);
                MethodHandle gtest = spreadArgumentsFromPos(test, gtype.changeReturnType(boolean.class), 0);
                MethodHandle gtarget = spreadArgumentsFromPos(target, gtype, 0);
                MethodHandle gfallback = spreadArgumentsFromPos(fallback, gtype, 0);
                MethodHandle gguard = new GuardWithTest(invoke, gtest, gtarget, gfallback);
                if (gtest == null || gtarget == null || gfallback == null)  return null;
                return collectArguments(gguard, type, 0, null);
            }
        }
        @Override
        String debugString() {
            return addTypeString(target, this);
        }
        private Object invoke_V(Object... av) throws Throwable {
            if ((boolean) test.invokeExact(av))
                return target.invokeExact(av);
            return fallback.invokeExact(av);
        }
        private Object invoke_L0() throws Throwable {
            if ((boolean) test.invokeExact())
                return target.invokeExact();
            return fallback.invokeExact();
        }
        private Object invoke_L1(Object a0) throws Throwable {
            if ((boolean) test.invokeExact(a0))
                return target.invokeExact(a0);
            return fallback.invokeExact(a0);
        }
        private Object invoke_L2(Object a0, Object a1) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1))
                return target.invokeExact(a0, a1);
            return fallback.invokeExact(a0, a1);
        }
        private Object invoke_L3(Object a0, Object a1, Object a2) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2))
                return target.invokeExact(a0, a1, a2);
            return fallback.invokeExact(a0, a1, a2);
        }
        private Object invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2, a3))
                return target.invokeExact(a0, a1, a2, a3);
            return fallback.invokeExact(a0, a1, a2, a3);
        }
        private Object invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2, a3, a4))
                return target.invokeExact(a0, a1, a2, a3, a4);
            return fallback.invokeExact(a0, a1, a2, a3, a4);
        }
        private Object invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2, a3, a4, a5))
                return target.invokeExact(a0, a1, a2, a3, a4, a5);
            return fallback.invokeExact(a0, a1, a2, a3, a4, a5);
        }
        private Object invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2, a3, a4, a5, a6))
                return target.invokeExact(a0, a1, a2, a3, a4, a5, a6);
            return fallback.invokeExact(a0, a1, a2, a3, a4, a5, a6);
        }
        private Object invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable {
            if ((boolean) test.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7))
                return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7);
            return fallback.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7);
        }
        static MethodHandle[] makeInvokes() {
            ArrayList<MethodHandle> invokes = new ArrayList<MethodHandle>();
            MethodHandles.Lookup lookup = IMPL_LOOKUP;
            for (;;) {
                int nargs = invokes.size();
                String name = "invoke_L"+nargs;
                MethodHandle invoke = null;
                try {
                    invoke = lookup.findVirtual(GuardWithTest.class, name, MethodType.genericMethodType(nargs));
                } catch (ReflectiveOperationException ex) {
                }
                if (invoke == null)  break;
                invokes.add(invoke);
            }
            assert(invokes.size() == 9);  // current number of methods
            return invokes.toArray(new MethodHandle[0]);
        };
        static final MethodHandle[] INVOKES = makeInvokes();
        // For testing use this:
        //static final MethodHandle[] INVOKES = Arrays.copyOf(makeInvokes(), 2);
        static final MethodHandle VARARGS_INVOKE;
        static {
            try {
                VARARGS_INVOKE = IMPL_LOOKUP.findVirtual(GuardWithTest.class, "invoke_V", MethodType.genericMethodType(0, true));
            } catch (ReflectiveOperationException ex) {
                throw uncaughtException(ex);
            }
        }
    }

    static
    MethodHandle selectAlternative(boolean testResult, MethodHandle target, MethodHandle fallback) {
        return testResult ? target : fallback;
    }

    static MethodHandle SELECT_ALTERNATIVE;
    static MethodHandle selectAlternative() {
        if (SELECT_ALTERNATIVE != null)  return SELECT_ALTERNATIVE;
        try {
            SELECT_ALTERNATIVE
            = IMPL_LOOKUP.findStatic(MethodHandleImpl.class, "selectAlternative",
                    MethodType.methodType(MethodHandle.class, boolean.class, MethodHandle.class, MethodHandle.class));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return SELECT_ALTERNATIVE;
    }

    static
    MethodHandle makeGuardWithTest(MethodHandle test,
                                   MethodHandle target,
                                   MethodHandle fallback) {
        // gwt(arg...)
        // [fold]=> continueAfterTest(z=test(arg...), arg...)
        // [filter]=> (tf=select(z))(arg...)
        //    where select(z) = select(z, t, f).bindTo(t, f) => z ? t f
        // [tailcall]=> tf(arg...)
        assert(test.type().returnType() == boolean.class);
        MethodType targetType = target.type();
        MethodType foldTargetType = targetType.insertParameterTypes(0, boolean.class);
        if (AdapterMethodHandle.canCollectArguments(foldTargetType, test.type(), 0, true)
            && GuardWithTest.preferRicochetFrame(targetType)) {
            // working backwards, as usual:
            assert(target.type().equals(fallback.type()));
            MethodHandle tailcall = MethodHandles.exactInvoker(target.type());
            MethodHandle select = selectAlternative();
            select = bindArgument(select, 2, fallback);
            select = bindArgument(select, 1, target);
            // select(z: boolean) => (z ? target : fallback)
            MethodHandle filter = filterArgument(tailcall, 0, select);
            assert(filter.type().parameterType(0) == boolean.class);
            MethodHandle fold = foldArguments(filter, filter.type().dropParameterTypes(0, 1), 0, test);
            return fold;
        }
        return GuardWithTest.make(test, target, fallback);
    }

    private static class GuardWithCatch extends BoundMethodHandle {
        private final MethodHandle target;
        private final Class<? extends Throwable> exType;
        private final MethodHandle catcher;
        GuardWithCatch(MethodHandle target, Class<? extends Throwable> exType, MethodHandle catcher) {
            this(INVOKES[target.type().parameterCount()], target, exType, catcher);
        }
        // FIXME: Build the control flow out of foldArguments.
        GuardWithCatch(MethodHandle invoker,
                       MethodHandle target, Class<? extends Throwable> exType, MethodHandle catcher) {
            super(invoker);
            this.target = target;
            this.exType = exType;
            this.catcher = catcher;
        }
        @Override
        String debugString() {
            return addTypeString(target, this);
        }
        private Object invoke_V(Object... av) throws Throwable {
            try {
                return target.invokeExact(av);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, av);
            }
        }
        private Object invoke_L0() throws Throwable {
            try {
                return target.invokeExact();
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t);
            }
        }
        private Object invoke_L1(Object a0) throws Throwable {
            try {
                return target.invokeExact(a0);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0);
            }
        }
        private Object invoke_L2(Object a0, Object a1) throws Throwable {
            try {
                return target.invokeExact(a0, a1);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1);
            }
        }
        private Object invoke_L3(Object a0, Object a1, Object a2) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2);
            }
        }
        private Object invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2, a3);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2, a3);
            }
        }
        private Object invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2, a3, a4);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2, a3, a4);
            }
        }
        private Object invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2, a3, a4, a5);
            }
        }
        private Object invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2, a3, a4, a5, a6);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2, a3, a4, a5, a6);
            }
        }
        private Object invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable {
            try {
                return target.invokeExact(a0, a1, a2, a3, a4, a5, a6, a7);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.invokeExact(t, a0, a1, a2, a3, a4, a5, a6, a7);
            }
        }
        static MethodHandle[] makeInvokes() {
            ArrayList<MethodHandle> invokes = new ArrayList<MethodHandle>();
            MethodHandles.Lookup lookup = IMPL_LOOKUP;
            for (;;) {
                int nargs = invokes.size();
                String name = "invoke_L"+nargs;
                MethodHandle invoke = null;
                try {
                    invoke = lookup.findVirtual(GuardWithCatch.class, name, MethodType.genericMethodType(nargs));
                } catch (ReflectiveOperationException ex) {
                }
                if (invoke == null)  break;
                invokes.add(invoke);
            }
            assert(invokes.size() == 9);  // current number of methods
            return invokes.toArray(new MethodHandle[0]);
        };
        static final MethodHandle[] INVOKES = makeInvokes();
        // For testing use this:
        //static final MethodHandle[] INVOKES = Arrays.copyOf(makeInvokes(), 2);
        static final MethodHandle VARARGS_INVOKE;
        static {
            try {
                VARARGS_INVOKE = IMPL_LOOKUP.findVirtual(GuardWithCatch.class, "invoke_V", MethodType.genericMethodType(0, true));
            } catch (ReflectiveOperationException ex) {
                throw uncaughtException(ex);
            }
        }
    }


    static
    MethodHandle makeGuardWithCatch(MethodHandle target,
                                    Class<? extends Throwable> exType,
                                    MethodHandle catcher) {
        MethodType type = target.type();
        MethodType ctype = catcher.type();
        int nargs = type.parameterCount();
        if (nargs < GuardWithCatch.INVOKES.length) {
            MethodType gtype = type.generic();
            MethodType gcatchType = gtype.insertParameterTypes(0, Throwable.class);
            MethodHandle gtarget = convertArguments(target, gtype, type, 0);
            MethodHandle gcatcher = convertArguments(catcher, gcatchType, ctype, 0);
            MethodHandle gguard = new GuardWithCatch(gtarget, exType, gcatcher);
            if (gtarget == null || gcatcher == null || gguard == null)  return null;
            return convertArguments(gguard, type, gtype, 0);
        } else {
            MethodType gtype = MethodType.genericMethodType(0, true);
            MethodType gcatchType = gtype.insertParameterTypes(0, Throwable.class);
            MethodHandle gtarget = spreadArgumentsFromPos(target, gtype, 0);
            catcher = catcher.asType(ctype.changeParameterType(0, Throwable.class));
            MethodHandle gcatcher = spreadArgumentsFromPos(catcher, gcatchType, 1);
            MethodHandle gguard = new GuardWithCatch(GuardWithCatch.VARARGS_INVOKE, gtarget, exType, gcatcher);
            if (gtarget == null || gcatcher == null || gguard == null)  return null;
            return collectArguments(gguard, type, 0, ValueConversions.varargsArray(nargs)).asType(type);
        }
    }

    static
    MethodHandle throwException(MethodType type) {
        return AdapterMethodHandle.makeRetypeRaw(type, throwException());
    }

    static MethodHandle THROW_EXCEPTION;
    static MethodHandle throwException() {
        if (THROW_EXCEPTION != null)  return THROW_EXCEPTION;
        try {
            THROW_EXCEPTION
            = IMPL_LOOKUP.findStatic(MethodHandleImpl.class, "throwException",
                    MethodType.methodType(Empty.class, Throwable.class));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return THROW_EXCEPTION;
    }
    static <T extends Throwable> Empty throwException(T t) throws T { throw t; }

    // Linkage support:
    static void registerBootstrap(Class<?> callerClass, MethodHandle bootstrapMethod) {
        MethodHandleNatives.registerBootstrap(callerClass, bootstrapMethod);
    }
    static MethodHandle getBootstrap(Class<?> callerClass) {
        return MethodHandleNatives.getBootstrap(callerClass);
    }
}
