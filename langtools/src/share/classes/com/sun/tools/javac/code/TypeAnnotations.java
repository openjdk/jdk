/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Type.AnnotatedType;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.CapturedType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.ErrorType;
import com.sun.tools.javac.code.Type.ForAll;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.PackageType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.UndetVar;
import com.sun.tools.javac.code.Type.Visitor;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntry;
import com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntryKind;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Annotate.Annotator;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;

/**
 * Contains operations specific to processing type annotations.
 * This class has two functions:
 * separate declaration from type annotations and insert the type
 * annotations to their types;
 * and determine the TypeAnnotationPositions for all type annotations.
 */
public class TypeAnnotations {
    // Class cannot be instantiated.
    private TypeAnnotations() {}

    /**
     * Separate type annotations from declaration annotations and
     * determine the correct positions for type annotations.
     * This version only visits types in signatures and should be
     * called from MemberEnter.
     * The method takes the Annotate object as parameter and
     * adds an Annotator to the correct Annotate queue for
     * later processing.
     */
    public static void organizeTypeAnnotationsSignatures(final Symtab syms, final Names names,
            final Log log, final JCClassDecl tree, Annotate annotate) {
        annotate.afterRepeated( new Annotator() {
            @Override
            public void enterAnnotation() {
                new TypeAnnotationPositions(syms, names, log, true).scan(tree);
            }
        } );
    }

    /**
     * This version only visits types in bodies, that is, field initializers,
     * top-level blocks, and method bodies, and should be called from Attr.
     */
    public static void organizeTypeAnnotationsBodies(Symtab syms, Names names, Log log, JCClassDecl tree) {
        new TypeAnnotationPositions(syms, names, log, false).scan(tree);
    }

    public enum AnnotationType { DECLARATION, TYPE, BOTH };

    /**
     * Determine whether an annotation is a declaration annotation,
     * a type annotation, or both.
     */
    public static AnnotationType annotationType(Symtab syms, Names names,
            Attribute.Compound a, Symbol s) {
        Attribute.Compound atTarget =
            a.type.tsym.attribute(syms.annotationTargetType.tsym);
        if (atTarget == null) {
            return inferTargetMetaInfo(a, s);
        }
        Attribute atValue = atTarget.member(names.value);
        if (!(atValue instanceof Attribute.Array)) {
            Assert.error("annotationType(): bad @Target argument " + atValue +
                    " (" + atValue.getClass() + ")");
            return AnnotationType.DECLARATION; // error recovery
        }
        Attribute.Array arr = (Attribute.Array) atValue;
        boolean isDecl = false, isType = false;
        for (Attribute app : arr.values) {
            if (!(app instanceof Attribute.Enum)) {
                Assert.error("annotationType(): unrecognized Attribute kind " + app +
                        " (" + app.getClass() + ")");
                isDecl = true;
                continue;
            }
            Attribute.Enum e = (Attribute.Enum) app;
            if (e.value.name == names.TYPE) {
                if (s.kind == Kinds.TYP)
                    isDecl = true;
            } else if (e.value.name == names.FIELD) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind != Kinds.MTH)
                    isDecl = true;
            } else if (e.value.name == names.METHOD) {
                if (s.kind == Kinds.MTH &&
                        !s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.PARAMETER) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) != 0)
                    isDecl = true;
            } else if (e.value.name == names.CONSTRUCTOR) {
                if (s.kind == Kinds.MTH &&
                        s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.LOCAL_VARIABLE) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) == 0)
                    isDecl = true;
            } else if (e.value.name == names.ANNOTATION_TYPE) {
                if (s.kind == Kinds.TYP &&
                        (s.flags() & Flags.ANNOTATION) != 0)
                    isDecl = true;
            } else if (e.value.name == names.PACKAGE) {
                if (s.kind == Kinds.PCK)
                    isDecl = true;
            } else if (e.value.name == names.TYPE_USE) {
                if (s.kind == Kinds.TYP ||
                        s.kind == Kinds.VAR ||
                        (s.kind == Kinds.MTH && !s.isConstructor() &&
                        !s.type.getReturnType().hasTag(TypeTag.VOID)) ||
                        (s.kind == Kinds.MTH && s.isConstructor()))
                    isType = true;
            } else if (e.value.name == names.TYPE_PARAMETER) {
                /* Irrelevant in this case */
                // TYPE_PARAMETER doesn't aid in distinguishing between
                // Type annotations and declaration annotations on an
                // Element
            } else {
                Assert.error("annotationType(): unrecognized Attribute name " + e.value.name +
                        " (" + e.value.name.getClass() + ")");
                isDecl = true;
            }
        }
        if (isDecl && isType) {
            return AnnotationType.BOTH;
        } else if (isType) {
            return AnnotationType.TYPE;
        } else {
            return AnnotationType.DECLARATION;
        }
    }

    /** Infer the target annotation kind, if none is give.
     * We only infer declaration annotations.
     */
    private static AnnotationType inferTargetMetaInfo(Attribute.Compound a, Symbol s) {
        return AnnotationType.DECLARATION;
    }


    private static class TypeAnnotationPositions extends TreeScanner {

        private final Symtab syms;
        private final Names names;
        private final Log log;
        private final boolean sigOnly;

        private TypeAnnotationPositions(Symtab syms, Names names, Log log, boolean sigOnly) {
            this.syms = syms;
            this.names = names;
            this.log = log;
            this.sigOnly = sigOnly;
        }

        /*
         * When traversing the AST we keep the "frames" of visited
         * trees in order to determine the position of annotations.
         */
        private ListBuffer<JCTree> frames = ListBuffer.lb();

        protected void push(JCTree t) { frames = frames.prepend(t); }
        protected JCTree pop() { return frames.next(); }
        // could this be frames.elems.tail.head?
        private JCTree peek2() { return frames.toList().tail.head; }

        @Override
        public void scan(JCTree tree) {
            push(tree);
            super.scan(tree);
            pop();
        }

        /**
         * Separates type annotations from declaration annotations.
         * This step is needed because in certain locations (where declaration
         * and type annotations can be mixed, e.g. the type of a field)
         * we never build an JCAnnotatedType. This step finds these
         * annotations and marks them as if they were part of the type.
         */
        private void separateAnnotationsKinds(JCTree typetree, Type type, Symbol sym,
                TypeAnnotationPosition pos) {
            /*
            System.out.printf("separateAnnotationsKinds(typetree: %s, type: %s, symbol: %s, pos: %s%n",
                    typetree, type, sym, pos);
            */
            List<Attribute.Compound> annotations = sym.getRawAttributes();
            ListBuffer<Attribute.Compound> declAnnos = new ListBuffer<Attribute.Compound>();
            ListBuffer<Attribute.TypeCompound> typeAnnos = new ListBuffer<Attribute.TypeCompound>();

            for (Attribute.Compound a : annotations) {
                switch (annotationType(syms, names, a, sym)) {
                case DECLARATION:
                    declAnnos.append(a);
                    break;
                case BOTH: {
                    declAnnos.append(a);
                    Attribute.TypeCompound ta = toTypeCompound(a, pos);
                    typeAnnos.append(ta);
                    break;
                }
                case TYPE: {
                    Attribute.TypeCompound ta = toTypeCompound(a, pos);
                    typeAnnos.append(ta);
                    break;
                }
                }
            }

            sym.annotations.reset();
            sym.annotations.setDeclarationAttributes(declAnnos.toList());

            if (typeAnnos.isEmpty()) {
                return;
            }

            List<Attribute.TypeCompound> typeAnnotations = typeAnnos.toList();

            if (type == null) {
                // When type is null, put the type annotations to the symbol.
                // This is used for constructor return annotations, for which
                // no appropriate type exists.
                sym.annotations.appendUniqueTypes(typeAnnotations);
                return;
            }

            // type is non-null and annotations are added to that type
            type = typeWithAnnotations(typetree, type, typeAnnotations, log);

            if (sym.getKind() == ElementKind.METHOD) {
                sym.type.asMethodType().restype = type;
            } else if (sym.getKind() == ElementKind.PARAMETER) {
                sym.type = type;
                if (sym.getQualifiedName().equals(names._this)) {
                    sym.owner.type.asMethodType().recvtype = type;
                    // note that the typeAnnotations will also be added to the owner below.
                } else {
                    MethodType methType = sym.owner.type.asMethodType();
                    List<VarSymbol> params = ((MethodSymbol)sym.owner).params;
                    List<Type> oldArgs = methType.argtypes;
                    ListBuffer<Type> newArgs = new ListBuffer<Type>();
                    while (params.nonEmpty()) {
                        if (params.head == sym) {
                            newArgs.add(type);
                        } else {
                            newArgs.add(oldArgs.head);
                        }
                        oldArgs = oldArgs.tail;
                        params = params.tail;
                    }
                    methType.argtypes = newArgs.toList();
                }
            } else {
                sym.type = type;
            }

            sym.annotations.appendUniqueTypes(typeAnnotations);

            if (sym.getKind() == ElementKind.PARAMETER ||
                    sym.getKind() == ElementKind.LOCAL_VARIABLE ||
                    sym.getKind() == ElementKind.RESOURCE_VARIABLE ||
                    sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                // Make sure all type annotations from the symbol are also
                // on the owner.
                sym.owner.annotations.appendUniqueTypes(sym.getRawTypeAttributes());
            }
        }

        // This method has a similar purpose as
        // {@link com.sun.tools.javac.parser.JavacParser.insertAnnotationsToMostInner(JCExpression, List<JCTypeAnnotation>, boolean)}
        // We found a type annotation in a declaration annotation position,
        // for example, on the return type.
        // Such an annotation is _not_ part of an JCAnnotatedType tree and we therefore
        // need to set its position explicitly.
        // The method returns a copy of type that contains these annotations.
        //
        // As a side effect the method sets the type annotation position of "annotations".
        // Note that it is assumed that all annotations share the same position.
        private static Type typeWithAnnotations(final JCTree typetree, final Type type,
                final List<Attribute.TypeCompound> annotations, Log log) {
            // System.out.printf("typeWithAnnotations(typetree: %s, type: %s, annotations: %s)%n",
            //         typetree, type, annotations);
            if (annotations.isEmpty()) {
                return type;
            }
            if (type.hasTag(TypeTag.ARRAY)) {
                Type toreturn;
                Type.ArrayType tomodify;
                Type.ArrayType arType;
                {
                    Type touse = type;
                    if (type.isAnnotated()) {
                        Type.AnnotatedType atype = (Type.AnnotatedType)type;
                        toreturn = new Type.AnnotatedType(atype.underlyingType);
                        ((Type.AnnotatedType)toreturn).typeAnnotations = atype.typeAnnotations;
                        touse = atype.underlyingType;
                        arType = (Type.ArrayType) touse;
                        tomodify = new Type.ArrayType(null, arType.tsym);
                        ((Type.AnnotatedType)toreturn).underlyingType = tomodify;
                    } else {
                        arType = (Type.ArrayType) touse;
                        tomodify = new Type.ArrayType(null, arType.tsym);
                        toreturn = tomodify;
                    }
                }
                JCArrayTypeTree arTree = arrayTypeTree(typetree);

                ListBuffer<TypePathEntry> depth = ListBuffer.lb();
                depth = depth.append(TypePathEntry.ARRAY);
                while (arType.elemtype.hasTag(TypeTag.ARRAY)) {
                    if (arType.elemtype.isAnnotated()) {
                        Type.AnnotatedType aelemtype = (Type.AnnotatedType) arType.elemtype;
                        Type.AnnotatedType newAT = new Type.AnnotatedType(aelemtype.underlyingType);
                        tomodify.elemtype = newAT;
                        newAT.typeAnnotations = aelemtype.typeAnnotations;
                        arType = (Type.ArrayType) aelemtype.underlyingType;
                        tomodify = new Type.ArrayType(null, arType.tsym);
                        newAT.underlyingType = tomodify;
                    } else {
                        arType = (Type.ArrayType) arType.elemtype;
                        tomodify.elemtype = new Type.ArrayType(null, arType.tsym);
                        tomodify = (Type.ArrayType) tomodify.elemtype;
                    }
                    arTree = arrayTypeTree(arTree.elemtype);
                    depth = depth.append(TypePathEntry.ARRAY);
                }
                Type arelemType = typeWithAnnotations(arTree.elemtype, arType.elemtype, annotations, log);
                tomodify.elemtype = arelemType;
                {
                    // All annotations share the same position; modify the first one.
                    Attribute.TypeCompound a = annotations.get(0);
                    TypeAnnotationPosition p = a.position;
                    p.location = p.location.prependList(depth.toList());
                }
                typetree.type = toreturn;
                return toreturn;
            } else if (type.hasTag(TypeTag.TYPEVAR)) {
                // Nothing to do for type variables.
                return type;
            } else if (type.getKind() == TypeKind.UNION) {
                // There is a TypeKind, but no TypeTag.
                JCTypeUnion tutree = (JCTypeUnion) typetree;
                JCExpression fst = tutree.alternatives.get(0);
                Type res = typeWithAnnotations(fst, fst.type, annotations, log);
                fst.type = res;
                // TODO: do we want to set res as first element in uct.alternatives?
                // UnionClassType uct = (com.sun.tools.javac.code.Type.UnionClassType)type;
                // Return the un-annotated union-type.
                return type;
            } else {
                Type enclTy = type;
                Element enclEl = type.asElement();
                JCTree enclTr = typetree;

                while (enclEl != null &&
                        enclEl.getKind() != ElementKind.PACKAGE &&
                        enclTy != null &&
                        enclTy.getKind() != TypeKind.NONE &&
                        enclTy.getKind() != TypeKind.ERROR &&
                        (enclTr.getKind() == JCTree.Kind.MEMBER_SELECT ||
                         enclTr.getKind() == JCTree.Kind.PARAMETERIZED_TYPE ||
                         enclTr.getKind() == JCTree.Kind.ANNOTATED_TYPE)) {
                    // Iterate also over the type tree, not just the type: the type is already
                    // completely resolved and we cannot distinguish where the annotation
                    // belongs for a nested type.
                    if (enclTr.getKind() == JCTree.Kind.MEMBER_SELECT) {
                        // only change encl in this case.
                        enclTy = enclTy.getEnclosingType();
                        enclEl = enclEl.getEnclosingElement();
                        enclTr = ((JCFieldAccess)enclTr).getExpression();
                    } else if (enclTr.getKind() == JCTree.Kind.PARAMETERIZED_TYPE) {
                        enclTr = ((JCTypeApply)enclTr).getType();
                    } else {
                        // only other option because of while condition
                        enclTr = ((JCAnnotatedType)enclTr).getUnderlyingType();
                    }
                }

                /** We are trying to annotate some enclosing type,
                 * but nothing more exists.
                 */
                if (enclTy != null &&
                        enclTy.getKind() == TypeKind.NONE &&
                        (enclTr.getKind() == JCTree.Kind.IDENTIFIER ||
                         enclTr.getKind() == JCTree.Kind.MEMBER_SELECT ||
                         enclTr.getKind() == JCTree.Kind.PARAMETERIZED_TYPE ||
                         enclTr.getKind() == JCTree.Kind.ANNOTATED_TYPE)) {
                    // TODO: also if it's "java. @A lang.Object", that is,
                    // if it's on a package?
                    log.error(enclTr.pos(), "cant.annotate.nested.type", enclTr.toString());
                    return type;
                }

                // At this point we have visited the part of the nested
                // type that is written in the source code.
                // Now count from here to the actual top-level class to determine
                // the correct nesting.

                // The genericLocation for the annotation.
                ListBuffer<TypePathEntry> depth = ListBuffer.lb();

                Type topTy = enclTy;
                while (enclEl != null &&
                        enclEl.getKind() != ElementKind.PACKAGE &&
                        topTy != null &&
                        topTy.getKind() != TypeKind.NONE &&
                        topTy.getKind() != TypeKind.ERROR) {
                    topTy = topTy.getEnclosingType();
                    enclEl = enclEl.getEnclosingElement();

                    if (topTy != null && topTy.getKind() != TypeKind.NONE) {
                        // Only count enclosing types.
                        depth = depth.append(TypePathEntry.INNER_TYPE);
                    }
                }

                if (depth.nonEmpty()) {
                    // Only need to change the annotation positions
                    // if they are on an enclosed type.
                    // All annotations share the same position; modify the first one.
                    Attribute.TypeCompound a = annotations.get(0);
                    TypeAnnotationPosition p = a.position;
                    p.location = p.location.appendList(depth.toList());
                }

                Type ret = typeWithAnnotations(type, enclTy, annotations);
                typetree.type = ret;
                return ret;
            }
        }

        private static JCArrayTypeTree arrayTypeTree(JCTree typetree) {
            if (typetree.getKind() == JCTree.Kind.ARRAY_TYPE) {
                return (JCArrayTypeTree) typetree;
            } else if (typetree.getKind() == JCTree.Kind.ANNOTATED_TYPE) {
                return (JCArrayTypeTree) ((JCAnnotatedType)typetree).underlyingType;
            } else {
                Assert.error("Could not determine array type from type tree: " + typetree);
                return null;
            }
        }

        /** Return a copy of the first type that only differs by
         * inserting the annotations to the left-most/inner-most type
         * or the type given by stopAt.
         *
         * We need the stopAt parameter to know where on a type to
         * put the annotations.
         * If we have nested classes Outer > Middle > Inner, and we
         * have the source type "@A Middle.Inner", we will invoke
         * this method with type = Outer.Middle.Inner,
         * stopAt = Middle.Inner, and annotations = @A.
         *
         * @param type The type to copy.
         * @param stopAt The type to stop at.
         * @param annotations The annotations to insert.
         * @return A copy of type that contains the annotations.
         */
        private static Type typeWithAnnotations(final Type type,
                final Type stopAt,
                final List<Attribute.TypeCompound> annotations) {
            Visitor<Type, List<TypeCompound>> visitor =
                    new Type.Visitor<Type, List<Attribute.TypeCompound>>() {
                @Override
                public Type visitClassType(ClassType t, List<TypeCompound> s) {
                    // assert that t.constValue() == null?
                    if (t == stopAt ||
                        t.getEnclosingType() == Type.noType) {
                        return new AnnotatedType(s, t);
                    } else {
                        ClassType ret = new ClassType(t.getEnclosingType().accept(this, s),
                                t.typarams_field, t.tsym);
                        ret.all_interfaces_field = t.all_interfaces_field;
                        ret.allparams_field = t.allparams_field;
                        ret.interfaces_field = t.interfaces_field;
                        ret.rank_field = t.rank_field;
                        ret.supertype_field = t.supertype_field;
                        return ret;
                    }
                }

                @Override
                public Type visitAnnotatedType(AnnotatedType t, List<TypeCompound> s) {
                    return new AnnotatedType(t.typeAnnotations, t.underlyingType.accept(this, s));
                }

                @Override
                public Type visitWildcardType(WildcardType t, List<TypeCompound> s) {
                    return new AnnotatedType(s, t);
                }

                @Override
                public Type visitArrayType(ArrayType t, List<TypeCompound> s) {
                    ArrayType ret = new ArrayType(t.elemtype.accept(this, s), t.tsym);
                    return ret;
                }

                @Override
                public Type visitMethodType(MethodType t, List<TypeCompound> s) {
                    // Impossible?
                    return t;
                }

                @Override
                public Type visitPackageType(PackageType t, List<TypeCompound> s) {
                    // Impossible?
                    return t;
                }

                @Override
                public Type visitTypeVar(TypeVar t, List<TypeCompound> s) {
                    return new AnnotatedType(s, t);
                }

                @Override
                public Type visitCapturedType(CapturedType t, List<TypeCompound> s) {
                    return new AnnotatedType(s, t);
                }

                @Override
                public Type visitForAll(ForAll t, List<TypeCompound> s) {
                    // Impossible?
                    return t;
                }

                @Override
                public Type visitUndetVar(UndetVar t, List<TypeCompound> s) {
                    // Impossible?
                    return t;
                }

                @Override
                public Type visitErrorType(ErrorType t, List<TypeCompound> s) {
                    return new AnnotatedType(s, t);
                }

                @Override
                public Type visitType(Type t, List<TypeCompound> s) {
                    return new AnnotatedType(s, t);
                }
            };

            return type.accept(visitor, annotations);
        }

        private static Attribute.TypeCompound toTypeCompound(Attribute.Compound a, TypeAnnotationPosition p) {
            // It is safe to alias the position.
            return new Attribute.TypeCompound(a, p);
        }


        /* This is the beginning of the second part of organizing
         * type annotations: determine the type annotation positions.
         */

        private void resolveFrame(JCTree tree, JCTree frame,
                List<JCTree> path, TypeAnnotationPosition p) {
            /*
            System.out.println("Resolving tree: " + tree + " kind: " + tree.getKind());
            System.out.println("    Framing tree: " + frame + " kind: " + frame.getKind());
            */

            // Note that p.offset is set in
            // com.sun.tools.javac.jvm.Gen.setTypeAnnotationPositions(int)

            switch (frame.getKind()) {
                case TYPE_CAST:
                    JCTypeCast frameTC = (JCTypeCast) frame;
                    p.type = TargetType.CAST;
                    if (frameTC.clazz.hasTag(Tag.TYPEINTERSECTION)) {
                        // This case was already handled by INTERSECTION_TYPE
                    } else {
                        p.type_index = 0;
                    }
                    p.pos = frame.pos;
                    return;

                case INSTANCE_OF:
                    p.type = TargetType.INSTANCEOF;
                    p.pos = frame.pos;
                    return;

                case NEW_CLASS:
                    JCNewClass frameNewClass = (JCNewClass) frame;
                    if (frameNewClass.def != null) {
                        // Special handling for anonymous class instantiations
                        JCClassDecl frameClassDecl = frameNewClass.def;
                        if (frameClassDecl.extending == tree) {
                            p.type = TargetType.CLASS_EXTENDS;
                            p.type_index = -1;
                        } else if (frameClassDecl.implementing.contains(tree)) {
                            p.type = TargetType.CLASS_EXTENDS;
                            p.type_index = frameClassDecl.implementing.indexOf(tree);
                        } else {
                            // In contrast to CLASS below, typarams cannot occur here.
                            Assert.error("Could not determine position of tree " + tree +
                                    " within frame " + frame);
                        }
                    } else if (frameNewClass.typeargs.contains(tree)) {
                        p.type = TargetType.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT;
                        p.type_index = frameNewClass.typeargs.indexOf(tree);
                    } else {
                        p.type = TargetType.NEW;
                    }
                    p.pos = frame.pos;
                    return;

                case NEW_ARRAY:
                    p.type = TargetType.NEW;
                    p.pos = frame.pos;
                    return;

                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    p.pos = frame.pos;
                    if (((JCClassDecl)frame).extending == tree) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = -1;
                    } else if (((JCClassDecl)frame).implementing.contains(tree)) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = ((JCClassDecl)frame).implementing.indexOf(tree);
                    } else if (((JCClassDecl)frame).typarams.contains(tree)) {
                        p.type = TargetType.CLASS_TYPE_PARAMETER;
                        p.parameter_index = ((JCClassDecl)frame).typarams.indexOf(tree);
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;

                case METHOD: {
                    JCMethodDecl frameMethod = (JCMethodDecl) frame;
                    p.pos = frame.pos;
                    if (frameMethod.thrown.contains(tree)) {
                        p.type = TargetType.THROWS;
                        p.type_index = frameMethod.thrown.indexOf(tree);
                    } else if (frameMethod.restype == tree) {
                        p.type = TargetType.METHOD_RETURN;
                    } else if (frameMethod.typarams.contains(tree)) {
                        p.type = TargetType.METHOD_TYPE_PARAMETER;
                        p.parameter_index = frameMethod.typarams.indexOf(tree);
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;
                }

                case PARAMETERIZED_TYPE: {
                    List<JCTree> newPath = path.tail;

                    if (((JCTypeApply)frame).clazz == tree) {
                        // generic: RAW; noop
                    } else if (((JCTypeApply)frame).arguments.contains(tree)) {
                        JCTypeApply taframe = (JCTypeApply) frame;
                        int arg = taframe.arguments.indexOf(tree);
                        p.location = p.location.prepend(new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT, arg));

                        Type typeToUse;
                        if (newPath.tail != null && newPath.tail.head.hasTag(Tag.NEWCLASS)) {
                            // If we are within an anonymous class instantiation, use its type,
                            // because it contains a correctly nested type.
                            typeToUse = newPath.tail.head.type;
                        } else {
                            typeToUse = taframe.type;
                        }

                        locateNestedTypes(typeToUse, p);
                    } else {
                        Assert.error("Could not determine type argument position of tree " + tree +
                                " within frame " + frame);
                    }

                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case MEMBER_REFERENCE: {
                    JCMemberReference mrframe = (JCMemberReference) frame;

                    if (mrframe.expr == tree) {
                        switch (mrframe.mode) {
                        case INVOKE:
                            p.type = TargetType.METHOD_REFERENCE;
                            break;
                        case NEW:
                            p.type = TargetType.CONSTRUCTOR_REFERENCE;
                            break;
                        default:
                            Assert.error("Unknown method reference mode " + mrframe.mode +
                                    " for tree " + tree + " within frame " + frame);
                        }
                        p.pos = frame.pos;
                    } else if (mrframe.typeargs != null &&
                            mrframe.typeargs.contains(tree)) {
                        int arg = mrframe.typeargs.indexOf(tree);
                        p.type_index = arg;
                        switch (mrframe.mode) {
                        case INVOKE:
                            p.type = TargetType.METHOD_REFERENCE_TYPE_ARGUMENT;
                            break;
                        case NEW:
                            p.type = TargetType.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT;
                            break;
                        default:
                            Assert.error("Unknown method reference mode " + mrframe.mode +
                                    " for tree " + tree + " within frame " + frame);
                        }
                        p.pos = frame.pos;
                    } else {
                        Assert.error("Could not determine type argument position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;
                }

                case ARRAY_TYPE: {
                    ListBuffer<TypePathEntry> index = ListBuffer.lb();
                    index = index.append(TypePathEntry.ARRAY);
                    List<JCTree> newPath = path.tail;
                    while (true) {
                        JCTree npHead = newPath.tail.head;
                        if (npHead.hasTag(JCTree.Tag.TYPEARRAY)) {
                            newPath = newPath.tail;
                            index = index.append(TypePathEntry.ARRAY);
                        } else if (npHead.hasTag(JCTree.Tag.ANNOTATED_TYPE)) {
                            newPath = newPath.tail;
                        } else {
                            break;
                        }
                    }
                    p.location = p.location.prependList(index.toList());
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case TYPE_PARAMETER:
                    if (path.tail.tail.head.hasTag(JCTree.Tag.CLASSDEF)) {
                        JCClassDecl clazz = (JCClassDecl)path.tail.tail.head;
                        p.type = TargetType.CLASS_TYPE_PARAMETER_BOUND;
                        p.parameter_index = clazz.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter)frame).bounds.indexOf(tree);
                        if (((JCTypeParameter)frame).bounds.get(0).type.isInterface()) {
                            // Account for an implicit Object as bound 0
                            p.bound_index += 1;
                        }
                    } else if (path.tail.tail.head.hasTag(JCTree.Tag.METHODDEF)) {
                        JCMethodDecl method = (JCMethodDecl)path.tail.tail.head;
                        p.type = TargetType.METHOD_TYPE_PARAMETER_BOUND;
                        p.parameter_index = method.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter)frame).bounds.indexOf(tree);
                        if (((JCTypeParameter)frame).bounds.get(0).type.isInterface()) {
                            // Account for an implicit Object as bound 0
                            p.bound_index += 1;
                        }
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    p.pos = frame.pos;
                    return;

                case VARIABLE:
                    VarSymbol v = ((JCVariableDecl)frame).sym;
                    p.pos = frame.pos;
                    switch (v.getKind()) {
                        case LOCAL_VARIABLE:
                            p.type = TargetType.LOCAL_VARIABLE;
                            break;
                        case FIELD:
                            p.type = TargetType.FIELD;
                            break;
                        case PARAMETER:
                            if (v.getQualifiedName().equals(names._this)) {
                                // TODO: Intro a separate ElementKind?
                                p.type = TargetType.METHOD_RECEIVER;
                            } else {
                                p.type = TargetType.METHOD_FORMAL_PARAMETER;
                                p.parameter_index = methodParamIndex(path, frame);
                            }
                            break;
                        case EXCEPTION_PARAMETER:
                            p.type = TargetType.EXCEPTION_PARAMETER;
                            break;
                        case RESOURCE_VARIABLE:
                            p.type = TargetType.RESOURCE_VARIABLE;
                            break;
                        default:
                            Assert.error("Found unexpected type annotation for variable: " + v + " with kind: " + v.getKind());
                    }
                    if (v.getKind() != ElementKind.FIELD) {
                        v.owner.annotations.appendUniqueTypes(v.getRawTypeAttributes());
                    }
                    return;

                case ANNOTATED_TYPE: {
                    if (frame == tree) {
                        // This is only true for the first annotated type we see.
                        // For any other annotated types along the path, we do
                        // not care about inner types.
                        JCAnnotatedType atypetree = (JCAnnotatedType) frame;
                        final Type utype = atypetree.underlyingType.type;
                        if (utype == null) {
                            // This might happen during DeferredAttr;
                            // we will be back later.
                            return;
                        }
                        Symbol tsym = utype.tsym;
                        if (tsym.getKind().equals(ElementKind.TYPE_PARAMETER) ||
                                utype.getKind().equals(TypeKind.WILDCARD) ||
                                utype.getKind().equals(TypeKind.ARRAY)) {
                            // Type parameters, wildcards, and arrays have the declaring
                            // class/method as enclosing elements.
                            // There is actually nothing to do for them.
                        } else {
                            locateNestedTypes(utype, p);
                        }
                    }
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case UNION_TYPE: {
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case INTERSECTION_TYPE: {
                    JCTypeIntersection isect = (JCTypeIntersection)frame;
                    p.type_index = isect.bounds.indexOf(tree);
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case METHOD_INVOCATION: {
                    JCMethodInvocation invocation = (JCMethodInvocation)frame;
                    if (!invocation.typeargs.contains(tree)) {
                        Assert.error("{" + tree + "} is not an argument in the invocation: " + invocation);
                    }
                    p.type = TargetType.METHOD_INVOCATION_TYPE_ARGUMENT;
                    p.pos = invocation.pos;
                    p.type_index = invocation.typeargs.indexOf(tree);
                    return;
                }

                case EXTENDS_WILDCARD:
                case SUPER_WILDCARD: {
                    // Annotations in wildcard bounds
                    p.location = p.location.prepend(TypePathEntry.WILDCARD);
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                case MEMBER_SELECT: {
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }

                default:
                    Assert.error("Unresolved frame: " + frame + " of kind: " + frame.getKind() +
                            "\n    Looking for tree: " + tree);
                    return;
            }
        }

        private static void locateNestedTypes(Type type, TypeAnnotationPosition p) {
            // The number of "steps" to get from the full type to the
            // left-most outer type.
            ListBuffer<TypePathEntry> depth = ListBuffer.lb();

            Type encl = type.getEnclosingType();
            while (encl != null &&
                    encl.getKind() != TypeKind.NONE &&
                    encl.getKind() != TypeKind.ERROR) {
                depth = depth.append(TypePathEntry.INNER_TYPE);
                encl = encl.getEnclosingType();
            }
            if (depth.nonEmpty()) {
                p.location = p.location.prependList(depth.toList());
            }
        }

        private static int methodParamIndex(List<JCTree> path, JCTree param) {
            List<JCTree> curr = path;
            while (curr.head.getTag() != Tag.METHODDEF &&
                    curr.head.getTag() != Tag.LAMBDA) {
                curr = curr.tail;
            }
            if (curr.head.getTag() == Tag.METHODDEF) {
                JCMethodDecl method = (JCMethodDecl)curr.head;
                return method.params.indexOf(param);
            } else if (curr.head.getTag() == Tag.LAMBDA) {
                JCLambda lambda = (JCLambda)curr.head;
                return lambda.params.indexOf(param);
            } else {
                Assert.error("methodParamIndex expected to find method or lambda for param: " + param);
                return -1;
            }
        }

        // Each class (including enclosed inner classes) is visited separately.
        // This flag is used to prevent from visiting inner classes.
        private boolean isInClass = false;

        @Override
        public void visitClassDef(JCClassDecl tree) {
            if (isInClass)
                return;
            isInClass = true;

            if (sigOnly) {
                scan(tree.mods);
                scan(tree.typarams);
                scan(tree.extending);
                scan(tree.implementing);
            }
            scan(tree.defs);
        }

        /**
         * Resolve declaration vs. type annotations in methods and
         * then determine the positions.
         */
        @Override
        public void visitMethodDef(final JCMethodDecl tree) {
            if (tree.sym == null) {
                // Something most be wrong, e.g. a class not found.
                // Quietly ignore. (See test FailOver15.java)
                return;
            }
            if (sigOnly) {
                if (!tree.mods.annotations.isEmpty()) {
                    // Nothing to do for separateAnnotationsKinds if
                    // there are no annotations of either kind.
                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.METHOD_RETURN;
                    if (tree.sym.isConstructor()) {
                        pos.pos = tree.pos;
                        // Use null to mark that the annotations go with the symbol.
                        separateAnnotationsKinds(tree, null, tree.sym, pos);
                    } else {
                        pos.pos = tree.restype.pos;
                        separateAnnotationsKinds(tree.restype, tree.sym.type.getReturnType(),
                                tree.sym, pos);
                    }
                }
                if (tree.recvparam != null && tree.recvparam.sym != null &&
                        !tree.recvparam.mods.annotations.isEmpty()) {
                    // Nothing to do for separateAnnotationsKinds if
                    // there are no annotations of either kind.
                    // TODO: make sure there are no declaration annotations.
                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.METHOD_RECEIVER;
                    pos.pos = tree.recvparam.vartype.pos;
                    separateAnnotationsKinds(tree.recvparam.vartype, tree.recvparam.sym.type,
                            tree.recvparam.sym, pos);
                }
                int i = 0;
                for (JCVariableDecl param : tree.params) {
                    if (!param.mods.annotations.isEmpty()) {
                        // Nothing to do for separateAnnotationsKinds if
                        // there are no annotations of either kind.
                        TypeAnnotationPosition pos = new TypeAnnotationPosition();
                        pos.type = TargetType.METHOD_FORMAL_PARAMETER;
                        pos.parameter_index = i;
                        pos.pos = param.vartype.pos;
                        separateAnnotationsKinds(param.vartype, param.sym.type, param.sym, pos);
                    }
                    ++i;
                }
            }

            push(tree);
            // super.visitMethodDef(tree);
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.restype);
                scan(tree.typarams);
                scan(tree.recvparam);
                scan(tree.params);
                scan(tree.thrown);
            } else {
                scan(tree.defaultValue);
                scan(tree.body);
            }
            pop();
        }

        /* Store a reference to the current lambda expression, to
         * be used by all type annotations within this expression.
         */
        private JCLambda currentLambda = null;

        public void visitLambda(JCLambda tree) {
            JCLambda prevLambda = currentLambda;
            try {
                currentLambda = tree;

                int i = 0;
                for (JCVariableDecl param : tree.params) {
                    if (!param.mods.annotations.isEmpty()) {
                        // Nothing to do for separateAnnotationsKinds if
                        // there are no annotations of either kind.
                        TypeAnnotationPosition pos = new TypeAnnotationPosition();
                        pos.type = TargetType.METHOD_FORMAL_PARAMETER;
                        pos.parameter_index = i;
                        pos.pos = param.vartype.pos;
                        pos.onLambda = tree;
                        separateAnnotationsKinds(param.vartype, param.sym.type, param.sym, pos);
                    }
                    ++i;
                }

                push(tree);
                scan(tree.body);
                scan(tree.params);
                pop();
            } finally {
                currentLambda = prevLambda;
            }
        }

        /**
         * Resolve declaration vs. type annotations in variable declarations and
         * then determine the positions.
         */
        @Override
        public void visitVarDef(final JCVariableDecl tree) {
            if (tree.mods.annotations.isEmpty()) {
                // Nothing to do for separateAnnotationsKinds if
                // there are no annotations of either kind.
            } else if (tree.sym == null) {
                // Something is wrong already. Quietly ignore.
            } else if (tree.sym.getKind() == ElementKind.PARAMETER) {
                // Parameters are handled in visitMethodDef or visitLambda.
            } else if (tree.sym.getKind() == ElementKind.FIELD) {
                if (sigOnly) {
                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.FIELD;
                    pos.pos = tree.pos;
                    separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
                }
            } else if (tree.sym.getKind() == ElementKind.LOCAL_VARIABLE) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.LOCAL_VARIABLE;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.EXCEPTION_PARAMETER;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.RESOURCE_VARIABLE) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.RESOURCE_VARIABLE;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.ENUM_CONSTANT) {
                // No type annotations can occur here.
            } else {
                // There is nothing else in a variable declaration that needs separation.
                Assert.error("Unhandled variable kind: " + tree + " of kind: " + tree.sym.getKind());
            }

            push(tree);
            // super.visitVarDef(tree);
            scan(tree.mods);
            scan(tree.vartype);
            if (!sigOnly) {
                scan(tree.init);
            }
            pop();
        }

        @Override
        public void visitBlock(JCBlock tree) {
            // Do not descend into top-level blocks when only interested
            // in the signature.
            if (!sigOnly) {
                scan(tree.stats);
            }
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            push(tree);
            findPosition(tree, tree, tree.annotations);
            pop();
            super.visitAnnotatedType(tree);
        }

        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            findPosition(tree, peek2(), tree.annotations);
            super.visitTypeParameter(tree);
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (tree.def != null &&
                    !tree.def.mods.annotations.isEmpty()) {
                JCClassDecl classdecl = tree.def;
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.CLASS_EXTENDS;
                pos.pos = tree.pos;
                if (classdecl.extending == tree.clazz) {
                    pos.type_index = -1;
                } else if (classdecl.implementing.contains(tree.clazz)) {
                    pos.type_index = classdecl.implementing.indexOf(tree.clazz);
                } else {
                    // In contrast to CLASS elsewhere, typarams cannot occur here.
                    Assert.error("Could not determine position of tree " + tree);
                }
                Type before = classdecl.sym.type;
                separateAnnotationsKinds(classdecl, tree.clazz.type, classdecl.sym, pos);

                // classdecl.sym.type now contains an annotated type, which
                // is not what we want there.
                // TODO: should we put this type somewhere in the superclass/interface?
                classdecl.sym.type = before;
            }

            scan(tree.encl);
            scan(tree.typeargs);
            scan(tree.clazz);
            scan(tree.args);

            // The class body will already be scanned.
            // scan(tree.def);
        }

        @Override
        public void visitNewArray(JCNewArray tree) {
            findPosition(tree, tree, tree.annotations);
            int dimAnnosCount = tree.dimAnnotations.size();
            ListBuffer<TypePathEntry> depth = ListBuffer.lb();

            // handle annotations associated with dimensions
            for (int i = 0; i < dimAnnosCount; ++i) {
                TypeAnnotationPosition p = new TypeAnnotationPosition();
                p.pos = tree.pos;
                p.onLambda = currentLambda;
                p.type = TargetType.NEW;
                if (i != 0) {
                    depth = depth.append(TypePathEntry.ARRAY);
                    p.location = p.location.appendList(depth.toList());
                }

                setTypeAnnotationPos(tree.dimAnnotations.get(i), p);
            }

            // handle "free" annotations
            // int i = dimAnnosCount == 0 ? 0 : dimAnnosCount - 1;
            // TODO: is depth.size == i here?
            JCExpression elemType = tree.elemtype;
            depth = depth.append(TypePathEntry.ARRAY);
            while (elemType != null) {
                if (elemType.hasTag(JCTree.Tag.ANNOTATED_TYPE)) {
                    JCAnnotatedType at = (JCAnnotatedType)elemType;
                    TypeAnnotationPosition p = new TypeAnnotationPosition();
                    p.type = TargetType.NEW;
                    p.pos = tree.pos;
                    p.onLambda = currentLambda;
                    locateNestedTypes(elemType.type, p);
                    p.location = p.location.prependList(depth.toList());
                    setTypeAnnotationPos(at.annotations, p);
                    elemType = at.underlyingType;
                } else if (elemType.hasTag(JCTree.Tag.TYPEARRAY)) {
                    depth = depth.append(TypePathEntry.ARRAY);
                    elemType = ((JCArrayTypeTree)elemType).elemtype;
                } else if (elemType.hasTag(JCTree.Tag.SELECT)) {
                    elemType = ((JCFieldAccess)elemType).selected;
                } else {
                    break;
                }
            }
            scan(tree.elems);
        }

        private void findPosition(JCTree tree, JCTree frame, List<JCAnnotation> annotations) {
            if (!annotations.isEmpty()) {
                /*
                System.out.println("Finding pos for: " + annotations);
                System.out.println("    tree: " + tree + " kind: " + tree.getKind());
                System.out.println("    frame: " + frame + " kind: " + frame.getKind());
                */
                TypeAnnotationPosition p = new TypeAnnotationPosition();
                p.onLambda = currentLambda;
                resolveFrame(tree, frame, frames.toList(), p);
                setTypeAnnotationPos(annotations, p);
            }
        }

        private static void setTypeAnnotationPos(List<JCAnnotation> annotations,
                TypeAnnotationPosition position) {
            for (JCAnnotation anno : annotations) {
                // attribute might be null during DeferredAttr;
                // we will be back later.
                if (anno.attribute != null) {
                    ((Attribute.TypeCompound) anno.attribute).position = position;
                }
            }
        }

        @Override
        public String toString() {
            return super.toString() + ": sigOnly: " + sigOnly;
        }
    }
}
