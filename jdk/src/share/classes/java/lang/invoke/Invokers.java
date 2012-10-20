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

import java.util.Arrays;
import sun.invoke.empty.Empty;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import static java.lang.invoke.LambdaForm.*;

/**
 * Construction and caching of often-used invokers.
 * @author jrose
 */
class Invokers {
    // exact type (sans leading taget MH) for the outgoing call
    private final MethodType targetType;

    // FIXME: Get rid of the invokers that are not useful.

    // exact invoker for the outgoing call
    private /*lazy*/ MethodHandle exactInvoker;

    // erased (partially untyped but with primitives) invoker for the outgoing call
    // FIXME: get rid of
    private /*lazy*/ MethodHandle erasedInvoker;
    // FIXME: get rid of
    /*lazy*/ MethodHandle erasedInvokerWithDrops;  // for InvokeGeneric

    // general invoker for the outgoing call
    private /*lazy*/ MethodHandle generalInvoker;

    // general invoker for the outgoing call, uses varargs
    private /*lazy*/ MethodHandle varargsInvoker;

    // general invoker for the outgoing call; accepts a trailing Object[]
    private final /*lazy*/ MethodHandle[] spreadInvokers;

    // invoker for an unbound callsite
    private /*lazy*/ MethodHandle uninitializedCallSite;

    /** Compute and cache information common to all collecting adapters
     *  that implement members of the erasure-family of the given erased type.
     */
    /*non-public*/ Invokers(MethodType targetType) {
        this.targetType = targetType;
        this.spreadInvokers = new MethodHandle[targetType.parameterCount()+1];
    }

    /*non-public*/ MethodHandle exactInvoker() {
        MethodHandle invoker = exactInvoker;
        if (invoker != null)  return invoker;
        MethodType mtype = targetType;
        MethodType invokerType = mtype.invokerType();
        LambdaForm lform;
        final int MTYPE_ARG_APPENDED = 1;  // argument count for appended mtype value
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_INVOKER_ARITY - MTYPE_ARG_APPENDED) {
            lform = invokeForm(mtype, false, MethodTypeForm.LF_EX_INVOKER);
            invoker = BoundMethodHandle.bindSingle(invokerType, lform, mtype);
        } else {
            // At maximum arity, we cannot afford an extra mtype argument,
            // so build a fully customized (non-cached) invoker form.
            lform = invokeForm(mtype, true, MethodTypeForm.LF_EX_INVOKER);
            invoker = SimpleMethodHandle.make(invokerType, lform);
        }
        assert(checkInvoker(invoker));
        exactInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle generalInvoker() {
        MethodHandle invoker = generalInvoker;
        if (invoker != null)  return invoker;
        MethodType mtype = targetType;
        MethodType invokerType = mtype.invokerType();
        LambdaForm lform;
        final int MTYPE_ARG_APPENDED = 1;  // argument count for appended mtype value
        assert(GENERIC_INVOKER_SLOP >= MTYPE_ARG_APPENDED);
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_INVOKER_ARITY - GENERIC_INVOKER_SLOP) {
            prepareForGenericCall(mtype);
            lform = invokeForm(mtype, false, MethodTypeForm.LF_GEN_INVOKER);
            invoker = BoundMethodHandle.bindSingle(invokerType, lform, mtype);
        } else {
            // At maximum arity, we cannot afford an extra mtype argument,
            // so build a fully customized (non-cached) invoker form.
            lform = invokeForm(mtype, true, MethodTypeForm.LF_GEN_INVOKER);
            invoker = SimpleMethodHandle.make(invokerType, lform);
        }
        assert(checkInvoker(invoker));
        generalInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle makeBasicInvoker() {
        MethodHandle invoker = DirectMethodHandle.make(invokeBasicMethod(targetType));
        assert(targetType == targetType.basicType());
        // Note:  This is not cached here.  It is cached by the calling MethodTypeForm.
        assert(checkInvoker(invoker));
        return invoker;
    }

    static MemberName invokeBasicMethod(MethodType type) {
        type = type.basicType();
        String name = "invokeBasic";
        try {
            //Lookup.findVirtual(MethodHandle.class, name, type);
            return IMPL_LOOKUP.resolveOrFail(REF_invokeVirtual, MethodHandle.class, name, type);
        } catch (ReflectiveOperationException ex) {
            throw newInternalError("JVM cannot find invoker for "+type, ex);
        }
    }

    private boolean checkInvoker(MethodHandle invoker) {
        assert(targetType.invokerType().equals(invoker.type()))
                : java.util.Arrays.asList(targetType, targetType.invokerType(), invoker);
        assert(invoker.internalMemberName() == null ||
               invoker.internalMemberName().getMethodType().equals(targetType));
        assert(!invoker.isVarargsCollector());
        return true;
    }

    // FIXME: get rid of
    /*non-public*/ MethodHandle erasedInvoker() {
        MethodHandle xinvoker = exactInvoker();
        MethodHandle invoker = erasedInvoker;
        if (invoker != null)  return invoker;
        MethodType erasedType = targetType.erase();
        invoker = xinvoker.asType(erasedType.invokerType());
        erasedInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle spreadInvoker(int leadingArgCount) {
        MethodHandle vaInvoker = spreadInvokers[leadingArgCount];
        if (vaInvoker != null)  return vaInvoker;
        int spreadArgCount = targetType.parameterCount() - leadingArgCount;
        MethodType spreadInvokerType = targetType
            .replaceParameterTypes(leadingArgCount, targetType.parameterCount(), Object[].class);
        if (targetType.parameterSlotCount() <= MethodType.MAX_MH_INVOKER_ARITY) {
            // Factor sinvoker.invoke(mh, a) into ginvoker.asSpreader().invoke(mh, a)
            // where ginvoker.invoke(mh, a*) => mh.invoke(a*).
            MethodHandle genInvoker = generalInvoker();
            vaInvoker = genInvoker.asSpreader(Object[].class, spreadArgCount);
        } else {
            // Cannot build a general invoker here of type ginvoker.invoke(mh, a*[254]).
            // Instead, factor sinvoker.invoke(mh, a) into ainvoker.invoke(filter(mh), a)
            // where filter(mh) == mh.asSpreader(Object[], spreadArgCount)
            MethodHandle arrayInvoker = MethodHandles.exactInvoker(spreadInvokerType);
            MethodHandle makeSpreader;
            try {
                makeSpreader = IMPL_LOOKUP
                    .findVirtual(MethodHandle.class, "asSpreader",
                        MethodType.methodType(MethodHandle.class, Class.class, int.class));
            } catch (ReflectiveOperationException ex) {
                throw newInternalError(ex);
            }
            makeSpreader = MethodHandles.insertArguments(makeSpreader, 1, Object[].class, spreadArgCount);
            vaInvoker = MethodHandles.filterArgument(arrayInvoker, 0, makeSpreader);
        }
        assert(vaInvoker.type().equals(spreadInvokerType.invokerType()));
        spreadInvokers[leadingArgCount] = vaInvoker;
        return vaInvoker;
    }

    /*non-public*/ MethodHandle varargsInvoker() {
        MethodHandle vaInvoker = varargsInvoker;
        if (vaInvoker != null)  return vaInvoker;
        vaInvoker = spreadInvoker(0).asType(MethodType.genericMethodType(0, true).invokerType());
        varargsInvoker = vaInvoker;
        return vaInvoker;
    }

    private static MethodHandle THROW_UCS = null;

    /*non-public*/ MethodHandle uninitializedCallSite() {
        MethodHandle invoker = uninitializedCallSite;
        if (invoker != null)  return invoker;
        if (targetType.parameterCount() > 0) {
            MethodType type0 = targetType.dropParameterTypes(0, targetType.parameterCount());
            Invokers invokers0 = type0.invokers();
            invoker = MethodHandles.dropArguments(invokers0.uninitializedCallSite(),
                                                  0, targetType.parameterList());
            assert(invoker.type().equals(targetType));
            uninitializedCallSite = invoker;
            return invoker;
        }
        invoker = THROW_UCS;
        if (invoker == null) {
            try {
                THROW_UCS = invoker = IMPL_LOOKUP
                    .findStatic(CallSite.class, "uninitializedCallSite",
                                MethodType.methodType(Empty.class));
            } catch (ReflectiveOperationException ex) {
                throw newInternalError(ex);
            }
        }
        invoker = MethodHandles.explicitCastArguments(invoker, MethodType.methodType(targetType.returnType()));
        invoker = invoker.dropArguments(targetType, 0, targetType.parameterCount());
        assert(invoker.type().equals(targetType));
        uninitializedCallSite = invoker;
        return invoker;
    }

    public String toString() {
        return "Invokers"+targetType;
    }

    static MemberName exactInvokerMethod(MethodType mtype, Object[] appendixResult) {
        LambdaForm lform;
        final int MTYPE_ARG_APPENDED = 1;  // argument count for appended mtype value
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_ARITY - MTYPE_ARG_APPENDED) {
            lform = invokeForm(mtype, false, MethodTypeForm.LF_EX_LINKER);
            appendixResult[0] = mtype;
        } else {
            lform = invokeForm(mtype, true, MethodTypeForm.LF_EX_LINKER);
        }
        return lform.vmentry;
    }

    static MemberName genericInvokerMethod(MethodType mtype, Object[] appendixResult) {
        LambdaForm lform;
        final int MTYPE_ARG_APPENDED = 1;  // argument count for appended mtype value
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_ARITY - (MTYPE_ARG_APPENDED + GENERIC_INVOKER_SLOP)) {
            lform = invokeForm(mtype, false, MethodTypeForm.LF_GEN_LINKER);
            appendixResult[0] = mtype;
            prepareForGenericCall(mtype);
        } else {
            lform = invokeForm(mtype, true, MethodTypeForm.LF_GEN_LINKER);
        }
        return lform.vmentry;
    }

    private static LambdaForm invokeForm(MethodType mtype, boolean customized, int which) {
        boolean isCached;
        if (!customized) {
            mtype = mtype.basicType();  // normalize Z to I, String to Object, etc.
            isCached = true;
        } else {
            isCached = false;  // maybe cache if mtype == mtype.basicType()
        }
        boolean isLinker, isGeneric;
        String debugName;
        switch (which) {
        case MethodTypeForm.LF_EX_LINKER:   isLinker = true;  isGeneric = false; debugName = "invokeExact_MT"; break;
        case MethodTypeForm.LF_EX_INVOKER:  isLinker = false; isGeneric = false; debugName = "exactInvoker"; break;
        case MethodTypeForm.LF_GEN_LINKER:  isLinker = true;  isGeneric = true;  debugName = "invoke_MT"; break;
        case MethodTypeForm.LF_GEN_INVOKER: isLinker = false; isGeneric = true;  debugName = "invoker"; break;
        default: throw new InternalError();
        }
        LambdaForm lform;
        if (isCached) {
            lform = mtype.form().cachedLambdaForm(which);
            if (lform != null)  return lform;
        }
        // exactInvokerForm (Object,Object)Object
        //   link with java.lang.invoke.MethodHandle.invokeBasic(MethodHandle,Object,Object)Object/invokeSpecial
        final int THIS_MH      = 0;
        final int CALL_MH      = THIS_MH + (isLinker ? 0 : 1);
        final int ARG_BASE     = CALL_MH + 1;
        final int OUTARG_LIMIT = ARG_BASE + mtype.parameterCount();
        final int INARG_LIMIT  = OUTARG_LIMIT + (isLinker && !customized ? 1 : 0);
        int nameCursor = OUTARG_LIMIT;
        final int MTYPE_ARG    = customized ? -1 : nameCursor++;  // might be last in-argument
        final int CHECK_TYPE   = nameCursor++;
        final int LINKER_CALL  = nameCursor++;
        MethodType invokerFormType = mtype.invokerType();
        if (isLinker) {
            if (!customized)
                invokerFormType = invokerFormType.appendParameterTypes(MemberName.class);
        } else {
            invokerFormType = invokerFormType.invokerType();
        }
        Name[] names = arguments(nameCursor - INARG_LIMIT, invokerFormType);
        assert(names.length == nameCursor)
                : Arrays.asList(mtype, customized, which, nameCursor, names.length);
        if (MTYPE_ARG >= INARG_LIMIT) {
            assert(names[MTYPE_ARG] == null);
            names[MTYPE_ARG] = BoundMethodHandle.getSpeciesData("L").getterName(names[THIS_MH], 0);
            // else if isLinker, then MTYPE is passed in from the caller (e.g., the JVM)
        }

        // Make the final call.  If isGeneric, then prepend the result of type checking.
        MethodType outCallType;
        Object[] outArgs;
        Object mtypeArg = (customized ? mtype : names[MTYPE_ARG]);
        if (!isGeneric) {
            names[CHECK_TYPE] = new Name(NF_checkExactType, names[CALL_MH], mtypeArg);
            // mh.invokeExact(a*):R => checkExactType(mh, TYPEOF(a*:R)); mh.invokeBasic(a*)
            outArgs = Arrays.copyOfRange(names, CALL_MH, OUTARG_LIMIT, Object[].class);
            outCallType = mtype;
        } else if (customized) {
            names[CHECK_TYPE] = new Name(NF_asType, names[CALL_MH], mtypeArg);
            // mh.invokeGeneric(a*):R =>
            //  let mt=TYPEOF(a*:R), tmh=asType(mh, mt);
            //    tmh.invokeBasic(a*)
            outArgs = Arrays.copyOfRange(names, CALL_MH, OUTARG_LIMIT, Object[].class);
            outCallType = mtype;
        } else {
            names[CHECK_TYPE] = new Name(NF_checkGenericType, names[CALL_MH], mtypeArg);
            // mh.invokeGeneric(a*):R =>
            //  let mt=TYPEOF(a*:R), gamh=checkGenericType(mh, mt);
            //    gamh.invokeBasic(mt, mh, a*)
            final int PREPEND_GAMH = 0, PREPEND_MT = 1, PREPEND_COUNT = 2;
            assert(GENERIC_INVOKER_SLOP == PREPEND_COUNT);
            outArgs = Arrays.copyOfRange(names, CALL_MH, OUTARG_LIMIT + PREPEND_COUNT, Object[].class);
            // prepend arguments:
            System.arraycopy(outArgs, 0, outArgs, PREPEND_COUNT, outArgs.length - PREPEND_COUNT);
            outArgs[PREPEND_GAMH] = names[CHECK_TYPE];
            outArgs[PREPEND_MT] = mtypeArg;
            outCallType = mtype.insertParameterTypes(0, MethodType.class, MethodHandle.class);
        }
        names[LINKER_CALL] = new Name(invokeBasicMethod(outCallType), outArgs);
        lform = new LambdaForm(debugName, INARG_LIMIT, names);
        if (isLinker)
            lform.compileToBytecode();  // JVM needs a real methodOop
        if (isCached)
            lform = mtype.form().setCachedLambdaForm(which, lform);
        return lform;
    }
    private static final int GENERIC_INVOKER_SLOP = 2;  // used elsewhere to avoid arity problems

    /*non-public*/ static
    WrongMethodTypeException newWrongMethodTypeException(MethodType actual, MethodType expected) {
        // FIXME: merge with JVM logic for throwing WMTE
        return new WrongMethodTypeException("expected "+expected+" but found "+actual);
    }

    /** Static definition of MethodHandle.invokeExact checking code. */
    /*non-public*/ static
    @ForceInline
    void checkExactType(Object mhObj, Object expectedObj) {
        MethodHandle mh = (MethodHandle) mhObj;
        MethodType expected = (MethodType) expectedObj;
        MethodType actual = mh.type();
        if (actual != expected)
            throw newWrongMethodTypeException(expected, actual);
    }

    /** Static definition of MethodHandle.invokeGeneric checking code. */
    /*non-public*/ static
    @ForceInline
    Object checkGenericType(Object mhObj, Object expectedObj) {
        MethodHandle mh = (MethodHandle) mhObj;
        MethodType expected = (MethodType) expectedObj;
        //MethodType actual = mh.type();
        MethodHandle gamh = expected.form().genericInvoker;
        if (gamh != null)  return gamh;
        return prepareForGenericCall(expected);
    }

    /**
     * Returns an adapter GA for invoking a MH with type adjustments.
     * The MethodType of the generic invocation site is prepended to MH
     * and its arguments as follows:
     * {@code (R)MH.invoke(A*) => GA.invokeBasic(TYPEOF<A*,R>, MH, A*)}
     */
    /*non-public*/ static MethodHandle prepareForGenericCall(MethodType mtype) {
        // force any needed adapters to be preconstructed
        MethodTypeForm form = mtype.form();
        MethodHandle gamh = form.genericInvoker;
        if (gamh != null)  return gamh;
        try {
            // Trigger adapter creation.
            gamh = InvokeGeneric.generalInvokerOf(form.erasedType);
            form.genericInvoker = gamh;
            return gamh;
        } catch (Exception ex) {
            throw newInternalError("Exception while resolving inexact invoke", ex);
        }
    }

    static MemberName linkToCallSiteMethod(MethodType mtype) {
        LambdaForm lform = callSiteForm(mtype);
        return lform.vmentry;
    }

    private static LambdaForm callSiteForm(MethodType mtype) {
        mtype = mtype.basicType();  // normalize Z to I, String to Object, etc.
        LambdaForm lform = mtype.form().cachedLambdaForm(MethodTypeForm.LF_CS_LINKER);
        if (lform != null)  return lform;
        // exactInvokerForm (Object,Object)Object
        //   link with java.lang.invoke.MethodHandle.invokeBasic(MethodHandle,Object,Object)Object/invokeSpecial
        final int ARG_BASE     = 0;
        final int OUTARG_LIMIT = ARG_BASE + mtype.parameterCount();
        final int INARG_LIMIT  = OUTARG_LIMIT + 1;
        int nameCursor = OUTARG_LIMIT;
        final int CSITE_ARG    = nameCursor++;  // the last in-argument
        final int CALL_MH      = nameCursor++;  // result of getTarget
        final int LINKER_CALL  = nameCursor++;
        MethodType invokerFormType = mtype.appendParameterTypes(CallSite.class);
        Name[] names = arguments(nameCursor - INARG_LIMIT, invokerFormType);
        assert(names.length == nameCursor);
        assert(names[CSITE_ARG] != null);
        names[CALL_MH] = new Name(NF_getCallSiteTarget, names[CSITE_ARG]);
        // (site.)invokedynamic(a*):R => mh = site.getTarget(); mh.invokeBasic(a*)
        final int PREPEND_MH = 0, PREPEND_COUNT = 1;
        Object[] outArgs = Arrays.copyOfRange(names, ARG_BASE, OUTARG_LIMIT + PREPEND_COUNT, Object[].class);
        // prepend MH argument:
        System.arraycopy(outArgs, 0, outArgs, PREPEND_COUNT, outArgs.length - PREPEND_COUNT);
        outArgs[PREPEND_MH] = names[CALL_MH];
        names[LINKER_CALL] = new Name(invokeBasicMethod(mtype), outArgs);
        lform = new LambdaForm("linkToCallSite", INARG_LIMIT, names);
        lform.compileToBytecode();  // JVM needs a real methodOop
        lform = mtype.form().setCachedLambdaForm(MethodTypeForm.LF_CS_LINKER, lform);
        return lform;
    }

    /** Static definition of MethodHandle.invokeGeneric checking code. */
    /*non-public*/ static
    @ForceInline
    Object getCallSiteTarget(Object site) {
        return ((CallSite)site).getTarget();
    }

    // Local constant functions:
    private static final NamedFunction NF_checkExactType;
    private static final NamedFunction NF_checkGenericType;
    private static final NamedFunction NF_asType;
    private static final NamedFunction NF_getCallSiteTarget;
    static {
        try {
            NF_checkExactType = new NamedFunction(Invokers.class
                    .getDeclaredMethod("checkExactType", Object.class, Object.class));
            NF_checkGenericType = new NamedFunction(Invokers.class
                    .getDeclaredMethod("checkGenericType", Object.class, Object.class));
            NF_asType = new NamedFunction(MethodHandle.class
                    .getDeclaredMethod("asType", MethodType.class));
            NF_getCallSiteTarget = new NamedFunction(Invokers.class
                    .getDeclaredMethod("getCallSiteTarget", Object.class));
            NF_checkExactType.resolve();
            NF_checkGenericType.resolve();
            NF_getCallSiteTarget.resolve();
            // bound
        } catch (ReflectiveOperationException ex) {
            throw newInternalError(ex);
        }
    }

}
