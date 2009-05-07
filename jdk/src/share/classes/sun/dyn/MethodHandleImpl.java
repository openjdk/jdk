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
import java.dyn.MethodHandles;
import java.dyn.MethodHandles.Lookup;
import java.dyn.MethodType;
import sun.dyn.util.VerifyType;
import java.dyn.NoAccessException;
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
        if (IMPL_LOOKUP_INIT != null || lookup.lookupClass() != Access.class)
            throw new InternalError();
        IMPL_LOOKUP_INIT = lookup;
    }

    public static Lookup getLookup(Access token) {
        Access.check(token);
        return IMPL_LOOKUP;
    }

    static {
        // Force initialization:
        Lookup.PUBLIC_LOOKUP.lookupClass();
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
        if (method.isStatic()) {
            doDispatch = false;
        } else {
            // adjust the advertised receiver type to be exactly the one requested
            // (in the case of invokespecial, this will be the calling class)
            mtype = mtype.insertParameterType(0, method.getDeclaringClass());
            if (method.isConstructor())
                doDispatch = true;
        }
        DirectMethodHandle mh = new DirectMethodHandle(mtype, method, doDispatch, lookupClass);
        if (!mh.isValid())
            throw newNoAccessException(method, lookupClass);
        return mh;
    }

    public static
    MethodHandle accessField(Access token,
                           MemberName member, boolean isSetter,
                           Class<?> lookupClass) {
        Access.check(token);
        // FIXME: Use sun.misc.Unsafe to dig up the dirt on the field.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static
    MethodHandle accessArrayElement(Access token,
                           Class<?> arrayClass, boolean isSetter) {
        Access.check(token);
        if (!arrayClass.isArray())
            throw newIllegalArgumentException("not an array: "+arrayClass);
        // FIXME: Use sun.misc.Unsafe to dig up the dirt on the array.
        throw new UnsupportedOperationException("Not yet implemented");
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
        throw new UnsupportedOperationException("NYI");
    }

    public static MethodHandle convertArguments(Access token,
                                                MethodHandle target,
                                                MethodType newType,
                                                MethodType oldType,
                                                int[] permutationOrNull) {
        Access.check(token);
        MethodHandle res = AdapterMethodHandle.makePairwiseConvert(token, newType, target);
        if (res != null)
            return res;
        int argc = oldType.parameterCount();
        // The JVM can't do it directly, so fill in the gap with a Java adapter.
        // TO DO: figure out what to put here from case-by-case experience
        // Use a heavier method:  Convert all the arguments to Object,
        // then back to the desired types.  We might have to use Java-based
        // method handles to do this.
        MethodType objType = MethodType.makeGeneric(argc);
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
        MethodType midType = MethodType.make(newType.returnType(), ptypes);
        // after spreading, some arguments may need further conversion
        target = convertArguments(token, target, midType, oldType, null);
        if (target == null)
            throw new UnsupportedOperationException("NYI: convert "+midType+" =calls=> "+oldType);
        res = AdapterMethodHandle.makeSpreadArguments(token, newType, target, spreadArgType, spreadArg, spreadCount);
        return res;
    }

    public static MethodHandle collectArguments(Access token,
                                                MethodHandle target,
                                                MethodType newType,
                                                int collectArg) {
        if (collectArg > 0)
            throw new UnsupportedOperationException("NYI");
        throw new UnsupportedOperationException("NYI");
    }
    public static
    MethodHandle dropArguments(Access token, MethodHandle target,
                               MethodType newType, int argnum) {
        Access.check(token);
        throw new UnsupportedOperationException("NYI");
    }

    public static
    MethodHandle makeGuardWithTest(Access token,
                                   final MethodHandle test,
                                   final MethodHandle target,
                                   final MethodHandle fallback) {
        Access.check(token);
        // %%% This is just a sketch.  It needs to be de-boxed.
        // Adjust the handles to accept varargs lists.
        MethodType type = target.type();
        Class<?>  rtype = type.returnType();
        if (type.parameterCount() != 1 || type.parameterType(0).isPrimitive()) {
            MethodType vatestType   = MethodType.make(boolean.class, Object[].class);
            MethodType vatargetType = MethodType.make(rtype, Object[].class);
            MethodHandle vaguard = makeGuardWithTest(token,
                    MethodHandles.spreadArguments(test, vatestType),
                    MethodHandles.spreadArguments(target, vatargetType),
                    MethodHandles.spreadArguments(fallback, vatargetType));
            return MethodHandles.collectArguments(vaguard, type);
        }
        if (rtype.isPrimitive()) {
            MethodType boxtype = type.changeReturnType(Object.class);
            MethodHandle boxguard = makeGuardWithTest(token,
                    test,
                    MethodHandles.convertArguments(target, boxtype),
                    MethodHandles.convertArguments(fallback, boxtype));
            return MethodHandles.convertArguments(boxguard, type);
        }
        // Got here?  Reduced calling sequence to Object(Object).
        class Guarder {
            Object invoke(Object x) {
                // If javac supports MethodHandle.invoke directly:
                //z = vatest.invoke<boolean>(arguments);
                // If javac does not support direct MH.invoke calls:
                boolean z = (Boolean) MethodHandles.invoke_1(test, x);
                MethodHandle mh = (z ? target : fallback);
                return MethodHandles.invoke_1(mh, x);
            }
            MethodHandle handle() {
                MethodType invokeType = MethodType.makeGeneric(0, true);
                MethodHandle vh = IMPL_LOOKUP.bind(this, "invoke", invokeType);
                return MethodHandles.collectArguments(vh, target.type());
            }
        }
        return new Guarder().handle();
    }

    public static
    MethodHandle combineArguments(Access token, MethodHandle target, MethodHandle checker, int pos) {
        Access.check(token);
        throw new UnsupportedOperationException("Not yet implemented");
    }

    protected static String basicToString(MethodHandle target) {
        MemberName name = null;
        if (target != null)
            name = MethodHandleNatives.getMethodName(target);
        if (name == null)
            return "<unknown>";
        return name.getName();
    }

    protected static String addTypeString(MethodHandle target, String name) {
        if (target == null)  return name;
        return name+target.type();
    }
    static RuntimeException newIllegalArgumentException(String string) {
        return new IllegalArgumentException(string);
    }

    @Override
    public String toString() {
        MethodHandle self = (MethodHandle) this;
        return addTypeString(self, basicToString(self));
    }
}
