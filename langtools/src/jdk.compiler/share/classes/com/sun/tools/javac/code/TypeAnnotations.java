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

import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Attribute.TypeCompound;
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
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Annotate.Worker;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

/**
 * Contains operations specific to processing type annotations.
 * This class has two functions:
 * separate declaration from type annotations and insert the type
 * annotations to their types;
 * and determine the TypeAnnotationPositions for all type annotations.
 */
public class TypeAnnotations {
    protected static final Context.Key<TypeAnnotations> typeAnnosKey = new Context.Key<>();

    public static TypeAnnotations instance(Context context) {
        TypeAnnotations instance = context.get(typeAnnosKey);
        if (instance == null)
            instance = new TypeAnnotations(context);
        return instance;
    }

    final Log log;
    final Names names;
    final Symtab syms;
    final Annotate annotate;
    final Attr attr;

    protected TypeAnnotations(Context context) {
        context.put(typeAnnosKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        annotate = Annotate.instance(context);
        attr = Attr.instance(context);
        Options options = Options.instance(context);
    }

    /**
     * Separate type annotations from declaration annotations and
     * determine the correct positions for type annotations.
     * This version only visits types in signatures and should be
     * called from MemberEnter.
     * The method takes the Annotate object as parameter and
     * adds an Annotate.Worker to the correct Annotate queue for
     * later processing.
     */
    public void organizeTypeAnnotationsSignatures(final Env<AttrContext> env, final JCClassDecl tree) {
        annotate.afterRepeated( new Worker() {
            @Override
            public void run() {
                JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);

                try {
                    new TypeAnnotationPositions(true).scan(tree);
                } finally {
                    log.useSource(oldSource);
                }
            }
        } );
    }

    public void validateTypeAnnotationsSignatures(final Env<AttrContext> env, final JCClassDecl tree) {
        annotate.validate(new Worker() { //validate annotations
            @Override
            public void run() {
                JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);

                try {
                    attr.validateTypeAnnotations(tree, true);
                } finally {
                    log.useSource(oldSource);
                }
            }
        } );
    }

    /**
     * This version only visits types in bodies, that is, field initializers,
     * top-level blocks, and method bodies, and should be called from Attr.
     */
    public void organizeTypeAnnotationsBodies(JCClassDecl tree) {
        new TypeAnnotationPositions(false).scan(tree);
    }

    public enum AnnotationType { DECLARATION, TYPE, BOTH }

    /**
     * Determine whether an annotation is a declaration annotation,
     * a type annotation, or both.
     */
    public AnnotationType annotationType(Attribute.Compound a, Symbol s) {
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


    private class TypeAnnotationPositions extends TreeScanner {

        private final boolean sigOnly;

        TypeAnnotationPositions(boolean sigOnly) {
            this.sigOnly = sigOnly;
        }

        /*
         * When traversing the AST we keep the "frames" of visited
         * trees in order to determine the position of annotations.
         */
        private ListBuffer<JCTree> frames = new ListBuffer<>();

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
            List<Attribute.Compound> annotations = sym.getRawAttributes();
            ListBuffer<Attribute.Compound> declAnnos = new ListBuffer<>();
            ListBuffer<Attribute.TypeCompound> typeAnnos = new ListBuffer<>();
            ListBuffer<Attribute.TypeCompound> onlyTypeAnnos = new ListBuffer<>();

            for (Attribute.Compound a : annotations) {
                switch (annotationType(a, sym)) {
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
                    // Also keep track which annotations are only type annotations
                    onlyTypeAnnos.append(ta);
                    break;
                }
                }
            }

            sym.resetAnnotations();
            sym.setDeclarationAttributes(declAnnos.toList());

            if (typeAnnos.isEmpty()) {
                return;
            }

            List<Attribute.TypeCompound> typeAnnotations = typeAnnos.toList();

            if (type == null) {
                // When type is null, put the type annotations to the symbol.
                // This is used for constructor return annotations, for which
                // we use the type of the enclosing class.
                type = sym.getEnclosingElement().asType();

                // Declaration annotations are always allowed on constructor returns.
                // Therefore, use typeAnnotations instead of onlyTypeAnnos.
                type = typeWithAnnotations(typetree, type, typeAnnotations, typeAnnotations);
                // Note that we don't use the result, the call to
                // typeWithAnnotations side-effects the type annotation positions.
                // This is important for constructors of nested classes.
                sym.appendUniqueTypeAttributes(typeAnnotations);
                return;
            }

            // type is non-null and annotations are added to that type
            type = typeWithAnnotations(typetree, type, typeAnnotations, onlyTypeAnnos.toList());

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
                    ListBuffer<Type> newArgs = new ListBuffer<>();
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

            sym.appendUniqueTypeAttributes(typeAnnotations);

            if (sym.getKind() == ElementKind.PARAMETER ||
                sym.getKind() == ElementKind.LOCAL_VARIABLE ||
                sym.getKind() == ElementKind.RESOURCE_VARIABLE ||
                sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                // Make sure all type annotations from the symbol are also
                // on the owner.
                sym.owner.appendUniqueTypeAttributes(sym.getRawTypeAttributes());
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
        private Type typeWithAnnotations(final JCTree typetree, final Type type,
                final List<Attribute.TypeCompound> annotations,
                final List<Attribute.TypeCompound> onlyTypeAnnotations) {
            //System.err.printf("typeWithAnnotations(typetree: %s, type: %s, annotations: %s, onlyTypeAnnotations: %s)%n",
            //         typetree, type, annotations, onlyTypeAnnotations);
            if (annotations.isEmpty()) {
                return type;
            }
            if (type.hasTag(TypeTag.ARRAY)) {
                Type.ArrayType arType = (Type.ArrayType) type;
                Type.ArrayType tomodify = new Type.ArrayType(null, arType.tsym,
                                                             Type.noAnnotations);
                Type toreturn;
                if (type.isAnnotated()) {
                    toreturn = tomodify.annotatedType(type.getAnnotationMirrors());
                } else {
                    toreturn = tomodify;
                }

                JCArrayTypeTree arTree = arrayTypeTree(typetree);

                ListBuffer<TypePathEntry> depth = new ListBuffer<>();
                depth = depth.append(TypePathEntry.ARRAY);
                while (arType.elemtype.hasTag(TypeTag.ARRAY)) {
                    if (arType.elemtype.isAnnotated()) {
                        Type aelemtype = arType.elemtype;
                        arType = (Type.ArrayType) aelemtype;
                        ArrayType prevToMod = tomodify;
                        tomodify = new Type.ArrayType(null, arType.tsym,
                                                      Type.noAnnotations);
                        prevToMod.elemtype = tomodify.annotatedType(arType.elemtype.getAnnotationMirrors());
                    } else {
                        arType = (Type.ArrayType) arType.elemtype;
                        tomodify.elemtype = new Type.ArrayType(null, arType.tsym,
                                                               Type.noAnnotations);
                        tomodify = (Type.ArrayType) tomodify.elemtype;
                    }
                    arTree = arrayTypeTree(arTree.elemtype);
                    depth = depth.append(TypePathEntry.ARRAY);
                }
                Type arelemType = typeWithAnnotations(arTree.elemtype, arType.elemtype, annotations, onlyTypeAnnotations);
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
                Type res = typeWithAnnotations(fst, fst.type, annotations, onlyTypeAnnotations);
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
                        enclTy.hasTag(TypeTag.NONE)) {
                    switch (onlyTypeAnnotations.size()) {
                    case 0:
                        // Don't issue an error if all type annotations are
                        // also declaration annotations.
                        // If the annotations are also declaration annotations, they are
                        // illegal as type annotations but might be legal as declaration annotations.
                        // The normal declaration annotation checks make sure that the use is valid.
                        break;
                    case 1:
                        log.error(typetree.pos(), "cant.type.annotate.scoping.1",
                                onlyTypeAnnotations);
                        break;
                    default:
                        log.error(typetree.pos(), "cant.type.annotate.scoping",
                                onlyTypeAnnotations);
                    }
                    return type;
                }

                // At this point we have visited the part of the nested
                // type that is written in the source code.
                // Now count from here to the actual top-level class to determine
                // the correct nesting.

                // The genericLocation for the annotation.
                ListBuffer<TypePathEntry> depth = new ListBuffer<>();

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

        private JCArrayTypeTree arrayTypeTree(JCTree typetree) {
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
        private Type typeWithAnnotations(final Type type,
                final Type stopAt,
                final List<Attribute.TypeCompound> annotations) {
            //System.err.println("typeWithAnnotations " + type + " " + annotations + " stopAt " + stopAt);
            Visitor<Type, List<TypeCompound>> visitor =
                    new Type.Visitor<Type, List<Attribute.TypeCompound>>() {
                @Override
                public Type visitClassType(ClassType t, List<TypeCompound> s) {
                    // assert that t.constValue() == null?
                    if (t == stopAt ||
                        t.getEnclosingType() == Type.noType) {
                        return t.annotatedType(s);
                    } else {
                        ClassType ret = new ClassType(t.getEnclosingType().accept(this, s),
                                                      t.typarams_field, t.tsym,
                                                      t.getAnnotationMirrors());
                        ret.all_interfaces_field = t.all_interfaces_field;
                        ret.allparams_field = t.allparams_field;
                        ret.interfaces_field = t.interfaces_field;
                        ret.rank_field = t.rank_field;
                        ret.supertype_field = t.supertype_field;
                        return ret;
                    }
                }

                @Override
                public Type visitWildcardType(WildcardType t, List<TypeCompound> s) {
                    return t.annotatedType(s);
                }

                @Override
                public Type visitArrayType(ArrayType t, List<TypeCompound> s) {
                    ArrayType ret = new ArrayType(t.elemtype.accept(this, s), t.tsym,
                                                  t.getAnnotationMirrors());
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
                    return t.annotatedType(s);
                }

                @Override
                public Type visitCapturedType(CapturedType t, List<TypeCompound> s) {
                    return t.annotatedType(s);
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
                    return t.annotatedType(s);
                }

                @Override
                public Type visitType(Type t, List<TypeCompound> s) {
                    return t.annotatedType(s);
                }
            };

            return type.accept(visitor, annotations);
        }

        private Attribute.TypeCompound toTypeCompound(Attribute.Compound a, TypeAnnotationPosition p) {
            // It is safe to alias the position.
            return new Attribute.TypeCompound(a, p);
        }


        /* This is the beginning of the second part of organizing
         * type annotations: determine the type annotation positions.
         */

        // This method is considered deprecated, and will be removed
        // in the near future.  Don't use it for anything new.
        private TypeAnnotationPosition
            resolveFrame(JCTree tree,
                         JCTree frame,
                         List<JCTree> path,
                         JCLambda currentLambda,
                         int outer_type_index,
                         ListBuffer<TypePathEntry> location) {
            /*
            System.out.println("Resolving tree: " + tree + " kind: " + tree.getKind());
            System.out.println("    Framing tree: " + frame + " kind: " + frame.getKind());
            */

            // Note that p.offset is set in
            // com.sun.tools.javac.jvm.Gen.setTypeAnnotationPositions(int)

            switch (frame.getKind()) {
                case TYPE_CAST:
                    return TypeAnnotationPosition.typeCast(location.toList(),
                                                           currentLambda,
                                                           outer_type_index,
                                                           frame.pos);

                case INSTANCE_OF:
                    return TypeAnnotationPosition.instanceOf(location.toList(),
                                                             currentLambda,
                                                             frame.pos);

                case NEW_CLASS:
                    final JCNewClass frameNewClass = (JCNewClass) frame;
                    if (frameNewClass.def != null) {
                        // Special handling for anonymous class instantiations
                        final JCClassDecl frameClassDecl = frameNewClass.def;
                        if (frameClassDecl.extending == tree) {
                            return TypeAnnotationPosition
                                .classExtends(location.toList(), currentLambda,
                                              frame.pos);
                        } else if (frameClassDecl.implementing.contains(tree)) {
                            final int type_index =
                                frameClassDecl.implementing.indexOf(tree);
                            return TypeAnnotationPosition
                                .classExtends(location.toList(), currentLambda,
                                              type_index, frame.pos);
                        } else {
                            // In contrast to CLASS below, typarams cannot occur here.
                            throw new AssertionError("Could not determine position of tree " + tree +
                                                     " within frame " + frame);
                        }
                    } else if (frameNewClass.typeargs.contains(tree)) {
                        final int type_index =
                            frameNewClass.typeargs.indexOf(tree);
                        return TypeAnnotationPosition
                            .constructorInvocationTypeArg(location.toList(),
                                                          currentLambda,
                                                          type_index,
                                                          frame.pos);
                    } else {
                        return TypeAnnotationPosition
                            .newObj(location.toList(), currentLambda,
                                    frame.pos);
                    }

                case NEW_ARRAY:
                    return TypeAnnotationPosition
                        .newObj(location.toList(), currentLambda, frame.pos);

                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    if (((JCClassDecl)frame).extending == tree) {
                        return TypeAnnotationPosition
                            .classExtends(location.toList(), currentLambda,
                                          frame.pos);
                    } else if (((JCClassDecl)frame).implementing.contains(tree)) {
                        final int type_index =
                            ((JCClassDecl)frame).implementing.indexOf(tree);
                        return TypeAnnotationPosition
                            .classExtends(location.toList(), currentLambda,
                                          type_index, frame.pos);
                    } else if (((JCClassDecl)frame).typarams.contains(tree)) {
                        final int parameter_index =
                            ((JCClassDecl)frame).typarams.indexOf(tree);
                        return TypeAnnotationPosition
                            .typeParameter(location.toList(), currentLambda,
                                           parameter_index, frame.pos);
                    } else {
                        throw new AssertionError("Could not determine position of tree " +
                                                 tree + " within frame " + frame);
                    }

                case METHOD: {
                    final JCMethodDecl frameMethod = (JCMethodDecl) frame;
                    if (frameMethod.thrown.contains(tree)) {
                        final int type_index = frameMethod.thrown.indexOf(tree);
                        return TypeAnnotationPosition
                            .methodThrows(location.toList(), currentLambda,
                                          type_index, frame.pos);
                    } else if (frameMethod.restype == tree) {
                        return TypeAnnotationPosition
                            .methodReturn(location.toList(), currentLambda,
                                          frame.pos);
                    } else if (frameMethod.typarams.contains(tree)) {
                        final int parameter_index =
                            frameMethod.typarams.indexOf(tree);
                        return TypeAnnotationPosition
                            .methodTypeParameter(location.toList(),
                                                 currentLambda,
                                                 parameter_index, frame.pos);
                    } else {
                        throw new AssertionError("Could not determine position of tree " + tree +
                                                 " within frame " + frame);
                    }
                }

                case PARAMETERIZED_TYPE: {
                    List<JCTree> newPath = path.tail;

                    if (((JCTypeApply)frame).clazz == tree) {
                        // generic: RAW; noop
                    } else if (((JCTypeApply)frame).arguments.contains(tree)) {
                        JCTypeApply taframe = (JCTypeApply) frame;
                        int arg = taframe.arguments.indexOf(tree);
                        location = location.prepend(
                            new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT,
                                              arg));

                        Type typeToUse;
                        if (newPath.tail != null &&
                            newPath.tail.head.hasTag(Tag.NEWCLASS)) {
                            // If we are within an anonymous class
                            // instantiation, use its type, because it
                            // contains a correctly nested type.
                            typeToUse = newPath.tail.head.type;
                        } else {
                            typeToUse = taframe.type;
                        }

                        location = locateNestedTypes(typeToUse, location);
                    } else {
                        throw new AssertionError("Could not determine type argument position of tree " + tree +
                                                 " within frame " + frame);
                    }

                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index, location);
                }

                case MEMBER_REFERENCE: {
                    JCMemberReference mrframe = (JCMemberReference) frame;

                    if (mrframe.expr == tree) {
                        switch (mrframe.mode) {
                        case INVOKE:
                            return TypeAnnotationPosition
                                .methodRef(location.toList(), currentLambda,
                                           frame.pos);
                        case NEW:
                            return TypeAnnotationPosition
                                .constructorRef(location.toList(),
                                                currentLambda,
                                                frame.pos);
                        default:
                            throw new AssertionError("Unknown method reference mode " + mrframe.mode +
                                                     " for tree " + tree + " within frame " + frame);
                        }
                    } else if (mrframe.typeargs != null &&
                            mrframe.typeargs.contains(tree)) {
                        final int type_index = mrframe.typeargs.indexOf(tree);
                        switch (mrframe.mode) {
                        case INVOKE:
                            return TypeAnnotationPosition
                                .methodRefTypeArg(location.toList(),
                                                  currentLambda,
                                                  type_index, frame.pos);
                        case NEW:
                            return TypeAnnotationPosition
                                .constructorRefTypeArg(location.toList(),
                                                       currentLambda,
                                                       type_index, frame.pos);
                        default:
                            throw new AssertionError("Unknown method reference mode " + mrframe.mode +
                                                   " for tree " + tree + " within frame " + frame);
                        }
                    } else {
                        throw new AssertionError("Could not determine type argument position of tree " + tree +
                                               " within frame " + frame);
                    }
                }

                case ARRAY_TYPE: {
                    location = location.prepend(TypePathEntry.ARRAY);
                    List<JCTree> newPath = path.tail;
                    while (true) {
                        JCTree npHead = newPath.tail.head;
                        if (npHead.hasTag(JCTree.Tag.TYPEARRAY)) {
                            newPath = newPath.tail;
                            location = location.prepend(TypePathEntry.ARRAY);
                        } else if (npHead.hasTag(JCTree.Tag.ANNOTATED_TYPE)) {
                            newPath = newPath.tail;
                        } else {
                            break;
                        }
                    }
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index, location);
                }

                case TYPE_PARAMETER:
                    if (path.tail.tail.head.hasTag(JCTree.Tag.CLASSDEF)) {
                        final JCClassDecl clazz =
                            (JCClassDecl)path.tail.tail.head;
                        final int parameter_index =
                            clazz.typarams.indexOf(path.tail.head);
                        final int bound_index =
                            ((JCTypeParameter)frame).bounds.get(0)
                            .type.isInterface() ?
                            ((JCTypeParameter)frame).bounds.indexOf(tree) + 1:
                            ((JCTypeParameter)frame).bounds.indexOf(tree);
                        return TypeAnnotationPosition
                            .typeParameterBound(location.toList(),
                                                currentLambda,
                                                parameter_index, bound_index,
                                                frame.pos);
                    } else if (path.tail.tail.head.hasTag(JCTree.Tag.METHODDEF)) {
                        final JCMethodDecl method =
                            (JCMethodDecl)path.tail.tail.head;
                        final int parameter_index =
                            method.typarams.indexOf(path.tail.head);
                        final int bound_index =
                            ((JCTypeParameter)frame).bounds.get(0)
                            .type.isInterface() ?
                            ((JCTypeParameter)frame).bounds.indexOf(tree) + 1:
                            ((JCTypeParameter)frame).bounds.indexOf(tree);
                        return TypeAnnotationPosition
                            .methodTypeParameterBound(location.toList(),
                                                      currentLambda,
                                                      parameter_index,
                                                      bound_index,
                                                      frame.pos);
                    } else {
                        throw new AssertionError("Could not determine position of tree " + tree +
                                                 " within frame " + frame);
                    }

                case VARIABLE:
                    VarSymbol v = ((JCVariableDecl)frame).sym;
                    if (v.getKind() != ElementKind.FIELD) {
                        v.owner.appendUniqueTypeAttributes(v.getRawTypeAttributes());
                    }
                    switch (v.getKind()) {
                        case LOCAL_VARIABLE:
                            return TypeAnnotationPosition
                                .localVariable(location.toList(), currentLambda,
                                               frame.pos);
                        case FIELD:
                            return TypeAnnotationPosition.field(location.toList(),
                                                                currentLambda,
                                                                frame.pos);
                        case PARAMETER:
                            if (v.getQualifiedName().equals(names._this)) {
                                return TypeAnnotationPosition
                                    .methodReceiver(location.toList(),
                                                    currentLambda,
                                                    frame.pos);
                            } else {
                                final int parameter_index =
                                    methodParamIndex(path, frame);
                                return TypeAnnotationPosition
                                    .methodParameter(location.toList(),
                                                     currentLambda,
                                                     parameter_index,
                                                     frame.pos);
                            }
                        case EXCEPTION_PARAMETER:
                            return TypeAnnotationPosition
                                .exceptionParameter(location.toList(),
                                                    currentLambda,
                                                    frame.pos);
                        case RESOURCE_VARIABLE:
                            return TypeAnnotationPosition
                                .resourceVariable(location.toList(),
                                                  currentLambda,
                                                  frame.pos);
                        default:
                            throw new AssertionError("Found unexpected type annotation for variable: " + v + " with kind: " + v.getKind());
                    }

                case ANNOTATED_TYPE: {
                    if (frame == tree) {
                        // This is only true for the first annotated type we see.
                        // For any other annotated types along the path, we do
                        // not care about inner types.
                        JCAnnotatedType atypetree = (JCAnnotatedType) frame;
                        final Type utype = atypetree.underlyingType.type;
                        Assert.checkNonNull(utype);
                        Symbol tsym = utype.tsym;
                        if (tsym.getKind().equals(ElementKind.TYPE_PARAMETER) ||
                                utype.getKind().equals(TypeKind.WILDCARD) ||
                                utype.getKind().equals(TypeKind.ARRAY)) {
                            // Type parameters, wildcards, and arrays have the declaring
                            // class/method as enclosing elements.
                            // There is actually nothing to do for them.
                        } else {
                            location = locateNestedTypes(utype, location);
                        }
                    }
                    List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index, location);
                }

                case UNION_TYPE: {
                    List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index, location);
                }

                case INTERSECTION_TYPE: {
                    JCTypeIntersection isect = (JCTypeIntersection)frame;
                    final List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        isect.bounds.indexOf(tree), location);
                }

                case METHOD_INVOCATION: {
                    JCMethodInvocation invocation = (JCMethodInvocation)frame;
                    if (!invocation.typeargs.contains(tree)) {
                        throw new AssertionError("{" + tree + "} is not an argument in the invocation: " + invocation);
                    }
                    MethodSymbol exsym = (MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    final int type_index = invocation.typeargs.indexOf(tree);
                    if (exsym == null) {
                        throw new AssertionError("could not determine symbol for {" + invocation + "}");
                    } else if (exsym.isConstructor()) {
                        return TypeAnnotationPosition
                            .constructorInvocationTypeArg(location.toList(),
                                                          currentLambda,
                                                          type_index,
                                                          invocation.pos);
                    } else {
                        return TypeAnnotationPosition
                            .methodInvocationTypeArg(location.toList(),
                                                     currentLambda,
                                                     type_index,
                                                     invocation.pos);
                    }
                }

                case EXTENDS_WILDCARD:
                case SUPER_WILDCARD: {
                    // Annotations in wildcard bounds
                    final List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index,
                                        location.prepend(TypePathEntry.WILDCARD));
                }

                case MEMBER_SELECT: {
                    final List<JCTree> newPath = path.tail;
                    return resolveFrame(newPath.head, newPath.tail.head,
                                        newPath, currentLambda,
                                        outer_type_index, location);
                }

                default:
                    throw new AssertionError("Unresolved frame: " + frame +
                                             " of kind: " + frame.getKind() +
                                             "\n    Looking for tree: " + tree);
            }
        }

        private ListBuffer<TypePathEntry>
            locateNestedTypes(Type type,
                              ListBuffer<TypePathEntry> depth) {
            Type encl = type.getEnclosingType();
            while (encl != null &&
                    encl.getKind() != TypeKind.NONE &&
                    encl.getKind() != TypeKind.ERROR) {
                depth = depth.prepend(TypePathEntry.INNER_TYPE);
                encl = encl.getEnclosingType();
            }
            return depth;
        }

        private int methodParamIndex(List<JCTree> path, JCTree param) {
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
                Assert.error("Visiting tree node before memberEnter");
            }
            if (sigOnly) {
                if (!tree.mods.annotations.isEmpty()) {
                    if (tree.sym.isConstructor()) {
                        final TypeAnnotationPosition pos =
                            TypeAnnotationPosition.methodReturn(tree.pos);
                        // Use null to mark that the annotations go
                        // with the symbol.
                        separateAnnotationsKinds(tree, null, tree.sym, pos);
                    } else {
                        final TypeAnnotationPosition pos =
                            TypeAnnotationPosition.methodReturn(tree.restype.pos);
                        separateAnnotationsKinds(tree.restype,
                                                 tree.sym.type.getReturnType(),
                                                 tree.sym, pos);
                    }
                }
                if (tree.recvparam != null && tree.recvparam.sym != null &&
                        !tree.recvparam.mods.annotations.isEmpty()) {
                    // Nothing to do for separateAnnotationsKinds if
                    // there are no annotations of either kind.
                    // TODO: make sure there are no declaration annotations.
                    final TypeAnnotationPosition pos =
                        TypeAnnotationPosition.methodReceiver(tree.recvparam.vartype.pos);
                    separateAnnotationsKinds(tree.recvparam.vartype,
                                             tree.recvparam.sym.type,
                                             tree.recvparam.sym, pos);
                }
                int i = 0;
                for (JCVariableDecl param : tree.params) {
                    if (!param.mods.annotations.isEmpty()) {
                        // Nothing to do for separateAnnotationsKinds if
                        // there are no annotations of either kind.
                        final TypeAnnotationPosition pos =
                            TypeAnnotationPosition.methodParameter(i, param.vartype.pos);
                        separateAnnotationsKinds(param.vartype,
                                                 param.sym.type,
                                                 param.sym, pos);
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
                        final TypeAnnotationPosition pos =
                            TypeAnnotationPosition.methodParameter(tree, i,
                                                                   param.vartype.pos);
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
                Assert.error("Visiting tree node before memberEnter");
            } else if (tree.sym.getKind() == ElementKind.PARAMETER) {
                // Parameters are handled in visitMethodDef or visitLambda.
            } else if (tree.sym.getKind() == ElementKind.FIELD) {
                if (sigOnly) {
                    TypeAnnotationPosition pos =
                        TypeAnnotationPosition.field(tree.pos);
                    separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
                }
            } else if (tree.sym.getKind() == ElementKind.LOCAL_VARIABLE) {
                final TypeAnnotationPosition pos =
                    TypeAnnotationPosition.localVariable(currentLambda,
                                                         tree.pos);
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                final TypeAnnotationPosition pos =
                    TypeAnnotationPosition.exceptionParameter(currentLambda,
                                                              tree.pos);
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.RESOURCE_VARIABLE) {
                final TypeAnnotationPosition pos =
                    TypeAnnotationPosition.resourceVariable(currentLambda,
                                                            tree.pos);
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

        private void copyNewClassAnnotationsToOwner(JCNewClass tree) {
            Symbol sym = tree.def.sym;
            final TypeAnnotationPosition pos =
                TypeAnnotationPosition.newObj(tree.pos);
            ListBuffer<Attribute.TypeCompound> newattrs = new ListBuffer<>();

            for (Attribute.TypeCompound old : sym.getRawTypeAttributes()) {
                newattrs.append(new Attribute.TypeCompound(old.type, old.values,
                                                           pos));
            }

            sym.owner.appendUniqueTypeAttributes(newattrs.toList());
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (tree.def != null &&
                    !tree.def.mods.annotations.isEmpty()) {
                JCClassDecl classdecl = tree.def;
                TypeAnnotationPosition pos;

                if (classdecl.extending == tree.clazz) {
                    pos = TypeAnnotationPosition.classExtends(tree.pos);
                } else if (classdecl.implementing.contains(tree.clazz)) {
                    final int index = classdecl.implementing.indexOf(tree.clazz);
                    pos = TypeAnnotationPosition.classExtends(index, tree.pos);
                } else {
                    // In contrast to CLASS elsewhere, typarams cannot occur here.
                    throw new AssertionError("Could not determine position of tree " + tree);
                }
                Type before = classdecl.sym.type;
                separateAnnotationsKinds(classdecl, tree.clazz.type, classdecl.sym, pos);
                copyNewClassAnnotationsToOwner(tree);
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
            ListBuffer<TypePathEntry> depth = new ListBuffer<>();

            // handle annotations associated with dimensions
            for (int i = 0; i < dimAnnosCount; ++i) {
                ListBuffer<TypePathEntry> location =
                    new ListBuffer<TypePathEntry>();
                if (i != 0) {
                    depth = depth.append(TypePathEntry.ARRAY);
                    location = location.appendList(depth.toList());
                }
                final TypeAnnotationPosition p =
                    TypeAnnotationPosition.newObj(location.toList(),
                                                  currentLambda,
                                                  tree.pos);

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
                    final ListBuffer<TypePathEntry> locationbuf =
                        locateNestedTypes(elemType.type,
                                          new ListBuffer<TypePathEntry>());
                    final List<TypePathEntry> location =
                        locationbuf.toList().prependList(depth.toList());
                    final TypeAnnotationPosition p =
                        TypeAnnotationPosition.newObj(location, currentLambda,
                                                      tree.pos);
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
                System.err.println("Finding pos for: " + annotations);
                System.err.println("    tree: " + tree + " kind: " + tree.getKind());
                System.err.println("    frame: " + frame + " kind: " + frame.getKind());
                */
                final TypeAnnotationPosition p =
                    resolveFrame(tree, frame, frames.toList(), currentLambda, 0,
                                 new ListBuffer<TypePathEntry>());
                setTypeAnnotationPos(annotations, p);
            }
        }

        private void setTypeAnnotationPos(List<JCAnnotation> annotations,
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
