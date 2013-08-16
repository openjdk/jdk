/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Type.UndetVar.InferenceBound;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.sun.tools.javac.comp.Infer.GraphSolver.InferenceGraph;
import com.sun.tools.javac.comp.Infer.GraphSolver.InferenceGraph.Node;
import com.sun.tools.javac.comp.Resolve.InapplicableMethodException;
import com.sun.tools.javac.comp.Resolve.VerboseResolutionMode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import static com.sun.tools.javac.code.TypeTag.*;

/** Helper class for type parameter inference, used by the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Infer {
    protected static final Context.Key<Infer> inferKey =
        new Context.Key<Infer>();

    Resolve rs;
    Check chk;
    Symtab syms;
    Types types;
    JCDiagnostic.Factory diags;
    Log log;

    /** should the graph solver be used? */
    boolean allowGraphInference;

    public static Infer instance(Context context) {
        Infer instance = context.get(inferKey);
        if (instance == null)
            instance = new Infer(context);
        return instance;
    }

    protected Infer(Context context) {
        context.put(inferKey, this);

        rs = Resolve.instance(context);
        chk = Check.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        log = Log.instance(context);
        inferenceException = new InferenceException(diags);
        Options options = Options.instance(context);
        allowGraphInference = Source.instance(context).allowGraphInference()
                && options.isUnset("useLegacyInference");
    }

    /** A value for prototypes that admit any type, including polymorphic ones. */
    public static final Type anyPoly = new JCNoType();

   /**
    * This exception class is design to store a list of diagnostics corresponding
    * to inference errors that can arise during a method applicability check.
    */
    public static class InferenceException extends InapplicableMethodException {
        private static final long serialVersionUID = 0;

        List<JCDiagnostic> messages = List.nil();

        InferenceException(JCDiagnostic.Factory diags) {
            super(diags);
        }

        @Override
        InapplicableMethodException setMessage(JCDiagnostic diag) {
            messages = messages.append(diag);
            return this;
        }

        @Override
        public JCDiagnostic getDiagnostic() {
            return messages.head;
        }

        void clear() {
            messages = List.nil();
        }
    }

    protected final InferenceException inferenceException;

    // <editor-fold defaultstate="collapsed" desc="Inference routines">
    /**
     * Main inference entry point - instantiate a generic method type
     * using given argument types and (possibly) an expected target-type.
     */
    public Type instantiateMethod(Env<AttrContext> env,
                                  List<Type> tvars,
                                  MethodType mt,
                                  Attr.ResultInfo resultInfo,
                                  Symbol msym,
                                  List<Type> argtypes,
                                  boolean allowBoxing,
                                  boolean useVarargs,
                                  Resolve.MethodResolutionContext resolveContext,
                                  Warner warn) throws InferenceException {
        //-System.err.println("instantiateMethod(" + tvars + ", " + mt + ", " + argtypes + ")"); //DEBUG
        final InferenceContext inferenceContext = new InferenceContext(tvars);
        inferenceException.clear();
        try {
            DeferredAttr.DeferredAttrContext deferredAttrContext =
                        resolveContext.deferredAttrContext(msym, inferenceContext, resultInfo, warn);

            resolveContext.methodCheck.argumentsAcceptable(env, deferredAttrContext,
                    argtypes, mt.getParameterTypes(), warn);

            if (allowGraphInference &&
                    resultInfo != null &&
                    !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {
                //inject return constraints earlier
                checkWithinBounds(inferenceContext, warn); //propagation
                Type newRestype = generateReturnConstraints(resultInfo, mt, inferenceContext);
                mt = (MethodType)types.createMethodTypeWithReturn(mt, newRestype);
                //propagate outwards if needed
                if (resultInfo.checkContext.inferenceContext().free(resultInfo.pt)) {
                    //propagate inference context outwards and exit
                    inferenceContext.dupTo(resultInfo.checkContext.inferenceContext());
                    deferredAttrContext.complete();
                    return mt;
                }
            }

            deferredAttrContext.complete();

            // minimize as yet undetermined type variables
            if (allowGraphInference) {
                inferenceContext.solve(warn);
            } else {
                inferenceContext.solveLegacy(true, warn, LegacyInferenceSteps.EQ_LOWER.steps); //minimizeInst
            }

            mt = (MethodType)inferenceContext.asInstType(mt);

            if (!allowGraphInference &&
                    inferenceContext.restvars().nonEmpty() &&
                    resultInfo != null &&
                    !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {
                generateReturnConstraints(resultInfo, mt, inferenceContext);
                inferenceContext.solveLegacy(false, warn, LegacyInferenceSteps.EQ_UPPER.steps); //maximizeInst
                mt = (MethodType)inferenceContext.asInstType(mt);
            }

            if (resultInfo != null && rs.verboseResolutionMode.contains(VerboseResolutionMode.DEFERRED_INST)) {
                log.note(env.tree.pos, "deferred.method.inst", msym, mt, resultInfo.pt);
            }

            // return instantiated version of method type
            return mt;
        } finally {
            if (resultInfo != null || !allowGraphInference) {
                inferenceContext.notifyChange();
            } else {
                inferenceContext.notifyChange(inferenceContext.boundedVars());
            }
        }
    }

    /**
     * Generate constraints from the generic method's return type. If the method
     * call occurs in a context where a type T is expected, use the expected
     * type to derive more constraints on the generic method inference variables.
     */
    Type generateReturnConstraints(Attr.ResultInfo resultInfo,
            MethodType mt, InferenceContext inferenceContext) {
        Type from = mt.getReturnType();
        if (mt.getReturnType().containsAny(inferenceContext.inferencevars) &&
                resultInfo.checkContext.inferenceContext() != emptyContext) {
            from = types.capture(from);
            //add synthetic captured ivars
            for (Type t : from.getTypeArguments()) {
                if (t.hasTag(TYPEVAR) && ((TypeVar)t).isCaptured()) {
                    inferenceContext.addVar((TypeVar)t);
                }
            }
        }
        Type qtype1 = inferenceContext.asFree(from);
        Type to = returnConstraintTarget(qtype1, resultInfo.pt);
        Assert.check(allowGraphInference || !resultInfo.checkContext.inferenceContext().free(to),
                "legacy inference engine cannot handle constraints on both sides of a subtyping assertion");
        //we need to skip capture?
        Warner retWarn = new Warner();
        if (!resultInfo.checkContext.compatible(qtype1, resultInfo.checkContext.inferenceContext().asFree(to), retWarn) ||
                //unchecked conversion is not allowed in source 7 mode
                (!allowGraphInference && retWarn.hasLint(Lint.LintCategory.UNCHECKED))) {
            throw inferenceException
                    .setMessage("infer.no.conforming.instance.exists",
                    inferenceContext.restvars(), mt.getReturnType(), to);
        }
        return from;
    }

    Type returnConstraintTarget(Type from, Type to) {
        if (from.hasTag(VOID)) {
            return syms.voidType;
        } else if (to.hasTag(NONE)) {
            return from.isPrimitive() ? from : syms.objectType;
        } else if (from.hasTag(UNDETVAR) && to.isPrimitive()) {
            if (!allowGraphInference) {
                //if legacy, just return boxed type
                return types.boxedClass(to).type;
            }
            //if graph inference we need to skip conflicting boxed bounds...
            UndetVar uv = (UndetVar)from;
            for (Type t : uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)) {
                Type boundAsPrimitive = types.unboxedType(t);
                if (boundAsPrimitive == null) continue;
                if (types.isConvertible(boundAsPrimitive, to)) {
                    //effectively skip return-type constraint generation (compatibility)
                    return syms.objectType;
                }
            }
            return types.boxedClass(to).type;
        } else {
            return to;
        }
    }

    /**
      * Infer cyclic inference variables as described in 15.12.2.8.
      */
    private void instantiateAsUninferredVars(List<Type> vars, InferenceContext inferenceContext) {
        ListBuffer<Type> todo = ListBuffer.lb();
        //step 1 - create fresh tvars
        for (Type t : vars) {
            UndetVar uv = (UndetVar)inferenceContext.asFree(t);
            List<Type> upperBounds = uv.getBounds(InferenceBound.UPPER);
            if (Type.containsAny(upperBounds, vars)) {
                TypeSymbol fresh_tvar = new TypeVariableSymbol(Flags.SYNTHETIC, uv.qtype.tsym.name, null, uv.qtype.tsym.owner);
                fresh_tvar.type = new TypeVar(fresh_tvar, types.makeCompoundType(uv.getBounds(InferenceBound.UPPER)), null);
                todo.append(uv);
                uv.inst = fresh_tvar.type;
            } else if (upperBounds.nonEmpty()) {
                uv.inst = types.glb(upperBounds);
            } else {
                uv.inst = syms.objectType;
            }
        }
        //step 2 - replace fresh tvars in their bounds
        List<Type> formals = vars;
        for (Type t : todo) {
            UndetVar uv = (UndetVar)t;
            TypeVar ct = (TypeVar)uv.inst;
            ct.bound = types.glb(inferenceContext.asInstTypes(types.getBounds(ct)));
            if (ct.bound.isErroneous()) {
                //report inference error if glb fails
                reportBoundError(uv, BoundErrorKind.BAD_UPPER);
            }
            formals = formals.tail;
        }
    }

    /**
     * Compute a synthetic method type corresponding to the requested polymorphic
     * method signature. The target return type is computed from the immediately
     * enclosing scope surrounding the polymorphic-signature call.
     */
    Type instantiatePolymorphicSignatureInstance(Env<AttrContext> env,
                                            MethodSymbol spMethod,  // sig. poly. method or null if none
                                            Resolve.MethodResolutionContext resolveContext,
                                            List<Type> argtypes) {
        final Type restype;

        //The return type for a polymorphic signature call is computed from
        //the enclosing tree E, as follows: if E is a cast, then use the
        //target type of the cast expression as a return type; if E is an
        //expression statement, the return type is 'void' - otherwise the
        //return type is simply 'Object'. A correctness check ensures that
        //env.next refers to the lexically enclosing environment in which
        //the polymorphic signature call environment is nested.

        switch (env.next.tree.getTag()) {
            case TYPECAST:
                JCTypeCast castTree = (JCTypeCast)env.next.tree;
                restype = (TreeInfo.skipParens(castTree.expr) == env.tree) ?
                    castTree.clazz.type :
                    syms.objectType;
                break;
            case EXEC:
                JCTree.JCExpressionStatement execTree =
                        (JCTree.JCExpressionStatement)env.next.tree;
                restype = (TreeInfo.skipParens(execTree.expr) == env.tree) ?
                    syms.voidType :
                    syms.objectType;
                break;
            default:
                restype = syms.objectType;
        }

        List<Type> paramtypes = Type.map(argtypes, new ImplicitArgType(spMethod, resolveContext.step));
        List<Type> exType = spMethod != null ?
            spMethod.getThrownTypes() :
            List.of(syms.throwableType); // make it throw all exceptions

        MethodType mtype = new MethodType(paramtypes,
                                          restype,
                                          exType,
                                          syms.methodClass);
        return mtype;
    }
    //where
        class ImplicitArgType extends DeferredAttr.DeferredTypeMap {

            public ImplicitArgType(Symbol msym, Resolve.MethodResolutionPhase phase) {
                rs.deferredAttr.super(AttrMode.SPECULATIVE, msym, phase);
            }

            public Type apply(Type t) {
                t = types.erasure(super.apply(t));
                if (t.hasTag(BOT))
                    // nulls type as the marker type Null (which has no instances)
                    // infer as java.lang.Void for now
                    t = types.boxedClass(syms.voidType).type;
                return t;
            }
        }

    /**
      * This method is used to infer a suitable target SAM in case the original
      * SAM type contains one or more wildcards. An inference process is applied
      * so that wildcard bounds, as well as explicit lambda/method ref parameters
      * (where applicable) are used to constraint the solution.
      */
    public Type instantiateFunctionalInterface(DiagnosticPosition pos, Type funcInterface,
            List<Type> paramTypes, Check.CheckContext checkContext) {
        if (types.capture(funcInterface) == funcInterface) {
            //if capture doesn't change the type then return the target unchanged
            //(this means the target contains no wildcards!)
            return funcInterface;
        } else {
            Type formalInterface = funcInterface.tsym.type;
            InferenceContext funcInterfaceContext =
                    new InferenceContext(funcInterface.tsym.type.getTypeArguments());

            Assert.check(paramTypes != null);
            //get constraints from explicit params (this is done by
            //checking that explicit param types are equal to the ones
            //in the functional interface descriptors)
            List<Type> descParameterTypes = types.findDescriptorType(formalInterface).getParameterTypes();
            if (descParameterTypes.size() != paramTypes.size()) {
                checkContext.report(pos, diags.fragment("incompatible.arg.types.in.lambda"));
                return types.createErrorType(funcInterface);
            }
            for (Type p : descParameterTypes) {
                if (!types.isSameType(funcInterfaceContext.asFree(p), paramTypes.head)) {
                    checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
                    return types.createErrorType(funcInterface);
                }
                paramTypes = paramTypes.tail;
            }

            try {
                funcInterfaceContext.solve(funcInterfaceContext.boundedVars(), types.noWarnings);
            } catch (InferenceException ex) {
                checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
            }

            List<Type> actualTypeargs = funcInterface.getTypeArguments();
            for (Type t : funcInterfaceContext.undetvars) {
                UndetVar uv = (UndetVar)t;
                if (uv.inst == null) {
                    uv.inst = actualTypeargs.head;
                }
                actualTypeargs = actualTypeargs.tail;
            }

            Type owntype = funcInterfaceContext.asInstType(formalInterface);
            if (!chk.checkValidGenericType(owntype)) {
                //if the inferred functional interface type is not well-formed,
                //or if it's not a subtype of the original target, issue an error
                checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
            }
            return owntype;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Bound checking">
    /**
     * Check bounds and perform incorporation
     */
    void checkWithinBounds(InferenceContext inferenceContext,
                             Warner warn) throws InferenceException {
        MultiUndetVarListener mlistener = new MultiUndetVarListener(inferenceContext.undetvars);
        List<Type> saved_undet = inferenceContext.save();
        try {
            while (true) {
                mlistener.reset();
                if (!allowGraphInference) {
                    //in legacy mode we lack of transitivity, so bound check
                    //cannot be run in parallel with other incoprporation rounds
                    for (Type t : inferenceContext.undetvars) {
                        UndetVar uv = (UndetVar)t;
                        IncorporationStep.CHECK_BOUNDS.apply(uv, inferenceContext, warn);
                    }
                }
                for (Type t : inferenceContext.undetvars) {
                    UndetVar uv = (UndetVar)t;
                    //bound incorporation
                    EnumSet<IncorporationStep> incorporationSteps = allowGraphInference ?
                            incorporationStepsGraph : incorporationStepsLegacy;
                    for (IncorporationStep is : incorporationSteps) {
                        if (is.accepts(uv, inferenceContext)) {
                            is.apply(uv, inferenceContext, warn);
                        }
                    }
                }
                if (!mlistener.changed || !allowGraphInference) break;
            }
        }
        finally {
            mlistener.detach();
            if (incorporationCache.size() == MAX_INCORPORATION_STEPS) {
                inferenceContext.rollback(saved_undet);
            }
            incorporationCache.clear();
        }
    }
    //where
        /**
         * This listener keeps track of changes on a group of inference variable
         * bounds. Note: the listener must be detached (calling corresponding
         * method) to make sure that the underlying inference variable is
         * left in a clean state.
         */
        class MultiUndetVarListener implements UndetVar.UndetVarListener {

            boolean changed;
            List<Type> undetvars;

            public MultiUndetVarListener(List<Type> undetvars) {
                this.undetvars = undetvars;
                for (Type t : undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.listener = this;
                }
            }

            public void varChanged(UndetVar uv, Set<InferenceBound> ibs) {
                //avoid non-termination
                if (incorporationCache.size() < MAX_INCORPORATION_STEPS) {
                    changed = true;
                }
            }

            void reset() {
                changed = false;
            }

            void detach() {
                for (Type t : undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.listener = null;
                }
            }
        };

        /** max number of incorporation rounds */
        static final int MAX_INCORPORATION_STEPS = 100;

    /**
     * This enumeration defines an entry point for doing inference variable
     * bound incorporation - it can be used to inject custom incorporation
     * logic into the basic bound checking routine
     */
    enum IncorporationStep {
        /**
         * Performs basic bound checking - i.e. is the instantiated type for a given
         * inference variable compatible with its bounds?
         */
        CHECK_BOUNDS() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                uv.substBounds(inferenceContext.inferenceVars(), inferenceContext.instTypes(), infer.types);
                infer.checkCompatibleUpperBounds(uv, inferenceContext);
                if (uv.inst != null) {
                    Type inst = uv.inst;
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (!isSubtype(inst, inferenceContext.asFree(u), warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.UPPER);
                        }
                    }
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        if (!isSubtype(inferenceContext.asFree(l), inst, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.LOWER);
                        }
                    }
                    for (Type e : uv.getBounds(InferenceBound.EQ)) {
                        if (!isSameType(inst, inferenceContext.asFree(e), infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.EQ);
                        }
                    }
                }
            }
            @Override
            boolean accepts(UndetVar uv, InferenceContext inferenceContext) {
                //applies to all undetvars
                return true;
            }
        },
        /**
         * Check consistency of equality constraints. This is a slightly more aggressive
         * inference routine that is designed as to maximize compatibility with JDK 7.
         * Note: this is not used in graph mode.
         */
        EQ_CHECK_LEGACY() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                Type eq = null;
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    Assert.check(!inferenceContext.free(e));
                    if (eq != null && !isSameType(e, eq, infer)) {
                        infer.reportBoundError(uv, BoundErrorKind.EQ);
                    }
                    eq = e;
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        Assert.check(!inferenceContext.free(l));
                        if (!isSubtype(l, e, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                        }
                    }
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (inferenceContext.free(u)) continue;
                        if (!isSubtype(e, u, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                        }
                    }
                }
            }
        },
        /**
         * Check consistency of equality constraints.
         */
        EQ_CHECK() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    if (e.containsAny(inferenceContext.inferenceVars())) continue;
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (!isSubtype(e, inferenceContext.asFree(u), warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                        }
                    }
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        if (!isSubtype(inferenceContext.asFree(l), e, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                        }
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha <: T} and {@code alpha :> S}
         * perform {@code S <: T} (which could lead to new bounds).
         */
        CROSS_UPPER_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.UPPER)) {
                    for (Type b2 : uv.getBounds(InferenceBound.LOWER)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn , infer);
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha <: T} and {@code alpha == S}
         * perform {@code S <: T} (which could lead to new bounds).
         */
        CROSS_UPPER_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.UPPER)) {
                    for (Type b2 : uv.getBounds(InferenceBound.EQ)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn, infer);
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha :> S} and {@code alpha == T}
         * perform {@code S <: T} (which could lead to new bounds).
         */
        CROSS_EQ_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.EQ)) {
                    for (Type b2 : uv.getBounds(InferenceBound.LOWER)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn, infer);
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha == S} and {@code alpha == T}
         * perform {@code S == T} (which could lead to new bounds).
         */
        CROSS_EQ_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.EQ)) {
                    for (Type b2 : uv.getBounds(InferenceBound.EQ)) {
                        if (b1 != b2) {
                            isSameType(inferenceContext.asFree(b2), inferenceContext.asFree(b1), infer);
                        }
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha <: beta} propagate lower bounds
         * from alpha to beta; also propagate upper bounds from beta to alpha.
         */
        PROP_UPPER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.UPPER)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar)inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;
                        //alpha <: beta
                        //0. set beta :> alpha
                        addBound(InferenceBound.LOWER, uv2, inferenceContext.asInstType(uv.qtype), infer);
                        //1. copy alpha's lower to beta's
                        for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                            addBound(InferenceBound.LOWER, uv2, inferenceContext.asInstType(l), infer);
                        }
                        //2. copy beta's upper to alpha's
                        for (Type u : uv2.getBounds(InferenceBound.UPPER)) {
                            addBound(InferenceBound.UPPER, uv, inferenceContext.asInstType(u), infer);
                        }
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha :> beta} propagate lower bounds
         * from beta to alpha; also propagate upper bounds from alpha to beta.
         */
        PROP_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.LOWER)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar)inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;
                        //alpha :> beta
                        //0. set beta <: alpha
                        addBound(InferenceBound.UPPER, uv2, inferenceContext.asInstType(uv.qtype), infer);
                        //1. copy alpha's upper to beta's
                        for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                            addBound(InferenceBound.UPPER, uv2, inferenceContext.asInstType(u), infer);
                        }
                        //2. copy beta's lower to alpha's
                        for (Type l : uv2.getBounds(InferenceBound.LOWER)) {
                            addBound(InferenceBound.LOWER, uv, inferenceContext.asInstType(l), infer);
                        }
                    }
                }
            }
        },
        /**
         * Given a bound set containing {@code alpha == beta} propagate lower/upper
         * bounds from alpha to beta and back.
         */
        PROP_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.EQ)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar)inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;
                        //alpha == beta
                        //0. set beta == alpha
                        addBound(InferenceBound.EQ, uv2, inferenceContext.asInstType(uv.qtype), infer);
                        //1. copy all alpha's bounds to beta's
                        for (InferenceBound ib : InferenceBound.values()) {
                            for (Type b2 : uv.getBounds(ib)) {
                                if (b2 != uv2) {
                                    addBound(ib, uv2, inferenceContext.asInstType(b2), infer);
                                }
                            }
                        }
                        //2. copy all beta's bounds to alpha's
                        for (InferenceBound ib : InferenceBound.values()) {
                            for (Type b2 : uv2.getBounds(ib)) {
                                if (b2 != uv) {
                                    addBound(ib, uv, inferenceContext.asInstType(b2), infer);
                                }
                            }
                        }
                    }
                }
            }
        };

        abstract void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn);

        boolean accepts(UndetVar uv, InferenceContext inferenceContext) {
            return !uv.isCaptured();
        }

        boolean isSubtype(Type s, Type t, Warner warn, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SUBTYPE, s, t, warn, infer);
        }

        boolean isSameType(Type s, Type t, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SAME_TYPE, s, t, null, infer);
        }

        void addBound(InferenceBound ib, UndetVar uv, Type b, Infer infer) {
            doIncorporationOp(opFor(ib), uv, b, null, infer);
        }

        IncorporationBinaryOpKind opFor(InferenceBound boundKind) {
            switch (boundKind) {
                case EQ:
                    return IncorporationBinaryOpKind.ADD_EQ_BOUND;
                case LOWER:
                    return IncorporationBinaryOpKind.ADD_LOWER_BOUND;
                case UPPER:
                    return IncorporationBinaryOpKind.ADD_UPPER_BOUND;
                default:
                    Assert.error("Can't get here!");
                    return null;
            }
        }

        boolean doIncorporationOp(IncorporationBinaryOpKind opKind, Type op1, Type op2, Warner warn, Infer infer) {
            IncorporationBinaryOp newOp = infer.new IncorporationBinaryOp(opKind, op1, op2);
            Boolean res = infer.incorporationCache.get(newOp);
            if (res == null) {
                infer.incorporationCache.put(newOp, res = newOp.apply(warn));
            }
            return res;
        }
    }

    /** incorporation steps to be executed when running in legacy mode */
    EnumSet<IncorporationStep> incorporationStepsLegacy = EnumSet.of(IncorporationStep.EQ_CHECK_LEGACY);

    /** incorporation steps to be executed when running in graph mode */
    EnumSet<IncorporationStep> incorporationStepsGraph =
            EnumSet.complementOf(EnumSet.of(IncorporationStep.EQ_CHECK_LEGACY));

    /**
     * Three kinds of basic operation are supported as part of an incorporation step:
     * (i) subtype check, (ii) same type check and (iii) bound addition (either
     * upper/lower/eq bound).
     */
    enum IncorporationBinaryOpKind {
        IS_SUBTYPE() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                return types.isSubtypeUnchecked(op1, op2, warn);
            }
        },
        IS_SAME_TYPE() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                return types.isSameType(op1, op2);
            }
        },
        ADD_UPPER_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar)op1;
                uv.addBound(InferenceBound.UPPER, op2, types);
                return true;
            }
        },
        ADD_LOWER_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar)op1;
                uv.addBound(InferenceBound.LOWER, op2, types);
                return true;
            }
        },
        ADD_EQ_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar)op1;
                uv.addBound(InferenceBound.EQ, op2, types);
                return true;
            }
        };

        abstract boolean apply(Type op1, Type op2, Warner warn, Types types);
    }

    /**
     * This class encapsulates a basic incorporation operation; incorporation
     * operations takes two type operands and a kind. Each operation performed
     * during an incorporation round is stored in a cache, so that operations
     * are not executed unnecessarily (which would potentially lead to adding
     * same bounds over and over).
     */
    class IncorporationBinaryOp {

        IncorporationBinaryOpKind opKind;
        Type op1;
        Type op2;

        IncorporationBinaryOp(IncorporationBinaryOpKind opKind, Type op1, Type op2) {
            this.opKind = opKind;
            this.op1 = op1;
            this.op2 = op2;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IncorporationBinaryOp)) {
                return false;
            } else {
                IncorporationBinaryOp that = (IncorporationBinaryOp)o;
                return opKind == that.opKind &&
                        types.isSameType(op1, that.op1, true) &&
                        types.isSameType(op2, that.op2, true);
            }
        }

        @Override
        public int hashCode() {
            int result = opKind.hashCode();
            result *= 127;
            result += types.hashCode(op1);
            result *= 127;
            result += types.hashCode(op2);
            return result;
        }

        boolean apply(Warner warn) {
            return opKind.apply(op1, op2, warn, types);
        }
    }

    /** an incorporation cache keeps track of all executed incorporation-related operations */
    Map<IncorporationBinaryOp, Boolean> incorporationCache =
            new HashMap<IncorporationBinaryOp, Boolean>();

    /**
     * Make sure that the upper bounds we got so far lead to a solvable inference
     * variable by making sure that a glb exists.
     */
    void checkCompatibleUpperBounds(UndetVar uv, InferenceContext inferenceContext) {
        List<Type> hibounds =
                Type.filter(uv.getBounds(InferenceBound.UPPER), new BoundFilter(inferenceContext));
        Type hb = null;
        if (hibounds.isEmpty())
            hb = syms.objectType;
        else if (hibounds.tail.isEmpty())
            hb = hibounds.head;
        else
            hb = types.glb(hibounds);
        if (hb == null || hb.isErroneous())
            reportBoundError(uv, BoundErrorKind.BAD_UPPER);
    }
    //where
        protected static class BoundFilter implements Filter<Type> {

            InferenceContext inferenceContext;

            public BoundFilter(InferenceContext inferenceContext) {
                this.inferenceContext = inferenceContext;
            }

            @Override
            public boolean accepts(Type t) {
                return !t.isErroneous() && !inferenceContext.free(t) &&
                        !t.hasTag(BOT);
            }
        };

    /**
     * This enumeration defines all possible bound-checking related errors.
     */
    enum BoundErrorKind {
        /**
         * The (uninstantiated) inference variable has incompatible upper bounds.
         */
        BAD_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },
        /**
         * An equality constraint is not compatible with an upper bound.
         */
        BAD_EQ_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.UPPER));
            }
        },
        /**
         * An equality constraint is not compatible with a lower bound.
         */
        BAD_EQ_LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.lower.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.LOWER));
            }
        },
        /**
         * Instantiated inference variable is not compatible with an upper bound.
         */
        UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.upper.bounds", uv.inst,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },
        /**
         * Instantiated inference variable is not compatible with a lower bound.
         */
        LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.lower.bounds", uv.inst,
                        uv.getBounds(InferenceBound.LOWER));
            }
        },
        /**
         * Instantiated inference variable is not compatible with an equality constraint.
         */
        EQ() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.eq.bounds", uv.inst,
                        uv.getBounds(InferenceBound.EQ));
            }
        };

        abstract InapplicableMethodException setMessage(InferenceException ex, UndetVar uv);
    }

    /**
     * Report a bound-checking error of given kind
     */
    void reportBoundError(UndetVar uv, BoundErrorKind bk) {
        throw bk.setMessage(inferenceException, uv);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inference engine">
    /**
     * Graph inference strategy - act as an input to the inference solver; a strategy is
     * composed of two ingredients: (i) find a node to solve in the inference graph,
     * and (ii) tell th engine when we are done fixing inference variables
     */
    interface GraphStrategy {
        /**
         * Pick the next node (leaf) to solve in the graph
         */
        Node pickNode(InferenceGraph g);
        /**
         * Is this the last step?
         */
        boolean done();
    }

    /**
     * Simple solver strategy class that locates all leaves inside a graph
     * and picks the first leaf as the next node to solve
     */
    abstract class LeafSolver implements GraphStrategy {
        public Node pickNode(InferenceGraph g) {
                        Assert.check(!g.nodes.isEmpty(), "No nodes to solve!");
            return g.nodes.get(0);
        }

        boolean isSubtype(Type s, Type t, Warner warn, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SUBTYPE, s, t, warn, infer);
        }

        boolean isSameType(Type s, Type t, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SAME_TYPE, s, t, null, infer);
        }

        void addBound(InferenceBound ib, UndetVar uv, Type b, Infer infer) {
            doIncorporationOp(opFor(ib), uv, b, null, infer);
        }

        IncorporationBinaryOpKind opFor(InferenceBound boundKind) {
            switch (boundKind) {
                case EQ:
                    return IncorporationBinaryOpKind.ADD_EQ_BOUND;
                case LOWER:
                    return IncorporationBinaryOpKind.ADD_LOWER_BOUND;
                case UPPER:
                    return IncorporationBinaryOpKind.ADD_UPPER_BOUND;
                default:
                    Assert.error("Can't get here!");
                    return null;
            }
        }

        boolean doIncorporationOp(IncorporationBinaryOpKind opKind, Type op1, Type op2, Warner warn, Infer infer) {
            IncorporationBinaryOp newOp = infer.new IncorporationBinaryOp(opKind, op1, op2);
            Boolean res = infer.incorporationCache.get(newOp);
            if (res == null) {
                infer.incorporationCache.put(newOp, res = newOp.apply(warn));
            }
            return res;
        }
    }

    /**
     * This solver uses an heuristic to pick the best leaf - the heuristic
     * tries to select the node that has maximal probability to contain one
     * or more inference variables in a given list
     */
    abstract class BestLeafSolver extends LeafSolver {

        List<Type> varsToSolve;

        BestLeafSolver(List<Type> varsToSolve) {
            this.varsToSolve = varsToSolve;
        }

        /**
         * Computes the minimum path that goes from a given node to any of the nodes
         * containing a variable in {@code varsToSolve}. For any given path, the cost
         * is computed as the total number of type-variables that should be eagerly
         * instantiated across that path.
         */
        int computeMinPath(InferenceGraph g, Node n) {
            return computeMinPath(g, n, List.<Node>nil(), 0);
        }

        int computeMinPath(InferenceGraph g, Node n, List<Node> path, int cost) {
            if (path.contains(n)) return Integer.MAX_VALUE;
            List<Node> path2 = path.prepend(n);
            int cost2 = cost + n.data.size();
            if (!Collections.disjoint(n.data, varsToSolve)) {
                return cost2;
            } else {
               int bestPath = Integer.MAX_VALUE;
               for (Node n2 : g.nodes) {
                   if (n2.deps.contains(n)) {
                       int res = computeMinPath(g, n2, path2, cost2);
                       if (res < bestPath) {
                           bestPath = res;
                       }
                   }
                }
               return bestPath;
            }
        }

        /**
         * Pick the leaf that minimize cost
         */
        @Override
        public Node pickNode(final InferenceGraph g) {
            final Map<Node, Integer> leavesMap = new HashMap<Node, Integer>();
            for (Node n : g.nodes) {
                if (n.isLeaf(n)) {
                    leavesMap.put(n, computeMinPath(g, n));
                }
            }
            Assert.check(!leavesMap.isEmpty(), "No nodes to solve!");
            TreeSet<Node> orderedLeaves = new TreeSet<Node>(new Comparator<Node>() {
                public int compare(Node n1, Node n2) {
                    return leavesMap.get(n1) - leavesMap.get(n2);
                }
            });
            orderedLeaves.addAll(leavesMap.keySet());
            return orderedLeaves.first();
        }
    }

    /**
     * The inference process can be thought of as a sequence of steps. Each step
     * instantiates an inference variable using a subset of the inference variable
     * bounds, if certain condition are met. Decisions such as the sequence in which
     * steps are applied, or which steps are to be applied are left to the inference engine.
     */
    enum InferenceStep {

        /**
         * Instantiate an inference variables using one of its (ground) equality
         * constraints
         */
        EQ(InferenceBound.EQ) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return filterBounds(uv, inferenceContext).head;
            }
        },
        /**
         * Instantiate an inference variables using its (ground) lower bounds. Such
         * bounds are merged together using lub().
         */
        LOWER(InferenceBound.LOWER) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                List<Type> lobounds = filterBounds(uv, inferenceContext);
                //note: lobounds should have at least one element
                Type owntype = lobounds.tail.tail == null  ? lobounds.head : infer.types.lub(lobounds);
                if (owntype.isPrimitive() || owntype.hasTag(ERROR)) {
                    throw infer.inferenceException
                        .setMessage("no.unique.minimal.instance.exists",
                                    uv.qtype, lobounds);
                } else {
                    return owntype;
                }
            }
        },
        /**
         * Infer uninstantiated/unbound inference variables occurring in 'throws'
         * clause as RuntimeException
         */
        THROWS(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                if ((t.qtype.tsym.flags() & Flags.THROWS) == 0) {
                    //not a throws undet var
                    return false;
                }
                if (t.getBounds(InferenceBound.EQ, InferenceBound.LOWER, InferenceBound.UPPER)
                            .diff(t.getDeclaredBounds()).nonEmpty()) {
                    //not an unbounded undet var
                    return false;
                }
                Infer infer = inferenceContext.infer();
                for (Type db : t.getDeclaredBounds()) {
                    if (t.isInterface()) continue;
                    if (infer.types.asSuper(infer.syms.runtimeExceptionType, db.tsym) != null) {
                        //declared bound is a supertype of RuntimeException
                        return true;
                    }
                }
                //declared bound is more specific then RuntimeException - give up
                return false;
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return inferenceContext.infer().syms.runtimeExceptionType;
            }
        },
        /**
         * Instantiate an inference variables using its (ground) upper bounds. Such
         * bounds are merged together using glb().
         */
        UPPER(InferenceBound.UPPER) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                List<Type> hibounds = filterBounds(uv, inferenceContext);
                //note: lobounds should have at least one element
                Type owntype = hibounds.tail.tail == null  ? hibounds.head : infer.types.glb(hibounds);
                if (owntype.isPrimitive() || owntype.hasTag(ERROR)) {
                    throw infer.inferenceException
                        .setMessage("no.unique.maximal.instance.exists",
                                    uv.qtype, hibounds);
                } else {
                    return owntype;
                }
            }
        },
        /**
         * Like the former; the only difference is that this step can only be applied
         * if all upper bounds are ground.
         */
        UPPER_LEGACY(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                return !inferenceContext.free(t.getBounds(ib)) && !t.isCaptured();
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return UPPER.solve(uv, inferenceContext);
            }
        },
        /**
         * Like the former; the only difference is that this step can only be applied
         * if all upper/lower bounds are ground.
         */
        CAPTURED(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                return t.isCaptured() &&
                        !inferenceContext.free(t.getBounds(InferenceBound.UPPER, InferenceBound.LOWER));
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                Type upper = UPPER.filterBounds(uv, inferenceContext).nonEmpty() ?
                        UPPER.solve(uv, inferenceContext) :
                        infer.syms.objectType;
                Type lower = LOWER.filterBounds(uv, inferenceContext).nonEmpty() ?
                        LOWER.solve(uv, inferenceContext) :
                        infer.syms.botType;
                CapturedType prevCaptured = (CapturedType)uv.qtype;
                return new CapturedType(prevCaptured.tsym.name, prevCaptured.tsym.owner, upper, lower, prevCaptured.wildcard);
            }
        };

        final InferenceBound ib;

        InferenceStep(InferenceBound ib) {
            this.ib = ib;
        }

        /**
         * Find an instantiated type for a given inference variable within
         * a given inference context
         */
        abstract Type solve(UndetVar uv, InferenceContext inferenceContext);

        /**
         * Can the inference variable be instantiated using this step?
         */
        public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
            return filterBounds(t, inferenceContext).nonEmpty() && !t.isCaptured();
        }

        /**
         * Return the subset of ground bounds in a given bound set (i.e. eq/lower/upper)
         */
        List<Type> filterBounds(UndetVar uv, InferenceContext inferenceContext) {
            return Type.filter(uv.getBounds(ib), new BoundFilter(inferenceContext));
        }
    }

    /**
     * This enumeration defines the sequence of steps to be applied when the
     * solver works in legacy mode. The steps in this enumeration reflect
     * the behavior of old inference routine (see JLS SE 7 15.12.2.7/15.12.2.8).
     */
    enum LegacyInferenceSteps {

        EQ_LOWER(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER)),
        EQ_UPPER(EnumSet.of(InferenceStep.EQ, InferenceStep.UPPER_LEGACY));

        final EnumSet<InferenceStep> steps;

        LegacyInferenceSteps(EnumSet<InferenceStep> steps) {
            this.steps = steps;
        }
    }

    /**
     * This enumeration defines the sequence of steps to be applied when the
     * graph solver is used. This order is defined so as to maximize compatibility
     * w.r.t. old inference routine (see JLS SE 7 15.12.2.7/15.12.2.8).
     */
    enum GraphInferenceSteps {

        EQ(EnumSet.of(InferenceStep.EQ)),
        EQ_LOWER(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER)),
        EQ_LOWER_THROWS_UPPER_CAPTURED(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER, InferenceStep.UPPER, InferenceStep.THROWS, InferenceStep.CAPTURED));

        final EnumSet<InferenceStep> steps;

        GraphInferenceSteps(EnumSet<InferenceStep> steps) {
            this.steps = steps;
        }
    }

    /**
     * This is the graph inference solver - the solver organizes all inference variables in
     * a given inference context by bound dependencies - in the general case, such dependencies
     * would lead to a cyclic directed graph (hence the name); the dependency info is used to build
     * an acyclic graph, where all cyclic variables are bundled together. An inference
     * step corresponds to solving a node in the acyclic graph - this is done by
     * relying on a given strategy (see GraphStrategy).
     */
    class GraphSolver {

        InferenceContext inferenceContext;
        Warner warn;

        GraphSolver(InferenceContext inferenceContext, Warner warn) {
            this.inferenceContext = inferenceContext;
            this.warn = warn;
        }

        /**
         * Solve variables in a given inference context. The amount of variables
         * to be solved, and the way in which the underlying acyclic graph is explored
         * depends on the selected solver strategy.
         */
        void solve(GraphStrategy sstrategy) {
            checkWithinBounds(inferenceContext, warn); //initial propagation of bounds
            InferenceGraph inferenceGraph = new InferenceGraph();
            while (!sstrategy.done()) {
                InferenceGraph.Node nodeToSolve = sstrategy.pickNode(inferenceGraph);
                List<Type> varsToSolve = List.from(nodeToSolve.data);
                List<Type> saved_undet = inferenceContext.save();
                try {
                    //repeat until all variables are solved
                    outer: while (Type.containsAny(inferenceContext.restvars(), varsToSolve)) {
                        //for each inference phase
                        for (GraphInferenceSteps step : GraphInferenceSteps.values()) {
                            if (inferenceContext.solveBasic(varsToSolve, step.steps)) {
                                checkWithinBounds(inferenceContext, warn);
                                continue outer;
                            }
                        }
                        //no progress
                        throw inferenceException.setMessage();
                    }
                }
                catch (InferenceException ex) {
                    //did we fail because of interdependent ivars?
                    inferenceContext.rollback(saved_undet);
                    instantiateAsUninferredVars(varsToSolve, inferenceContext);
                    checkWithinBounds(inferenceContext, warn);
                }
                inferenceGraph.deleteNode(nodeToSolve);
            }
        }

        /**
         * The dependencies between the inference variables that need to be solved
         * form a (possibly cyclic) graph. This class reduces the original dependency graph
         * to an acyclic version, where cyclic nodes are folded into a single 'super node'.
         */
        class InferenceGraph {

            /**
             * This class represents a node in the graph. Each node corresponds
             * to an inference variable and has edges (dependencies) on other
             * nodes. The node defines an entry point that can be used to receive
             * updates on the structure of the graph this node belongs to (used to
             * keep dependencies in sync).
             */
            class Node extends GraphUtils.TarjanNode<ListBuffer<Type>> {

                Set<Node> deps;

                Node(Type ivar) {
                    super(ListBuffer.of(ivar));
                    this.deps = new HashSet<Node>();
                }

                @Override
                public Iterable<? extends Node> getDependencies() {
                    return deps;
                }

                @Override
                public String printDependency(GraphUtils.Node<ListBuffer<Type>> to) {
                    StringBuilder buf = new StringBuilder();
                    String sep = "";
                    for (Type from : data) {
                        UndetVar uv = (UndetVar)inferenceContext.asFree(from);
                        for (Type bound : uv.getBounds(InferenceBound.values())) {
                            if (bound.containsAny(List.from(to.data))) {
                                buf.append(sep);
                                buf.append(bound);
                                sep = ",";
                            }
                        }
                    }
                    return buf.toString();
                }

                boolean isLeaf(Node n) {
                    //no deps, or only one self dep
                    return (n.deps.isEmpty() ||
                            n.deps.size() == 1 && n.deps.contains(n));
                }

                void mergeWith(List<? extends Node> nodes) {
                    for (Node n : nodes) {
                        Assert.check(n.data.length() == 1, "Attempt to merge a compound node!");
                        data.appendList(n.data);
                        deps.addAll(n.deps);
                    }
                    //update deps
                    Set<Node> deps2 = new HashSet<Node>();
                    for (Node d : deps) {
                        if (data.contains(d.data.first())) {
                            deps2.add(this);
                        } else {
                            deps2.add(d);
                        }
                    }
                    deps = deps2;
                }

                void graphChanged(Node from, Node to) {
                    if (deps.contains(from)) {
                        deps.remove(from);
                        if (to != null) {
                            deps.add(to);
                        }
                    }
                }
            }

            /** the nodes in the inference graph */
            ArrayList<Node> nodes;

            InferenceGraph() {
                initNodes();
            }

            /**
             * Delete a node from the graph. This update the underlying structure
             * of the graph (including dependencies) via listeners updates.
             */
            public void deleteNode(Node n) {
                Assert.check(nodes.contains(n));
                nodes.remove(n);
                notifyUpdate(n, null);
            }

            /**
             * Notify all nodes of a change in the graph. If the target node is
             * {@code null} the source node is assumed to be removed.
             */
            void notifyUpdate(Node from, Node to) {
                for (Node n : nodes) {
                    n.graphChanged(from, to);
                }
            }

            /**
             * Create the graph nodes. First a simple node is created for every inference
             * variables to be solved. Then Tarjan is used to found all connected components
             * in the graph. For each component containing more than one node, a super node is
                 * created, effectively replacing the original cyclic nodes.
             */
            void initNodes() {
                nodes = new ArrayList<Node>();
                for (Type t : inferenceContext.restvars()) {
                    nodes.add(new Node(t));
                }
                for (Node n_i : nodes) {
                    Type i = n_i.data.first();
                    for (Node n_j : nodes) {
                        Type j = n_j.data.first();
                        UndetVar uv_i = (UndetVar)inferenceContext.asFree(i);
                        if (Type.containsAny(uv_i.getBounds(InferenceBound.values()), List.of(j))) {
                            //update i's deps
                            n_i.deps.add(n_j);
                        }
                    }
                }
                ArrayList<Node> acyclicNodes = new ArrayList<Node>();
                for (List<? extends Node> conSubGraph : GraphUtils.tarjan(nodes)) {
                    if (conSubGraph.length() > 1) {
                        Node root = conSubGraph.head;
                        root.mergeWith(conSubGraph.tail);
                        for (Node n : conSubGraph) {
                            notifyUpdate(n, root);
                        }
                    }
                    acyclicNodes.add(conSubGraph.head);
                }
                nodes = acyclicNodes;
            }

            /**
             * Debugging: dot representation of this graph
             */
            String toDot() {
                StringBuilder buf = new StringBuilder();
                for (Type t : inferenceContext.undetvars) {
                    UndetVar uv = (UndetVar)t;
                    buf.append(String.format("var %s - upper bounds = %s, lower bounds = %s, eq bounds = %s\\n",
                            uv.qtype, uv.getBounds(InferenceBound.UPPER), uv.getBounds(InferenceBound.LOWER),
                            uv.getBounds(InferenceBound.EQ)));
                }
                return GraphUtils.toDot(nodes, "inferenceGraph" + hashCode(), buf.toString());
            }
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Inference context">
    /**
     * Functional interface for defining inference callbacks. Certain actions
     * (i.e. subtyping checks) might need to be redone after all inference variables
     * have been fixed.
     */
    interface FreeTypeListener {
        void typesInferred(InferenceContext inferenceContext);
    }

    /**
     * An inference context keeps track of the set of variables that are free
     * in the current context. It provides utility methods for opening/closing
     * types to their corresponding free/closed forms. It also provide hooks for
     * attaching deferred post-inference action (see PendingCheck). Finally,
     * it can be used as an entry point for performing upper/lower bound inference
     * (see InferenceKind).
     */
     class InferenceContext {

        /** list of inference vars as undet vars */
        List<Type> undetvars;

        /** list of inference vars in this context */
        List<Type> inferencevars;

        java.util.Map<FreeTypeListener, List<Type>> freeTypeListeners =
                new java.util.HashMap<FreeTypeListener, List<Type>>();

        List<FreeTypeListener> freetypeListeners = List.nil();

        public InferenceContext(List<Type> inferencevars) {
            this.undetvars = Type.map(inferencevars, fromTypeVarFun);
            this.inferencevars = inferencevars;
        }
        //where
            Mapping fromTypeVarFun = new Mapping("fromTypeVarFunWithBounds") {
                // mapping that turns inference variables into undet vars
                public Type apply(Type t) {
                    if (t.hasTag(TYPEVAR)) {
                        TypeVar tv = (TypeVar)t;
                        return tv.isCaptured() ?
                                new CapturedUndetVar((CapturedType)tv, types) :
                                new UndetVar(tv, types);
                    } else {
                        return t.map(this);
                    }
                }
            };

        /**
         * add a new inference var to this inference context
         */
        void addVar(TypeVar t) {
            this.undetvars = this.undetvars.prepend(fromTypeVarFun.apply(t));
            this.inferencevars = this.inferencevars.prepend(t);
        }

        /**
         * returns the list of free variables (as type-variables) in this
         * inference context
         */
        List<Type> inferenceVars() {
            return inferencevars;
        }

        /**
         * returns the list of uninstantiated variables (as type-variables) in this
         * inference context
         */
        List<Type> restvars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.inst == null;
                }
            });
        }

        /**
         * returns the list of instantiated variables (as type-variables) in this
         * inference context
         */
        List<Type> instvars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.inst != null;
                }
            });
        }

        /**
         * Get list of bounded inference variables (where bound is other than
         * declared bounds).
         */
        final List<Type> boundedVars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.getBounds(InferenceBound.UPPER)
                            .diff(uv.getDeclaredBounds())
                            .appendList(uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)).nonEmpty();
                }
            });
        }

        private List<Type> filterVars(Filter<UndetVar> fu) {
            ListBuffer<Type> res = ListBuffer.lb();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar)t;
                if (fu.accepts(uv)) {
                    res.append(uv.qtype);
                }
            }
            return res.toList();
        }

        /**
         * is this type free?
         */
        final boolean free(Type t) {
            return t.containsAny(inferencevars);
        }

        final boolean free(List<Type> ts) {
            for (Type t : ts) {
                if (free(t)) return true;
            }
            return false;
        }

        /**
         * Returns a list of free variables in a given type
         */
        final List<Type> freeVarsIn(Type t) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type iv : inferenceVars()) {
                if (t.contains(iv)) {
                    buf.add(iv);
                }
            }
            return buf.toList();
        }

        final List<Type> freeVarsIn(List<Type> ts) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : ts) {
                buf.appendList(freeVarsIn(t));
            }
            ListBuffer<Type> buf2 = ListBuffer.lb();
            for (Type t : buf) {
                if (!buf2.contains(t)) {
                    buf2.add(t);
                }
            }
            return buf2.toList();
        }

        /**
         * Replace all free variables in a given type with corresponding
         * undet vars (used ahead of subtyping/compatibility checks to allow propagation
         * of inference constraints).
         */
        final Type asFree(Type t) {
            return types.subst(t, inferencevars, undetvars);
        }

        final List<Type> asFree(List<Type> ts) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : ts) {
                buf.append(asFree(t));
            }
            return buf.toList();
        }

        List<Type> instTypes() {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar)t;
                buf.append(uv.inst != null ? uv.inst : uv.qtype);
            }
            return buf.toList();
        }

        /**
         * Replace all free variables in a given type with corresponding
         * instantiated types - if one or more free variable has not been
         * fully instantiated, it will still be available in the resulting type.
         */
        Type asInstType(Type t) {
            return types.subst(t, inferencevars, instTypes());
        }

        List<Type> asInstTypes(List<Type> ts) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : ts) {
                buf.append(asInstType(t));
            }
            return buf.toList();
        }

        /**
         * Add custom hook for performing post-inference action
         */
        void addFreeTypeListener(List<Type> types, FreeTypeListener ftl) {
            freeTypeListeners.put(ftl, freeVarsIn(types));
        }

        /**
         * Mark the inference context as complete and trigger evaluation
         * of all deferred checks.
         */
        void notifyChange() {
            notifyChange(inferencevars.diff(restvars()));
        }

        void notifyChange(List<Type> inferredVars) {
            InferenceException thrownEx = null;
            for (Map.Entry<FreeTypeListener, List<Type>> entry :
                    new HashMap<FreeTypeListener, List<Type>>(freeTypeListeners).entrySet()) {
                if (!Type.containsAny(entry.getValue(), inferencevars.diff(inferredVars))) {
                    try {
                        entry.getKey().typesInferred(this);
                        freeTypeListeners.remove(entry.getKey());
                    } catch (InferenceException ex) {
                        if (thrownEx == null) {
                            thrownEx = ex;
                        }
                    }
                }
            }
            //inference exception multiplexing - present any inference exception
            //thrown when processing listeners as a single one
            if (thrownEx != null) {
                throw thrownEx;
            }
        }

        /**
         * Save the state of this inference context
         */
        List<Type> save() {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar)t;
                UndetVar uv2 = new UndetVar((TypeVar)uv.qtype, types);
                for (InferenceBound ib : InferenceBound.values()) {
                    for (Type b : uv.getBounds(ib)) {
                        uv2.addBound(ib, b, types);
                    }
                }
                uv2.inst = uv.inst;
                buf.add(uv2);
            }
            return buf.toList();
        }

        /**
         * Restore the state of this inference context to the previous known checkpoint
         */
        void rollback(List<Type> saved_undet) {
             Assert.check(saved_undet != null && saved_undet.length() == undetvars.length());
            //restore bounds (note: we need to preserve the old instances)
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar)t;
                UndetVar uv_saved = (UndetVar)saved_undet.head;
                for (InferenceBound ib : InferenceBound.values()) {
                    uv.setBounds(ib, uv_saved.getBounds(ib));
                }
                uv.inst = uv_saved.inst;
                saved_undet = saved_undet.tail;
            }
        }

        /**
         * Copy variable in this inference context to the given context
         */
        void dupTo(final InferenceContext that) {
            that.inferencevars = that.inferencevars.appendList(inferencevars);
            that.undetvars = that.undetvars.appendList(undetvars);
            //set up listeners to notify original inference contexts as
            //propagated vars are inferred in new context
            for (Type t : inferencevars) {
                that.freeTypeListeners.put(new FreeTypeListener() {
                    public void typesInferred(InferenceContext inferenceContext) {
                        InferenceContext.this.notifyChange();
                    }
                }, List.of(t));
            }
        }

        /**
         * Solve with given graph strategy.
         */
        private void solve(GraphStrategy ss, Warner warn) {
            GraphSolver s = new GraphSolver(this, warn);
            s.solve(ss);
        }

        /**
         * Solve all variables in this context.
         */
        public void solve(Warner warn) {
            solve(new LeafSolver() {
                public boolean done() {
                    return restvars().isEmpty();
                }
            }, warn);
        }

        /**
         * Solve all variables in the given list.
         */
        public void solve(final List<Type> vars, Warner warn) {
            solve(new BestLeafSolver(vars) {
                public boolean done() {
                    return !free(asInstTypes(vars));
                }
            }, warn);
        }

        /**
         * Solve at least one variable in given list.
         */
        public void solveAny(List<Type> varsToSolve, Warner warn) {
            checkWithinBounds(this, warn); //propagate bounds
            List<Type> boundedVars = boundedVars().intersect(restvars()).intersect(varsToSolve);
            if (boundedVars.isEmpty()) {
                throw inferenceException.setMessage("cyclic.inference",
                                freeVarsIn(varsToSolve));
            }
            solve(new BestLeafSolver(boundedVars) {
                public boolean done() {
                    return instvars().intersect(varsToSolve).nonEmpty();
                }
            }, warn);
        }

        /**
         * Apply a set of inference steps
         */
        private boolean solveBasic(EnumSet<InferenceStep> steps) {
            return solveBasic(inferencevars, steps);
        }

        private boolean solveBasic(List<Type> varsToSolve, EnumSet<InferenceStep> steps) {
            boolean changed = false;
            for (Type t : varsToSolve.intersect(restvars())) {
                UndetVar uv = (UndetVar)asFree(t);
                for (InferenceStep step : steps) {
                    if (step.accepts(uv, this)) {
                        uv.inst = step.solve(uv, this);
                        changed = true;
                        break;
                    }
                }
            }
            return changed;
        }

        /**
         * Instantiate inference variables in legacy mode (JLS 15.12.2.7, 15.12.2.8).
         * During overload resolution, instantiation is done by doing a partial
         * inference process using eq/lower bound instantiation. During check,
         * we also instantiate any remaining vars by repeatedly using eq/upper
         * instantiation, until all variables are solved.
         */
        public void solveLegacy(boolean partial, Warner warn, EnumSet<InferenceStep> steps) {
            while (true) {
                boolean stuck = !solveBasic(steps);
                if (restvars().isEmpty() || partial) {
                    //all variables have been instantiated - exit
                    break;
                } else if (stuck) {
                    //some variables could not be instantiated because of cycles in
                    //upper bounds - provide a (possibly recursive) default instantiation
                    instantiateAsUninferredVars(restvars(), this);
                    break;
                } else {
                    //some variables have been instantiated - replace newly instantiated
                    //variables in remaining upper bounds and continue
                    for (Type t : undetvars) {
                        UndetVar uv = (UndetVar)t;
                        uv.substBounds(inferenceVars(), instTypes(), types);
                    }
                }
            }
            checkWithinBounds(this, warn);
        }

        private Infer infer() {
            //back-door to infer
            return Infer.this;
        }
    }

    final InferenceContext emptyContext = new InferenceContext(List.<Type>nil());
    // </editor-fold>
}
