/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.Wrapper;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
 import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Shared information for a group of method types, which differ
 * only by reference types, and therefore share a common erasure
 * and wrapping.
 * <p>
 * For an empirical discussion of the structure of method types,
 * see <a href="http://groups.google.com/group/jvm-languages/browse_thread/thread/ac9308ae74da9b7e/">
 * the thread "Avoiding Boxing" on jvm-languages</a>.
 * There are approximately 2000 distinct erased method types in the JDK.
 * There are a little over 10 times that number of unerased types.
 * No more than half of these are likely to be loaded at once.
 * @author John Rose
 */
final class MethodTypeForm {
    final int[] argToSlotTable, slotToArgTable;
    final long argCounts;               // packed slot & value counts
    final long primCounts;              // packed prim & double counts
    final int vmslots;                  // total number of parameter slots
    final MethodType erasedType;        // the canonical erasure
    final MethodType basicType;         // the canonical erasure, with primitives simplified

    // Cached adapter information:
    @Stable String typeString;           // argument type signature characters
    @Stable MethodHandle genericInvoker; // JVM hook for inexact invoke
    @Stable MethodHandle basicInvoker;   // cached instance of MH.invokeBasic
    @Stable MethodHandle namedFunctionInvoker; // cached helper for LF.NamedFunction

    // Cached lambda form information, for basic types only:
    final @Stable LambdaForm[] lambdaForms;
    // Indexes into lambdaForms:
    static final int
            LF_INVVIRTUAL     =  0,  // DMH invokeVirtual
            LF_INVSTATIC      =  1,
            LF_INVSPECIAL     =  2,
            LF_NEWINVSPECIAL  =  3,
            LF_INVINTERFACE   =  4,
            LF_INVSTATIC_INIT =  5,  // DMH invokeStatic with <clinit> barrier
            LF_INTERPRET      =  6,  // LF interpreter
            LF_COUNTER        =  7,  // CMH wrapper
            LF_REINVOKE       =  8,  // other wrapper
            LF_EX_LINKER      =  9,  // invokeExact_MT
            LF_EX_INVOKER     = 10,  // invokeExact MH
            LF_GEN_LINKER     = 11,
            LF_GEN_INVOKER    = 12,
            LF_CS_LINKER      = 13,  // linkToCallSite_CS
            LF_MH_LINKER      = 14,  // linkToCallSite_MH
            LF_GWC            = 15,
            LF_LIMIT          = 16;

    public MethodType erasedType() {
        return erasedType;
    }

    public MethodType basicType() {
        return basicType;
    }

    public LambdaForm cachedLambdaForm(int which) {
        return lambdaForms[which];
    }

    synchronized public LambdaForm setCachedLambdaForm(int which, LambdaForm form) {
        // Simulate a CAS, to avoid racy duplication of results.
        LambdaForm prev = lambdaForms[which];
        if (prev != null) return prev;
        return lambdaForms[which] = form;
    }

    public MethodHandle basicInvoker() {
        assert(erasedType == basicType) : "erasedType: " + erasedType + " != basicType: " + basicType;  // primitives must be flattened also
        MethodHandle invoker = basicInvoker;
        if (invoker != null)  return invoker;
        invoker = DirectMethodHandle.make(invokeBasicMethod(basicType));
        basicInvoker = invoker;
        return invoker;
    }

    // This next one is called from LambdaForm.NamedFunction.<init>.
    /*non-public*/ static MemberName invokeBasicMethod(MethodType basicType) {
        assert(basicType == basicType.basicType());
        try {
            // Do approximately the same as this public API call:
            //   Lookup.findVirtual(MethodHandle.class, name, type);
            // But bypass access and corner case checks, since we know exactly what we need.
            return IMPL_LOOKUP.resolveOrFail(REF_invokeVirtual, MethodHandle.class, "invokeBasic", basicType);
         } catch (ReflectiveOperationException ex) {
            throw newInternalError("JVM cannot find invoker for "+basicType, ex);
        }
    }

    /**
     * Build an MTF for a given type, which must have all references erased to Object.
     * This MTF will stand for that type and all un-erased variations.
     * Eagerly compute some basic properties of the type, common to all variations.
     */
    protected MethodTypeForm(MethodType erasedType) {
        this.erasedType = erasedType;

        Class<?>[] ptypes = erasedType.ptypes();
        int ptypeCount = ptypes.length;
        int pslotCount = ptypeCount;            // temp. estimate
        int rtypeCount = 1;                     // temp. estimate
        int rslotCount = 1;                     // temp. estimate

        int[] argToSlotTab = null, slotToArgTab = null;

        // Walk the argument types, looking for primitives.
        int pac = 0, lac = 0, prc = 0, lrc = 0;
        Class<?>[] epts = ptypes;
        Class<?>[] bpts = epts;
        for (int i = 0; i < epts.length; i++) {
            Class<?> pt = epts[i];
            if (pt != Object.class) {
                ++pac;
                Wrapper w = Wrapper.forPrimitiveType(pt);
                if (w.isDoubleWord())  ++lac;
                if (w.isSubwordOrInt() && pt != int.class) {
                    if (bpts == epts)
                        bpts = bpts.clone();
                    bpts[i] = int.class;
                }
            }
        }
        pslotCount += lac;                  // #slots = #args + #longs
        Class<?> rt = erasedType.returnType();
        Class<?> bt = rt;
        if (rt != Object.class) {
            ++prc;          // even void.class counts as a prim here
            Wrapper w = Wrapper.forPrimitiveType(rt);
            if (w.isDoubleWord())  ++lrc;
            if (w.isSubwordOrInt() && rt != int.class)
                bt = int.class;
            // adjust #slots, #args
            if (rt == void.class)
                rtypeCount = rslotCount = 0;
            else
                rslotCount += lrc;
        }
        if (epts == bpts && bt == rt) {
            this.basicType = erasedType;
        } else {
            this.basicType = MethodType.makeImpl(bt, bpts, true);
        }
        if (lac != 0) {
            int slot = ptypeCount + lac;
            slotToArgTab = new int[slot+1];
            argToSlotTab = new int[1+ptypeCount];
            argToSlotTab[0] = slot;  // argument "-1" is past end of slots
            for (int i = 0; i < epts.length; i++) {
                Class<?> pt = epts[i];
                Wrapper w = Wrapper.forBasicType(pt);
                if (w.isDoubleWord())  --slot;
                --slot;
                slotToArgTab[slot] = i+1; // "+1" see argSlotToParameter note
                argToSlotTab[1+i]  = slot;
            }
            assert(slot == 0);  // filled the table
        }
        this.primCounts = pack(lrc, prc, lac, pac);
        this.argCounts = pack(rslotCount, rtypeCount, pslotCount, ptypeCount);
        if (slotToArgTab == null) {
            int slot = ptypeCount; // first arg is deepest in stack
            slotToArgTab = new int[slot+1];
            argToSlotTab = new int[1+ptypeCount];
            argToSlotTab[0] = slot;  // argument "-1" is past end of slots
            for (int i = 0; i < ptypeCount; i++) {
                --slot;
                slotToArgTab[slot] = i+1; // "+1" see argSlotToParameter note
                argToSlotTab[1+i]  = slot;
            }
        }
        this.argToSlotTable = argToSlotTab;
        this.slotToArgTable = slotToArgTab;

        if (pslotCount >= 256)  throw newIllegalArgumentException("too many arguments");

        // send a few bits down to the JVM:
        this.vmslots = parameterSlotCount();

        if (basicType == erasedType) {
            lambdaForms = new LambdaForm[LF_LIMIT];
        } else {
            lambdaForms = null;  // could be basicType.form().lambdaForms;
        }
    }

    private static long pack(int a, int b, int c, int d) {
        assert(((a|b|c|d) & ~0xFFFF) == 0);
        long hw = ((a << 16) | b), lw = ((c << 16) | d);
        return (hw << 32) | lw;
    }
    private static char unpack(long packed, int word) { // word==0 => return a, ==3 => return d
        assert(word <= 3);
        return (char)(packed >> ((3-word) * 16));
    }

    public int parameterCount() {                      // # outgoing values
        return unpack(argCounts, 3);
    }
    public int parameterSlotCount() {                  // # outgoing interpreter slots
        return unpack(argCounts, 2);
    }
    public int returnCount() {                         // = 0 (V), or 1
        return unpack(argCounts, 1);
    }
    public int returnSlotCount() {                     // = 0 (V), 2 (J/D), or 1
        return unpack(argCounts, 0);
    }
    public int primitiveParameterCount() {
        return unpack(primCounts, 3);
    }
    public int longPrimitiveParameterCount() {
        return unpack(primCounts, 2);
    }
    public int primitiveReturnCount() {                // = 0 (obj), or 1
        return unpack(primCounts, 1);
    }
    public int longPrimitiveReturnCount() {            // = 1 (J/D), or 0
        return unpack(primCounts, 0);
    }
    public boolean hasPrimitives() {
        return primCounts != 0;
    }
    public boolean hasNonVoidPrimitives() {
        if (primCounts == 0)  return false;
        if (primitiveParameterCount() != 0)  return true;
        return (primitiveReturnCount() != 0 && returnCount() != 0);
    }
    public boolean hasLongPrimitives() {
        return (longPrimitiveParameterCount() | longPrimitiveReturnCount()) != 0;
    }
    public int parameterToArgSlot(int i) {
        return argToSlotTable[1+i];
    }
    public int argSlotToParameter(int argSlot) {
        // Note:  Empty slots are represented by zero in this table.
        // Valid arguments slots contain incremented entries, so as to be non-zero.
        // We return -1 the caller to mean an empty slot.
        return slotToArgTable[argSlot] - 1;
    }

    static MethodTypeForm findForm(MethodType mt) {
        MethodType erased = canonicalize(mt, ERASE, ERASE);
        if (erased == null) {
            // It is already erased.  Make a new MethodTypeForm.
            return new MethodTypeForm(mt);
        } else {
            // Share the MethodTypeForm with the erased version.
            return erased.form();
        }
    }

    /** Codes for {@link #canonicalize(java.lang.Class, int)}.
     * ERASE means change every reference to {@code Object}.
     * WRAP means convert primitives (including {@code void} to their
     * corresponding wrapper types.  UNWRAP means the reverse of WRAP.
     * INTS means convert all non-void primitive types to int or long,
     * according to size.  LONGS means convert all non-void primitives
     * to long, regardless of size.  RAW_RETURN means convert a type
     * (assumed to be a return type) to int if it is smaller than an int,
     * or if it is void.
     */
    public static final int NO_CHANGE = 0, ERASE = 1, WRAP = 2, UNWRAP = 3, INTS = 4, LONGS = 5, RAW_RETURN = 6;

    /** Canonicalize the types in the given method type.
     * If any types change, intern the new type, and return it.
     * Otherwise return null.
     */
    public static MethodType canonicalize(MethodType mt, int howRet, int howArgs) {
        Class<?>[] ptypes = mt.ptypes();
        Class<?>[] ptc = MethodTypeForm.canonicalizes(ptypes, howArgs);
        Class<?> rtype = mt.returnType();
        Class<?> rtc = MethodTypeForm.canonicalize(rtype, howRet);
        if (ptc == null && rtc == null) {
            // It is already canonical.
            return null;
        }
        // Find the erased version of the method type:
        if (rtc == null)  rtc = rtype;
        if (ptc == null)  ptc = ptypes;
        return MethodType.makeImpl(rtc, ptc, true);
    }

    /** Canonicalize the given return or param type.
     *  Return null if the type is already canonicalized.
     */
    static Class<?> canonicalize(Class<?> t, int how) {
        Class<?> ct;
        if (t == Object.class) {
            // no change, ever
        } else if (!t.isPrimitive()) {
            switch (how) {
                case UNWRAP:
                    ct = Wrapper.asPrimitiveType(t);
                    if (ct != t)  return ct;
                    break;
                case RAW_RETURN:
                case ERASE:
                    return Object.class;
            }
        } else if (t == void.class) {
            // no change, usually
            switch (how) {
                case RAW_RETURN:
                    return int.class;
                case WRAP:
                    return Void.class;
            }
        } else {
            // non-void primitive
            switch (how) {
                case WRAP:
                    return Wrapper.asWrapperType(t);
                case INTS:
                    if (t == int.class || t == long.class)
                        return null;  // no change
                    if (t == double.class)
                        return long.class;
                    return int.class;
                case LONGS:
                    if (t == long.class)
                        return null;  // no change
                    return long.class;
                case RAW_RETURN:
                    if (t == int.class || t == long.class ||
                        t == float.class || t == double.class)
                        return null;  // no change
                    // everything else returns as an int
                    return int.class;
            }
        }
        // no change; return null to signify
        return null;
    }

    /** Canonicalize each param type in the given array.
     *  Return null if all types are already canonicalized.
     */
    static Class<?>[] canonicalizes(Class<?>[] ts, int how) {
        Class<?>[] cs = null;
        for (int imax = ts.length, i = 0; i < imax; i++) {
            Class<?> c = canonicalize(ts[i], how);
            if (c == void.class)
                c = null;  // a Void parameter was unwrapped to void; ignore
            if (c != null) {
                if (cs == null)
                    cs = ts.clone();
                cs[i] = c;
            }
        }
        return cs;
    }

    @Override
    public String toString() {
        return "Form"+erasedType;
    }

}
