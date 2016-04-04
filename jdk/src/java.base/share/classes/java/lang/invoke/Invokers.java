/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.reflect.Array;
import java.util.Arrays;

import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import static java.lang.invoke.LambdaForm.*;

/**
 * Construction and caching of often-used invokers.
 * @author jrose
 */
class Invokers {
    // exact type (sans leading target MH) for the outgoing call
    private final MethodType targetType;

    // Cached adapter information:
    private final @Stable MethodHandle[] invokers = new MethodHandle[INV_LIMIT];
    // Indexes into invokers:
    static final int
            INV_EXACT          =  0,  // MethodHandles.exactInvoker
            INV_GENERIC        =  1,  // MethodHandles.invoker (generic invocation)
            INV_BASIC          =  2,  // MethodHandles.basicInvoker
            INV_LIMIT          =  3;

    /** Compute and cache information common to all collecting adapters
     *  that implement members of the erasure-family of the given erased type.
     */
    /*non-public*/ Invokers(MethodType targetType) {
        this.targetType = targetType;
    }

    /*non-public*/ MethodHandle exactInvoker() {
        MethodHandle invoker = cachedInvoker(INV_EXACT);
        if (invoker != null)  return invoker;
        invoker = makeExactOrGeneralInvoker(true);
        return setCachedInvoker(INV_EXACT, invoker);
    }

    /*non-public*/ MethodHandle genericInvoker() {
        MethodHandle invoker = cachedInvoker(INV_GENERIC);
        if (invoker != null)  return invoker;
        invoker = makeExactOrGeneralInvoker(false);
        return setCachedInvoker(INV_GENERIC, invoker);
    }

    /*non-public*/ MethodHandle basicInvoker() {
        MethodHandle invoker = cachedInvoker(INV_BASIC);
        if (invoker != null)  return invoker;
        MethodType basicType = targetType.basicType();
        if (basicType != targetType) {
            // double cache; not used significantly
            return setCachedInvoker(INV_BASIC, basicType.invokers().basicInvoker());
        }
        invoker = basicType.form().cachedMethodHandle(MethodTypeForm.MH_BASIC_INV);
        if (invoker == null) {
            MemberName method = invokeBasicMethod(basicType);
            invoker = DirectMethodHandle.make(method);
            assert(checkInvoker(invoker));
            invoker = basicType.form().setCachedMethodHandle(MethodTypeForm.MH_BASIC_INV, invoker);
        }
        return setCachedInvoker(INV_BASIC, invoker);
    }

    /*non-public*/ MethodHandle varHandleMethodInvoker(VarHandle.AccessMode ak) {
        // TODO cache invoker
        return makeVarHandleMethodInvoker(ak);
    }

    /*non-public*/ MethodHandle varHandleMethodExactInvoker(VarHandle.AccessMode ak) {
        // TODO cache invoker
        return makeVarHandleMethodExactInvoker(ak);
    }

    private MethodHandle cachedInvoker(int idx) {
        return invokers[idx];
    }

    private synchronized MethodHandle setCachedInvoker(int idx, final MethodHandle invoker) {
        // Simulate a CAS, to avoid racy duplication of results.
        MethodHandle prev = invokers[idx];
        if (prev != null)  return prev;
        return invokers[idx] = invoker;
    }

    private MethodHandle makeExactOrGeneralInvoker(boolean isExact) {
        MethodType mtype = targetType;
        MethodType invokerType = mtype.invokerType();
        int which = (isExact ? MethodTypeForm.LF_EX_INVOKER : MethodTypeForm.LF_GEN_INVOKER);
        LambdaForm lform = invokeHandleForm(mtype, false, which);
        MethodHandle invoker = BoundMethodHandle.bindSingle(invokerType, lform, mtype);
        String whichName = (isExact ? "invokeExact" : "invoke");
        invoker = invoker.withInternalMemberName(MemberName.makeMethodHandleInvoke(whichName, mtype), false);
        assert(checkInvoker(invoker));
        maybeCompileToBytecode(invoker);
        return invoker;
    }

    private MethodHandle makeVarHandleMethodInvoker(VarHandle.AccessMode ak) {
        MethodType mtype = targetType;
        MethodType invokerType = mtype.insertParameterTypes(0, VarHandle.class);

        LambdaForm lform = varHandleMethodGenericInvokerHandleForm(ak.name(), mtype);
        VarHandle.AccessDescriptor ad = new VarHandle.AccessDescriptor(mtype, ak.at.ordinal(), ak.ordinal());
        MethodHandle invoker = BoundMethodHandle.bindSingle(invokerType, lform, ad);

        invoker = invoker.withInternalMemberName(MemberName.makeVarHandleMethodInvoke(ak.name(), mtype), false);
        assert(checkVarHandleInvoker(invoker));

        maybeCompileToBytecode(invoker);
        return invoker;
    }

    private MethodHandle makeVarHandleMethodExactInvoker(VarHandle.AccessMode ak) {
        MethodType mtype = targetType;
        MethodType invokerType = mtype.insertParameterTypes(0, VarHandle.class);

        LambdaForm lform = varHandleMethodExactInvokerHandleForm(ak.name(), mtype);
        VarHandle.AccessDescriptor ad = new VarHandle.AccessDescriptor(mtype, ak.at.ordinal(), ak.ordinal());
        MethodHandle invoker = BoundMethodHandle.bindSingle(invokerType, lform, ad);

        invoker = invoker.withInternalMemberName(MemberName.makeVarHandleMethodInvoke(ak.name(), mtype), false);
        assert(checkVarHandleInvoker(invoker));

        maybeCompileToBytecode(invoker);
        return invoker;
    }

    /** If the target type seems to be common enough, eagerly compile the invoker to bytecodes. */
    private void maybeCompileToBytecode(MethodHandle invoker) {
        final int EAGER_COMPILE_ARITY_LIMIT = 10;
        if (targetType == targetType.erase() &&
            targetType.parameterCount() < EAGER_COMPILE_ARITY_LIMIT) {
            invoker.form.compileToBytecode();
        }
    }

    // This next one is called from LambdaForm.NamedFunction.<init>.
    /*non-public*/ static MemberName invokeBasicMethod(MethodType basicType) {
        assert(basicType == basicType.basicType());
        try {
            //Lookup.findVirtual(MethodHandle.class, name, type);
            return IMPL_LOOKUP.resolveOrFail(REF_invokeVirtual, MethodHandle.class, "invokeBasic", basicType);
        } catch (ReflectiveOperationException ex) {
            throw newInternalError("JVM cannot find invoker for "+basicType, ex);
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

    private boolean checkVarHandleInvoker(MethodHandle invoker) {
        MethodType invokerType = targetType.insertParameterTypes(0, VarHandle.class);
        assert(invokerType.equals(invoker.type()))
                : java.util.Arrays.asList(targetType, invokerType, invoker);
        assert(invoker.internalMemberName() == null ||
               invoker.internalMemberName().getMethodType().equals(targetType));
        assert(!invoker.isVarargsCollector());
        return true;
    }

    /**
     * Find or create an invoker which passes unchanged a given number of arguments
     * and spreads the rest from a trailing array argument.
     * The invoker target type is the post-spread type {@code (TYPEOF(uarg*), TYPEOF(sarg*))=>RT}.
     * All the {@code sarg}s must have a common type {@code C}.  (If there are none, {@code Object} is assumed.}
     * @param leadingArgCount the number of unchanged (non-spread) arguments
     * @return {@code invoker.invokeExact(mh, uarg*, C[]{sarg*}) := (RT)mh.invoke(uarg*, sarg*)}
     */
    /*non-public*/ MethodHandle spreadInvoker(int leadingArgCount) {
        int spreadArgCount = targetType.parameterCount() - leadingArgCount;
        MethodType postSpreadType = targetType;
        Class<?> argArrayType = impliedRestargType(postSpreadType, leadingArgCount);
        if (postSpreadType.parameterSlotCount() <= MethodType.MAX_MH_INVOKER_ARITY) {
            return genericInvoker().asSpreader(argArrayType, spreadArgCount);
        }
        // Cannot build a generic invoker here of type ginvoker.invoke(mh, a*[254]).
        // Instead, factor sinvoker.invoke(mh, a) into ainvoker.invoke(filter(mh), a)
        // where filter(mh) == mh.asSpreader(Object[], spreadArgCount)
        MethodType preSpreadType = postSpreadType
            .replaceParameterTypes(leadingArgCount, postSpreadType.parameterCount(), argArrayType);
        MethodHandle arrayInvoker = MethodHandles.invoker(preSpreadType);
        MethodHandle makeSpreader = MethodHandles.insertArguments(Lazy.MH_asSpreader, 1, argArrayType, spreadArgCount);
        return MethodHandles.filterArgument(arrayInvoker, 0, makeSpreader);
    }

    private static Class<?> impliedRestargType(MethodType restargType, int fromPos) {
        if (restargType.isGeneric())  return Object[].class;  // can be nothing else
        int maxPos = restargType.parameterCount();
        if (fromPos >= maxPos)  return Object[].class;  // reasonable default
        Class<?> argType = restargType.parameterType(fromPos);
        for (int i = fromPos+1; i < maxPos; i++) {
            if (argType != restargType.parameterType(i))
                throw newIllegalArgumentException("need homogeneous rest arguments", restargType);
        }
        if (argType == Object.class)  return Object[].class;
        return Array.newInstance(argType, 0).getClass();
    }

    public String toString() {
        return "Invokers"+targetType;
    }

    static MemberName methodHandleInvokeLinkerMethod(String name,
                                                     MethodType mtype,
                                                     Object[] appendixResult) {
        int which;
        switch (name) {
            case "invokeExact":  which = MethodTypeForm.LF_EX_LINKER; break;
            case "invoke":       which = MethodTypeForm.LF_GEN_LINKER; break;
            default:             throw new InternalError("not invoker: "+name);
        }
        LambdaForm lform;
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_ARITY - MH_LINKER_ARG_APPENDED) {
            lform = invokeHandleForm(mtype, false, which);
            appendixResult[0] = mtype;
        } else {
            lform = invokeHandleForm(mtype, true, which);
        }
        return lform.vmentry;
    }

    // argument count to account for trailing "appendix value" (typically the mtype)
    private static final int MH_LINKER_ARG_APPENDED = 1;

    /** Returns an adapter for invokeExact or generic invoke, as a MH or constant pool linker.
     * If !customized, caller is responsible for supplying, during adapter execution,
     * a copy of the exact mtype.  This is because the adapter might be generalized to
     * a basic type.
     * @param mtype the caller's method type (either basic or full-custom)
     * @param customized whether to use a trailing appendix argument (to carry the mtype)
     * @param which bit-encoded 0x01 whether it is a CP adapter ("linker") or MHs.invoker value ("invoker");
     *                          0x02 whether it is for invokeExact or generic invoke
     */
    private static LambdaForm invokeHandleForm(MethodType mtype, boolean customized, int which) {
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
        final int CHECK_CUSTOM = (CUSTOMIZE_THRESHOLD >= 0) ? nameCursor++ : -1;
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
            BoundMethodHandle.SpeciesData speciesData = BoundMethodHandle.speciesData_L();
            names[THIS_MH] = names[THIS_MH].withConstraint(speciesData);
            NamedFunction getter = speciesData.getterFunction(0);
            names[MTYPE_ARG] = new Name(getter, names[THIS_MH]);
            // else if isLinker, then MTYPE is passed in from the caller (e.g., the JVM)
        }

        // Make the final call.  If isGeneric, then prepend the result of type checking.
        MethodType outCallType = mtype.basicType();
        Object[] outArgs = Arrays.copyOfRange(names, CALL_MH, OUTARG_LIMIT, Object[].class);
        Object mtypeArg = (customized ? mtype : names[MTYPE_ARG]);
        if (!isGeneric) {
            names[CHECK_TYPE] = new Name(NF_checkExactType, names[CALL_MH], mtypeArg);
            // mh.invokeExact(a*):R => checkExactType(mh, TYPEOF(a*:R)); mh.invokeBasic(a*)
        } else {
            names[CHECK_TYPE] = new Name(NF_checkGenericType, names[CALL_MH], mtypeArg);
            // mh.invokeGeneric(a*):R => checkGenericType(mh, TYPEOF(a*:R)).invokeBasic(a*)
            outArgs[0] = names[CHECK_TYPE];
        }
        if (CHECK_CUSTOM != -1) {
            names[CHECK_CUSTOM] = new Name(NF_checkCustomized, outArgs[0]);
        }
        names[LINKER_CALL] = new Name(outCallType, outArgs);
        lform = new LambdaForm(debugName, INARG_LIMIT, names);
        if (isLinker)
            lform.compileToBytecode();  // JVM needs a real methodOop
        if (isCached)
            lform = mtype.form().setCachedLambdaForm(which, lform);
        return lform;
    }


    static MemberName varHandleInvokeLinkerMethod(String name,
                                                  MethodType mtype) {
        LambdaForm lform;
        if (mtype.parameterSlotCount() <= MethodType.MAX_MH_ARITY - MH_LINKER_ARG_APPENDED) {
            lform = varHandleMethodGenericLinkerHandleForm(name, mtype);
        } else {
            // TODO
            throw newInternalError("Unsupported parameter slot count " + mtype.parameterSlotCount());
        }
        return lform.vmentry;
    }

    private static LambdaForm varHandleMethodGenericLinkerHandleForm(String name, MethodType mtype) {
        // TODO Cache form?

        final int THIS_VH      = 0;
        final int ARG_BASE     = THIS_VH + 1;
        final int ARG_LIMIT = ARG_BASE + mtype.parameterCount();
        int nameCursor = ARG_LIMIT;
        final int VAD_ARG      = nameCursor++;
        final int CHECK_TYPE   = nameCursor++;
        final int CHECK_CUSTOM = (CUSTOMIZE_THRESHOLD >= 0) ? nameCursor++ : -1;
        final int LINKER_CALL  = nameCursor++;

        Name[] names = new Name[LINKER_CALL + 1];
        names[THIS_VH] = argument(THIS_VH, BasicType.basicType(Object.class));
        for (int i = 0; i < mtype.parameterCount(); i++) {
            names[ARG_BASE + i] = argument(ARG_BASE + i, BasicType.basicType(mtype.parameterType(i)));
        }
        names[VAD_ARG] = new Name(ARG_LIMIT, BasicType.basicType(Object.class));

        names[CHECK_TYPE] = new Name(NF_checkVarHandleGenericType, names[THIS_VH], names[VAD_ARG]);

        Object[] outArgs = new Object[ARG_LIMIT + 1];
        outArgs[0] = names[CHECK_TYPE];
        for (int i = 0; i < ARG_LIMIT; i++) {
            outArgs[i + 1] = names[i];
        }

        if (CHECK_CUSTOM != -1) {
            names[CHECK_CUSTOM] = new Name(NF_checkCustomized, outArgs[0]);
        }

        MethodType outCallType = mtype.insertParameterTypes(0, VarHandle.class)
                .basicType();
        names[LINKER_CALL] = new Name(outCallType, outArgs);
        LambdaForm lform = new LambdaForm(name + ":VarHandle_invoke_MT_" + shortenSignature(basicTypeSignature(mtype)),
                                          ARG_LIMIT + 1, names);

        lform.prepare();
        return lform;
    }

    private static LambdaForm varHandleMethodExactInvokerHandleForm(String name, MethodType mtype) {
        // TODO Cache form?

        final int THIS_MH      = 0;
        final int CALL_VH      = THIS_MH + 1;
        final int ARG_BASE     = CALL_VH + 1;
        final int ARG_LIMIT = ARG_BASE + mtype.parameterCount();
        int nameCursor = ARG_LIMIT;
        final int VAD_ARG      = nameCursor++;
        final int CHECK_TYPE   = nameCursor++;
        final int GET_MEMBER   = nameCursor++;
        final int LINKER_CALL  = nameCursor++;

        MethodType invokerFormType = mtype.insertParameterTypes(0, VarHandle.class)
                .basicType()
                .appendParameterTypes(MemberName.class);

        MemberName linker = new MemberName(MethodHandle.class, "linkToStatic", invokerFormType, REF_invokeStatic);
        try {
            linker = MemberName.getFactory().resolveOrFail(REF_invokeStatic, linker, null, NoSuchMethodException.class);
        } catch (ReflectiveOperationException ex) {
            throw newInternalError(ex);
        }

        Name[] names = new Name[LINKER_CALL + 1];
        names[THIS_MH] = argument(THIS_MH, BasicType.basicType(Object.class));
        names[CALL_VH] = argument(CALL_VH, BasicType.basicType(Object.class));
        for (int i = 0; i < mtype.parameterCount(); i++) {
            names[ARG_BASE + i] = argument(ARG_BASE + i, BasicType.basicType(mtype.parameterType(i)));
        }

        BoundMethodHandle.SpeciesData speciesData = BoundMethodHandle.speciesData_L();
        names[THIS_MH] = names[THIS_MH].withConstraint(speciesData);

        NamedFunction getter = speciesData.getterFunction(0);
        names[VAD_ARG] = new Name(getter, names[THIS_MH]);

        Object[] outArgs = Arrays.copyOfRange(names, CALL_VH, ARG_LIMIT + 1, Object[].class);

        names[CHECK_TYPE] = new Name(NF_checkVarHandleExactType, names[CALL_VH], names[VAD_ARG]);

        names[GET_MEMBER] = new Name(NF_getVarHandleMemberName, names[CALL_VH], names[VAD_ARG]);
        outArgs[outArgs.length - 1] = names[GET_MEMBER];

        names[LINKER_CALL] = new Name(linker, outArgs);
        LambdaForm lform = new LambdaForm(name + ":VarHandle_exactInvoker" + shortenSignature(basicTypeSignature(mtype)),
                                          ARG_LIMIT, names);

        lform.prepare();
        return lform;
    }

    private static LambdaForm varHandleMethodGenericInvokerHandleForm(String name, MethodType mtype) {
        // TODO Cache form?

        final int THIS_MH      = 0;
        final int CALL_VH      = THIS_MH + 1;
        final int ARG_BASE     = CALL_VH + 1;
        final int ARG_LIMIT = ARG_BASE + mtype.parameterCount();
        int nameCursor = ARG_LIMIT;
        final int VAD_ARG      = nameCursor++;
        final int CHECK_TYPE   = nameCursor++;
        final int LINKER_CALL  = nameCursor++;

        Name[] names = new Name[LINKER_CALL + 1];
        names[THIS_MH] = argument(THIS_MH, BasicType.basicType(Object.class));
        names[CALL_VH] = argument(CALL_VH, BasicType.basicType(Object.class));
        for (int i = 0; i < mtype.parameterCount(); i++) {
            names[ARG_BASE + i] = argument(ARG_BASE + i, BasicType.basicType(mtype.parameterType(i)));
        }

        BoundMethodHandle.SpeciesData speciesData = BoundMethodHandle.speciesData_L();
        names[THIS_MH] = names[THIS_MH].withConstraint(speciesData);

        NamedFunction getter = speciesData.getterFunction(0);
        names[VAD_ARG] = new Name(getter, names[THIS_MH]);

        names[CHECK_TYPE] = new Name(NF_checkVarHandleGenericType, names[CALL_VH], names[VAD_ARG]);

        Object[] outArgs = new Object[ARG_LIMIT];
        outArgs[0] = names[CHECK_TYPE];
        for (int i = 1; i < ARG_LIMIT; i++) {
            outArgs[i] = names[i];
        }

        MethodType outCallType = mtype.insertParameterTypes(0, VarHandle.class)
                .basicType();
        names[LINKER_CALL] = new Name(outCallType, outArgs);
        LambdaForm lform = new LambdaForm(name + ":VarHandle_invoker" + shortenSignature(basicTypeSignature(mtype)),
                                          ARG_LIMIT, names);

        lform.prepare();
        return lform;
    }

    /*non-public*/ static
    @ForceInline
    MethodHandle checkVarHandleGenericType(VarHandle vh, VarHandle.AccessDescriptor vad) {
        MethodType expected = vad.symbolicMethodType;
        MethodType actual = VarHandle.AccessType.getMethodType(vad.type, vh);

        MemberName mn = VarHandle.AccessMode.getMemberName(vad.mode, vh.vform);
        if (mn == null)
            throw vh.unsupported();
        // TODO the following MH is not constant, cache in stable field array
        // on VarForm?
        MethodHandle mh = DirectMethodHandle.make(mn);
        if (actual == expected) {
            return mh;
        }
        else {
            // Adapt to the actual (which should never fail since mh's method
            // type is in the basic form), then to the expected (which my fail
            // if the symbolic type descriptor does not match)
            // TODO optimize for the case of actual.erased() == expected.erased()
            return mh.asType(actual.insertParameterTypes(0, VarHandle.class)).
                    asType(expected.insertParameterTypes(0, VarHandle.class));
        }
    }

    /*non-public*/ static
    @ForceInline
    void checkVarHandleExactType(VarHandle vh, VarHandle.AccessDescriptor vad) {
        MethodType expected = vad.symbolicMethodType;
        MethodType actual = VarHandle.AccessType.getMethodType(vad.type, vh);
        if (actual != expected)
            throw newWrongMethodTypeException(expected, actual);
    }

    /*non-public*/ static
    @ForceInline
    MemberName getVarHandleMemberName(VarHandle vh, VarHandle.AccessDescriptor vad) {
        MemberName mn = VarHandle.AccessMode.getMemberName(vad.mode, vh.vform);
        if (mn == null) {
            throw vh.unsupported();
        }
        return mn;
    }

    /*non-public*/ static
    WrongMethodTypeException newWrongMethodTypeException(MethodType actual, MethodType expected) {
        // FIXME: merge with JVM logic for throwing WMTE
        return new WrongMethodTypeException("expected "+expected+" but found "+actual);
    }

    /** Static definition of MethodHandle.invokeExact checking code. */
    /*non-public*/ static
    @ForceInline
    void checkExactType(MethodHandle mh, MethodType expected) {
        MethodType actual = mh.type();
        if (actual != expected)
            throw newWrongMethodTypeException(expected, actual);
    }

    /** Static definition of MethodHandle.invokeGeneric checking code.
     * Directly returns the type-adjusted MH to invoke, as follows:
     * {@code (R)MH.invoke(a*) => MH.asType(TYPEOF(a*:R)).invokeBasic(a*)}
     */
    /*non-public*/ static
    @ForceInline
    MethodHandle checkGenericType(MethodHandle mh,  MethodType expected) {
        return mh.asType(expected);
        /* Maybe add more paths here.  Possible optimizations:
         * for (R)MH.invoke(a*),
         * let MT0 = TYPEOF(a*:R), MT1 = MH.type
         *
         * if MT0==MT1 or MT1 can be safely called by MT0
         *  => MH.invokeBasic(a*)
         * if MT1 can be safely called by MT0[R := Object]
         *  => MH.invokeBasic(a*) & checkcast(R)
         * if MT1 can be safely called by MT0[* := Object]
         *  => checkcast(A)* & MH.invokeBasic(a*) & checkcast(R)
         * if a big adapter BA can be pulled out of (MT0,MT1)
         *  => BA.invokeBasic(MT0,MH,a*)
         * if a local adapter LA can cached on static CS0 = new GICS(MT0)
         *  => CS0.LA.invokeBasic(MH,a*)
         * else
         *  => MH.asType(MT0).invokeBasic(A*)
         */
    }

    static MemberName linkToCallSiteMethod(MethodType mtype) {
        LambdaForm lform = callSiteForm(mtype, false);
        return lform.vmentry;
    }

    static MemberName linkToTargetMethod(MethodType mtype) {
        LambdaForm lform = callSiteForm(mtype, true);
        return lform.vmentry;
    }

    // skipCallSite is true if we are optimizing a ConstantCallSite
    private static LambdaForm callSiteForm(MethodType mtype, boolean skipCallSite) {
        mtype = mtype.basicType();  // normalize Z to I, String to Object, etc.
        final int which = (skipCallSite ? MethodTypeForm.LF_MH_LINKER : MethodTypeForm.LF_CS_LINKER);
        LambdaForm lform = mtype.form().cachedLambdaForm(which);
        if (lform != null)  return lform;
        // exactInvokerForm (Object,Object)Object
        //   link with java.lang.invoke.MethodHandle.invokeBasic(MethodHandle,Object,Object)Object/invokeSpecial
        final int ARG_BASE     = 0;
        final int OUTARG_LIMIT = ARG_BASE + mtype.parameterCount();
        final int INARG_LIMIT  = OUTARG_LIMIT + 1;
        int nameCursor = OUTARG_LIMIT;
        final int APPENDIX_ARG = nameCursor++;  // the last in-argument
        final int CSITE_ARG    = skipCallSite ? -1 : APPENDIX_ARG;
        final int CALL_MH      = skipCallSite ? APPENDIX_ARG : nameCursor++;  // result of getTarget
        final int LINKER_CALL  = nameCursor++;
        MethodType invokerFormType = mtype.appendParameterTypes(skipCallSite ? MethodHandle.class : CallSite.class);
        Name[] names = arguments(nameCursor - INARG_LIMIT, invokerFormType);
        assert(names.length == nameCursor);
        assert(names[APPENDIX_ARG] != null);
        if (!skipCallSite)
            names[CALL_MH] = new Name(NF_getCallSiteTarget, names[CSITE_ARG]);
        // (site.)invokedynamic(a*):R => mh = site.getTarget(); mh.invokeBasic(a*)
        final int PREPEND_MH = 0, PREPEND_COUNT = 1;
        Object[] outArgs = Arrays.copyOfRange(names, ARG_BASE, OUTARG_LIMIT + PREPEND_COUNT, Object[].class);
        // prepend MH argument:
        System.arraycopy(outArgs, 0, outArgs, PREPEND_COUNT, outArgs.length - PREPEND_COUNT);
        outArgs[PREPEND_MH] = names[CALL_MH];
        names[LINKER_CALL] = new Name(mtype, outArgs);
        lform = new LambdaForm((skipCallSite ? "linkToTargetMethod" : "linkToCallSite"), INARG_LIMIT, names);
        lform.compileToBytecode();  // JVM needs a real methodOop
        lform = mtype.form().setCachedLambdaForm(which, lform);
        return lform;
    }

    /** Static definition of MethodHandle.invokeGeneric checking code. */
    /*non-public*/ static
    @ForceInline
    MethodHandle getCallSiteTarget(CallSite site) {
        return site.getTarget();
    }

    /*non-public*/ static
    @ForceInline
    void checkCustomized(MethodHandle mh) {
        if (MethodHandleImpl.isCompileConstant(mh)) return;
        if (mh.form.customized == null) {
            maybeCustomize(mh);
        }
    }

    /*non-public*/ static
    @DontInline
    void maybeCustomize(MethodHandle mh) {
        byte count = mh.customizationCount;
        if (count >= CUSTOMIZE_THRESHOLD) {
            mh.customize();
        } else {
            mh.customizationCount = (byte)(count+1);
        }
    }

    // Local constant functions:
    private static final NamedFunction
        NF_checkExactType,
        NF_checkGenericType,
        NF_getCallSiteTarget,
        NF_checkCustomized,
        NF_checkVarHandleGenericType,
        NF_checkVarHandleExactType,
        NF_getVarHandleMemberName;
    static {
        try {
            NamedFunction nfs[] = {
                NF_checkExactType = new NamedFunction(Invokers.class
                        .getDeclaredMethod("checkExactType", MethodHandle.class,  MethodType.class)),
                NF_checkGenericType = new NamedFunction(Invokers.class
                        .getDeclaredMethod("checkGenericType", MethodHandle.class,  MethodType.class)),
                NF_getCallSiteTarget = new NamedFunction(Invokers.class
                        .getDeclaredMethod("getCallSiteTarget", CallSite.class)),
                NF_checkCustomized = new NamedFunction(Invokers.class
                        .getDeclaredMethod("checkCustomized", MethodHandle.class)),
                NF_checkVarHandleGenericType = new NamedFunction(Invokers.class
                        .getDeclaredMethod("checkVarHandleGenericType", VarHandle.class, VarHandle.AccessDescriptor.class)),
                NF_checkVarHandleExactType = new NamedFunction(Invokers.class
                        .getDeclaredMethod("checkVarHandleExactType", VarHandle.class, VarHandle.AccessDescriptor.class)),
                NF_getVarHandleMemberName = new NamedFunction(Invokers.class
                        .getDeclaredMethod("getVarHandleMemberName", VarHandle.class, VarHandle.AccessDescriptor.class))
            };
            // Each nf must be statically invocable or we get tied up in our bootstraps.
            assert(InvokerBytecodeGenerator.isStaticallyInvocable(nfs));
        } catch (ReflectiveOperationException ex) {
            throw newInternalError(ex);
        }
    }

    private static class Lazy {
        private static final MethodHandle MH_asSpreader;

        static {
            try {
                MH_asSpreader = IMPL_LOOKUP.findVirtual(MethodHandle.class, "asSpreader",
                        MethodType.methodType(MethodHandle.class, Class.class, int.class));
            } catch (ReflectiveOperationException ex) {
                throw newInternalError(ex);
            }
        }
    }
}
