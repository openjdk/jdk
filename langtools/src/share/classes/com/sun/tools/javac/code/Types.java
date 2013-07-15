/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Attribute.RetentionPolicy;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Type.UndetVar.InferenceBound;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.util.*;
import static com.sun.tools.javac.code.BoundKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Scope.*;
import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.code.Type.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.jvm.ClassFile.externalize;
import static com.sun.tools.javac.util.ListBuffer.lb;

/**
 * Utility class containing various operations on types.
 *
 * <p>Unless other names are more illustrative, the following naming
 * conventions should be observed in this file:
 *
 * <dl>
 * <dt>t</dt>
 * <dd>If the first argument to an operation is a type, it should be named t.</dd>
 * <dt>s</dt>
 * <dd>Similarly, if the second argument to an operation is a type, it should be named s.</dd>
 * <dt>ts</dt>
 * <dd>If an operations takes a list of types, the first should be named ts.</dd>
 * <dt>ss</dt>
 * <dd>A second list of types should be named ss.</dd>
 * </dl>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class Types {
    protected static final Context.Key<Types> typesKey =
        new Context.Key<Types>();

    final Symtab syms;
    final JavacMessages messages;
    final Names names;
    final boolean allowBoxing;
    final boolean allowCovariantReturns;
    final boolean allowObjectToPrimitiveCast;
    final boolean allowDefaultMethods;
    final ClassReader reader;
    final Check chk;
    final Enter enter;
    JCDiagnostic.Factory diags;
    List<Warner> warnStack = List.nil();
    final Name capturedName;
    private final FunctionDescriptorLookupError functionDescriptorLookupError;

    public final Warner noWarnings;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    public static Types instance(Context context) {
        Types instance = context.get(typesKey);
        if (instance == null)
            instance = new Types(context);
        return instance;
    }

    protected Types(Context context) {
        context.put(typesKey, this);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        Source source = Source.instance(context);
        allowBoxing = source.allowBoxing();
        allowCovariantReturns = source.allowCovariantReturns();
        allowObjectToPrimitiveCast = source.allowObjectToPrimitiveCast();
        allowDefaultMethods = source.allowDefaultMethods();
        reader = ClassReader.instance(context);
        chk = Check.instance(context);
        enter = Enter.instance(context);
        capturedName = names.fromString("<captured wildcard>");
        messages = JavacMessages.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        functionDescriptorLookupError = new FunctionDescriptorLookupError();
        noWarnings = new Warner(null);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="upperBound">
    /**
     * The "rvalue conversion".<br>
     * The upper bound of most types is the type
     * itself.  Wildcards, on the other hand have upper
     * and lower bounds.
     * @param t a type
     * @return the upper bound of the given type
     */
    public Type upperBound(Type t) {
        return upperBound.visit(t);
    }
    // where
        private final MapVisitor<Void> upperBound = new MapVisitor<Void>() {

            @Override
            public Type visitWildcardType(WildcardType t, Void ignored) {
                if (t.isSuperBound())
                    return t.bound == null ? syms.objectType : t.bound.bound;
                else
                    return visit(t.type);
            }

            @Override
            public Type visitCapturedType(CapturedType t, Void ignored) {
                return visit(t.bound);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="lowerBound">
    /**
     * The "lvalue conversion".<br>
     * The lower bound of most types is the type
     * itself.  Wildcards, on the other hand have upper
     * and lower bounds.
     * @param t a type
     * @return the lower bound of the given type
     */
    public Type lowerBound(Type t) {
        return lowerBound.visit(t);
    }
    // where
        private final MapVisitor<Void> lowerBound = new MapVisitor<Void>() {

            @Override
            public Type visitWildcardType(WildcardType t, Void ignored) {
                return t.isExtendsBound() ? syms.botType : visit(t.type);
            }

            @Override
            public Type visitCapturedType(CapturedType t, Void ignored) {
                return visit(t.getLowerBound());
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isUnbounded">
    /**
     * Checks that all the arguments to a class are unbounded
     * wildcards or something else that doesn't make any restrictions
     * on the arguments. If a class isUnbounded, a raw super- or
     * subclass can be cast to it without a warning.
     * @param t a type
     * @return true iff the given type is unbounded or raw
     */
    public boolean isUnbounded(Type t) {
        return isUnbounded.visit(t);
    }
    // where
        private final UnaryVisitor<Boolean> isUnbounded = new UnaryVisitor<Boolean>() {

            public Boolean visitType(Type t, Void ignored) {
                return true;
            }

            @Override
            public Boolean visitClassType(ClassType t, Void ignored) {
                List<Type> parms = t.tsym.type.allparams();
                List<Type> args = t.allparams();
                while (parms.nonEmpty()) {
                    WildcardType unb = new WildcardType(syms.objectType,
                                                        BoundKind.UNBOUND,
                                                        syms.boundClass,
                                                        (TypeVar)parms.head.unannotatedType());
                    if (!containsType(args.head, unb))
                        return false;
                    parms = parms.tail;
                    args = args.tail;
                }
                return true;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="asSub">
    /**
     * Return the least specific subtype of t that starts with symbol
     * sym.  If none exists, return null.  The least specific subtype
     * is determined as follows:
     *
     * <p>If there is exactly one parameterized instance of sym that is a
     * subtype of t, that parameterized instance is returned.<br>
     * Otherwise, if the plain type or raw type `sym' is a subtype of
     * type t, the type `sym' itself is returned.  Otherwise, null is
     * returned.
     */
    public Type asSub(Type t, Symbol sym) {
        return asSub.visit(t, sym);
    }
    // where
        private final SimpleVisitor<Type,Symbol> asSub = new SimpleVisitor<Type,Symbol>() {

            public Type visitType(Type t, Symbol sym) {
                return null;
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                if (t.tsym == sym)
                    return t;
                Type base = asSuper(sym.type, t.tsym);
                if (base == null)
                    return null;
                ListBuffer<Type> from = new ListBuffer<Type>();
                ListBuffer<Type> to = new ListBuffer<Type>();
                try {
                    adapt(base, t, from, to);
                } catch (AdaptFailure ex) {
                    return null;
                }
                Type res = subst(sym.type, from.toList(), to.toList());
                if (!isSubtype(res, t))
                    return null;
                ListBuffer<Type> openVars = new ListBuffer<Type>();
                for (List<Type> l = sym.type.allparams();
                     l.nonEmpty(); l = l.tail)
                    if (res.contains(l.head) && !t.contains(l.head))
                        openVars.append(l.head);
                if (openVars.nonEmpty()) {
                    if (t.isRaw()) {
                        // The subtype of a raw type is raw
                        res = erasure(res);
                    } else {
                        // Unbound type arguments default to ?
                        List<Type> opens = openVars.toList();
                        ListBuffer<Type> qs = new ListBuffer<Type>();
                        for (List<Type> iter = opens; iter.nonEmpty(); iter = iter.tail) {
                            qs.append(new WildcardType(syms.objectType, BoundKind.UNBOUND, syms.boundClass, (TypeVar) iter.head.unannotatedType()));
                        }
                        res = subst(res, opens, qs.toList());
                    }
                }
                return res;
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isConvertible">
    /**
     * Is t a subtype of or convertible via boxing/unboxing
     * conversion to s?
     */
    public boolean isConvertible(Type t, Type s, Warner warn) {
        if (t.hasTag(ERROR)) {
            return true;
        }
        boolean tPrimitive = t.isPrimitive();
        boolean sPrimitive = s.isPrimitive();
        if (tPrimitive == sPrimitive) {
            return isSubtypeUnchecked(t, s, warn);
        }
        if (!allowBoxing) return false;
        return tPrimitive
            ? isSubtype(boxedClass(t).type, s)
            : isSubtype(unboxedType(t), s);
    }

    /**
     * Is t a subtype of or convertiable via boxing/unboxing
     * convertions to s?
     */
    public boolean isConvertible(Type t, Type s) {
        return isConvertible(t, s, noWarnings);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="findSam">

    /**
     * Exception used to report a function descriptor lookup failure. The exception
     * wraps a diagnostic that can be used to generate more details error
     * messages.
     */
    public static class FunctionDescriptorLookupError extends RuntimeException {
        private static final long serialVersionUID = 0;

        JCDiagnostic diagnostic;

        FunctionDescriptorLookupError() {
            this.diagnostic = null;
        }

        FunctionDescriptorLookupError setMessage(JCDiagnostic diag) {
            this.diagnostic = diag;
            return this;
        }

        public JCDiagnostic getDiagnostic() {
            return diagnostic;
        }
    }

    /**
     * A cache that keeps track of function descriptors associated with given
     * functional interfaces.
     */
    class DescriptorCache {

        private WeakHashMap<TypeSymbol, Entry> _map = new WeakHashMap<TypeSymbol, Entry>();

        class FunctionDescriptor {
            Symbol descSym;

            FunctionDescriptor(Symbol descSym) {
                this.descSym = descSym;
            }

            public Symbol getSymbol() {
                return descSym;
            }

            public Type getType(Type site) {
                site = removeWildcards(site);
                if (!chk.checkValidGenericType(site)) {
                    //if the inferred functional interface type is not well-formed,
                    //or if it's not a subtype of the original target, issue an error
                    throw failure(diags.fragment("no.suitable.functional.intf.inst", site));
                }
                return memberType(site, descSym);
            }
        }

        class Entry {
            final FunctionDescriptor cachedDescRes;
            final int prevMark;

            public Entry(FunctionDescriptor cachedDescRes,
                    int prevMark) {
                this.cachedDescRes = cachedDescRes;
                this.prevMark = prevMark;
            }

            boolean matches(int mark) {
                return  this.prevMark == mark;
            }
        }

        FunctionDescriptor get(TypeSymbol origin) throws FunctionDescriptorLookupError {
            Entry e = _map.get(origin);
            CompoundScope members = membersClosure(origin.type, false);
            if (e == null ||
                    !e.matches(members.getMark())) {
                FunctionDescriptor descRes = findDescriptorInternal(origin, members);
                _map.put(origin, new Entry(descRes, members.getMark()));
                return descRes;
            }
            else {
                return e.cachedDescRes;
            }
        }

        /**
         * Compute the function descriptor associated with a given functional interface
         */
        public FunctionDescriptor findDescriptorInternal(TypeSymbol origin,
                CompoundScope membersCache) throws FunctionDescriptorLookupError {
            if (!origin.isInterface() || (origin.flags() & ANNOTATION) != 0) {
                //t must be an interface
                throw failure("not.a.functional.intf", origin);
            }

            final ListBuffer<Symbol> abstracts = ListBuffer.lb();
            for (Symbol sym : membersCache.getElements(new DescriptorFilter(origin))) {
                Type mtype = memberType(origin.type, sym);
                if (abstracts.isEmpty() ||
                        (sym.name == abstracts.first().name &&
                        overrideEquivalent(mtype, memberType(origin.type, abstracts.first())))) {
                    abstracts.append(sym);
                } else {
                    //the target method(s) should be the only abstract members of t
                    throw failure("not.a.functional.intf.1",  origin,
                            diags.fragment("incompatible.abstracts", Kinds.kindName(origin), origin));
                }
            }
            if (abstracts.isEmpty()) {
                //t must define a suitable non-generic method
                throw failure("not.a.functional.intf.1", origin,
                            diags.fragment("no.abstracts", Kinds.kindName(origin), origin));
            } else if (abstracts.size() == 1) {
                return new FunctionDescriptor(abstracts.first());
            } else { // size > 1
                FunctionDescriptor descRes = mergeDescriptors(origin, abstracts.toList());
                if (descRes == null) {
                    //we can get here if the functional interface is ill-formed
                    ListBuffer<JCDiagnostic> descriptors = ListBuffer.lb();
                    for (Symbol desc : abstracts) {
                        String key = desc.type.getThrownTypes().nonEmpty() ?
                                "descriptor.throws" : "descriptor";
                        descriptors.append(diags.fragment(key, desc.name,
                                desc.type.getParameterTypes(),
                                desc.type.getReturnType(),
                                desc.type.getThrownTypes()));
                    }
                    JCDiagnostic.MultilineDiagnostic incompatibleDescriptors =
                            new JCDiagnostic.MultilineDiagnostic(diags.fragment("incompatible.descs.in.functional.intf",
                            Kinds.kindName(origin), origin), descriptors.toList());
                    throw failure(incompatibleDescriptors);
                }
                return descRes;
            }
        }

        /**
         * Compute a synthetic type for the target descriptor given a list
         * of override-equivalent methods in the functional interface type.
         * The resulting method type is a method type that is override-equivalent
         * and return-type substitutable with each method in the original list.
         */
        private FunctionDescriptor mergeDescriptors(TypeSymbol origin, List<Symbol> methodSyms) {
            //pick argument types - simply take the signature that is a
            //subsignature of all other signatures in the list (as per JLS 8.4.2)
            List<Symbol> mostSpecific = List.nil();
            outer: for (Symbol msym1 : methodSyms) {
                Type mt1 = memberType(origin.type, msym1);
                for (Symbol msym2 : methodSyms) {
                    Type mt2 = memberType(origin.type, msym2);
                    if (!isSubSignature(mt1, mt2)) {
                        continue outer;
                    }
                }
                mostSpecific = mostSpecific.prepend(msym1);
            }
            if (mostSpecific.isEmpty()) {
                return null;
            }


            //pick return types - this is done in two phases: (i) first, the most
            //specific return type is chosen using strict subtyping; if this fails,
            //a second attempt is made using return type substitutability (see JLS 8.4.5)
            boolean phase2 = false;
            Symbol bestSoFar = null;
            while (bestSoFar == null) {
                outer: for (Symbol msym1 : mostSpecific) {
                    Type mt1 = memberType(origin.type, msym1);
                    for (Symbol msym2 : methodSyms) {
                        Type mt2 = memberType(origin.type, msym2);
                        if (phase2 ?
                                !returnTypeSubstitutable(mt1, mt2) :
                                !isSubtypeInternal(mt1.getReturnType(), mt2.getReturnType())) {
                            continue outer;
                        }
                    }
                    bestSoFar = msym1;
                }
                if (phase2) {
                    break;
                } else {
                    phase2 = true;
                }
            }
            if (bestSoFar == null) return null;

            //merge thrown types - form the intersection of all the thrown types in
            //all the signatures in the list
            List<Type> thrown = null;
            for (Symbol msym1 : methodSyms) {
                Type mt1 = memberType(origin.type, msym1);
                thrown = (thrown == null) ?
                    mt1.getThrownTypes() :
                    chk.intersect(mt1.getThrownTypes(), thrown);
            }

            final List<Type> thrown1 = thrown;
            return new FunctionDescriptor(bestSoFar) {
                @Override
                public Type getType(Type origin) {
                    Type mt = memberType(origin, getSymbol());
                    return createMethodTypeWithThrown(mt, thrown1);
                }
            };
        }

        boolean isSubtypeInternal(Type s, Type t) {
            return (s.isPrimitive() && t.isPrimitive()) ?
                    isSameType(t, s) :
                    isSubtype(s, t);
        }

        FunctionDescriptorLookupError failure(String msg, Object... args) {
            return failure(diags.fragment(msg, args));
        }

        FunctionDescriptorLookupError failure(JCDiagnostic diag) {
            return functionDescriptorLookupError.setMessage(diag);
        }
    }

    private DescriptorCache descCache = new DescriptorCache();

    /**
     * Find the method descriptor associated to this class symbol - if the
     * symbol 'origin' is not a functional interface, an exception is thrown.
     */
    public Symbol findDescriptorSymbol(TypeSymbol origin) throws FunctionDescriptorLookupError {
        return descCache.get(origin).getSymbol();
    }

    /**
     * Find the type of the method descriptor associated to this class symbol -
     * if the symbol 'origin' is not a functional interface, an exception is thrown.
     */
    public Type findDescriptorType(Type origin) throws FunctionDescriptorLookupError {
        return descCache.get(origin.tsym).getType(origin);
    }

    /**
     * Is given type a functional interface?
     */
    public boolean isFunctionalInterface(TypeSymbol tsym) {
        try {
            findDescriptorSymbol(tsym);
            return true;
        } catch (FunctionDescriptorLookupError ex) {
            return false;
        }
    }

    public boolean isFunctionalInterface(Type site) {
        try {
            findDescriptorType(site);
            return true;
        } catch (FunctionDescriptorLookupError ex) {
            return false;
        }
    }

    public Type removeWildcards(Type site) {
        Type capturedSite = capture(site);
        if (capturedSite != site) {
            Type formalInterface = site.tsym.type;
            ListBuffer<Type> typeargs = ListBuffer.lb();
            List<Type> actualTypeargs = site.getTypeArguments();
            List<Type> capturedTypeargs = capturedSite.getTypeArguments();
            //simply replace the wildcards with its bound
            for (Type t : formalInterface.getTypeArguments()) {
                if (actualTypeargs.head.hasTag(WILDCARD)) {
                    WildcardType wt = (WildcardType)actualTypeargs.head.unannotatedType();
                    Type bound;
                    switch (wt.kind) {
                        case EXTENDS:
                        case UNBOUND:
                            CapturedType capVar = (CapturedType)capturedTypeargs.head.unannotatedType();
                            //use declared bound if it doesn't depend on formal type-args
                            bound = capVar.bound.containsAny(capturedSite.getTypeArguments()) ?
                                    wt.type : capVar.bound;
                            break;
                        default:
                            bound = wt.type;
                    }
                    typeargs.append(bound);
                } else {
                    typeargs.append(actualTypeargs.head);
                }
                actualTypeargs = actualTypeargs.tail;
                capturedTypeargs = capturedTypeargs.tail;
            }
            return subst(formalInterface, formalInterface.getTypeArguments(), typeargs.toList());
        } else {
            return site;
        }
    }

    /**
     * Create a symbol for a class that implements a given functional interface
     * and overrides its functional descriptor. This routine is used for two
     * main purposes: (i) checking well-formedness of a functional interface;
     * (ii) perform functional interface bridge calculation.
     */
    public ClassSymbol makeFunctionalInterfaceClass(Env<AttrContext> env, Name name, List<Type> targets, long cflags) {
        Assert.check(targets.nonEmpty() && isFunctionalInterface(targets.head));
        Symbol descSym = findDescriptorSymbol(targets.head.tsym);
        Type descType = findDescriptorType(targets.head);
        ClassSymbol csym = new ClassSymbol(cflags, name, env.enclClass.sym.outermostClass());
        csym.completer = null;
        csym.members_field = new Scope(csym);
        MethodSymbol instDescSym = new MethodSymbol(descSym.flags(), descSym.name, descType, csym);
        csym.members_field.enter(instDescSym);
        Type.ClassType ctype = new Type.ClassType(Type.noType, List.<Type>nil(), csym);
        ctype.supertype_field = syms.objectType;
        ctype.interfaces_field = targets;
        csym.type = ctype;
        csym.sourcefile = ((ClassSymbol)csym.owner).sourcefile;
        return csym;
    }

    /**
     * Find the minimal set of methods that are overridden by the functional
     * descriptor in 'origin'. All returned methods are assumed to have different
     * erased signatures.
     */
    public List<Symbol> functionalInterfaceBridges(TypeSymbol origin) {
        Assert.check(isFunctionalInterface(origin));
        Symbol descSym = findDescriptorSymbol(origin);
        CompoundScope members = membersClosure(origin.type, false);
        ListBuffer<Symbol> overridden = ListBuffer.lb();
        outer: for (Symbol m2 : members.getElementsByName(descSym.name, bridgeFilter)) {
            if (m2 == descSym) continue;
            else if (descSym.overrides(m2, origin, Types.this, false)) {
                for (Symbol m3 : overridden) {
                    if (isSameType(m3.erasure(Types.this), m2.erasure(Types.this)) ||
                            (m3.overrides(m2, origin, Types.this, false) &&
                            (pendingBridges((ClassSymbol)origin, m3.enclClass()) ||
                            (((MethodSymbol)m2).binaryImplementation((ClassSymbol)m3.owner, Types.this) != null)))) {
                        continue outer;
                    }
                }
                overridden.add(m2);
            }
        }
        return overridden.toList();
    }
    //where
        private Filter<Symbol> bridgeFilter = new Filter<Symbol>() {
            public boolean accepts(Symbol t) {
                return t.kind == Kinds.MTH &&
                        t.name != names.init &&
                        t.name != names.clinit &&
                        (t.flags() & SYNTHETIC) == 0;
            }
        };
        private boolean pendingBridges(ClassSymbol origin, TypeSymbol s) {
            //a symbol will be completed from a classfile if (a) symbol has
            //an associated file object with CLASS kind and (b) the symbol has
            //not been entered
            if (origin.classfile != null &&
                    origin.classfile.getKind() == JavaFileObject.Kind.CLASS &&
                    enter.getEnv(origin) == null) {
                return false;
            }
            if (origin == s) {
                return true;
            }
            for (Type t : interfaces(origin.type)) {
                if (pendingBridges((ClassSymbol)t.tsym, s)) {
                    return true;
                }
            }
            return false;
        }
    // </editor-fold>

   /**
    * Scope filter used to skip methods that should be ignored (such as methods
    * overridden by j.l.Object) during function interface conversion interface check
    */
    class DescriptorFilter implements Filter<Symbol> {

       TypeSymbol origin;

       DescriptorFilter(TypeSymbol origin) {
           this.origin = origin;
       }

       @Override
       public boolean accepts(Symbol sym) {
           return sym.kind == Kinds.MTH &&
                   (sym.flags() & (ABSTRACT | DEFAULT)) == ABSTRACT &&
                   !overridesObjectMethod(origin, sym) &&
                   (interfaceCandidates(origin.type, (MethodSymbol)sym).head.flags() & DEFAULT) == 0;
       }
    };

    // <editor-fold defaultstate="collapsed" desc="isSubtype">
    /**
     * Is t an unchecked subtype of s?
     */
    public boolean isSubtypeUnchecked(Type t, Type s) {
        return isSubtypeUnchecked(t, s, noWarnings);
    }
    /**
     * Is t an unchecked subtype of s?
     */
    public boolean isSubtypeUnchecked(Type t, Type s, Warner warn) {
        boolean result = isSubtypeUncheckedInternal(t, s, warn);
        if (result) {
            checkUnsafeVarargsConversion(t, s, warn);
        }
        return result;
    }
    //where
        private boolean isSubtypeUncheckedInternal(Type t, Type s, Warner warn) {
            if (t.hasTag(ARRAY) && s.hasTag(ARRAY)) {
                t = t.unannotatedType();
                s = s.unannotatedType();
                if (((ArrayType)t).elemtype.isPrimitive()) {
                    return isSameType(elemtype(t), elemtype(s));
                } else {
                    return isSubtypeUnchecked(elemtype(t), elemtype(s), warn);
                }
            } else if (isSubtype(t, s)) {
                return true;
            } else if (t.hasTag(TYPEVAR)) {
                return isSubtypeUnchecked(t.getUpperBound(), s, warn);
            } else if (!s.isRaw()) {
                Type t2 = asSuper(t, s.tsym);
                if (t2 != null && t2.isRaw()) {
                    if (isReifiable(s)) {
                        warn.silentWarn(LintCategory.UNCHECKED);
                    } else {
                        warn.warn(LintCategory.UNCHECKED);
                    }
                    return true;
                }
            }
            return false;
        }

        private void checkUnsafeVarargsConversion(Type t, Type s, Warner warn) {
            if (!t.hasTag(ARRAY) || isReifiable(t)) {
                return;
            }
            t = t.unannotatedType();
            s = s.unannotatedType();
            ArrayType from = (ArrayType)t;
            boolean shouldWarn = false;
            switch (s.getTag()) {
                case ARRAY:
                    ArrayType to = (ArrayType)s;
                    shouldWarn = from.isVarargs() &&
                            !to.isVarargs() &&
                            !isReifiable(from);
                    break;
                case CLASS:
                    shouldWarn = from.isVarargs();
                    break;
            }
            if (shouldWarn) {
                warn.warn(LintCategory.VARARGS);
            }
        }

    /**
     * Is t a subtype of s?<br>
     * (not defined for Method and ForAll types)
     */
    final public boolean isSubtype(Type t, Type s) {
        return isSubtype(t, s, true);
    }
    final public boolean isSubtypeNoCapture(Type t, Type s) {
        return isSubtype(t, s, false);
    }
    public boolean isSubtype(Type t, Type s, boolean capture) {
        if (t == s)
            return true;

        t = t.unannotatedType();
        s = s.unannotatedType();

        if (t == s)
            return true;

        if (s.isPartial())
            return isSuperType(s, t);

        if (s.isCompound()) {
            for (Type s2 : interfaces(s).prepend(supertype(s))) {
                if (!isSubtype(t, s2, capture))
                    return false;
            }
            return true;
        }

        Type lower = lowerBound(s);
        if (s != lower)
            return isSubtype(capture ? capture(t) : t, lower, false);

        return isSubtype.visit(capture ? capture(t) : t, s);
    }
    // where
        private TypeRelation isSubtype = new TypeRelation()
        {
            @Override
            public Boolean visitType(Type t, Type s) {
                switch (t.getTag()) {
                 case BYTE:
                     return (!s.hasTag(CHAR) && t.getTag().isSubRangeOf(s.getTag()));
                 case CHAR:
                     return (!s.hasTag(SHORT) && t.getTag().isSubRangeOf(s.getTag()));
                 case SHORT: case INT: case LONG:
                 case FLOAT: case DOUBLE:
                     return t.getTag().isSubRangeOf(s.getTag());
                 case BOOLEAN: case VOID:
                     return t.hasTag(s.getTag());
                 case TYPEVAR:
                     return isSubtypeNoCapture(t.getUpperBound(), s);
                 case BOT:
                     return
                         s.hasTag(BOT) || s.hasTag(CLASS) ||
                         s.hasTag(ARRAY) || s.hasTag(TYPEVAR);
                 case WILDCARD: //we shouldn't be here - avoids crash (see 7034495)
                 case NONE:
                     return false;
                 default:
                     throw new AssertionError("isSubtype " + t.getTag());
                 }
            }

            private Set<TypePair> cache = new HashSet<TypePair>();

            private boolean containsTypeRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        return containsType(t.getTypeArguments(),
                                            s.getTypeArguments());
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    return containsType(t.getTypeArguments(),
                                        rewriteSupers(s).getTypeArguments());
                }
            }

            private Type rewriteSupers(Type t) {
                if (!t.isParameterized())
                    return t;
                ListBuffer<Type> from = lb();
                ListBuffer<Type> to = lb();
                adaptSelf(t, from, to);
                if (from.isEmpty())
                    return t;
                ListBuffer<Type> rewrite = lb();
                boolean changed = false;
                for (Type orig : to.toList()) {
                    Type s = rewriteSupers(orig);
                    if (s.isSuperBound() && !s.isExtendsBound()) {
                        s = new WildcardType(syms.objectType,
                                             BoundKind.UNBOUND,
                                             syms.boundClass);
                        changed = true;
                    } else if (s != orig) {
                        s = new WildcardType(upperBound(s),
                                             BoundKind.EXTENDS,
                                             syms.boundClass);
                        changed = true;
                    }
                    rewrite.append(s);
                }
                if (changed)
                    return subst(t.tsym.type, from.toList(), rewrite.toList());
                else
                    return t;
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                Type sup = asSuper(t, s.tsym);
                return sup != null
                    && sup.tsym == s.tsym
                    // You're not allowed to write
                    //     Vector<Object> vec = new Vector<String>();
                    // But with wildcards you can write
                    //     Vector<? extends Object> vec = new Vector<String>();
                    // which means that subtype checking must be done
                    // here instead of same-type checking (via containsType).
                    && (!s.isParameterized() || containsTypeRecursive(s, sup))
                    && isSubtypeNoCapture(sup.getEnclosingType(),
                                          s.getEnclosingType());
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                if (s.hasTag(ARRAY)) {
                    if (t.elemtype.isPrimitive())
                        return isSameType(t.elemtype, elemtype(s));
                    else
                        return isSubtypeNoCapture(t.elemtype, elemtype(s));
                }

                if (s.hasTag(CLASS)) {
                    Name sname = s.tsym.getQualifiedName();
                    return sname == names.java_lang_Object
                        || sname == names.java_lang_Cloneable
                        || sname == names.java_io_Serializable;
                }

                return false;
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                //todo: test against origin needed? or replace with substitution?
                if (t == s || t.qtype == s || s.hasTag(ERROR) || s.hasTag(UNKNOWN)) {
                    return true;
                } else if (s.hasTag(BOT)) {
                    //if 's' is 'null' there's no instantiated type U for which
                    //U <: s (but 'null' itself, which is not a valid type)
                    return false;
                }

                t.addBound(InferenceBound.UPPER, s, Types.this);
                return true;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };

    /**
     * Is t a subtype of every type in given list `ts'?<br>
     * (not defined for Method and ForAll types)<br>
     * Allows unchecked conversions.
     */
    public boolean isSubtypeUnchecked(Type t, List<Type> ts, Warner warn) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (!isSubtypeUnchecked(t, l.head, warn))
                return false;
        return true;
    }

    /**
     * Are corresponding elements of ts subtypes of ss?  If lists are
     * of different length, return false.
     */
    public boolean isSubtypes(List<Type> ts, List<Type> ss) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSubtype(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }

    /**
     * Are corresponding elements of ts subtypes of ss, allowing
     * unchecked conversions?  If lists are of different length,
     * return false.
     **/
    public boolean isSubtypesUnchecked(List<Type> ts, List<Type> ss, Warner warn) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSubtypeUnchecked(ts.head, ss.head, warn)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSuperType">
    /**
     * Is t a supertype of s?
     */
    public boolean isSuperType(Type t, Type s) {
        switch (t.getTag()) {
        case ERROR:
            return true;
        case UNDETVAR: {
            UndetVar undet = (UndetVar)t;
            if (t == s ||
                undet.qtype == s ||
                s.hasTag(ERROR) ||
                s.hasTag(BOT)) {
                return true;
            }
            undet.addBound(InferenceBound.LOWER, s, this);
            return true;
        }
        default:
            return isSubtype(s, t);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSameType">
    /**
     * Are corresponding elements of the lists the same type?  If
     * lists are of different length, return false.
     */
    public boolean isSameTypes(List<Type> ts, List<Type> ss) {
        return isSameTypes(ts, ss, false);
    }
    public boolean isSameTypes(List<Type> ts, List<Type> ss, boolean strict) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSameType(ts.head, ss.head, strict)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }

    /**
    * A polymorphic signature method (JLS SE 7, 8.4.1) is a method that
    * (i) is declared in the java.lang.invoke.MethodHandle class, (ii) takes
    * a single variable arity parameter (iii) whose declared type is Object[],
    * (iv) has a return type of Object and (v) is native.
    */
   public boolean isSignaturePolymorphic(MethodSymbol msym) {
       List<Type> argtypes = msym.type.getParameterTypes();
       return (msym.flags_field & NATIVE) != 0 &&
               msym.owner == syms.methodHandleType.tsym &&
               argtypes.tail.tail == null &&
               argtypes.head.hasTag(TypeTag.ARRAY) &&
               msym.type.getReturnType().tsym == syms.objectType.tsym &&
               ((ArrayType)argtypes.head).elemtype.tsym == syms.objectType.tsym;
   }

    /**
     * Is t the same type as s?
     */
    public boolean isSameType(Type t, Type s) {
        return isSameType(t, s, false);
    }
    public boolean isSameType(Type t, Type s, boolean strict) {
        return strict ?
                isSameTypeStrict.visit(t, s) :
                isSameTypeLoose.visit(t, s);
    }
    public boolean isSameAnnotatedType(Type t, Type s) {
        return isSameAnnotatedType.visit(t, s);
    }
    // where
        abstract class SameTypeVisitor extends TypeRelation {

            public Boolean visitType(Type t, Type s) {
                if (t == s)
                    return true;

                if (s.isPartial())
                    return visit(s, t);

                switch (t.getTag()) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE: case BOOLEAN: case VOID: case BOT: case NONE:
                    return t.hasTag(s.getTag());
                case TYPEVAR: {
                    if (s.hasTag(TYPEVAR)) {
                        //type-substitution does not preserve type-var types
                        //check that type var symbols and bounds are indeed the same
                        return sameTypeVars((TypeVar)t.unannotatedType(), (TypeVar)s.unannotatedType());
                    }
                    else {
                        //special case for s == ? super X, where upper(s) = u
                        //check that u == t, where u has been set by Type.withTypeVar
                        return s.isSuperBound() &&
                                !s.isExtendsBound() &&
                                visit(t, upperBound(s));
                    }
                }
                default:
                    throw new AssertionError("isSameType " + t.getTag());
                }
            }

            abstract boolean sameTypeVars(TypeVar tv1, TypeVar tv2);

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (s.isPartial())
                    return visit(s, t);
                else
                    return false;
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                if (t == s)
                    return true;

                if (s.isPartial())
                    return visit(s, t);

                if (s.isSuperBound() && !s.isExtendsBound())
                    return visit(t, upperBound(s)) && visit(t, lowerBound(s));

                if (t.isCompound() && s.isCompound()) {
                    if (!visit(supertype(t), supertype(s)))
                        return false;

                    HashSet<UniqueType> set = new HashSet<UniqueType>();
                    for (Type x : interfaces(t))
                        set.add(new UniqueType(x, Types.this));
                    for (Type x : interfaces(s)) {
                        if (!set.remove(new UniqueType(x, Types.this)))
                            return false;
                    }
                    return (set.isEmpty());
                }
                return t.tsym == s.tsym
                    && visit(t.getEnclosingType(), s.getEnclosingType())
                    && containsTypes(t.getTypeArguments(), s.getTypeArguments());
            }

            abstract protected boolean containsTypes(List<Type> ts1, List<Type> ts2);

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                if (t == s)
                    return true;

                if (s.isPartial())
                    return visit(s, t);

                return s.hasTag(ARRAY)
                    && containsTypeEquivalent(t.elemtype, elemtype(s));
            }

            @Override
            public Boolean visitMethodType(MethodType t, Type s) {
                // isSameType for methods does not take thrown
                // exceptions into account!
                return hasSameArgs(t, s) && visit(t.getReturnType(), s.getReturnType());
            }

            @Override
            public Boolean visitPackageType(PackageType t, Type s) {
                return t == s;
            }

            @Override
            public Boolean visitForAll(ForAll t, Type s) {
                if (!s.hasTag(FORALL)) {
                    return false;
                }

                ForAll forAll = (ForAll)s;
                return hasSameBounds(t, forAll)
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                if (s.hasTag(WILDCARD)) {
                    // FIXME, this might be leftovers from before capture conversion
                    return false;
                }

                if (t == s || t.qtype == s || s.hasTag(ERROR) || s.hasTag(UNKNOWN)) {
                    return true;
                }

                t.addBound(InferenceBound.EQ, s, Types.this);

                return true;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        }

        /**
         * Standard type-equality relation - type variables are considered
         * equals if they share the same type symbol.
         */
        TypeRelation isSameTypeLoose = new LooseSameTypeVisitor();

        private class LooseSameTypeVisitor extends SameTypeVisitor {
            @Override
            boolean sameTypeVars(TypeVar tv1, TypeVar tv2) {
                return tv1.tsym == tv2.tsym && visit(tv1.getUpperBound(), tv2.getUpperBound());
            }
            @Override
            protected boolean containsTypes(List<Type> ts1, List<Type> ts2) {
                return containsTypeEquivalent(ts1, ts2);
            }
        };

        /**
         * Strict type-equality relation - type variables are considered
         * equals if they share the same object identity.
         */
        TypeRelation isSameTypeStrict = new SameTypeVisitor() {
            @Override
            boolean sameTypeVars(TypeVar tv1, TypeVar tv2) {
                return tv1 == tv2;
            }
            @Override
            protected boolean containsTypes(List<Type> ts1, List<Type> ts2) {
                return isSameTypes(ts1, ts2, true);
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (!s.hasTag(WILDCARD)) {
                    return false;
                } else {
                    WildcardType t2 = (WildcardType)s.unannotatedType();
                    return t.kind == t2.kind &&
                            isSameType(t.type, t2.type, true);
                }
            }
        };

        /**
         * A version of LooseSameTypeVisitor that takes AnnotatedTypes
         * into account.
         */
        TypeRelation isSameAnnotatedType = new LooseSameTypeVisitor() {
            @Override
            public Boolean visitAnnotatedType(AnnotatedType t, Type s) {
                if (!s.isAnnotated())
                    return false;
                if (!t.getAnnotationMirrors().containsAll(s.getAnnotationMirrors()))
                    return false;
                if (!s.getAnnotationMirrors().containsAll(t.getAnnotationMirrors()))
                    return false;
                return visit(t.underlyingType, s);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Contains Type">
    public boolean containedBy(Type t, Type s) {
        switch (t.getTag()) {
        case UNDETVAR:
            if (s.hasTag(WILDCARD)) {
                UndetVar undetvar = (UndetVar)t;
                WildcardType wt = (WildcardType)s.unannotatedType();
                switch(wt.kind) {
                    case UNBOUND: //similar to ? extends Object
                    case EXTENDS: {
                        Type bound = upperBound(s);
                        undetvar.addBound(InferenceBound.UPPER, bound, this);
                        break;
                    }
                    case SUPER: {
                        Type bound = lowerBound(s);
                        undetvar.addBound(InferenceBound.LOWER, bound, this);
                        break;
                    }
                }
                return true;
            } else {
                return isSameType(t, s);
            }
        case ERROR:
            return true;
        default:
            return containsType(s, t);
        }
    }

    boolean containsType(List<Type> ts, List<Type> ss) {
        while (ts.nonEmpty() && ss.nonEmpty()
               && containsType(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.isEmpty() && ss.isEmpty();
    }

    /**
     * Check if t contains s.
     *
     * <p>T contains S if:
     *
     * <p>{@code L(T) <: L(S) && U(S) <: U(T)}
     *
     * <p>This relation is only used by ClassType.isSubtype(), that
     * is,
     *
     * <p>{@code C<S> <: C<T> if T contains S.}
     *
     * <p>Because of F-bounds, this relation can lead to infinite
     * recursion.  Thus we must somehow break that recursion.  Notice
     * that containsType() is only called from ClassType.isSubtype().
     * Since the arguments have already been checked against their
     * bounds, we know:
     *
     * <p>{@code U(S) <: U(T) if T is "super" bound (U(T) *is* the bound)}
     *
     * <p>{@code L(T) <: L(S) if T is "extends" bound (L(T) is bottom)}
     *
     * @param t a type
     * @param s a type
     */
    public boolean containsType(Type t, Type s) {
        return containsType.visit(t, s);
    }
    // where
        private TypeRelation containsType = new TypeRelation() {

            private Type U(Type t) {
                while (t.hasTag(WILDCARD)) {
                    WildcardType w = (WildcardType)t.unannotatedType();
                    if (w.isSuperBound())
                        return w.bound == null ? syms.objectType : w.bound.bound;
                    else
                        t = w.type;
                }
                return t;
            }

            private Type L(Type t) {
                while (t.hasTag(WILDCARD)) {
                    WildcardType w = (WildcardType)t.unannotatedType();
                    if (w.isExtendsBound())
                        return syms.botType;
                    else
                        t = w.type;
                }
                return t;
            }

            public Boolean visitType(Type t, Type s) {
                if (s.isPartial())
                    return containedBy(s, t);
                else
                    return isSameType(t, s);
            }

//            void debugContainsType(WildcardType t, Type s) {
//                System.err.println();
//                System.err.format(" does %s contain %s?%n", t, s);
//                System.err.format(" %s U(%s) <: U(%s) %s = %s%n",
//                                  upperBound(s), s, t, U(t),
//                                  t.isSuperBound()
//                                  || isSubtypeNoCapture(upperBound(s), U(t)));
//                System.err.format(" %s L(%s) <: L(%s) %s = %s%n",
//                                  L(t), t, s, lowerBound(s),
//                                  t.isExtendsBound()
//                                  || isSubtypeNoCapture(L(t), lowerBound(s)));
//                System.err.println();
//            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (s.isPartial())
                    return containedBy(s, t);
                else {
//                    debugContainsType(t, s);
                    return isSameWildcard(t, s)
                        || isCaptureOf(s, t)
                        || ((t.isExtendsBound() || isSubtypeNoCapture(L(t), lowerBound(s))) &&
                            (t.isSuperBound() || isSubtypeNoCapture(upperBound(s), U(t))));
                }
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                if (!s.hasTag(WILDCARD)) {
                    return isSameType(t, s);
                } else {
                    return false;
                }
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };

    public boolean isCaptureOf(Type s, WildcardType t) {
        if (!s.hasTag(TYPEVAR) || !((TypeVar)s.unannotatedType()).isCaptured())
            return false;
        return isSameWildcard(t, ((CapturedType)s.unannotatedType()).wildcard);
    }

    public boolean isSameWildcard(WildcardType t, Type s) {
        if (!s.hasTag(WILDCARD))
            return false;
        WildcardType w = (WildcardType)s.unannotatedType();
        return w.kind == t.kind && w.type == t.type;
    }

    public boolean containsTypeEquivalent(List<Type> ts, List<Type> ss) {
        while (ts.nonEmpty() && ss.nonEmpty()
               && containsTypeEquivalent(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.isEmpty() && ss.isEmpty();
    }
    // </editor-fold>

    /**
     * Can t and s be compared for equality?  Any primitive ==
     * primitive or primitive == object comparisons here are an error.
     * Unboxing and correct primitive == primitive comparisons are
     * already dealt with in Attr.visitBinary.
     *
     */
    public boolean isEqualityComparable(Type s, Type t, Warner warn) {
        if (t.isNumeric() && s.isNumeric())
            return true;

        boolean tPrimitive = t.isPrimitive();
        boolean sPrimitive = s.isPrimitive();
        if (!tPrimitive && !sPrimitive) {
            return isCastable(s, t, warn) || isCastable(t, s, warn);
        } else {
            return false;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="isCastable">
    public boolean isCastable(Type t, Type s) {
        return isCastable(t, s, noWarnings);
    }

    /**
     * Is t is castable to s?<br>
     * s is assumed to be an erased type.<br>
     * (not defined for Method and ForAll types).
     */
    public boolean isCastable(Type t, Type s, Warner warn) {
        if (t == s)
            return true;

        if (t.isPrimitive() != s.isPrimitive())
            return allowBoxing && (
                    isConvertible(t, s, warn)
                    || (allowObjectToPrimitiveCast &&
                        s.isPrimitive() &&
                        isSubtype(boxedClass(s).type, t)));
        if (warn != warnStack.head) {
            try {
                warnStack = warnStack.prepend(warn);
                checkUnsafeVarargsConversion(t, s, warn);
                return isCastable.visit(t,s);
            } finally {
                warnStack = warnStack.tail;
            }
        } else {
            return isCastable.visit(t,s);
        }
    }
    // where
        private TypeRelation isCastable = new TypeRelation() {

            public Boolean visitType(Type t, Type s) {
                if (s.hasTag(ERROR))
                    return true;

                switch (t.getTag()) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE:
                    return s.isNumeric();
                case BOOLEAN:
                    return s.hasTag(BOOLEAN);
                case VOID:
                    return false;
                case BOT:
                    return isSubtype(t, s);
                default:
                    throw new AssertionError();
                }
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                return isCastable(upperBound(t), s, warnStack.head);
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                if (s.hasTag(ERROR) || s.hasTag(BOT))
                    return true;

                if (s.hasTag(TYPEVAR)) {
                    if (isCastable(t, s.getUpperBound(), noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                }

                if (t.isCompound() || s.isCompound()) {
                    return !t.isCompound() ?
                            visitIntersectionType((IntersectionClassType)s.unannotatedType(), t, true) :
                            visitIntersectionType((IntersectionClassType)t.unannotatedType(), s, false);
                }

                if (s.hasTag(CLASS) || s.hasTag(ARRAY)) {
                    boolean upcast;
                    if ((upcast = isSubtype(erasure(t), erasure(s)))
                        || isSubtype(erasure(s), erasure(t))) {
                        if (!upcast && s.hasTag(ARRAY)) {
                            if (!isReifiable(s))
                                warnStack.head.warn(LintCategory.UNCHECKED);
                            return true;
                        } else if (s.isRaw()) {
                            return true;
                        } else if (t.isRaw()) {
                            if (!isUnbounded(s))
                                warnStack.head.warn(LintCategory.UNCHECKED);
                            return true;
                        }
                        // Assume |a| <: |b|
                        final Type a = upcast ? t : s;
                        final Type b = upcast ? s : t;
                        final boolean HIGH = true;
                        final boolean LOW = false;
                        final boolean DONT_REWRITE_TYPEVARS = false;
                        Type aHigh = rewriteQuantifiers(a, HIGH, DONT_REWRITE_TYPEVARS);
                        Type aLow  = rewriteQuantifiers(a, LOW,  DONT_REWRITE_TYPEVARS);
                        Type bHigh = rewriteQuantifiers(b, HIGH, DONT_REWRITE_TYPEVARS);
                        Type bLow  = rewriteQuantifiers(b, LOW,  DONT_REWRITE_TYPEVARS);
                        Type lowSub = asSub(bLow, aLow.tsym);
                        Type highSub = (lowSub == null) ? null : asSub(bHigh, aHigh.tsym);
                        if (highSub == null) {
                            final boolean REWRITE_TYPEVARS = true;
                            aHigh = rewriteQuantifiers(a, HIGH, REWRITE_TYPEVARS);
                            aLow  = rewriteQuantifiers(a, LOW,  REWRITE_TYPEVARS);
                            bHigh = rewriteQuantifiers(b, HIGH, REWRITE_TYPEVARS);
                            bLow  = rewriteQuantifiers(b, LOW,  REWRITE_TYPEVARS);
                            lowSub = asSub(bLow, aLow.tsym);
                            highSub = (lowSub == null) ? null : asSub(bHigh, aHigh.tsym);
                        }
                        if (highSub != null) {
                            if (!(a.tsym == highSub.tsym && a.tsym == lowSub.tsym)) {
                                Assert.error(a.tsym + " != " + highSub.tsym + " != " + lowSub.tsym);
                            }
                            if (!disjointTypes(aHigh.allparams(), highSub.allparams())
                                && !disjointTypes(aHigh.allparams(), lowSub.allparams())
                                && !disjointTypes(aLow.allparams(), highSub.allparams())
                                && !disjointTypes(aLow.allparams(), lowSub.allparams())) {
                                if (upcast ? giveWarning(a, b) :
                                    giveWarning(b, a))
                                    warnStack.head.warn(LintCategory.UNCHECKED);
                                return true;
                            }
                        }
                        if (isReifiable(s))
                            return isSubtypeUnchecked(a, b);
                        else
                            return isSubtypeUnchecked(a, b, warnStack.head);
                    }

                    // Sidecast
                    if (s.hasTag(CLASS)) {
                        if ((s.tsym.flags() & INTERFACE) != 0) {
                            return ((t.tsym.flags() & FINAL) == 0)
                                ? sideCast(t, s, warnStack.head)
                                : sideCastFinal(t, s, warnStack.head);
                        } else if ((t.tsym.flags() & INTERFACE) != 0) {
                            return ((s.tsym.flags() & FINAL) == 0)
                                ? sideCast(t, s, warnStack.head)
                                : sideCastFinal(t, s, warnStack.head);
                        } else {
                            // unrelated class types
                            return false;
                        }
                    }
                }
                return false;
            }

            boolean visitIntersectionType(IntersectionClassType ict, Type s, boolean reverse) {
                Warner warn = noWarnings;
                for (Type c : ict.getComponents()) {
                    warn.clear();
                    if (reverse ? !isCastable(s, c, warn) : !isCastable(c, s, warn))
                        return false;
                }
                if (warn.hasLint(LintCategory.UNCHECKED))
                    warnStack.head.warn(LintCategory.UNCHECKED);
                return true;
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                switch (s.getTag()) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    if (isCastable(s, t, noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                case CLASS:
                    return isSubtype(t, s);
                case ARRAY:
                    if (elemtype(t).isPrimitive() || elemtype(s).isPrimitive()) {
                        return elemtype(t).hasTag(elemtype(s).getTag());
                    } else {
                        return visit(elemtype(t), elemtype(s));
                    }
                default:
                    return false;
                }
            }

            @Override
            public Boolean visitTypeVar(TypeVar t, Type s) {
                switch (s.getTag()) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    if (isSubtype(t, s)) {
                        return true;
                    } else if (isCastable(t.bound, s, noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                default:
                    return isCastable(t.bound, s, warnStack.head);
                }
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="disjointTypes">
    public boolean disjointTypes(List<Type> ts, List<Type> ss) {
        while (ts.tail != null && ss.tail != null) {
            if (disjointType(ts.head, ss.head)) return true;
            ts = ts.tail;
            ss = ss.tail;
        }
        return false;
    }

    /**
     * Two types or wildcards are considered disjoint if it can be
     * proven that no type can be contained in both. It is
     * conservative in that it is allowed to say that two types are
     * not disjoint, even though they actually are.
     *
     * The type {@code C<X>} is castable to {@code C<Y>} exactly if
     * {@code X} and {@code Y} are not disjoint.
     */
    public boolean disjointType(Type t, Type s) {
        return disjointType.visit(t, s);
    }
    // where
        private TypeRelation disjointType = new TypeRelation() {

            private Set<TypePair> cache = new HashSet<TypePair>();

            @Override
            public Boolean visitType(Type t, Type s) {
                if (s.hasTag(WILDCARD))
                    return visit(s, t);
                else
                    return notSoftSubtypeRecursive(t, s) || notSoftSubtypeRecursive(s, t);
            }

            private boolean isCastableRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        return Types.this.isCastable(t, s);
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    return true;
                }
            }

            private boolean notSoftSubtypeRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        return Types.this.notSoftSubtype(t, s);
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    return false;
                }
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (t.isUnbound())
                    return false;

                if (!s.hasTag(WILDCARD)) {
                    if (t.isExtendsBound())
                        return notSoftSubtypeRecursive(s, t.type);
                    else
                        return notSoftSubtypeRecursive(t.type, s);
                }

                if (s.isUnbound())
                    return false;

                if (t.isExtendsBound()) {
                    if (s.isExtendsBound())
                        return !isCastableRecursive(t.type, upperBound(s));
                    else if (s.isSuperBound())
                        return notSoftSubtypeRecursive(lowerBound(s), t.type);
                } else if (t.isSuperBound()) {
                    if (s.isExtendsBound())
                        return notSoftSubtypeRecursive(t.type, upperBound(s));
                }
                return false;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="lowerBoundArgtypes">
    /**
     * Returns the lower bounds of the formals of a method.
     */
    public List<Type> lowerBoundArgtypes(Type t) {
        return lowerBounds(t.getParameterTypes());
    }
    public List<Type> lowerBounds(List<Type> ts) {
        return map(ts, lowerBoundMapping);
    }
    private final Mapping lowerBoundMapping = new Mapping("lowerBound") {
            public Type apply(Type t) {
                return lowerBound(t);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="notSoftSubtype">
    /**
     * This relation answers the question: is impossible that
     * something of type `t' can be a subtype of `s'? This is
     * different from the question "is `t' not a subtype of `s'?"
     * when type variables are involved: Integer is not a subtype of T
     * where {@code <T extends Number>} but it is not true that Integer cannot
     * possibly be a subtype of T.
     */
    public boolean notSoftSubtype(Type t, Type s) {
        if (t == s) return false;
        if (t.hasTag(TYPEVAR)) {
            TypeVar tv = (TypeVar) t;
            return !isCastable(tv.bound,
                               relaxBound(s),
                               noWarnings);
        }
        if (!s.hasTag(WILDCARD))
            s = upperBound(s);

        return !isSubtype(t, relaxBound(s));
    }

    private Type relaxBound(Type t) {
        if (t.hasTag(TYPEVAR)) {
            while (t.hasTag(TYPEVAR))
                t = t.getUpperBound();
            t = rewriteQuantifiers(t, true, true);
        }
        return t;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isReifiable">
    public boolean isReifiable(Type t) {
        return isReifiable.visit(t);
    }
    // where
        private UnaryVisitor<Boolean> isReifiable = new UnaryVisitor<Boolean>() {

            public Boolean visitType(Type t, Void ignored) {
                return true;
            }

            @Override
            public Boolean visitClassType(ClassType t, Void ignored) {
                if (t.isCompound())
                    return false;
                else {
                    if (!t.isParameterized())
                        return true;

                    for (Type param : t.allparams()) {
                        if (!param.isUnbound())
                            return false;
                    }
                    return true;
                }
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Void ignored) {
                return visit(t.elemtype);
            }

            @Override
            public Boolean visitTypeVar(TypeVar t, Void ignored) {
                return false;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Array Utils">
    public boolean isArray(Type t) {
        while (t.hasTag(WILDCARD))
            t = upperBound(t);
        return t.hasTag(ARRAY);
    }

    /**
     * The element type of an array.
     */
    public Type elemtype(Type t) {
        switch (t.getTag()) {
        case WILDCARD:
            return elemtype(upperBound(t));
        case ARRAY:
            t = t.unannotatedType();
            return ((ArrayType)t).elemtype;
        case FORALL:
            return elemtype(((ForAll)t).qtype);
        case ERROR:
            return t;
        default:
            return null;
        }
    }

    public Type elemtypeOrType(Type t) {
        Type elemtype = elemtype(t);
        return elemtype != null ?
            elemtype :
            t;
    }

    /**
     * Mapping to take element type of an arraytype
     */
    private Mapping elemTypeFun = new Mapping ("elemTypeFun") {
        public Type apply(Type t) { return elemtype(t); }
    };

    /**
     * The number of dimensions of an array type.
     */
    public int dimensions(Type t) {
        int result = 0;
        while (t.hasTag(ARRAY)) {
            result++;
            t = elemtype(t);
        }
        return result;
    }

    /**
     * Returns an ArrayType with the component type t
     *
     * @param t The component type of the ArrayType
     * @return the ArrayType for the given component
     */
    public ArrayType makeArrayType(Type t) {
        if (t.hasTag(VOID) || t.hasTag(PACKAGE)) {
            Assert.error("Type t must not be a VOID or PACKAGE type, " + t.toString());
        }
        return new ArrayType(t, syms.arrayClass);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="asSuper">
    /**
     * Return the (most specific) base type of t that starts with the
     * given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    public Type asSuper(Type t, Symbol sym) {
        return asSuper.visit(t, sym);
    }
    // where
        private SimpleVisitor<Type,Symbol> asSuper = new SimpleVisitor<Type,Symbol>() {

            public Type visitType(Type t, Symbol sym) {
                return null;
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                if (t.tsym == sym)
                    return t;

                Type st = supertype(t);
                if (st.hasTag(CLASS) || st.hasTag(TYPEVAR) || st.hasTag(ERROR)) {
                    Type x = asSuper(st, sym);
                    if (x != null)
                        return x;
                }
                if ((sym.flags() & INTERFACE) != 0) {
                    for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail) {
                        Type x = asSuper(l.head, sym);
                        if (x != null)
                            return x;
                    }
                }
                return null;
            }

            @Override
            public Type visitArrayType(ArrayType t, Symbol sym) {
                return isSubtype(t, sym.type) ? sym.type : null;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Symbol sym) {
                if (t.tsym == sym)
                    return t;
                else
                    return asSuper(t.bound, sym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };

    /**
     * Return the base type of t or any of its outer types that starts
     * with the given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    public Type asOuterSuper(Type t, Symbol sym) {
        switch (t.getTag()) {
        case CLASS:
            do {
                Type s = asSuper(t, sym);
                if (s != null) return s;
                t = t.getEnclosingType();
            } while (t.hasTag(CLASS));
            return null;
        case ARRAY:
            return isSubtype(t, sym.type) ? sym.type : null;
        case TYPEVAR:
            return asSuper(t, sym);
        case ERROR:
            return t;
        default:
            return null;
        }
    }

    /**
     * Return the base type of t or any of its enclosing types that
     * starts with the given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    public Type asEnclosingSuper(Type t, Symbol sym) {
        switch (t.getTag()) {
        case CLASS:
            do {
                Type s = asSuper(t, sym);
                if (s != null) return s;
                Type outer = t.getEnclosingType();
                t = (outer.hasTag(CLASS)) ? outer :
                    (t.tsym.owner.enclClass() != null) ? t.tsym.owner.enclClass().type :
                    Type.noType;
            } while (t.hasTag(CLASS));
            return null;
        case ARRAY:
            return isSubtype(t, sym.type) ? sym.type : null;
        case TYPEVAR:
            return asSuper(t, sym);
        case ERROR:
            return t;
        default:
            return null;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="memberType">
    /**
     * The type of given symbol, seen as a member of t.
     *
     * @param t a type
     * @param sym a symbol
     */
    public Type memberType(Type t, Symbol sym) {
        return (sym.flags() & STATIC) != 0
            ? sym.type
            : memberType.visit(t, sym);
        }
    // where
        private SimpleVisitor<Type,Symbol> memberType = new SimpleVisitor<Type,Symbol>() {

            public Type visitType(Type t, Symbol sym) {
                return sym.type;
            }

            @Override
            public Type visitWildcardType(WildcardType t, Symbol sym) {
                return memberType(upperBound(t), sym);
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                Symbol owner = sym.owner;
                long flags = sym.flags();
                if (((flags & STATIC) == 0) && owner.type.isParameterized()) {
                    Type base = asOuterSuper(t, owner);
                    //if t is an intersection type T = CT & I1 & I2 ... & In
                    //its supertypes CT, I1, ... In might contain wildcards
                    //so we need to go through capture conversion
                    base = t.isCompound() ? capture(base) : base;
                    if (base != null) {
                        List<Type> ownerParams = owner.type.allparams();
                        List<Type> baseParams = base.allparams();
                        if (ownerParams.nonEmpty()) {
                            if (baseParams.isEmpty()) {
                                // then base is a raw type
                                return erasure(sym.type);
                            } else {
                                return subst(sym.type, ownerParams, baseParams);
                            }
                        }
                    }
                }
                return sym.type;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Symbol sym) {
                return memberType(t.bound, sym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isAssignable">
    public boolean isAssignable(Type t, Type s) {
        return isAssignable(t, s, noWarnings);
    }

    /**
     * Is t assignable to s?<br>
     * Equivalent to subtype except for constant values and raw
     * types.<br>
     * (not defined for Method and ForAll types)
     */
    public boolean isAssignable(Type t, Type s, Warner warn) {
        if (t.hasTag(ERROR))
            return true;
        if (t.getTag().isSubRangeOf(INT) && t.constValue() != null) {
            int value = ((Number)t.constValue()).intValue();
            switch (s.getTag()) {
            case BYTE:
                if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE)
                    return true;
                break;
            case CHAR:
                if (Character.MIN_VALUE <= value && value <= Character.MAX_VALUE)
                    return true;
                break;
            case SHORT:
                if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE)
                    return true;
                break;
            case INT:
                return true;
            case CLASS:
                switch (unboxedType(s).getTag()) {
                case BYTE:
                case CHAR:
                case SHORT:
                    return isAssignable(t, unboxedType(s), warn);
                }
                break;
            }
        }
        return isConvertible(t, s, warn);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="erasure">
    /**
     * The erasure of t {@code |t|} -- the type that results when all
     * type parameters in t are deleted.
     */
    public Type erasure(Type t) {
        return eraseNotNeeded(t)? t : erasure(t, false);
    }
    //where
    private boolean eraseNotNeeded(Type t) {
        // We don't want to erase primitive types and String type as that
        // operation is idempotent. Also, erasing these could result in loss
        // of information such as constant values attached to such types.
        return (t.isPrimitive()) || (syms.stringType.tsym == t.tsym);
    }

    private Type erasure(Type t, boolean recurse) {
        if (t.isPrimitive())
            return t; /* fast special case */
        else
            return erasure.visit(t, recurse);
        }
    // where
        private SimpleVisitor<Type, Boolean> erasure = new SimpleVisitor<Type, Boolean>() {
            public Type visitType(Type t, Boolean recurse) {
                if (t.isPrimitive())
                    return t; /*fast special case*/
                else
                    return t.map(recurse ? erasureRecFun : erasureFun);
            }

            @Override
            public Type visitWildcardType(WildcardType t, Boolean recurse) {
                return erasure(upperBound(t), recurse);
            }

            @Override
            public Type visitClassType(ClassType t, Boolean recurse) {
                Type erased = t.tsym.erasure(Types.this);
                if (recurse) {
                    erased = new ErasedClassType(erased.getEnclosingType(),erased.tsym);
                }
                return erased;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Boolean recurse) {
                return erasure(t.bound, recurse);
            }

            @Override
            public Type visitErrorType(ErrorType t, Boolean recurse) {
                return t;
            }

            @Override
            public Type visitAnnotatedType(AnnotatedType t, Boolean recurse) {
                Type erased = erasure(t.underlyingType, recurse);
                if (erased.isAnnotated()) {
                    // This can only happen when the underlying type is a
                    // type variable and the upper bound of it is annotated.
                    // The annotation on the type variable overrides the one
                    // on the bound.
                    erased = ((AnnotatedType)erased).underlyingType;
                }
                return new AnnotatedType(t.typeAnnotations, erased);
            }
        };

    private Mapping erasureFun = new Mapping ("erasure") {
            public Type apply(Type t) { return erasure(t); }
        };

    private Mapping erasureRecFun = new Mapping ("erasureRecursive") {
        public Type apply(Type t) { return erasureRecursive(t); }
    };

    public List<Type> erasure(List<Type> ts) {
        return Type.map(ts, erasureFun);
    }

    public Type erasureRecursive(Type t) {
        return erasure(t, true);
    }

    public List<Type> erasureRecursive(List<Type> ts) {
        return Type.map(ts, erasureRecFun);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="makeCompoundType">
    /**
     * Make a compound type from non-empty list of types
     *
     * @param bounds            the types from which the compound type is formed
     * @param supertype         is objectType if all bounds are interfaces,
     *                          null otherwise.
     */
    public Type makeCompoundType(List<Type> bounds) {
        return makeCompoundType(bounds, bounds.head.tsym.isInterface());
    }
    public Type makeCompoundType(List<Type> bounds, boolean allInterfaces) {
        Assert.check(bounds.nonEmpty());
        Type firstExplicitBound = bounds.head;
        if (allInterfaces) {
            bounds = bounds.prepend(syms.objectType);
        }
        ClassSymbol bc =
            new ClassSymbol(ABSTRACT|PUBLIC|SYNTHETIC|COMPOUND|ACYCLIC,
                            Type.moreInfo
                                ? names.fromString(bounds.toString())
                                : names.empty,
                            null,
                            syms.noSymbol);
        bc.type = new IntersectionClassType(bounds, bc, allInterfaces);
        bc.erasure_field = (bounds.head.hasTag(TYPEVAR)) ?
                syms.objectType : // error condition, recover
                erasure(firstExplicitBound);
        bc.members_field = new Scope(bc);
        return bc.type;
    }

    /**
     * A convenience wrapper for {@link #makeCompoundType(List)}; the
     * arguments are converted to a list and passed to the other
     * method.  Note that this might cause a symbol completion.
     * Hence, this version of makeCompoundType may not be called
     * during a classfile read.
     */
    public Type makeCompoundType(Type bound1, Type bound2) {
        return makeCompoundType(List.of(bound1, bound2));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="supertype">
    public Type supertype(Type t) {
        return supertype.visit(t);
    }
    // where
        private UnaryVisitor<Type> supertype = new UnaryVisitor<Type>() {

            public Type visitType(Type t, Void ignored) {
                // A note on wildcards: there is no good way to
                // determine a supertype for a super bounded wildcard.
                return null;
            }

            @Override
            public Type visitClassType(ClassType t, Void ignored) {
                if (t.supertype_field == null) {
                    Type supertype = ((ClassSymbol)t.tsym).getSuperclass();
                    // An interface has no superclass; its supertype is Object.
                    if (t.isInterface())
                        supertype = ((ClassType)t.tsym.type).supertype_field;
                    if (t.supertype_field == null) {
                        List<Type> actuals = classBound(t).allparams();
                        List<Type> formals = t.tsym.type.allparams();
                        if (t.hasErasedSupertypes()) {
                            t.supertype_field = erasureRecursive(supertype);
                        } else if (formals.nonEmpty()) {
                            t.supertype_field = subst(supertype, formals, actuals);
                        }
                        else {
                            t.supertype_field = supertype;
                        }
                    }
                }
                return t.supertype_field;
            }

            /**
             * The supertype is always a class type. If the type
             * variable's bounds start with a class type, this is also
             * the supertype.  Otherwise, the supertype is
             * java.lang.Object.
             */
            @Override
            public Type visitTypeVar(TypeVar t, Void ignored) {
                if (t.bound.hasTag(TYPEVAR) ||
                    (!t.bound.isCompound() && !t.bound.isInterface())) {
                    return t.bound;
                } else {
                    return supertype(t.bound);
                }
            }

            @Override
            public Type visitArrayType(ArrayType t, Void ignored) {
                if (t.elemtype.isPrimitive() || isSameType(t.elemtype, syms.objectType))
                    return arraySuperType();
                else
                    return new ArrayType(supertype(t.elemtype), t.tsym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Void ignored) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interfaces">
    /**
     * Return the interfaces implemented by this class.
     */
    public List<Type> interfaces(Type t) {
        return interfaces.visit(t);
    }
    // where
        private UnaryVisitor<List<Type>> interfaces = new UnaryVisitor<List<Type>>() {

            public List<Type> visitType(Type t, Void ignored) {
                return List.nil();
            }

            @Override
            public List<Type> visitClassType(ClassType t, Void ignored) {
                if (t.interfaces_field == null) {
                    List<Type> interfaces = ((ClassSymbol)t.tsym).getInterfaces();
                    if (t.interfaces_field == null) {
                        // If t.interfaces_field is null, then t must
                        // be a parameterized type (not to be confused
                        // with a generic type declaration).
                        // Terminology:
                        //    Parameterized type: List<String>
                        //    Generic type declaration: class List<E> { ... }
                        // So t corresponds to List<String> and
                        // t.tsym.type corresponds to List<E>.
                        // The reason t must be parameterized type is
                        // that completion will happen as a side
                        // effect of calling
                        // ClassSymbol.getInterfaces.  Since
                        // t.interfaces_field is null after
                        // completion, we can assume that t is not the
                        // type of a class/interface declaration.
                        Assert.check(t != t.tsym.type, t);
                        List<Type> actuals = t.allparams();
                        List<Type> formals = t.tsym.type.allparams();
                        if (t.hasErasedSupertypes()) {
                            t.interfaces_field = erasureRecursive(interfaces);
                        } else if (formals.nonEmpty()) {
                            t.interfaces_field =
                                upperBounds(subst(interfaces, formals, actuals));
                        }
                        else {
                            t.interfaces_field = interfaces;
                        }
                    }
                }
                return t.interfaces_field;
            }

            @Override
            public List<Type> visitTypeVar(TypeVar t, Void ignored) {
                if (t.bound.isCompound())
                    return interfaces(t.bound);

                if (t.bound.isInterface())
                    return List.of(t.bound);

                return List.nil();
            }
        };

    public boolean isDirectSuperInterface(TypeSymbol isym, TypeSymbol origin) {
        for (Type i2 : interfaces(origin.type)) {
            if (isym == i2.tsym) return true;
        }
        return false;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isDerivedRaw">
    Map<Type,Boolean> isDerivedRawCache = new HashMap<Type,Boolean>();

    public boolean isDerivedRaw(Type t) {
        Boolean result = isDerivedRawCache.get(t);
        if (result == null) {
            result = isDerivedRawInternal(t);
            isDerivedRawCache.put(t, result);
        }
        return result;
    }

    public boolean isDerivedRawInternal(Type t) {
        if (t.isErroneous())
            return false;
        return
            t.isRaw() ||
            supertype(t) != null && isDerivedRaw(supertype(t)) ||
            isDerivedRaw(interfaces(t));
    }

    public boolean isDerivedRaw(List<Type> ts) {
        List<Type> l = ts;
        while (l.nonEmpty() && !isDerivedRaw(l.head)) l = l.tail;
        return l.nonEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="setBounds">
    /**
     * Set the bounds field of the given type variable to reflect a
     * (possibly multiple) list of bounds.
     * @param t                 a type variable
     * @param bounds            the bounds, must be nonempty
     * @param supertype         is objectType if all bounds are interfaces,
     *                          null otherwise.
     */
    public void setBounds(TypeVar t, List<Type> bounds) {
        setBounds(t, bounds, bounds.head.tsym.isInterface());
    }

    /**
     * Same as {@link #setBounds(Type.TypeVar,List,Type)}, except that
     * third parameter is computed directly, as follows: if all
     * all bounds are interface types, the computed supertype is Object,
     * otherwise the supertype is simply left null (in this case, the supertype
     * is assumed to be the head of the bound list passed as second argument).
     * Note that this check might cause a symbol completion. Hence, this version of
     * setBounds may not be called during a classfile read.
     */
    public void setBounds(TypeVar t, List<Type> bounds, boolean allInterfaces) {
        t.bound = bounds.tail.isEmpty() ?
                bounds.head :
                makeCompoundType(bounds, allInterfaces);
        t.rank_field = -1;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getBounds">
    /**
     * Return list of bounds of the given type variable.
     */
    public List<Type> getBounds(TypeVar t) {
        if (t.bound.hasTag(NONE))
            return List.nil();
        else if (t.bound.isErroneous() || !t.bound.isCompound())
            return List.of(t.bound);
        else if ((erasure(t).tsym.flags() & INTERFACE) == 0)
            return interfaces(t).prepend(supertype(t));
        else
            // No superclass was given in bounds.
            // In this case, supertype is Object, erasure is first interface.
            return interfaces(t);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="classBound">
    /**
     * If the given type is a (possibly selected) type variable,
     * return the bounding class of this type, otherwise return the
     * type itself.
     */
    public Type classBound(Type t) {
        return classBound.visit(t);
    }
    // where
        private UnaryVisitor<Type> classBound = new UnaryVisitor<Type>() {

            public Type visitType(Type t, Void ignored) {
                return t;
            }

            @Override
            public Type visitClassType(ClassType t, Void ignored) {
                Type outer1 = classBound(t.getEnclosingType());
                if (outer1 != t.getEnclosingType())
                    return new ClassType(outer1, t.getTypeArguments(), t.tsym);
                else
                    return t;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Void ignored) {
                return classBound(supertype(t));
            }

            @Override
            public Type visitErrorType(ErrorType t, Void ignored) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="sub signature / override equivalence">
    /**
     * Returns true iff the first signature is a <em>sub
     * signature</em> of the other.  This is <b>not</b> an equivalence
     * relation.
     *
     * @jls section 8.4.2.
     * @see #overrideEquivalent(Type t, Type s)
     * @param t first signature (possibly raw).
     * @param s second signature (could be subjected to erasure).
     * @return true if t is a sub signature of s.
     */
    public boolean isSubSignature(Type t, Type s) {
        return isSubSignature(t, s, true);
    }

    public boolean isSubSignature(Type t, Type s, boolean strict) {
        return hasSameArgs(t, s, strict) || hasSameArgs(t, erasure(s), strict);
    }

    /**
     * Returns true iff these signatures are related by <em>override
     * equivalence</em>.  This is the natural extension of
     * isSubSignature to an equivalence relation.
     *
     * @jls section 8.4.2.
     * @see #isSubSignature(Type t, Type s)
     * @param t a signature (possible raw, could be subjected to
     * erasure).
     * @param s a signature (possible raw, could be subjected to
     * erasure).
     * @return true if either argument is a sub signature of the other.
     */
    public boolean overrideEquivalent(Type t, Type s) {
        return hasSameArgs(t, s) ||
            hasSameArgs(t, erasure(s)) || hasSameArgs(erasure(t), s);
    }

    public boolean overridesObjectMethod(TypeSymbol origin, Symbol msym) {
        for (Scope.Entry e = syms.objectType.tsym.members().lookup(msym.name) ; e.scope != null ; e = e.next()) {
            if (msym.overrides(e.sym, origin, Types.this, true)) {
                return true;
            }
        }
        return false;
    }

    // <editor-fold defaultstate="collapsed" desc="Determining method implementation in given site">
    class ImplementationCache {

        private WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, Entry>>> _map =
                new WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, Entry>>>();

        class Entry {
            final MethodSymbol cachedImpl;
            final Filter<Symbol> implFilter;
            final boolean checkResult;
            final int prevMark;

            public Entry(MethodSymbol cachedImpl,
                    Filter<Symbol> scopeFilter,
                    boolean checkResult,
                    int prevMark) {
                this.cachedImpl = cachedImpl;
                this.implFilter = scopeFilter;
                this.checkResult = checkResult;
                this.prevMark = prevMark;
            }

            boolean matches(Filter<Symbol> scopeFilter, boolean checkResult, int mark) {
                return this.implFilter == scopeFilter &&
                        this.checkResult == checkResult &&
                        this.prevMark == mark;
            }
        }

        MethodSymbol get(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
            SoftReference<Map<TypeSymbol, Entry>> ref_cache = _map.get(ms);
            Map<TypeSymbol, Entry> cache = ref_cache != null ? ref_cache.get() : null;
            if (cache == null) {
                cache = new HashMap<TypeSymbol, Entry>();
                _map.put(ms, new SoftReference<Map<TypeSymbol, Entry>>(cache));
            }
            Entry e = cache.get(origin);
            CompoundScope members = membersClosure(origin.type, true);
            if (e == null ||
                    !e.matches(implFilter, checkResult, members.getMark())) {
                MethodSymbol impl = implementationInternal(ms, origin, checkResult, implFilter);
                cache.put(origin, new Entry(impl, implFilter, checkResult, members.getMark()));
                return impl;
            }
            else {
                return e.cachedImpl;
            }
        }

        private MethodSymbol implementationInternal(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
            for (Type t = origin.type; t.hasTag(CLASS) || t.hasTag(TYPEVAR); t = supertype(t)) {
                while (t.hasTag(TYPEVAR))
                    t = t.getUpperBound();
                TypeSymbol c = t.tsym;
                for (Scope.Entry e = c.members().lookup(ms.name, implFilter);
                     e.scope != null;
                     e = e.next(implFilter)) {
                    if (e.sym != null &&
                             e.sym.overrides(ms, origin, Types.this, checkResult))
                        return (MethodSymbol)e.sym;
                }
            }
            return null;
        }
    }

    private ImplementationCache implCache = new ImplementationCache();

    public MethodSymbol implementation(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
        return implCache.get(ms, origin, checkResult, implFilter);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="compute transitive closure of all members in given site">
    class MembersClosureCache extends SimpleVisitor<CompoundScope, Boolean> {

        private WeakHashMap<TypeSymbol, Entry> _map =
                new WeakHashMap<TypeSymbol, Entry>();

        class Entry {
            final boolean skipInterfaces;
            final CompoundScope compoundScope;

            public Entry(boolean skipInterfaces, CompoundScope compoundScope) {
                this.skipInterfaces = skipInterfaces;
                this.compoundScope = compoundScope;
            }

            boolean matches(boolean skipInterfaces) {
                return this.skipInterfaces == skipInterfaces;
            }
        }

        List<TypeSymbol> seenTypes = List.nil();

        /** members closure visitor methods **/

        public CompoundScope visitType(Type t, Boolean skipInterface) {
            return null;
        }

        @Override
        public CompoundScope visitClassType(ClassType t, Boolean skipInterface) {
            if (seenTypes.contains(t.tsym)) {
                //this is possible when an interface is implemented in multiple
                //superclasses, or when a classs hierarchy is circular - in such
                //cases we don't need to recurse (empty scope is returned)
                return new CompoundScope(t.tsym);
            }
            try {
                seenTypes = seenTypes.prepend(t.tsym);
                ClassSymbol csym = (ClassSymbol)t.tsym;
                Entry e = _map.get(csym);
                if (e == null || !e.matches(skipInterface)) {
                    CompoundScope membersClosure = new CompoundScope(csym);
                    if (!skipInterface) {
                        for (Type i : interfaces(t)) {
                            membersClosure.addSubScope(visit(i, skipInterface));
                        }
                    }
                    membersClosure.addSubScope(visit(supertype(t), skipInterface));
                    membersClosure.addSubScope(csym.members());
                    e = new Entry(skipInterface, membersClosure);
                    _map.put(csym, e);
                }
                return e.compoundScope;
            }
            finally {
                seenTypes = seenTypes.tail;
            }
        }

        @Override
        public CompoundScope visitTypeVar(TypeVar t, Boolean skipInterface) {
            return visit(t.getUpperBound(), skipInterface);
        }
    }

    private MembersClosureCache membersCache = new MembersClosureCache();

    public CompoundScope membersClosure(Type site, boolean skipInterface) {
        return membersCache.visit(site, skipInterface);
    }
    // </editor-fold>


    //where
    public List<MethodSymbol> interfaceCandidates(Type site, MethodSymbol ms) {
        Filter<Symbol> filter = new MethodFilter(ms, site);
        List<MethodSymbol> candidates = List.nil();
            for (Symbol s : membersClosure(site, false).getElements(filter)) {
                if (!site.tsym.isInterface() && !s.owner.isInterface()) {
                    return List.of((MethodSymbol)s);
                } else if (!candidates.contains(s)) {
                    candidates = candidates.prepend((MethodSymbol)s);
                }
            }
            return prune(candidates);
        }

    public List<MethodSymbol> prune(List<MethodSymbol> methods) {
        ListBuffer<MethodSymbol> methodsMin = ListBuffer.lb();
        for (MethodSymbol m1 : methods) {
            boolean isMin_m1 = true;
            for (MethodSymbol m2 : methods) {
                if (m1 == m2) continue;
                if (m2.owner != m1.owner &&
                        asSuper(m2.owner.type, m1.owner) != null) {
                    isMin_m1 = false;
                    break;
                }
            }
            if (isMin_m1)
                methodsMin.append(m1);
        }
        return methodsMin.toList();
    }
    // where
            private class MethodFilter implements Filter<Symbol> {

                Symbol msym;
                Type site;

                MethodFilter(Symbol msym, Type site) {
                    this.msym = msym;
                    this.site = site;
                }

                public boolean accepts(Symbol s) {
                    return s.kind == Kinds.MTH &&
                            s.name == msym.name &&
                            (s.flags() & SYNTHETIC) == 0 &&
                            s.isInheritedIn(site.tsym, Types.this) &&
                            overrideEquivalent(memberType(site, s), memberType(site, msym));
                }
            };
    // </editor-fold>

    /**
     * Does t have the same arguments as s?  It is assumed that both
     * types are (possibly polymorphic) method types.  Monomorphic
     * method types "have the same arguments", if their argument lists
     * are equal.  Polymorphic method types "have the same arguments",
     * if they have the same arguments after renaming all type
     * variables of one to corresponding type variables in the other,
     * where correspondence is by position in the type parameter list.
     */
    public boolean hasSameArgs(Type t, Type s) {
        return hasSameArgs(t, s, true);
    }

    public boolean hasSameArgs(Type t, Type s, boolean strict) {
        return hasSameArgs(t, s, strict ? hasSameArgs_strict : hasSameArgs_nonstrict);
    }

    private boolean hasSameArgs(Type t, Type s, TypeRelation hasSameArgs) {
        return hasSameArgs.visit(t, s);
    }
    // where
        private class HasSameArgs extends TypeRelation {

            boolean strict;

            public HasSameArgs(boolean strict) {
                this.strict = strict;
            }

            public Boolean visitType(Type t, Type s) {
                throw new AssertionError();
            }

            @Override
            public Boolean visitMethodType(MethodType t, Type s) {
                return s.hasTag(METHOD)
                    && containsTypeEquivalent(t.argtypes, s.getParameterTypes());
            }

            @Override
            public Boolean visitForAll(ForAll t, Type s) {
                if (!s.hasTag(FORALL))
                    return strict ? false : visitMethodType(t.asMethodType(), s);

                ForAll forAll = (ForAll)s;
                return hasSameBounds(t, forAll)
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return false;
            }
        };

        TypeRelation hasSameArgs_strict = new HasSameArgs(true);
        TypeRelation hasSameArgs_nonstrict = new HasSameArgs(false);

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="subst">
    public List<Type> subst(List<Type> ts,
                            List<Type> from,
                            List<Type> to) {
        return new Subst(from, to).subst(ts);
    }

    /**
     * Substitute all occurrences of a type in `from' with the
     * corresponding type in `to' in 't'. Match lists `from' and `to'
     * from the right: If lists have different length, discard leading
     * elements of the longer list.
     */
    public Type subst(Type t, List<Type> from, List<Type> to) {
        return new Subst(from, to).subst(t);
    }

    private class Subst extends UnaryVisitor<Type> {
        List<Type> from;
        List<Type> to;

        public Subst(List<Type> from, List<Type> to) {
            int fromLength = from.length();
            int toLength = to.length();
            while (fromLength > toLength) {
                fromLength--;
                from = from.tail;
            }
            while (fromLength < toLength) {
                toLength--;
                to = to.tail;
            }
            this.from = from;
            this.to = to;
        }

        Type subst(Type t) {
            if (from.tail == null)
                return t;
            else
                return visit(t);
            }

        List<Type> subst(List<Type> ts) {
            if (from.tail == null)
                return ts;
            boolean wild = false;
            if (ts.nonEmpty() && from.nonEmpty()) {
                Type head1 = subst(ts.head);
                List<Type> tail1 = subst(ts.tail);
                if (head1 != ts.head || tail1 != ts.tail)
                    return tail1.prepend(head1);
            }
            return ts;
        }

        public Type visitType(Type t, Void ignored) {
            return t;
        }

        @Override
        public Type visitMethodType(MethodType t, Void ignored) {
            List<Type> argtypes = subst(t.argtypes);
            Type restype = subst(t.restype);
            List<Type> thrown = subst(t.thrown);
            if (argtypes == t.argtypes &&
                restype == t.restype &&
                thrown == t.thrown)
                return t;
            else
                return new MethodType(argtypes, restype, thrown, t.tsym);
        }

        @Override
        public Type visitTypeVar(TypeVar t, Void ignored) {
            for (List<Type> from = this.from, to = this.to;
                 from.nonEmpty();
                 from = from.tail, to = to.tail) {
                if (t == from.head) {
                    return to.head.withTypeVar(t);
                }
            }
            return t;
        }

        @Override
        public Type visitClassType(ClassType t, Void ignored) {
            if (!t.isCompound()) {
                List<Type> typarams = t.getTypeArguments();
                List<Type> typarams1 = subst(typarams);
                Type outer = t.getEnclosingType();
                Type outer1 = subst(outer);
                if (typarams1 == typarams && outer1 == outer)
                    return t;
                else
                    return new ClassType(outer1, typarams1, t.tsym);
            } else {
                Type st = subst(supertype(t));
                List<Type> is = upperBounds(subst(interfaces(t)));
                if (st == supertype(t) && is == interfaces(t))
                    return t;
                else
                    return makeCompoundType(is.prepend(st));
            }
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void ignored) {
            Type bound = t.type;
            if (t.kind != BoundKind.UNBOUND)
                bound = subst(bound);
            if (bound == t.type) {
                return t;
            } else {
                if (t.isExtendsBound() && bound.isExtendsBound())
                    bound = upperBound(bound);
                return new WildcardType(bound, t.kind, syms.boundClass, t.bound);
            }
        }

        @Override
        public Type visitArrayType(ArrayType t, Void ignored) {
            Type elemtype = subst(t.elemtype);
            if (elemtype == t.elemtype)
                return t;
            else
                return new ArrayType(upperBound(elemtype), t.tsym);
        }

        @Override
        public Type visitForAll(ForAll t, Void ignored) {
            if (Type.containsAny(to, t.tvars)) {
                //perform alpha-renaming of free-variables in 't'
                //if 'to' types contain variables that are free in 't'
                List<Type> freevars = newInstances(t.tvars);
                t = new ForAll(freevars,
                        Types.this.subst(t.qtype, t.tvars, freevars));
            }
            List<Type> tvars1 = substBounds(t.tvars, from, to);
            Type qtype1 = subst(t.qtype);
            if (tvars1 == t.tvars && qtype1 == t.qtype) {
                return t;
            } else if (tvars1 == t.tvars) {
                return new ForAll(tvars1, qtype1);
            } else {
                return new ForAll(tvars1, Types.this.subst(qtype1, t.tvars, tvars1));
            }
        }

        @Override
        public Type visitErrorType(ErrorType t, Void ignored) {
            return t;
        }
    }

    public List<Type> substBounds(List<Type> tvars,
                                  List<Type> from,
                                  List<Type> to) {
        if (tvars.isEmpty())
            return tvars;
        ListBuffer<Type> newBoundsBuf = lb();
        boolean changed = false;
        // calculate new bounds
        for (Type t : tvars) {
            TypeVar tv = (TypeVar) t;
            Type bound = subst(tv.bound, from, to);
            if (bound != tv.bound)
                changed = true;
            newBoundsBuf.append(bound);
        }
        if (!changed)
            return tvars;
        ListBuffer<Type> newTvars = lb();
        // create new type variables without bounds
        for (Type t : tvars) {
            newTvars.append(new TypeVar(t.tsym, null, syms.botType));
        }
        // the new bounds should use the new type variables in place
        // of the old
        List<Type> newBounds = newBoundsBuf.toList();
        from = tvars;
        to = newTvars.toList();
        for (; !newBounds.isEmpty(); newBounds = newBounds.tail) {
            newBounds.head = subst(newBounds.head, from, to);
        }
        newBounds = newBoundsBuf.toList();
        // set the bounds of new type variables to the new bounds
        for (Type t : newTvars.toList()) {
            TypeVar tv = (TypeVar) t;
            tv.bound = newBounds.head;
            newBounds = newBounds.tail;
        }
        return newTvars.toList();
    }

    public TypeVar substBound(TypeVar t, List<Type> from, List<Type> to) {
        Type bound1 = subst(t.bound, from, to);
        if (bound1 == t.bound)
            return t;
        else {
            // create new type variable without bounds
            TypeVar tv = new TypeVar(t.tsym, null, syms.botType);
            // the new bound should use the new type variable in place
            // of the old
            tv.bound = subst(bound1, List.<Type>of(t), List.<Type>of(tv));
            return tv;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="hasSameBounds">
    /**
     * Does t have the same bounds for quantified variables as s?
     */
    boolean hasSameBounds(ForAll t, ForAll s) {
        List<Type> l1 = t.tvars;
        List<Type> l2 = s.tvars;
        while (l1.nonEmpty() && l2.nonEmpty() &&
               isSameType(l1.head.getUpperBound(),
                          subst(l2.head.getUpperBound(),
                                s.tvars,
                                t.tvars))) {
            l1 = l1.tail;
            l2 = l2.tail;
        }
        return l1.isEmpty() && l2.isEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="newInstances">
    /** Create new vector of type variables from list of variables
     *  changing all recursive bounds from old to new list.
     */
    public List<Type> newInstances(List<Type> tvars) {
        List<Type> tvars1 = Type.map(tvars, newInstanceFun);
        for (List<Type> l = tvars1; l.nonEmpty(); l = l.tail) {
            TypeVar tv = (TypeVar) l.head;
            tv.bound = subst(tv.bound, tvars, tvars1);
        }
        return tvars1;
    }
    private static final Mapping newInstanceFun = new Mapping("newInstanceFun") {
            public Type apply(Type t) { return new TypeVar(t.tsym, t.getUpperBound(), t.getLowerBound()); }
        };
    // </editor-fold>

    public Type createMethodTypeWithParameters(Type original, List<Type> newParams) {
        return original.accept(methodWithParameters, newParams);
    }
    // where
        private final MapVisitor<List<Type>> methodWithParameters = new MapVisitor<List<Type>>() {
            public Type visitType(Type t, List<Type> newParams) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, List<Type> newParams) {
                return new MethodType(newParams, t.restype, t.thrown, t.tsym);
            }
            public Type visitForAll(ForAll t, List<Type> newParams) {
                return new ForAll(t.tvars, t.qtype.accept(this, newParams));
            }
        };

    public Type createMethodTypeWithThrown(Type original, List<Type> newThrown) {
        return original.accept(methodWithThrown, newThrown);
    }
    // where
        private final MapVisitor<List<Type>> methodWithThrown = new MapVisitor<List<Type>>() {
            public Type visitType(Type t, List<Type> newThrown) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, List<Type> newThrown) {
                return new MethodType(t.argtypes, t.restype, newThrown, t.tsym);
            }
            public Type visitForAll(ForAll t, List<Type> newThrown) {
                return new ForAll(t.tvars, t.qtype.accept(this, newThrown));
            }
        };

    public Type createMethodTypeWithReturn(Type original, Type newReturn) {
        return original.accept(methodWithReturn, newReturn);
    }
    // where
        private final MapVisitor<Type> methodWithReturn = new MapVisitor<Type>() {
            public Type visitType(Type t, Type newReturn) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, Type newReturn) {
                return new MethodType(t.argtypes, newReturn, t.thrown, t.tsym);
            }
            public Type visitForAll(ForAll t, Type newReturn) {
                return new ForAll(t.tvars, t.qtype.accept(this, newReturn));
            }
        };

    // <editor-fold defaultstate="collapsed" desc="createErrorType">
    public Type createErrorType(Type originalType) {
        return new ErrorType(originalType, syms.errSymbol);
    }

    public Type createErrorType(ClassSymbol c, Type originalType) {
        return new ErrorType(c, originalType);
    }

    public Type createErrorType(Name name, TypeSymbol container, Type originalType) {
        return new ErrorType(name, container, originalType);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="rank">
    /**
     * The rank of a class is the length of the longest path between
     * the class and java.lang.Object in the class inheritance
     * graph. Undefined for all but reference types.
     */
    public int rank(Type t) {
        t = t.unannotatedType();
        switch(t.getTag()) {
        case CLASS: {
            ClassType cls = (ClassType)t;
            if (cls.rank_field < 0) {
                Name fullname = cls.tsym.getQualifiedName();
                if (fullname == names.java_lang_Object)
                    cls.rank_field = 0;
                else {
                    int r = rank(supertype(cls));
                    for (List<Type> l = interfaces(cls);
                         l.nonEmpty();
                         l = l.tail) {
                        if (rank(l.head) > r)
                            r = rank(l.head);
                    }
                    cls.rank_field = r + 1;
                }
            }
            return cls.rank_field;
        }
        case TYPEVAR: {
            TypeVar tvar = (TypeVar)t;
            if (tvar.rank_field < 0) {
                int r = rank(supertype(tvar));
                for (List<Type> l = interfaces(tvar);
                     l.nonEmpty();
                     l = l.tail) {
                    if (rank(l.head) > r) r = rank(l.head);
                }
                tvar.rank_field = r + 1;
            }
            return tvar.rank_field;
        }
        case ERROR:
            return 0;
        default:
            throw new AssertionError();
        }
    }
    // </editor-fold>

    /**
     * Helper method for generating a string representation of a given type
     * accordingly to a given locale
     */
    public String toString(Type t, Locale locale) {
        return Printer.createStandardPrinter(messages).visit(t, locale);
    }

    /**
     * Helper method for generating a string representation of a given type
     * accordingly to a given locale
     */
    public String toString(Symbol t, Locale locale) {
        return Printer.createStandardPrinter(messages).visit(t, locale);
    }

    // <editor-fold defaultstate="collapsed" desc="toString">
    /**
     * This toString is slightly more descriptive than the one on Type.
     *
     * @deprecated Types.toString(Type t, Locale l) provides better support
     * for localization
     */
    @Deprecated
    public String toString(Type t) {
        if (t.hasTag(FORALL)) {
            ForAll forAll = (ForAll)t;
            return typaramsString(forAll.tvars) + forAll.qtype;
        }
        return "" + t;
    }
    // where
        private String typaramsString(List<Type> tvars) {
            StringBuilder s = new StringBuilder();
            s.append('<');
            boolean first = true;
            for (Type t : tvars) {
                if (!first) s.append(", ");
                first = false;
                appendTyparamString(((TypeVar)t.unannotatedType()), s);
            }
            s.append('>');
            return s.toString();
        }
        private void appendTyparamString(TypeVar t, StringBuilder buf) {
            buf.append(t);
            if (t.bound == null ||
                t.bound.tsym.getQualifiedName() == names.java_lang_Object)
                return;
            buf.append(" extends "); // Java syntax; no need for i18n
            Type bound = t.bound;
            if (!bound.isCompound()) {
                buf.append(bound);
            } else if ((erasure(t).tsym.flags() & INTERFACE) == 0) {
                buf.append(supertype(t));
                for (Type intf : interfaces(t)) {
                    buf.append('&');
                    buf.append(intf);
                }
            } else {
                // No superclass was given in bounds.
                // In this case, supertype is Object, erasure is first interface.
                boolean first = true;
                for (Type intf : interfaces(t)) {
                    if (!first) buf.append('&');
                    first = false;
                    buf.append(intf);
                }
            }
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Determining least upper bounds of types">
    /**
     * A cache for closures.
     *
     * <p>A closure is a list of all the supertypes and interfaces of
     * a class or interface type, ordered by ClassSymbol.precedes
     * (that is, subclasses come first, arbitrary but fixed
     * otherwise).
     */
    private Map<Type,List<Type>> closureCache = new HashMap<Type,List<Type>>();

    /**
     * Returns the closure of a class or interface type.
     */
    public List<Type> closure(Type t) {
        List<Type> cl = closureCache.get(t);
        if (cl == null) {
            Type st = supertype(t);
            if (!t.isCompound()) {
                if (st.hasTag(CLASS)) {
                    cl = insert(closure(st), t);
                } else if (st.hasTag(TYPEVAR)) {
                    cl = closure(st).prepend(t);
                } else {
                    cl = List.of(t);
                }
            } else {
                cl = closure(supertype(t));
            }
            for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail)
                cl = union(cl, closure(l.head));
            closureCache.put(t, cl);
        }
        return cl;
    }

    /**
     * Insert a type in a closure
     */
    public List<Type> insert(List<Type> cl, Type t) {
        if (cl.isEmpty() || t.tsym.precedes(cl.head.tsym, this)) {
            return cl.prepend(t);
        } else if (cl.head.tsym.precedes(t.tsym, this)) {
            return insert(cl.tail, t).prepend(cl.head);
        } else {
            return cl;
        }
    }

    /**
     * Form the union of two closures
     */
    public List<Type> union(List<Type> cl1, List<Type> cl2) {
        if (cl1.isEmpty()) {
            return cl2;
        } else if (cl2.isEmpty()) {
            return cl1;
        } else if (cl1.head.tsym.precedes(cl2.head.tsym, this)) {
            return union(cl1.tail, cl2).prepend(cl1.head);
        } else if (cl2.head.tsym.precedes(cl1.head.tsym, this)) {
            return union(cl1, cl2.tail).prepend(cl2.head);
        } else {
            return union(cl1.tail, cl2.tail).prepend(cl1.head);
        }
    }

    /**
     * Intersect two closures
     */
    public List<Type> intersect(List<Type> cl1, List<Type> cl2) {
        if (cl1 == cl2)
            return cl1;
        if (cl1.isEmpty() || cl2.isEmpty())
            return List.nil();
        if (cl1.head.tsym.precedes(cl2.head.tsym, this))
            return intersect(cl1.tail, cl2);
        if (cl2.head.tsym.precedes(cl1.head.tsym, this))
            return intersect(cl1, cl2.tail);
        if (isSameType(cl1.head, cl2.head))
            return intersect(cl1.tail, cl2.tail).prepend(cl1.head);
        if (cl1.head.tsym == cl2.head.tsym &&
            cl1.head.hasTag(CLASS) && cl2.head.hasTag(CLASS)) {
            if (cl1.head.isParameterized() && cl2.head.isParameterized()) {
                Type merge = merge(cl1.head,cl2.head);
                return intersect(cl1.tail, cl2.tail).prepend(merge);
            }
            if (cl1.head.isRaw() || cl2.head.isRaw())
                return intersect(cl1.tail, cl2.tail).prepend(erasure(cl1.head));
        }
        return intersect(cl1.tail, cl2.tail);
    }
    // where
        class TypePair {
            final Type t1;
            final Type t2;
            TypePair(Type t1, Type t2) {
                this.t1 = t1;
                this.t2 = t2;
            }
            @Override
            public int hashCode() {
                return 127 * Types.this.hashCode(t1) + Types.this.hashCode(t2);
            }
            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof TypePair))
                    return false;
                TypePair typePair = (TypePair)obj;
                return isSameType(t1, typePair.t1)
                    && isSameType(t2, typePair.t2);
            }
        }
        Set<TypePair> mergeCache = new HashSet<TypePair>();
        private Type merge(Type c1, Type c2) {
            ClassType class1 = (ClassType) c1;
            List<Type> act1 = class1.getTypeArguments();
            ClassType class2 = (ClassType) c2;
            List<Type> act2 = class2.getTypeArguments();
            ListBuffer<Type> merged = new ListBuffer<Type>();
            List<Type> typarams = class1.tsym.type.getTypeArguments();

            while (act1.nonEmpty() && act2.nonEmpty() && typarams.nonEmpty()) {
                if (containsType(act1.head, act2.head)) {
                    merged.append(act1.head);
                } else if (containsType(act2.head, act1.head)) {
                    merged.append(act2.head);
                } else {
                    TypePair pair = new TypePair(c1, c2);
                    Type m;
                    if (mergeCache.add(pair)) {
                        m = new WildcardType(lub(upperBound(act1.head),
                                                 upperBound(act2.head)),
                                             BoundKind.EXTENDS,
                                             syms.boundClass);
                        mergeCache.remove(pair);
                    } else {
                        m = new WildcardType(syms.objectType,
                                             BoundKind.UNBOUND,
                                             syms.boundClass);
                    }
                    merged.append(m.withTypeVar(typarams.head));
                }
                act1 = act1.tail;
                act2 = act2.tail;
                typarams = typarams.tail;
            }
            Assert.check(act1.isEmpty() && act2.isEmpty() && typarams.isEmpty());
            return new ClassType(class1.getEnclosingType(), merged.toList(), class1.tsym);
        }

    /**
     * Return the minimum type of a closure, a compound type if no
     * unique minimum exists.
     */
    private Type compoundMin(List<Type> cl) {
        if (cl.isEmpty()) return syms.objectType;
        List<Type> compound = closureMin(cl);
        if (compound.isEmpty())
            return null;
        else if (compound.tail.isEmpty())
            return compound.head;
        else
            return makeCompoundType(compound);
    }

    /**
     * Return the minimum types of a closure, suitable for computing
     * compoundMin or glb.
     */
    private List<Type> closureMin(List<Type> cl) {
        ListBuffer<Type> classes = lb();
        ListBuffer<Type> interfaces = lb();
        while (!cl.isEmpty()) {
            Type current = cl.head;
            if (current.isInterface())
                interfaces.append(current);
            else
                classes.append(current);
            ListBuffer<Type> candidates = lb();
            for (Type t : cl.tail) {
                if (!isSubtypeNoCapture(current, t))
                    candidates.append(t);
            }
            cl = candidates.toList();
        }
        return classes.appendList(interfaces).toList();
    }

    /**
     * Return the least upper bound of pair of types.  if the lub does
     * not exist return null.
     */
    public Type lub(Type t1, Type t2) {
        return lub(List.of(t1, t2));
    }

    /**
     * Return the least upper bound (lub) of set of types.  If the lub
     * does not exist return the type of null (bottom).
     */
    public Type lub(List<Type> ts) {
        final int ARRAY_BOUND = 1;
        final int CLASS_BOUND = 2;
        int boundkind = 0;
        for (Type t : ts) {
            switch (t.getTag()) {
            case CLASS:
                boundkind |= CLASS_BOUND;
                break;
            case ARRAY:
                boundkind |= ARRAY_BOUND;
                break;
            case  TYPEVAR:
                do {
                    t = t.getUpperBound();
                } while (t.hasTag(TYPEVAR));
                if (t.hasTag(ARRAY)) {
                    boundkind |= ARRAY_BOUND;
                } else {
                    boundkind |= CLASS_BOUND;
                }
                break;
            default:
                if (t.isPrimitive())
                    return syms.errType;
            }
        }
        switch (boundkind) {
        case 0:
            return syms.botType;

        case ARRAY_BOUND:
            // calculate lub(A[], B[])
            List<Type> elements = Type.map(ts, elemTypeFun);
            for (Type t : elements) {
                if (t.isPrimitive()) {
                    // if a primitive type is found, then return
                    // arraySuperType unless all the types are the
                    // same
                    Type first = ts.head;
                    for (Type s : ts.tail) {
                        if (!isSameType(first, s)) {
                             // lub(int[], B[]) is Cloneable & Serializable
                            return arraySuperType();
                        }
                    }
                    // all the array types are the same, return one
                    // lub(int[], int[]) is int[]
                    return first;
                }
            }
            // lub(A[], B[]) is lub(A, B)[]
            return new ArrayType(lub(elements), syms.arrayClass);

        case CLASS_BOUND:
            // calculate lub(A, B)
            while (!ts.head.hasTag(CLASS) && !ts.head.hasTag(TYPEVAR)) {
                ts = ts.tail;
            }
            Assert.check(!ts.isEmpty());
            //step 1 - compute erased candidate set (EC)
            List<Type> cl = erasedSupertypes(ts.head);
            for (Type t : ts.tail) {
                if (t.hasTag(CLASS) || t.hasTag(TYPEVAR))
                    cl = intersect(cl, erasedSupertypes(t));
            }
            //step 2 - compute minimal erased candidate set (MEC)
            List<Type> mec = closureMin(cl);
            //step 3 - for each element G in MEC, compute lci(Inv(G))
            List<Type> candidates = List.nil();
            for (Type erasedSupertype : mec) {
                List<Type> lci = List.of(asSuper(ts.head, erasedSupertype.tsym));
                for (Type t : ts) {
                    lci = intersect(lci, List.of(asSuper(t, erasedSupertype.tsym)));
                }
                candidates = candidates.appendList(lci);
            }
            //step 4 - let MEC be { G1, G2 ... Gn }, then we have that
            //lub = lci(Inv(G1)) & lci(Inv(G2)) & ... & lci(Inv(Gn))
            return compoundMin(candidates);

        default:
            // calculate lub(A, B[])
            List<Type> classes = List.of(arraySuperType());
            for (Type t : ts) {
                if (!t.hasTag(ARRAY)) // Filter out any arrays
                    classes = classes.prepend(t);
            }
            // lub(A, B[]) is lub(A, arraySuperType)
            return lub(classes);
        }
    }
    // where
        List<Type> erasedSupertypes(Type t) {
            ListBuffer<Type> buf = lb();
            for (Type sup : closure(t)) {
                if (sup.hasTag(TYPEVAR)) {
                    buf.append(sup);
                } else {
                    buf.append(erasure(sup));
                }
            }
            return buf.toList();
        }

        private Type arraySuperType = null;
        private Type arraySuperType() {
            // initialized lazily to avoid problems during compiler startup
            if (arraySuperType == null) {
                synchronized (this) {
                    if (arraySuperType == null) {
                        // JLS 10.8: all arrays implement Cloneable and Serializable.
                        arraySuperType = makeCompoundType(List.of(syms.serializableType,
                                                                  syms.cloneableType), true);
                    }
                }
            }
            return arraySuperType;
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Greatest lower bound">
    public Type glb(List<Type> ts) {
        Type t1 = ts.head;
        for (Type t2 : ts.tail) {
            if (t1.isErroneous())
                return t1;
            t1 = glb(t1, t2);
        }
        return t1;
    }
    //where
    public Type glb(Type t, Type s) {
        if (s == null)
            return t;
        else if (t.isPrimitive() || s.isPrimitive())
            return syms.errType;
        else if (isSubtypeNoCapture(t, s))
            return t;
        else if (isSubtypeNoCapture(s, t))
            return s;

        List<Type> closure = union(closure(t), closure(s));
        List<Type> bounds = closureMin(closure);

        if (bounds.isEmpty()) {             // length == 0
            return syms.objectType;
        } else if (bounds.tail.isEmpty()) { // length == 1
            return bounds.head;
        } else {                            // length > 1
            int classCount = 0;
            for (Type bound : bounds)
                if (!bound.isInterface())
                    classCount++;
            if (classCount > 1)
                return createErrorType(t);
        }
        return makeCompoundType(bounds);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="hashCode">
    /**
     * Compute a hash code on a type.
     */
    public int hashCode(Type t) {
        return hashCode.visit(t);
    }
    // where
        private static final UnaryVisitor<Integer> hashCode = new UnaryVisitor<Integer>() {

            public Integer visitType(Type t, Void ignored) {
                return t.getTag().ordinal();
            }

            @Override
            public Integer visitClassType(ClassType t, Void ignored) {
                int result = visit(t.getEnclosingType());
                result *= 127;
                result += t.tsym.flatName().hashCode();
                for (Type s : t.getTypeArguments()) {
                    result *= 127;
                    result += visit(s);
                }
                return result;
            }

            @Override
            public Integer visitMethodType(MethodType t, Void ignored) {
                int h = METHOD.ordinal();
                for (List<Type> thisargs = t.argtypes;
                     thisargs.tail != null;
                     thisargs = thisargs.tail)
                    h = (h << 5) + visit(thisargs.head);
                return (h << 5) + visit(t.restype);
            }

            @Override
            public Integer visitWildcardType(WildcardType t, Void ignored) {
                int result = t.kind.hashCode();
                if (t.type != null) {
                    result *= 127;
                    result += visit(t.type);
                }
                return result;
            }

            @Override
            public Integer visitArrayType(ArrayType t, Void ignored) {
                return visit(t.elemtype) + 12;
            }

            @Override
            public Integer visitTypeVar(TypeVar t, Void ignored) {
                return System.identityHashCode(t.tsym);
            }

            @Override
            public Integer visitUndetVar(UndetVar t, Void ignored) {
                return System.identityHashCode(t);
            }

            @Override
            public Integer visitErrorType(ErrorType t, Void ignored) {
                return 0;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Return-Type-Substitutable">
    /**
     * Does t have a result that is a subtype of the result type of s,
     * suitable for covariant returns?  It is assumed that both types
     * are (possibly polymorphic) method types.  Monomorphic method
     * types are handled in the obvious way.  Polymorphic method types
     * require renaming all type variables of one to corresponding
     * type variables in the other, where correspondence is by
     * position in the type parameter list. */
    public boolean resultSubtype(Type t, Type s, Warner warner) {
        List<Type> tvars = t.getTypeArguments();
        List<Type> svars = s.getTypeArguments();
        Type tres = t.getReturnType();
        Type sres = subst(s.getReturnType(), svars, tvars);
        return covariantReturnType(tres, sres, warner);
    }

    /**
     * Return-Type-Substitutable.
     * @jls section 8.4.5
     */
    public boolean returnTypeSubstitutable(Type r1, Type r2) {
        if (hasSameArgs(r1, r2))
            return resultSubtype(r1, r2, noWarnings);
        else
            return covariantReturnType(r1.getReturnType(),
                                       erasure(r2.getReturnType()),
                                       noWarnings);
    }

    public boolean returnTypeSubstitutable(Type r1,
                                           Type r2, Type r2res,
                                           Warner warner) {
        if (isSameType(r1.getReturnType(), r2res))
            return true;
        if (r1.getReturnType().isPrimitive() || r2res.isPrimitive())
            return false;

        if (hasSameArgs(r1, r2))
            return covariantReturnType(r1.getReturnType(), r2res, warner);
        if (!allowCovariantReturns)
            return false;
        if (isSubtypeUnchecked(r1.getReturnType(), r2res, warner))
            return true;
        if (!isSubtype(r1.getReturnType(), erasure(r2res)))
            return false;
        warner.warn(LintCategory.UNCHECKED);
        return true;
    }

    /**
     * Is t an appropriate return type in an overrider for a
     * method that returns s?
     */
    public boolean covariantReturnType(Type t, Type s, Warner warner) {
        return
            isSameType(t, s) ||
            allowCovariantReturns &&
            !t.isPrimitive() &&
            !s.isPrimitive() &&
            isAssignable(t, s, warner);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Box/unbox support">
    /**
     * Return the class that boxes the given primitive.
     */
    public ClassSymbol boxedClass(Type t) {
        return reader.enterClass(syms.boxedName[t.getTag().ordinal()]);
    }

    /**
     * Return the boxed type if 't' is primitive, otherwise return 't' itself.
     */
    public Type boxedTypeOrType(Type t) {
        return t.isPrimitive() ?
            boxedClass(t).type :
            t;
    }

    /**
     * Return the primitive type corresponding to a boxed type.
     */
    public Type unboxedType(Type t) {
        if (allowBoxing) {
            for (int i=0; i<syms.boxedName.length; i++) {
                Name box = syms.boxedName[i];
                if (box != null &&
                    asSuper(t, reader.enterClass(box)) != null)
                    return syms.typeOfTag[i];
            }
        }
        return Type.noType;
    }

    /**
     * Return the unboxed type if 't' is a boxed class, otherwise return 't' itself.
     */
    public Type unboxedTypeOrType(Type t) {
        Type unboxedType = unboxedType(t);
        return unboxedType.hasTag(NONE) ? t : unboxedType;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Capture conversion">
    /*
     * JLS 5.1.10 Capture Conversion:
     *
     * Let G name a generic type declaration with n formal type
     * parameters A1 ... An with corresponding bounds U1 ... Un. There
     * exists a capture conversion from G<T1 ... Tn> to G<S1 ... Sn>,
     * where, for 1 <= i <= n:
     *
     * + If Ti is a wildcard type argument (4.5.1) of the form ? then
     *   Si is a fresh type variable whose upper bound is
     *   Ui[A1 := S1, ..., An := Sn] and whose lower bound is the null
     *   type.
     *
     * + If Ti is a wildcard type argument of the form ? extends Bi,
     *   then Si is a fresh type variable whose upper bound is
     *   glb(Bi, Ui[A1 := S1, ..., An := Sn]) and whose lower bound is
     *   the null type, where glb(V1,... ,Vm) is V1 & ... & Vm. It is
     *   a compile-time error if for any two classes (not interfaces)
     *   Vi and Vj,Vi is not a subclass of Vj or vice versa.
     *
     * + If Ti is a wildcard type argument of the form ? super Bi,
     *   then Si is a fresh type variable whose upper bound is
     *   Ui[A1 := S1, ..., An := Sn] and whose lower bound is Bi.
     *
     * + Otherwise, Si = Ti.
     *
     * Capture conversion on any type other than a parameterized type
     * (4.5) acts as an identity conversion (5.1.1). Capture
     * conversions never require a special action at run time and
     * therefore never throw an exception at run time.
     *
     * Capture conversion is not applied recursively.
     */
    /**
     * Capture conversion as specified by the JLS.
     */

    public List<Type> capture(List<Type> ts) {
        List<Type> buf = List.nil();
        for (Type t : ts) {
            buf = buf.prepend(capture(t));
        }
        return buf.reverse();
    }
    public Type capture(Type t) {
        if (!t.hasTag(CLASS))
            return t;
        if (t.getEnclosingType() != Type.noType) {
            Type capturedEncl = capture(t.getEnclosingType());
            if (capturedEncl != t.getEnclosingType()) {
                Type type1 = memberType(capturedEncl, t.tsym);
                t = subst(type1, t.tsym.type.getTypeArguments(), t.getTypeArguments());
            }
        }
        t = t.unannotatedType();
        ClassType cls = (ClassType)t;
        if (cls.isRaw() || !cls.isParameterized())
            return cls;

        ClassType G = (ClassType)cls.asElement().asType();
        List<Type> A = G.getTypeArguments();
        List<Type> T = cls.getTypeArguments();
        List<Type> S = freshTypeVariables(T);

        List<Type> currentA = A;
        List<Type> currentT = T;
        List<Type> currentS = S;
        boolean captured = false;
        while (!currentA.isEmpty() &&
               !currentT.isEmpty() &&
               !currentS.isEmpty()) {
            if (currentS.head != currentT.head) {
                captured = true;
                WildcardType Ti = (WildcardType)currentT.head.unannotatedType();
                Type Ui = currentA.head.getUpperBound();
                CapturedType Si = (CapturedType)currentS.head.unannotatedType();
                if (Ui == null)
                    Ui = syms.objectType;
                switch (Ti.kind) {
                case UNBOUND:
                    Si.bound = subst(Ui, A, S);
                    Si.lower = syms.botType;
                    break;
                case EXTENDS:
                    Si.bound = glb(Ti.getExtendsBound(), subst(Ui, A, S));
                    Si.lower = syms.botType;
                    break;
                case SUPER:
                    Si.bound = subst(Ui, A, S);
                    Si.lower = Ti.getSuperBound();
                    break;
                }
                if (Si.bound == Si.lower)
                    currentS.head = Si.bound;
            }
            currentA = currentA.tail;
            currentT = currentT.tail;
            currentS = currentS.tail;
        }
        if (!currentA.isEmpty() || !currentT.isEmpty() || !currentS.isEmpty())
            return erasure(t); // some "rare" type involved

        if (captured)
            return new ClassType(cls.getEnclosingType(), S, cls.tsym);
        else
            return t;
    }
    // where
        public List<Type> freshTypeVariables(List<Type> types) {
            ListBuffer<Type> result = lb();
            for (Type t : types) {
                if (t.hasTag(WILDCARD)) {
                    t = t.unannotatedType();
                    Type bound = ((WildcardType)t).getExtendsBound();
                    if (bound == null)
                        bound = syms.objectType;
                    result.append(new CapturedType(capturedName,
                                                   syms.noSymbol,
                                                   bound,
                                                   syms.botType,
                                                   (WildcardType)t));
                } else {
                    result.append(t);
                }
            }
            return result.toList();
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Internal utility methods">
    private List<Type> upperBounds(List<Type> ss) {
        if (ss.isEmpty()) return ss;
        Type head = upperBound(ss.head);
        List<Type> tail = upperBounds(ss.tail);
        if (head != ss.head || tail != ss.tail)
            return tail.prepend(head);
        else
            return ss;
    }

    private boolean sideCast(Type from, Type to, Warner warn) {
        // We are casting from type $from$ to type $to$, which are
        // non-final unrelated types.  This method
        // tries to reject a cast by transferring type parameters
        // from $to$ to $from$ by common superinterfaces.
        boolean reverse = false;
        Type target = to;
        if ((to.tsym.flags() & INTERFACE) == 0) {
            Assert.check((from.tsym.flags() & INTERFACE) != 0);
            reverse = true;
            to = from;
            from = target;
        }
        List<Type> commonSupers = superClosure(to, erasure(from));
        boolean giveWarning = commonSupers.isEmpty();
        // The arguments to the supers could be unified here to
        // get a more accurate analysis
        while (commonSupers.nonEmpty()) {
            Type t1 = asSuper(from, commonSupers.head.tsym);
            Type t2 = commonSupers.head; // same as asSuper(to, commonSupers.head.tsym);
            if (disjointTypes(t1.getTypeArguments(), t2.getTypeArguments()))
                return false;
            giveWarning = giveWarning || (reverse ? giveWarning(t2, t1) : giveWarning(t1, t2));
            commonSupers = commonSupers.tail;
        }
        if (giveWarning && !isReifiable(reverse ? from : to))
            warn.warn(LintCategory.UNCHECKED);
        if (!allowCovariantReturns)
            // reject if there is a common method signature with
            // incompatible return types.
            chk.checkCompatibleAbstracts(warn.pos(), from, to);
        return true;
    }

    private boolean sideCastFinal(Type from, Type to, Warner warn) {
        // We are casting from type $from$ to type $to$, which are
        // unrelated types one of which is final and the other of
        // which is an interface.  This method
        // tries to reject a cast by transferring type parameters
        // from the final class to the interface.
        boolean reverse = false;
        Type target = to;
        if ((to.tsym.flags() & INTERFACE) == 0) {
            Assert.check((from.tsym.flags() & INTERFACE) != 0);
            reverse = true;
            to = from;
            from = target;
        }
        Assert.check((from.tsym.flags() & FINAL) != 0);
        Type t1 = asSuper(from, to.tsym);
        if (t1 == null) return false;
        Type t2 = to;
        if (disjointTypes(t1.getTypeArguments(), t2.getTypeArguments()))
            return false;
        if (!allowCovariantReturns)
            // reject if there is a common method signature with
            // incompatible return types.
            chk.checkCompatibleAbstracts(warn.pos(), from, to);
        if (!isReifiable(target) &&
            (reverse ? giveWarning(t2, t1) : giveWarning(t1, t2)))
            warn.warn(LintCategory.UNCHECKED);
        return true;
    }

    private boolean giveWarning(Type from, Type to) {
        List<Type> bounds = to.isCompound() ?
                ((IntersectionClassType)to.unannotatedType()).getComponents() : List.of(to);
        for (Type b : bounds) {
            Type subFrom = asSub(from, b.tsym);
            if (b.isParameterized() &&
                    (!(isUnbounded(b) ||
                    isSubtype(from, b) ||
                    ((subFrom != null) && containsType(b.allparams(), subFrom.allparams()))))) {
                return true;
            }
        }
        return false;
    }

    private List<Type> superClosure(Type t, Type s) {
        List<Type> cl = List.nil();
        for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail) {
            if (isSubtype(s, erasure(l.head))) {
                cl = insert(cl, l.head);
            } else {
                cl = union(cl, superClosure(l.head, s));
            }
        }
        return cl;
    }

    private boolean containsTypeEquivalent(Type t, Type s) {
        return
            isSameType(t, s) || // shortcut
            containsType(t, s) && containsType(s, t);
    }

    // <editor-fold defaultstate="collapsed" desc="adapt">
    /**
     * Adapt a type by computing a substitution which maps a source
     * type to a target type.
     *
     * @param source    the source type
     * @param target    the target type
     * @param from      the type variables of the computed substitution
     * @param to        the types of the computed substitution.
     */
    public void adapt(Type source,
                       Type target,
                       ListBuffer<Type> from,
                       ListBuffer<Type> to) throws AdaptFailure {
        new Adapter(from, to).adapt(source, target);
    }

    class Adapter extends SimpleVisitor<Void, Type> {

        ListBuffer<Type> from;
        ListBuffer<Type> to;
        Map<Symbol,Type> mapping;

        Adapter(ListBuffer<Type> from, ListBuffer<Type> to) {
            this.from = from;
            this.to = to;
            mapping = new HashMap<Symbol,Type>();
        }

        public void adapt(Type source, Type target) throws AdaptFailure {
            visit(source, target);
            List<Type> fromList = from.toList();
            List<Type> toList = to.toList();
            while (!fromList.isEmpty()) {
                Type val = mapping.get(fromList.head.tsym);
                if (toList.head != val)
                    toList.head = val;
                fromList = fromList.tail;
                toList = toList.tail;
            }
        }

        @Override
        public Void visitClassType(ClassType source, Type target) throws AdaptFailure {
            if (target.hasTag(CLASS))
                adaptRecursive(source.allparams(), target.allparams());
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType source, Type target) throws AdaptFailure {
            if (target.hasTag(ARRAY))
                adaptRecursive(elemtype(source), elemtype(target));
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType source, Type target) throws AdaptFailure {
            if (source.isExtendsBound())
                adaptRecursive(upperBound(source), upperBound(target));
            else if (source.isSuperBound())
                adaptRecursive(lowerBound(source), lowerBound(target));
            return null;
        }

        @Override
        public Void visitTypeVar(TypeVar source, Type target) throws AdaptFailure {
            // Check to see if there is
            // already a mapping for $source$, in which case
            // the old mapping will be merged with the new
            Type val = mapping.get(source.tsym);
            if (val != null) {
                if (val.isSuperBound() && target.isSuperBound()) {
                    val = isSubtype(lowerBound(val), lowerBound(target))
                        ? target : val;
                } else if (val.isExtendsBound() && target.isExtendsBound()) {
                    val = isSubtype(upperBound(val), upperBound(target))
                        ? val : target;
                } else if (!isSameType(val, target)) {
                    throw new AdaptFailure();
                }
            } else {
                val = target;
                from.append(source);
                to.append(target);
            }
            mapping.put(source.tsym, val);
            return null;
        }

        @Override
        public Void visitType(Type source, Type target) {
            return null;
        }

        private Set<TypePair> cache = new HashSet<TypePair>();

        private void adaptRecursive(Type source, Type target) {
            TypePair pair = new TypePair(source, target);
            if (cache.add(pair)) {
                try {
                    visit(source, target);
                } finally {
                    cache.remove(pair);
                }
            }
        }

        private void adaptRecursive(List<Type> source, List<Type> target) {
            if (source.length() == target.length()) {
                while (source.nonEmpty()) {
                    adaptRecursive(source.head, target.head);
                    source = source.tail;
                    target = target.tail;
                }
            }
        }
    }

    public static class AdaptFailure extends RuntimeException {
        static final long serialVersionUID = -7490231548272701566L;
    }

    private void adaptSelf(Type t,
                           ListBuffer<Type> from,
                           ListBuffer<Type> to) {
        try {
            //if (t.tsym.type != t)
                adapt(t.tsym.type, t, from, to);
        } catch (AdaptFailure ex) {
            // Adapt should never fail calculating a mapping from
            // t.tsym.type to t as there can be no merge problem.
            throw new AssertionError(ex);
        }
    }
    // </editor-fold>

    /**
     * Rewrite all type variables (universal quantifiers) in the given
     * type to wildcards (existential quantifiers).  This is used to
     * determine if a cast is allowed.  For example, if high is true
     * and {@code T <: Number}, then {@code List<T>} is rewritten to
     * {@code List<?  extends Number>}.  Since {@code List<Integer> <:
     * List<? extends Number>} a {@code List<T>} can be cast to {@code
     * List<Integer>} with a warning.
     * @param t a type
     * @param high if true return an upper bound; otherwise a lower
     * bound
     * @param rewriteTypeVars only rewrite captured wildcards if false;
     * otherwise rewrite all type variables
     * @return the type rewritten with wildcards (existential
     * quantifiers) only
     */
    private Type rewriteQuantifiers(Type t, boolean high, boolean rewriteTypeVars) {
        return new Rewriter(high, rewriteTypeVars).visit(t);
    }

    class Rewriter extends UnaryVisitor<Type> {

        boolean high;
        boolean rewriteTypeVars;

        Rewriter(boolean high, boolean rewriteTypeVars) {
            this.high = high;
            this.rewriteTypeVars = rewriteTypeVars;
        }

        @Override
        public Type visitClassType(ClassType t, Void s) {
            ListBuffer<Type> rewritten = new ListBuffer<Type>();
            boolean changed = false;
            for (Type arg : t.allparams()) {
                Type bound = visit(arg);
                if (arg != bound) {
                    changed = true;
                }
                rewritten.append(bound);
            }
            if (changed)
                return subst(t.tsym.type,
                        t.tsym.type.allparams(),
                        rewritten.toList());
            else
                return t;
        }

        public Type visitType(Type t, Void s) {
            return high ? upperBound(t) : lowerBound(t);
        }

        @Override
        public Type visitCapturedType(CapturedType t, Void s) {
            Type w_bound = t.wildcard.type;
            Type bound = w_bound.contains(t) ?
                        erasure(w_bound) :
                        visit(w_bound);
            return rewriteAsWildcardType(visit(bound), t.wildcard.bound, t.wildcard.kind);
        }

        @Override
        public Type visitTypeVar(TypeVar t, Void s) {
            if (rewriteTypeVars) {
                Type bound = t.bound.contains(t) ?
                        erasure(t.bound) :
                        visit(t.bound);
                return rewriteAsWildcardType(bound, t, EXTENDS);
            } else {
                return t;
            }
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void s) {
            Type bound2 = visit(t.type);
            return t.type == bound2 ? t : rewriteAsWildcardType(bound2, t.bound, t.kind);
        }

        private Type rewriteAsWildcardType(Type bound, TypeVar formal, BoundKind bk) {
            switch (bk) {
               case EXTENDS: return high ?
                       makeExtendsWildcard(B(bound), formal) :
                       makeExtendsWildcard(syms.objectType, formal);
               case SUPER: return high ?
                       makeSuperWildcard(syms.botType, formal) :
                       makeSuperWildcard(B(bound), formal);
               case UNBOUND: return makeExtendsWildcard(syms.objectType, formal);
               default:
                   Assert.error("Invalid bound kind " + bk);
                   return null;
            }
        }

        Type B(Type t) {
            while (t.hasTag(WILDCARD)) {
                WildcardType w = (WildcardType)t.unannotatedType();
                t = high ?
                    w.getExtendsBound() :
                    w.getSuperBound();
                if (t == null) {
                    t = high ? syms.objectType : syms.botType;
                }
            }
            return t;
        }
    }


    /**
     * Create a wildcard with the given upper (extends) bound; create
     * an unbounded wildcard if bound is Object.
     *
     * @param bound the upper bound
     * @param formal the formal type parameter that will be
     * substituted by the wildcard
     */
    private WildcardType makeExtendsWildcard(Type bound, TypeVar formal) {
        if (bound == syms.objectType) {
            return new WildcardType(syms.objectType,
                                    BoundKind.UNBOUND,
                                    syms.boundClass,
                                    formal);
        } else {
            return new WildcardType(bound,
                                    BoundKind.EXTENDS,
                                    syms.boundClass,
                                    formal);
        }
    }

    /**
     * Create a wildcard with the given lower (super) bound; create an
     * unbounded wildcard if bound is bottom (type of {@code null}).
     *
     * @param bound the lower bound
     * @param formal the formal type parameter that will be
     * substituted by the wildcard
     */
    private WildcardType makeSuperWildcard(Type bound, TypeVar formal) {
        if (bound.hasTag(BOT)) {
            return new WildcardType(syms.objectType,
                                    BoundKind.UNBOUND,
                                    syms.boundClass,
                                    formal);
        } else {
            return new WildcardType(bound,
                                    BoundKind.SUPER,
                                    syms.boundClass,
                                    formal);
        }
    }

    /**
     * A wrapper for a type that allows use in sets.
     */
    public static class UniqueType {
        public final Type type;
        final Types types;

        public UniqueType(Type type, Types types) {
            this.type = type;
            this.types = types;
        }

        public int hashCode() {
            return types.hashCode(type);
        }

        public boolean equals(Object obj) {
            return (obj instanceof UniqueType) &&
                types.isSameAnnotatedType(type, ((UniqueType)obj).type);
        }

        public String toString() {
            return type.toString();
        }

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Visitors">
    /**
     * A default visitor for types.  All visitor methods except
     * visitType are implemented by delegating to visitType.  Concrete
     * subclasses must provide an implementation of visitType and can
     * override other methods as needed.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * type itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class DefaultTypeVisitor<R,S> implements Type.Visitor<R,S> {
        final public R visit(Type t, S s)               { return t.accept(this, s); }
        public R visitClassType(ClassType t, S s)       { return visitType(t, s); }
        public R visitWildcardType(WildcardType t, S s) { return visitType(t, s); }
        public R visitArrayType(ArrayType t, S s)       { return visitType(t, s); }
        public R visitMethodType(MethodType t, S s)     { return visitType(t, s); }
        public R visitPackageType(PackageType t, S s)   { return visitType(t, s); }
        public R visitTypeVar(TypeVar t, S s)           { return visitType(t, s); }
        public R visitCapturedType(CapturedType t, S s) { return visitType(t, s); }
        public R visitForAll(ForAll t, S s)             { return visitType(t, s); }
        public R visitUndetVar(UndetVar t, S s)         { return visitType(t, s); }
        public R visitErrorType(ErrorType t, S s)       { return visitType(t, s); }
        // Pretend annotations don't exist
        public R visitAnnotatedType(AnnotatedType t, S s) { return visit(t.underlyingType, s); }
    }

    /**
     * A default visitor for symbols.  All visitor methods except
     * visitSymbol are implemented by delegating to visitSymbol.  Concrete
     * subclasses must provide an implementation of visitSymbol and can
     * override other methods as needed.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * symbol itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class DefaultSymbolVisitor<R,S> implements Symbol.Visitor<R,S> {
        final public R visit(Symbol s, S arg)                   { return s.accept(this, arg); }
        public R visitClassSymbol(ClassSymbol s, S arg)         { return visitSymbol(s, arg); }
        public R visitMethodSymbol(MethodSymbol s, S arg)       { return visitSymbol(s, arg); }
        public R visitOperatorSymbol(OperatorSymbol s, S arg)   { return visitSymbol(s, arg); }
        public R visitPackageSymbol(PackageSymbol s, S arg)     { return visitSymbol(s, arg); }
        public R visitTypeSymbol(TypeSymbol s, S arg)           { return visitSymbol(s, arg); }
        public R visitVarSymbol(VarSymbol s, S arg)             { return visitSymbol(s, arg); }
    }

    /**
     * A <em>simple</em> visitor for types.  This visitor is simple as
     * captured wildcards, for-all types (generic methods), and
     * undetermined type variables (part of inference) are hidden.
     * Captured wildcards are hidden by treating them as type
     * variables and the rest are hidden by visiting their qtypes.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * type itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class SimpleVisitor<R,S> extends DefaultTypeVisitor<R,S> {
        @Override
        public R visitCapturedType(CapturedType t, S s) {
            return visitTypeVar(t, s);
        }
        @Override
        public R visitForAll(ForAll t, S s) {
            return visit(t.qtype, s);
        }
        @Override
        public R visitUndetVar(UndetVar t, S s) {
            return visit(t.qtype, s);
        }
    }

    /**
     * A plain relation on types.  That is a 2-ary function on the
     * form Type&nbsp;&times;&nbsp;Type&nbsp;&rarr;&nbsp;Boolean.
     * <!-- In plain text: Type x Type -> Boolean -->
     */
    public static abstract class TypeRelation extends SimpleVisitor<Boolean,Type> {}

    /**
     * A convenience visitor for implementing operations that only
     * require one argument (the type itself), that is, unary
     * operations.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     */
    public static abstract class UnaryVisitor<R> extends SimpleVisitor<R,Void> {
        final public R visit(Type t) { return t.accept(this, null); }
    }

    /**
     * A visitor for implementing a mapping from types to types.  The
     * default behavior of this class is to implement the identity
     * mapping (mapping a type to itself).  This can be overridden in
     * subclasses.
     *
     * @param <S> the type of the second argument (the first being the
     * type itself) of this mapping; use Void if a second argument is
     * not needed.
     */
    public static class MapVisitor<S> extends DefaultTypeVisitor<Type,S> {
        final public Type visit(Type t) { return t.accept(this, null); }
        public Type visitType(Type t, S s) { return t; }
    }
    // </editor-fold>


    // <editor-fold defaultstate="collapsed" desc="Annotation support">

    public RetentionPolicy getRetention(Attribute.Compound a) {
        return getRetention(a.type.tsym);
    }

    public RetentionPolicy getRetention(Symbol sym) {
        RetentionPolicy vis = RetentionPolicy.CLASS; // the default
        Attribute.Compound c = sym.attribute(syms.retentionType.tsym);
        if (c != null) {
            Attribute value = c.member(names.value);
            if (value != null && value instanceof Attribute.Enum) {
                Name levelName = ((Attribute.Enum)value).value.name;
                if (levelName == names.SOURCE) vis = RetentionPolicy.SOURCE;
                else if (levelName == names.CLASS) vis = RetentionPolicy.CLASS;
                else if (levelName == names.RUNTIME) vis = RetentionPolicy.RUNTIME;
                else ;// /* fail soft */ throw new AssertionError(levelName);
            }
        }
        return vis;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Signature Generation">

    public static abstract class SignatureGenerator {

        private final Types types;

        protected abstract void append(char ch);
        protected abstract void append(byte[] ba);
        protected abstract void append(Name name);
        protected void classReference(ClassSymbol c) { /* by default: no-op */ }

        protected SignatureGenerator(Types types) {
            this.types = types;
        }

        /**
         * Assemble signature of given type in string buffer.
         */
        public void assembleSig(Type type) {
            type = type.unannotatedType();
            switch (type.getTag()) {
                case BYTE:
                    append('B');
                    break;
                case SHORT:
                    append('S');
                    break;
                case CHAR:
                    append('C');
                    break;
                case INT:
                    append('I');
                    break;
                case LONG:
                    append('J');
                    break;
                case FLOAT:
                    append('F');
                    break;
                case DOUBLE:
                    append('D');
                    break;
                case BOOLEAN:
                    append('Z');
                    break;
                case VOID:
                    append('V');
                    break;
                case CLASS:
                    append('L');
                    assembleClassSig(type);
                    append(';');
                    break;
                case ARRAY:
                    ArrayType at = (ArrayType) type;
                    append('[');
                    assembleSig(at.elemtype);
                    break;
                case METHOD:
                    MethodType mt = (MethodType) type;
                    append('(');
                    assembleSig(mt.argtypes);
                    append(')');
                    assembleSig(mt.restype);
                    if (hasTypeVar(mt.thrown)) {
                        for (List<Type> l = mt.thrown; l.nonEmpty(); l = l.tail) {
                            append('^');
                            assembleSig(l.head);
                        }
                    }
                    break;
                case WILDCARD: {
                    Type.WildcardType ta = (Type.WildcardType) type;
                    switch (ta.kind) {
                        case SUPER:
                            append('-');
                            assembleSig(ta.type);
                            break;
                        case EXTENDS:
                            append('+');
                            assembleSig(ta.type);
                            break;
                        case UNBOUND:
                            append('*');
                            break;
                        default:
                            throw new AssertionError(ta.kind);
                    }
                    break;
                }
                case TYPEVAR:
                    append('T');
                    append(type.tsym.name);
                    append(';');
                    break;
                case FORALL:
                    Type.ForAll ft = (Type.ForAll) type;
                    assembleParamsSig(ft.tvars);
                    assembleSig(ft.qtype);
                    break;
                default:
                    throw new AssertionError("typeSig " + type.getTag());
            }
        }

        public boolean hasTypeVar(List<Type> l) {
            while (l.nonEmpty()) {
                if (l.head.hasTag(TypeTag.TYPEVAR)) {
                    return true;
                }
                l = l.tail;
            }
            return false;
        }

        public void assembleClassSig(Type type) {
            type = type.unannotatedType();
            ClassType ct = (ClassType) type;
            ClassSymbol c = (ClassSymbol) ct.tsym;
            classReference(c);
            Type outer = ct.getEnclosingType();
            if (outer.allparams().nonEmpty()) {
                boolean rawOuter =
                        c.owner.kind == Kinds.MTH || // either a local class
                        c.name == types.names.empty; // or anonymous
                assembleClassSig(rawOuter
                        ? types.erasure(outer)
                        : outer);
                append('.');
                Assert.check(c.flatname.startsWith(c.owner.enclClass().flatname));
                append(rawOuter
                        ? c.flatname.subName(c.owner.enclClass().flatname.getByteLength() + 1, c.flatname.getByteLength())
                        : c.name);
            } else {
                append(externalize(c.flatname));
            }
            if (ct.getTypeArguments().nonEmpty()) {
                append('<');
                assembleSig(ct.getTypeArguments());
                append('>');
            }
        }

        public void assembleParamsSig(List<Type> typarams) {
            append('<');
            for (List<Type> ts = typarams; ts.nonEmpty(); ts = ts.tail) {
                Type.TypeVar tvar = (Type.TypeVar) ts.head;
                append(tvar.tsym.name);
                List<Type> bounds = types.getBounds(tvar);
                if ((bounds.head.tsym.flags() & INTERFACE) != 0) {
                    append(':');
                }
                for (List<Type> l = bounds; l.nonEmpty(); l = l.tail) {
                    append(':');
                    assembleSig(l.head);
                }
            }
            append('>');
        }

        private void assembleSig(List<Type> types) {
            for (List<Type> ts = types; ts.nonEmpty(); ts = ts.tail) {
                assembleSig(ts.head);
            }
        }
    }
    // </editor-fold>
}
