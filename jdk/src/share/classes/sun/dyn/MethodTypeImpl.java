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

import java.dyn.*;
import sun.dyn.util.Wrapper;
import static sun.dyn.MemberName.newIllegalArgumentException;

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
public class MethodTypeImpl {
    final int[] argToSlotTable, slotToArgTable;
    final long argCounts;               // packed slot & value counts
    final long primCounts;              // packed prim & double counts
    final int vmslots;                  // total number of parameter slots
    final MethodType erasedType;        // the canonical erasure
    /*lazy*/ MethodType primsAsBoxes;   // replace prims by wrappers
    /*lazy*/ MethodType primArgsAsBoxes; // wrap args only; make raw return
    /*lazy*/ MethodType primsAsInts;    // replace prims by int/long
    /*lazy*/ MethodType primsAsLongs;   // replace prims by long
    /*lazy*/ MethodType primsAtEnd;     // reorder primitives to the end

    // Cached adapter information:
    /*lazy*/ ToGeneric   toGeneric;     // convert cs. with prims to w/o
    /*lazy*/ FromGeneric fromGeneric;   // convert cs. w/o prims to with
    /*lazy*/ SpreadGeneric[] spreadGeneric; // expand one argument to many
    /*lazy*/ FilterGeneric filterGeneric; // convert argument(s) on the fly

    public MethodType erasedType() {
        return erasedType;
    }

    public static MethodTypeImpl of(MethodType type) {
        return METHOD_TYPE_FRIEND.form(type);
    }

    /** Access methods for the internals of MethodType, supplied to
     *  MethodTypeImpl as a trusted agent.
     */
    static public interface MethodTypeFriend {
        Class<?>[]     ptypes(MethodType mt);
        MethodTypeImpl form(MethodType mt);
        void           setForm(MethodType mt, MethodTypeImpl form);
        MethodType     makeImpl(Class<?> rtype, Class<?>[] ptypes, boolean trusted);
        MethodTypeImpl newMethodTypeForm(MethodType mt);
        Invokers       getInvokers(MethodType mt);
        void           setInvokers(MethodType mt, Invokers inv);
    }
    public static void setMethodTypeFriend(Access token, MethodTypeFriend am) {
        Access.check(token);
        if (METHOD_TYPE_FRIEND != null)
            throw new InternalError();  // just once
        METHOD_TYPE_FRIEND = am;
    }
    static private MethodTypeFriend METHOD_TYPE_FRIEND;

    protected MethodTypeImpl(MethodType erasedType) {
        this.erasedType = erasedType;

        Class<?>[] ptypes = METHOD_TYPE_FRIEND.ptypes(erasedType);
        int ptypeCount = ptypes.length;
        int pslotCount = ptypeCount;            // temp. estimate
        int rtypeCount = 1;                     // temp. estimate
        int rslotCount = 1;                     // temp. estimate

        int[] argToSlotTab = null, slotToArgTab = null;

        // Walk the argument types, looking for primitives.
        int pac = 0, lac = 0, prc = 0, lrc = 0;
        Class<?> epts[] = ptypes;
        for (int i = 0; i < epts.length; i++) {
            Class<?> pt = epts[i];
            if (pt != Object.class) {
                assert(pt.isPrimitive());
                ++pac;
                if (hasTwoArgSlots(pt))  ++lac;
            }
        }
        pslotCount += lac;                  // #slots = #args + #longs
        Class<?> rt = erasedType.returnType();
        if (rt != Object.class) {
            ++prc;          // even void.class counts as a prim here
            if (hasTwoArgSlots(rt))  ++lrc;
            // adjust #slots, #args
            if (rt == void.class)
                rtypeCount = rslotCount = 0;
            else
                rslotCount += lrc;
        }
        if (lac != 0) {
            int slot = ptypeCount + lac;
            slotToArgTab = new int[slot+1];
            argToSlotTab = new int[1+ptypeCount];
            argToSlotTab[0] = slot;  // argument "-1" is past end of slots
            for (int i = 0; i < epts.length; i++) {
                Class<?> pt = epts[i];
                if (hasTwoArgSlots(pt))  --slot;
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

        // short circuit some no-op canonicalizations:
        if (!hasPrimitives()) {
            primsAsBoxes = erasedType;
            primArgsAsBoxes = erasedType;
            primsAsInts  = erasedType;
            primsAsLongs = erasedType;
            primsAtEnd   = erasedType;
        }
    }

    /** Turn all primitive types to corresponding wrapper types.
     */
    public MethodType primsAsBoxes() {
        MethodType ct = primsAsBoxes;
        if (ct != null)  return ct;
        MethodType t = erasedType;
        ct = canonicalize(erasedType, WRAP, WRAP);
        if (ct == null)  ct = t;  // no prims to box
        return primsAsBoxes = ct;
    }

    /** Turn all primitive argument types to corresponding wrapper types.
     *  Subword and void return types are promoted to int.
     */
    public MethodType primArgsAsBoxes() {
        MethodType ct = primArgsAsBoxes;
        if (ct != null)  return ct;
        MethodType t = erasedType;
        ct = canonicalize(erasedType, RAW_RETURN, WRAP);
        if (ct == null)  ct = t;  // no prims to box
        return primArgsAsBoxes = ct;
    }

    /** Turn all primitive types to either int or long.
     *  Floating point return types are not changed, because
     *  they may require special calling sequences.
     *  A void return value is turned to int.
     */
    public MethodType primsAsInts() {
        MethodType ct = primsAsInts;
        if (ct != null)  return ct;
        MethodType t = erasedType;
        ct = canonicalize(t, RAW_RETURN, INTS);
        if (ct == null)  ct = t;  // no prims to int-ify
        return primsAsInts = ct;
    }

    /** Turn all primitive types to either int or long.
     *  Floating point return types are not changed, because
     *  they may require special calling sequences.
     *  A void return value is turned to int.
     */
    public MethodType primsAsLongs() {
        MethodType ct = primsAsLongs;
        if (ct != null)  return ct;
        MethodType t = erasedType;
        ct = canonicalize(t, RAW_RETURN, LONGS);
        if (ct == null)  ct = t;  // no prims to int-ify
        return primsAsLongs = ct;
    }

    /** Stably sort parameters into 3 buckets: ref, int, long. */
    public MethodType primsAtEnd() {
        MethodType ct = primsAtEnd;
        if (ct != null)  return ct;
        MethodType t = erasedType;

        int pac = primitiveParameterCount();
        if (pac == 0)
            return primsAtEnd = t;

        int argc = parameterCount();
        int lac = longPrimitiveParameterCount();
        if (pac == argc && (lac == 0 || lac == argc))
            return primsAtEnd = t;

        // known to have a mix of 2 or 3 of ref, int, long
        return primsAtEnd = reorderParameters(t, primsAtEndOrder(t), null);

    }

    /** Compute a new ordering of parameters so that all references
     *  are before all ints or longs, and all ints are before all longs.
     *  For this ordering, doubles count as longs, and all other primitive
     *  values count as ints.
     *  As a special case, if the parameters are already in the specified
     *  order, this method returns a null reference, rather than an array
     *  specifying a null permutation.
     *  <p>
     *  For example, the type {@code (int,boolean,int,Object,String)void}
     *  produces the order {@code {3,4,0,1,2}}, the type
     *  {@code (long,int,String)void} produces {@code {2,1,2}}, and
     *  the type {@code (Object,int)Object} produces {@code null}.
     */
    public static int[] primsAtEndOrder(MethodType mt) {
        MethodTypeImpl form = METHOD_TYPE_FRIEND.form(mt);
        if (form.primsAtEnd == form.erasedType)
            // quick check shows no reordering is necessary
            return null;

        int argc = form.parameterCount();
        int[] paramOrder = new int[argc];

        // 3-way bucket sort:
        int pac = form.primitiveParameterCount();
        int lac = form.longPrimitiveParameterCount();
        int rfill = 0, ifill = argc - pac, lfill = argc - lac;

        Class<?>[] ptypes = METHOD_TYPE_FRIEND.ptypes(mt);
        boolean changed = false;
        for (int i = 0; i < ptypes.length; i++) {
            Class<?> pt = ptypes[i];
            int ord;
            if (!pt.isPrimitive())             ord = rfill++;
            else if (!hasTwoArgSlots(pt))      ord = ifill++;
            else                               ord = lfill++;
            if (ord != i)  changed = true;
            paramOrder[i] = ord;
        }
        assert(rfill == argc - pac && ifill == argc - lac && lfill == argc);
        if (!changed) {
            form.primsAtEnd = form.erasedType;
            return null;
        }
        return paramOrder;
    }

    /** Put the existing parameters of mt into a new order, given by newParamOrder.
     *  The third argument is logically appended to mt.parameterArray,
     *  so that elements of newParamOrder can index either pre-existing or
     *  new parameter types.
     */
    public static MethodType reorderParameters(MethodType mt, int[] newParamOrder, Class<?>[] moreParams) {
        if (newParamOrder == null)  return mt;  // no-op reordering
        Class<?>[] ptypes = METHOD_TYPE_FRIEND.ptypes(mt);
        Class<?>[] ntypes = new Class<?>[newParamOrder.length];
        int ordMax = ptypes.length + (moreParams == null ? 0 : moreParams.length);
        boolean changed = (ntypes.length != ptypes.length);
        for (int i = 0; i < newParamOrder.length; i++) {
            int ord = newParamOrder[i];
            if (ord != i)  changed = true;
            Class<?> nt;
            if (ord < ptypes.length)   nt = ptypes[ord];
            else if (ord == ordMax)    nt = mt.returnType();
            else                       nt = moreParams[ord - ptypes.length];
            ntypes[i] = nt;
        }
        if (!changed)  return mt;
        return METHOD_TYPE_FRIEND.makeImpl(mt.returnType(), ntypes, true);
    }

    private static boolean hasTwoArgSlots(Class<?> type) {
        return type == long.class || type == double.class;
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
//    public boolean hasNonVoidPrimitives() {
//        if (primCounts == 0)  return false;
//        if (primitiveParameterCount() != 0)  return true;
//        return (primitiveReturnCount() != 0 && returnCount() != 0);
//    }
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

    public static void initForm(Access token, MethodType mt) {
        Access.check(token);
        MethodTypeImpl form = findForm(mt);
        METHOD_TYPE_FRIEND.setForm(mt, form);
        if (form.erasedType == mt) {
            // This is a principal (erased) type; show it to the JVM.
            MethodHandleImpl.init(token, mt);
        }
    }

    static MethodTypeImpl findForm(MethodType mt) {
        MethodType erased = canonicalize(mt, ERASE, ERASE);
        if (erased == null) {
            // It is already erased.  Make a new MethodTypeImpl.
            return METHOD_TYPE_FRIEND.newMethodTypeForm(mt);
        } else {
            // Share the MethodTypeImpl with the erased version.
            return METHOD_TYPE_FRIEND.form(erased);
        }
    }

    /** Codes for {@link #canonicalize(java.lang.Class, int).
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
        Class<?>[] ptypes = METHOD_TYPE_FRIEND.ptypes(mt);
        Class<?>[] ptc = MethodTypeImpl.canonicalizes(ptypes, howArgs);
        Class<?> rtype = mt.returnType();
        Class<?> rtc = MethodTypeImpl.canonicalize(rtype, howRet);
        if (ptc == null && rtc == null) {
            // It is already canonical.
            return null;
        }
        // Find the erased version of the method type:
        if (rtc == null)  rtc = rtype;
        if (ptc == null)  ptc = ptypes;
        return METHOD_TYPE_FRIEND.makeImpl(rtc, ptc, true);
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
            if (c != null) {
                if (cs == null)
                    cs = ts.clone();
                cs[i] = c;
            }
        }
        return cs;
    }

    public static Invokers invokers(Access token, MethodType type) {
        Access.check(token);
        Invokers inv = METHOD_TYPE_FRIEND.getInvokers(type);
        if (inv != null)  return inv;
        inv = new Invokers(token, type);
        METHOD_TYPE_FRIEND.setInvokers(type, inv);
        return inv;
    }

    @Override
    public String toString() {
        return "Form"+erasedType;
    }

}
