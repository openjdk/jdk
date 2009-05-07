/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.api.Formattable.LocalizedString;
import static com.sun.tools.javac.comp.Resolve.MethodResolutionPhase.*;

import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import javax.lang.model.element.ElementVisitor;

import java.util.Map;
import java.util.HashMap;

/** Helper class for name resolution, used mostly by the attribution phase.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Resolve {
    protected static final Context.Key<Resolve> resolveKey =
        new Context.Key<Resolve>();

    Names names;
    Log log;
    Symtab syms;
    Check chk;
    Infer infer;
    ClassReader reader;
    TreeInfo treeinfo;
    Types types;
    JCDiagnostic.Factory diags;
    public final boolean boxingEnabled; // = source.allowBoxing();
    public final boolean varargsEnabled; // = source.allowVarargs();
    public final boolean allowInvokedynamic; // = options.get("invokedynamic");
    private final boolean debugResolve;

    public static Resolve instance(Context context) {
        Resolve instance = context.get(resolveKey);
        if (instance == null)
            instance = new Resolve(context);
        return instance;
    }

    protected Resolve(Context context) {
        context.put(resolveKey, this);
        syms = Symtab.instance(context);

        varNotFound = new
            ResolveError(ABSENT_VAR, syms.errSymbol, "variable not found");
        wrongMethod = new
            ResolveError(WRONG_MTH, syms.errSymbol, "method not found");
        wrongMethods = new
            ResolveError(WRONG_MTHS, syms.errSymbol, "wrong methods");
        methodNotFound = new
            ResolveError(ABSENT_MTH, syms.errSymbol, "method not found");
        typeNotFound = new
            ResolveError(ABSENT_TYP, syms.errSymbol, "type not found");

        names = Names.instance(context);
        log = Log.instance(context);
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
        debugResolve = options.get("debugresolve") != null;
        allowInvokedynamic = options.get("invokedynamic") != null;
    }

    /** error symbols, which are returned when resolution fails
     */
    final ResolveError varNotFound;
    final ResolveError wrongMethod;
    final ResolveError wrongMethods;
    final ResolveError methodNotFound;
    final ResolveError typeNotFound;

/* ************************************************************************
 * Identifier resolution
 *************************************************************************/

    /** An environment is "static" if its static level is greater than
     *  the one of its outer environment
     */
    static boolean isStatic(Env<AttrContext> env) {
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
        switch ((short)(c.flags() & AccessFlags)) {
        case PRIVATE:
            return
                env.enclClass.sym.outermostClass() ==
                c.owner.outermostClass();
        case 0:
            return
                env.toplevel.packge == c.owner // fast special case
                ||
                env.toplevel.packge == c.packge()
                ||
                // Hack: this case is added since synthesized default constructors
                // of anonymous classes should be allowed to access
                // classes which would be inaccessible otherwise.
                env.enclMethod != null &&
                (env.enclMethod.mods.flags & ANONCONSTR) != 0;
        default: // error recovery
        case PUBLIC:
            return true;
        case PROTECTED:
            return
                env.toplevel.packge == c.owner // fast special case
                ||
                env.toplevel.packge == c.packge()
                ||
                isInnerSubClass(env.enclClass.sym, c.owner);
        }
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
        return (t.tag == ARRAY)
            ? isAccessible(env, types.elemtype(t))
            : isAccessible(env, t.tsym);
    }

    /** Is symbol accessible as a member of given type in given evironment?
     *  @param env    The current environment.
     *  @param site   The type of which the tested symbol is regarded
     *                as a member.
     *  @param sym    The symbol.
     */
    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym) {
        if (sym.name == names.init && sym.owner != site.tsym) return false;
        ClassSymbol sub;
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
                isAccessible(env, site)
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
                isAccessible(env, site)
                &&
                notOverriddenIn(site, sym);
        default: // this case includes erroneous combinations as well
            return isAccessible(env, site) && notOverriddenIn(site, sym);
        }
    }
    //where
    /* `sym' is accessible only if not overridden by
     * another symbol which is a member of `site'
     * (because, if it is overridden, `sym' is not strictly
     * speaking a member of `site'.)
     */
    private boolean notOverriddenIn(Type site, Symbol sym) {
        if (sym.kind != MTH || sym.isConstructor() || sym.isStatic())
            return true;
        else {
            Symbol s2 = ((MethodSymbol)sym).implementation(site.tsym, types, true);
            return (s2 == null || s2 == sym);
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
                        List<Type> argtypes,
                        List<Type> typeargtypes,
                        boolean allowBoxing,
                        boolean useVarargs,
                        Warner warn)
        throws Infer.NoInstanceException {
        if (useVarargs && (m.flags() & VARARGS) == 0) return null;
        Type mt = types.memberType(site, m);

        // tvars is the list of formal type variables for which type arguments
        // need to inferred.
        List<Type> tvars = env.info.tvars;
        if (typeargtypes == null) typeargtypes = List.nil();
        if (mt.tag != FORALL && typeargtypes.nonEmpty()) {
            // This is not a polymorphic method, but typeargs are supplied
            // which is fine, see JLS3 15.12.2.1
        } else if (mt.tag == FORALL && typeargtypes.nonEmpty()) {
            ForAll pmt = (ForAll) mt;
            if (typeargtypes.length() != pmt.tvars.length())
                return null;
            // Check type arguments are within bounds
            List<Type> formals = pmt.tvars;
            List<Type> actuals = typeargtypes;
            while (formals.nonEmpty() && actuals.nonEmpty()) {
                List<Type> bounds = types.subst(types.getBounds((TypeVar)formals.head),
                                                pmt.tvars, typeargtypes);
                for (; bounds.nonEmpty(); bounds = bounds.tail)
                    if (!types.isSubtypeUnchecked(actuals.head, bounds.head, warn))
                        return null;
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
        boolean instNeeded = tvars.tail != null/*inlined: tvars.nonEmpty()*/;
        for (List<Type> l = argtypes;
             l.tail != null/*inlined: l.nonEmpty()*/ && !instNeeded;
             l = l.tail) {
            if (l.head.tag == FORALL) instNeeded = true;
        }

        if (instNeeded)
            return
            infer.instantiateMethod(tvars,
                                    (MethodType)mt,
                                    argtypes,
                                    allowBoxing,
                                    useVarargs,
                                    warn);
        return
            argumentsAcceptable(argtypes, mt.getParameterTypes(),
                                allowBoxing, useVarargs, warn)
            ? mt
            : null;
    }

    /** Same but returns null instead throwing a NoInstanceException
     */
    Type instantiate(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     boolean allowBoxing,
                     boolean useVarargs,
                     Warner warn) {
        try {
            return rawInstantiate(env, site, m, argtypes, typeargtypes,
                                  allowBoxing, useVarargs, warn);
        } catch (Infer.NoInstanceException ex) {
            return null;
        }
    }

    /** Check if a parameter list accepts a list of args.
     */
    boolean argumentsAcceptable(List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn) {
        Type varargsFormal = useVarargs ? formals.last() : null;
        while (argtypes.nonEmpty() && formals.head != varargsFormal) {
            boolean works = allowBoxing
                ? types.isConvertible(argtypes.head, formals.head, warn)
                : types.isSubtypeUnchecked(argtypes.head, formals.head, warn);
            if (!works) return false;
            argtypes = argtypes.tail;
            formals = formals.tail;
        }
        if (formals.head != varargsFormal) return false; // not enough args
        if (!useVarargs)
            return argtypes.isEmpty();
        Type elt = types.elemtype(varargsFormal);
        while (argtypes.nonEmpty()) {
            if (!types.isConvertible(argtypes.head, elt, warn))
                return false;
            argtypes = argtypes.tail;
        }
        return true;
    }

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
        assert sym.kind < AMBIGUOUS;
        try {
            if (rawInstantiate(env, site, sym, argtypes, typeargtypes,
                               allowBoxing, useVarargs, Warner.noWarnings) == null) {
                // inapplicable
                switch (bestSoFar.kind) {
                case ABSENT_MTH: return wrongMethod.setWrongSym(sym);
                case WRONG_MTH: return wrongMethods;
                default: return bestSoFar;
                }
            }
        } catch (Infer.NoInstanceException ex) {
            switch (bestSoFar.kind) {
            case ABSENT_MTH:
                return wrongMethod.setWrongSym(sym, ex.getDiagnostic());
            case WRONG_MTH:
                return wrongMethods;
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
            : mostSpecific(sym, bestSoFar, env, site,
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
    Symbol mostSpecific(Symbol m1,
                        Symbol m2,
                        Env<AttrContext> env,
                        final Type site,
                        boolean allowBoxing,
                        boolean useVarargs) {
        switch (m2.kind) {
        case MTH:
            if (m1 == m2) return m1;
            Type mt1 = types.memberType(site, m1);
            noteWarner.unchecked = false;
            boolean m1SignatureMoreSpecific =
                (instantiate(env, site, m2, types.lowerBoundArgtypes(mt1), null,
                             allowBoxing, false, noteWarner) != null ||
                 useVarargs && instantiate(env, site, m2, types.lowerBoundArgtypes(mt1), null,
                                           allowBoxing, true, noteWarner) != null) &&
                !noteWarner.unchecked;
            Type mt2 = types.memberType(site, m2);
            noteWarner.unchecked = false;
            boolean m2SignatureMoreSpecific =
                (instantiate(env, site, m1, types.lowerBoundArgtypes(mt2), null,
                             allowBoxing, false, noteWarner) != null ||
                 useVarargs && instantiate(env, site, m1, types.lowerBoundArgtypes(mt2), null,
                                           allowBoxing, true, noteWarner) != null) &&
                !noteWarner.unchecked;
            if (m1SignatureMoreSpecific && m2SignatureMoreSpecific) {
                if (!types.overrideEquivalent(mt1, mt2))
                    return new AmbiguityError(m1, m2);
                // same signature; select (a) the non-bridge method, or
                // (b) the one that overrides the other, or (c) the concrete
                // one, or (d) merge both abstract signatures
                if ((m1.flags() & BRIDGE) != (m2.flags() & BRIDGE)) {
                    return ((m1.flags() & BRIDGE) != 0) ? m2 : m1;
                }
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
                    return new AmbiguityError(m1, m2);
                // check that both signatures have the same erasure
                if (!types.isSameTypes(m1.erasure(types).getParameterTypes(),
                                       m2.erasure(types).getParameterTypes()))
                    return new AmbiguityError(m1, m2);
                // both abstract, neither overridden; merge throws clause and result type
                Symbol mostSpecific;
                Type result2 = mt2.getReturnType();
                if (mt2.tag == FORALL)
                    result2 = types.subst(result2, ((ForAll)mt2).tvars, ((ForAll)mt1).tvars);
                if (types.isSubtype(mt1.getReturnType(), result2)) {
                    mostSpecific = m1;
                } else if (types.isSubtype(result2, mt1.getReturnType())) {
                    mostSpecific = m2;
                } else {
                    // Theoretically, this can't happen, but it is possible
                    // due to error recovery or mixing incompatible class files
                    return new AmbiguityError(m1, m2);
                }
                MethodSymbol result = new MethodSymbol(
                        mostSpecific.flags(),
                        mostSpecific.name,
                        null,
                        mostSpecific.owner) {
                    @Override
                    public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult) {
                        if (origin == site.tsym)
                            return this;
                        else
                            return super.implementation(origin, types, checkResult);
                    }
                };
                result.type = (Type)mostSpecific.type.clone();
                result.type.setThrown(chk.intersect(mt1.getThrownTypes(),
                                                    mt2.getThrownTypes()));
                return result;
            }
            if (m1SignatureMoreSpecific) return m1;
            if (m2SignatureMoreSpecific) return m2;
            return new AmbiguityError(m1, m2);
        case AMBIGUOUS:
            AmbiguityError e = (AmbiguityError)m2;
            Symbol err1 = mostSpecific(m1, e.sym1, env, site, allowBoxing, useVarargs);
            Symbol err2 = mostSpecific(m1, e.sym2, env, site, allowBoxing, useVarargs);
            if (err1 == err2) return err1;
            if (err1 == e.sym1 && err2 == e.sym2) return m2;
            if (err1 instanceof AmbiguityError &&
                err2 instanceof AmbiguityError &&
                ((AmbiguityError)err1).sym1 == ((AmbiguityError)err2).sym1)
                return new AmbiguityError(m1, m2);
            else
                return new AmbiguityError(err1, err2);
        default:
            throw new AssertionError();
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
        return findMethod(env,
                          site,
                          name,
                          argtypes,
                          typeargtypes,
                          site.tsym.type,
                          true,
                          methodNotFound,
                          allowBoxing,
                          useVarargs,
                          operator);
    }
    // where
    private Symbol findMethod(Env<AttrContext> env,
                              Type site,
                              Name name,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Type intype,
                              boolean abstractok,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs,
                              boolean operator) {
        for (Type ct = intype; ct.tag == CLASS || ct.tag == TYPEVAR; ct = types.supertype(ct)) {
            while (ct.tag == TYPEVAR)
                ct = ct.getUpperBound();
            ClassSymbol c = (ClassSymbol)ct.tsym;
            if ((c.flags() & (ABSTRACT | INTERFACE | ENUM)) == 0)
                abstractok = false;
            for (Scope.Entry e = c.members().lookup(name);
                 e.scope != null;
                 e = e.next()) {
                //- System.out.println(" e " + e.sym);
                if (e.sym.kind == MTH &&
                    (e.sym.flags_field & SYNTHETIC) == 0) {
                    bestSoFar = selectBest(env, site, argtypes, typeargtypes,
                                           e.sym, bestSoFar,
                                           allowBoxing,
                                           useVarargs,
                                           operator);
                }
            }
            //- System.out.println(" - " + bestSoFar);
            if (abstractok) {
                Symbol concrete = methodNotFound;
                if ((bestSoFar.flags() & ABSTRACT) == 0)
                    concrete = bestSoFar;
                for (List<Type> l = types.interfaces(c.type);
                     l.nonEmpty();
                     l = l.tail) {
                    bestSoFar = findMethod(env, site, name, argtypes,
                                           typeargtypes,
                                           l.head, abstractok, bestSoFar,
                                           allowBoxing, useVarargs, operator);
                }
                if (concrete != bestSoFar &&
                    concrete.kind < ERR  && bestSoFar.kind < ERR &&
                    types.isSubSignature(concrete.type, bestSoFar.type))
                    bestSoFar = concrete;
            }
        }
        return bestSoFar;
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

    /** Find or create an implicit method of exactly the given type (after erasure).
     *  Searches in a side table, not the main scope of the site.
     *  This emulates the lookup process required by JSR 292 in JVM.
     *  @param env       The current environment.
     *  @param site      The original type from where the selection
     *                   takes place.
     *  @param name      The method's name.
     *  @param argtypes  The method's value arguments.
     *  @param typeargtypes The method's type arguments
     */
    Symbol findImplicitMethod(Env<AttrContext> env,
                              Type site,
                              Name name,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        assert allowInvokedynamic;
        assert site == syms.invokeDynamicType || (site == syms.methodHandleType && name == names.invoke);
        ClassSymbol c = (ClassSymbol) site.tsym;
        Scope implicit = c.members().next;
        if (implicit == null) {
            c.members().next = implicit = new Scope(c);
        }
        Type restype;
        if (typeargtypes.isEmpty()) {
            restype = syms.objectType;
        } else {
            restype = typeargtypes.head;
            if (!typeargtypes.tail.isEmpty())
                return methodNotFound;
        }
        List<Type> paramtypes = Type.map(argtypes, implicitArgType);
        MethodType mtype = new MethodType(paramtypes,
                                          restype,
                                          List.<Type>nil(),
                                          syms.methodClass);
        int flags = PUBLIC | ABSTRACT;
        if (site == syms.invokeDynamicType)  flags |= STATIC;
        Symbol m = null;
        for (Scope.Entry e = implicit.lookup(name);
             e.scope != null;
             e = e.next()) {
            Symbol sym = e.sym;
            assert sym.kind == MTH;
            if (types.isSameType(mtype, sym.type)
                && (sym.flags() & STATIC) == (flags & STATIC)) {
                m = sym;
                break;
            }
        }
        if (m == null) {
            // create the desired method
            m = new MethodSymbol(flags, name, mtype, c);
            implicit.enter(m);
        }
        assert argumentsAcceptable(argtypes, types.memberType(site, m).getParameterTypes(),
                                   false, false, Warner.noWarnings);
        assert null != instantiate(env, site, m, argtypes, typeargtypes, false, false, Warner.noWarnings);
        return m;
    }
    //where
        Mapping implicitArgType = new Mapping ("implicitArgType") {
                public Type apply(Type t) { return implicitArgType(t); }
            };
        Type implicitArgType(Type argType) {
            argType = types.erasure(argType);
            if (argType.tag == BOT)
                // nulls type as the marker type Null (which has no instances)
                // TO DO: figure out how to access java.lang.Null safely, else throw nice error
                //argType = types.boxedClass(syms.botType).type;
                argType = types.boxedClass(syms.voidType).type;  // REMOVE
            return argType;
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

        if (env.tree.getTag() != JCTree.IMPORT) {
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
     *  symbol (--> flyweight pattern). This improves performance since we
     *  expect misses to happen frequently.
     *
     *  @param sym       The symbol that was found, or a ResolveError.
     *  @param pos       The position to use for error reporting.
     *  @param site      The original type from where the selection took place.
     *  @param name      The symbol's name.
     *  @param argtypes  The invocation's value arguments,
     *                   if we looked for a method.
     *  @param typeargtypes  The invocation's type arguments,
     *                   if we looked for a method.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes) {
        if (sym.kind >= AMBIGUOUS) {
//          printscopes(site.tsym.members());//DEBUG
            if (!site.isErroneous() &&
                !Type.isErroneous(argtypes) &&
                (typeargtypes==null || !Type.isErroneous(typeargtypes)))
                ((ResolveError)sym).report(log, pos, site, name, argtypes, typeargtypes);
            do {
                sym = ((ResolveError)sym).sym;
            } while (sym.kind >= AMBIGUOUS);
            if (sym == syms.errSymbol // preserve the symbol name through errors
                || ((sym.kind & ERRONEOUS) == 0 // make sure an error symbol is returned
                    && (sym.kind & TYP) != 0))
                sym = types.createErrorType(name, qualified ? site.tsym : syms.noSymbol, sym.type).tsym;
        }
        return sym;
    }

    /** Same as above, but without type arguments and arguments.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified) {
        if (sym.kind >= AMBIGUOUS)
            return access(sym, pos, site, name, qualified, List.<Type>nil(), null);
        else
            return sym;
    }

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
        return access(
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
        Symbol sym = methodNotFound;
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            sym = findFun(env, name, argtypes, typeargtypes,
                    steps.head.isBoxingRequired,
                    env.info.varArgs = steps.head.isVarargsRequired);
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
            MethodResolutionPhase errPhase =
                    firstErroneousResolutionPhase();
            sym = access(methodResolutionCache.get(errPhase),
                    pos, env.enclClass.sym.type, name, false, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired;
        }
        return sym;
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
        Symbol sym = methodNotFound;
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            sym = findMethod(env, site, name, argtypes, typeargtypes,
                    steps.head.isBoxingRequired(),
                    env.info.varArgs = steps.head.isVarargsRequired(), false);
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS &&
            allowInvokedynamic &&
            (site == syms.invokeDynamicType ||
             site == syms.methodHandleType && name == names.invoke)) {
            // lookup failed; supply an exactly-typed implicit method
            sym = findImplicitMethod(env, site, name, argtypes, typeargtypes);
            env.info.varArgs = false;
        }
        if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
            MethodResolutionPhase errPhase =
                    firstErroneousResolutionPhase();
            sym = access(methodResolutionCache.get(errPhase),
                    pos, site, name, true, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired;
        }
        return sym;
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
        Symbol sym = resolveQualifiedMethod(
            pos, env, site, name, argtypes, typeargtypes);
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
        Symbol sym = methodNotFound;
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            sym = resolveConstructor(pos, env, site, argtypes, typeargtypes,
                    steps.head.isBoxingRequired(),
                    env.info.varArgs = steps.head.isVarargsRequired());
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
            MethodResolutionPhase errPhase = firstErroneousResolutionPhase();
            sym = access(methodResolutionCache.get(errPhase),
                    pos, site, names.init, true, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired();
        }
        return sym;
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
        Symbol sym = findMethod(env, site,
                                names.init, argtypes,
                                typeargtypes, allowBoxing,
                                useVarargs, false);
        if ((sym.flags() & DEPRECATED) != 0 &&
            (env.info.scope.owner.flags() & DEPRECATED) == 0 &&
            env.info.scope.owner.outermostClass() != sym.outermostClass())
            chk.warnDeprecated(pos, sym);
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
        Symbol sym = resolveConstructor(
            pos, env, site, argtypes, typeargtypes);
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
    Symbol resolveOperator(DiagnosticPosition pos, int optag,
                           Env<AttrContext> env, List<Type> argtypes) {
        Name name = treeinfo.operatorName(optag);
        Symbol sym = findMethod(env, syms.predefClass.type, name, argtypes,
                                null, false, false, true);
        if (boxingEnabled && sym.kind >= WRONG_MTHS)
            sym = findMethod(env, syms.predefClass.type, name, argtypes,
                             null, true, false, true);
        return access(sym, pos, env.enclClass.sym.type, name,
                      false, argtypes, null);
    }

    /** Resolve operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param arg       The type of the operand.
     */
    Symbol resolveUnaryOperator(DiagnosticPosition pos, int optag, Env<AttrContext> env, Type arg) {
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
                                 int optag,
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
                    return access(sym, pos, env.enclClass.sym.type,
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
                                 Symbol member) {
        Name name = names._this;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            if (env1.enclClass.sym.isSubClass(member.owner, types) &&
                isAccessible(env, env1.enclClass.sym.type, member)) {
                Symbol sym = env1.info.scope.lookup(name).sym;
                if (sym != null) {
                    if (staticOnly) sym = new StaticError(sym);
                    return access(sym, pos, env.enclClass.sym.type,
                                  name, true);
                }
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0)
                staticOnly = true;
            env1 = env1.outer;
        }
        log.error(pos, "encl.class.required", member);
        return syms.errSymbol;
    }

    /**
     * Resolve an appropriate implicit this instance for t's container.
     * JLS2 8.8.5.1 and 15.9.2
     */
    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t) {
        Type thisType = (((t.tsym.owner.kind & (MTH|VAR)) != 0)
                         ? resolveSelf(pos, env, t.getEnclosingType().tsym, names._this)
                         : resolveSelfContaining(pos, env, t.tsym)).type;
        if (env.info.isSelfCall && thisType.tsym == env.enclClass.sym)
            log.error(pos, "cant.ref.before.ctor.called", "this");
        return thisType;
    }

/* ***************************************************************************
 *  ResolveError classes, indicating error situations when accessing symbols
 ****************************************************************************/

    public void logAccessError(Env<AttrContext> env, JCTree tree, Type type) {
        AccessError error = new AccessError(env, type.getEnclosingType(), type.tsym);
        error.report(log, tree.pos(), type.getEnclosingType(), null, null, null);
    }

    private final LocalizedString noArgs = new LocalizedString("compiler.misc.no.args");

    public Object methodArguments(List<Type> argtypes) {
        return argtypes.isEmpty() ? noArgs : argtypes;
    }

    /** Root class for resolve errors.
     *  Instances of this class indicate "Symbol not found".
     *  Instances of subclass indicate other errors.
     */
    private class ResolveError extends Symbol {

        ResolveError(int kind, Symbol sym, String debugName) {
            super(kind, 0, null, null, null);
            this.debugName = debugName;
            this.sym = sym;
        }

        /** The name of the kind of error, for debugging only.
         */
        final String debugName;

        /** The symbol that was determined by resolution, or errSymbol if none
         *  was found.
         */
        final Symbol sym;

        /** The symbol that was a close mismatch, or null if none was found.
         *  wrongSym is currently set if a simgle method with the correct name, but
         *  the wrong parameters was found.
         */
        Symbol wrongSym;

        /** An auxiliary explanation set in case of instantiation errors.
         */
        JCDiagnostic explanation;


        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            throw new AssertionError();
        }

        /** Print the (debug only) name of the kind of error.
         */
        public String toString() {
            return debugName + " wrongSym=" + wrongSym + " explanation=" + explanation;
        }

        /** Update wrongSym and explanation and return this.
         */
        ResolveError setWrongSym(Symbol sym, JCDiagnostic explanation) {
            this.wrongSym = sym;
            this.explanation = explanation;
            return this;
        }

        /** Update wrongSym and return this.
         */
        ResolveError setWrongSym(Symbol sym) {
            this.wrongSym = sym;
            this.explanation = null;
            return this;
        }

        public boolean exists() {
            switch (kind) {
            case HIDDEN:
            case ABSENT_VAR:
            case ABSENT_MTH:
            case ABSENT_TYP:
                return false;
            default:
                return true;
            }
        }

        /** Report error.
         *  @param log       The error log to be used for error reporting.
         *  @param pos       The position to be used for error reporting.
         *  @param site      The original type from where the selection took place.
         *  @param name      The name of the symbol to be resolved.
         *  @param argtypes  The invocation's value arguments,
         *                   if we looked for a method.
         *  @param typeargtypes  The invocation's type arguments,
         *                   if we looked for a method.
         */
        void report(Log log, DiagnosticPosition pos, Type site, Name name,
                    List<Type> argtypes, List<Type> typeargtypes) {
            if (argtypes == null)
                argtypes = List.nil();
            if (typeargtypes == null)
                typeargtypes = List.nil();
            if (name != names.error) {
                KindName kindname = absentKind(kind);
                Name idname = name;
                if (kind >= WRONG_MTHS && kind <= ABSENT_MTH) {
                    if (isOperator(name)) {
                        log.error(pos, "operator.cant.be.applied",
                                  name, argtypes);
                        return;
                    }
                    if (name == names.init) {
                        kindname = KindName.CONSTRUCTOR;
                        idname = site.tsym.name;
                    }
                }
                if (kind == WRONG_MTH) {
                    Symbol ws = wrongSym.asMemberOf(site, types);
                    log.error(pos,
                              "cant.apply.symbol" + (explanation != null ? ".1" : ""),
                              kindname,
                              ws.name == names.init ? ws.owner.name : ws.name,
                              methodArguments(ws.type.getParameterTypes()),
                              methodArguments(argtypes),
                              kindName(ws.owner),
                              ws.owner.type,
                              explanation);
                } else if (!site.tsym.name.isEmpty()) {
                    if (site.tsym.kind == PCK && !site.tsym.exists())
                        log.error(pos, "doesnt.exist", site.tsym);
                    else {
                        String errKey = getErrorKey("cant.resolve.location",
                                                    argtypes, typeargtypes,
                                                    kindname);
                        log.error(pos, errKey, kindname, idname, //symbol kindname, name
                                  typeargtypes, argtypes, //type parameters and arguments (if any)
                                  typeKindName(site), site); //location kindname, type
                    }
                } else {
                    String errKey = getErrorKey("cant.resolve",
                                                argtypes, typeargtypes,
                                                kindname);
                    log.error(pos, errKey, kindname, idname, //symbol kindname, name
                              typeargtypes, argtypes); //type parameters and arguments (if any)
                }
            }
        }
        //where
        String getErrorKey(String key, List<Type> argtypes, List<Type> typeargtypes, KindName kindname) {
            String suffix = "";
            switch (kindname) {
                case METHOD:
                case CONSTRUCTOR: {
                    suffix += ".args";
                    suffix += typeargtypes.nonEmpty() ? ".params" : "";
                }
            }
            return key + suffix;
        }

        /** A name designates an operator if it consists
         *  of a non-empty sequence of operator symbols +-~!/*%&|^<>=
         */
        boolean isOperator(Name name) {
            int i = 0;
            while (i < name.getByteLength() &&
                   "+-~!*/%&|^<>=".indexOf(name.getByteAt(i)) >= 0) i++;
            return i > 0 && i == name.getByteLength();
        }
    }

    /** Resolve error class indicating that a symbol is not accessible.
     */
    class AccessError extends ResolveError {

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

        private Env<AttrContext> env;
        private Type site;

        /** Report error.
         *  @param log       The error log to be used for error reporting.
         *  @param pos       The position to be used for error reporting.
         *  @param site      The original type from where the selection took place.
         *  @param name      The name of the symbol to be resolved.
         *  @param argtypes  The invocation's value arguments,
         *                   if we looked for a method.
         *  @param typeargtypes  The invocation's type arguments,
         *                   if we looked for a method.
         */
        void report(Log log, DiagnosticPosition pos, Type site, Name name,
                    List<Type> argtypes, List<Type> typeargtypes) {
            if (sym.owner.type.tag != ERROR) {
                if (sym.name == names.init && sym.owner != site.tsym)
                    new ResolveError(ABSENT_MTH, sym.owner, "absent method " + sym).report(
                        log, pos, site, name, argtypes, typeargtypes);
                if ((sym.flags() & PUBLIC) != 0
                    || (env != null && this.site != null
                        && !isAccessible(env, this.site)))
                    log.error(pos, "not.def.access.class.intf.cant.access",
                        sym, sym.location());
                else if ((sym.flags() & (PRIVATE | PROTECTED)) != 0)
                    log.error(pos, "report.access", sym,
                              asFlagSet(sym.flags() & (PRIVATE | PROTECTED)),
                              sym.location());
                else
                    log.error(pos, "not.def.public.cant.access",
                              sym, sym.location());
            }
        }
    }

    /** Resolve error class indicating that an instance member was accessed
     *  from a static context.
     */
    class StaticError extends ResolveError {
        StaticError(Symbol sym) {
            super(STATICERR, sym, "static error");
        }

        /** Report error.
         *  @param log       The error log to be used for error reporting.
         *  @param pos       The position to be used for error reporting.
         *  @param site      The original type from where the selection took place.
         *  @param name      The name of the symbol to be resolved.
         *  @param argtypes  The invocation's value arguments,
         *                   if we looked for a method.
         *  @param typeargtypes  The invocation's type arguments,
         *                   if we looked for a method.
         */
        void report(Log log,
                    DiagnosticPosition pos,
                    Type site,
                    Name name,
                    List<Type> argtypes,
                    List<Type> typeargtypes) {
            Symbol errSym = ((sym.kind == TYP && sym.type.tag == CLASS)
                ? types.erasure(sym.type).tsym
                : sym);
            log.error(pos, "non-static.cant.be.ref",
                      kindName(sym), errSym);
        }
    }

    /** Resolve error class indicating an ambiguous reference.
     */
    class AmbiguityError extends ResolveError {
        Symbol sym1;
        Symbol sym2;

        AmbiguityError(Symbol sym1, Symbol sym2) {
            super(AMBIGUOUS, sym1, "ambiguity error");
            this.sym1 = sym1;
            this.sym2 = sym2;
        }

        /** Report error.
         *  @param log       The error log to be used for error reporting.
         *  @param pos       The position to be used for error reporting.
         *  @param site      The original type from where the selection took place.
         *  @param name      The name of the symbol to be resolved.
         *  @param argtypes  The invocation's value arguments,
         *                   if we looked for a method.
         *  @param typeargtypes  The invocation's type arguments,
         *                   if we looked for a method.
         */
        void report(Log log, DiagnosticPosition pos, Type site, Name name,
                    List<Type> argtypes, List<Type> typeargtypes) {
            AmbiguityError pair = this;
            while (true) {
                if (pair.sym1.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym1;
                else if (pair.sym2.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym2;
                else break;
            }
            Name sname = pair.sym1.name;
            if (sname == names.init) sname = pair.sym1.owner.name;
            log.error(pos, "ref.ambiguous", sname,
                      kindName(pair.sym1),
                      pair.sym1,
                      pair.sym1.location(site, types),
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

    private Map<MethodResolutionPhase, Symbol> methodResolutionCache =
        new HashMap<MethodResolutionPhase, Symbol>(MethodResolutionPhase.values().length);

    final List<MethodResolutionPhase> methodResolutionSteps = List.of(BASIC, BOX, VARARITY);

    private MethodResolutionPhase firstErroneousResolutionPhase() {
        MethodResolutionPhase bestSoFar = BASIC;
        Symbol sym = methodNotFound;
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= WRONG_MTHS) {
            sym = methodResolutionCache.get(steps.head);
            bestSoFar = steps.head;
            steps = steps.tail;
        }
        return bestSoFar;
    }
}
