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

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.Resolve.InapplicableMethodException;
import com.sun.tools.javac.comp.Resolve.VerboseResolutionMode;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import static com.sun.tools.javac.code.TypeTags.*;

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
    Log log;
    JCDiagnostic.Factory diags;

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
        log = Log.instance(context);
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        ambiguousNoInstanceException =
            new NoInstanceException(true, diags);
        unambiguousNoInstanceException =
            new NoInstanceException(false, diags);
        invalidInstanceException =
            new InvalidInstanceException(diags);

    }

    public static class InferenceException extends InapplicableMethodException {
        private static final long serialVersionUID = 0;

        InferenceException(JCDiagnostic.Factory diags) {
            super(diags);
        }
    }

    public static class NoInstanceException extends InferenceException {
        private static final long serialVersionUID = 1;

        boolean isAmbiguous; // exist several incomparable best instances?

        NoInstanceException(boolean isAmbiguous, JCDiagnostic.Factory diags) {
            super(diags);
            this.isAmbiguous = isAmbiguous;
        }
    }

    public static class InvalidInstanceException extends InferenceException {
        private static final long serialVersionUID = 2;

        InvalidInstanceException(JCDiagnostic.Factory diags) {
            super(diags);
        }
    }

    private final NoInstanceException ambiguousNoInstanceException;
    private final NoInstanceException unambiguousNoInstanceException;
    private final InvalidInstanceException invalidInstanceException;

/***************************************************************************
 * Auxiliary type values and classes
 ***************************************************************************/

    /** A mapping that turns type variables into undetermined type variables.
     */
    List<Type> makeUndetvars(List<Type> tvars) {
        List<Type> undetvars = Type.map(tvars, fromTypeVarFun);
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            uv.hibounds = types.getBounds((TypeVar)uv.qtype);
        }
        return undetvars;
    }
    //where
            Mapping fromTypeVarFun = new Mapping("fromTypeVarFun") {
                public Type apply(Type t) {
                    if (t.tag == TYPEVAR) return new UndetVar(t);
                    else return t.map(this);
                }
            };

/***************************************************************************
 * Mini/Maximization of UndetVars
 ***************************************************************************/

    /** Instantiate undetermined type variable to its minimal upper bound.
     *  Throw a NoInstanceException if this not possible.
     */
    void maximizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        List<Type> hibounds = Type.filter(that.hibounds, errorFilter);
        if (that.eq.isEmpty()) {
            if (hibounds.isEmpty())
                that.inst = syms.objectType;
            else if (hibounds.tail.isEmpty())
                that.inst = hibounds.head;
            else
                that.inst = types.glb(hibounds);
        } else {
            that.inst = that.eq.head;
        }
        if (that.inst == null ||
            that.inst.isErroneous())
            throw ambiguousNoInstanceException
                .setMessage("no.unique.maximal.instance.exists",
                            that.qtype, hibounds);
    }

    private Filter<Type> errorFilter = new Filter<Type>() {
        @Override
        public boolean accepts(Type t) {
            return !t.isErroneous();
        }
    };

    /** Instantiate undetermined type variable to the lub of all its lower bounds.
     *  Throw a NoInstanceException if this not possible.
     */
    void minimizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        List<Type> lobounds = Type.filter(that.lobounds, errorFilter);
        if (that.eq.isEmpty()) {
            if (lobounds.isEmpty())
                that.inst = syms.botType;
            else if (lobounds.tail.isEmpty())
                that.inst = lobounds.head.isPrimitive() ? syms.errType : lobounds.head;
            else {
                that.inst = types.lub(lobounds);
            }
            if (that.inst == null || that.inst.tag == ERROR)
                    throw ambiguousNoInstanceException
                        .setMessage("no.unique.minimal.instance.exists",
                                    that.qtype, lobounds);
        } else {
            that.inst = that.eq.head;
        }
    }

    Type asUndetType(Type t, List<Type> undetvars) {
        return types.subst(t, inferenceVars(undetvars), undetvars);
    }

    List<Type> inferenceVars(List<Type> undetvars) {
        ListBuffer<Type> tvars = ListBuffer.lb();
        for (Type uv : undetvars) {
            tvars.append(((UndetVar)uv).qtype);
        }
        return tvars.toList();
    }

/***************************************************************************
 * Exported Methods
 ***************************************************************************/

    /** Try to instantiate expression type `that' to given type `to'.
     *  If a maximal instantiation exists which makes this type
     *  a subtype of type `to', return the instantiated type.
     *  If no instantiation exists, or if several incomparable
     *  best instantiations exist throw a NoInstanceException.
     */
    public Type instantiateExpr(ForAll that,
                                Type to,
                                Warner warn) throws InferenceException {
        List<Type> undetvars = that.undetvars();
        Type qtype1 = types.subst(that.qtype, that.tvars, undetvars);
        if (!types.isSubtype(qtype1,
                qtype1.tag == UNDETVAR ? types.boxedTypeOrType(to) : to)) {
            throw unambiguousNoInstanceException
                .setMessage("infer.no.conforming.instance.exists",
                            that.tvars, that.qtype, to);
        }

        List<Type> insttypes;
        while (true) {
            boolean stuck = true;
            insttypes = List.nil();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar)t;
                if (uv.inst == null && (uv.eq.nonEmpty() || !Type.containsAny(uv.hibounds, that.tvars))) {
                    maximizeInst((UndetVar)t, warn);
                    stuck = false;
                }
                insttypes = insttypes.append(uv.inst == null ? uv.qtype : uv.inst);
            }
            if (!Type.containsAny(insttypes, that.tvars)) {
                //all variables have been instantiated - exit
                break;
            } else if (stuck) {
                //some variables could not be instantiated because of cycles in
                //upper bounds - provide a (possibly recursive) default instantiation
                insttypes = types.subst(insttypes,
                    that.tvars,
                    instantiateAsUninferredVars(undetvars, that.tvars));
                break;
            } else {
                //some variables have been instantiated - replace newly instantiated
                //variables in remaining upper bounds and continue
                for (Type t : undetvars) {
                    UndetVar uv = (UndetVar)t;
                    uv.hibounds = types.subst(uv.hibounds, that.tvars, insttypes);
                }
            }
        }
        return that.inst(insttypes, types);
    }

    /**
     * Infer cyclic inference variables as described in 15.12.2.8.
     */
    private List<Type> instantiateAsUninferredVars(List<Type> undetvars, List<Type> tvars) {
        Assert.check(undetvars.length() == tvars.length());
        ListBuffer<Type> insttypes = ListBuffer.lb();
        ListBuffer<Type> todo = ListBuffer.lb();
        //step 1 - create fresh tvars
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            if (uv.inst == null) {
                TypeSymbol fresh_tvar = new TypeSymbol(Flags.SYNTHETIC, uv.qtype.tsym.name, null, uv.qtype.tsym.owner);
                fresh_tvar.type = new TypeVar(fresh_tvar, types.makeCompoundType(uv.hibounds), null);
                todo.append(uv);
                uv.inst = fresh_tvar.type;
            }
            insttypes.append(uv.inst);
        }
        //step 2 - replace fresh tvars in their bounds
        List<Type> formals = tvars;
        for (Type t : todo) {
            UndetVar uv = (UndetVar)t;
            TypeVar ct = (TypeVar)uv.inst;
            ct.bound = types.glb(types.subst(types.getBounds(ct), tvars, insttypes.toList()));
            if (ct.bound.isErroneous()) {
                //report inference error if glb fails
                reportBoundError(uv, BoundErrorKind.BAD_UPPER);
            }
            formals = formals.tail;
        }
        return insttypes.toList();
    }

    /** Instantiate method type `mt' by finding instantiations of
     *  `tvars' so that method can be applied to `argtypes'.
     */
    public Type instantiateMethod(final Env<AttrContext> env,
                                  List<Type> tvars,
                                  MethodType mt,
                                  final Symbol msym,
                                  final List<Type> argtypes,
                                  final boolean allowBoxing,
                                  final boolean useVarargs,
                                  final Warner warn) throws InferenceException {
        //-System.err.println("instantiateMethod(" + tvars + ", " + mt + ", " + argtypes + ")"); //DEBUG
        final List<Type> undetvars =  makeUndetvars(tvars);

        final List<Type> capturedArgs =
                rs.checkRawArgumentsAcceptable(env, undetvars, argtypes, mt.getParameterTypes(),
                    allowBoxing, useVarargs, warn, new InferenceCheckHandler(undetvars));

        // minimize as yet undetermined type variables
        for (Type t : undetvars)
            minimizeInst((UndetVar) t, warn);

        /** Type variables instantiated to bottom */
        ListBuffer<Type> restvars = new ListBuffer<Type>();

        /** Undet vars instantiated to bottom */
        final ListBuffer<Type> restundet = new ListBuffer<Type>();

        /** Instantiated types or TypeVars if under-constrained */
        ListBuffer<Type> insttypes = new ListBuffer<Type>();

        /** Instantiated types or UndetVars if under-constrained */
        ListBuffer<Type> undettypes = new ListBuffer<Type>();

        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            if (uv.inst.tag == BOT) {
                restvars.append(uv.qtype);
                restundet.append(uv);
                insttypes.append(uv.qtype);
                undettypes.append(uv);
                uv.inst = null;
            } else {
                insttypes.append(uv.inst);
                undettypes.append(uv.inst);
            }
        }
        checkWithinBounds(tvars, undetvars, insttypes.toList(), warn);

        mt = (MethodType)types.subst(mt, tvars, insttypes.toList());

        if (!restvars.isEmpty()) {
            // if there are uninstantiated variables,
            // quantify result type with them
            final List<Type> inferredTypes = insttypes.toList();
            final List<Type> all_tvars = tvars; //this is the wrong tvars
            return new UninferredMethodType(env.tree.pos(), msym, mt, restvars.toList()) {
                @Override
                List<Type> undetvars() {
                    return restundet.toList();
                }
                @Override
                void instantiateReturnType(Type restype, List<Type> inferred, Types types) throws NoInstanceException {
                    Type owntype = new MethodType(types.subst(getParameterTypes(), tvars, inferred),
                                       restype,
                                       types.subst(getThrownTypes(), tvars, inferred),
                                       qtype.tsym);
                    // check that actuals conform to inferred formals
                    warn.clear();
                    checkArgumentsAcceptable(env, capturedArgs, owntype.getParameterTypes(), allowBoxing, useVarargs, warn);
                    // check that inferred bounds conform to their bounds
                    checkWithinBounds(all_tvars, undetvars,
                           types.subst(inferredTypes, tvars, inferred), warn);
                    qtype = chk.checkMethod(owntype, msym, env, TreeInfo.args(env.tree), capturedArgs, useVarargs, warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED));
                }
            };
        }
        else {
            // check that actuals conform to inferred formals
            checkArgumentsAcceptable(env, capturedArgs, mt.getParameterTypes(), allowBoxing, useVarargs, warn);
            // return instantiated version of method type
            return mt;
        }
    }
    //where

        /** inference check handler **/
        class InferenceCheckHandler implements Resolve.MethodCheckHandler {

            List<Type> undetvars;

            public InferenceCheckHandler(List<Type> undetvars) {
                this.undetvars = undetvars;
            }

            public InapplicableMethodException arityMismatch() {
                return unambiguousNoInstanceException.setMessage("infer.arg.length.mismatch");
            }
            public InapplicableMethodException argumentMismatch(boolean varargs, Type found, Type expected) {
                String key = varargs ?
                    "infer.varargs.argument.mismatch" :
                    "infer.no.conforming.assignment.exists";
                return unambiguousNoInstanceException.setMessage(key,
                        inferenceVars(undetvars), found, expected);
            }
            public InapplicableMethodException inaccessibleVarargs(Symbol location, Type expected) {
                return unambiguousNoInstanceException.setMessage("inaccessible.varargs.type",
                        expected, Kinds.kindName(location), location);
            }
        }

        /**
         * A delegated type representing a partially uninferred method type.
         * The return type of a partially uninferred method type is a ForAll
         * type - when the return type is instantiated (see Infer.instantiateExpr)
         * the underlying method type is also updated.
         */
        abstract class UninferredMethodType extends DelegatedType {

            final List<Type> tvars;
            final Symbol msym;
            final DiagnosticPosition pos;

            public UninferredMethodType(DiagnosticPosition pos, Symbol msym, MethodType mtype, List<Type> tvars) {
                super(METHOD, new MethodType(mtype.argtypes, null, mtype.thrown, mtype.tsym));
                this.tvars = tvars;
                this.msym = msym;
                this.pos = pos;
                asMethodType().restype = new UninferredReturnType(tvars, mtype.restype);
            }

            @Override
            public MethodType asMethodType() {
                return qtype.asMethodType();
            }

            @Override
            public Type map(Mapping f) {
                return qtype.map(f);
            }

            abstract void instantiateReturnType(Type restype, List<Type> inferred, Types types);

            abstract List<Type> undetvars();

            class UninferredReturnType extends ForAll {
                public UninferredReturnType(List<Type> tvars, Type restype) {
                    super(tvars, restype);
                }
                @Override
                public Type inst(List<Type> actuals, Types types) {
                    Type newRestype = super.inst(actuals, types);
                    instantiateReturnType(newRestype, actuals, types);
                    if (rs.verboseResolutionMode.contains(VerboseResolutionMode.DEFERRED_INST)) {
                        log.note(pos, "deferred.method.inst", msym, UninferredMethodType.this.qtype, newRestype);
                    }
                    return UninferredMethodType.this.qtype.getReturnType();
                }
                @Override
                public List<Type> undetvars() {
                    return UninferredMethodType.this.undetvars();
                }
            }
        }

        private void checkArgumentsAcceptable(Env<AttrContext> env, List<Type> actuals, List<Type> formals,
                boolean allowBoxing, boolean useVarargs, Warner warn) {
            try {
                rs.checkRawArgumentsAcceptable(env, actuals, formals,
                       allowBoxing, useVarargs, warn);
            }
            catch (InapplicableMethodException ex) {
                // inferred method is not applicable
                throw invalidInstanceException.setMessage(ex.getDiagnostic());
            }
        }

    /** check that type parameters are within their bounds.
     */
    void checkWithinBounds(List<Type> tvars,
                           List<Type> undetvars,
                           List<Type> arguments,
                           Warner warn)
        throws InvalidInstanceException {
        List<Type> args = arguments;
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            uv.hibounds = types.subst(uv.hibounds, tvars, arguments);
            uv.lobounds = types.subst(uv.lobounds, tvars, arguments);
            uv.eq = types.subst(uv.eq, tvars, arguments);
            checkCompatibleUpperBounds(uv, tvars);
            if (args.head.tag != TYPEVAR || !args.head.containsAny(tvars)) {
                Type inst = args.head;
                for (Type u : uv.hibounds) {
                    if (!types.isSubtypeUnchecked(inst, types.subst(u, tvars, undetvars), warn)) {
                        reportBoundError(uv, BoundErrorKind.UPPER);
                    }
                }
                for (Type l : uv.lobounds) {
                    if (!types.isSubtypeUnchecked(types.subst(l, tvars, undetvars), inst, warn)) {
                        reportBoundError(uv, BoundErrorKind.LOWER);
                    }
                }
                for (Type e : uv.eq) {
                    if (!types.isSameType(inst, types.subst(e, tvars, undetvars))) {
                        reportBoundError(uv, BoundErrorKind.EQ);
                    }
                }
            }
            args = args.tail;
        }
    }

    void checkCompatibleUpperBounds(UndetVar uv, List<Type> tvars) {
        // VGJ: sort of inlined maximizeInst() below.  Adding
        // bounds can cause lobounds that are above hibounds.
        ListBuffer<Type> hiboundsNoVars = ListBuffer.lb();
        for (Type t : Type.filter(uv.hibounds, errorFilter)) {
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
                return ex.setMessage("incompatible.upper.bounds", uv.qtype, uv.hibounds);
            }
        },
        UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.upper.bounds", uv.inst, uv.hibounds);
            }
        },
        LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.lower.bounds", uv.inst, uv.lobounds);
            }
        },
        EQ() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.eq.bounds", uv.inst, uv.eq);
            }
        };

        abstract InapplicableMethodException setMessage(InferenceException ex, UndetVar uv);
    }
    //where
    void reportBoundError(UndetVar uv, BoundErrorKind bk) {
        throw bk.setMessage(uv.inst == null ? ambiguousNoInstanceException : invalidInstanceException, uv);
    }

    /**
     * Compute a synthetic method type corresponding to the requested polymorphic
     * method signature. The target return type is computed from the immediately
     * enclosing scope surrounding the polymorphic-signature call.
     */
    Type instantiatePolymorphicSignatureInstance(Env<AttrContext> env,
                                            MethodSymbol spMethod,  // sig. poly. method or null if none
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

        List<Type> paramtypes = Type.map(argtypes, implicitArgType);
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
        Mapping implicitArgType = new Mapping ("implicitArgType") {
                public Type apply(Type t) {
                    t = types.erasure(t);
                    if (t.tag == BOT)
                        // nulls type as the marker type Null (which has no instances)
                        // infer as java.lang.Void for now
                        t = types.boxedClass(syms.voidType).type;
                    return t;
                }
        };
    }
