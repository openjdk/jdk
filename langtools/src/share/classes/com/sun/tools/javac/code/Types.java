/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.comp.Check;

import static com.sun.tools.javac.code.Type.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.BoundKind.*;
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
    final ClassReader reader;
    final Source source;
    final Check chk;
    List<Warner> warnStack = List.nil();
    final Name capturedName;

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
        allowBoxing = Source.instance(context).allowBoxing();
        reader = ClassReader.instance(context);
        source = Source.instance(context);
        chk = Check.instance(context);
        capturedName = names.fromString("<captured wildcard>");
        messages = JavacMessages.instance(context);
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
                                                        (TypeVar)parms.head);
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
                            qs.append(new WildcardType(syms.objectType, BoundKind.UNBOUND, syms.boundClass, (TypeVar) iter.head));
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
     * Is t a subtype of or convertiable via boxing/unboxing
     * convertions to s?
     */
    public boolean isConvertible(Type t, Type s, Warner warn) {
        boolean tPrimitive = t.isPrimitive();
        boolean sPrimitive = s.isPrimitive();
        if (tPrimitive == sPrimitive)
            return isSubtypeUnchecked(t, s, warn);
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
        return isConvertible(t, s, Warner.noWarnings);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSubtype">
    /**
     * Is t an unchecked subtype of s?
     */
    public boolean isSubtypeUnchecked(Type t, Type s) {
        return isSubtypeUnchecked(t, s, Warner.noWarnings);
    }
    /**
     * Is t an unchecked subtype of s?
     */
    public boolean isSubtypeUnchecked(Type t, Type s, Warner warn) {
        if (t.tag == ARRAY && s.tag == ARRAY) {
            return (((ArrayType)t).elemtype.tag <= lastBaseTag)
                ? isSameType(elemtype(t), elemtype(s))
                : isSubtypeUnchecked(elemtype(t), elemtype(s), warn);
        } else if (isSubtype(t, s)) {
            return true;
        }
        else if (t.tag == TYPEVAR) {
            return isSubtypeUnchecked(t.getUpperBound(), s, warn);
        }
        else if (s.tag == UNDETVAR) {
            UndetVar uv = (UndetVar)s;
            if (uv.inst != null)
                return isSubtypeUnchecked(t, uv.inst, warn);
        }
        else if (!s.isRaw()) {
            Type t2 = asSuper(t, s.tsym);
            if (t2 != null && t2.isRaw()) {
                if (isReifiable(s))
                    warn.silentUnchecked();
                else
                    warn.warnUnchecked();
                return true;
            }
        }
        return false;
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

        if (s.tag >= firstPartialTag)
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
            public Boolean visitType(Type t, Type s) {
                switch (t.tag) {
                case BYTE: case CHAR:
                    return (t.tag == s.tag ||
                              t.tag + 2 <= s.tag && s.tag <= DOUBLE);
                case SHORT: case INT: case LONG: case FLOAT: case DOUBLE:
                    return t.tag <= s.tag && s.tag <= DOUBLE;
                case BOOLEAN: case VOID:
                    return t.tag == s.tag;
                case TYPEVAR:
                    return isSubtypeNoCapture(t.getUpperBound(), s);
                case BOT:
                    return
                        s.tag == BOT || s.tag == CLASS ||
                        s.tag == ARRAY || s.tag == TYPEVAR;
                case NONE:
                    return false;
                default:
                    throw new AssertionError("isSubtype " + t.tag);
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
                if (s.tag == ARRAY) {
                    if (t.elemtype.tag <= lastBaseTag)
                        return isSameType(t.elemtype, elemtype(s));
                    else
                        return isSubtypeNoCapture(t.elemtype, elemtype(s));
                }

                if (s.tag == CLASS) {
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
                if (t == s || t.qtype == s || s.tag == ERROR || s.tag == UNKNOWN)
                    return true;

                if (t.inst != null)
                    return isSubtypeNoCapture(t.inst, s); // TODO: ", warn"?

                t.hibounds = t.hibounds.prepend(s);
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
        switch (t.tag) {
        case ERROR:
            return true;
        case UNDETVAR: {
            UndetVar undet = (UndetVar)t;
            if (t == s ||
                undet.qtype == s ||
                s.tag == ERROR ||
                s.tag == BOT) return true;
            if (undet.inst != null)
                return isSubtype(s, undet.inst);
            undet.lobounds = undet.lobounds.prepend(s);
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
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSameType(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }

    /**
     * Is t the same type as s?
     */
    public boolean isSameType(Type t, Type s) {
        return isSameType.visit(t, s);
    }
    // where
        private TypeRelation isSameType = new TypeRelation() {

            public Boolean visitType(Type t, Type s) {
                if (t == s)
                    return true;

                if (s.tag >= firstPartialTag)
                    return visit(s, t);

                switch (t.tag) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE: case BOOLEAN: case VOID: case BOT: case NONE:
                    return t.tag == s.tag;
                case TYPEVAR: {
                    if (s.tag == TYPEVAR) {
                        //type-substitution does not preserve type-var types
                        //check that type var symbols and bounds are indeed the same
                        return t.tsym == s.tsym &&
                                visit(t.getUpperBound(), s.getUpperBound());
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
                    throw new AssertionError("isSameType " + t.tag);
                }
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (s.tag >= firstPartialTag)
                    return visit(s, t);
                else
                    return false;
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                if (t == s)
                    return true;

                if (s.tag >= firstPartialTag)
                    return visit(s, t);

                if (s.isSuperBound() && !s.isExtendsBound())
                    return visit(t, upperBound(s)) && visit(t, lowerBound(s));

                if (t.isCompound() && s.isCompound()) {
                    if (!visit(supertype(t), supertype(s)))
                        return false;

                    HashSet<SingletonType> set = new HashSet<SingletonType>();
                    for (Type x : interfaces(t))
                        set.add(new SingletonType(x));
                    for (Type x : interfaces(s)) {
                        if (!set.remove(new SingletonType(x)))
                            return false;
                    }
                    return (set.size() == 0);
                }
                return t.tsym == s.tsym
                    && visit(t.getEnclosingType(), s.getEnclosingType())
                    && containsTypeEquivalent(t.getTypeArguments(), s.getTypeArguments());
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                if (t == s)
                    return true;

                if (s.tag >= firstPartialTag)
                    return visit(s, t);

                return s.tag == ARRAY
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
                if (s.tag != FORALL)
                    return false;

                ForAll forAll = (ForAll)s;
                return hasSameBounds(t, forAll)
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                if (s.tag == WILDCARD)
                    // FIXME, this might be leftovers from before capture conversion
                    return false;

                if (t == s || t.qtype == s || s.tag == ERROR || s.tag == UNKNOWN)
                    return true;

                if (t.inst != null)
                    return visit(t.inst, s);

                t.inst = fromUnknownFun.apply(s);
                for (List<Type> l = t.lobounds; l.nonEmpty(); l = l.tail) {
                    if (!isSubtype(l.head, t.inst))
                        return false;
                }
                for (List<Type> l = t.hibounds; l.nonEmpty(); l = l.tail) {
                    if (!isSubtype(t.inst, l.head))
                        return false;
                }
                return true;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fromUnknownFun">
    /**
     * A mapping that turns all unknown types in this type to fresh
     * unknown variables.
     */
    public Mapping fromUnknownFun = new Mapping("fromUnknownFun") {
            public Type apply(Type t) {
                if (t.tag == UNKNOWN) return new UndetVar(t);
                else return t.map(this);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Contains Type">
    public boolean containedBy(Type t, Type s) {
        switch (t.tag) {
        case UNDETVAR:
            if (s.tag == WILDCARD) {
                UndetVar undetvar = (UndetVar)t;
                WildcardType wt = (WildcardType)s;
                switch(wt.kind) {
                    case UNBOUND: //similar to ? extends Object
                    case EXTENDS: {
                        Type bound = upperBound(s);
                        // We should check the new upper bound against any of the
                        // undetvar's lower bounds.
                        for (Type t2 : undetvar.lobounds) {
                            if (!isSubtype(t2, bound))
                                return false;
                        }
                        undetvar.hibounds = undetvar.hibounds.prepend(bound);
                        break;
                    }
                    case SUPER: {
                        Type bound = lowerBound(s);
                        // We should check the new lower bound against any of the
                        // undetvar's lower bounds.
                        for (Type t2 : undetvar.hibounds) {
                            if (!isSubtype(bound, t2))
                                return false;
                        }
                        undetvar.lobounds = undetvar.lobounds.prepend(bound);
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
                while (t.tag == WILDCARD) {
                    WildcardType w = (WildcardType)t;
                    if (w.isSuperBound())
                        return w.bound == null ? syms.objectType : w.bound.bound;
                    else
                        t = w.type;
                }
                return t;
            }

            private Type L(Type t) {
                while (t.tag == WILDCARD) {
                    WildcardType w = (WildcardType)t;
                    if (w.isExtendsBound())
                        return syms.botType;
                    else
                        t = w.type;
                }
                return t;
            }

            public Boolean visitType(Type t, Type s) {
                if (s.tag >= firstPartialTag)
                    return containedBy(s, t);
                else
                    return isSameType(t, s);
            }

            void debugContainsType(WildcardType t, Type s) {
                System.err.println();
                System.err.format(" does %s contain %s?%n", t, s);
                System.err.format(" %s U(%s) <: U(%s) %s = %s%n",
                                  upperBound(s), s, t, U(t),
                                  t.isSuperBound()
                                  || isSubtypeNoCapture(upperBound(s), U(t)));
                System.err.format(" %s L(%s) <: L(%s) %s = %s%n",
                                  L(t), t, s, lowerBound(s),
                                  t.isExtendsBound()
                                  || isSubtypeNoCapture(L(t), lowerBound(s)));
                System.err.println();
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (s.tag >= firstPartialTag)
                    return containedBy(s, t);
                else {
                    // debugContainsType(t, s);
                    return isSameWildcard(t, s)
                        || isCaptureOf(s, t)
                        || ((t.isExtendsBound() || isSubtypeNoCapture(L(t), lowerBound(s))) &&
                            (t.isSuperBound() || isSubtypeNoCapture(upperBound(s), U(t))));
                }
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                if (s.tag != WILDCARD)
                    return isSameType(t, s);
                else
                    return false;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };

    public boolean isCaptureOf(Type s, WildcardType t) {
        if (s.tag != TYPEVAR || !((TypeVar)s).isCaptured())
            return false;
        return isSameWildcard(t, ((CapturedType)s).wildcard);
    }

    public boolean isSameWildcard(WildcardType t, Type s) {
        if (s.tag != WILDCARD)
            return false;
        WildcardType w = (WildcardType)s;
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

    // <editor-fold defaultstate="collapsed" desc="isCastable">
    public boolean isCastable(Type t, Type s) {
        return isCastable(t, s, Warner.noWarnings);
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
            return allowBoxing && isConvertible(t, s, warn);

        if (warn != warnStack.head) {
            try {
                warnStack = warnStack.prepend(warn);
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
                if (s.tag == ERROR)
                    return true;

                switch (t.tag) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE:
                    return s.tag <= DOUBLE;
                case BOOLEAN:
                    return s.tag == BOOLEAN;
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
                if (s.tag == ERROR || s.tag == BOT)
                    return true;

                if (s.tag == TYPEVAR) {
                    if (isCastable(s.getUpperBound(), t, Warner.noWarnings)) {
                        warnStack.head.warnUnchecked();
                        return true;
                    } else {
                        return false;
                    }
                }

                if (t.isCompound()) {
                    Warner oldWarner = warnStack.head;
                    warnStack.head = Warner.noWarnings;
                    if (!visit(supertype(t), s))
                        return false;
                    for (Type intf : interfaces(t)) {
                        if (!visit(intf, s))
                            return false;
                    }
                    if (warnStack.head.unchecked == true)
                        oldWarner.warnUnchecked();
                    return true;
                }

                if (s.isCompound()) {
                    // call recursively to reuse the above code
                    return visitClassType((ClassType)s, t);
                }

                if (s.tag == CLASS || s.tag == ARRAY) {
                    boolean upcast;
                    if ((upcast = isSubtype(erasure(t), erasure(s)))
                        || isSubtype(erasure(s), erasure(t))) {
                        if (!upcast && s.tag == ARRAY) {
                            if (!isReifiable(s))
                                warnStack.head.warnUnchecked();
                            return true;
                        } else if (s.isRaw()) {
                            return true;
                        } else if (t.isRaw()) {
                            if (!isUnbounded(s))
                                warnStack.head.warnUnchecked();
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
                            assert a.tsym == highSub.tsym && a.tsym == lowSub.tsym
                                : a.tsym + " != " + highSub.tsym + " != " + lowSub.tsym;
                            if (!disjointTypes(aHigh.allparams(), highSub.allparams())
                                && !disjointTypes(aHigh.allparams(), lowSub.allparams())
                                && !disjointTypes(aLow.allparams(), highSub.allparams())
                                && !disjointTypes(aLow.allparams(), lowSub.allparams())) {
                                if (upcast ? giveWarning(a, b) :
                                    giveWarning(b, a))
                                    warnStack.head.warnUnchecked();
                                return true;
                            }
                        }
                        if (isReifiable(s))
                            return isSubtypeUnchecked(a, b);
                        else
                            return isSubtypeUnchecked(a, b, warnStack.head);
                    }

                    // Sidecast
                    if (s.tag == CLASS) {
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

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                switch (s.tag) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    if (isCastable(s, t, Warner.noWarnings)) {
                        warnStack.head.warnUnchecked();
                        return true;
                    } else {
                        return false;
                    }
                case CLASS:
                    return isSubtype(t, s);
                case ARRAY:
                    if (elemtype(t).tag <= lastBaseTag) {
                        return elemtype(t).tag == elemtype(s).tag;
                    } else {
                        return visit(elemtype(t), elemtype(s));
                    }
                default:
                    return false;
                }
            }

            @Override
            public Boolean visitTypeVar(TypeVar t, Type s) {
                switch (s.tag) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    if (isSubtype(t, s)) {
                        return true;
                    } else if (isCastable(t.bound, s, Warner.noWarnings)) {
                        warnStack.head.warnUnchecked();
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
     * The type C<X> is castable to C<Y> exactly if X and Y are not
     * disjoint.
     */
    public boolean disjointType(Type t, Type s) {
        return disjointType.visit(t, s);
    }
    // where
        private TypeRelation disjointType = new TypeRelation() {

            private Set<TypePair> cache = new HashSet<TypePair>();

            public Boolean visitType(Type t, Type s) {
                if (s.tag == WILDCARD)
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

                if (s.tag != WILDCARD) {
                    if (t.isExtendsBound())
                        return notSoftSubtypeRecursive(s, t.type);
                    else // isSuperBound()
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
        return map(t.getParameterTypes(), lowerBoundMapping);
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
     * where <T extends Number> but it is not true that Integer cannot
     * possibly be a subtype of T.
     */
    public boolean notSoftSubtype(Type t, Type s) {
        if (t == s) return false;
        if (t.tag == TYPEVAR) {
            TypeVar tv = (TypeVar) t;
            if (s.tag == TYPEVAR)
                s = s.getUpperBound();
            return !isCastable(tv.bound,
                               s,
                               Warner.noWarnings);
        }
        if (s.tag != WILDCARD)
            s = upperBound(s);
        if (s.tag == TYPEVAR)
            s = s.getUpperBound();

        return !isSubtype(t, s);
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
        while (t.tag == WILDCARD)
            t = upperBound(t);
        return t.tag == ARRAY;
    }

    /**
     * The element type of an array.
     */
    public Type elemtype(Type t) {
        switch (t.tag) {
        case WILDCARD:
            return elemtype(upperBound(t));
        case ARRAY:
            return ((ArrayType)t).elemtype;
        case FORALL:
            return elemtype(((ForAll)t).qtype);
        case ERROR:
            return t;
        default:
            return null;
        }
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
        while (t.tag == ARRAY) {
            result++;
            t = elemtype(t);
        }
        return result;
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
                if (st.tag == CLASS || st.tag == TYPEVAR || st.tag == ERROR) {
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
        switch (t.tag) {
        case CLASS:
            do {
                Type s = asSuper(t, sym);
                if (s != null) return s;
                t = t.getEnclosingType();
            } while (t.tag == CLASS);
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
        switch (t.tag) {
        case CLASS:
            do {
                Type s = asSuper(t, sym);
                if (s != null) return s;
                Type outer = t.getEnclosingType();
                t = (outer.tag == CLASS) ? outer :
                    (t.tsym.owner.enclClass() != null) ? t.tsym.owner.enclClass().type :
                    Type.noType;
            } while (t.tag == CLASS);
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
        return isAssignable(t, s, Warner.noWarnings);
    }

    /**
     * Is t assignable to s?<br>
     * Equivalent to subtype except for constant values and raw
     * types.<br>
     * (not defined for Method and ForAll types)
     */
    public boolean isAssignable(Type t, Type s, Warner warn) {
        if (t.tag == ERROR)
            return true;
        if (t.tag <= INT && t.constValue() != null) {
            int value = ((Number)t.constValue()).intValue();
            switch (s.tag) {
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
                switch (unboxedType(s).tag) {
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
        return erasure(t, false);
    }
    //where
    private Type erasure(Type t, boolean recurse) {
        if (t.tag <= lastBaseTag)
            return t; /* fast special case */
        else
            return erasure.visit(t, recurse);
        }
    // where
        private SimpleVisitor<Type, Boolean> erasure = new SimpleVisitor<Type, Boolean>() {
            public Type visitType(Type t, Boolean recurse) {
                if (t.tag <= lastBaseTag)
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
    public Type makeCompoundType(List<Type> bounds,
                                 Type supertype) {
        ClassSymbol bc =
            new ClassSymbol(ABSTRACT|PUBLIC|SYNTHETIC|COMPOUND|ACYCLIC,
                            Type.moreInfo
                                ? names.fromString(bounds.toString())
                                : names.empty,
                            syms.noSymbol);
        if (bounds.head.tag == TYPEVAR)
            // error condition, recover
                bc.erasure_field = syms.objectType;
            else
                bc.erasure_field = erasure(bounds.head);
            bc.members_field = new Scope(bc);
        ClassType bt = (ClassType)bc.type;
        bt.allparams_field = List.nil();
        if (supertype != null) {
            bt.supertype_field = supertype;
            bt.interfaces_field = bounds;
        } else {
            bt.supertype_field = bounds.head;
            bt.interfaces_field = bounds.tail;
        }
        assert bt.supertype_field.tsym.completer != null
            || !bt.supertype_field.isInterface()
            : bt.supertype_field;
        return bt;
    }

    /**
     * Same as {@link #makeCompoundType(List,Type)}, except that the
     * second parameter is computed directly. Note that this might
     * cause a symbol completion.  Hence, this version of
     * makeCompoundType may not be called during a classfile read.
     */
    public Type makeCompoundType(List<Type> bounds) {
        Type supertype = (bounds.head.tsym.flags() & INTERFACE) != 0 ?
            supertype(bounds.head) : null;
        return makeCompoundType(bounds, supertype);
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
                if (t.bound.tag == TYPEVAR ||
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
                        assert t != t.tsym.type : t.toString();
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
    public void setBounds(TypeVar t, List<Type> bounds, Type supertype) {
        if (bounds.tail.isEmpty())
            t.bound = bounds.head;
        else
            t.bound = makeCompoundType(bounds, supertype);
        t.rank_field = -1;
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
    public void setBounds(TypeVar t, List<Type> bounds) {
        Type supertype = (bounds.head.tsym.flags() & INTERFACE) != 0 ?
            syms.objectType : null;
        setBounds(t, bounds, supertype);
        t.rank_field = -1;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getBounds">
    /**
     * Return list of bounds of the given type variable.
     */
    public List<Type> getBounds(TypeVar t) {
        if (t.bound.isErroneous() || !t.bound.isCompound())
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
     * @see "The Java Language Specification, Third Ed. (8.4.2)."
     * @see #overrideEquivalent(Type t, Type s)
     * @param t first signature (possibly raw).
     * @param s second signature (could be subjected to erasure).
     * @return true if t is a sub signature of s.
     */
    public boolean isSubSignature(Type t, Type s) {
        return hasSameArgs(t, s) || hasSameArgs(t, erasure(s));
    }

    /**
     * Returns true iff these signatures are related by <em>override
     * equivalence</em>.  This is the natural extension of
     * isSubSignature to an equivalence relation.
     *
     * @see "The Java Language Specification, Third Ed. (8.4.2)."
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

    private WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, MethodSymbol>>> implCache_check =
            new WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, MethodSymbol>>>();

    private WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, MethodSymbol>>> implCache_nocheck =
            new WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, MethodSymbol>>>();

    public MethodSymbol implementation(MethodSymbol ms, TypeSymbol origin, Types types, boolean checkResult) {
        Map<MethodSymbol, SoftReference<Map<TypeSymbol, MethodSymbol>>> implCache = checkResult ?
            implCache_check : implCache_nocheck;
        SoftReference<Map<TypeSymbol, MethodSymbol>> ref_cache = implCache.get(ms);
        Map<TypeSymbol, MethodSymbol> cache = ref_cache != null ? ref_cache.get() : null;
        if (cache == null) {
            cache = new HashMap<TypeSymbol, MethodSymbol>();
            implCache.put(ms, new SoftReference<Map<TypeSymbol, MethodSymbol>>(cache));
        }
        MethodSymbol impl = cache.get(origin);
        if (impl == null) {
            for (Type t = origin.type; t.tag == CLASS || t.tag == TYPEVAR; t = types.supertype(t)) {
                while (t.tag == TYPEVAR)
                    t = t.getUpperBound();
                TypeSymbol c = t.tsym;
                for (Scope.Entry e = c.members().lookup(ms.name);
                     e.scope != null;
                     e = e.next()) {
                    if (e.sym.kind == Kinds.MTH) {
                        MethodSymbol m = (MethodSymbol) e.sym;
                        if (m.overrides(ms, origin, types, checkResult) &&
                            (m.flags() & SYNTHETIC) == 0) {
                            impl = m;
                            cache.put(origin, m);
                            return impl;
                        }
                    }
                }
            }
        }
        return impl;
    }

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
        return hasSameArgs.visit(t, s);
    }
    // where
        private TypeRelation hasSameArgs = new TypeRelation() {

            public Boolean visitType(Type t, Type s) {
                throw new AssertionError();
            }

            @Override
            public Boolean visitMethodType(MethodType t, Type s) {
                return s.tag == METHOD
                    && containsTypeEquivalent(t.argtypes, s.getParameterTypes());
            }

            @Override
            public Boolean visitForAll(ForAll t, Type s) {
                if (s.tag != FORALL)
                    return false;

                ForAll forAll = (ForAll)s;
                return hasSameBounds(t, forAll)
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return false;
            }
        };
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
    static private Mapping newInstanceFun = new Mapping("newInstanceFun") {
            public Type apply(Type t) { return new TypeVar(t.tsym, t.getUpperBound(), t.getLowerBound()); }
        };
    // </editor-fold>

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
        switch(t.tag) {
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
        if (t.tag == FORALL) {
            ForAll forAll = (ForAll)t;
            return typaramsString(forAll.tvars) + forAll.qtype;
        }
        return "" + t;
    }
    // where
        private String typaramsString(List<Type> tvars) {
            StringBuffer s = new StringBuffer();
            s.append('<');
            boolean first = true;
            for (Type t : tvars) {
                if (!first) s.append(", ");
                first = false;
                appendTyparamString(((TypeVar)t), s);
            }
            s.append('>');
            return s.toString();
        }
        private void appendTyparamString(TypeVar t, StringBuffer buf) {
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
                if (st.tag == CLASS) {
                    cl = insert(closure(st), t);
                } else if (st.tag == TYPEVAR) {
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
            cl1.head.tag == CLASS && cl2.head.tag == CLASS) {
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
                return 127 * Types.hashCode(t1) + Types.hashCode(t2);
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
            assert(act1.isEmpty() && act2.isEmpty() && typarams.isEmpty());
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
            switch (t.tag) {
            case CLASS:
                boundkind |= CLASS_BOUND;
                break;
            case ARRAY:
                boundkind |= ARRAY_BOUND;
                break;
            case  TYPEVAR:
                do {
                    t = t.getUpperBound();
                } while (t.tag == TYPEVAR);
                if (t.tag == ARRAY) {
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
            while (ts.head.tag != CLASS && ts.head.tag != TYPEVAR)
                ts = ts.tail;
            assert !ts.isEmpty();
            List<Type> cl = closure(ts.head);
            for (Type t : ts.tail) {
                if (t.tag == CLASS || t.tag == TYPEVAR)
                    cl = intersect(cl, closure(t));
            }
            return compoundMin(cl);

        default:
            // calculate lub(A, B[])
            List<Type> classes = List.of(arraySuperType());
            for (Type t : ts) {
                if (t.tag != ARRAY) // Filter out any arrays
                    classes = classes.prepend(t);
            }
            // lub(A, B[]) is lub(A, arraySuperType)
            return lub(classes);
        }
    }
    // where
        private Type arraySuperType = null;
        private Type arraySuperType() {
            // initialized lazily to avoid problems during compiler startup
            if (arraySuperType == null) {
                synchronized (this) {
                    if (arraySuperType == null) {
                        // JLS 10.8: all arrays implement Cloneable and Serializable.
                        arraySuperType = makeCompoundType(List.of(syms.serializableType,
                                                                  syms.cloneableType),
                                                          syms.objectType);
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
    public static int hashCode(Type t) {
        return hashCode.visit(t);
    }
    // where
        private static final UnaryVisitor<Integer> hashCode = new UnaryVisitor<Integer>() {

            public Integer visitType(Type t, Void ignored) {
                return t.tag;
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
     * @see <a href="http://java.sun.com/docs/books/jls/">The Java
     * Language Specification, Third Ed. (8.4.5)</a>
     */
    public boolean returnTypeSubstitutable(Type r1, Type r2) {
        if (hasSameArgs(r1, r2))
            return resultSubtype(r1, r2, Warner.noWarnings);
        else
            return covariantReturnType(r1.getReturnType(),
                                       erasure(r2.getReturnType()),
                                       Warner.noWarnings);
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
        if (!source.allowCovariantReturns())
            return false;
        if (isSubtypeUnchecked(r1.getReturnType(), r2res, warner))
            return true;
        if (!isSubtype(r1.getReturnType(), erasure(r2res)))
            return false;
        warner.warnUnchecked();
        return true;
    }

    /**
     * Is t an appropriate return type in an overrider for a
     * method that returns s?
     */
    public boolean covariantReturnType(Type t, Type s, Warner warner) {
        return
            isSameType(t, s) ||
            source.allowCovariantReturns() &&
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
        return reader.enterClass(syms.boxedName[t.tag]);
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
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Capture conversion">
    /*
     * JLS 3rd Ed. 5.1.10 Capture Conversion:
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
     * Capture conversion as specified by JLS 3rd Ed.
     */

    public List<Type> capture(List<Type> ts) {
        List<Type> buf = List.nil();
        for (Type t : ts) {
            buf = buf.prepend(capture(t));
        }
        return buf.reverse();
    }
    public Type capture(Type t) {
        if (t.tag != CLASS)
            return t;
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
                WildcardType Ti = (WildcardType)currentT.head;
                Type Ui = currentA.head.getUpperBound();
                CapturedType Si = (CapturedType)currentS.head;
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
                if (t.tag == WILDCARD) {
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
            assert (from.tsym.flags() & INTERFACE) != 0;
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
            warn.warnUnchecked();
        if (!source.allowCovariantReturns())
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
            assert (from.tsym.flags() & INTERFACE) != 0;
            reverse = true;
            to = from;
            from = target;
        }
        assert (from.tsym.flags() & FINAL) != 0;
        Type t1 = asSuper(from, to.tsym);
        if (t1 == null) return false;
        Type t2 = to;
        if (disjointTypes(t1.getTypeArguments(), t2.getTypeArguments()))
            return false;
        if (!source.allowCovariantReturns())
            // reject if there is a common method signature with
            // incompatible return types.
            chk.checkCompatibleAbstracts(warn.pos(), from, to);
        if (!isReifiable(target) &&
            (reverse ? giveWarning(t2, t1) : giveWarning(t1, t2)))
            warn.warnUnchecked();
        return true;
    }

    private boolean giveWarning(Type from, Type to) {
        Type subFrom = asSub(from, to.tsym);
        return to.isParameterized() &&
                (!(isUnbounded(to) ||
                isSubtype(from, to) ||
                ((subFrom != null) && isSameType(subFrom, to))));
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
            if (target.tag == CLASS)
                adaptRecursive(source.allparams(), target.allparams());
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType source, Type target) throws AdaptFailure {
            if (target.tag == ARRAY)
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
        return new Rewriter(high, rewriteTypeVars).rewrite(t);
    }

    class Rewriter extends UnaryVisitor<Type> {

        boolean high;
        boolean rewriteTypeVars;

        Rewriter(boolean high, boolean rewriteTypeVars) {
            this.high = high;
            this.rewriteTypeVars = rewriteTypeVars;
        }

        Type rewrite(Type t) {
            ListBuffer<Type> from = new ListBuffer<Type>();
            ListBuffer<Type> to = new ListBuffer<Type>();
            adaptSelf(t, from, to);
            ListBuffer<Type> rewritten = new ListBuffer<Type>();
            List<Type> formals = from.toList();
            boolean changed = false;
            for (Type arg : to.toList()) {
                Type bound = visit(arg);
                if (arg != bound) {
                    changed = true;
                    bound = high ? makeExtendsWildcard(bound, (TypeVar)formals.head)
                              : makeSuperWildcard(bound, (TypeVar)formals.head);
                }
                rewritten.append(bound);
                formals = formals.tail;
            }
            if (changed)
                return subst(t.tsym.type, from.toList(), rewritten.toList());
            else
                return t;
        }

        public Type visitType(Type t, Void s) {
            return high ? upperBound(t) : lowerBound(t);
        }

        @Override
        public Type visitCapturedType(CapturedType t, Void s) {
            return visitWildcardType(t.wildcard, null);
        }

        @Override
        public Type visitTypeVar(TypeVar t, Void s) {
            if (rewriteTypeVars)
                return high ? t.bound : syms.botType;
            else
                return t;
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void s) {
            Type bound = high ? t.getExtendsBound() :
                                t.getSuperBound();
            if (bound == null)
                bound = high ? syms.objectType : syms.botType;
            return bound;
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
        if (bound.tag == BOT) {
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
    class SingletonType {
        final Type t;
        SingletonType(Type t) {
            this.t = t;
        }
        public int hashCode() {
            return Types.hashCode(t);
        }
        public boolean equals(Object obj) {
            return (obj instanceof SingletonType) &&
                isSameType(t, ((SingletonType)obj).t);
        }
        public String toString() {
            return t.toString();
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
}
