/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Type.UndetVar.InferenceBound;
import com.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.sun.tools.javac.comp.Resolve.InapplicableMethodException;
import com.sun.tools.javac.comp.Resolve.VerboseResolutionMode;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.HashMap;
import java.util.Map;

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

    /** A value for prototypes that admit any type, including polymorphic ones. */
    public static final Type anyPoly = new Type(NONE, null);

    Symtab syms;
    Types types;
    Check chk;
    Resolve rs;
    DeferredAttr deferredAttr;
    Log log;
    JCDiagnostic.Factory diags;

    /** Should we inject return-type constraints earlier? */
    boolean allowEarlyReturnConstraints;

    public static Infer instance(Context context) {
        Infer instance = context.get(inferKey);
        if (instance == null)
            instance = new Infer(context);
        return instance;
    }

    protected Infer(Context context) {
        context.put(inferKey, this);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        rs = Resolve.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        log = Log.instance(context);
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        inferenceException = new InferenceException(diags);
        allowEarlyReturnConstraints = Source.instance(context).allowEarlyReturnConstraints();
    }

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

    final InferenceException inferenceException;

/***************************************************************************
 * Mini/Maximization of UndetVars
 ***************************************************************************/

    /** Instantiate undetermined type variable to its minimal upper bound.
     *  Throw a NoInstanceException if this not possible.
     */
   void maximizeInst(UndetVar that, Warner warn) throws InferenceException {
        List<Type> hibounds = Type.filter(that.getBounds(InferenceBound.UPPER), boundFilter);
        if (that.getBounds(InferenceBound.EQ).isEmpty()) {
            if (hibounds.isEmpty())
                that.inst = syms.objectType;
            else if (hibounds.tail.isEmpty())
                that.inst = hibounds.head;
            else
                that.inst = types.glb(hibounds);
        } else {
            that.inst = that.getBounds(InferenceBound.EQ).head;
        }
        if (that.inst == null ||
            that.inst.isErroneous())
            throw inferenceException
                .setMessage("no.unique.maximal.instance.exists",
                            that.qtype, hibounds);
    }

    private Filter<Type> boundFilter = new Filter<Type>() {
        @Override
        public boolean accepts(Type t) {
            return !t.isErroneous() && !t.hasTag(BOT);
        }
    };

    /** Instantiate undetermined type variable to the lub of all its lower bounds.
     *  Throw a NoInstanceException if this not possible.
     */
    void minimizeInst(UndetVar that, Warner warn) throws InferenceException {
        List<Type> lobounds = Type.filter(that.getBounds(InferenceBound.LOWER), boundFilter);
        if (that.getBounds(InferenceBound.EQ).isEmpty()) {
            if (lobounds.isEmpty()) {
                //do nothing - the inference variable is under-constrained
                return;
            } else if (lobounds.tail.isEmpty())
                that.inst = lobounds.head.isPrimitive() ? syms.errType : lobounds.head;
            else {
                that.inst = types.lub(lobounds);
            }
            if (that.inst == null || that.inst.hasTag(ERROR))
                    throw inferenceException
                        .setMessage("no.unique.minimal.instance.exists",
                                    that.qtype, lobounds);
        } else {
            that.inst = that.getBounds(InferenceBound.EQ).head;
        }
    }

/***************************************************************************
 * Exported Methods
 ***************************************************************************/

    /**
     * Instantiate uninferred inference variables (JLS 15.12.2.8). First
     * if the method return type is non-void, we derive constraints from the
     * expected type - then we use declared bound well-formedness to derive additional
     * constraints. If no instantiation exists, or if several incomparable
     * best instantiations exist throw a NoInstanceException.
     */
    public void instantiateUninferred(DiagnosticPosition pos,
            InferenceContext inferenceContext,
            MethodType mtype,
            Attr.ResultInfo resultInfo,
            Warner warn) throws InferenceException {
        while (true) {
            boolean stuck = true;
            for (Type t : inferenceContext.undetvars) {
                UndetVar uv = (UndetVar)t;
                if (uv.inst == null && (uv.getBounds(InferenceBound.EQ).nonEmpty() ||
                        !inferenceContext.free(uv.getBounds(InferenceBound.UPPER)))) {
                    maximizeInst((UndetVar)t, warn);
                    stuck = false;
                }
            }
            if (inferenceContext.restvars().isEmpty()) {
                //all variables have been instantiated - exit
                break;
            } else if (stuck) {
                //some variables could not be instantiated because of cycles in
                //upper bounds - provide a (possibly recursive) default instantiation
                instantiateAsUninferredVars(inferenceContext);
                break;
            } else {
                //some variables have been instantiated - replace newly instantiated
                //variables in remaining upper bounds and continue
                for (Type t : inferenceContext.undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.substBounds(inferenceContext.inferenceVars(), inferenceContext.instTypes(), types);
                }
            }
        }
    }

    /**
     * Infer cyclic inference variables as described in 15.12.2.8.
     */
    private void instantiateAsUninferredVars(InferenceContext inferenceContext) {
        ListBuffer<Type> todo = ListBuffer.lb();
        //step 1 - create fresh tvars
        for (Type t : inferenceContext.undetvars) {
            UndetVar uv = (UndetVar)t;
            if (uv.inst == null) {
                TypeSymbol fresh_tvar = new TypeSymbol(Flags.SYNTHETIC, uv.qtype.tsym.name, null, uv.qtype.tsym.owner);
                fresh_tvar.type = new TypeVar(fresh_tvar, types.makeCompoundType(uv.getBounds(InferenceBound.UPPER)), null);
                todo.append(uv);
                uv.inst = fresh_tvar.type;
            }
        }
        //step 2 - replace fresh tvars in their bounds
        List<Type> formals = inferenceContext.inferenceVars();
        for (Type t : todo) {
            UndetVar uv = (UndetVar)t;
            TypeVar ct = (TypeVar)uv.inst;
            ct.bound = types.glb(inferenceContext.asInstTypes(types.getBounds(ct), types));
            if (ct.bound.isErroneous()) {
                //report inference error if glb fails
                reportBoundError(uv, BoundErrorKind.BAD_UPPER);
            }
            formals = formals.tail;
        }
    }

    /** Instantiate a generic method type by finding instantiations for all its
     * inference variables so that it can be applied to a given argument type list.
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
                                  Resolve.MethodCheck methodCheck,
                                  Warner warn) throws InferenceException {
        //-System.err.println("instantiateMethod(" + tvars + ", " + mt + ", " + argtypes + ")"); //DEBUG
        final InferenceContext inferenceContext = new InferenceContext(tvars, this, true);
        inferenceException.clear();

        DeferredAttr.DeferredAttrContext deferredAttrContext =
                resolveContext.deferredAttrContext(msym, inferenceContext);

        try {
            methodCheck.argumentsAcceptable(env, deferredAttrContext, argtypes, mt.getParameterTypes(), warn);

            if (resultInfo != null && allowEarlyReturnConstraints &&
                    !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {
                generateReturnConstraints(mt, inferenceContext, resultInfo);
            }

            deferredAttrContext.complete();

            // minimize as yet undetermined type variables
            for (Type t : inferenceContext.undetvars) {
                minimizeInst((UndetVar)t, warn);
            }

            checkWithinBounds(inferenceContext, warn);

            mt = (MethodType)inferenceContext.asInstType(mt, types);

            List<Type> restvars = inferenceContext.restvars();

            if (!restvars.isEmpty()) {
                if (resultInfo != null && !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {
                    if (!allowEarlyReturnConstraints) {
                        generateReturnConstraints(mt, inferenceContext, resultInfo);
                    }
                    instantiateUninferred(env.tree.pos(), inferenceContext, mt, resultInfo, warn);
                    checkWithinBounds(inferenceContext, warn);
                    mt = (MethodType)inferenceContext.asInstType(mt, types);
                    if (rs.verboseResolutionMode.contains(VerboseResolutionMode.DEFERRED_INST)) {
                        log.note(env.tree.pos, "deferred.method.inst", msym, mt, resultInfo.pt);
                    }
                }
            }

            // return instantiated version of method type
            return mt;
        } finally {
            inferenceContext.notifyChange(types);
        }
    }
    //where
        void generateReturnConstraints(Type mt, InferenceContext inferenceContext, Attr.ResultInfo resultInfo) {
            if (resultInfo != null) {
                Type to = resultInfo.pt;
                if (to.hasTag(NONE) || resultInfo.checkContext.inferenceContext().free(resultInfo.pt)) {
                    to = mt.getReturnType().isPrimitiveOrVoid() ?
                            mt.getReturnType() : syms.objectType;
                }
                Type qtype1 = inferenceContext.asFree(mt.getReturnType(), types);
                Warner retWarn = new Warner();
                if (!resultInfo.checkContext.compatible(qtype1, qtype1.hasTag(UNDETVAR) ? types.boxedTypeOrType(to) : to, retWarn) ||
                        //unchecked conversion is not allowed
                        retWarn.hasLint(Lint.LintCategory.UNCHECKED)) {
                    throw inferenceException
                            .setMessage("infer.no.conforming.instance.exists",
                            inferenceContext.restvars(), mt.getReturnType(), to);
                }
            }
        }

    /** check that type parameters are within their bounds.
     */
    void checkWithinBounds(InferenceContext inferenceContext,
                           Warner warn) throws InferenceException {
        //step 1 - check compatibility of instantiated type w.r.t. initial bounds
        for (Type t : inferenceContext.undetvars) {
            UndetVar uv = (UndetVar)t;
            uv.substBounds(inferenceContext.inferenceVars(), inferenceContext.instTypes(), types);
            checkCompatibleUpperBounds(uv, inferenceContext.inferenceVars());
            if (!inferenceContext.restvars().contains(uv.qtype)) {
                Type inst = inferenceContext.asInstType(t, types);
                for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                    if (!types.isSubtypeUnchecked(inst, inferenceContext.asFree(u, types), warn)) {
                        reportBoundError(uv, BoundErrorKind.UPPER);
                    }
                }
                for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                    Assert.check(!inferenceContext.free(l));
                    if (!types.isSubtypeUnchecked(l, inst, warn)) {
                        reportBoundError(uv, BoundErrorKind.LOWER);
                    }
                }
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    Assert.check(!inferenceContext.free(e));
                    if (!types.isSameType(inst, e)) {
                        reportBoundError(uv, BoundErrorKind.EQ);
                    }
                }
            }
        }

        //step 2 - check that eq bounds are consistent w.r.t. eq/lower bounds
        for (Type t : inferenceContext.undetvars) {
            UndetVar uv = (UndetVar)t;
            //check eq bounds consistency
            Type eq = null;
            for (Type e : uv.getBounds(InferenceBound.EQ)) {
                Assert.check(!inferenceContext.free(e));
                if (eq != null && !types.isSameType(e, eq)) {
                    reportBoundError(uv, BoundErrorKind.EQ);
                }
                eq = e;
                for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                    Assert.check(!inferenceContext.free(l));
                    if (!types.isSubtypeUnchecked(l, e, warn)) {
                        reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                    }
                }
                for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                    if (inferenceContext.free(u)) continue;
                    if (!types.isSubtypeUnchecked(e, u, warn)) {
                        reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                    }
                }
            }
        }
    }

    void checkCompatibleUpperBounds(UndetVar uv, List<Type> tvars) {
        // VGJ: sort of inlined maximizeInst() below.  Adding
        // bounds can cause lobounds that are above hibounds.
        ListBuffer<Type> hiboundsNoVars = ListBuffer.lb();
        for (Type t : Type.filter(uv.getBounds(InferenceBound.UPPER), boundFilter)) {
            if (!t.containsAny(tvars)) {
                hiboundsNoVars.append(t);
            }
        }
        List<Type> hibounds = hiboundsNoVars.toList();
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

    enum BoundErrorKind {
        BAD_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },
        BAD_EQ_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.UPPER));
            }
        },
        BAD_EQ_LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.lower.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.LOWER));
            }
        },
        UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.upper.bounds", uv.inst,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },
        LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.lower.bounds", uv.inst,
                        uv.getBounds(InferenceBound.LOWER));
            }
        },
        EQ() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.eq.bounds", uv.inst,
                        uv.getBounds(InferenceBound.EQ));
            }
        };

        abstract InapplicableMethodException setMessage(InferenceException ex, UndetVar uv);
    }
    //where
    void reportBoundError(UndetVar uv, BoundErrorKind bk) {
        throw bk.setMessage(inferenceException, uv);
    }

    // <editor-fold desc="functional interface instantiation">
    /**
     * This method is used to infer a suitable target functional interface in case
     * the original parameterized interface contains wildcards. An inference process
     * is applied so that wildcard bounds, as well as explicit lambda/method ref parameters
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
                    new InferenceContext(funcInterface.tsym.type.getTypeArguments(), this, false);
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
                if (!types.isSameType(funcInterfaceContext.asFree(p, types), paramTypes.head)) {
                    checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
                    return types.createErrorType(funcInterface);
                }
                paramTypes = paramTypes.tail;
            }
            List<Type> actualTypeargs = funcInterface.getTypeArguments();
            for (Type t : funcInterfaceContext.undetvars) {
                UndetVar uv = (UndetVar)t;
                minimizeInst(uv, types.noWarnings);
                if (uv.inst == null &&
                        Type.filter(uv.getBounds(InferenceBound.UPPER), boundFilter).nonEmpty()) {
                    maximizeInst(uv, types.noWarnings);
                }
                if (uv.inst == null) {
                    uv.inst = actualTypeargs.head;
                }
                actualTypeargs = actualTypeargs.tail;
            }
            Type owntype = funcInterfaceContext.asInstType(formalInterface, types);
            if (!chk.checkValidGenericType(owntype)) {
                //if the inferred functional interface type is not well-formed,
                //or if it's not a subtype of the original target, issue an error
                checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
            }
            return owntype;
        }
    }
    // </editor-fold>

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
                deferredAttr.super(AttrMode.SPECULATIVE, msym, phase);
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
     * Mapping that turns inference variables into undet vars
     * (used by inference context)
     */
    class FromTypeVarFun extends Mapping {

        boolean includeBounds;

        FromTypeVarFun(boolean includeBounds) {
            super("fromTypeVarFunWithBounds");
            this.includeBounds = includeBounds;
        }

        public Type apply(Type t) {
            if (t.hasTag(TYPEVAR)) return new UndetVar((TypeVar)t, types, includeBounds);
            else return t.map(this);
        }
    };

    /**
     * An inference context keeps track of the set of variables that are free
     * in the current context. It provides utility methods for opening/closing
     * types to their corresponding free/closed forms. It also provide hooks for
     * attaching deferred post-inference action (see PendingCheck). Finally,
     * it can be used as an entry point for performing upper/lower bound inference
     * (see InferenceKind).
     */
    static class InferenceContext {

        /**
        * Single-method-interface for defining inference callbacks. Certain actions
        * (i.e. subtyping checks) might need to be redone after all inference variables
        * have been fixed.
        */
        interface FreeTypeListener {
            void typesInferred(InferenceContext inferenceContext);
        }

        /** list of inference vars as undet vars */
        List<Type> undetvars;

        /** list of inference vars in this context */
        List<Type> inferencevars;

        java.util.Map<FreeTypeListener, List<Type>> freeTypeListeners =
                new java.util.HashMap<FreeTypeListener, List<Type>>();

        List<FreeTypeListener> freetypeListeners = List.nil();

        public InferenceContext(List<Type> inferencevars, Infer infer, boolean includeBounds) {
            this.undetvars = Type.map(inferencevars, infer.new FromTypeVarFun(includeBounds));
            this.inferencevars = inferencevars;
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
         * inference context (usually called after instantiate())
         */
        List<Type> restvars() {
            List<Type> undetvars = this.undetvars;
            ListBuffer<Type> restvars = ListBuffer.lb();
            for (Type t : instTypes()) {
                UndetVar uv = (UndetVar)undetvars.head;
                if (uv.qtype == t) {
                    restvars.append(t);
                }
                undetvars = undetvars.tail;
            }
            return restvars.toList();
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
        final Type asFree(Type t, Types types) {
            return types.subst(t, inferencevars, undetvars);
        }

        final List<Type> asFree(List<Type> ts, Types types) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : ts) {
                buf.append(asFree(t, types));
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
        Type asInstType(Type t, Types types) {
            return types.subst(t, inferencevars, instTypes());
        }

        List<Type> asInstTypes(List<Type> ts, Types types) {
            ListBuffer<Type> buf = ListBuffer.lb();
            for (Type t : ts) {
                buf.append(asInstType(t, types));
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
        void notifyChange(Types types) {
            InferenceException thrownEx = null;
            for (Map.Entry<FreeTypeListener, List<Type>> entry :
                    new HashMap<FreeTypeListener, List<Type>>(freeTypeListeners).entrySet()) {
                if (!Type.containsAny(entry.getValue(), restvars())) {
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

        void solveAny(List<Type> varsToSolve, Types types, Infer infer) {
            boolean progress = false;
            for (Type t : varsToSolve) {
                UndetVar uv = (UndetVar)asFree(t, types);
                if (uv.inst == null) {
                    infer.minimizeInst(uv, types.noWarnings);
                    if (uv.inst != null) {
                        progress = true;
                    }
                }
            }
            if (!progress) {
                throw infer.inferenceException.setMessage("cyclic.inference", varsToSolve);
            }
        }
    }

    final InferenceContext emptyContext = new InferenceContext(List.<Type>nil(), this, false);
}
