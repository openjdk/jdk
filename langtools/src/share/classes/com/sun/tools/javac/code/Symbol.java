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

package com.sun.tools.javac.code;

import java.util.Set;
import java.util.concurrent.Callable;

import javax.lang.model.element.*;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.model.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Name;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.code.TypeTag.FORALL;
import static com.sun.tools.javac.code.TypeTag.TYPEVAR;

/** Root class for Java symbols. It contains subclasses
 *  for specific sorts of symbols, such as variables, methods and operators,
 *  types, packages. Each subclass is represented as a static inner class
 *  inside Symbol.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Symbol implements Element {
    // public Throwable debug = new Throwable();

    /** The kind of this symbol.
     *  @see Kinds
     */
    public int kind;

    /** The flags of this symbol.
     */
    public long flags_field;

    /** An accessor method for the flags of this symbol.
     *  Flags of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public long flags() { return flags_field; }

    /** The name of this symbol in Utf8 representation.
     */
    public Name name;

    /** The type of this symbol.
     */
    public Type type;

    /** The owner of this symbol.
     */
    public Symbol owner;

    /** The completer of this symbol.
     */
    public Completer completer;

    /** A cache for the type erasure of this symbol.
     */
    public Type erasure_field;

    // <editor-fold defaultstate="collapsed" desc="annotations">

    /** The attributes of this symbol are contained in this
     * Annotations. The Annotations instance is NOT immutable.
     */
    protected Annotations annotations;

    /** An accessor method for the attributes of this symbol.
     *  Attributes of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public List<Attribute.Compound> getRawAttributes() {
        return (annotations == null)
                ? List.<Attribute.Compound>nil()
                : annotations.getDeclarationAttributes();
    }

    /** An accessor method for the type attributes of this symbol.
     *  Attributes of class symbols should be accessed through the accessor
     *  method to make sure that the class symbol is loaded.
     */
    public List<Attribute.TypeCompound> getRawTypeAttributes() {
        return (annotations == null)
                ? List.<Attribute.TypeCompound>nil()
                : annotations.getTypeAttributes();
    }

    /** Fetch a particular annotation from a symbol. */
    public Attribute.Compound attribute(Symbol anno) {
        for (Attribute.Compound a : getRawAttributes()) {
            if (a.type.tsym == anno) return a;
        }
        return null;
    }

    public boolean annotationsPendingCompletion() {
        return annotations == null ? false : annotations.pendingCompletion();
    }

    public void appendAttributes(List<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedAnnos().append(l);
        }
    }

    public void appendClassInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedAnnos().appendClassInitTypeAttributes(l);
        }
    }

    public void appendInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedAnnos().appendInitTypeAttributes(l);
        }
    }

    public void appendTypeAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.TypeCompound> ctx) {
        initedAnnos().appendTypeAttributesWithCompletion(ctx);
    }

    public void appendUniqueTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedAnnos().appendUniqueTypes(l);
        }
    }

    public List<Attribute.TypeCompound> getClassInitTypeAttributes() {
        return (annotations == null)
                ? List.<Attribute.TypeCompound>nil()
                : annotations.getClassInitTypeAttributes();
    }

    public List<Attribute.TypeCompound> getInitTypeAttributes() {
        return (annotations == null)
                ? List.<Attribute.TypeCompound>nil()
                : annotations.getInitTypeAttributes();
    }

    public List<Attribute.Compound> getDeclarationAttributes() {
        return (annotations == null)
                ? List.<Attribute.Compound>nil()
                : annotations.getDeclarationAttributes();
    }

    public boolean hasAnnotations() {
        return (annotations != null && !annotations.isEmpty());
    }

    public boolean hasTypeAnnotations() {
        return (annotations != null && !annotations.isTypesEmpty());
    }

    public void prependAttributes(List<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedAnnos().prepend(l);
        }
    }

    public void resetAnnotations() {
        initedAnnos().reset();
    }

    public void setAttributes(Symbol other) {
        if (annotations != null || other.annotations != null) {
            initedAnnos().setAttributes(other.annotations);
        }
    }

    public void setDeclarationAttributes(List<Attribute.Compound> a) {
        if (annotations != null || a.nonEmpty()) {
            initedAnnos().setDeclarationAttributes(a);
        }
    }

    public void setDeclarationAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.Compound> ctx) {
        initedAnnos().setDeclarationAttributesWithCompletion(ctx);
    }

    public void setTypeAttributes(List<Attribute.TypeCompound> a) {
        if (annotations != null || a.nonEmpty()) {
            if (annotations == null)
                annotations = new Annotations(this);
            annotations.setTypeAttributes(a);
        }
    }

    private Annotations initedAnnos() {
        if (annotations == null)
            annotations = new Annotations(this);
        return annotations;
    }

    /** This method is intended for debugging only. */
    public Annotations getAnnotations() {
        return annotations;
    }

    // </editor-fold>

    /** Construct a symbol with given kind, flags, name, type and owner.
     */
    public Symbol(int kind, long flags, Name name, Type type, Symbol owner) {
        this.kind = kind;
        this.flags_field = flags;
        this.type = type;
        this.owner = owner;
        this.completer = null;
        this.erasure_field = null;
        this.name = name;
    }

    /** Clone this symbol with new owner.
     *  Legal only for fields and methods.
     */
    public Symbol clone(Symbol newOwner) {
        throw new AssertionError();
    }

    public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
        return v.visitSymbol(this, p);
    }

    /** The Java source which this symbol represents.
     *  A description of this symbol; overrides Object.
     */
    public String toString() {
        return name.toString();
    }

    /** A Java source description of the location of this symbol; used for
     *  error reporting.
     *
     * @return null if the symbol is a package or a toplevel class defined in
     * the default package; otherwise, the owner symbol is returned
     */
    public Symbol location() {
        if (owner.name == null || (owner.name.isEmpty() &&
                (owner.flags() & BLOCK) == 0 && owner.kind != PCK && owner.kind != TYP)) {
            return null;
        }
        return owner;
    }

    public Symbol location(Type site, Types types) {
        if (owner.name == null || owner.name.isEmpty()) {
            return location();
        }
        if (owner.type.hasTag(CLASS)) {
            Type ownertype = types.asOuterSuper(site, owner);
            if (ownertype != null) return ownertype.tsym;
        }
        return owner;
    }

    public Symbol baseSymbol() {
        return this;
    }

    /** The symbol's erased type.
     */
    public Type erasure(Types types) {
        if (erasure_field == null)
            erasure_field = types.erasure(type);
        return erasure_field;
    }

    /** The external type of a symbol. This is the symbol's erased type
     *  except for constructors of inner classes which get the enclosing
     *  instance class added as first argument.
     */
    public Type externalType(Types types) {
        Type t = erasure(types);
        if (name == name.table.names.init && owner.hasOuterInstance()) {
            Type outerThisType = types.erasure(owner.type.getEnclosingType());
            return new MethodType(t.getParameterTypes().prepend(outerThisType),
                                  t.getReturnType(),
                                  t.getThrownTypes(),
                                  t.tsym);
        } else {
            return t;
        }
    }

    public boolean isDeprecated() {
        return (flags_field & DEPRECATED) != 0;
    }

    public boolean isStatic() {
        return
            (flags() & STATIC) != 0 ||
            (owner.flags() & INTERFACE) != 0 && kind != MTH;
    }

    public boolean isInterface() {
        return (flags() & INTERFACE) != 0;
    }

    public boolean isPrivate() {
        return (flags_field & Flags.AccessFlags) == PRIVATE;
    }

    public boolean isEnum() {
        return (flags() & ENUM) != 0;
    }

    /** Is this symbol declared (directly or indirectly) local
     *  to a method or variable initializer?
     *  Also includes fields of inner classes which are in
     *  turn local to a method or variable initializer.
     */
    public boolean isLocal() {
        return
            (owner.kind & (VAR | MTH)) != 0 ||
            (owner.kind == TYP && owner.isLocal());
    }

    /** Has this symbol an empty name? This includes anonymous
     *  inner classes.
     */
    public boolean isAnonymous() {
        return name.isEmpty();
    }

    /** Is this symbol a constructor?
     */
    public boolean isConstructor() {
        return name == name.table.names.init;
    }

    /** The fully qualified name of this symbol.
     *  This is the same as the symbol's name except for class symbols,
     *  which are handled separately.
     */
    public Name getQualifiedName() {
        return name;
    }

    /** The fully qualified name of this symbol after converting to flat
     *  representation. This is the same as the symbol's name except for
     *  class symbols, which are handled separately.
     */
    public Name flatName() {
        return getQualifiedName();
    }

    /** If this is a class or package, its members, otherwise null.
     */
    public Scope members() {
        return null;
    }

    /** A class is an inner class if it it has an enclosing instance class.
     */
    public boolean isInner() {
        return type.getEnclosingType().hasTag(CLASS);
    }

    /** An inner class has an outer instance if it is not an interface
     *  it has an enclosing instance class which might be referenced from the class.
     *  Nested classes can see instance members of their enclosing class.
     *  Their constructors carry an additional this$n parameter, inserted
     *  implicitly by the compiler.
     *
     *  @see #isInner
     */
    public boolean hasOuterInstance() {
        return
            type.getEnclosingType().hasTag(CLASS) && (flags() & (INTERFACE | NOOUTERTHIS)) == 0;
    }

    /** The closest enclosing class of this symbol's declaration.
     */
    public ClassSymbol enclClass() {
        Symbol c = this;
        while (c != null &&
               ((c.kind & TYP) == 0 || !c.type.hasTag(CLASS))) {
            c = c.owner;
        }
        return (ClassSymbol)c;
    }

    /** The outermost class which indirectly owns this symbol.
     */
    public ClassSymbol outermostClass() {
        Symbol sym = this;
        Symbol prev = null;
        while (sym.kind != PCK) {
            prev = sym;
            sym = sym.owner;
        }
        return (ClassSymbol) prev;
    }

    /** The package which indirectly owns this symbol.
     */
    public PackageSymbol packge() {
        Symbol sym = this;
        while (sym.kind != PCK) {
            sym = sym.owner;
        }
        return (PackageSymbol) sym;
    }

    /** Is this symbol a subclass of `base'? Only defined for ClassSymbols.
     */
    public boolean isSubClass(Symbol base, Types types) {
        throw new AssertionError("isSubClass " + this);
    }

    /** Fully check membership: hierarchy, protection, and hiding.
     *  Does not exclude methods not inherited due to overriding.
     */
    public boolean isMemberOf(TypeSymbol clazz, Types types) {
        return
            owner == clazz ||
            clazz.isSubClass(owner, types) &&
            isInheritedIn(clazz, types) &&
            !hiddenIn((ClassSymbol)clazz, types);
    }

    /** Is this symbol the same as or enclosed by the given class? */
    public boolean isEnclosedBy(ClassSymbol clazz) {
        for (Symbol sym = this; sym.kind != PCK; sym = sym.owner)
            if (sym == clazz) return true;
        return false;
    }

    /** Check for hiding.  Note that this doesn't handle multiple
     *  (interface) inheritance. */
    private boolean hiddenIn(ClassSymbol clazz, Types types) {
        if (kind == MTH && (flags() & STATIC) == 0) return false;
        while (true) {
            if (owner == clazz) return false;
            Scope.Entry e = clazz.members().lookup(name);
            while (e.scope != null) {
                if (e.sym == this) return false;
                if (e.sym.kind == kind &&
                    (kind != MTH ||
                     (e.sym.flags() & STATIC) != 0 &&
                     types.isSubSignature(e.sym.type, type)))
                    return true;
                e = e.next();
            }
            Type superType = types.supertype(clazz.type);
            if (!superType.hasTag(CLASS)) return false;
            clazz = (ClassSymbol)superType.tsym;
        }
    }

    /** Is this symbol inherited into a given class?
     *  PRE: If symbol's owner is a interface,
     *       it is already assumed that the interface is a superinterface
     *       of given class.
     *  @param clazz  The class for which we want to establish membership.
     *                This must be a subclass of the member's owner.
     */
    public boolean isInheritedIn(Symbol clazz, Types types) {
        switch ((int)(flags_field & Flags.AccessFlags)) {
        default: // error recovery
        case PUBLIC:
            return true;
        case PRIVATE:
            return this.owner == clazz;
        case PROTECTED:
            // we model interfaces as extending Object
            return (clazz.flags() & INTERFACE) == 0;
        case 0:
            PackageSymbol thisPackage = this.packge();
            for (Symbol sup = clazz;
                 sup != null && sup != this.owner;
                 sup = types.supertype(sup.type).tsym) {
                while (sup.type.hasTag(TYPEVAR))
                    sup = sup.type.getUpperBound().tsym;
                if (sup.type.isErroneous())
                    return true; // error recovery
                if ((sup.flags() & COMPOUND) != 0)
                    continue;
                if (sup.packge() != thisPackage)
                    return false;
            }
            return (clazz.flags() & INTERFACE) == 0;
        }
    }

    /** The (variable or method) symbol seen as a member of given
     *  class type`site' (this might change the symbol's type).
     *  This is used exclusively for producing diagnostics.
     */
    public Symbol asMemberOf(Type site, Types types) {
        throw new AssertionError();
    }

    /** Does this method symbol override `other' symbol, when both are seen as
     *  members of class `origin'?  It is assumed that _other is a member
     *  of origin.
     *
     *  It is assumed that both symbols have the same name.  The static
     *  modifier is ignored for this test.
     *
     *  See JLS 8.4.6.1 (without transitivity) and 8.4.6.4
     */
    public boolean overrides(Symbol _other, TypeSymbol origin, Types types, boolean checkResult) {
        return false;
    }

    /** Complete the elaboration of this symbol's definition.
     */
    public void complete() throws CompletionFailure {
        if (completer != null) {
            Completer c = completer;
            completer = null;
            c.complete(this);
        }
    }

    /** True if the symbol represents an entity that exists.
     */
    public boolean exists() {
        return true;
    }

    public Type asType() {
        return type;
    }

    public Symbol getEnclosingElement() {
        return owner;
    }

    public ElementKind getKind() {
        return ElementKind.OTHER;       // most unkind
    }

    public Set<Modifier> getModifiers() {
        return Flags.asModifierSet(flags());
    }

    public Name getSimpleName() {
        return name;
    }

    /**
     * This is the implementation for {@code
     * javax.lang.model.element.Element.getAnnotationMirrors()}.
     */
    @Override
    public List<Attribute.Compound> getAnnotationMirrors() {
        return getRawAttributes();
    }

    /**
     * @deprecated this method should never be used by javac internally.
     */
    @Deprecated
    public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annoType) {
        return JavacAnnoConstructs.getAnnotation(this, annoType);
    }

    // This method is part of the javax.lang.model API, do not use this in javac code.
    public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annoType) {
        return JavacAnnoConstructs.getAnnotations(this, annoType);
    }

    // TODO: getEnclosedElements should return a javac List, fix in FilteredMemberList
    public java.util.List<Symbol> getEnclosedElements() {
        return List.nil();
    }

    public List<TypeVariableSymbol> getTypeParameters() {
        ListBuffer<TypeVariableSymbol> l = ListBuffer.lb();
        for (Type t : type.getTypeArguments()) {
            Assert.check(t.tsym.getKind() == ElementKind.TYPE_PARAMETER);
            l.append((TypeVariableSymbol)t.tsym);
        }
        return l.toList();
    }

    public static class DelegatedSymbol<T extends Symbol> extends Symbol {
        protected T other;
        public DelegatedSymbol(T other) {
            super(other.kind, other.flags_field, other.name, other.type, other.owner);
            this.other = other;
        }
        public String toString() { return other.toString(); }
        public Symbol location() { return other.location(); }
        public Symbol location(Type site, Types types) { return other.location(site, types); }
        public Symbol baseSymbol() { return other; }
        public Type erasure(Types types) { return other.erasure(types); }
        public Type externalType(Types types) { return other.externalType(types); }
        public boolean isLocal() { return other.isLocal(); }
        public boolean isConstructor() { return other.isConstructor(); }
        public Name getQualifiedName() { return other.getQualifiedName(); }
        public Name flatName() { return other.flatName(); }
        public Scope members() { return other.members(); }
        public boolean isInner() { return other.isInner(); }
        public boolean hasOuterInstance() { return other.hasOuterInstance(); }
        public ClassSymbol enclClass() { return other.enclClass(); }
        public ClassSymbol outermostClass() { return other.outermostClass(); }
        public PackageSymbol packge() { return other.packge(); }
        public boolean isSubClass(Symbol base, Types types) { return other.isSubClass(base, types); }
        public boolean isMemberOf(TypeSymbol clazz, Types types) { return other.isMemberOf(clazz, types); }
        public boolean isEnclosedBy(ClassSymbol clazz) { return other.isEnclosedBy(clazz); }
        public boolean isInheritedIn(Symbol clazz, Types types) { return other.isInheritedIn(clazz, types); }
        public Symbol asMemberOf(Type site, Types types) { return other.asMemberOf(site, types); }
        public void complete() throws CompletionFailure { other.complete(); }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return other.accept(v, p);
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitSymbol(other, p);
        }

        public T getUnderlyingSymbol() {
            return other;
        }
    }

    /** A base class for Symbols representing types.
     */
    public static abstract class TypeSymbol extends Symbol {
        public TypeSymbol(int kind, long flags, Name name, Type type, Symbol owner) {
            super(kind, flags, name, type, owner);
        }
        /** form a fully qualified name from a name and an owner
         */
        static public Name formFullName(Name name, Symbol owner) {
            if (owner == null) return name;
            if (((owner.kind != ERR)) &&
                ((owner.kind & (VAR | MTH)) != 0
                 || (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
                 )) return name;
            Name prefix = owner.getQualifiedName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append('.', name);
        }

        /** form a fully qualified name from a name and an owner, after
         *  converting to flat representation
         */
        static public Name formFlatName(Name name, Symbol owner) {
            if (owner == null ||
                (owner.kind & (VAR | MTH)) != 0
                || (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
                ) return name;
            char sep = owner.kind == TYP ? '$' : '.';
            Name prefix = owner.flatName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append(sep, name);
        }

        /**
         * A total ordering between type symbols that refines the
         * class inheritance graph.
         *
         * Typevariables always precede other kinds of symbols.
         */
        public final boolean precedes(TypeSymbol that, Types types) {
            if (this == that)
                return false;
            if (type.hasTag(that.type.getTag())) {
                if (type.hasTag(CLASS)) {
                    return
                        types.rank(that.type) < types.rank(this.type) ||
                        types.rank(that.type) == types.rank(this.type) &&
                        that.getQualifiedName().compareTo(this.getQualifiedName()) < 0;
                } else if (type.hasTag(TYPEVAR)) {
                    return types.isSubtype(this.type, that.type);
                }
            }
            return type.hasTag(TYPEVAR);
        }

        @Override
        public java.util.List<Symbol> getEnclosedElements() {
            List<Symbol> list = List.nil();
            if (kind == TYP && type.hasTag(TYPEVAR)) {
                return list;
            }
            for (Scope.Entry e = members().elems; e != null; e = e.sibling) {
                if (e.sym != null && (e.sym.flags() & SYNTHETIC) == 0 && e.sym.owner == this)
                    list = list.prepend(e.sym);
            }
            return list;
        }

        @Override
        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitTypeSymbol(this, p);
        }
    }

    /**
     * Type variables are represented by instances of this class.
     */
    public static class TypeVariableSymbol
            extends TypeSymbol implements TypeParameterElement {

        public TypeVariableSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
        }

        public ElementKind getKind() {
            return ElementKind.TYPE_PARAMETER;
        }

        @Override
        public Symbol getGenericElement() {
            return owner;
        }

        public List<Type> getBounds() {
            TypeVar t = (TypeVar)type;
            Type bound = t.getUpperBound();
            if (!bound.isCompound())
                return List.of(bound);
            ClassType ct = (ClassType)bound;
            if (!ct.tsym.erasure_field.isInterface()) {
                return ct.interfaces_field.prepend(ct.supertype_field);
            } else {
                // No superclass was given in bounds.
                // In this case, supertype is Object, erasure is first interface.
                return ct.interfaces_field;
            }
        }

        @Override
        public List<Attribute.Compound> getAnnotationMirrors() {
            return onlyTypeVariableAnnotations(owner.getRawTypeAttributes());
        }

        private List<Attribute.Compound> onlyTypeVariableAnnotations(
                List<Attribute.TypeCompound> candidates) {
            // Declaration annotations on TypeParameters are stored in type attributes
            List<Attribute.Compound> res = List.nil();
            for (Attribute.TypeCompound a : candidates) {
                if (a.position.type == TargetType.CLASS_TYPE_PARAMETER ||
                    a.position.type == TargetType.METHOD_TYPE_PARAMETER)
                    res = res.prepend(a);
            }

            return res = res.reverse();
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitTypeParameter(this, p);
        }
    }

    /** A class for package symbols
     */
    public static class PackageSymbol extends TypeSymbol
        implements PackageElement {

        public Scope members_field;
        public Name fullname;
        public ClassSymbol package_info; // see bug 6443073

        public PackageSymbol(Name name, Type type, Symbol owner) {
            super(PCK, 0, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
        }

        public PackageSymbol(Name name, Symbol owner) {
            this(name, null, owner);
            this.type = new PackageType(this);
        }

        public String toString() {
            return fullname.toString();
        }

        public Name getQualifiedName() {
            return fullname;
        }

        public boolean isUnnamed() {
            return name.isEmpty() && owner != null;
        }

        public Scope members() {
            if (completer != null) complete();
            return members_field;
        }

        public long flags() {
            if (completer != null) complete();
            return flags_field;
        }

        @Override
        public List<Attribute.Compound> getRawAttributes() {
            if (completer != null) complete();
            if (package_info != null && package_info.completer != null) {
                package_info.complete();
                mergeAttributes();
            }
            return super.getRawAttributes();
        }

        private void mergeAttributes() {
            if (annotations == null &&
                package_info.annotations != null) {
                annotations = new Annotations(this);
                annotations.setAttributes(package_info.annotations);
            }
        }

        /** A package "exists" if a type or package that exists has
         *  been seen within it.
         */
        public boolean exists() {
            return (flags_field & EXISTS) != 0;
        }

        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        public Symbol getEnclosingElement() {
            return null;
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitPackage(this, p);
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitPackageSymbol(this, p);
        }
    }

    /** A class for class symbols
     */
    public static class ClassSymbol extends TypeSymbol implements TypeElement {

        /** a scope for all class members; variables, methods and inner classes
         *  type parameters are not part of this scope
         */
        public Scope members_field;

        /** the fully qualified name of the class, i.e. pck.outer.inner.
         *  null for anonymous classes
         */
        public Name fullname;

        /** the fully qualified name of the class after converting to flat
         *  representation, i.e. pck.outer$inner,
         *  set externally for local and anonymous classes
         */
        public Name flatname;

        /** the sourcefile where the class came from
         */
        public JavaFileObject sourcefile;

        /** the classfile from where to load this class
         *  this will have extension .class or .java
         */
        public JavaFileObject classfile;

        /** the list of translated local classes (used for generating
         * InnerClasses attribute)
         */
        public List<ClassSymbol> trans_local;

        /** the constant pool of the class
         */
        public Pool pool;

        public ClassSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
            this.flatname = formFlatName(name, owner);
            this.sourcefile = null;
            this.classfile = null;
            this.pool = null;
        }

        public ClassSymbol(long flags, Name name, Symbol owner) {
            this(
                flags,
                name,
                new ClassType(Type.noType, null, null),
                owner);
            this.type.tsym = this;
        }

        /** The Java source which this symbol represents.
         */
        public String toString() {
            return className();
        }

        public long flags() {
            if (completer != null) complete();
            return flags_field;
        }

        public Scope members() {
            if (completer != null) complete();
            return members_field;
        }

        @Override
        public List<Attribute.Compound> getRawAttributes() {
            if (completer != null) complete();
            return super.getRawAttributes();
        }

        @Override
        public List<Attribute.TypeCompound> getRawTypeAttributes() {
            if (completer != null) complete();
            return super.getRawTypeAttributes();
        }

        public Type erasure(Types types) {
            if (erasure_field == null)
                erasure_field = new ClassType(types.erasure(type.getEnclosingType()),
                                              List.<Type>nil(), this);
            return erasure_field;
        }

        public String className() {
            if (name.isEmpty())
                return
                    Log.getLocalizedString("anonymous.class", flatname);
            else
                return fullname.toString();
        }

        public Name getQualifiedName() {
            return fullname;
        }

        public Name flatName() {
            return flatname;
        }

        public boolean isSubClass(Symbol base, Types types) {
            if (this == base) {
                return true;
            } else if ((base.flags() & INTERFACE) != 0) {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    for (List<Type> is = types.interfaces(t);
                         is.nonEmpty();
                         is = is.tail)
                        if (is.head.tsym.isSubClass(base, types)) return true;
            } else {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    if (t.tsym == base) return true;
            }
            return false;
        }

        /** Complete the elaboration of this symbol's definition.
         */
        public void complete() throws CompletionFailure {
            try {
                super.complete();
            } catch (CompletionFailure ex) {
                // quiet error recovery
                flags_field |= (PUBLIC|STATIC);
                this.type = new ErrorType(this, Type.noType);
                throw ex;
            }
        }

        public List<Type> getInterfaces() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType)type;
                if (t.interfaces_field == null) // FIXME: shouldn't be null
                    t.interfaces_field = List.nil();
                if (t.all_interfaces_field != null)
                    return Type.getModelTypes(t.all_interfaces_field);
                return t.interfaces_field;
            } else {
                return List.nil();
            }
        }

        public Type getSuperclass() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType)type;
                if (t.supertype_field == null) // FIXME: shouldn't be null
                    t.supertype_field = Type.noType;
                // An interface has no superclass; its supertype is Object.
                return t.isInterface()
                    ? Type.noType
                    : t.supertype_field.getModelType();
            } else {
                return Type.noType;
            }
        }

        public ElementKind getKind() {
            long flags = flags();
            if ((flags & ANNOTATION) != 0)
                return ElementKind.ANNOTATION_TYPE;
            else if ((flags & INTERFACE) != 0)
                return ElementKind.INTERFACE;
            else if ((flags & ENUM) != 0)
                return ElementKind.ENUM;
            else
                return ElementKind.CLASS;
        }

        public NestingKind getNestingKind() {
            complete();
            if (owner.kind == PCK)
                return NestingKind.TOP_LEVEL;
            else if (name.isEmpty())
                return NestingKind.ANONYMOUS;
            else if (owner.kind == MTH)
                return NestingKind.LOCAL;
            else
                return NestingKind.MEMBER;
        }

        /**
         * Since this method works in terms of the runtime representation
         * of annotations, it should never be used by javac internally.
         */
        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annoType) {
            return JavacAnnoConstructs.getAnnotation(this, annoType);
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitType(this, p);
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitClassSymbol(this, p);
        }
    }


    /** A class for variable symbols
     */
    public static class VarSymbol extends Symbol implements VariableElement {

        /** The variable's declaration position.
         */
        public int pos = Position.NOPOS;

        /** The variable's address. Used for different purposes during
         *  flow analysis, translation and code generation.
         *  Flow analysis:
         *    If this is a blank final or local variable, its sequence number.
         *  Translation:
         *    If this is a private field, its access number.
         *  Code generation:
         *    If this is a local variable, its logical slot number.
         */
        public int adr = -1;

        /** Construct a variable symbol, given its flags, name, type and owner.
         */
        public VarSymbol(long flags, Name name, Type type, Symbol owner) {
            super(VAR, flags, name, type, owner);
        }

        /** Clone this symbol with new owner.
         */
        public VarSymbol clone(Symbol newOwner) {
            VarSymbol v = new VarSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return VarSymbol.this;
                }
            };
            v.pos = pos;
            v.adr = adr;
            v.data = data;
//          System.out.println("clone " + v + " in " + newOwner);//DEBUG
            return v;
        }

        public String toString() {
            return name.toString();
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new VarSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        public ElementKind getKind() {
            long flags = flags();
            if ((flags & PARAMETER) != 0) {
                if (isExceptionParameter())
                    return ElementKind.EXCEPTION_PARAMETER;
                else
                    return ElementKind.PARAMETER;
            } else if ((flags & ENUM) != 0) {
                return ElementKind.ENUM_CONSTANT;
            } else if (owner.kind == TYP || owner.kind == ERR) {
                return ElementKind.FIELD;
            } else if (isResourceVariable()) {
                return ElementKind.RESOURCE_VARIABLE;
            } else {
                return ElementKind.LOCAL_VARIABLE;
            }
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitVariable(this, p);
        }

        public Object getConstantValue() { // Mirror API
            return Constants.decode(getConstValue(), type);
        }

        public void setLazyConstValue(final Env<AttrContext> env,
                                      final Attr attr,
                                      final JCTree.JCExpression initializer)
        {
            setData(new Callable<Object>() {
                public Object call() {
                    return attr.attribLazyConstantValue(env, initializer, type);
                }
            });
        }

        /**
         * The variable's constant value, if this is a constant.
         * Before the constant value is evaluated, it points to an
         * initalizer environment.  If this is not a constant, it can
         * be used for other stuff.
         */
        private Object data;

        public boolean isExceptionParameter() {
            return data == ElementKind.EXCEPTION_PARAMETER;
        }

        public boolean isResourceVariable() {
            return data == ElementKind.RESOURCE_VARIABLE;
        }

        public Object getConstValue() {
            // TODO: Consider if getConstValue and getConstantValue can be collapsed
            if (data == ElementKind.EXCEPTION_PARAMETER ||
                data == ElementKind.RESOURCE_VARIABLE) {
                return null;
            } else if (data instanceof Callable<?>) {
                // In this case, this is a final variable, with an as
                // yet unevaluated initializer.
                Callable<?> eval = (Callable<?>)data;
                data = null; // to make sure we don't evaluate this twice.
                try {
                    data = eval.call();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
            return data;
        }

        public void setData(Object data) {
            Assert.check(!(data instanceof Env<?>), this);
            this.data = data;
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitVarSymbol(this, p);
        }
    }

    /** A class for method symbols.
     */
    public static class MethodSymbol extends Symbol implements ExecutableElement {

        /** The code of the method. */
        public Code code = null;

        /** The extra (synthetic/mandated) parameters of the method. */
        public List<VarSymbol> extraParams = List.nil();

        /** The parameters of the method. */
        public List<VarSymbol> params = null;

        /** The names of the parameters */
        public List<Name> savedParameterNames;

        /** For an attribute field accessor, its default value if any.
         *  The value is null if none appeared in the method
         *  declaration.
         */
        public Attribute defaultValue = null;

        /** Construct a method symbol, given its flags, name, type and owner.
         */
        public MethodSymbol(long flags, Name name, Type type, Symbol owner) {
            super(MTH, flags, name, type, owner);
            if (owner.type.hasTag(TYPEVAR)) Assert.error(owner + "." + name);
        }

        /** Clone this symbol with new owner.
         */
        public MethodSymbol clone(Symbol newOwner) {
            MethodSymbol m = new MethodSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return MethodSymbol.this;
                }
            };
            m.code = code;
            return m;
        }

        @Override
        public Set<Modifier> getModifiers() {
            long flags = flags();
            return Flags.asModifierSet((flags & DEFAULT) != 0 ? flags & ~ABSTRACT : flags);
        }

        /** The Java source which this symbol represents.
         */
        public String toString() {
            if ((flags() & BLOCK) != 0) {
                return owner.name.toString();
            } else {
                String s = (name == name.table.names.init)
                    ? owner.name.toString()
                    : name.toString();
                if (type != null) {
                    if (type.hasTag(FORALL))
                        s = "<" + ((ForAll)type).getTypeArguments() + ">" + s;
                    s += "(" + type.argtypes((flags() & VARARGS) != 0) + ")";
                }
                return s;
            }
        }

        public boolean isDynamic() {
            return false;
        }

        /** find a symbol that this (proxy method) symbol implements.
         *  @param    c       The class whose members are searched for
         *                    implementations
         */
        public Symbol implemented(TypeSymbol c, Types types) {
            Symbol impl = null;
            for (List<Type> is = types.interfaces(c.type);
                 impl == null && is.nonEmpty();
                 is = is.tail) {
                TypeSymbol i = is.head.tsym;
                impl = implementedIn(i, types);
                if (impl == null)
                    impl = implemented(i, types);
            }
            return impl;
        }

        public Symbol implementedIn(TypeSymbol c, Types types) {
            Symbol impl = null;
            for (Scope.Entry e = c.members().lookup(name);
                 impl == null && e.scope != null;
                 e = e.next()) {
                if (this.overrides(e.sym, (TypeSymbol)owner, types, true) &&
                    // FIXME: I suspect the following requires a
                    // subst() for a parametric return type.
                    types.isSameType(type.getReturnType(),
                                     types.memberType(owner.type, e.sym).getReturnType())) {
                    impl = e.sym;
                }
            }
            return impl;
        }

        /** Will the erasure of this method be considered by the VM to
         *  override the erasure of the other when seen from class `origin'?
         */
        public boolean binaryOverrides(Symbol _other, TypeSymbol origin, Types types) {
            if (isConstructor() || _other.kind != MTH) return false;

            if (this == _other) return true;
            MethodSymbol other = (MethodSymbol)_other;

            // check for a direct implementation
            if (other.isOverridableIn((TypeSymbol)owner) &&
                types.asSuper(owner.type, other.owner) != null &&
                types.isSameType(erasure(types), other.erasure(types)))
                return true;

            // check for an inherited implementation
            return
                (flags() & ABSTRACT) == 0 &&
                other.isOverridableIn(origin) &&
                this.isMemberOf(origin, types) &&
                types.isSameType(erasure(types), other.erasure(types));
        }

        /** The implementation of this (abstract) symbol in class origin,
         *  from the VM's point of view, null if method does not have an
         *  implementation in class.
         *  @param origin   The class of which the implementation is a member.
         */
        public MethodSymbol binaryImplementation(ClassSymbol origin, Types types) {
            for (TypeSymbol c = origin; c != null; c = types.supertype(c.type).tsym) {
                for (Scope.Entry e = c.members().lookup(name);
                     e.scope != null;
                     e = e.next()) {
                    if (e.sym.kind == MTH &&
                        ((MethodSymbol)e.sym).binaryOverrides(this, origin, types))
                        return (MethodSymbol)e.sym;
                }
            }
            return null;
        }

        /** Does this symbol override `other' symbol, when both are seen as
         *  members of class `origin'?  It is assumed that _other is a member
         *  of origin.
         *
         *  It is assumed that both symbols have the same name.  The static
         *  modifier is ignored for this test.
         *
         *  See JLS 8.4.6.1 (without transitivity) and 8.4.6.4
         */
        public boolean overrides(Symbol _other, TypeSymbol origin, Types types, boolean checkResult) {
            if (isConstructor() || _other.kind != MTH) return false;

            if (this == _other) return true;
            MethodSymbol other = (MethodSymbol)_other;

            // check for a direct implementation
            if (other.isOverridableIn((TypeSymbol)owner) &&
                types.asSuper(owner.type, other.owner) != null) {
                Type mt = types.memberType(owner.type, this);
                Type ot = types.memberType(owner.type, other);
                if (types.isSubSignature(mt, ot)) {
                    if (!checkResult)
                        return true;
                    if (types.returnTypeSubstitutable(mt, ot))
                        return true;
                }
            }

            // check for an inherited implementation
            if ((flags() & ABSTRACT) != 0 ||
                    ((other.flags() & ABSTRACT) == 0 && (other.flags() & DEFAULT) == 0) ||
                    !other.isOverridableIn(origin) ||
                    !this.isMemberOf(origin, types))
                return false;

            // assert types.asSuper(origin.type, other.owner) != null;
            Type mt = types.memberType(origin.type, this);
            Type ot = types.memberType(origin.type, other);
            return
                types.isSubSignature(mt, ot) &&
                (!checkResult || types.resultSubtype(mt, ot, types.noWarnings));
        }

        private boolean isOverridableIn(TypeSymbol origin) {
            // JLS 8.4.6.1
            switch ((int)(flags_field & Flags.AccessFlags)) {
            case Flags.PRIVATE:
                return false;
            case Flags.PUBLIC:
                return !this.owner.isInterface() ||
                        (flags_field & STATIC) == 0;
            case Flags.PROTECTED:
                return (origin.flags() & INTERFACE) == 0;
            case 0:
                // for package private: can only override in the same
                // package
                return
                    this.packge() == origin.packge() &&
                    (origin.flags() & INTERFACE) == 0;
            default:
                return false;
            }
        }

        @Override
        public boolean isInheritedIn(Symbol clazz, Types types) {
            switch ((int)(flags_field & Flags.AccessFlags)) {
                case PUBLIC:
                    return !this.owner.isInterface() ||
                            clazz == owner ||
                            (flags_field & STATIC) == 0;
                default:
                    return super.isInheritedIn(clazz, types);
            }
        }

        /** The implementation of this (abstract) symbol in class origin;
         *  null if none exists. Synthetic methods are not considered
         *  as possible implementations.
         */
        public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult) {
            return implementation(origin, types, checkResult, implementation_filter);
        }
        // where
            public static final Filter<Symbol> implementation_filter = new Filter<Symbol>() {
                public boolean accepts(Symbol s) {
                    return s.kind == Kinds.MTH &&
                            (s.flags() & SYNTHETIC) == 0;
                }
            };

        public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult, Filter<Symbol> implFilter) {
            MethodSymbol res = types.implementation(this, origin, checkResult, implFilter);
            if (res != null)
                return res;
            // if origin is derived from a raw type, we might have missed
            // an implementation because we do not know enough about instantiations.
            // in this case continue with the supertype as origin.
            if (types.isDerivedRaw(origin.type) && !origin.isInterface())
                return implementation(types.supertype(origin.type).tsym, types, checkResult);
            else
                return null;
        }

        public List<VarSymbol> params() {
            owner.complete();
            if (params == null) {
                // If ClassReader.saveParameterNames has been set true, then
                // savedParameterNames will be set to a list of names that
                // matches the types in type.getParameterTypes().  If any names
                // were not found in the class file, those names in the list will
                // be set to the empty name.
                // If ClassReader.saveParameterNames has been set false, then
                // savedParameterNames will be null.
                List<Name> paramNames = savedParameterNames;
                savedParameterNames = null;
                // discard the provided names if the list of names is the wrong size.
                if (paramNames == null || paramNames.size() != type.getParameterTypes().size()) {
                    paramNames = List.nil();
                }
                ListBuffer<VarSymbol> buf = new ListBuffer<VarSymbol>();
                List<Name> remaining = paramNames;
                // assert: remaining and paramNames are both empty or both
                // have same cardinality as type.getParameterTypes()
                int i = 0;
                for (Type t : type.getParameterTypes()) {
                    Name paramName;
                    if (remaining.isEmpty()) {
                        // no names for any parameters available
                        paramName = createArgName(i, paramNames);
                    } else {
                        paramName = remaining.head;
                        remaining = remaining.tail;
                        if (paramName.isEmpty()) {
                            // no name for this specific parameter
                            paramName = createArgName(i, paramNames);
                        }
                    }
                    buf.append(new VarSymbol(PARAMETER, paramName, t, this));
                    i++;
                }
                params = buf.toList();
            }
            return params;
        }

        // Create a name for the argument at position 'index' that is not in
        // the exclude list. In normal use, either no names will have been
        // provided, in which case the exclude list is empty, or all the names
        // will have been provided, in which case this method will not be called.
        private Name createArgName(int index, List<Name> exclude) {
            String prefix = "arg";
            while (true) {
                Name argName = name.table.fromString(prefix + index);
                if (!exclude.contains(argName))
                    return argName;
                prefix += "$";
            }
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new MethodSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        public ElementKind getKind() {
            if (name == name.table.names.init)
                return ElementKind.CONSTRUCTOR;
            else if (name == name.table.names.clinit)
                return ElementKind.STATIC_INIT;
            else if ((flags() & BLOCK) != 0)
                return isStatic() ? ElementKind.STATIC_INIT : ElementKind.INSTANCE_INIT;
            else
                return ElementKind.METHOD;
        }

        public boolean isStaticOrInstanceInit() {
            return getKind() == ElementKind.STATIC_INIT ||
                    getKind() == ElementKind.INSTANCE_INIT;
        }

        public Attribute getDefaultValue() {
            return defaultValue;
        }

        public List<VarSymbol> getParameters() {
            return params();
        }

        public boolean isVarArgs() {
            return (flags() & VARARGS) != 0;
        }

        public boolean isDefault() {
            return (flags() & DEFAULT) != 0;
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitMethodSymbol(this, p);
        }

        public Type getReceiverType() {
            return asType().getReceiverType();
        }

        public Type getReturnType() {
            return asType().getReturnType();
        }

        public List<Type> getThrownTypes() {
            return asType().getThrownTypes();
        }
    }

    /** A class for invokedynamic method calls.
     */
    public static class DynamicMethodSymbol extends MethodSymbol {

        public Object[] staticArgs;
        public Symbol bsm;
        public int bsmKind;

        public DynamicMethodSymbol(Name name, Symbol owner, int bsmKind, MethodSymbol bsm, Type type, Object[] staticArgs) {
            super(0, name, type, owner);
            this.bsm = bsm;
            this.bsmKind = bsmKind;
            this.staticArgs = staticArgs;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    /** A class for predefined operators.
     */
    public static class OperatorSymbol extends MethodSymbol {

        public int opcode;

        public OperatorSymbol(Name name, Type type, int opcode, Symbol owner) {
            super(PUBLIC | STATIC, name, type, owner);
            this.opcode = opcode;
        }

        public <R, P> R accept(Symbol.Visitor<R, P> v, P p) {
            return v.visitOperatorSymbol(this, p);
        }
    }

    /** Symbol completer interface.
     */
    public static interface Completer {
        void complete(Symbol sym) throws CompletionFailure;
    }

    public static class CompletionFailure extends RuntimeException {
        private static final long serialVersionUID = 0;
        public Symbol sym;

        /** A diagnostic object describing the failure
         */
        public JCDiagnostic diag;

        /** A localized string describing the failure.
         * @deprecated Use {@code getDetail()} or {@code getMessage()}
         */
        @Deprecated
        public String errmsg;

        public CompletionFailure(Symbol sym, String errmsg) {
            this.sym = sym;
            this.errmsg = errmsg;
//          this.printStackTrace();//DEBUG
        }

        public CompletionFailure(Symbol sym, JCDiagnostic diag) {
            this.sym = sym;
            this.diag = diag;
//          this.printStackTrace();//DEBUG
        }

        public JCDiagnostic getDiagnostic() {
            return diag;
        }

        @Override
        public String getMessage() {
            if (diag != null)
                return diag.getMessage(null);
            else
                return errmsg;
        }

        public Object getDetailValue() {
            return (diag != null ? diag : errmsg);
        }

        @Override
        public CompletionFailure initCause(Throwable cause) {
            super.initCause(cause);
            return this;
        }

    }

    /**
     * A visitor for symbols.  A visitor is used to implement operations
     * (or relations) on symbols.  Most common operations on types are
     * binary relations and this interface is designed for binary
     * relations, that is, operations on the form
     * Symbol&nbsp;&times;&nbsp;P&nbsp;&rarr;&nbsp;R.
     * <!-- In plain text: Type x P -> R -->
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <P> the type of the second argument (the first being the
     * symbol itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public interface Visitor<R,P> {
        R visitClassSymbol(ClassSymbol s, P arg);
        R visitMethodSymbol(MethodSymbol s, P arg);
        R visitPackageSymbol(PackageSymbol s, P arg);
        R visitOperatorSymbol(OperatorSymbol s, P arg);
        R visitVarSymbol(VarSymbol s, P arg);
        R visitTypeSymbol(TypeSymbol s, P arg);
        R visitSymbol(Symbol s, P arg);
    }
}
