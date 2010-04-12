/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.dyn;

import java.dyn.JavaMethodHandle;
import java.dyn.MethodHandle;
import java.dyn.MethodHandles;
import java.dyn.MethodHandles.Lookup;
import java.dyn.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.dyn.util.VerifyType;
import java.dyn.NoAccessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import sun.dyn.empty.Empty;
import sun.dyn.util.ValueConversions;
import sun.dyn.util.Wrapper;
import sun.misc.Unsafe;
import static sun.dyn.MemberName.newIllegalArgumentException;
import static sun.dyn.MemberName.newNoAccessException;

/**
 * Base class for method handles, containing JVM-specific fields and logic.
 * TO DO:  It should not be a base class.
 * @author jrose
 */
public abstract class MethodHandleImpl {

    // Fields which really belong in MethodHandle:
    private byte       vmentry;    // adapter stub or method entry point
    //private int      vmslots;    // optionally, hoist type.form.vmslots
    protected Object   vmtarget;   // VM-specific, class-specific target value
    //MethodType       type;       // defined in MethodHandle

    // TO DO:  vmtarget should be invisible to Java, since the JVM puts internal
    // managed pointers into it.  Making it visible exposes it to debuggers,
    // which can cause errors when they treat the pointer as an Object.

    // These two dummy fields are present to force 'I' and 'J' signatures
    // into this class's constant pool, so they can be transferred
    // to vmentry when this class is loaded.
    static final int  INT_FIELD = 0;
    static final long LONG_FIELD = 0;

    /** Access methods for the internals of MethodHandle, supplied to
     *  MethodHandleImpl as a trusted agent.
     */
    static public interface MethodHandleFriend {
        void initType(MethodHandle mh, MethodType type);
    }
    public static void setMethodHandleFriend(Access token, MethodHandleFriend am) {
        Access.check(token);
        if (METHOD_HANDLE_FRIEND != null)
            throw new InternalError();  // just once
        METHOD_HANDLE_FRIEND = am;
    }
    static private MethodHandleFriend METHOD_HANDLE_FRIEND;

    // NOT public
    static void initType(MethodHandle mh, MethodType type) {
        METHOD_HANDLE_FRIEND.initType(mh, type);
    }

    // type is defined in java.dyn.MethodHandle, which is platform-independent

    // vmentry (a void* field) is used *only* by by the JVM.
    // The JVM adjusts its type to int or long depending on system wordsize.
    // Since it is statically typed as neither int nor long, it is impossible
    // to use this field from Java bytecode.  (Please don't try to, either.)

    // The vmentry is an assembly-language stub which is jumped to
    // immediately after the method type is verified.
    // For a direct MH, this stub loads the vmtarget's entry point
    // and jumps to it.

    /**
     * VM-based method handles must have a security token.
     * This security token can only be obtained by trusted code.
     * Do not create method handles directly; use factory methods.
     */
    public MethodHandleImpl(Access token) {
        Access.check(token);
    }

    /** Initialize the method type form to participate in JVM calls.
     *  This is done once for each erased type.
     */
    public static void init(Access token, MethodType self) {
        Access.check(token);
        if (MethodHandleNatives.JVM_SUPPORT)
            MethodHandleNatives.init(self);
    }

    /// Factory methods to create method handles:

    private static final MemberName.Factory LOOKUP = MemberName.Factory.INSTANCE;

    static private Lookup IMPL_LOOKUP_INIT;

    public static void initLookup(Access token, Lookup lookup) {
        Access.check(token);
        if (IMPL_LOOKUP_INIT != null || lookup.lookupClass() != null)
            throw new InternalError();
        IMPL_LOOKUP_INIT = lookup;
    }

    public static Lookup getLookup(Access token) {
        Access.check(token);
        return IMPL_LOOKUP;
    }

    static {
        // Force initialization of Lookup, so it calls us back as initLookup:
        MethodHandles.publicLookup();
        if (IMPL_LOOKUP_INIT == null)
            throw new InternalError();
    }

    public static void initStatics() {
        // Trigger preceding sequence.
    }

    /** Shared secret with MethodHandles.Lookup, a copy of Lookup.IMPL_LOOKUP. */
    static final Lookup IMPL_LOOKUP = IMPL_LOOKUP_INIT;


    /** Look up a given method.
     * Callable only from java.dyn and related packages.
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
     * @throws NoAccessException if the given method cannot be accessed by the lookup class
     */
    public static
    MethodHandle findMethod(Access token, MemberName method,
            boolean doDispatch, Class<?> lookupClass) {
        Access.check(token);  // only trusted calls
        MethodType mtype = method.getMethodType();
        MethodType rtype = mtype;
        if (method.isStatic()) {
            doDispatch = false;
        } else {
            // adjust the advertised receiver type to be exactly the one requested
            // (in the case of invokespecial, this will be the calling class)
            Class<?> recvType = method.getDeclaringClass();
            mtype = mtype.insertParameterTypes(0, recvType);
            if (method.isConstructor())
                doDispatch = true;
            // FIXME: JVM has trouble building MH.invoke sites for
            // classes off the boot class path
            rtype = mtype;
            if (recvType.getClassLoader() != null)
                rtype = rtype.changeParameterType(0, Object.class);
        }
        DirectMethodHandle mh = new DirectMethodHandle(mtype, method, doDispatch, lookupClass);
        if (!mh.isValid())
            throw newNoAccessException(method, lookupClass);
        MethodHandle rmh = AdapterMethodHandle.makePairwiseConvert(token, rtype, mh);
        if (rmh == null)  throw new InternalError();
        return rmh;
    }

    public static
    MethodHandle accessField(Access token,
                             MemberName member, boolean isSetter,
                             Class<?> lookupClass) {
        Access.check(token);
        // Use sun. misc.Unsafe to dig up the dirt on the field.
        MethodHandle mh = new FieldAccessor(token, member, isSetter);
        return mh;
    }

    public static
    MethodHandle accessArrayElement(Access token,
                                    Class<?> arrayClass, boolean isSetter) {
        Access.check(token);
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
                mhs[0] = MethodHandles.insertArguments(mhs[0], 0, elemClass);
                mhs[1] = MethodHandles.insertArguments(mhs[1], 0, elemClass);
            }
            synchronized (FieldAccessor.ARRAY_CACHE) {}  // memory barrier
            FieldAccessor.ARRAY_CACHE.put(elemClass, mhs);
        }
        return mhs[isSetter ? 1 : 0];
    }

    static final class FieldAccessor<C,V> extends JavaMethodHandle {
        private static final Unsafe unsafe = Unsafe.getUnsafe();
        final Object base;  // for static refs only
        final long offset;
        final String name;

        public FieldAccessor(Access token, MemberName field, boolean isSetter) {
            super(fhandle(field.getDeclaringClass(), field.getFieldType(), isSetter, field.isStatic()));
            this.offset = (long) field.getVMIndex(token);
            this.name = field.getName();
            this.base = staticBase(field);
        }
        public String toString() { return name; }

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

        static Object staticBase(MemberName field) {
            if (!field.isStatic())  return null;
            Class c = field.getDeclaringClass();
            java.lang.reflect.Field f;
            try {
                // FIXME:  Should not have to create 'f' to get this value.
                f = c.getDeclaredField(field.getName());
                return unsafe.staticFieldBase(f);
            } catch (Exception ee) {
                Error e = new InternalError();
                e.initCause(ee);
                throw e;
            }
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
            } catch (NoAccessException ee) {
                Error e = new InternalError("name,type="+name+type);
                e.initCause(ee);
                throw e;
            }
            if (evclass != vclass || (!isStatic && ecclass != cclass)) {
                MethodType strongType = FieldAccessor.ftype(cclass, vclass, isSetter, isStatic);
                strongType = strongType.insertParameterTypes(0, FieldAccessor.class);
                mh = MethodHandles.convertArguments(mh, strongType);
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
            } catch (NoAccessException ee) {
                Error e = new InternalError("name,type="+name+type);
                e.initCause(ee);
                throw e;
            }
            if (caclass != null) {
                MethodType strongType = FieldAccessor.atype(caclass, isSetter);
                mh = MethodHandles.insertArguments(mh, 0, caclass);
                mh = MethodHandles.convertArguments(mh, strongType);
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
    public static
    MethodHandle bindReceiver(Access token,
                              MethodHandle target, Object receiver) {
        Access.check(token);
        if (target instanceof AdapterMethodHandle) {
            Object info = MethodHandleNatives.getTargetInfo(target);
            if (info instanceof DirectMethodHandle) {
                DirectMethodHandle dmh = (DirectMethodHandle) info;
                if (receiver == null ||
                    dmh.type().parameterType(0).isAssignableFrom(receiver.getClass())) {
                    MethodHandle bmh = new BoundMethodHandle(dmh, receiver, 0);
                    MethodType newType = target.type().dropParameterTypes(0, 1);
                    return convertArguments(token, bmh, newType, bmh.type(), null);
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
    public static
    MethodHandle bindArgument(Access token,
                              MethodHandle target, int argnum, Object receiver) {
        Access.check(token);
        return new BoundMethodHandle(target, receiver, argnum);
    }

    public static MethodHandle convertArguments(Access token,
                                                MethodHandle target,
                                                MethodType newType,
                                                MethodType oldType,
                                                int[] permutationOrNull) {
        Access.check(token);
        if (permutationOrNull != null) {
            int outargs = oldType.parameterCount(), inargs = newType.parameterCount();
            if (permutationOrNull.length != outargs)
                throw newIllegalArgumentException("wrong number of arguments in permutation");
            // Make the individual outgoing argument types match up first.
            Class<?>[] callTypeArgs = new Class<?>[outargs];
            for (int i = 0; i < outargs; i++)
                callTypeArgs[i] = newType.parameterType(permutationOrNull[i]);
            MethodType callType = MethodType.methodType(oldType.returnType(), callTypeArgs);
            target = convertArguments(token, target, callType, oldType, null);
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
                                = AdapterMethodHandle.makeRotateArguments(token, rotType, target,
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
                        target = AdapterMethodHandle.makeSwapArguments(token, swapType, target, i, j);
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
                target = AdapterMethodHandle.makeDupArguments(token, dupType, target, dupArgPos, dupArgCount);
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
                target = AdapterMethodHandle.makeDropArguments(token, dropType, target, dropArgPos, dropArgCount);
                if (target == null)  throw newIllegalArgumentException("cannot drop");
                oldType = target.type();
            }
        }
        if (newType == oldType)
            return target;
        if (oldType.parameterCount() != newType.parameterCount())
            throw newIllegalArgumentException("mismatched parameter count");
        MethodHandle res = AdapterMethodHandle.makePairwiseConvert(token, newType, target);
        if (res != null)
            return res;
        int argc = oldType.parameterCount();
        // The JVM can't do it directly, so fill in the gap with a Java adapter.
        // TO DO: figure out what to put here from case-by-case experience
        // Use a heavier method:  Convert all the arguments to Object,
        // then back to the desired types.  We might have to use Java-based
        // method handles to do this.
        MethodType objType = MethodType.genericMethodType(argc);
        MethodHandle objTarget = AdapterMethodHandle.makePairwiseConvert(token, objType, target);
        if (objTarget == null)
            objTarget = FromGeneric.make(target);
        res = AdapterMethodHandle.makePairwiseConvert(token, newType, objTarget);
        if (res != null)
            return res;
        return ToGeneric.make(newType, objTarget);
    }

    public static MethodHandle spreadArguments(Access token,
                                               MethodHandle target,
                                               MethodType newType,
                                               int spreadArg) {
        Access.check(token);
        // TO DO: maybe allow the restarg to be Object and implicitly cast to Object[]
        MethodType oldType = target.type();
        // spread the last argument of newType to oldType
        int spreadCount = oldType.parameterCount() - spreadArg;
        Class<Object[]> spreadArgType = Object[].class;
        MethodHandle res = AdapterMethodHandle.makeSpreadArguments(token, newType, target, spreadArgType, spreadArg, spreadCount);
        if (res != null)
            return res;
        // try an intermediate adapter
        Class<?> spreadType = null;
        if (spreadArg < 0 || spreadArg >= newType.parameterCount()
            || !VerifyType.isSpreadArgType(spreadType = newType.parameterType(spreadArg)))
            throw newIllegalArgumentException("no restarg in "+newType);
        Class<?>[] ptypes = oldType.parameterArray();
        for (int i = 0; i < spreadCount; i++)
            ptypes[spreadArg + i] = VerifyType.spreadArgElementType(spreadType, i);
        MethodType midType = MethodType.methodType(newType.returnType(), ptypes);
        // after spreading, some arguments may need further conversion
        MethodHandle target2 = convertArguments(token, target, midType, oldType, null);
        if (target2 == null)
            throw new UnsupportedOperationException("NYI: convert "+midType+" =calls=> "+oldType);
        res = AdapterMethodHandle.makeSpreadArguments(token, newType, target2, spreadArgType, spreadArg, spreadCount);
        if (res != null)
            return res;
        res = SpreadGeneric.make(target2, spreadCount);
        if (res != null)
            res = convertArguments(token, res, newType, res.type(), null);
        return res;
    }

    public static MethodHandle collectArguments(Access token,
                                                MethodHandle target,
                                                MethodType newType,
                                                int collectArg,
                                                MethodHandle collector) {
        MethodType oldType = target.type();     // (a...,c)=>r
        if (collector == null) {
            int numCollect = newType.parameterCount() - oldType.parameterCount() + 1;
            collector = ValueConversions.varargsArray(numCollect);
        }
        //         newType                      // (a..., b...)=>r
        MethodType colType = collector.type();  // (b...)=>c
        //         oldType                      // (a..., b...)=>r
        assert(newType.parameterCount() == collectArg + colType.parameterCount());
        assert(oldType.parameterCount() == collectArg + 1);
        MethodHandle gtarget = convertArguments(token, target, oldType.generic(), oldType, null);
        MethodHandle gcollector = convertArguments(token, collector, colType.generic(), colType, null);
        if (gtarget == null || gcollector == null)  return null;
        MethodHandle gresult = FilterGeneric.makeArgumentCollector(gcollector, gtarget);
        MethodHandle result = convertArguments(token, gresult, newType, gresult.type(), null);
        return result;
    }

    public static MethodHandle filterArgument(Access token,
                                              MethodHandle target,
                                              int pos,
                                              MethodHandle filter) {
        Access.check(token);
        MethodType ttype = target.type(), gttype = ttype.generic();
        if (ttype != gttype) {
            target = convertArguments(token, target, gttype, ttype, null);
            ttype = gttype;
        }
        MethodType ftype = filter.type(), gftype = ftype.generic();
        if (ftype.parameterCount() != 1)
            throw new InternalError();
        if (ftype != gftype) {
            filter = convertArguments(token, filter, gftype, ftype, null);
            ftype = gftype;
        }
        if (ftype == ttype) {
            // simple unary case
            return FilterOneArgument.make(filter, target);
        }
        return FilterGeneric.makeArgumentFilter(pos, filter, target);
    }

    public static MethodHandle foldArguments(Access token,
                                             MethodHandle target,
                                             MethodType newType,
                                             MethodHandle combiner) {
        Access.check(token);
        MethodType oldType = target.type();
        MethodType ctype = combiner.type();
        MethodHandle gtarget = convertArguments(token, target, oldType.generic(), oldType, null);
        MethodHandle gcombiner = convertArguments(token, combiner, ctype.generic(), ctype, null);
        if (gtarget == null || gcombiner == null)  return null;
        MethodHandle gresult = FilterGeneric.makeArgumentFolder(gcombiner, gtarget);
        MethodHandle result = convertArguments(token, gresult, newType, gresult.type(), null);
        return result;
    }

    public static
    MethodHandle dropArguments(Access token, MethodHandle target,
                               MethodType newType, int argnum) {
        Access.check(token);
        int drops = newType.parameterCount() - target.type().parameterCount();
        MethodHandle res = AdapterMethodHandle.makeDropArguments(token, newType, target, argnum, drops);
        if (res != null)
            return res;
        throw new UnsupportedOperationException("NYI");
    }

    private static class GuardWithTest extends JavaMethodHandle {
        private final MethodHandle test, target, fallback;
        public GuardWithTest(MethodHandle test, MethodHandle target, MethodHandle fallback) {
            this(INVOKES[target.type().parameterCount()], test, target, fallback);
        }
        public GuardWithTest(MethodHandle invoker,
                             MethodHandle test, MethodHandle target, MethodHandle fallback) {
            super(invoker);
            this.test = test;
            this.target = target;
            this.fallback = fallback;
        }
        @Override
        public String toString() {
            return target.toString();
        }
        private Object invoke_V(Object... av) throws Throwable {
            if (test.<boolean>invoke(av))
                return target.<Object>invoke(av);
            return fallback.<Object>invoke(av);
        }
        private Object invoke_L0() throws Throwable {
            if (test.<boolean>invoke())
                return target.<Object>invoke();
            return fallback.<Object>invoke();
        }
        private Object invoke_L1(Object a0) throws Throwable {
            if (test.<boolean>invoke(a0))
                return target.<Object>invoke(a0);
            return fallback.<Object>invoke(a0);
        }
        private Object invoke_L2(Object a0, Object a1) throws Throwable {
            if (test.<boolean>invoke(a0, a1))
                return target.<Object>invoke(a0, a1);
            return fallback.<Object>invoke(a0, a1);
        }
        private Object invoke_L3(Object a0, Object a1, Object a2) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2))
                return target.<Object>invoke(a0, a1, a2);
            return fallback.<Object>invoke(a0, a1, a2);
        }
        private Object invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2, a3))
                return target.<Object>invoke(a0, a1, a2, a3);
            return fallback.<Object>invoke(a0, a1, a2, a3);
        }
        private Object invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2, a3, a4))
                return target.<Object>invoke(a0, a1, a2, a3, a4);
            return fallback.<Object>invoke(a0, a1, a2, a3, a4);
        }
        private Object invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2, a3, a4, a5))
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5);
            return fallback.<Object>invoke(a0, a1, a2, a3, a4, a5);
        }
        private Object invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2, a3, a4, a5, a6))
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5, a6);
            return fallback.<Object>invoke(a0, a1, a2, a3, a4, a5, a6);
        }
        private Object invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable {
            if (test.<boolean>invoke(a0, a1, a2, a3, a4, a5, a6, a7))
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5, a6, a7);
            return fallback.<Object>invoke(a0, a1, a2, a3, a4, a5, a6, a7);
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
                } catch (NoAccessException ex) {
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
            } catch (NoAccessException ex) {
                throw new InternalError("");
            }
        }
    }

    public static
    MethodHandle makeGuardWithTest(Access token,
                                   MethodHandle test,
                                   MethodHandle target,
                                   MethodHandle fallback) {
        Access.check(token);
        MethodType type = target.type();
        int nargs = type.parameterCount();
        if (nargs < GuardWithTest.INVOKES.length) {
            MethodType gtype = type.generic();
            MethodHandle gtest = convertArguments(token, test, gtype.changeReturnType(boolean.class), test.type(), null);
            MethodHandle gtarget = convertArguments(token, target, gtype, type, null);
            MethodHandle gfallback = convertArguments(token, fallback, gtype, type, null);
            if (gtest == null || gtarget == null || gfallback == null)  return null;
            MethodHandle gguard = new GuardWithTest(gtest, gtarget, gfallback);
            return convertArguments(token, gguard, type, gtype, null);
        } else {
            MethodType gtype = MethodType.genericMethodType(0, true);
            MethodHandle gtest = spreadArguments(token, test, gtype.changeReturnType(boolean.class), 0);
            MethodHandle gtarget = spreadArguments(token, target, gtype, 0);
            MethodHandle gfallback = spreadArguments(token, fallback, gtype, 0);
            MethodHandle gguard = new GuardWithTest(GuardWithTest.VARARGS_INVOKE, gtest, gtarget, gfallback);
            if (gtest == null || gtarget == null || gfallback == null)  return null;
            return collectArguments(token, gguard, type, 0, null);
        }
    }

    private static class GuardWithCatch extends JavaMethodHandle {
        private final MethodHandle target;
        private final Class<? extends Throwable> exType;
        private final MethodHandle catcher;
        public GuardWithCatch(MethodHandle target, Class<? extends Throwable> exType, MethodHandle catcher) {
            this(INVOKES[target.type().parameterCount()], target, exType, catcher);
        }
        public GuardWithCatch(MethodHandle invoker,
                              MethodHandle target, Class<? extends Throwable> exType, MethodHandle catcher) {
            super(invoker);
            this.target = target;
            this.exType = exType;
            this.catcher = catcher;
        }
        @Override
        public String toString() {
            return target.toString();
        }
        private Object invoke_V(Object... av) throws Throwable {
            try {
                return target.<Object>invoke(av);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, av);
            }
        }
        private Object invoke_L0() throws Throwable {
            try {
                return target.<Object>invoke();
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t);
            }
        }
        private Object invoke_L1(Object a0) throws Throwable {
            try {
                return target.<Object>invoke(a0);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0);
            }
        }
        private Object invoke_L2(Object a0, Object a1) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1);
            }
        }
        private Object invoke_L3(Object a0, Object a1, Object a2) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2);
            }
        }
        private Object invoke_L4(Object a0, Object a1, Object a2, Object a3) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2, a3);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2, a3);
            }
        }
        private Object invoke_L5(Object a0, Object a1, Object a2, Object a3, Object a4) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2, a3, a4);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2, a3, a4);
            }
        }
        private Object invoke_L6(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2, a3, a4, a5);
            }
        }
        private Object invoke_L7(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5, a6);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2, a3, a4, a5, a6);
            }
        }
        private Object invoke_L8(Object a0, Object a1, Object a2, Object a3, Object a4, Object a5, Object a6, Object a7) throws Throwable {
            try {
                return target.<Object>invoke(a0, a1, a2, a3, a4, a5, a6, a7);
            } catch (Throwable t) {
                if (!exType.isInstance(t))  throw t;
                return catcher.<Object>invoke(t, a0, a1, a2, a3, a4, a5, a6, a7);
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
                } catch (NoAccessException ex) {
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
            } catch (NoAccessException ex) {
                throw new InternalError("");
            }
        }
    }


    public static
    MethodHandle makeGuardWithCatch(Access token,
                                    MethodHandle target,
                                    Class<? extends Throwable> exType,
                                    MethodHandle catcher) {
        Access.check(token);
        MethodType type = target.type();
        MethodType ctype = catcher.type();
        int nargs = type.parameterCount();
        if (nargs < GuardWithCatch.INVOKES.length) {
            MethodType gtype = type.generic();
            MethodType gcatchType = gtype.insertParameterTypes(0, Throwable.class);
            MethodHandle gtarget = convertArguments(token, target, gtype, type, null);
            MethodHandle gcatcher = convertArguments(token, catcher, gcatchType, ctype, null);
            MethodHandle gguard = new GuardWithCatch(gtarget, exType, gcatcher);
            if (gtarget == null || gcatcher == null || gguard == null)  return null;
            return convertArguments(token, gguard, type, gtype, null);
        } else {
            MethodType gtype = MethodType.genericMethodType(0, true);
            MethodType gcatchType = gtype.insertParameterTypes(0, Throwable.class);
            MethodHandle gtarget = spreadArguments(token, target, gtype, 0);
            MethodHandle gcatcher = spreadArguments(token, catcher, gcatchType, 1);
            MethodHandle gguard = new GuardWithCatch(GuardWithCatch.VARARGS_INVOKE, gtarget, exType, gcatcher);
            if (gtarget == null || gcatcher == null || gguard == null)  return null;
            return collectArguments(token, gguard, type, 0, null);
        }
    }

    public static
    MethodHandle throwException(Access token, MethodType type) {
        Access.check(token);
        return AdapterMethodHandle.makeRetypeRaw(token, type, THROW_EXCEPTION);
    }

    static final MethodHandle THROW_EXCEPTION
            = IMPL_LOOKUP.findStatic(MethodHandleImpl.class, "throwException",
                    MethodType.methodType(Empty.class, Throwable.class));
    static <T extends Throwable> Empty throwException(T t) throws T { throw t; }

    public static String getNameString(Access token, MethodHandle target) {
        Access.check(token);
        MemberName name = null;
        if (target != null)
            name = MethodHandleNatives.getMethodName(target);
        if (name == null)
            return "<unknown>";
        return name.getName();
    }

    public static String addTypeString(MethodHandle target) {
        if (target == null)  return "null";
        return target.toString() + target.type();
    }

    public static void checkSpreadArgument(Object av, int n) {
        if (av == null ? n != 0 : ((Object[])av).length != n)
            throw newIllegalArgumentException("Array is not of length "+n);
    }

    public static void raiseException(int code, Object actual, Object required) {
        String message;
        // disregard the identity of the actual object, if it is not a class:
        if (!(actual instanceof Class) && !(actual instanceof MethodType))
            actual = actual.getClass();
        if (actual != null)
            message = "required "+required+" but encountered "+actual;
        else
            message = "required "+required;
        switch (code) {
        case 192: // checkcast
            throw new ClassCastException(message);
        default:
            throw new InternalError("unexpected code "+code+": "+message);
        }
    }
}
