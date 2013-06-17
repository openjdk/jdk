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

import com.sun.tools.javac.api.Formattable.LocalizedString;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Attr.ResultInfo;
import com.sun.tools.javac.comp.Check.CheckContext;
import com.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.sun.tools.javac.comp.DeferredAttr.DeferredAttrContext;
import com.sun.tools.javac.comp.DeferredAttr.DeferredType;
import com.sun.tools.javac.comp.Infer.InferenceContext;
import com.sun.tools.javac.comp.Infer.FreeTypeListener;
import com.sun.tools.javac.comp.Resolve.MethodResolutionContext.Candidate;
import com.sun.tools.javac.comp.Resolve.MethodResolutionDiagHelper.DiagnosticRewriter;
import com.sun.tools.javac.comp.Resolve.MethodResolutionDiagHelper.Template;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.JCTree.JCPolyExpression.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import javax.lang.model.element.ElementVisitor;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.BLOCK;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.ERRONEOUS;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.comp.Resolve.MethodResolutionPhase.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/** Helper class for name resolution, used mostly by the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Resolve {
    protected static final Context.Key<Resolve> resolveKey =
        new Context.Key<Resolve>();

    Names names;
    Log log;
    Symtab syms;
    Attr attr;
    DeferredAttr deferredAttr;
    Check chk;
    Infer infer;
    ClassReader reader;
    TreeInfo treeinfo;
    Types types;
    JCDiagnostic.Factory diags;
    public final boolean boxingEnabled; // = source.allowBoxing();
    public final boolean varargsEnabled; // = source.allowVarargs();
    public final boolean allowMethodHandles;
    public final boolean allowDefaultMethods;
    public final boolean allowStructuralMostSpecific;
    private final boolean debugResolve;
    private final boolean compactMethodDiags;
    final EnumSet<VerboseResolutionMode> verboseResolutionMode;

    Scope polymorphicSignatureScope;

    protected Resolve(Context context) {
        context.put(resolveKey, this);
        syms = Symtab.instance(context);

        varNotFound = new
            SymbolNotFoundError(ABSENT_VAR);
        methodNotFound = new
            SymbolNotFoundError(ABSENT_MTH);
        typeNotFound = new
            SymbolNotFoundError(ABSENT_TYP);

        names = Names.instance(context);
        log = Log.instance(context);
        attr = Attr.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        chk = Check.instance(context);
        infer = Infer.instance(context);
        reader = ClassReader.instance(context);
        treeinfo = TreeInfo.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Source source = Source.instance(context);
        boxingEnabled = source.allowBoxing();
        varargsEnabled = source.allowVarargs();
        Options options = Options.instance(context);
        debugResolve = options.isSet("debugresolve");
        compactMethodDiags = options.isSet(Option.XDIAGS, "compact") ||
                options.isUnset(Option.XDIAGS) && options.isUnset("rawDiagnostics");
        verboseResolutionMode = VerboseResolutionMode.getVerboseResolutionMode(options);
        Target target = Target.instance(context);
        allowMethodHandles = target.hasMethodHandles();
        allowDefaultMethods = source.allowDefaultMethods();
        allowStructuralMostSpecific = source.allowStructuralMostSpecific();
        polymorphicSignatureScope = new Scope(syms.noSymbol);

        inapplicableMethodException = new InapplicableMethodException(diags);
    }

    /** error symbols, which are returned when resolution fails
     */
    private final SymbolNotFoundError varNotFound;
    private final SymbolNotFoundError methodNotFound;
    private final SymbolNotFoundError typeNotFound;

    public static Resolve instance(Context context) {
        Resolve instance = context.get(resolveKey);
        if (instance == null)
            instance = new Resolve(context);
        return instance;
    }

    // <editor-fold defaultstate="collapsed" desc="Verbose resolution diagnostics support">
    enum VerboseResolutionMode {
        SUCCESS("success"),
        FAILURE("failure"),
        APPLICABLE("applicable"),
        INAPPLICABLE("inapplicable"),
        DEFERRED_INST("deferred-inference"),
        PREDEF("predef"),
        OBJECT_INIT("object-init"),
        INTERNAL("internal");

        final String opt;

        private VerboseResolutionMode(String opt) {
            this.opt = opt;
        }

        static EnumSet<VerboseResolutionMode> getVerboseResolutionMode(Options opts) {
            String s = opts.get("verboseResolution");
            EnumSet<VerboseResolutionMode> res = EnumSet.noneOf(VerboseResolutionMode.class);
            if (s == null) return res;
            if (s.contains("all")) {
                res = EnumSet.allOf(VerboseResolutionMode.class);
            }
            Collection<String> args = Arrays.asList(s.split(","));
            for (VerboseResolutionMode mode : values()) {
                if (args.contains(mode.opt)) {
                    res.add(mode);
                } else if (args.contains("-" + mode.opt)) {
                    res.remove(mode);
                }
            }
            return res;
        }
    }

    void reportVerboseResolutionDiagnostic(DiagnosticPosition dpos, Name name, Type site,
            List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar) {
        boolean success = bestSoFar.kind < ERRONEOUS;

        if (success && !verboseResolutionMode.contains(VerboseResolutionMode.SUCCESS)) {
            return;
        } else if (!success && !verboseResolutionMode.contains(VerboseResolutionMode.FAILURE)) {
            return;
        }

        if (bestSoFar.name == names.init &&
                bestSoFar.owner == syms.objectType.tsym &&
                !verboseResolutionMode.contains(VerboseResolutionMode.OBJECT_INIT)) {
            return; //skip diags for Object constructor resolution
        } else if (site == syms.predefClass.type &&
                !verboseResolutionMode.contains(VerboseResolutionMode.PREDEF)) {
            return; //skip spurious diags for predef symbols (i.e. operators)
        } else if (currentResolutionContext.internalResolution &&
                !verboseResolutionMode.contains(VerboseResolutionMode.INTERNAL)) {
            return;
        }

        int pos = 0;
        int mostSpecificPos = -1;
        ListBuffer<JCDiagnostic> subDiags = ListBuffer.lb();
        for (Candidate c : currentResolutionContext.candidates) {
            if (currentResolutionContext.step != c.step ||
                    (c.isApplicable() && !verboseResolutionMode.contains(VerboseResolutionMode.APPLICABLE)) ||
                    (!c.isApplicable() && !verboseResolutionMode.contains(VerboseResolutionMode.INAPPLICABLE))) {
                continue;
            } else {
                subDiags.append(c.isApplicable() ?
                        getVerboseApplicableCandidateDiag(pos, c.sym, c.mtype) :
                        getVerboseInapplicableCandidateDiag(pos, c.sym, c.details));
                if (c.sym == bestSoFar)
                    mostSpecificPos = pos;
                pos++;
            }
        }
        String key = success ? "verbose.resolve.multi" : "verbose.resolve.multi.1";
        List<Type> argtypes2 = Type.map(argtypes,
                    deferredAttr.new RecoveryDeferredTypeMap(AttrMode.SPECULATIVE, bestSoFar, currentResolutionContext.step));
        JCDiagnostic main = diags.note(log.currentSource(), dpos, key, name,
                site.tsym, mostSpecificPos, currentResolutionContext.step,
                methodArguments(argtypes2),
                methodArguments(typeargtypes));
        JCDiagnostic d = new JCDiagnostic.MultilineDiagnostic(main, subDiags.toList());
        log.report(d);
    }

    JCDiagnostic getVerboseApplicableCandidateDiag(int pos, Symbol sym, Type inst) {
        JCDiagnostic subDiag = null;
        if (sym.type.hasTag(FORALL)) {
            subDiag = diags.fragment("partial.inst.sig", inst);
        }

        String key = subDiag == null ?
                "applicable.method.found" :
                "applicable.method.found.1";

        return diags.fragment(key, pos, sym, subDiag);
    }

    JCDiagnostic getVerboseInapplicableCandidateDiag(int pos, Symbol sym, JCDiagnostic subDiag) {
        return diags.fragment("not.applicable.method.found", pos, sym, subDiag);
    }
    // </editor-fold>

/* ************************************************************************
 * Identifier resolution
 *************************************************************************/

    /** An environment is "static" if its static level is greater than
     *  the one of its outer environment
     */
    protected static boolean isStatic(Env<AttrContext> env) {
        return env.info.staticLevel > env.outer.info.staticLevel;
    }

    /** An environment is an "initializer" if it is a constructor or
     *  an instance initializer.
     */
    static boolean isInitializer(Env<AttrContext> env) {
        Symbol owner = env.info.scope.owner;
        return owner.isConstructor() ||
            owner.owner.kind == TYP &&
            (owner.kind == VAR ||
             owner.kind == MTH && (owner.flags() & BLOCK) != 0) &&
            (owner.flags() & STATIC) == 0;
    }

    /** Is class accessible in given evironment?
     *  @param env    The current environment.
     *  @param c      The class whose accessibility is checked.
     */
    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c) {
        return isAccessible(env, c, false);
    }

    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c, boolean checkInner) {
        boolean isAccessible = false;
        switch ((short)(c.flags() & AccessFlags)) {
            case PRIVATE:
                isAccessible =
                    env.enclClass.sym.outermostClass() ==
                    c.owner.outermostClass();
                break;
            case 0:
                isAccessible =
                    env.toplevel.packge == c.owner // fast special case
                    ||
                    env.toplevel.packge == c.packge()
                    ||
                    // Hack: this case is added since synthesized default constructors
                    // of anonymous classes should be allowed to access
                    // classes which would be inaccessible otherwise.
                    env.enclMethod != null &&
                    (env.enclMethod.mods.flags & ANONCONSTR) != 0;
                break;
            default: // error recovery
            case PUBLIC:
                isAccessible = true;
                break;
            case PROTECTED:
                isAccessible =
                    env.toplevel.packge == c.owner // fast special case
                    ||
                    env.toplevel.packge == c.packge()
                    ||
                    isInnerSubClass(env.enclClass.sym, c.owner);
                break;
        }
        return (checkInner == false || c.type.getEnclosingType() == Type.noType) ?
            isAccessible :
            isAccessible && isAccessible(env, c.type.getEnclosingType(), checkInner);
    }
    //where
        /** Is given class a subclass of given base class, or an inner class
         *  of a subclass?
         *  Return null if no such class exists.
         *  @param c     The class which is the subclass or is contained in it.
         *  @param base  The base class
         */
        private boolean isInnerSubClass(ClassSymbol c, Symbol base) {
            while (c != null && !c.isSubClass(base, types)) {
                c = c.owner.enclClass();
            }
            return c != null;
        }

    boolean isAccessible(Env<AttrContext> env, Type t) {
        return isAccessible(env, t, false);
    }

    boolean isAccessible(Env<AttrContext> env, Type t, boolean checkInner) {
        return (t.hasTag(ARRAY))
            ? isAccessible(env, types.elemtype(t))
            : isAccessible(env, t.tsym, checkInner);
    }

    /** Is symbol accessible as a member of given type in given environment?
     *  @param env    The current environment.
     *  @param site   The type of which the tested symbol is regarded
     *                as a member.
     *  @param sym    The symbol.
     */
    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym) {
        return isAccessible(env, site, sym, false);
    }
    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym, boolean checkInner) {
        if (sym.name == names.init && sym.owner != site.tsym) return false;
        switch ((short)(sym.flags() & AccessFlags)) {
        case PRIVATE:
            return
                (env.enclClass.sym == sym.owner // fast special case
                 ||
                 env.enclClass.sym.outermostClass() ==
                 sym.owner.outermostClass())
                &&
                sym.isInheritedIn(site.tsym, types);
        case 0:
            return
                (env.toplevel.packge == sym.owner.owner // fast special case
                 ||
                 env.toplevel.packge == sym.packge())
                &&
                isAccessible(env, site, checkInner)
                &&
                sym.isInheritedIn(site.tsym, types)
                &&
                notOverriddenIn(site, sym);
        case PROTECTED:
            return
                (env.toplevel.packge == sym.owner.owner // fast special case
                 ||
                 env.toplevel.packge == sym.packge()
                 ||
                 isProtectedAccessible(sym, env.enclClass.sym, site)
                 ||
                 // OK to select instance method or field from 'super' or type name
                 // (but type names should be disallowed elsewhere!)
                 env.info.selectSuper && (sym.flags() & STATIC) == 0 && sym.kind != TYP)
                &&
                isAccessible(env, site, checkInner)
                &&
                notOverriddenIn(site, sym);
        default: // this case includes erroneous combinations as well
            return isAccessible(env, site, checkInner) && notOverriddenIn(site, sym);
        }
    }
    //where
    /* `sym' is accessible only if not overridden by
     * another symbol which is a member of `site'
     * (because, if it is overridden, `sym' is not strictly
     * speaking a member of `site'). A polymorphic signature method
     * cannot be overridden (e.g. MH.invokeExact(Object[])).
     */
    private boolean notOverriddenIn(Type site, Symbol sym) {
        if (sym.kind != MTH || sym.isConstructor() || sym.isStatic())
            return true;
        else {
            Symbol s2 = ((MethodSymbol)sym).implementation(site.tsym, types, true);
            return (s2 == null || s2 == sym || sym.owner == s2.owner ||
                    !types.isSubSignature(types.memberType(site, s2), types.memberType(site, sym)));
        }
    }
    //where
        /** Is given protected symbol accessible if it is selected from given site
         *  and the selection takes place in given class?
         *  @param sym     The symbol with protected access
         *  @param c       The class where the access takes place
         *  @site          The type of the qualifier
         */
        private
        boolean isProtectedAccessible(Symbol sym, ClassSymbol c, Type site) {
            while (c != null &&
                   !(c.isSubClass(sym.owner, types) &&
                     (c.flags() & INTERFACE) == 0 &&
                     // In JLS 2e 6.6.2.1, the subclass restriction applies
                     // only to instance fields and methods -- types are excluded
                     // regardless of whether they are declared 'static' or not.
                     ((sym.flags() & STATIC) != 0 || sym.kind == TYP || site.tsym.isSubClass(c, types))))
                c = c.owner.enclClass();
            return c != null;
        }

    /**
     * Performs a recursive scan of a type looking for accessibility problems
     * from current attribution environment
     */
    void checkAccessibleType(Env<AttrContext> env, Type t) {
        accessibilityChecker.visit(t, env);
    }

    /**
     * Accessibility type-visitor
     */
    Types.SimpleVisitor<Void, Env<AttrContext>> accessibilityChecker =
            new Types.SimpleVisitor<Void, Env<AttrContext>>() {

        void visit(List<Type> ts, Env<AttrContext> env) {
            for (Type t : ts) {
                visit(t, env);
            }
        }

        public Void visitType(Type t, Env<AttrContext> env) {
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType t, Env<AttrContext> env) {
            visit(t.elemtype, env);
            return null;
        }

        @Override
        public Void visitClassType(ClassType t, Env<AttrContext> env) {
            visit(t.getTypeArguments(), env);
            if (!isAccessible(env, t, true)) {
                accessBase(new AccessError(t.tsym), env.tree.pos(), env.enclClass.sym, t, t.tsym.name, true);
            }
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType t, Env<AttrContext> env) {
            visit(t.type, env);
            return null;
        }

        @Override
        public Void visitMethodType(MethodType t, Env<AttrContext> env) {
            visit(t.getParameterTypes(), env);
            visit(t.getReturnType(), env);
            visit(t.getThrownTypes(), env);
            return null;
        }
    };

    /** Try to instantiate the type of a method so that it fits
     *  given type arguments and argument types. If successful, return
     *  the method's instantiated type, else return null.
     *  The instantiation will take into account an additional leading
     *  formal parameter if the method is an instance method seen as a member
     *  of an under determined site. In this case, we treat site as an additional
     *  parameter and the parameters of the class containing the method as
     *  additional type variables that get instantiated.
     *
     *  @param env         The current environment
     *  @param site        The type of which the method is a member.
     *  @param m           The method symbol.
     *  @param argtypes    The invocation's given value arguments.
     *  @param typeargtypes    The invocation's given type arguments.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Type rawInstantiate(Env<AttrContext> env,
                        Type site,
                        Symbol m,
                        ResultInfo resultInfo,
                        List<Type> argtypes,
                        List<Type> typeargtypes,
                        boolean allowBoxing,
                        boolean useVarargs,
                        Warner warn) throws Infer.InferenceException {

        Type mt = types.memberType(site, m);
        // tvars is the list of formal type variables for which type arguments
        // need to inferred.
        List<Type> tvars = List.nil();
        if (typeargtypes == null) typeargtypes = List.nil();
        if (!mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {
            // This is not a polymorphic method, but typeargs are supplied
            // which is fine, see JLS 15.12.2.1
        } else if (mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {
            ForAll pmt = (ForAll) mt;
            if (typeargtypes.length() != pmt.tvars.length())
                throw inapplicableMethodException.setMessage("arg.length.mismatch"); // not enough args
            // Check type arguments are within bounds
            List<Type> formals = pmt.tvars;
            List<Type> actuals = typeargtypes;
            while (formals.nonEmpty() && actuals.nonEmpty()) {
                List<Type> bounds = types.subst(types.getBounds((TypeVar)formals.head),
                                                pmt.tvars, typeargtypes);
                for (; bounds.nonEmpty(); bounds = bounds.tail)
                    if (!types.isSubtypeUnchecked(actuals.head, bounds.head, warn))
                        throw inapplicableMethodException.setMessage("explicit.param.do.not.conform.to.bounds",actuals.head, bounds);
                formals = formals.tail;
                actuals = actuals.tail;
            }
            mt = types.subst(pmt.qtype, pmt.tvars, typeargtypes);
        } else if (mt.hasTag(FORALL)) {
            ForAll pmt = (ForAll) mt;
            List<Type> tvars1 = types.newInstances(pmt.tvars);
            tvars = tvars.appendList(tvars1);
            mt = types.subst(pmt.qtype, pmt.tvars, tvars1);
        }

        // find out whether we need to go the slow route via infer
        boolean instNeeded = tvars.tail != null; /*inlined: tvars.nonEmpty()*/
        for (List<Type> l = argtypes;
             l.tail != null/*inlined: l.nonEmpty()*/ && !instNeeded;
             l = l.tail) {
            if (l.head.hasTag(FORALL)) instNeeded = true;
        }

        if (instNeeded)
            return infer.instantiateMethod(env,
                                    tvars,
                                    (MethodType)mt,
                                    resultInfo,
                                    m,
                                    argtypes,
                                    allowBoxing,
                                    useVarargs,
                                    currentResolutionContext,
                                    warn);

        currentResolutionContext.methodCheck.argumentsAcceptable(env, currentResolutionContext.deferredAttrContext(m, infer.emptyContext, resultInfo, warn),
                                argtypes, mt.getParameterTypes(), warn);
        return mt;
    }

    Type checkMethod(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     ResultInfo resultInfo,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     Warner warn) {
        MethodResolutionContext prevContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            currentResolutionContext.attrMode = DeferredAttr.AttrMode.CHECK;
            MethodResolutionPhase step = currentResolutionContext.step = env.info.pendingResolutionPhase;
            return rawInstantiate(env, site, m, resultInfo, argtypes, typeargtypes,
                    step.isBoxingRequired(), step.isVarargsRequired(), warn);
        }
        finally {
            currentResolutionContext = prevContext;
        }
    }

    /** Same but returns null instead throwing a NoInstanceException
     */
    Type instantiate(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     ResultInfo resultInfo,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     boolean allowBoxing,
                     boolean useVarargs,
                     Warner warn) {
        try {
            return rawInstantiate(env, site, m, resultInfo, argtypes, typeargtypes,
                                  allowBoxing, useVarargs, warn);
        } catch (InapplicableMethodException ex) {
            return null;
        }
    }

    /**
     * This interface defines an entry point that should be used to perform a
     * method check. A method check usually consist in determining as to whether
     * a set of types (actuals) is compatible with another set of types (formals).
     * Since the notion of compatibility can vary depending on the circumstances,
     * this interfaces allows to easily add new pluggable method check routines.
     */
    interface MethodCheck {
        /**
         * Main method check routine. A method check usually consist in determining
         * as to whether a set of types (actuals) is compatible with another set of
         * types (formals). If an incompatibility is found, an unchecked exception
         * is assumed to be thrown.
         */
        void argumentsAcceptable(Env<AttrContext> env,
                                DeferredAttrContext deferredAttrContext,
                                List<Type> argtypes,
                                List<Type> formals,
                                Warner warn);

        /**
         * Retrieve the method check object that will be used during a
         * most specific check.
         */
        MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict);
    }

    /**
     * Helper enum defining all method check diagnostics (used by resolveMethodCheck).
     */
    enum MethodCheckDiag {
        /**
         * Actuals and formals differs in length.
         */
        ARITY_MISMATCH("arg.length.mismatch", "infer.arg.length.mismatch"),
        /**
         * An actual is incompatible with a formal.
         */
        ARG_MISMATCH("no.conforming.assignment.exists", "infer.no.conforming.assignment.exists"),
        /**
         * An actual is incompatible with the varargs element type.
         */
        VARARG_MISMATCH("varargs.argument.mismatch", "infer.varargs.argument.mismatch"),
        /**
         * The varargs element type is inaccessible.
         */
        INACCESSIBLE_VARARGS("inaccessible.varargs.type", "inaccessible.varargs.type");

        final String basicKey;
        final String inferKey;

        MethodCheckDiag(String basicKey, String inferKey) {
            this.basicKey = basicKey;
            this.inferKey = inferKey;
        }

        String regex() {
            return String.format("([a-z]*\\.)*(%s|%s)", basicKey, inferKey);
        }
    }

    /**
     * Dummy method check object. All methods are deemed applicable, regardless
     * of their formal parameter types.
     */
    MethodCheck nilMethodCheck = new MethodCheck() {
        public void argumentsAcceptable(Env<AttrContext> env, DeferredAttrContext deferredAttrContext, List<Type> argtypes, List<Type> formals, Warner warn) {
            //do nothing - method always applicable regardless of actuals
        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return this;
        }
    };

    /**
     * Base class for 'real' method checks. The class defines the logic for
     * iterating through formals and actuals and provides and entry point
     * that can be used by subclasses in order to define the actual check logic.
     */
    abstract class AbstractMethodCheck implements MethodCheck {
        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                    DeferredAttrContext deferredAttrContext,
                                    List<Type> argtypes,
                                    List<Type> formals,
                                    Warner warn) {
            //should we expand formals?
            boolean useVarargs = deferredAttrContext.phase.isVarargsRequired();
            List<JCExpression> trees = TreeInfo.args(env.tree);

            //inference context used during this method check
            InferenceContext inferenceContext = deferredAttrContext.inferenceContext;

            Type varargsFormal = useVarargs ? formals.last() : null;

            if (varargsFormal == null &&
                    argtypes.size() != formals.size()) {
                reportMC(env.tree, MethodCheckDiag.ARITY_MISMATCH, inferenceContext); // not enough args
            }

            while (argtypes.nonEmpty() && formals.head != varargsFormal) {
                DiagnosticPosition pos = trees != null ? trees.head : null;
                checkArg(pos, false, argtypes.head, formals.head, deferredAttrContext, warn);
                argtypes = argtypes.tail;
                formals = formals.tail;
                trees = trees != null ? trees.tail : trees;
            }

            if (formals.head != varargsFormal) {
                reportMC(env.tree, MethodCheckDiag.ARITY_MISMATCH, inferenceContext); // not enough args
            }

            if (useVarargs) {
                //note: if applicability check is triggered by most specific test,
                //the last argument of a varargs is _not_ an array type (see JLS 15.12.2.5)
                final Type elt = types.elemtype(varargsFormal);
                while (argtypes.nonEmpty()) {
                    DiagnosticPosition pos = trees != null ? trees.head : null;
                    checkArg(pos, true, argtypes.head, elt, deferredAttrContext, warn);
                    argtypes = argtypes.tail;
                    trees = trees != null ? trees.tail : trees;
                }
            }
        }

        /**
         * Does the actual argument conforms to the corresponding formal?
         */
        abstract void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn);

        protected void reportMC(DiagnosticPosition pos, MethodCheckDiag diag, InferenceContext inferenceContext, Object... args) {
            boolean inferDiag = inferenceContext != infer.emptyContext;
            InapplicableMethodException ex = inferDiag ?
                    infer.inferenceException : inapplicableMethodException;
            if (inferDiag && (!diag.inferKey.equals(diag.basicKey))) {
                Object[] args2 = new Object[args.length + 1];
                System.arraycopy(args, 0, args2, 1, args.length);
                args2[0] = inferenceContext.inferenceVars();
                args = args2;
            }
            String key = inferDiag ? diag.inferKey : diag.basicKey;
            throw ex.setMessage(diags.create(DiagnosticType.FRAGMENT, log.currentSource(), pos, key, args));
        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return nilMethodCheck;
        }
    }

    /**
     * Arity-based method check. A method is applicable if the number of actuals
     * supplied conforms to the method signature.
     */
    MethodCheck arityMethodCheck = new AbstractMethodCheck() {
        @Override
        void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn) {
            //do nothing - actual always compatible to formals
        }
    };

    /**
     * Main method applicability routine. Given a list of actual types A,
     * a list of formal types F, determines whether the types in A are
     * compatible (by method invocation conversion) with the types in F.
     *
     * Since this routine is shared between overload resolution and method
     * type-inference, a (possibly empty) inference context is used to convert
     * formal types to the corresponding 'undet' form ahead of a compatibility
     * check so that constraints can be propagated and collected.
     *
     * Moreover, if one or more types in A is a deferred type, this routine uses
     * DeferredAttr in order to perform deferred attribution. If one or more actual
     * deferred types are stuck, they are placed in a queue and revisited later
     * after the remainder of the arguments have been seen. If this is not sufficient
     * to 'unstuck' the argument, a cyclic inference error is called out.
     *
     * A method check handler (see above) is used in order to report errors.
     */
    MethodCheck resolveMethodCheck = new AbstractMethodCheck() {

        @Override
        void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn) {
            ResultInfo mresult = methodCheckResult(varargs, formal, deferredAttrContext, warn);
            mresult.check(pos, actual);
        }

        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                    DeferredAttrContext deferredAttrContext,
                                    List<Type> argtypes,
                                    List<Type> formals,
                                    Warner warn) {
            super.argumentsAcceptable(env, deferredAttrContext, argtypes, formals, warn);
            //should we expand formals?
            if (deferredAttrContext.phase.isVarargsRequired()) {
                //check varargs element type accessibility
                varargsAccessible(env, types.elemtype(formals.last()),
                        deferredAttrContext.inferenceContext);
            }
        }

        private void varargsAccessible(final Env<AttrContext> env, final Type t, final InferenceContext inferenceContext) {
            if (inferenceContext.free(t)) {
                inferenceContext.addFreeTypeListener(List.of(t), new FreeTypeListener() {
                    @Override
                    public void typesInferred(InferenceContext inferenceContext) {
                        varargsAccessible(env, inferenceContext.asInstType(t), inferenceContext);
                    }
                });
            } else {
                if (!isAccessible(env, t)) {
                    Symbol location = env.enclClass.sym;
                    reportMC(env.tree, MethodCheckDiag.INACCESSIBLE_VARARGS, inferenceContext, t, Kinds.kindName(location), location);
                }
            }
        }

        private ResultInfo methodCheckResult(final boolean varargsCheck, Type to,
                final DeferredAttr.DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            CheckContext checkContext = new MethodCheckContext(!deferredAttrContext.phase.isBoxingRequired(), deferredAttrContext, rsWarner) {
                MethodCheckDiag methodDiag = varargsCheck ?
                                 MethodCheckDiag.VARARG_MISMATCH : MethodCheckDiag.ARG_MISMATCH;

                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    reportMC(pos, methodDiag, deferredAttrContext.inferenceContext, details);
                }
            };
            return new MethodResultInfo(to, checkContext);
        }

        @Override
        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return new MostSpecificCheck(strict, actuals);
        }
    };

    /**
     * Check context to be used during method applicability checks. A method check
     * context might contain inference variables.
     */
    abstract class MethodCheckContext implements CheckContext {

        boolean strict;
        DeferredAttrContext deferredAttrContext;
        Warner rsWarner;

        public MethodCheckContext(boolean strict, DeferredAttrContext deferredAttrContext, Warner rsWarner) {
           this.strict = strict;
           this.deferredAttrContext = deferredAttrContext;
           this.rsWarner = rsWarner;
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return strict ?
                    types.isSubtypeUnchecked(found, deferredAttrContext.inferenceContext.asFree(req), warn) :
                    types.isConvertible(found, deferredAttrContext.inferenceContext.asFree(req), warn);
        }

        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            throw inapplicableMethodException.setMessage(details);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return rsWarner;
        }

        public InferenceContext inferenceContext() {
            return deferredAttrContext.inferenceContext;
        }

        public DeferredAttrContext deferredAttrContext() {
            return deferredAttrContext;
        }
    }

    /**
     * ResultInfo class to be used during method applicability checks. Check
     * for deferred types goes through special path.
     */
    class MethodResultInfo extends ResultInfo {

        public MethodResultInfo(Type pt, CheckContext checkContext) {
            attr.super(VAL, pt, checkContext);
        }

        @Override
        protected Type check(DiagnosticPosition pos, Type found) {
            if (found.hasTag(DEFERRED)) {
                DeferredType dt = (DeferredType)found;
                return dt.check(this);
            } else {
                return super.check(pos, chk.checkNonVoid(pos, types.capture(types.upperBound(found.baseType()))));
            }
        }

        @Override
        protected MethodResultInfo dup(Type newPt) {
            return new MethodResultInfo(newPt, checkContext);
        }

        @Override
        protected ResultInfo dup(CheckContext newContext) {
            return new MethodResultInfo(pt, newContext);
        }
    }

    /**
     * Most specific method applicability routine. Given a list of actual types A,
     * a list of formal types F1, and a list of formal types F2, the routine determines
     * as to whether the types in F1 can be considered more specific than those in F2 w.r.t.
     * argument types A.
     */
    class MostSpecificCheck implements MethodCheck {

        boolean strict;
        List<Type> actuals;

        MostSpecificCheck(boolean strict, List<Type> actuals) {
            this.strict = strict;
            this.actuals = actuals;
        }

        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                    DeferredAttrContext deferredAttrContext,
                                    List<Type> formals1,
                                    List<Type> formals2,
                                    Warner warn) {
            formals2 = adjustArgs(formals2, deferredAttrContext.msym, formals1.length(), deferredAttrContext.phase.isVarargsRequired());
            while (formals2.nonEmpty()) {
                ResultInfo mresult = methodCheckResult(formals2.head, deferredAttrContext, warn, actuals.head);
                mresult.check(null, formals1.head);
                formals1 = formals1.tail;
                formals2 = formals2.tail;
                actuals = actuals.isEmpty() ? actuals : actuals.tail;
            }
        }

       /**
        * Create a method check context to be used during the most specific applicability check
        */
        ResultInfo methodCheckResult(Type to, DeferredAttr.DeferredAttrContext deferredAttrContext,
               Warner rsWarner, Type actual) {
           return attr.new ResultInfo(Kinds.VAL, to,
                   new MostSpecificCheckContext(strict, deferredAttrContext, rsWarner, actual));
        }

        /**
         * Subclass of method check context class that implements most specific
         * method conversion. If the actual type under analysis is a deferred type
         * a full blown structural analysis is carried out.
         */
        class MostSpecificCheckContext extends MethodCheckContext {

            Type actual;

            public MostSpecificCheckContext(boolean strict, DeferredAttrContext deferredAttrContext, Warner rsWarner, Type actual) {
                super(strict, deferredAttrContext, rsWarner);
                this.actual = actual;
            }

            public boolean compatible(Type found, Type req, Warner warn) {
                if (!allowStructuralMostSpecific || actual == null) {
                    return super.compatible(found, req, warn);
                } else {
                    switch (actual.getTag()) {
                        case DEFERRED:
                            DeferredType dt = (DeferredType) actual;
                            DeferredType.SpeculativeCache.Entry e = dt.speculativeCache.get(deferredAttrContext.msym, deferredAttrContext.phase);
                            return (e == null || e.speculativeTree == deferredAttr.stuckTree)
                                    ? false : mostSpecific(found, req, e.speculativeTree, warn);
                        default:
                            return standaloneMostSpecific(found, req, actual, warn);
                    }
                }
            }

            private boolean mostSpecific(Type t, Type s, JCTree tree, Warner warn) {
                MostSpecificChecker msc = new MostSpecificChecker(t, s, warn);
                msc.scan(tree);
                return msc.result;
            }

            boolean polyMostSpecific(Type t1, Type t2, Warner warn) {
                return (!t1.isPrimitive() && t2.isPrimitive())
                        ? true : super.compatible(t1, t2, warn);
            }

            boolean standaloneMostSpecific(Type t1, Type t2, Type exprType, Warner warn) {
                return (exprType.isPrimitive() == t1.isPrimitive()
                        && exprType.isPrimitive() != t2.isPrimitive())
                        ? true : super.compatible(t1, t2, warn);
            }

            /**
             * Structural checker for most specific.
             */
            class MostSpecificChecker extends DeferredAttr.PolyScanner {

                final Type t;
                final Type s;
                final Warner warn;
                boolean result;

                MostSpecificChecker(Type t, Type s, Warner warn) {
                    this.t = t;
                    this.s = s;
                    this.warn = warn;
                    result = true;
                }

                @Override
                void skip(JCTree tree) {
                    result &= standaloneMostSpecific(t, s, tree.type, warn);
                }

                @Override
                public void visitConditional(JCConditional tree) {
                    if (tree.polyKind == PolyKind.STANDALONE) {
                        result &= standaloneMostSpecific(t, s, tree.type, warn);
                    } else {
                        super.visitConditional(tree);
                    }
                }

                @Override
                public void visitApply(JCMethodInvocation tree) {
                    result &= (tree.polyKind == PolyKind.STANDALONE)
                            ? standaloneMostSpecific(t, s, tree.type, warn)
                            : polyMostSpecific(t, s, warn);
                }

                @Override
                public void visitNewClass(JCNewClass tree) {
                    result &= (tree.polyKind == PolyKind.STANDALONE)
                            ? standaloneMostSpecific(t, s, tree.type, warn)
                            : polyMostSpecific(t, s, warn);
                }

                @Override
                public void visitReference(JCMemberReference tree) {
                    if (types.isFunctionalInterface(t.tsym) &&
                            types.isFunctionalInterface(s.tsym) &&
                            types.asSuper(t, s.tsym) == null &&
                            types.asSuper(s, t.tsym) == null) {
                        Type desc_t = types.findDescriptorType(t);
                        Type desc_s = types.findDescriptorType(s);
                        if (types.isSameTypes(desc_t.getParameterTypes(), desc_s.getParameterTypes())) {
                            if (!desc_s.getReturnType().hasTag(VOID)) {
                                //perform structural comparison
                                Type ret_t = desc_t.getReturnType();
                                Type ret_s = desc_s.getReturnType();
                                result &= ((tree.refPolyKind == PolyKind.STANDALONE)
                                        ? standaloneMostSpecific(ret_t, ret_s, tree.sym.type.getReturnType(), warn)
                                        : polyMostSpecific(ret_t, ret_s, warn));
                            } else {
                                return;
                            }
                        } else {
                            result &= false;
                        }
                    } else {
                        result &= MostSpecificCheckContext.super.compatible(t, s, warn);
                    }
                }

                @Override
                public void visitLambda(JCLambda tree) {
                    if (types.isFunctionalInterface(t.tsym) &&
                            types.isFunctionalInterface(s.tsym) &&
                            types.asSuper(t, s.tsym) == null &&
                            types.asSuper(s, t.tsym) == null) {
                        Type desc_t = types.findDescriptorType(t);
                        Type desc_s = types.findDescriptorType(s);
                        if (tree.paramKind == JCLambda.ParameterKind.EXPLICIT
                                || types.isSameTypes(desc_t.getParameterTypes(), desc_s.getParameterTypes())) {
                            if (!desc_s.getReturnType().hasTag(VOID)) {
                                //perform structural comparison
                                Type ret_t = desc_t.getReturnType();
                                Type ret_s = desc_s.getReturnType();
                                scanLambdaBody(tree, ret_t, ret_s);
                            } else {
                                return;
                            }
                        } else {
                            result &= false;
                        }
                    } else {
                        result &= MostSpecificCheckContext.super.compatible(t, s, warn);
                    }
                }
                //where

                void scanLambdaBody(JCLambda lambda, final Type t, final Type s) {
                    if (lambda.getBodyKind() == JCTree.JCLambda.BodyKind.EXPRESSION) {
                        result &= MostSpecificCheckContext.this.mostSpecific(t, s, lambda.body, warn);
                    } else {
                        DeferredAttr.LambdaReturnScanner lambdaScanner =
                                new DeferredAttr.LambdaReturnScanner() {
                                    @Override
                                    public void visitReturn(JCReturn tree) {
                                        if (tree.expr != null) {
                                            result &= MostSpecificCheckContext.this.mostSpecific(t, s, tree.expr, warn);
                                        }
                                    }
                                };
                        lambdaScanner.scan(lambda.body);
                    }
                }
            }
        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            Assert.error("Cannot get here!");
            return null;
        }
    }

    public static class InapplicableMethodException extends RuntimeException {
        private static final long serialVersionUID = 0;

        JCDiagnostic diagnostic;
        JCDiagnostic.Factory diags;

        InapplicableMethodException(JCDiagnostic.Factory diags) {
            this.diagnostic = null;
            this.diags = diags;
        }
        InapplicableMethodException setMessage() {
            return setMessage((JCDiagnostic)null);
        }
        InapplicableMethodException setMessage(String key) {
            return setMessage(key != null ? diags.fragment(key) : null);
        }
        InapplicableMethodException setMessage(String key, Object... args) {
            return setMessage(key != null ? diags.fragment(key, args) : null);
        }
        InapplicableMethodException setMessage(JCDiagnostic diag) {
            this.diagnostic = diag;
            return this;
        }

        public JCDiagnostic getDiagnostic() {
            return diagnostic;
        }
    }
    private final InapplicableMethodException inapplicableMethodException;

/* ***************************************************************************
 *  Symbol lookup
 *  the following naming conventions for arguments are used
 *
 *       env      is the environment where the symbol was mentioned
 *       site     is the type of which the symbol is a member
 *       name     is the symbol's name
 *                if no arguments are given
 *       argtypes are the value arguments, if we search for a method
 *
 *  If no symbol was found, a ResolveError detailing the problem is returned.
 ****************************************************************************/

    /** Find field. Synthetic fields are always skipped.
     *  @param env     The current environment.
     *  @param site    The original type from where the selection takes place.
     *  @param name    The name of the field.
     *  @param c       The class to search for the field. This is always
     *                 a superclass or implemented interface of site's class.
     */
    Symbol findField(Env<AttrContext> env,
                     Type site,
                     Name name,
                     TypeSymbol c) {
        while (c.type.hasTag(TYPEVAR))
            c = c.type.getUpperBound().tsym;
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == VAR && (e.sym.flags_field & SYNTHETIC) == 0) {
                return isAccessible(env, site, e.sym)
                    ? e.sym : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        Type st = types.supertype(c.type);
        if (st != null && (st.hasTag(CLASS) || st.hasTag(TYPEVAR))) {
            sym = findField(env, site, name, st.tsym);
            if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findField(env, site, name, l.head.tsym);
            if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
                sym.owner != bestSoFar.owner)
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Resolve a field identifier, throw a fatal error if not found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     */
    public VarSymbol resolveInternalField(DiagnosticPosition pos, Env<AttrContext> env,
                                          Type site, Name name) {
        Symbol sym = findField(env, site, name, site.tsym);
        if (sym.kind == VAR) return (VarSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.field",
                                name));
    }

    /** Find unqualified variable or field with given name.
     *  Synthetic fields always skipped.
     *  @param env     The current environment.
     *  @param name    The name of the variable or field.
     */
    Symbol findVar(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            Scope.Entry e = env1.info.scope.lookup(name);
            while (e.scope != null &&
                   (e.sym.kind != VAR ||
                    (e.sym.flags_field & SYNTHETIC) != 0))
                e = e.next();
            sym = (e.scope != null)
                ? e.sym
                : findField(
                    env1, env1.enclClass.sym.type, name, env1.enclClass.sym);
            if (sym.exists()) {
                if (staticOnly &&
                    sym.kind == VAR &&
                    sym.owner.kind == TYP &&
                    (sym.flags() & STATIC) == 0)
                    return new StaticError(sym);
                else
                    return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }

            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }

        sym = findField(env, syms.predefClass.type, name, syms.predefClass);
        if (sym.exists())
            return sym;
        if (bestSoFar.exists())
            return bestSoFar;

        Scope.Entry e = env.toplevel.namedImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == VAR) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                return isAccessible(env, origin, sym)
                    ? sym : new AccessError(env, origin, sym);
            }
        }

        Symbol origin = null;
        e = env.toplevel.starImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            if (sym.kind != VAR)
                continue;
            // invariant: sym.kind == VAR
            if (bestSoFar.kind < AMBIGUOUS && sym.owner != bestSoFar.owner)
                return new AmbiguityError(bestSoFar, sym);
            else if (bestSoFar.kind >= VAR) {
                origin = e.getOrigin().owner;
                bestSoFar = isAccessible(env, origin.type, sym)
                    ? sym : new AccessError(env, origin.type, sym);
            }
        }
        if (bestSoFar.kind == VAR && bestSoFar.owner.type != origin.type)
            return bestSoFar.clone(origin);
        else
            return bestSoFar;
    }

    Warner noteWarner = new Warner();

    /** Select the best method for a call site among two choices.
     *  @param env              The current environment.
     *  @param site             The original type from where the
     *                          selection takes place.
     *  @param argtypes         The invocation's value arguments,
     *  @param typeargtypes     The invocation's type arguments,
     *  @param sym              Proposed new best match.
     *  @param bestSoFar        Previously found best match.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    @SuppressWarnings("fallthrough")
    Symbol selectBest(Env<AttrContext> env,
                      Type site,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      Symbol sym,
                      Symbol bestSoFar,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        if (sym.kind == ERR ||
                !sym.isInheritedIn(site.tsym, types)) {
            return bestSoFar;
        } else if (useVarargs && (sym.flags() & VARARGS) == 0) {
            return bestSoFar.kind >= ERRONEOUS ?
                    new BadVarargsMethod((ResolveError)bestSoFar) :
                    bestSoFar;
        }
        Assert.check(sym.kind < AMBIGUOUS);
        try {
            Type mt = rawInstantiate(env, site, sym, null, argtypes, typeargtypes,
                               allowBoxing, useVarargs, types.noWarnings);
            if (!operator || verboseResolutionMode.contains(VerboseResolutionMode.PREDEF))
                currentResolutionContext.addApplicableCandidate(sym, mt);
        } catch (InapplicableMethodException ex) {
            if (!operator)
                currentResolutionContext.addInapplicableCandidate(sym, ex.getDiagnostic());
            switch (bestSoFar.kind) {
                case ABSENT_MTH:
                    return new InapplicableSymbolError(currentResolutionContext);
                case WRONG_MTH:
                    if (operator) return bestSoFar;
                    bestSoFar = new InapplicableSymbolsError(currentResolutionContext);
                default:
                    return bestSoFar;
            }
        }
        if (!isAccessible(env, site, sym)) {
            return (bestSoFar.kind == ABSENT_MTH)
                ? new AccessError(env, site, sym)
                : bestSoFar;
        }
        return (bestSoFar.kind > AMBIGUOUS)
            ? sym
            : mostSpecific(argtypes, sym, bestSoFar, env, site,
                           allowBoxing && operator, useVarargs);
    }

    /* Return the most specific of the two methods for a call,
     *  given that both are accessible and applicable.
     *  @param m1               A new candidate for most specific.
     *  @param m2               The previous most specific candidate.
     *  @param env              The current environment.
     *  @param site             The original type from where the selection
     *                          takes place.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol mostSpecific(List<Type> argtypes, Symbol m1,
                        Symbol m2,
                        Env<AttrContext> env,
                        final Type site,
                        boolean allowBoxing,
                        boolean useVarargs) {
        switch (m2.kind) {
        case MTH:
            if (m1 == m2) return m1;
            boolean m1SignatureMoreSpecific =
                    signatureMoreSpecific(argtypes, env, site, m1, m2, allowBoxing, useVarargs);
            boolean m2SignatureMoreSpecific =
                    signatureMoreSpecific(argtypes, env, site, m2, m1, allowBoxing, useVarargs);
            if (m1SignatureMoreSpecific && m2SignatureMoreSpecific) {
                Type mt1 = types.memberType(site, m1);
                Type mt2 = types.memberType(site, m2);
                if (!types.overrideEquivalent(mt1, mt2))
                    return ambiguityError(m1, m2);

                // same signature; select (a) the non-bridge method, or
                // (b) the one that overrides the other, or (c) the concrete
                // one, or (d) merge both abstract signatures
                if ((m1.flags() & BRIDGE) != (m2.flags() & BRIDGE))
                    return ((m1.flags() & BRIDGE) != 0) ? m2 : m1;

                // if one overrides or hides the other, use it
                TypeSymbol m1Owner = (TypeSymbol)m1.owner;
                TypeSymbol m2Owner = (TypeSymbol)m2.owner;
                if (types.asSuper(m1Owner.type, m2Owner) != null &&
                    ((m1.owner.flags_field & INTERFACE) == 0 ||
                     (m2.owner.flags_field & INTERFACE) != 0) &&
                    m1.overrides(m2, m1Owner, types, false))
                    return m1;
                if (types.asSuper(m2Owner.type, m1Owner) != null &&
                    ((m2.owner.flags_field & INTERFACE) == 0 ||
                     (m1.owner.flags_field & INTERFACE) != 0) &&
                    m2.overrides(m1, m2Owner, types, false))
                    return m2;
                boolean m1Abstract = (m1.flags() & ABSTRACT) != 0;
                boolean m2Abstract = (m2.flags() & ABSTRACT) != 0;
                if (m1Abstract && !m2Abstract) return m2;
                if (m2Abstract && !m1Abstract) return m1;
                // both abstract or both concrete
                return ambiguityError(m1, m2);
            }
            if (m1SignatureMoreSpecific) return m1;
            if (m2SignatureMoreSpecific) return m2;
            return ambiguityError(m1, m2);
        case AMBIGUOUS:
            //check if m1 is more specific than all ambiguous methods in m2
            AmbiguityError e = (AmbiguityError)m2;
            for (Symbol s : e.ambiguousSyms) {
                if (mostSpecific(argtypes, m1, s, env, site, allowBoxing, useVarargs) != m1) {
                    return e.addAmbiguousSymbol(m1);
                }
            }
            return m1;
        default:
            throw new AssertionError();
        }
    }
    //where
    private boolean signatureMoreSpecific(List<Type> actuals, Env<AttrContext> env, Type site, Symbol m1, Symbol m2, boolean allowBoxing, boolean useVarargs) {
        noteWarner.clear();
        int maxLength = Math.max(
                            Math.max(m1.type.getParameterTypes().length(), actuals.length()),
                            m2.type.getParameterTypes().length());
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            currentResolutionContext.step = prevResolutionContext.step;
            currentResolutionContext.methodCheck =
                    prevResolutionContext.methodCheck.mostSpecificCheck(actuals, !allowBoxing);
            Type mst = instantiate(env, site, m2, null,
                    adjustArgs(types.lowerBounds(types.memberType(site, m1).getParameterTypes()), m1, maxLength, useVarargs), null,
                    allowBoxing, useVarargs, noteWarner);
            return mst != null &&
                    !noteWarner.hasLint(Lint.LintCategory.UNCHECKED);
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }
    private List<Type> adjustArgs(List<Type> args, Symbol msym, int length, boolean allowVarargs) {
        if ((msym.flags() & VARARGS) != 0 && allowVarargs) {
            Type varargsElem = types.elemtype(args.last());
            if (varargsElem == null) {
                Assert.error("Bad varargs = " + args.last() + " " + msym);
            }
            List<Type> newArgs = args.reverse().tail.prepend(varargsElem).reverse();
            while (newArgs.length() < length) {
                newArgs = newArgs.append(newArgs.last());
            }
            return newArgs;
        } else {
            return args;
        }
    }
    //where
    Type mostSpecificReturnType(Type mt1, Type mt2) {
        Type rt1 = mt1.getReturnType();
        Type rt2 = mt2.getReturnType();

        if (mt1.hasTag(FORALL) && mt2.hasTag(FORALL)) {
            //if both are generic methods, adjust return type ahead of subtyping check
            rt1 = types.subst(rt1, mt1.getTypeArguments(), mt2.getTypeArguments());
        }
        //first use subtyping, then return type substitutability
        if (types.isSubtype(rt1, rt2)) {
            return mt1;
        } else if (types.isSubtype(rt2, rt1)) {
            return mt2;
        } else if (types.returnTypeSubstitutable(mt1, mt2)) {
            return mt1;
        } else if (types.returnTypeSubstitutable(mt2, mt1)) {
            return mt2;
        } else {
            return null;
        }
    }
    //where
    Symbol ambiguityError(Symbol m1, Symbol m2) {
        if (((m1.flags() | m2.flags()) & CLASH) != 0) {
            return (m1.flags() & CLASH) == 0 ? m1 : m2;
        } else {
            return new AmbiguityError(m1, m2);
        }
    }

    Symbol findMethodInScope(Env<AttrContext> env,
            Type site,
            Name name,
            List<Type> argtypes,
            List<Type> typeargtypes,
            Scope sc,
            Symbol bestSoFar,
            boolean allowBoxing,
            boolean useVarargs,
            boolean operator,
            boolean abstractok) {
        for (Symbol s : sc.getElementsByName(name, new LookupFilter(abstractok))) {
            bestSoFar = selectBest(env, site, argtypes, typeargtypes, s,
                    bestSoFar, allowBoxing, useVarargs, operator);
        }
        return bestSoFar;
    }
    //where
        class LookupFilter implements Filter<Symbol> {

            boolean abstractOk;

            LookupFilter(boolean abstractOk) {
                this.abstractOk = abstractOk;
            }

            public boolean accepts(Symbol s) {
                long flags = s.flags();
                return s.kind == MTH &&
                        (flags & SYNTHETIC) == 0 &&
                        (abstractOk ||
                        (flags & DEFAULT) != 0 ||
                        (flags & ABSTRACT) == 0);
            }
        };

    /** Find best qualified method matching given name, type and value
     *  arguments.
     *  @param env       The current environment.
     *  @param site      The original type from where the selection
     *                   takes place.
     *  @param name      The method's name.
     *  @param argtypes  The method's value arguments.
     *  @param typeargtypes The method's type arguments
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol findMethod(Env<AttrContext> env,
                      Type site,
                      Name name,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        Symbol bestSoFar = methodNotFound;
        bestSoFar = findMethod(env,
                          site,
                          name,
                          argtypes,
                          typeargtypes,
                          site.tsym.type,
                          bestSoFar,
                          allowBoxing,
                          useVarargs,
                          operator);
        reportVerboseResolutionDiagnostic(env.tree.pos(), name, site, argtypes, typeargtypes, bestSoFar);
        return bestSoFar;
    }
    // where
    private Symbol findMethod(Env<AttrContext> env,
                              Type site,
                              Name name,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Type intype,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs,
                              boolean operator) {
        @SuppressWarnings({"unchecked","rawtypes"})
        List<Type>[] itypes = (List<Type>[])new List[] { List.<Type>nil(), List.<Type>nil() };
        InterfaceLookupPhase iphase = InterfaceLookupPhase.ABSTRACT_OK;
        for (TypeSymbol s : superclasses(intype)) {
            bestSoFar = findMethodInScope(env, site, name, argtypes, typeargtypes,
                    s.members(), bestSoFar, allowBoxing, useVarargs, operator, true);
            if (name == names.init) return bestSoFar;
            iphase = (iphase == null) ? null : iphase.update(s, this);
            if (iphase != null) {
                for (Type itype : types.interfaces(s.type)) {
                    itypes[iphase.ordinal()] = types.union(types.closure(itype), itypes[iphase.ordinal()]);
                }
            }
        }

        Symbol concrete = bestSoFar.kind < ERR &&
                (bestSoFar.flags() & ABSTRACT) == 0 ?
                bestSoFar : methodNotFound;

        for (InterfaceLookupPhase iphase2 : InterfaceLookupPhase.values()) {
            if (iphase2 == InterfaceLookupPhase.DEFAULT_OK && !allowDefaultMethods) break;
            //keep searching for abstract methods
            for (Type itype : itypes[iphase2.ordinal()]) {
                if (!itype.isInterface()) continue; //skip j.l.Object (included by Types.closure())
                if (iphase2 == InterfaceLookupPhase.DEFAULT_OK &&
                        (itype.tsym.flags() & DEFAULT) == 0) continue;
                bestSoFar = findMethodInScope(env, site, name, argtypes, typeargtypes,
                        itype.tsym.members(), bestSoFar, allowBoxing, useVarargs, operator, true);
                if (concrete != bestSoFar &&
                        concrete.kind < ERR  && bestSoFar.kind < ERR &&
                        types.isSubSignature(concrete.type, bestSoFar.type)) {
                    //this is an hack - as javac does not do full membership checks
                    //most specific ends up comparing abstract methods that might have
                    //been implemented by some concrete method in a subclass and,
                    //because of raw override, it is possible for an abstract method
                    //to be more specific than the concrete method - so we need
                    //to explicitly call that out (see CR 6178365)
                    bestSoFar = concrete;
                }
            }
        }
        return bestSoFar;
    }

    enum InterfaceLookupPhase {
        ABSTRACT_OK() {
            @Override
            InterfaceLookupPhase update(Symbol s, Resolve rs) {
                //We should not look for abstract methods if receiver is a concrete class
                //(as concrete classes are expected to implement all abstracts coming
                //from superinterfaces)
                if ((s.flags() & (ABSTRACT | INTERFACE | ENUM)) != 0) {
                    return this;
                } else if (rs.allowDefaultMethods) {
                    return DEFAULT_OK;
                } else {
                    return null;
                }
            }
        },
        DEFAULT_OK() {
            @Override
            InterfaceLookupPhase update(Symbol s, Resolve rs) {
                return this;
            }
        };

        abstract InterfaceLookupPhase update(Symbol s, Resolve rs);
    }

    /**
     * Return an Iterable object to scan the superclasses of a given type.
     * It's crucial that the scan is done lazily, as we don't want to accidentally
     * access more supertypes than strictly needed (as this could trigger completion
     * errors if some of the not-needed supertypes are missing/ill-formed).
     */
    Iterable<TypeSymbol> superclasses(final Type intype) {
        return new Iterable<TypeSymbol>() {
            public Iterator<TypeSymbol> iterator() {
                return new Iterator<TypeSymbol>() {

                    List<TypeSymbol> seen = List.nil();
                    TypeSymbol currentSym = symbolFor(intype);
                    TypeSymbol prevSym = null;

                    public boolean hasNext() {
                        if (currentSym == syms.noSymbol) {
                            currentSym = symbolFor(types.supertype(prevSym.type));
                        }
                        return currentSym != null;
                    }

                    public TypeSymbol next() {
                        prevSym = currentSym;
                        currentSym = syms.noSymbol;
                        Assert.check(prevSym != null || prevSym != syms.noSymbol);
                        return prevSym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    TypeSymbol symbolFor(Type t) {
                        if (!t.hasTag(CLASS) &&
                                !t.hasTag(TYPEVAR)) {
                            return null;
                        }
                        while (t.hasTag(TYPEVAR))
                            t = t.getUpperBound();
                        if (seen.contains(t.tsym)) {
                            //degenerate case in which we have a circular
                            //class hierarchy - because of ill-formed classfiles
                            return null;
                        }
                        seen = seen.prepend(t.tsym);
                        return t.tsym;
                    }
                };
            }
        };
    }

    /** Find unqualified method matching given name, type and value arguments.
     *  @param env       The current environment.
     *  @param name      The method's name.
     *  @param argtypes  The method's value arguments.
     *  @param typeargtypes  The method's type arguments.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs) {
        Symbol bestSoFar = methodNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            sym = findMethod(
                env1, env1.enclClass.sym.type, name, argtypes, typeargtypes,
                allowBoxing, useVarargs, false);
            if (sym.exists()) {
                if (staticOnly &&
                    sym.kind == MTH &&
                    sym.owner.kind == TYP &&
                    (sym.flags() & STATIC) == 0) return new StaticError(sym);
                else return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }

        sym = findMethod(env, syms.predefClass.type, name, argtypes,
                         typeargtypes, allowBoxing, useVarargs, false);
        if (sym.exists())
            return sym;

        Scope.Entry e = env.toplevel.namedImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                                       argtypes, typeargtypes,
                                       sym, bestSoFar,
                                       allowBoxing, useVarargs, false);
            }
        }
        if (bestSoFar.exists())
            return bestSoFar;

        e = env.toplevel.starImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                                       argtypes, typeargtypes,
                                       sym, bestSoFar,
                                       allowBoxing, useVarargs, false);
            }
        }
        return bestSoFar;
    }

    /** Load toplevel or member class with given fully qualified name and
     *  verify that it is accessible.
     *  @param env       The current environment.
     *  @param name      The fully qualified name of the class to be loaded.
     */
    Symbol loadClass(Env<AttrContext> env, Name name) {
        try {
            ClassSymbol c = reader.loadClass(name);
            return isAccessible(env, c) ? c : new AccessError(c);
        } catch (ClassReader.BadClassFile err) {
            throw err;
        } catch (CompletionFailure ex) {
            return typeNotFound;
        }
    }

    /** Find qualified member type.
     *  @param env       The current environment.
     *  @param site      The original type from where the selection takes
     *                   place.
     *  @param name      The type's name.
     *  @param c         The class to search for the member type. This is
     *                   always a superclass or implemented interface of
     *                   site's class.
     */
    Symbol findMemberType(Env<AttrContext> env,
                          Type site,
                          Name name,
                          TypeSymbol c) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == TYP) {
                return isAccessible(env, site, e.sym)
                    ? e.sym
                    : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        Type st = types.supertype(c.type);
        if (st != null && st.hasTag(CLASS)) {
            sym = findMemberType(env, site, name, st.tsym);
            if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findMemberType(env, site, name, l.head.tsym);
            if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
                sym.owner != bestSoFar.owner)
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Find a global type in given scope and load corresponding class.
     *  @param env       The current environment.
     *  @param scope     The scope in which to look for the type.
     *  @param name      The type's name.
     */
    Symbol findGlobalType(Env<AttrContext> env, Scope scope, Name name) {
        Symbol bestSoFar = typeNotFound;
        for (Scope.Entry e = scope.lookup(name); e.scope != null; e = e.next()) {
            Symbol sym = loadClass(env, e.sym.flatName());
            if (bestSoFar.kind == TYP && sym.kind == TYP &&
                bestSoFar != sym)
                return new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Find an unqualified type symbol.
     *  @param env       The current environment.
     *  @param name      The type's name.
     */
    Symbol findType(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        boolean staticOnly = false;
        for (Env<AttrContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            if (isStatic(env1)) staticOnly = true;
            for (Scope.Entry e = env1.info.scope.lookup(name);
                 e.scope != null;
                 e = e.next()) {
                if (e.sym.kind == TYP) {
                    if (staticOnly &&
                        e.sym.type.hasTag(TYPEVAR) &&
                        e.sym.owner.kind == TYP) return new StaticError(e.sym);
                    return e.sym;
                }
            }

            sym = findMemberType(env1, env1.enclClass.sym.type, name,
                                 env1.enclClass.sym);
            if (staticOnly && sym.kind == TYP &&
                sym.type.hasTag(CLASS) &&
                sym.type.getEnclosingType().hasTag(CLASS) &&
                env1.enclClass.sym.type.isParameterized() &&
                sym.type.getEnclosingType().isParameterized())
                return new StaticError(sym);
            else if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;

            JCClassDecl encl = env1.baseClause ? (JCClassDecl)env1.tree : env1.enclClass;
            if ((encl.sym.flags() & STATIC) != 0)
                staticOnly = true;
        }

        if (!env.tree.hasTag(IMPORT)) {
            sym = findGlobalType(env, env.toplevel.namedImportScope, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;

            sym = findGlobalType(env, env.toplevel.packge.members(), name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;

            sym = findGlobalType(env, env.toplevel.starImportScope, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }

        return bestSoFar;
    }

    /** Find an unqualified identifier which matches a specified kind set.
     *  @param env       The current environment.
     *  @param name      The identifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a subset of VAL, TYP, PCK).
     */
    Symbol findIdent(Env<AttrContext> env, Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;

        if ((kind & VAR) != 0) {
            sym = findVar(env, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }

        if ((kind & TYP) != 0) {
            sym = findType(env, name);
            if (sym.kind==TYP) {
                 reportDependence(env.enclClass.sym, sym);
            }
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }

        if ((kind & PCK) != 0) return reader.enterPackage(name);
        else return bestSoFar;
    }

    /** Report dependencies.
     * @param from The enclosing class sym
     * @param to   The found identifier that the class depends on.
     */
    public void reportDependence(Symbol from, Symbol to) {
        // Override if you want to collect the reported dependencies.
    }

    /** Find an identifier in a package which matches a specified kind set.
     *  @param env       The current environment.
     *  @param name      The identifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a nonempty subset of TYP, PCK).
     */
    Symbol findIdentInPackage(Env<AttrContext> env, TypeSymbol pck,
                              Name name, int kind) {
        Name fullname = TypeSymbol.formFullName(name, pck);
        Symbol bestSoFar = typeNotFound;
        PackageSymbol pack = null;
        if ((kind & PCK) != 0) {
            pack = reader.enterPackage(fullname);
            if (pack.exists()) return pack;
        }
        if ((kind & TYP) != 0) {
            Symbol sym = loadClass(env, fullname);
            if (sym.exists()) {
                // don't allow programs to use flatnames
                if (name == sym.name) return sym;
            }
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        return (pack != null) ? pack : bestSoFar;
    }

    /** Find an identifier among the members of a given type `site'.
     *  @param env       The current environment.
     *  @param site      The type containing the symbol to be found.
     *  @param name      The identifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a subset of VAL, TYP).
     */
    Symbol findIdentInType(Env<AttrContext> env, Type site,
                           Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        if ((kind & VAR) != 0) {
            sym = findField(env, site, name, site.tsym);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }

        if ((kind & TYP) != 0) {
            sym = findMemberType(env, site, name, site.tsym);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        return bestSoFar;
    }

/* ***************************************************************************
 *  Access checking
 *  The following methods convert ResolveErrors to ErrorSymbols, issuing
 *  an error message in the process
 ****************************************************************************/

    /** If `sym' is a bad symbol: report error and return errSymbol
     *  else pass through unchanged,
     *  additional arguments duplicate what has been used in trying to find the
     *  symbol {@literal (--> flyweight pattern)}. This improves performance since we
     *  expect misses to happen frequently.
     *
     *  @param sym       The symbol that was found, or a ResolveError.
     *  @param pos       The position to use for error reporting.
     *  @param location  The symbol the served as a context for this lookup
     *  @param site      The original type from where the selection took place.
     *  @param name      The symbol's name.
     *  @param qualified Did we get here through a qualified expression resolution?
     *  @param argtypes  The invocation's value arguments,
     *                   if we looked for a method.
     *  @param typeargtypes  The invocation's type arguments,
     *                   if we looked for a method.
     *  @param logResolveHelper helper class used to log resolve errors
     */
    Symbol accessInternal(Symbol sym,
                  DiagnosticPosition pos,
                  Symbol location,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes,
                  LogResolveHelper logResolveHelper) {
        if (sym.kind >= AMBIGUOUS) {
            ResolveError errSym = (ResolveError)sym;
            sym = errSym.access(name, qualified ? site.tsym : syms.noSymbol);
            argtypes = logResolveHelper.getArgumentTypes(errSym, sym, name, argtypes);
            if (logResolveHelper.resolveDiagnosticNeeded(site, argtypes, typeargtypes)) {
                logResolveError(errSym, pos, location, site, name, argtypes, typeargtypes);
            }
        }
        return sym;
    }

    /**
     * Variant of the generalized access routine, to be used for generating method
     * resolution diagnostics
     */
    Symbol accessMethod(Symbol sym,
                  DiagnosticPosition pos,
                  Symbol location,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes) {
        return accessInternal(sym, pos, location, site, name, qualified, argtypes, typeargtypes, methodLogResolveHelper);
    }

    /** Same as original accessMethod(), but without location.
     */
    Symbol accessMethod(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes) {
        return accessMethod(sym, pos, site.tsym, site, name, qualified, argtypes, typeargtypes);
    }

    /**
     * Variant of the generalized access routine, to be used for generating variable,
     * type resolution diagnostics
     */
    Symbol accessBase(Symbol sym,
                  DiagnosticPosition pos,
                  Symbol location,
                  Type site,
                  Name name,
                  boolean qualified) {
        return accessInternal(sym, pos, location, site, name, qualified, List.<Type>nil(), null, basicLogResolveHelper);
    }

    /** Same as original accessBase(), but without location.
     */
    Symbol accessBase(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified) {
        return accessBase(sym, pos, site.tsym, site, name, qualified);
    }

    interface LogResolveHelper {
        boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes);
        List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes);
    }

    LogResolveHelper basicLogResolveHelper = new LogResolveHelper() {
        public boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes) {
            return !site.isErroneous();
        }
        public List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes) {
            return argtypes;
        }
    };

    LogResolveHelper methodLogResolveHelper = new LogResolveHelper() {
        public boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes) {
            return !site.isErroneous() &&
                        !Type.isErroneous(argtypes) &&
                        (typeargtypes == null || !Type.isErroneous(typeargtypes));
        }
        public List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes) {
            return (syms.operatorNames.contains(name)) ?
                    argtypes :
                    Type.map(argtypes, new ResolveDeferredRecoveryMap(accessedSym));
        }

        class ResolveDeferredRecoveryMap extends DeferredAttr.RecoveryDeferredTypeMap {

            public ResolveDeferredRecoveryMap(Symbol msym) {
                deferredAttr.super(AttrMode.SPECULATIVE, msym, currentResolutionContext.step);
            }

            @Override
            protected Type typeOf(DeferredType dt) {
                Type res = super.typeOf(dt);
                if (!res.isErroneous()) {
                    switch (TreeInfo.skipParens(dt.tree).getTag()) {
                        case LAMBDA:
                        case REFERENCE:
                            return dt;
                        case CONDEXPR:
                            return res == Type.recoveryType ?
                                    dt : res;
                    }
                }
                return res;
            }
        }
    };

    /** Check that sym is not an abstract method.
     */
    void checkNonAbstract(DiagnosticPosition pos, Symbol sym) {
        if ((sym.flags() & ABSTRACT) != 0 && (sym.flags() & DEFAULT) == 0)
            log.error(pos, "abstract.cant.be.accessed.directly",
                      kindName(sym), sym, sym.location());
    }

/* ***************************************************************************
 *  Debugging
 ****************************************************************************/

    /** print all scopes starting with scope s and proceeding outwards.
     *  used for debugging.
     */
    public void printscopes(Scope s) {
        while (s != null) {
            if (s.owner != null)
                System.err.print(s.owner + ": ");
            for (Scope.Entry e = s.elems; e != null; e = e.sibling) {
                if ((e.sym.flags() & ABSTRACT) != 0)
                    System.err.print("abstract ");
                System.err.print(e.sym + " ");
            }
            System.err.println();
            s = s.next;
        }
    }

    void printscopes(Env<AttrContext> env) {
        while (env.outer != null) {
            System.err.println("------------------------------");
            printscopes(env.info.scope);
            env = env.outer;
        }
    }

    public void printscopes(Type t) {
        while (t.hasTag(CLASS)) {
            printscopes(t.tsym.members());
            t = types.supertype(t);
        }
    }

/* ***************************************************************************
 *  Name resolution
 *  Naming conventions are as for symbol lookup
 *  Unlike the find... methods these methods will report access errors
 ****************************************************************************/

    /** Resolve an unqualified (non-method) identifier.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the identifier use.
     *  @param name      The identifier's name.
     *  @param kind      The set of admissible symbol kinds for the identifier.
     */
    Symbol resolveIdent(DiagnosticPosition pos, Env<AttrContext> env,
                        Name name, int kind) {
        return accessBase(
            findIdent(env, name, kind),
            pos, env.enclClass.sym.type, name, false);
    }

    /** Resolve an unqualified method identifier.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    Symbol resolveMethod(DiagnosticPosition pos,
                         Env<AttrContext> env,
                         Name name,
                         List<Type> argtypes,
                         List<Type> typeargtypes) {
        return lookupMethod(env, pos, env.enclClass.sym, resolveMethodCheck,
                new BasicLookupHelper(name, env.enclClass.sym.type, argtypes, typeargtypes) {
                    @Override
                    Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                        return findFun(env, name, argtypes, typeargtypes,
                                phase.isBoxingRequired(),
                                phase.isVarargsRequired());
                    }});
    }

    /** Resolve a qualified method identifier
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    Symbol resolveQualifiedMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                  Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return resolveQualifiedMethod(pos, env, site.tsym, site, name, argtypes, typeargtypes);
    }
    Symbol resolveQualifiedMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                  Symbol location, Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }
    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext,
                                  DiagnosticPosition pos, Env<AttrContext> env,
                                  Symbol location, Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new BasicLookupHelper(name, site, argtypes, typeargtypes) {
            @Override
            Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                return findMethod(env, site, name, argtypes, typeargtypes,
                        phase.isBoxingRequired(),
                        phase.isVarargsRequired(), false);
            }
            @Override
            Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                if (sym.kind >= AMBIGUOUS) {
                    sym = super.access(env, pos, location, sym);
                } else if (allowMethodHandles) {
                    MethodSymbol msym = (MethodSymbol)sym;
                    if (msym.isSignaturePolymorphic(types)) {
                        return findPolymorphicSignatureInstance(env, sym, argtypes);
                    }
                }
                return sym;
            }
        });
    }

    /** Find or create an implicit method of exactly the given type (after erasure).
     *  Searches in a side table, not the main scope of the site.
     *  This emulates the lookup process required by JSR 292 in JVM.
     *  @param env       Attribution environment
     *  @param spMethod  signature polymorphic method - i.e. MH.invokeExact
     *  @param argtypes  The required argument types
     */
    Symbol findPolymorphicSignatureInstance(Env<AttrContext> env,
                                            final Symbol spMethod,
                                            List<Type> argtypes) {
        Type mtype = infer.instantiatePolymorphicSignatureInstance(env,
                (MethodSymbol)spMethod, currentResolutionContext, argtypes);
        for (Symbol sym : polymorphicSignatureScope.getElementsByName(spMethod.name)) {
            if (types.isSameType(mtype, sym.type)) {
               return sym;
            }
        }

        // create the desired method
        long flags = ABSTRACT | HYPOTHETICAL | spMethod.flags() & Flags.AccessFlags;
        Symbol msym = new MethodSymbol(flags, spMethod.name, mtype, spMethod.owner) {
            @Override
            public Symbol baseSymbol() {
                return spMethod;
            }
        };
        polymorphicSignatureScope.enter(msym);
        return msym;
    }

    /** Resolve a qualified method identifier, throw a fatal error if not
     *  found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    public MethodSymbol resolveInternalMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                        Type site, Name name,
                                        List<Type> argtypes,
                                        List<Type> typeargtypes) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.internalResolution = true;
        Symbol sym = resolveQualifiedMethod(resolveContext, pos, env, site.tsym,
                site, name, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.meth",
                                name));
    }

    /** Resolve constructor.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     */
    Symbol resolveConstructor(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        return resolveConstructor(new MethodResolutionContext(), pos, env, site, argtypes, typeargtypes);
    }

    private Symbol resolveConstructor(MethodResolutionContext resolveContext,
                              final DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        return lookupMethod(env, pos, site.tsym, resolveContext, new BasicLookupHelper(names.init, site, argtypes, typeargtypes) {
            @Override
            Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                return findConstructor(pos, env, site, argtypes, typeargtypes,
                        phase.isBoxingRequired(),
                        phase.isVarargsRequired());
            }
        });
    }

    /** Resolve a constructor, throw a fatal error if not found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type to be constructed.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    public MethodSymbol resolveInternalConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                                        Type site,
                                        List<Type> argtypes,
                                        List<Type> typeargtypes) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.internalResolution = true;
        Symbol sym = resolveConstructor(resolveContext, pos, env, site, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.ctor", site));
    }

    Symbol findConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                              Type site, List<Type> argtypes,
                              List<Type> typeargtypes,
                              boolean allowBoxing,
                              boolean useVarargs) {
        Symbol sym = findMethod(env, site,
                                    names.init, argtypes,
                                    typeargtypes, allowBoxing,
                                    useVarargs, false);
        chk.checkDeprecated(pos, env.info.scope.owner, sym);
        return sym;
    }

    /** Resolve constructor using diamond inference.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *                   The scope of this class has been touched in attribution.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     */
    Symbol resolveDiamond(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        return lookupMethod(env, pos, site.tsym, resolveMethodCheck,
                new BasicLookupHelper(names.init, site, argtypes, typeargtypes) {
                    @Override
                    Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                        return findDiamond(env, site, argtypes, typeargtypes,
                                phase.isBoxingRequired(),
                                phase.isVarargsRequired());
                    }
                    @Override
                    Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                        if (sym.kind >= AMBIGUOUS) {
                            final JCDiagnostic details = sym.kind == WRONG_MTH ?
                                            ((InapplicableSymbolError)sym).errCandidate().details :
                                            null;
                            sym = new InapplicableSymbolError(sym.kind, "diamondError", currentResolutionContext) {
                                @Override
                                JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos,
                                        Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
                                    String key = details == null ?
                                        "cant.apply.diamond" :
                                        "cant.apply.diamond.1";
                                    return diags.create(dkind, log.currentSource(), pos, key,
                                            diags.fragment("diamond", site.tsym), details);
                                }
                            };
                            sym = accessMethod(sym, pos, site, names.init, true, argtypes, typeargtypes);
                            env.info.pendingResolutionPhase = currentResolutionContext.step;
                        }
                        return sym;
                    }});
    }

    /** This method scans all the constructor symbol in a given class scope -
     *  assuming that the original scope contains a constructor of the kind:
     *  {@code Foo(X x, Y y)}, where X,Y are class type-variables declared in Foo,
     *  a method check is executed against the modified constructor type:
     *  {@code <X,Y>Foo<X,Y>(X x, Y y)}. This is crucial in order to enable diamond
     *  inference. The inferred return type of the synthetic constructor IS
     *  the inferred type for the diamond operator.
     */
    private Symbol findDiamond(Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              boolean allowBoxing,
                              boolean useVarargs) {
        Symbol bestSoFar = methodNotFound;
        for (Scope.Entry e = site.tsym.members().lookup(names.init);
             e.scope != null;
             e = e.next()) {
            final Symbol sym = e.sym;
            //- System.out.println(" e " + e.sym);
            if (sym.kind == MTH &&
                (sym.flags_field & SYNTHETIC) == 0) {
                    List<Type> oldParams = e.sym.type.hasTag(FORALL) ?
                            ((ForAll)sym.type).tvars :
                            List.<Type>nil();
                    Type constrType = new ForAll(site.tsym.type.getTypeArguments().appendList(oldParams),
                            types.createMethodTypeWithReturn(sym.type.asMethodType(), site));
                    MethodSymbol newConstr = new MethodSymbol(sym.flags(), names.init, constrType, site.tsym) {
                        @Override
                        public Symbol baseSymbol() {
                            return sym;
                        }
                    };
                    bestSoFar = selectBest(env, site, argtypes, typeargtypes,
                            newConstr,
                            bestSoFar,
                            allowBoxing,
                            useVarargs,
                            false);
            }
        }
        return bestSoFar;
    }



    /** Resolve operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param argtypes  The types of the operands.
     */
    Symbol resolveOperator(DiagnosticPosition pos, JCTree.Tag optag,
                           Env<AttrContext> env, List<Type> argtypes) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            Name name = treeinfo.operatorName(optag);
            return lookupMethod(env, pos, syms.predefClass, currentResolutionContext,
                    new BasicLookupHelper(name, syms.predefClass.type, argtypes, null, BOX) {
                @Override
                Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                    return findMethod(env, site, name, argtypes, typeargtypes,
                            phase.isBoxingRequired(),
                            phase.isVarargsRequired(), true);
                }
                @Override
                Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                    return accessMethod(sym, pos, env.enclClass.sym.type, name,
                          false, argtypes, null);
                }
            });
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    /** Resolve operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param arg       The type of the operand.
     */
    Symbol resolveUnaryOperator(DiagnosticPosition pos, JCTree.Tag optag, Env<AttrContext> env, Type arg) {
        return resolveOperator(pos, optag, env, List.of(arg));
    }

    /** Resolve binary operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param left      The types of the left operand.
     *  @param right     The types of the right operand.
     */
    Symbol resolveBinaryOperator(DiagnosticPosition pos,
                                 JCTree.Tag optag,
                                 Env<AttrContext> env,
                                 Type left,
                                 Type right) {
        return resolveOperator(pos, optag, env, List.of(left, right));
    }

    /**
     * Resolution of member references is typically done as a single
     * overload resolution step, where the argument types A are inferred from
     * the target functional descriptor.
     *
     * If the member reference is a method reference with a type qualifier,
     * a two-step lookup process is performed. The first step uses the
     * expected argument list A, while the second step discards the first
     * type from A (which is treated as a receiver type).
     *
     * There are two cases in which inference is performed: (i) if the member
     * reference is a constructor reference and the qualifier type is raw - in
     * which case diamond inference is used to infer a parameterization for the
     * type qualifier; (ii) if the member reference is an unbound reference
     * where the type qualifier is raw - in that case, during the unbound lookup
     * the receiver argument type is used to infer an instantiation for the raw
     * qualifier type.
     *
     * When a multi-step resolution process is exploited, it is an error
     * if two candidates are found (ambiguity).
     *
     * This routine returns a pair (T,S), where S is the member reference symbol,
     * and T is the type of the class in which S is defined. This is necessary as
     * the type T might be dynamically inferred (i.e. if constructor reference
     * has a raw qualifier).
     */
    Pair<Symbol, ReferenceLookupHelper> resolveMemberReference(DiagnosticPosition pos,
                                  Env<AttrContext> env,
                                  JCMemberReference referenceTree,
                                  Type site,
                                  Name name, List<Type> argtypes,
                                  List<Type> typeargtypes,
                                  boolean boxingAllowed,
                                  MethodCheck methodCheck) {
        MethodResolutionPhase maxPhase = boxingAllowed ? VARARITY : BASIC;

        ReferenceLookupHelper boundLookupHelper;
        if (!name.equals(names.init)) {
            //method reference
            boundLookupHelper =
                    new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        } else if (site.hasTag(ARRAY)) {
            //array constructor reference
            boundLookupHelper =
                    new ArrayConstructorReferenceLookupHelper(referenceTree, site, argtypes, typeargtypes, maxPhase);
        } else {
            //class constructor reference
            boundLookupHelper =
                    new ConstructorReferenceLookupHelper(referenceTree, site, argtypes, typeargtypes, maxPhase);
        }

        //step 1 - bound lookup
        Env<AttrContext> boundEnv = env.dup(env.tree, env.info.dup());
        Symbol boundSym = lookupMethod(boundEnv, env.tree.pos(), site.tsym, methodCheck, boundLookupHelper);

        //step 2 - unbound lookup
        ReferenceLookupHelper unboundLookupHelper = boundLookupHelper.unboundLookup();
        Env<AttrContext> unboundEnv = env.dup(env.tree, env.info.dup());
        Symbol unboundSym = lookupMethod(unboundEnv, env.tree.pos(), site.tsym, methodCheck, unboundLookupHelper);

        //merge results
        Pair<Symbol, ReferenceLookupHelper> res;
        if (!lookupSuccess(unboundSym)) {
            res = new Pair<Symbol, ReferenceLookupHelper>(boundSym, boundLookupHelper);
            env.info.pendingResolutionPhase = boundEnv.info.pendingResolutionPhase;
        } else if (lookupSuccess(boundSym)) {
            res = new Pair<Symbol, ReferenceLookupHelper>(ambiguityError(boundSym, unboundSym), boundLookupHelper);
            env.info.pendingResolutionPhase = boundEnv.info.pendingResolutionPhase;
        } else {
            res = new Pair<Symbol, ReferenceLookupHelper>(unboundSym, unboundLookupHelper);
            env.info.pendingResolutionPhase = unboundEnv.info.pendingResolutionPhase;
        }

        return res;
    }
    //private
        boolean lookupSuccess(Symbol s) {
            return s.kind == MTH || s.kind == AMBIGUOUS;
        }

    /**
     * Helper for defining custom method-like lookup logic; a lookup helper
     * provides hooks for (i) the actual lookup logic and (ii) accessing the
     * lookup result (this step might result in compiler diagnostics to be generated)
     */
    abstract class LookupHelper {

        /** name of the symbol to lookup */
        Name name;

        /** location in which the lookup takes place */
        Type site;

        /** actual types used during the lookup */
        List<Type> argtypes;

        /** type arguments used during the lookup */
        List<Type> typeargtypes;

        /** Max overload resolution phase handled by this helper */
        MethodResolutionPhase maxPhase;

        LookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            this.name = name;
            this.site = site;
            this.argtypes = argtypes;
            this.typeargtypes = typeargtypes;
            this.maxPhase = maxPhase;
        }

        /**
         * Should lookup stop at given phase with given result
         */
        protected boolean shouldStop(Symbol sym, MethodResolutionPhase phase) {
            return phase.ordinal() > maxPhase.ordinal() ||
                    sym.kind < ERRONEOUS || sym.kind == AMBIGUOUS;
        }

        /**
         * Search for a symbol under a given overload resolution phase - this method
         * is usually called several times, once per each overload resolution phase
         */
        abstract Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase);

        /**
         * Validate the result of the lookup
         */
        abstract Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym);
    }

    abstract class BasicLookupHelper extends LookupHelper {

        BasicLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            this(name, site, argtypes, typeargtypes, MethodResolutionPhase.VARARITY);
        }

        BasicLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
            if (sym.kind == AMBIGUOUS) {
                AmbiguityError a_err = (AmbiguityError)sym;
                sym = a_err.mergeAbstracts(site);
            }
            if (sym.kind >= AMBIGUOUS) {
                //if nothing is found return the 'first' error
                sym = accessMethod(sym, pos, location, site, name, true, argtypes, typeargtypes);
            }
            return sym;
        }
    }

    /**
     * Helper class for member reference lookup. A reference lookup helper
     * defines the basic logic for member reference lookup; a method gives
     * access to an 'unbound' helper used to perform an unbound member
     * reference lookup.
     */
    abstract class ReferenceLookupHelper extends LookupHelper {

        /** The member reference tree */
        JCMemberReference referenceTree;

        ReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(name, site, argtypes, typeargtypes, maxPhase);
            this.referenceTree = referenceTree;

        }

        /**
         * Returns an unbound version of this lookup helper. By default, this
         * method returns an dummy lookup helper.
         */
        ReferenceLookupHelper unboundLookup() {
            //dummy loopkup helper that always return 'methodNotFound'
            return new ReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase) {
                @Override
                ReferenceLookupHelper unboundLookup() {
                    return this;
                }
                @Override
                Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                    return methodNotFound;
                }
                @Override
                ReferenceKind referenceKind(Symbol sym) {
                    Assert.error();
                    return null;
                }
            };
        }

        /**
         * Get the kind of the member reference
         */
        abstract JCMemberReference.ReferenceKind referenceKind(Symbol sym);

        Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
            if (sym.kind == AMBIGUOUS) {
                AmbiguityError a_err = (AmbiguityError)sym;
                sym = a_err.mergeAbstracts(site);
            }
            //skip error reporting
            return sym;
        }
    }

    /**
     * Helper class for method reference lookup. The lookup logic is based
     * upon Resolve.findMethod; in certain cases, this helper class has a
     * corresponding unbound helper class (see UnboundMethodReferenceLookupHelper).
     * In such cases, non-static lookup results are thrown away.
     */
    class MethodReferenceLookupHelper extends ReferenceLookupHelper {

        MethodReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        final Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            return findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
        }

        @Override
        ReferenceLookupHelper unboundLookup() {
            if (TreeInfo.isStaticSelector(referenceTree.expr, names) &&
                    argtypes.nonEmpty() &&
                    (argtypes.head.hasTag(NONE) || types.isSubtypeUnchecked(argtypes.head, site))) {
                return new UnboundMethodReferenceLookupHelper(referenceTree, name,
                        site, argtypes, typeargtypes, maxPhase);
            } else {
                return super.unboundLookup();
            }
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            if (sym.isStatic()) {
                return ReferenceKind.STATIC;
            } else {
                Name selName = TreeInfo.name(referenceTree.getQualifierExpression());
                return selName != null && selName == names._super ?
                        ReferenceKind.SUPER :
                        ReferenceKind.BOUND;
            }
        }
    }

    /**
     * Helper class for unbound method reference lookup. Essentially the same
     * as the basic method reference lookup helper; main difference is that static
     * lookup results are thrown away. If qualifier type is raw, an attempt to
     * infer a parameterized type is made using the first actual argument (that
     * would otherwise be ignored during the lookup).
     */
    class UnboundMethodReferenceLookupHelper extends MethodReferenceLookupHelper {

        UnboundMethodReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes.tail, typeargtypes, maxPhase);
            if (site.isRaw() && !argtypes.head.hasTag(NONE)) {
                Type asSuperSite = types.asSuper(argtypes.head, site.tsym);
                this.site = asSuperSite;
            }
        }

        @Override
        ReferenceLookupHelper unboundLookup() {
            return this;
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return ReferenceKind.UNBOUND;
        }
    }

    /**
     * Helper class for array constructor lookup; an array constructor lookup
     * is simulated by looking up a method that returns the array type specified
     * as qualifier, and that accepts a single int parameter (size of the array).
     */
    class ArrayConstructorReferenceLookupHelper extends ReferenceLookupHelper {

        ArrayConstructorReferenceLookupHelper(JCMemberReference referenceTree, Type site, List<Type> argtypes,
                List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, names.init, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        protected Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            Scope sc = new Scope(syms.arrayClass);
            MethodSymbol arrayConstr = new MethodSymbol(PUBLIC, name, null, site.tsym);
            arrayConstr.type = new MethodType(List.of(syms.intType), site, List.<Type>nil(), syms.methodClass);
            sc.enter(arrayConstr);
            return findMethodInScope(env, site, name, argtypes, typeargtypes, sc, methodNotFound, phase.isBoxingRequired(), phase.isVarargsRequired(), false, false);
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return ReferenceKind.ARRAY_CTOR;
        }
    }

    /**
     * Helper class for constructor reference lookup. The lookup logic is based
     * upon either Resolve.findMethod or Resolve.findDiamond - depending on
     * whether the constructor reference needs diamond inference (this is the case
     * if the qualifier type is raw). A special erroneous symbol is returned
     * if the lookup returns the constructor of an inner class and there's no
     * enclosing instance in scope.
     */
    class ConstructorReferenceLookupHelper extends ReferenceLookupHelper {

        boolean needsInference;

        ConstructorReferenceLookupHelper(JCMemberReference referenceTree, Type site, List<Type> argtypes,
                List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, names.init, site, argtypes, typeargtypes, maxPhase);
            if (site.isRaw()) {
                this.site = new ClassType(site.getEnclosingType(), site.tsym.type.getTypeArguments(), site.tsym);
                needsInference = true;
            }
        }

        @Override
        protected Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            Symbol sym = needsInference ?
                findDiamond(env, site, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired()) :
                findMethod(env, site, name, argtypes, typeargtypes,
                        phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
            return sym.kind != MTH ||
                          site.getEnclosingType().hasTag(NONE) ||
                          hasEnclosingInstance(env, site) ?
                          sym : new InvalidSymbolError(Kinds.MISSING_ENCL, sym, null) {
                    @Override
                    JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
                       return diags.create(dkind, log.currentSource(), pos,
                            "cant.access.inner.cls.constr", site.tsym.name, argtypes, site.getEnclosingType());
                    }
                };
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return site.getEnclosingType().hasTag(NONE) ?
                    ReferenceKind.TOPLEVEL : ReferenceKind.IMPLICIT_INNER;
        }
    }

    /**
     * Main overload resolution routine. On each overload resolution step, a
     * lookup helper class is used to perform the method/constructor lookup;
     * at the end of the lookup, the helper is used to validate the results
     * (this last step might trigger overload resolution diagnostics).
     */
    Symbol lookupMethod(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, MethodCheck methodCheck, LookupHelper lookupHelper) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.methodCheck = methodCheck;
        return lookupMethod(env, pos, location, resolveContext, lookupHelper);
    }

    Symbol lookupMethod(Env<AttrContext> env, DiagnosticPosition pos, Symbol location,
            MethodResolutionContext resolveContext, LookupHelper lookupHelper) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            Symbol bestSoFar = methodNotFound;
            currentResolutionContext = resolveContext;
            for (MethodResolutionPhase phase : methodResolutionSteps) {
                if (!phase.isApplicable(boxingEnabled, varargsEnabled) ||
                        lookupHelper.shouldStop(bestSoFar, phase)) break;
                MethodResolutionPhase prevPhase = currentResolutionContext.step;
                Symbol prevBest = bestSoFar;
                currentResolutionContext.step = phase;
                bestSoFar = phase.mergeResults(bestSoFar, lookupHelper.lookup(env, phase));
                env.info.pendingResolutionPhase = (prevBest == bestSoFar) ? prevPhase : phase;
            }
            return lookupHelper.access(env, pos, location, bestSoFar);
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    /**
     * Resolve `c.name' where name == this or name == super.
     * @param pos           The position to use for error reporting.
     * @param env           The environment current at the expression.
     * @param c             The qualifier.
     * @param name          The identifier's name.
     */
    Symbol resolveSelf(DiagnosticPosition pos,
                       Env<AttrContext> env,
                       TypeSymbol c,
                       Name name) {
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            if (env1.enclClass.sym == c) {
                Symbol sym = env1.info.scope.lookup(name).sym;
                if (sym != null) {
                    if (staticOnly) sym = new StaticError(sym);
                    return accessBase(sym, pos, env.enclClass.sym.type,
                                  name, true);
                }
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }
        if (allowDefaultMethods && c.isInterface() &&
                name == names._super && !isStatic(env) &&
                types.isDirectSuperInterface(c, env.enclClass.sym)) {
            //this might be a default super call if one of the superinterfaces is 'c'
            for (Type t : pruneInterfaces(env.enclClass.type)) {
                if (t.tsym == c) {
                    env.info.defaultSuperCallSite = t;
                    return new VarSymbol(0, names._super,
                            types.asSuper(env.enclClass.type, c), env.enclClass.sym);
                }
            }
            //find a direct superinterface that is a subtype of 'c'
            for (Type i : types.interfaces(env.enclClass.type)) {
                if (i.tsym.isSubClass(c, types) && i.tsym != c) {
                    log.error(pos, "illegal.default.super.call", c,
                            diags.fragment("redundant.supertype", c, i));
                    return syms.errSymbol;
                }
            }
            Assert.error();
        }
        log.error(pos, "not.encl.class", c);
        return syms.errSymbol;
    }
    //where
    private List<Type> pruneInterfaces(Type t) {
        ListBuffer<Type> result = ListBuffer.lb();
        for (Type t1 : types.interfaces(t)) {
            boolean shouldAdd = true;
            for (Type t2 : types.interfaces(t)) {
                if (t1 != t2 && types.isSubtypeNoCapture(t2, t1)) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                result.append(t1);
            }
        }
        return result.toList();
    }


    /**
     * Resolve `c.this' for an enclosing class c that contains the
     * named member.
     * @param pos           The position to use for error reporting.
     * @param env           The environment current at the expression.
     * @param member        The member that must be contained in the result.
     */
    Symbol resolveSelfContaining(DiagnosticPosition pos,
                                 Env<AttrContext> env,
                                 Symbol member,
                                 boolean isSuperCall) {
        Symbol sym = resolveSelfContainingInternal(env, member, isSuperCall);
        if (sym == null) {
            log.error(pos, "encl.class.required", member);
            return syms.errSymbol;
        } else {
            return accessBase(sym, pos, env.enclClass.sym.type, sym.name, true);
        }
    }

    boolean hasEnclosingInstance(Env<AttrContext> env, Type type) {
        Symbol encl = resolveSelfContainingInternal(env, type.tsym, false);
        return encl != null && encl.kind < ERRONEOUS;
    }

    private Symbol resolveSelfContainingInternal(Env<AttrContext> env,
                                 Symbol member,
                                 boolean isSuperCall) {
        Name name = names._this;
        Env<AttrContext> env1 = isSuperCall ? env.outer : env;
        boolean staticOnly = false;
        if (env1 != null) {
            while (env1 != null && env1.outer != null) {
                if (isStatic(env1)) staticOnly = true;
                if (env1.enclClass.sym.isSubClass(member.owner, types)) {
                    Symbol sym = env1.info.scope.lookup(name).sym;
                    if (sym != null) {
                        if (staticOnly) sym = new StaticError(sym);
                        return sym;
                    }
                }
                if ((env1.enclClass.sym.flags() & STATIC) != 0)
                    staticOnly = true;
                env1 = env1.outer;
            }
        }
        return null;
    }

    /**
     * Resolve an appropriate implicit this instance for t's container.
     * JLS 8.8.5.1 and 15.9.2
     */
    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t) {
        return resolveImplicitThis(pos, env, t, false);
    }

    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t, boolean isSuperCall) {
        Type thisType = (((t.tsym.owner.kind & (MTH|VAR)) != 0)
                         ? resolveSelf(pos, env, t.getEnclosingType().tsym, names._this)
                         : resolveSelfContaining(pos, env, t.tsym, isSuperCall)).type;
        if (env.info.isSelfCall && thisType.tsym == env.enclClass.sym)
            log.error(pos, "cant.ref.before.ctor.called", "this");
        return thisType;
    }

/* ***************************************************************************
 *  ResolveError classes, indicating error situations when accessing symbols
 ****************************************************************************/

    //used by TransTypes when checking target type of synthetic cast
    public void logAccessErrorInternal(Env<AttrContext> env, JCTree tree, Type type) {
        AccessError error = new AccessError(env, env.enclClass.type, type.tsym);
        logResolveError(error, tree.pos(), env.enclClass.sym, env.enclClass.type, null, null, null);
    }
    //where
    private void logResolveError(ResolveError error,
            DiagnosticPosition pos,
            Symbol location,
            Type site,
            Name name,
            List<Type> argtypes,
            List<Type> typeargtypes) {
        JCDiagnostic d = error.getDiagnostic(JCDiagnostic.DiagnosticType.ERROR,
                pos, location, site, name, argtypes, typeargtypes);
        if (d != null) {
            d.setFlag(DiagnosticFlag.RESOLVE_ERROR);
            log.report(d);
        }
    }

    private final LocalizedString noArgs = new LocalizedString("compiler.misc.no.args");

    public Object methodArguments(List<Type> argtypes) {
        if (argtypes == null || argtypes.isEmpty()) {
            return noArgs;
        } else {
            ListBuffer<Object> diagArgs = ListBuffer.lb();
            for (Type t : argtypes) {
                if (t.hasTag(DEFERRED)) {
                    diagArgs.append(((DeferredAttr.DeferredType)t).tree);
                } else {
                    diagArgs.append(t);
                }
            }
            return diagArgs;
        }
    }

    /**
     * Root class for resolution errors. Subclass of ResolveError
     * represent a different kinds of resolution error - as such they must
     * specify how they map into concrete compiler diagnostics.
     */
    abstract class ResolveError extends Symbol {

        /** The name of the kind of error, for debugging only. */
        final String debugName;

        ResolveError(int kind, String debugName) {
            super(kind, 0, null, null, null);
            this.debugName = debugName;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            throw new AssertionError();
        }

        @Override
        public String toString() {
            return debugName;
        }

        @Override
        public boolean exists() {
            return false;
        }

        /**
         * Create an external representation for this erroneous symbol to be
         * used during attribution - by default this returns the symbol of a
         * brand new error type which stores the original type found
         * during resolution.
         *
         * @param name     the name used during resolution
         * @param location the location from which the symbol is accessed
         */
        protected Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }

        /**
         * Create a diagnostic representing this resolution error.
         *
         * @param dkind     The kind of the diagnostic to be created (e.g error).
         * @param pos       The position to be used for error reporting.
         * @param site      The original type from where the selection took place.
         * @param name      The name of the symbol to be resolved.
         * @param argtypes  The invocation's value arguments,
         *                  if we looked for a method.
         * @param typeargtypes  The invocation's type arguments,
         *                      if we looked for a method.
         */
        abstract JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes);
    }

    /**
     * This class is the root class of all resolution errors caused by
     * an invalid symbol being found during resolution.
     */
    abstract class InvalidSymbolError extends ResolveError {

        /** The invalid symbol found during resolution */
        Symbol sym;

        InvalidSymbolError(int kind, Symbol sym, String debugName) {
            super(kind, debugName);
            this.sym = sym;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String toString() {
             return super.toString() + " wrongSym=" + sym;
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            if ((sym.kind & ERRONEOUS) == 0 && (sym.kind & TYP) != 0)
                return types.createErrorType(name, location, sym.type).tsym;
            else
                return sym;
        }
    }

    /**
     * InvalidSymbolError error class indicating that a symbol matching a
     * given name does not exists in a given site.
     */
    class SymbolNotFoundError extends ResolveError {

        SymbolNotFoundError(int kind) {
            super(kind, "symbol not found error");
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            argtypes = argtypes == null ? List.<Type>nil() : argtypes;
            typeargtypes = typeargtypes == null ? List.<Type>nil() : typeargtypes;
            if (name == names.error)
                return null;

            if (syms.operatorNames.contains(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                    "operator.cant.be.applied" :
                    "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            }
            boolean hasLocation = false;
            if (location == null) {
                location = site.tsym;
            }
            if (!location.name.isEmpty()) {
                if (location.kind == PCK && !site.tsym.exists()) {
                    return diags.create(dkind, log.currentSource(), pos,
                        "doesnt.exist", location);
                }
                hasLocation = !location.name.equals(names._this) &&
                        !location.name.equals(names._super);
            }
            boolean isConstructor = kind == ABSENT_MTH && name == names.init;
            KindName kindname = isConstructor ? KindName.CONSTRUCTOR : absentKind(kind);
            Name idname = isConstructor ? site.tsym.name : name;
            String errKey = getErrorKey(kindname, typeargtypes.nonEmpty(), hasLocation);
            if (hasLocation) {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname, //symbol kindname, name
                        typeargtypes, args(argtypes), //type parameters and arguments (if any)
                        getLocationDiag(location, site)); //location kindname, type
            }
            else {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname, //symbol kindname, name
                        typeargtypes, args(argtypes)); //type parameters and arguments (if any)
            }
        }
        //where
        private Object args(List<Type> args) {
            return args.isEmpty() ? args : methodArguments(args);
        }

        private String getErrorKey(KindName kindname, boolean hasTypeArgs, boolean hasLocation) {
            String key = "cant.resolve";
            String suffix = hasLocation ? ".location" : "";
            switch (kindname) {
                case METHOD:
                case CONSTRUCTOR: {
                    suffix += ".args";
                    suffix += hasTypeArgs ? ".params" : "";
                }
            }
            return key + suffix;
        }
        private JCDiagnostic getLocationDiag(Symbol location, Type site) {
            if (location.kind == VAR) {
                return diags.fragment("location.1",
                    kindName(location),
                    location,
                    location.type);
            } else {
                return diags.fragment("location",
                    typeKindName(site),
                    site,
                    null);
            }
        }
    }

    /**
     * InvalidSymbolError error class indicating that a given symbol
     * (either a method, a constructor or an operand) is not applicable
     * given an actual arguments/type argument list.
     */
    class InapplicableSymbolError extends ResolveError {

        protected MethodResolutionContext resolveContext;

        InapplicableSymbolError(MethodResolutionContext context) {
            this(WRONG_MTH, "inapplicable symbol error", context);
        }

        protected InapplicableSymbolError(int kind, String debugName, MethodResolutionContext context) {
            super(kind, debugName);
            this.resolveContext = context;
        }

        @Override
        public String toString() {
            return super.toString();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            if (name == names.error)
                return null;

            if (syms.operatorNames.contains(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                    "operator.cant.be.applied" :
                    "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            }
            else {
                Candidate c = errCandidate();
                if (compactMethodDiags) {
                    for (Map.Entry<Template, DiagnosticRewriter> _entry :
                            MethodResolutionDiagHelper.rewriters.entrySet()) {
                        if (_entry.getKey().matches(c.details)) {
                            JCDiagnostic simpleDiag =
                                    _entry.getValue().rewriteDiagnostic(diags, pos,
                                        log.currentSource(), dkind, c.details);
                            simpleDiag.setFlag(DiagnosticFlag.COMPRESSED);
                            return simpleDiag;
                        }
                    }
                }
                Symbol ws = c.sym.asMemberOf(site, types);
                return diags.create(dkind, log.currentSource(), pos,
                          "cant.apply.symbol",
                          kindName(ws),
                          ws.name == names.init ? ws.owner.name : ws.name,
                          methodArguments(ws.type.getParameterTypes()),
                          methodArguments(argtypes),
                          kindName(ws.owner),
                          ws.owner.type,
                          c.details);
            }
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }

        private Candidate errCandidate() {
            Candidate bestSoFar = null;
            for (Candidate c : resolveContext.candidates) {
                if (c.isApplicable()) continue;
                bestSoFar = c;
            }
            Assert.checkNonNull(bestSoFar);
            return bestSoFar;
        }
    }

    /**
     * ResolveError error class indicating that a set of symbols
     * (either methods, constructors or operands) is not applicable
     * given an actual arguments/type argument list.
     */
    class InapplicableSymbolsError extends InapplicableSymbolError {

        InapplicableSymbolsError(MethodResolutionContext context) {
            super(WRONG_MTHS, "inapplicable symbols", context);
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            Map<Symbol, JCDiagnostic> candidatesMap = mapCandidates();
            Map<Symbol, JCDiagnostic> filteredCandidates = filterCandidates(candidatesMap);
            if (filteredCandidates.isEmpty()) {
                filteredCandidates = candidatesMap;
            }
            boolean truncatedDiag = candidatesMap.size() != filteredCandidates.size();
            if (filteredCandidates.size() > 1) {
                JCDiagnostic err = diags.create(dkind,
                        null,
                        truncatedDiag ?
                            EnumSet.of(DiagnosticFlag.COMPRESSED) :
                            EnumSet.noneOf(DiagnosticFlag.class),
                        log.currentSource(),
                        pos,
                        "cant.apply.symbols",
                        name == names.init ? KindName.CONSTRUCTOR : absentKind(kind),
                        name == names.init ? site.tsym.name : name,
                        methodArguments(argtypes));
                return new JCDiagnostic.MultilineDiagnostic(err, candidateDetails(filteredCandidates, site));
            } else if (filteredCandidates.size() == 1) {
                JCDiagnostic d =  new InapplicableSymbolError(resolveContext).getDiagnostic(dkind, pos,
                    location, site, name, argtypes, typeargtypes);
                if (truncatedDiag) {
                    d.setFlag(DiagnosticFlag.COMPRESSED);
                }
                return d;
            } else {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind, pos,
                    location, site, name, argtypes, typeargtypes);
            }
        }
        //where
            private Map<Symbol, JCDiagnostic> mapCandidates() {
                Map<Symbol, JCDiagnostic> candidates = new LinkedHashMap<Symbol, JCDiagnostic>();
                for (Candidate c : resolveContext.candidates) {
                    if (c.isApplicable()) continue;
                    candidates.put(c.sym, c.details);
                }
                return candidates;
            }

            Map<Symbol, JCDiagnostic> filterCandidates(Map<Symbol, JCDiagnostic> candidatesMap) {
                Map<Symbol, JCDiagnostic> candidates = new LinkedHashMap<Symbol, JCDiagnostic>();
                for (Map.Entry<Symbol, JCDiagnostic> _entry : candidatesMap.entrySet()) {
                    JCDiagnostic d = _entry.getValue();
                    if (!compactMethodDiags ||
                            !new Template(MethodCheckDiag.ARITY_MISMATCH.regex()).matches(d)) {
                        candidates.put(_entry.getKey(), d);
                    }
                }
                return candidates;
            }

            private List<JCDiagnostic> candidateDetails(Map<Symbol, JCDiagnostic> candidatesMap, Type site) {
                List<JCDiagnostic> details = List.nil();
                for (Map.Entry<Symbol, JCDiagnostic> _entry : candidatesMap.entrySet()) {
                    Symbol sym = _entry.getKey();
                    JCDiagnostic detailDiag = diags.fragment("inapplicable.method",
                            Kinds.kindName(sym),
                            sym.location(site, types),
                            sym.asMemberOf(site, types),
                            _entry.getValue());
                    details = details.prepend(detailDiag);
                }
                //typically members are visited in reverse order (see Scope)
                //so we need to reverse the candidate list so that candidates
                //conform to source order
                return details;
            }
    }

    /**
     * An InvalidSymbolError error class indicating that a symbol is not
     * accessible from a given site
     */
    class AccessError extends InvalidSymbolError {

        private Env<AttrContext> env;
        private Type site;

        AccessError(Symbol sym) {
            this(null, null, sym);
        }

        AccessError(Env<AttrContext> env, Type site, Symbol sym) {
            super(HIDDEN, sym, "access error");
            this.env = env;
            this.site = site;
            if (debugResolve)
                log.error("proc.messager", sym + " @ " + site + " is inaccessible.");
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            if (sym.owner.type.hasTag(ERROR))
                return null;

            if (sym.name == names.init && sym.owner != site.tsym) {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind,
                        pos, location, site, name, argtypes, typeargtypes);
            }
            else if ((sym.flags() & PUBLIC) != 0
                || (env != null && this.site != null
                    && !isAccessible(env, this.site))) {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.access.class.intf.cant.access",
                    sym, sym.location());
            }
            else if ((sym.flags() & (PRIVATE | PROTECTED)) != 0) {
                return diags.create(dkind, log.currentSource(),
                        pos, "report.access", sym,
                        asFlagSet(sym.flags() & (PRIVATE | PROTECTED)),
                        sym.location());
            }
            else {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.public.cant.access", sym, sym.location());
            }
        }
    }

    /**
     * InvalidSymbolError error class indicating that an instance member
     * has erroneously been accessed from a static context.
     */
    class StaticError extends InvalidSymbolError {

        StaticError(Symbol sym) {
            super(STATICERR, sym, "static error");
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            Symbol errSym = ((sym.kind == TYP && sym.type.hasTag(CLASS))
                ? types.erasure(sym.type).tsym
                : sym);
            return diags.create(dkind, log.currentSource(), pos,
                    "non-static.cant.be.ref", kindName(sym), errSym);
        }
    }

    /**
     * InvalidSymbolError error class indicating that a pair of symbols
     * (either methods, constructors or operands) are ambiguous
     * given an actual arguments/type argument list.
     */
    class AmbiguityError extends ResolveError {

        /** The other maximally specific symbol */
        List<Symbol> ambiguousSyms = List.nil();

        @Override
        public boolean exists() {
            return true;
        }

        AmbiguityError(Symbol sym1, Symbol sym2) {
            super(AMBIGUOUS, "ambiguity error");
            ambiguousSyms = flatten(sym2).appendList(flatten(sym1));
        }

        private List<Symbol> flatten(Symbol sym) {
            if (sym.kind == AMBIGUOUS) {
                return ((AmbiguityError)sym).ambiguousSyms;
            } else {
                return List.of(sym);
            }
        }

        AmbiguityError addAmbiguousSymbol(Symbol s) {
            ambiguousSyms = ambiguousSyms.prepend(s);
            return this;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            List<Symbol> diagSyms = ambiguousSyms.reverse();
            Symbol s1 = diagSyms.head;
            Symbol s2 = diagSyms.tail.head;
            Name sname = s1.name;
            if (sname == names.init) sname = s1.owner.name;
            return diags.create(dkind, log.currentSource(),
                      pos, "ref.ambiguous", sname,
                      kindName(s1),
                      s1,
                      s1.location(site, types),
                      kindName(s2),
                      s2,
                      s2.location(site, types));
        }

        /**
         * If multiple applicable methods are found during overload and none of them
         * is more specific than the others, attempt to merge their signatures.
         */
        Symbol mergeAbstracts(Type site) {
            Symbol fst = ambiguousSyms.last();
            Symbol res = fst;
            for (Symbol s : ambiguousSyms.reverse()) {
                Type mt1 = types.memberType(site, res);
                Type mt2 = types.memberType(site, s);
                if ((s.flags() & ABSTRACT) == 0 ||
                        !types.overrideEquivalent(mt1, mt2) ||
                        !types.isSameTypes(fst.erasure(types).getParameterTypes(),
                                       s.erasure(types).getParameterTypes())) {
                    //ambiguity cannot be resolved
                    return this;
                } else {
                    Type mst = mostSpecificReturnType(mt1, mt2);
                    if (mst == null) {
                        // Theoretically, this can't happen, but it is possible
                        // due to error recovery or mixing incompatible class files
                        return this;
                    }
                    Symbol mostSpecific = mst == mt1 ? res : s;
                    List<Type> allThrown = chk.intersect(mt1.getThrownTypes(), mt2.getThrownTypes());
                    Type newSig = types.createMethodTypeWithThrown(mostSpecific.type, allThrown);
                    res = new MethodSymbol(
                            mostSpecific.flags(),
                            mostSpecific.name,
                            newSig,
                            mostSpecific.owner);
                }
            }
            return res;
        }

        @Override
        protected Symbol access(Name name, TypeSymbol location) {
            Symbol firstAmbiguity = ambiguousSyms.last();
            return firstAmbiguity.kind == TYP ?
                    types.createErrorType(name, location, firstAmbiguity.type).tsym :
                    firstAmbiguity;
        }
    }

    class BadVarargsMethod extends ResolveError {

        ResolveError delegatedError;

        BadVarargsMethod(ResolveError delegatedError) {
            super(delegatedError.kind, "badVarargs");
            this.delegatedError = delegatedError;
        }

        @Override
        public Symbol baseSymbol() {
            return delegatedError.baseSymbol();
        }

        @Override
        protected Symbol access(Name name, TypeSymbol location) {
            return delegatedError.access(name, location);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
            return delegatedError.getDiagnostic(dkind, pos, location, site, name, argtypes, typeargtypes);
        }
    }

    /**
     * Helper class for method resolution diagnostic simplification.
     * Certain resolution diagnostic are rewritten as simpler diagnostic
     * where the enclosing resolution diagnostic (i.e. 'inapplicable method')
     * is stripped away, as it doesn't carry additional info. The logic
     * for matching a given diagnostic is given in terms of a template
     * hierarchy: a diagnostic template can be specified programmatically,
     * so that only certain diagnostics are matched. Each templete is then
     * associated with a rewriter object that carries out the task of rewtiting
     * the diagnostic to a simpler one.
     */
    static class MethodResolutionDiagHelper {

        /**
         * A diagnostic rewriter transforms a method resolution diagnostic
         * into a simpler one
         */
        interface DiagnosticRewriter {
            JCDiagnostic rewriteDiagnostic(JCDiagnostic.Factory diags,
                    DiagnosticPosition preferedPos, DiagnosticSource preferredSource,
                    DiagnosticType preferredKind, JCDiagnostic d);
        }

        /**
         * A diagnostic template is made up of two ingredients: (i) a regular
         * expression for matching a diagnostic key and (ii) a list of sub-templates
         * for matching diagnostic arguments.
         */
        static class Template {

            /** regex used to match diag key */
            String regex;

            /** templates used to match diagnostic args */
            Template[] subTemplates;

            Template(String key, Template... subTemplates) {
                this.regex = key;
                this.subTemplates = subTemplates;
            }

            /**
             * Returns true if the regex matches the diagnostic key and if
             * all diagnostic arguments are matches by corresponding sub-templates.
             */
            boolean matches(Object o) {
                JCDiagnostic d = (JCDiagnostic)o;
                Object[] args = d.getArgs();
                if (!d.getCode().matches(regex) ||
                        subTemplates.length != d.getArgs().length) {
                    return false;
                }
                for (int i = 0; i < args.length ; i++) {
                    if (!subTemplates[i].matches(args[i])) {
                        return false;
                    }
                }
                return true;
            }
        }

        /** a dummy template that match any diagnostic argument */
        static final Template skip = new Template("") {
            @Override
            boolean matches(Object d) {
                return true;
            }
        };

        /** rewriter map used for method resolution simplification */
        static final Map<Template, DiagnosticRewriter> rewriters =
                new LinkedHashMap<Template, DiagnosticRewriter>();

        static {
            String argMismatchRegex = MethodCheckDiag.ARG_MISMATCH.regex();
            rewriters.put(new Template(argMismatchRegex, new Template("(.*)(bad.arg.types.in.lambda)", skip, skip)),
                    new DiagnosticRewriter() {
                @Override
                public JCDiagnostic rewriteDiagnostic(JCDiagnostic.Factory diags,
                        DiagnosticPosition preferedPos, DiagnosticSource preferredSource,
                        DiagnosticType preferredKind, JCDiagnostic d) {
                    return (JCDiagnostic)((JCDiagnostic)d.getArgs()[0]).getArgs()[1];
                }
            });

            rewriters.put(new Template(argMismatchRegex, skip),
                    new DiagnosticRewriter() {
                @Override
                public JCDiagnostic rewriteDiagnostic(JCDiagnostic.Factory diags,
                        DiagnosticPosition preferedPos, DiagnosticSource preferredSource,
                        DiagnosticType preferredKind, JCDiagnostic d) {
                    JCDiagnostic cause = (JCDiagnostic)d.getArgs()[0];
                    return diags.create(preferredKind, preferredSource, d.getDiagnosticPosition(),
                            "prob.found.req", cause);
                }
            });
        }
    }

    enum MethodResolutionPhase {
        BASIC(false, false),
        BOX(true, false),
        VARARITY(true, true) {
            @Override
            public Symbol mergeResults(Symbol bestSoFar, Symbol sym) {
                switch (sym.kind) {
                    case WRONG_MTH:
                        return (bestSoFar.kind == WRONG_MTH || bestSoFar.kind == WRONG_MTHS) ?
                            bestSoFar :
                            sym;
                    case ABSENT_MTH:
                        return bestSoFar;
                    default:
                        return sym;
                }
            }
        };

        final boolean isBoxingRequired;
        final boolean isVarargsRequired;

        MethodResolutionPhase(boolean isBoxingRequired, boolean isVarargsRequired) {
           this.isBoxingRequired = isBoxingRequired;
           this.isVarargsRequired = isVarargsRequired;
        }

        public boolean isBoxingRequired() {
            return isBoxingRequired;
        }

        public boolean isVarargsRequired() {
            return isVarargsRequired;
        }

        public boolean isApplicable(boolean boxingEnabled, boolean varargsEnabled) {
            return (varargsEnabled || !isVarargsRequired) &&
                   (boxingEnabled || !isBoxingRequired);
        }

        public Symbol mergeResults(Symbol prev, Symbol sym) {
            return sym;
        }
    }

    final List<MethodResolutionPhase> methodResolutionSteps = List.of(BASIC, BOX, VARARITY);

    /**
     * A resolution context is used to keep track of intermediate results of
     * overload resolution, such as list of method that are not applicable
     * (used to generate more precise diagnostics) and so on. Resolution contexts
     * can be nested - this means that when each overload resolution routine should
     * work within the resolution context it created.
     */
    class MethodResolutionContext {

        private List<Candidate> candidates = List.nil();

        MethodResolutionPhase step = null;

        MethodCheck methodCheck = resolveMethodCheck;

        private boolean internalResolution = false;
        private DeferredAttr.AttrMode attrMode = DeferredAttr.AttrMode.SPECULATIVE;

        void addInapplicableCandidate(Symbol sym, JCDiagnostic details) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, details, null);
            candidates = candidates.append(c);
        }

        void addApplicableCandidate(Symbol sym, Type mtype) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, null, mtype);
            candidates = candidates.append(c);
        }

        DeferredAttrContext deferredAttrContext(Symbol sym, InferenceContext inferenceContext, ResultInfo pendingResult, Warner warn) {
            return deferredAttr.new DeferredAttrContext(attrMode, sym, step, inferenceContext, pendingResult != null ? pendingResult.checkContext.deferredAttrContext() : deferredAttr.emptyDeferredAttrContext, warn);
        }

        /**
         * This class represents an overload resolution candidate. There are two
         * kinds of candidates: applicable methods and inapplicable methods;
         * applicable methods have a pointer to the instantiated method type,
         * while inapplicable candidates contain further details about the
         * reason why the method has been considered inapplicable.
         */
        @SuppressWarnings("overrides")
        class Candidate {

            final MethodResolutionPhase step;
            final Symbol sym;
            final JCDiagnostic details;
            final Type mtype;

            private Candidate(MethodResolutionPhase step, Symbol sym, JCDiagnostic details, Type mtype) {
                this.step = step;
                this.sym = sym;
                this.details = details;
                this.mtype = mtype;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Candidate) {
                    Symbol s1 = this.sym;
                    Symbol s2 = ((Candidate)o).sym;
                    if  ((s1 != s2 &&
                            (s1.overrides(s2, s1.owner.type.tsym, types, false) ||
                            (s2.overrides(s1, s2.owner.type.tsym, types, false)))) ||
                            ((s1.isConstructor() || s2.isConstructor()) && s1.owner != s2.owner))
                        return true;
                }
                return false;
            }

            boolean isApplicable() {
                return mtype != null;
            }
        }

        DeferredAttr.AttrMode attrMode() {
            return attrMode;
        }

        boolean internal() {
            return internalResolution;
        }
    }

    MethodResolutionContext currentResolutionContext = null;
}
