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

import java.dyn.MethodHandle;
import java.dyn.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import static sun.dyn.MethodHandleNatives.Constants.*;

/**
 * The JVM interface for the method handles package is all here.
 * @author jrose
 */
class MethodHandleNatives {

    private MethodHandleNatives() { } // static only

    /// MethodName support

    static native void init(MemberName self, Object ref);
    static native void expand(MemberName self);
    static native void resolve(MemberName self, Class<?> caller);
    static native int getMembers(Class<?> defc, String matchName, String matchSig,
            int matchFlags, Class<?> caller, int skip, MemberName[] results);

    static Class<?> asNativeCaller(Class<?> lookupClass) {
        if (lookupClass == null)  // means "public only, non-privileged"
            return sun.dyn.empty.Empty.class;
        if (lookupClass == Access.class)  // means "internal, privileged"
            return null;    // to the JVM, null means completely privileged
        return lookupClass;
    }

    /// MethodHandle support

    /** Initialize the method handle to adapt the call. */
    static native void init(AdapterMethodHandle self, MethodHandle target, int argnum);
    /** Initialize the method handle to call the correct method, directly. */
    static native void init(BoundMethodHandle self, Object target, int argnum);
    /** Initialize the method handle to call as if by an invoke* instruction. */
    static native void init(DirectMethodHandle self, Object ref, boolean doDispatch, Class<?> caller);

    /** Initialize a method type, once per form. */
    static native void init(MethodType self);

    /** Tell the JVM that we need to change the target of an invokedynamic. */
    static native void linkCallSite(CallSiteImpl site, MethodHandle target);

    /** Fetch the vmtarget field.
     *  It will be sanitized as necessary to avoid exposing non-Java references.
     *  This routine is for debugging and reflection.
     */
    static native Object getTarget(MethodHandle self, int format);

    /** Fetch the name of the handled method, if available.
     *  This routine is for debugging and reflection.
     */
    static MemberName getMethodName(MethodHandle self) {
        if (!JVM_SUPPORT)  return null;
        return (MemberName) getTarget(self, ETF_METHOD_NAME);
    }

    /** Fetch the reflective version of the handled method, if available.
     */
    static AccessibleObject getTargetMethod(MethodHandle self) {
        if (!JVM_SUPPORT)  return null;
        return (AccessibleObject) getTarget(self, ETF_REFLECT_METHOD);
    }

    /** Fetch the target of this method handle.
     *  If it directly targets a method, return a tuple of method info.
     *  The info is of the form new Object[]{defclass, name, sig, refclass}.
     *  If it is chained to another method handle, return that handle.
     */
    static Object getTargetInfo(MethodHandle self) {
        if (!JVM_SUPPORT)  return null;
        return getTarget(self, ETF_HANDLE_OR_METHOD_NAME);
    }

    static Object[] makeTarget(Class<?> defc, String name, String sig, int mods, Class<?> refc) {
        return new Object[] { defc, name, sig, mods, refc };
    }

    /** Fetch MH-related JVM parameter.
     *  which=0 retrieves MethodHandlePushLimit
     *  which=1 retrieves stack slot push size (in address units)
     */
    static native int getConstant(int which);

    /** True iff this HotSpot JVM has built-in support for method handles.
     * If false, some test cases might run, but functionality will be missing.
     */
    public static final boolean JVM_SUPPORT;

    /** Java copy of MethodHandlePushLimit in range 2..255. */
    static final int JVM_PUSH_LIMIT;
    /** JVM stack motion (in words) after one slot is pushed, usually -1.
     */
    static final int JVM_STACK_MOVE_UNIT;

    private static native void registerNatives();
    static {
        boolean JVM_SUPPORT_;
        int     JVM_PUSH_LIMIT_;
        int     JVM_STACK_MOVE_UNIT_;
        try {
            registerNatives();
            JVM_SUPPORT_ = true;
            JVM_PUSH_LIMIT_ = getConstant(Constants.GC_JVM_PUSH_LIMIT);
            JVM_STACK_MOVE_UNIT_ = getConstant(Constants.GC_JVM_STACK_MOVE_LIMIT);
            //sun.reflect.Reflection.registerMethodsToFilter(MethodHandleImpl.class, "init");
        } catch (UnsatisfiedLinkError ee) {
            // ignore; if we use init() methods later we'll see linkage errors
            JVM_SUPPORT_ = false;
            JVM_PUSH_LIMIT_ = 3;  // arbitrary
            JVM_STACK_MOVE_UNIT_ = -1;  // arbitrary
            //System.out.println("Warning: Running with JVM_SUPPORT=false");
            //System.out.println(ee);
            JVM_SUPPORT = JVM_SUPPORT_;
            JVM_PUSH_LIMIT = JVM_PUSH_LIMIT_;
            JVM_STACK_MOVE_UNIT = JVM_STACK_MOVE_UNIT_;
            throw ee;  // just die; hopeless to try to run with an older JVM
        }
        JVM_SUPPORT = JVM_SUPPORT_;
        JVM_PUSH_LIMIT = JVM_PUSH_LIMIT_;
        JVM_STACK_MOVE_UNIT = JVM_STACK_MOVE_UNIT_;
    }

    // All compile-time constants go here.
    // There is an opportunity to check them against the JVM's idea of them.
    static class Constants {
        Constants() { } // static only
        // MethodHandleImpl
        static final int // for getConstant
                GC_JVM_PUSH_LIMIT = 0,
                GC_JVM_STACK_MOVE_LIMIT = 1;
        static final int
                ETF_HANDLE_OR_METHOD_NAME = 0, // all available data (immediate MH or method)
                ETF_DIRECT_HANDLE         = 1, // ultimate method handle (will be a DMH, may be self)
                ETF_METHOD_NAME           = 2, // ultimate method as MemberName
                ETF_REFLECT_METHOD        = 3; // ultimate method as java.lang.reflect object (sans refClass)

        // MemberName
        // The JVM uses values of -2 and above for vtable indexes.
        // Field values are simple positive offsets.
        // Ref: src/share/vm/oops/methodOop.hpp
        // This value is negative enough to avoid such numbers,
        // but not too negative.
        static final int
                MN_IS_METHOD           = 0x00010000, // method (not constructor)
                MN_IS_CONSTRUCTOR      = 0x00020000, // constructor
                MN_IS_FIELD            = 0x00040000, // field
                MN_IS_TYPE             = 0x00080000, // nested type
                MN_SEARCH_SUPERCLASSES = 0x00100000, // for MHN.getMembers
                MN_SEARCH_INTERFACES   = 0x00200000, // for MHN.getMembers
                VM_INDEX_UNINITIALIZED = -99;

        // AdapterMethodHandle
        /** Conversions recognized by the JVM.
         *  They must align with the constants in sun.dyn_AdapterMethodHandle,
         *  in the JVM file hotspot/src/share/vm/classfile/javaClasses.hpp.
         */
        static final int
            OP_RETYPE_ONLY   = 0x0, // no argument changes; straight retype
            OP_CHECK_CAST    = 0x1, // ref-to-ref conversion; requires a Class argument
            OP_PRIM_TO_PRIM  = 0x2, // converts from one primitive to another
            OP_REF_TO_PRIM   = 0x3, // unboxes a wrapper to produce a primitive
            OP_PRIM_TO_REF   = 0x4, // boxes a primitive into a wrapper (NYI)
            OP_SWAP_ARGS     = 0x5, // swap arguments (vminfo is 2nd arg)
            OP_ROT_ARGS      = 0x6, // rotate arguments (vminfo is displaced arg)
            OP_DUP_ARGS      = 0x7, // duplicates one or more arguments (at TOS)
            OP_DROP_ARGS     = 0x8, // remove one or more argument slots
            OP_COLLECT_ARGS  = 0x9, // combine one or more arguments into a varargs (NYI)
            OP_SPREAD_ARGS   = 0xA, // expand in place a varargs array (of known size)
            OP_FLYBY         = 0xB, // operate first on reified argument list (NYI)
            OP_RICOCHET      = 0xC, // run an adapter chain on the return value (NYI)
            CONV_OP_LIMIT    = 0xD; // limit of CONV_OP enumeration
        /** Shift and mask values for decoding the AMH.conversion field.
         *  These numbers are shared with the JVM for creating AMHs.
         */
        static final int
            CONV_OP_MASK     = 0xF00, // this nybble contains the conversion op field
            CONV_VMINFO_MASK = 0x0FF, // LSB is reserved for JVM use
            CONV_VMINFO_SHIFT     =  0, // position of bits in CONV_VMINFO_MASK
            CONV_OP_SHIFT         =  8, // position of bits in CONV_OP_MASK
            CONV_DEST_TYPE_SHIFT  = 12, // byte 2 has the adapter BasicType (if needed)
            CONV_SRC_TYPE_SHIFT   = 16, // byte 2 has the source BasicType (if needed)
            CONV_STACK_MOVE_SHIFT = 20, // high 12 bits give signed SP change
            CONV_STACK_MOVE_MASK  = (1 << (32 - CONV_STACK_MOVE_SHIFT)) - 1;

        /** Which conv-ops are implemented by the JVM? */
        static final int CONV_OP_IMPLEMENTED_MASK =
                // TODO: The following expression should be replaced by
                // a JVM query.
                ((1<<OP_RETYPE_ONLY)
                |(1<<OP_CHECK_CAST)
                |(1<<OP_PRIM_TO_PRIM)
                |(1<<OP_REF_TO_PRIM)
                |(1<<OP_SWAP_ARGS)
                |(1<<OP_ROT_ARGS)
                |(1<<OP_DUP_ARGS)
                |(1<<OP_DROP_ARGS)
                );

        /**
         * Basic types as encoded in the JVM.  These code values are not
         * intended for use outside this class.  They are used as part of
         * a private interface between the JVM and this class.
         */
        static final int
            T_BOOLEAN  =  4,
            T_CHAR     =  5,
            T_FLOAT    =  6,
            T_DOUBLE   =  7,
            T_BYTE     =  8,
            T_SHORT    =  9,
            T_INT      = 10,
            T_LONG     = 11,
            T_OBJECT   = 12,
            //T_ARRAY    = 13
            T_VOID     = 14;
            //T_ADDRESS  = 15
    }

    private static native int getNamedCon(int which, Object[] name);
    static boolean verifyConstants() {
        Object[] box = { null };
        for (int i = 0; ; i++) {
            box[0] = null;
            int vmval = getNamedCon(i, box);
            if (box[0] == null)  break;
            String name = (String) box[0];
            try {
                Field con = Constants.class.getDeclaredField(name);
                int jval = con.getInt(null);
                if (jval != vmval)
                    throw new InternalError(name+": JVM has "+vmval+" while Java has "+jval);
            } catch (Exception ex) {
                throw new InternalError(name+": access failed, got "+ex);
            }
        }
        return true;
    }
    static {
        if (JVM_SUPPORT)  verifyConstants();
    }
}
