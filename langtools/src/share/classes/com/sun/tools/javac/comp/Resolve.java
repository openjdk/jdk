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
import com.sun.tools.javac.comp.Infer.InferenceContext.FreeTypeListener;
import com.sun.tools.javac.comp.Resolve.MethodResolutionContext.Candidate;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;

import javax.lang.model.element.ElementVisitor;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Flags.BLOCK;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.Kinds.ERRONEOUS;
import static com.sun.tools.javac.code.TypeTags.*;
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
    private final boolean debugResolve;
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
        verboseResolutionMode = VerboseResolutionMode.getVerboseResolutionMode(options);
        Target target = Target.instance(context);
        allowMethodHandles = target.hasMethodHandles();
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

        String opt;

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
        if (sym.type.tag == FORALL) {
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
        return (t.tag == ARRAY)
            ? isAccessible(env, types.elemtype(t))
            : isAccessible(env, t.tsym, checkInner);
    }

    /** Is symbol accessible as a member of given type in given evironment?
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

    /** Try to instantiate the type of a method so that it fits
     *  given type arguments and argument types. If succesful, return
     *  the method's instantiated type, else return null.
     *  The instantiation will take into account an additional leading
     *  formal parameter if the method is an instance method seen as a member
     *  of un underdetermined site In this case, we treat site as an additional
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
                        Warner warn)
        throws Infer.InferenceException {
        if (useVarargs && (m.flags() & VARARGS) == 0) {
            //better error recovery - if we stumbled upon a non-varargs method
            //during varargs applicability phase, the method should be treated as
            //not applicable; the reason for inapplicability can be found in the
            //candidate for 'm' that was created during the BOX phase.
            Candidate prevCandidate = currentResolutionContext.getCandidate(m, BOX);
            JCDiagnostic details = null;
            if (prevCandidate != null && !prevCandidate.isApplicable()) {
                details = prevCandidate.details;
            }
            throw inapplicableMethodException.setMessage(details);
        }
        Type mt = types.memberType(site, m);

        // tvars is the list of formal type variables for which type arguments
        // need to inferred.
        List<Type> tvars = List.nil();
        if (typeargtypes == null) typeargtypes = List.nil();
        if (mt.tag != FORALL && typeargtypes.nonEmpty()) {
            // This is not a polymorphic method, but typeargs are supplied
            // which is fine, see JLS 15.12.2.1
        } else if (mt.tag == FORALL && typeargtypes.nonEmpty()) {
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
        } else if (mt.tag == FORALL) {
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
            if (l.head.tag == FORALL) instNeeded = true;
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

        checkRawArgumentsAcceptable(env, m, argtypes, mt.getParameterTypes(),
                                allowBoxing, useVarargs, warn);
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

    /** Check if a parameter list accepts a list of args.
     */
    boolean argumentsAcceptable(Env<AttrContext> env,
                                Symbol msym,
                                List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn) {
        try {
            checkRawArgumentsAcceptable(env, msym, argtypes, formals, allowBoxing, useVarargs, warn);
            return true;
        } catch (InapplicableMethodException ex) {
            return false;
        }
    }
    /**
     * A check handler is used by the main method applicability routine in order
     * to handle specific method applicability failures. It is assumed that a class
     * implementing this interface should throw exceptions that are a subtype of
     * InapplicableMethodException (see below). Such exception will terminate the
     * method applicability check and propagate important info outwards (for the
     * purpose of generating better diagnostics).
     */
    interface MethodCheckHandler {
        /* The number of actuals and formals differ */
        InapplicableMethodException arityMismatch();
        /* An actual argument type does not conform to the corresponding formal type */
        InapplicableMethodException argumentMismatch(boolean varargs, JCDiagnostic details);
        /* The element type of a varargs is not accessible in the current context */
        InapplicableMethodException inaccessibleVarargs(Symbol location, Type expected);
    }

    /**
     * Basic method check handler used within Resolve - all methods end up
     * throwing InapplicableMethodException; a diagnostic fragment that describes
     * the cause as to why the method is not applicable is set on the exception
     * before it is thrown.
     */
    MethodCheckHandler resolveHandler = new MethodCheckHandler() {
            public InapplicableMethodException arityMismatch() {
                return inapplicableMethodException.setMessage("arg.length.mismatch");
            }
            public InapplicableMethodException argumentMismatch(boolean varargs, JCDiagnostic details) {
                String key = varargs ?
                        "varargs.argument.mismatch" :
                        "no.conforming.assignment.exists";
                return inapplicableMethodException.setMessage(key,
                        details);
            }
            public InapplicableMethodException inaccessibleVarargs(Symbol location, Type expected) {
                return inapplicableMethodException.setMessage("inaccessible.varargs.type",
                        expected, Kinds.kindName(location), location);
            }
    };

    void checkRawArgumentsAcceptable(Env<AttrContext> env,
                                Symbol msym,
                                List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn) {
        checkRawArgumentsAcceptable(env, msym, currentResolutionContext.attrMode(), infer.emptyContext, argtypes, formals,
                allowBoxing, useVarargs, warn, resolveHandler);
    }

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
    void checkRawArgumentsAcceptable(final Env<AttrContext> env,
                                Symbol msym,
                                DeferredAttr.AttrMode mode,
                                final Infer.InferenceContext inferenceContext,
                                List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn,
                                final MethodCheckHandler handler) {
        Type varargsFormal = useVarargs ? formals.last() : null;

        if (varargsFormal == null &&
                argtypes.size() != formals.size()) {
            throw handler.arityMismatch(); // not enough args
        }

        DeferredAttr.DeferredAttrContext deferredAttrContext =
                deferredAttr.new DeferredAttrContext(mode, msym, currentResolutionContext.step, inferenceContext);

        while (argtypes.nonEmpty() && formals.head != varargsFormal) {
            ResultInfo mresult = methodCheckResult(formals.head, allowBoxing, false, inferenceContext, deferredAttrContext, handler, warn);
            mresult.check(null, argtypes.head);
            argtypes = argtypes.tail;
            formals = formals.tail;
        }

        if (formals.head != varargsFormal) {
            throw handler.arityMismatch(); // not enough args
        }

        if (useVarargs) {
            //note: if applicability check is triggered by most specific test,
            //the last argument of a varargs is _not_ an array type (see JLS 15.12.2.5)
            final Type elt = types.elemtype(varargsFormal);
            ResultInfo mresult = methodCheckResult(elt, allowBoxing, true, inferenceContext, deferredAttrContext, handler, warn);
            while (argtypes.nonEmpty()) {
                mresult.check(null, argtypes.head);
                argtypes = argtypes.tail;
            }
            //check varargs element type accessibility
            varargsAccessible(env, elt, handler, inferenceContext);
        }

        deferredAttrContext.complete();
    }

    void varargsAccessible(final Env<AttrContext> env, final Type t, final Resolve.MethodCheckHandler handler, final InferenceContext inferenceContext) {
        if (inferenceContext.free(t)) {
            inferenceContext.addFreeTypeListener(List.of(t), new FreeTypeListener() {
                @Override
                public void typesInferred(InferenceContext inferenceContext) {
                    varargsAccessible(env, inferenceContext.asInstType(t, types), handler, inferenceContext);
                }
            });
        } else {
            if (!isAccessible(env, t)) {
                Symbol location = env.enclClass.sym;
                throw handler.inaccessibleVarargs(location, t);
            }
        }
    }

    /**
     * Check context to be used during method applicability checks. A method check
     * context might contain inference variables.
     */
    abstract class MethodCheckContext implements CheckContext {

        MethodCheckHandler handler;
        boolean useVarargs;
        Infer.InferenceContext inferenceContext;
        DeferredAttrContext deferredAttrContext;
        Warner rsWarner;

        public MethodCheckContext(MethodCheckHandler handler, boolean useVarargs,
                Infer.InferenceContext inferenceContext, DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            this.handler = handler;
            this.useVarargs = useVarargs;
            this.inferenceContext = inferenceContext;
            this.deferredAttrContext = deferredAttrContext;
            this.rsWarner = rsWarner;
        }

        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            throw handler.argumentMismatch(useVarargs, details);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return rsWarner;
        }

        public InferenceContext inferenceContext() {
            return inferenceContext;
        }

        public DeferredAttrContext deferredAttrContext() {
            return deferredAttrContext;
        }
    }

    /**
     * Subclass of method check context class that implements strict method conversion.
     * Strict method conversion checks compatibility between types using subtyping tests.
     */
    class StrictMethodContext extends MethodCheckContext {

        public StrictMethodContext(MethodCheckHandler handler, boolean useVarargs,
                Infer.InferenceContext inferenceContext, DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            super(handler, useVarargs, inferenceContext, deferredAttrContext, rsWarner);
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return types.isSubtypeUnchecked(found, inferenceContext.asFree(req, types), warn);
        }

        public boolean allowBoxing() {
            return false;
        }
    }

    /**
     * Subclass of method check context class that implements loose method conversion.
     * Loose method conversion checks compatibility between types using method conversion tests.
     */
    class LooseMethodContext extends MethodCheckContext {

        public LooseMethodContext(MethodCheckHandler handler, boolean useVarargs,
                Infer.InferenceContext inferenceContext, DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            super(handler, useVarargs, inferenceContext, deferredAttrContext, rsWarner);
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return types.isConvertible(found, inferenceContext.asFree(req, types), warn);
        }

        public boolean allowBoxing() {
            return true;
        }
    }

    /**
     * Create a method check context to be used during method applicability check
     */
    ResultInfo methodCheckResult(Type to, boolean allowBoxing, boolean useVarargs,
            Infer.InferenceContext inferenceContext, DeferredAttr.DeferredAttrContext deferredAttrContext,
            MethodCheckHandler methodHandler, Warner rsWarner) {
        MethodCheckContext checkContext = allowBoxing ?
                new LooseMethodContext(methodHandler, useVarargs, inferenceContext, deferredAttrContext, rsWarner) :
                new StrictMethodContext(methodHandler, useVarargs, inferenceContext, deferredAttrContext, rsWarner);
        return new MethodResultInfo(to, checkContext, deferredAttrContext);
    }

    class MethodResultInfo extends ResultInfo {

        DeferredAttr.DeferredAttrContext deferredAttrContext;

        public MethodResultInfo(Type pt, MethodCheckContext checkContext, DeferredAttr.DeferredAttrContext deferredAttrContext) {
            attr.super(VAL, pt, checkContext);
            this.deferredAttrContext = deferredAttrContext;
        }

        @Override
        protected Type check(DiagnosticPosition pos, Type found) {
            if (found.tag == DEFERRED) {
                DeferredType dt = (DeferredType)found;
                return dt.check(this);
            } else {
                return super.check(pos, chk.checkNonVoid(pos, types.capture(types.upperBound(found.baseType()))));
            }
        }

        @Override
        protected MethodResultInfo dup(Type newPt) {
            return new MethodResultInfo(newPt, (MethodCheckContext)checkContext, deferredAttrContext);
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
        while (c.type.tag == TYPEVAR)
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
        if (st != null && (st.tag == CLASS || st.tag == TYPEVAR)) {
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
        if (sym.kind == ERR) return bestSoFar;
        if (!sym.isInheritedIn(site.tsym, types)) return bestSoFar;
        Assert.check(sym.kind < AMBIGUOUS);
        try {
            Type mt = rawInstantiate(env, site, sym, null, argtypes, typeargtypes,
                               allowBoxing, useVarargs, Warner.noWarnings);
            if (!operator)
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
                if (!m1Abstract && !m2Abstract)
                    return ambiguityError(m1, m2);
                // check that both signatures have the same erasure
                if (!types.isSameTypes(m1.erasure(types).getParameterTypes(),
                                       m2.erasure(types).getParameterTypes()))
                    return ambiguityError(m1, m2);
                // both abstract, neither overridden; merge throws clause and result type
                Type mst = mostSpecificReturnType(mt1, mt2);
                if (mst == null) {
                    // Theoretically, this can't happen, but it is possible
                    // due to error recovery or mixing incompatible class files
                    return ambiguityError(m1, m2);
                }
                Symbol mostSpecific = mst == mt1 ? m1 : m2;
                List<Type> allThrown = chk.intersect(mt1.getThrownTypes(), mt2.getThrownTypes());
                Type newSig = types.createMethodTypeWithThrown(mostSpecific.type, allThrown);
                MethodSymbol result = new MethodSymbol(
                        mostSpecific.flags(),
                        mostSpecific.name,
                        newSig,
                        mostSpecific.owner) {
                    @Override
                    public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult) {
                        if (origin == site.tsym)
                            return this;
                        else
                            return super.implementation(origin, types, checkResult);
                        }
                    };
                return result;
            }
            if (m1SignatureMoreSpecific) return m1;
            if (m2SignatureMoreSpecific) return m2;
            return ambiguityError(m1, m2);
        case AMBIGUOUS:
            AmbiguityError e = (AmbiguityError)m2;
            Symbol err1 = mostSpecific(argtypes, m1, e.sym, env, site, allowBoxing, useVarargs);
            Symbol err2 = mostSpecific(argtypes, m1, e.sym2, env, site, allowBoxing, useVarargs);
            if (err1 == err2) return err1;
            if (err1 == e.sym && err2 == e.sym2) return m2;
            if (err1 instanceof AmbiguityError &&
                err2 instanceof AmbiguityError &&
                ((AmbiguityError)err1).sym == ((AmbiguityError)err2).sym)
                return ambiguityError(m1, m2);
            else
                return ambiguityError(err1, err2);
        default:
            throw new AssertionError();
        }
    }
    //where
    private boolean signatureMoreSpecific(List<Type> actuals, Env<AttrContext> env, Type site, Symbol m1, Symbol m2, boolean allowBoxing, boolean useVarargs) {
        Symbol m12 = adjustVarargs(m1, m2, useVarargs);
        Symbol m22 = adjustVarargs(m2, m1, useVarargs);
        Type mtype1 = types.memberType(site, m12);
        Type mtype2 = types.memberType(site, m22);

        //check if invocation is more specific
        if (invocationMoreSpecific(env, site, m22, mtype1.getParameterTypes(), allowBoxing, useVarargs)) {
            return true;
        }

        //perform structural check

        List<Type> formals1 = mtype1.getParameterTypes();
        Type lastFormal1 = formals1.last();
        List<Type> formals2 = mtype2.getParameterTypes();
        Type lastFormal2 = formals2.last();
        ListBuffer<Type> newFormals = ListBuffer.lb();

        boolean hasStructuralPoly = false;
        for (Type actual : actuals) {
            //perform formal argument adaptation in case actuals > formals (varargs)
            Type f1 = formals1.isEmpty() ?
                    lastFormal1 : formals1.head;
            Type f2 = formals2.isEmpty() ?
                    lastFormal2 : formals2.head;

            //is this a structural actual argument?
            boolean isStructuralPoly = actual.tag == DEFERRED &&
                    (((DeferredType)actual).tree.hasTag(LAMBDA) ||
                    ((DeferredType)actual).tree.hasTag(REFERENCE));

            Type newFormal = f1;

            if (isStructuralPoly) {
                //for structural arguments only - check that corresponding formals
                //are related - if so replace formal with <null>
                hasStructuralPoly = true;
                DeferredType dt = (DeferredType)actual;
                Type t1 = deferredAttr.new DeferredTypeMap(AttrMode.SPECULATIVE, m1, currentResolutionContext.step).apply(dt);
                Type t2 = deferredAttr.new DeferredTypeMap(AttrMode.SPECULATIVE, m2, currentResolutionContext.step).apply(dt);
                if (t1.isErroneous() || t2.isErroneous() || !isStructuralSubtype(t1, t2)) {
                    //not structural subtypes - simply fail
                    return false;
                } else {
                    newFormal = syms.botType;
                }
            }

            newFormals.append(newFormal);
            if (newFormals.length() > mtype2.getParameterTypes().length()) {
                //expand m2's type so as to fit the new formal arity (varargs)
                m22.type = types.createMethodTypeWithParameters(m22.type, m22.type.getParameterTypes().append(f2));
            }

            formals1 = formals1.isEmpty() ? formals1 : formals1.tail;
            formals2 = formals2.isEmpty() ? formals2 : formals2.tail;
        }

        if (!hasStructuralPoly) {
            //if no structural actual was found, we're done
            return false;
        }
        //perform additional adaptation if actuals < formals (varargs)
        for (Type t : formals1) {
            newFormals.append(t);
        }
        //check if invocation (with tweaked args) is more specific
        return invocationMoreSpecific(env, site, m22, newFormals.toList(), allowBoxing, useVarargs);
    }
    //where
    private boolean invocationMoreSpecific(Env<AttrContext> env, Type site, Symbol m2, List<Type> argtypes1, boolean allowBoxing, boolean useVarargs) {
        noteWarner.clear();
        Type mst = instantiate(env, site, m2, null,
                types.lowerBounds(argtypes1), null,
                allowBoxing, false, noteWarner);
        return mst != null &&
                !noteWarner.hasLint(Lint.LintCategory.UNCHECKED);
    }
    //where
    private Symbol adjustVarargs(Symbol to, Symbol from, boolean useVarargs) {
        List<Type> fromArgs = from.type.getParameterTypes();
        List<Type> toArgs = to.type.getParameterTypes();
        if (useVarargs &&
                (from.flags() & VARARGS) != 0 &&
                (to.flags() & VARARGS) != 0) {
            Type varargsTypeFrom = fromArgs.last();
            Type varargsTypeTo = toArgs.last();
            ListBuffer<Type> args = ListBuffer.lb();
            if (toArgs.length() < fromArgs.length()) {
                //if we are checking a varargs method 'from' against another varargs
                //method 'to' (where arity of 'to' < arity of 'from') then expand signature
                //of 'to' to 'fit' arity of 'from' (this means adding fake formals to 'to'
                //until 'to' signature has the same arity as 'from')
                while (fromArgs.head != varargsTypeFrom) {
                    args.append(toArgs.head == varargsTypeTo ? types.elemtype(varargsTypeTo) : toArgs.head);
                    fromArgs = fromArgs.tail;
                    toArgs = toArgs.head == varargsTypeTo ?
                        toArgs :
                        toArgs.tail;
                }
            } else {
                //formal argument list is same as original list where last
                //argument (array type) is removed
                args.appendList(toArgs.reverse().tail.reverse());
            }
            //append varargs element type as last synthetic formal
            args.append(types.elemtype(varargsTypeTo));
            Type mtype = types.createMethodTypeWithParameters(to.type, args.toList());
            return new MethodSymbol(to.flags_field & ~VARARGS, to.name, mtype, to.owner);
        } else {
            return to;
        }
    }
    //where
    boolean isStructuralSubtype(Type s, Type t) {

        Type ret_s = types.findDescriptorType(s).getReturnType();
        Type ret_t = types.findDescriptorType(t).getReturnType();

        //covariant most specific check for function descriptor return type
        if (!types.isSubtype(ret_s, ret_t)) {
            return false;
        }

        List<Type> args_s = types.findDescriptorType(s).getParameterTypes();
        List<Type> args_t = types.findDescriptorType(t).getParameterTypes();

        //arity must be identical
        if (args_s.length() != args_t.length()) {
            return false;
        }

        //invariant most specific check for function descriptor parameter types
        if (!types.isSameTypes(args_t, args_s)) {
            return false;
        }

        return true;
    }
    //where
    Type mostSpecificReturnType(Type mt1, Type mt2) {
        Type rt1 = mt1.getReturnType();
        Type rt2 = mt2.getReturnType();

        if (mt1.tag == FORALL && mt2.tag == FORALL) {
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
        boolean abstractOk = true;
        List<Type> itypes = List.nil();
        for (TypeSymbol s : superclasses(intype)) {
            bestSoFar = lookupMethod(env, site, name, argtypes, typeargtypes,
                    s.members(), bestSoFar, allowBoxing, useVarargs, operator, true);
            //We should not look for abstract methods if receiver is a concrete class
            //(as concrete classes are expected to implement all abstracts coming
            //from superinterfaces)
            abstractOk &= (s.flags() & (ABSTRACT | INTERFACE | ENUM)) != 0;
            if (abstractOk) {
                for (Type itype : types.interfaces(s.type)) {
                    itypes = types.union(types.closure(itype), itypes);
                }
            }
            if (name == names.init) break;
        }

        Symbol concrete = bestSoFar.kind < ERR &&
                (bestSoFar.flags() & ABSTRACT) == 0 ?
                bestSoFar : methodNotFound;

        if (name != names.init) {
            //keep searching for abstract methods
            for (Type itype : itypes) {
                if (!itype.isInterface()) continue; //skip j.l.Object (included by Types.closure())
                bestSoFar = lookupMethod(env, site, name, argtypes, typeargtypes,
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
                        if (t.tag != CLASS &&
                                t.tag != TYPEVAR) {
                            return null;
                        }
                        while (t.tag == TYPEVAR)
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

    /**
     * Lookup a method with given name and argument types in a given scope
     */
    Symbol lookupMethod(Env<AttrContext> env,
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
        for (Symbol s : sc.getElementsByName(name, lookupFilter)) {
            bestSoFar = selectBest(env, site, argtypes, typeargtypes, s,
                    bestSoFar, allowBoxing, useVarargs, operator);
        }
        return bestSoFar;
    }
    //where
        Filter<Symbol> lookupFilter = new Filter<Symbol>() {
            public boolean accepts(Symbol s) {
                return s.kind == MTH &&
                        (s.flags() & SYNTHETIC) == 0;
            }
        };

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
        if (st != null && st.tag == CLASS) {
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
                        e.sym.type.tag == TYPEVAR &&
                        e.sym.owner.kind == TYP) return new StaticError(e.sym);
                    return e.sym;
                }
            }

            sym = findMemberType(env1, env1.enclClass.sym.type, name,
                                 env1.enclClass.sym);
            if (staticOnly && sym.kind == TYP &&
                sym.type.tag == CLASS &&
                sym.type.getEnclosingType().tag == CLASS &&
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
     *  @param name      The indentifier's name.
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
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }

        if ((kind & PCK) != 0) return reader.enterPackage(name);
        else return bestSoFar;
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
            if (syms.operatorNames.contains(name)) {
                return argtypes;
            } else {
                Symbol msym = errSym.kind == WRONG_MTH ?
                        ((InapplicableSymbolError)errSym).errCandidate().sym : accessedSym;

                List<Type> argtypes2 = Type.map(argtypes,
                        deferredAttr.new RecoveryDeferredTypeMap(AttrMode.SPECULATIVE, msym, currentResolutionContext.firstErroneousResolutionPhase()));

                if (msym != accessedSym) {
                    //fixup deferred type caches - this 'hack' is required because the symbol
                    //returned by InapplicableSymbolError.access() will hide the candidate
                    //method symbol that can be used for lookups in the speculative cache,
                    //causing problems in Attr.checkId()
                    for (Type t : argtypes) {
                        if (t.tag == DEFERRED) {
                            DeferredType dt = (DeferredType)t;
                            dt.speculativeCache.dupAllTo(msym, accessedSym);
                        }
                    }
                }
                return argtypes2;
            }
        }
    };

    /** Check that sym is not an abstract method.
     */
    void checkNonAbstract(DiagnosticPosition pos, Symbol sym) {
        if ((sym.flags() & ABSTRACT) != 0)
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
        while (t.tag == CLASS) {
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
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= ERRONEOUS) {
                currentResolutionContext.step = env.info.pendingResolutionPhase = steps.head;
                sym = findFun(env, name, argtypes, typeargtypes,
                        steps.head.isBoxingRequired,
                        steps.head.isVarargsRequired);
                currentResolutionContext.resolutionCache.put(steps.head, sym);
                steps = steps.tail;
            }
            if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
                MethodResolutionPhase errPhase =
                        currentResolutionContext.firstErroneousResolutionPhase();
                sym = accessMethod(currentResolutionContext.resolutionCache.get(errPhase),
                        pos, env.enclClass.sym.type, name, false, argtypes, typeargtypes);
                env.info.pendingResolutionPhase = errPhase;
            }
            return sym;
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
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
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = resolveContext;
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= ERRONEOUS) {
                currentResolutionContext.step = env.info.pendingResolutionPhase = steps.head;
                sym = findMethod(env, site, name, argtypes, typeargtypes,
                        steps.head.isBoxingRequired(),
                        steps.head.isVarargsRequired(), false);
                currentResolutionContext.resolutionCache.put(steps.head, sym);
                steps = steps.tail;
            }
            if (sym.kind >= AMBIGUOUS) {
                //if nothing is found return the 'first' error
                MethodResolutionPhase errPhase =
                        currentResolutionContext.firstErroneousResolutionPhase();
                sym = accessMethod(currentResolutionContext.resolutionCache.get(errPhase),
                        pos, location, site, name, true, argtypes, typeargtypes);
                env.info.pendingResolutionPhase = errPhase;
            } else if (allowMethodHandles) {
                MethodSymbol msym = (MethodSymbol)sym;
                if (msym.isSignaturePolymorphic(types)) {
                    env.info.pendingResolutionPhase = BASIC;
                    return findPolymorphicSignatureInstance(env, sym, argtypes);
                }
            }
            return sym;
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    /** Find or create an implicit method of exactly the given type (after erasure).
     *  Searches in a side table, not the main scope of the site.
     *  This emulates the lookup process required by JSR 292 in JVM.
     *  @param env       Attribution environment
     *  @param spMethod  signature polymorphic method - i.e. MH.invokeExact
     *  @param argtypes  The required argument types
     */
    Symbol findPolymorphicSignatureInstance(Env<AttrContext> env,
                                            Symbol spMethod,
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
        Symbol msym = new MethodSymbol(flags, spMethod.name, mtype, spMethod.owner);
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
                              DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = resolveContext;
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= ERRONEOUS) {
                currentResolutionContext.step = env.info.pendingResolutionPhase = steps.head;
                sym = findConstructor(pos, env, site, argtypes, typeargtypes,
                        steps.head.isBoxingRequired(),
                        steps.head.isVarargsRequired());
                currentResolutionContext.resolutionCache.put(steps.head, sym);
                steps = steps.tail;
            }
            if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
                MethodResolutionPhase errPhase = currentResolutionContext.firstErroneousResolutionPhase();
                sym = accessMethod(currentResolutionContext.resolutionCache.get(errPhase),
                        pos, site, names.init, true, argtypes, typeargtypes);
                env.info.pendingResolutionPhase = errPhase;
            }
            return sym;
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
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
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= ERRONEOUS) {
                currentResolutionContext.step = env.info.pendingResolutionPhase = steps.head;
                sym = findDiamond(env, site, argtypes, typeargtypes,
                        steps.head.isBoxingRequired(),
                        steps.head.isVarargsRequired());
                currentResolutionContext.resolutionCache.put(steps.head, sym);
                steps = steps.tail;
            }
            if (sym.kind >= AMBIGUOUS) {
                Symbol errSym =
                        currentResolutionContext.resolutionCache.get(currentResolutionContext.firstErroneousResolutionPhase());
                final JCDiagnostic details = errSym.kind == WRONG_MTH ?
                                ((InapplicableSymbolError)errSym).errCandidate().details :
                                null;
                errSym = new InapplicableSymbolError(errSym.kind, "diamondError", currentResolutionContext) {
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
                MethodResolutionPhase errPhase = currentResolutionContext.firstErroneousResolutionPhase();
                sym = accessMethod(errSym, pos, site, names.init, true, argtypes, typeargtypes);
                env.info.pendingResolutionPhase = errPhase;
            }
            return sym;
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
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
                    List<Type> oldParams = e.sym.type.tag == FORALL ?
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
                                  boolean boxingAllowed) {
        //step 1 - bound lookup
        ReferenceLookupHelper boundLookupHelper = name.equals(names.init) ?
                new ConstructorReferenceLookupHelper(referenceTree, site, argtypes, typeargtypes, boxingAllowed) :
                new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, boxingAllowed);
        Env<AttrContext> boundEnv = env.dup(env.tree, env.info.dup());
        Symbol boundSym = findMemberReference(boundEnv, boundLookupHelper);

        //step 2 - unbound lookup
        ReferenceLookupHelper unboundLookupHelper = boundLookupHelper.unboundLookup();
        Env<AttrContext> unboundEnv = env.dup(env.tree, env.info.dup());
        Symbol unboundSym = findMemberReference(unboundEnv, unboundLookupHelper);

        //merge results
        Pair<Symbol, ReferenceLookupHelper> res;
        if (unboundSym.kind != MTH) {
            res = new Pair<Symbol, ReferenceLookupHelper>(boundSym, boundLookupHelper);
            env.info.pendingResolutionPhase = boundEnv.info.pendingResolutionPhase;
        } else if (boundSym.kind == MTH) {
            res = new Pair<Symbol, ReferenceLookupHelper>(ambiguityError(boundSym, unboundSym), boundLookupHelper);
            env.info.pendingResolutionPhase = boundEnv.info.pendingResolutionPhase;
        } else {
            res = new Pair<Symbol, ReferenceLookupHelper>(unboundSym, unboundLookupHelper);
            env.info.pendingResolutionPhase = unboundEnv.info.pendingResolutionPhase;
        }

        return res;
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

        LookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            this.name = name;
            this.site = site;
            this.argtypes = argtypes;
            this.typeargtypes = typeargtypes;
        }

        /**
         * Search for a symbol under a given overload resolution phase - this method
         * is usually called several times, once per each overload resolution phase
         */
        abstract Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase);

        /**
         * Validate the result of the lookup
         */
        abstract Symbol access(Env<AttrContext> env, Symbol symbol);
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

        /** Max overload resolution phase handled by this helper */
        MethodResolutionPhase maxPhase;

        ReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                List<Type> argtypes, List<Type> typeargtypes, boolean boxingAllowed) {
            super(name, site, argtypes, typeargtypes);
            this.referenceTree = referenceTree;
            this.maxPhase = boxingAllowed ? VARARITY : BASIC;
        }

        /**
         * Returns an unbound version of this lookup helper. By default, this
         * method returns an dummy lookup helper.
         */
        ReferenceLookupHelper unboundLookup() {
            //dummy loopkup helper that always return 'methodNotFound'
            return new ReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase.isBoxingRequired()) {
                @Override
                ReferenceLookupHelper unboundLookup() {
                    return this;
                }
                @Override
                Symbol lookupReference(Env<AttrContext> env, MethodResolutionPhase phase) {
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

        @Override
        Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            return (env.info.pendingResolutionPhase.ordinal() > maxPhase.ordinal()) ?
                    methodNotFound : lookupReference(env, phase);
        }

        abstract Symbol lookupReference(Env<AttrContext> env, MethodResolutionPhase phase);

        Symbol access(Env<AttrContext> env, Symbol sym) {
            if (sym.kind >= AMBIGUOUS) {
                MethodResolutionPhase errPhase = currentResolutionContext.firstErroneousResolutionPhase();
                if (errPhase.ordinal() > maxPhase.ordinal()) {
                    errPhase = maxPhase;
                }
                env.info.pendingResolutionPhase = errPhase;
                sym = currentResolutionContext.resolutionCache.get(errPhase);
            }
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
                List<Type> argtypes, List<Type> typeargtypes, boolean boxingAllowed) {
            super(referenceTree, name, site, argtypes, typeargtypes, boxingAllowed);
        }

        protected Symbol lookupReferenceInternal(Env<AttrContext> env, MethodResolutionPhase phase) {
            return findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
        }

        protected Symbol adjustLookupResult(Env<AttrContext> env, Symbol sym) {
            return !TreeInfo.isStaticSelector(referenceTree.expr, names) ||
                        sym.kind != MTH ||
                        sym.isStatic() ? sym : new StaticError(sym);
        }

        @Override
        final Symbol lookupReference(Env<AttrContext> env, MethodResolutionPhase phase) {
            return adjustLookupResult(env, lookupReferenceInternal(env, phase));
        }

        @Override
        ReferenceLookupHelper unboundLookup() {
            if (TreeInfo.isStaticSelector(referenceTree.expr, names) &&
                    argtypes.nonEmpty() &&
                    types.isSubtypeUnchecked(argtypes.head, site)) {
                return new UnboundMethodReferenceLookupHelper(referenceTree, name,
                        site, argtypes, typeargtypes, maxPhase.isBoxingRequired());
            } else {
                return super.unboundLookup();
            }
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            if (sym.isStatic()) {
                return TreeInfo.isStaticSelector(referenceTree.expr, names) ?
                        ReferenceKind.STATIC : ReferenceKind.STATIC_EVAL;
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
                List<Type> argtypes, List<Type> typeargtypes, boolean boxingAllowed) {
            super(referenceTree, name,
                    site.isRaw() ? types.asSuper(argtypes.head, site.tsym) : site,
                    argtypes.tail, typeargtypes, boxingAllowed);
        }

        @Override
        protected Symbol adjustLookupResult(Env<AttrContext> env, Symbol sym) {
            return sym.kind != MTH || !sym.isStatic() ? sym : new StaticError(sym);
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
                List<Type> typeargtypes, boolean boxingAllowed) {
            super(referenceTree, names.init, site, argtypes, typeargtypes, boxingAllowed);
            if (site.isRaw()) {
                this.site = new ClassType(site.getEnclosingType(), site.tsym.type.getTypeArguments(), site.tsym);
                needsInference = true;
            }
        }

        @Override
        protected Symbol lookupReference(Env<AttrContext> env, MethodResolutionPhase phase) {
            Symbol sym = needsInference ?
                findDiamond(env, site, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired()) :
                findMethod(env, site, name, argtypes, typeargtypes,
                        phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
            return sym.kind != MTH ||
                          site.getEnclosingType().tag == NONE ||
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
            return site.getEnclosingType().tag == NONE ?
                    ReferenceKind.TOPLEVEL : ReferenceKind.IMPLICIT_INNER;
        }
    }

    /**
     * Resolution step for member reference. This generalizes a standard
     * method/constructor lookup - on each overload resolution step, a
     * lookup helper class is used to perform the reference lookup; at the end
     * of the lookup, the helper is used to validate the results.
     */
    Symbol findMemberReference(Env<AttrContext> env, LookupHelper lookupHelper) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= ERRONEOUS) {
                currentResolutionContext.step = env.info.pendingResolutionPhase = steps.head;
                sym = lookupHelper.lookup(env, steps.head);
                currentResolutionContext.resolutionCache.put(steps.head, sym);
                steps = steps.tail;
            }
            return lookupHelper.access(env, sym);
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    /** Resolve constructor.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     *  @param allowBoxing Allow boxing and varargs conversions.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol resolveConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                              Type site, List<Type> argtypes,
                              List<Type> typeargtypes,
                              boolean allowBoxing,
                              boolean useVarargs) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            return findConstructor(pos, env, site, argtypes, typeargtypes, allowBoxing, useVarargs);
        }
        finally {
            currentResolutionContext = prevResolutionContext;
        }
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
            Symbol sym = findMethod(env, syms.predefClass.type, name, argtypes,
                                    null, false, false, true);
            if (boxingEnabled && sym.kind >= WRONG_MTHS)
                sym = findMethod(env, syms.predefClass.type, name, argtypes,
                                 null, true, false, true);
            return accessMethod(sym, pos, env.enclClass.sym.type, name,
                          false, argtypes, null);
        }
        finally {
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
        log.error(pos, "not.encl.class", c);
        return syms.errSymbol;
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
                if (t.tag == DEFERRED) {
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
            if (sym.kind >= AMBIGUOUS)
                return ((ResolveError)sym).access(name, location);
            else if ((sym.kind & ERRONEOUS) == 0 && (sym.kind & TYP) != 0)
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
                        typeargtypes, argtypes, //type parameters and arguments (if any)
                        getLocationDiag(location, site)); //location kindname, type
            }
            else {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname, //symbol kindname, name
                        typeargtypes, argtypes); //type parameters and arguments (if any)
            }
        }
        //where
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

        protected boolean shouldReport(Candidate c) {
            MethodResolutionPhase errPhase = resolveContext.firstErroneousResolutionPhase();
            return !c.isApplicable() &&
                    c.step == errPhase;
        }

        private Candidate errCandidate() {
            for (Candidate c : resolveContext.candidates) {
                if (shouldReport(c)) {
                    return c;
                }
            }
            Assert.error();
            return null;
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
            if (!resolveContext.candidates.isEmpty()) {
                JCDiagnostic err = diags.create(dkind,
                        log.currentSource(),
                        pos,
                        "cant.apply.symbols",
                        name == names.init ? KindName.CONSTRUCTOR : absentKind(kind),
                        getName(),
                        argtypes);
                return new JCDiagnostic.MultilineDiagnostic(err, candidateDetails(site));
            } else {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind, pos,
                    location, site, name, argtypes, typeargtypes);
            }
        }

        //where
        List<JCDiagnostic> candidateDetails(Type site) {
            List<JCDiagnostic> details = List.nil();
            for (Candidate c : resolveContext.candidates) {
                if (!shouldReport(c)) continue;
                JCDiagnostic detailDiag = diags.fragment("inapplicable.method",
                        Kinds.kindName(c.sym),
                        c.sym.location(site, types),
                        c.sym.asMemberOf(site, types),
                        c.details);
                details = details.prepend(detailDiag);
            }
            return details.reverse();
        }

        private Name getName() {
            Symbol sym = resolveContext.candidates.head.sym;
            return sym.name == names.init ?
                sym.owner.name :
                sym.name;
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
            if (sym.owner.type.tag == ERROR)
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
            Symbol errSym = ((sym.kind == TYP && sym.type.tag == CLASS)
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
    class AmbiguityError extends InvalidSymbolError {

        /** The other maximally specific symbol */
        Symbol sym2;

        AmbiguityError(Symbol sym1, Symbol sym2) {
            super(AMBIGUOUS, sym1, "ambiguity error");
            this.sym2 = sym2;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            AmbiguityError pair = this;
            while (true) {
                if (pair.sym.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym;
                else if (pair.sym2.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym2;
                else break;
            }
            Name sname = pair.sym.name;
            if (sname == names.init) sname = pair.sym.owner.name;
            return diags.create(dkind, log.currentSource(),
                      pos, "ref.ambiguous", sname,
                      kindName(pair.sym),
                      pair.sym,
                      pair.sym.location(site, types),
                      kindName(pair.sym2),
                      pair.sym2,
                      pair.sym2.location(site, types));
        }
    }

    enum MethodResolutionPhase {
        BASIC(false, false),
        BOX(true, false),
        VARARITY(true, true);

        boolean isBoxingRequired;
        boolean isVarargsRequired;

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

        private Map<MethodResolutionPhase, Symbol> resolutionCache =
            new EnumMap<MethodResolutionPhase, Symbol>(MethodResolutionPhase.class);

        MethodResolutionPhase step = null;

        private boolean internalResolution = false;
        private DeferredAttr.AttrMode attrMode = DeferredAttr.AttrMode.SPECULATIVE;

        private MethodResolutionPhase firstErroneousResolutionPhase() {
            MethodResolutionPhase bestSoFar = BASIC;
            Symbol sym = methodNotFound;
            List<MethodResolutionPhase> steps = methodResolutionSteps;
            while (steps.nonEmpty() &&
                   steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                   sym.kind >= WRONG_MTHS) {
                sym = resolutionCache.get(steps.head);
                if (sym.kind == ABSENT_MTH) break; //ignore spurious empty entries
                bestSoFar = steps.head;
                steps = steps.tail;
            }
            return bestSoFar;
        }

        void addInapplicableCandidate(Symbol sym, JCDiagnostic details) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, details, null);
            candidates = candidates.append(c);
        }

        void addApplicableCandidate(Symbol sym, Type mtype) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, null, mtype);
            candidates = candidates.append(c);
        }

        Candidate getCandidate(Symbol sym, MethodResolutionPhase phase) {
            for (Candidate c : currentResolutionContext.candidates) {
                if (c.step == phase &&
                        c.sym.baseSymbol() == sym.baseSymbol()) {
                    return c;
                }
            }
            return null;
        }

        /**
         * This class represents an overload resolution candidate. There are two
         * kinds of candidates: applicable methods and inapplicable methods;
         * applicable methods have a pointer to the instantiated method type,
         * while inapplicable candidates contain further details about the
         * reason why the method has been considered inapplicable.
         */
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
