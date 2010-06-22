/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Type.ForAll.ConstraintKind;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.JCDiagnostic;

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
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        ambiguousNoInstanceException =
            new NoInstanceException(true, diags);
        unambiguousNoInstanceException =
            new NoInstanceException(false, diags);
        invalidInstanceException =
            new InvalidInstanceException(diags);

    }

    public static class InferenceException extends RuntimeException {
        private static final long serialVersionUID = 0;

        JCDiagnostic diagnostic;
        JCDiagnostic.Factory diags;

        InferenceException(JCDiagnostic.Factory diags) {
            this.diagnostic = null;
            this.diags = diags;
        }

        InferenceException setMessage(String key, Object... args) {
            this.diagnostic = diags.fragment(key, args);
            return this;
        }

        public JCDiagnostic getDiagnostic() {
             return diagnostic;
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
    Mapping fromTypeVarFun = new Mapping("fromTypeVarFun") {
            public Type apply(Type t) {
                if (t.tag == TYPEVAR) return new UndetVar(t);
                else return t.map(this);
            }
        };

    /** A mapping that returns its type argument with every UndetVar replaced
     *  by its `inst' field. Throws a NoInstanceException
     *  if this not possible because an `inst' field is null.
     */
    Mapping getInstFun = new Mapping("getInstFun") {
            public Type apply(Type t) {
                switch (t.tag) {
                case UNKNOWN:
                    throw ambiguousNoInstanceException
                        .setMessage("undetermined.type");
                case UNDETVAR:
                    UndetVar that = (UndetVar) t;
                    if (that.inst == null)
                        throw ambiguousNoInstanceException
                            .setMessage("type.variable.has.undetermined.type",
                                        that.qtype);
                    return apply(that.inst);
                default:
                    return t.map(this);
                }
            }
        };

/***************************************************************************
 * Mini/Maximization of UndetVars
 ***************************************************************************/

    /** Instantiate undetermined type variable to its minimal upper bound.
     *  Throw a NoInstanceException if this not possible.
     */
    void maximizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        if (that.inst == null) {
            if (that.hibounds.isEmpty())
                that.inst = syms.objectType;
            else if (that.hibounds.tail.isEmpty())
                that.inst = that.hibounds.head;
            else
                that.inst = types.glb(that.hibounds);
        }
        if (that.inst == null ||
            that.inst.isErroneous())
            throw ambiguousNoInstanceException
                .setMessage("no.unique.maximal.instance.exists",
                            that.qtype, that.hibounds);
    }
    //where
        private boolean isSubClass(Type t, final List<Type> ts) {
            t = t.baseType();
            if (t.tag == TYPEVAR) {
                List<Type> bounds = types.getBounds((TypeVar)t);
                for (Type s : ts) {
                    if (!types.isSameType(t, s.baseType())) {
                        for (Type bound : bounds) {
                            if (!isSubClass(bound, List.of(s.baseType())))
                                return false;
                        }
                    }
                }
            } else {
                for (Type s : ts) {
                    if (!t.tsym.isSubClass(s.baseType().tsym, types))
                        return false;
                }
            }
            return true;
        }

    /** Instantiate undetermined type variable to the lub of all its lower bounds.
     *  Throw a NoInstanceException if this not possible.
     */
    void minimizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        if (that.inst == null) {
            if (that.lobounds.isEmpty())
                that.inst = syms.botType;
            else if (that.lobounds.tail.isEmpty())
                that.inst = that.lobounds.head.isPrimitive() ? syms.errType : that.lobounds.head;
            else {
                that.inst = types.lub(that.lobounds);
            }
            if (that.inst == null || that.inst.tag == ERROR)
                    throw ambiguousNoInstanceException
                        .setMessage("no.unique.minimal.instance.exists",
                                    that.qtype, that.lobounds);
            // VGJ: sort of inlined maximizeInst() below.  Adding
            // bounds can cause lobounds that are above hibounds.
            if (that.hibounds.isEmpty())
                return;
            Type hb = null;
            if (that.hibounds.tail.isEmpty())
                hb = that.hibounds.head;
            else for (List<Type> bs = that.hibounds;
                      bs.nonEmpty() && hb == null;
                      bs = bs.tail) {
                if (isSubClass(bs.head, that.hibounds))
                    hb = types.fromUnknownFun.apply(bs.head);
            }
            if (hb == null ||
                !types.isSubtypeUnchecked(hb, that.hibounds, warn) ||
                !types.isSubtypeUnchecked(that.inst, hb, warn))
                throw ambiguousNoInstanceException;
        }
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
        List<Type> undetvars = Type.map(that.tvars, fromTypeVarFun);
        for (List<Type> l = undetvars; l.nonEmpty(); l = l.tail) {
            UndetVar uv = (UndetVar) l.head;
            TypeVar tv = (TypeVar)uv.qtype;
            ListBuffer<Type> hibounds = new ListBuffer<Type>();
            for (Type t : that.getConstraints(tv, ConstraintKind.EXTENDS).prependList(types.getBounds(tv))) {
                if (!t.containsSome(that.tvars) && t.tag != BOT) {
                    hibounds.append(t);
                }
            }
            List<Type> inst = that.getConstraints(tv, ConstraintKind.EQUAL);
            if (inst.nonEmpty() && inst.head.tag != BOT) {
                uv.inst = inst.head;
            }
            uv.hibounds = hibounds.toList();
        }
        Type qtype1 = types.subst(that.qtype, that.tvars, undetvars);
        if (!types.isSubtype(qtype1, to)) {
            throw unambiguousNoInstanceException
                .setMessage("no.conforming.instance.exists",
                            that.tvars, that.qtype, to);
        }
        for (List<Type> l = undetvars; l.nonEmpty(); l = l.tail)
            maximizeInst((UndetVar) l.head, warn);
        // System.out.println(" = " + qtype1.map(getInstFun));//DEBUG

        // check bounds
        List<Type> targs = Type.map(undetvars, getInstFun);
        targs = types.subst(targs, that.tvars, targs);
        checkWithinBounds(that.tvars, targs, warn);
        return chk.checkType(warn.pos(), that.inst(targs, types), to);
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
        List<Type> undetvars = Type.map(tvars, fromTypeVarFun);
        List<Type> formals = mt.argtypes;
        //need to capture exactly once - otherwise subsequent
        //applicability checks might fail
        final List<Type> capturedArgs = types.capture(argtypes);
        List<Type> actuals = capturedArgs;
        List<Type> actualsNoCapture = argtypes;
        // instantiate all polymorphic argument types and
        // set up lower bounds constraints for undetvars
        Type varargsFormal = useVarargs ? formals.last() : null;
        while (actuals.nonEmpty() && formals.head != varargsFormal) {
            Type formal = formals.head;
            Type actual = actuals.head.baseType();
            Type actualNoCapture = actualsNoCapture.head.baseType();
            if (actual.tag == FORALL)
                actual = instantiateArg((ForAll)actual, formal, tvars, warn);
            Type undetFormal = types.subst(formal, tvars, undetvars);
            boolean works = allowBoxing
                ? types.isConvertible(actual, undetFormal, warn)
                : types.isSubtypeUnchecked(actual, undetFormal, warn);
            if (!works) {
                throw unambiguousNoInstanceException
                    .setMessage("no.conforming.assignment.exists",
                                tvars, actualNoCapture, formal);
            }
            formals = formals.tail;
            actuals = actuals.tail;
            actualsNoCapture = actualsNoCapture.tail;
        }
        if (formals.head != varargsFormal || // not enough args
            !useVarargs && actuals.nonEmpty()) { // too many args
            // argument lists differ in length
            throw unambiguousNoInstanceException
                .setMessage("arg.length.mismatch");
        }

        // for varargs arguments as well
        if (useVarargs) {
            Type elemType = types.elemtype(varargsFormal);
            Type elemUndet = types.subst(elemType, tvars, undetvars);
            while (actuals.nonEmpty()) {
                Type actual = actuals.head.baseType();
                Type actualNoCapture = actualsNoCapture.head.baseType();
                if (actual.tag == FORALL)
                    actual = instantiateArg((ForAll)actual, elemType, tvars, warn);
                boolean works = types.isConvertible(actual, elemUndet, warn);
                if (!works) {
                    throw unambiguousNoInstanceException
                        .setMessage("no.conforming.assignment.exists",
                                    tvars, actualNoCapture, elemType);
                }
                actuals = actuals.tail;
                actualsNoCapture = actualsNoCapture.tail;
            }
        }

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
        checkWithinBounds(tvars, undettypes.toList(), warn);

        mt = (MethodType)types.subst(mt, tvars, insttypes.toList());

        if (!restvars.isEmpty()) {
            // if there are uninstantiated variables,
            // quantify result type with them
            final List<Type> inferredTypes = insttypes.toList();
            final List<Type> all_tvars = tvars; //this is the wrong tvars
            final MethodType mt2 = new MethodType(mt.argtypes, null, mt.thrown, syms.methodClass);
            mt2.restype = new ForAll(restvars.toList(), mt.restype) {
                @Override
                public List<Type> getConstraints(TypeVar tv, ConstraintKind ck) {
                    for (Type t : restundet.toList()) {
                        UndetVar uv = (UndetVar)t;
                        if (uv.qtype == tv) {
                            switch (ck) {
                                case EXTENDS: return uv.hibounds;
                                case SUPER: return uv.lobounds;
                                case EQUAL: return uv.inst != null ? List.of(uv.inst) : List.<Type>nil();
                            }
                        }
                    }
                    return List.nil();
                }

                @Override
                public Type inst(List<Type> inferred, Types types) throws NoInstanceException {
                    List<Type> formals = types.subst(mt2.argtypes, tvars, inferred);
                    if (!rs.argumentsAcceptable(capturedArgs, formals,
                           allowBoxing, useVarargs, warn)) {
                      // inferred method is not applicable
                      throw invalidInstanceException.setMessage("inferred.do.not.conform.to.params", formals, argtypes);
                    }
                    // check that inferred bounds conform to their bounds
                    checkWithinBounds(all_tvars,
                           types.subst(inferredTypes, tvars, inferred), warn);
                    if (useVarargs) {
                        chk.checkVararg(env.tree.pos(), formals, msym, env);
                    }
                    return super.inst(inferred, types);
            }};
            return mt2;
        }
        else if (!rs.argumentsAcceptable(capturedArgs, mt.getParameterTypes(), allowBoxing, useVarargs, warn)) {
            // inferred method is not applicable
            throw invalidInstanceException.setMessage("inferred.do.not.conform.to.params", mt.getParameterTypes(), argtypes);
        }
        else {
            // return instantiated version of method type
            return mt;
        }
    }
    //where

        /** Try to instantiate argument type `that' to given type `to'.
         *  If this fails, try to insantiate `that' to `to' where
         *  every occurrence of a type variable in `tvars' is replaced
         *  by an unknown type.
         */
        private Type instantiateArg(ForAll that,
                                    Type to,
                                    List<Type> tvars,
                                    Warner warn) throws InferenceException {
            List<Type> targs;
            try {
                return instantiateExpr(that, to, warn);
            } catch (NoInstanceException ex) {
                Type to1 = to;
                for (List<Type> l = tvars; l.nonEmpty(); l = l.tail)
                    to1 = types.subst(to1, List.of(l.head), List.of(syms.unknownType));
                return instantiateExpr(that, to1, warn);
            }
        }

    /** check that type parameters are within their bounds.
     */
    private void checkWithinBounds(List<Type> tvars,
                                   List<Type> arguments,
                                   Warner warn)
        throws InvalidInstanceException {
        for (List<Type> tvs = tvars, args = arguments;
             tvs.nonEmpty();
             tvs = tvs.tail, args = args.tail) {
            if (args.head instanceof UndetVar) continue;
            List<Type> bounds = types.subst(types.getBounds((TypeVar)tvs.head), tvars, arguments);
            if (!types.isSubtypeUnchecked(args.head, bounds, warn))
                throw invalidInstanceException
                    .setMessage("inferred.do.not.conform.to.bounds",
                                args.head, bounds);
        }
    }
}
