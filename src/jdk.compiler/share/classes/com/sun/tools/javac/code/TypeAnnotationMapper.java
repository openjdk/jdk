/*
 * Copyright (c) 2023, Alphabet LLC. All rights reserved.
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

import static com.sun.tools.javac.code.TargetType.CLASS_EXTENDS;
import static com.sun.tools.javac.code.TargetType.CLASS_TYPE_PARAMETER_BOUND;
import static com.sun.tools.javac.code.TargetType.METHOD_FORMAL_PARAMETER;
import static com.sun.tools.javac.code.TargetType.METHOD_RECEIVER;
import static com.sun.tools.javac.code.TargetType.METHOD_RETURN;
import static com.sun.tools.javac.code.TargetType.METHOD_TYPE_PARAMETER_BOUND;
import static com.sun.tools.javac.code.TargetType.THROWS;
import static com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntryKind.TYPE_ARGUMENT;

import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntry;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Contains operations to processing type annotations read from class files. */
public final class TypeAnnotationMapper {

    private TypeAnnotationMapper() {}

    /**
     * Rewrites types in the given symbol to include type annotations.
     *
     * <p>The list of type annotations includes annotations for all types in the signature of the
     * symbol. Associating the annotations with the correct type requires interpreting the JVMS
     * 4.7.20-A target_type to locate the correct type to rewrite, and then interpreting the JVMS
     * 4.7.20.2 type_path to associate the annotation with the correct contained type.
     */
    public static void addTypeAnnotationsToSymbol(Symbol s, List<TypeCompound> attributes) {
        new TypeAnnotationSymbolVisitor(attributes).visit(s, null);
    }

    private static class TypeAnnotationSymbolVisitor
            extends Types.DefaultSymbolVisitor<Void, Void> {

        private final List<TypeCompound> attributes;

        public TypeAnnotationSymbolVisitor(List<TypeCompound> attributes) {
            this.attributes = attributes;
        }

        @Override
        public Void visitClassSymbol(Symbol.ClassSymbol s, Void unused) {
            ClassType t = (ClassType) s.type;
            int i = 0;
            ListBuffer<Type> interfaces = new ListBuffer<>();
            for (Type itf : t.interfaces_field) {
                interfaces.add(addTypeAnnotations(itf, classExtends(i++)));
            }
            t.interfaces_field = interfaces.toList();
            t.supertype_field = addTypeAnnotations(t.supertype_field, classExtends(65535));
            if (t.typarams_field != null) {
                t.typarams_field = rewriteTypeParameters(t.typarams_field, CLASS_TYPE_PARAMETER_BOUND);
            }
            return null;
        }

        @Override
        public Void visitMethodSymbol(Symbol.MethodSymbol s, Void unused) {
            Type t = s.type;
            if (t.hasTag(TypeTag.FORALL)) {
                Type.ForAll fa = (Type.ForAll) t;
                fa.tvars = rewriteTypeParameters(fa.tvars, METHOD_TYPE_PARAMETER_BOUND);
                t = fa.qtype;
            }
            MethodType mt = (MethodType) t;
            ListBuffer<Type> argtypes = new ListBuffer<>();
            int i = 0;
            for (Symbol.VarSymbol param : s.params) {
                param.type = addTypeAnnotations(param.type, methodFormalParameter(i++));
                argtypes.add(param.type);
            }
            mt.argtypes = argtypes.toList();
            ListBuffer<Type> thrown = new ListBuffer<>();
            i = 0;
            for (Type thrownType : mt.thrown) {
                thrown.add(addTypeAnnotations(thrownType, thrownType(i++)));
            }
            mt.thrown = thrown.toList();
            mt.restype = addTypeAnnotations(mt.restype, METHOD_RETURN);
            if (mt.recvtype != null) {
                mt.recvtype = addTypeAnnotations(mt.recvtype, METHOD_RECEIVER);
            }
            return null;
        }

        @Override
        public Void visitVarSymbol(Symbol.VarSymbol s, Void unused) {
            s.type = addTypeAnnotations(s.type, TargetType.FIELD);
            return null;
        }

        @Override
        public Void visitSymbol(Symbol s, Void unused) {
            return null;
        }

        private List<Type> rewriteTypeParameters(List<Type> tvars, TargetType boundType) {
            ListBuffer<Type> tvarbuf = new ListBuffer<>();
            int typeVariableIndex = 0;
            for (Type tvar : tvars) {
                Type bound = tvar.getUpperBound();
                if (bound.isCompound()) {
                    ClassType ct = (ClassType) bound;
                    int boundIndex = 0;
                    if (ct.supertype_field != null) {
                        ct.supertype_field =
                                addTypeAnnotations(
                                        ct.supertype_field,
                                        typeParameterBound(
                                                boundType, typeVariableIndex, boundIndex++));
                    }
                    ListBuffer<Type> itfbuf = new ListBuffer<>();
                    for (Type itf : ct.interfaces_field) {
                        itfbuf.add(
                                addTypeAnnotations(
                                        itf,
                                        typeParameterBound(
                                                boundType, typeVariableIndex, boundIndex++)));
                    }
                    ct.interfaces_field = itfbuf.toList();
                } else {
                    bound =
                            addTypeAnnotations(
                                    bound,
                                    typeParameterBound(
                                            boundType,
                                            typeVariableIndex,
                                            bound.isInterface() ? 1 : 0));
                }
                ((TypeVar) tvar).setUpperBound(bound);
                tvarbuf.add(tvar);
                typeVariableIndex++;
            }
            return tvarbuf.toList();
        }

        private Type addTypeAnnotations(Type type, TargetType targetType) {
            return addTypeAnnotations(type, pos -> pos.type == targetType);
        }

        private Type addTypeAnnotations(Type type, Predicate<TypeAnnotationPosition> filter) {
            Assert.checkNonNull(type);

            // Find type annotations that match the given target type
            ListBuffer<TypeCompound> filtered = new ListBuffer<>();
            for (TypeCompound attribute : this.attributes) {
                if (filter.test(attribute.position)) {
                    filtered.add(attribute);
                }
            }
            if (filtered.isEmpty()) {
                return type;
            }

            // Group the matching annotations by their type path. Each group of annotations will be
            // added to a type at that location.
            Map<List<TypePathEntry>, ListBuffer<TypeCompound>> attributesByPath = new HashMap<>();
            for (TypeCompound attribute : filtered.toList()) {
                attributesByPath
                        .computeIfAbsent(attribute.position.location, k -> new ListBuffer<>())
                        .add(attribute);
            }

            // Search the structure of the type to find the contained types at each type path
            Map<Type, List<TypeCompound>> attributesByType = new HashMap<>();
            new TypeAnnotationLocator(attributesByPath, attributesByType).visit(type, List.nil());

            // Rewrite the type and add the annotations
            type = new TypeAnnotationTypeMapping(attributesByType).visit(type, null);
            Assert.check(attributesByType.isEmpty(), "Failed to apply annotations to types");

            return type;
        }

        private static Predicate<TypeAnnotationPosition> typeParameterBound(
                TargetType targetType, int parameterIndex, int boundIndex) {
            return pos ->
                    pos.type == targetType
                            && pos.parameter_index == parameterIndex
                            && pos.bound_index == boundIndex;
        }

        private static Predicate<TypeAnnotationPosition> methodFormalParameter(int index) {
            return pos -> pos.type == METHOD_FORMAL_PARAMETER && pos.parameter_index == index;
        }

        private static Predicate<TypeAnnotationPosition> thrownType(int index) {
            return pos -> pos.type == THROWS && pos.type_index == index;
        }

        private static Predicate<TypeAnnotationPosition> classExtends(int index) {
            return pos -> pos.type == CLASS_EXTENDS && pos.type_index == index;
        }
    }

    /**
     * Visit all contained types, assembling a type path to represent the current location, and
     * record the types at each type path that need to be annotated.
     */
    private static class TypeAnnotationLocator
            extends Types.DefaultTypeVisitor<Void, List<TypePathEntry>> {
        private final Map<List<TypePathEntry>, ListBuffer<TypeCompound>> attributesByPath;
        private final Map<Type, List<TypeCompound>> attributesByType;

        public TypeAnnotationLocator(
                Map<List<TypePathEntry>, ListBuffer<TypeCompound>> attributesByPath,
                Map<Type, List<TypeCompound>> attributesByType) {
            this.attributesByPath = attributesByPath;
            this.attributesByType = attributesByType;
        }

        @Override
        public Void visitClassType(ClassType t, List<TypePathEntry> path) {
            // As described in JVMS 4.7.20.2, type annotations on nested types are located with
            // 'left-to-right' steps starting on 'the outermost part of the type for which a type
            // annotation is admissible'. So the current path represents the outermost containing
            // type of the type being visited, and we add type path steps for every contained nested
            // type.
            List<ClassType> enclosing = List.nil();
            for (Type curr = t;
                    curr != null && curr != Type.noType;
                    curr = curr.getEnclosingType()) {
                enclosing = enclosing.prepend((ClassType) curr);
            }
            for (ClassType te : enclosing) {
                if (te.typarams_field != null) {
                    int i = 0;
                    for (Type typaram : te.typarams_field) {
                        visit(typaram, path.append(new TypePathEntry(TYPE_ARGUMENT, i++)));
                    }
                }
                visitType(te, path);
                path = path.append(TypePathEntry.INNER_TYPE);
            }
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType t, List<TypePathEntry> path) {
            visit(t.type, path.append(TypePathEntry.WILDCARD));
            return super.visitWildcardType(t, path);
        }

        @Override
        public Void visitArrayType(ArrayType t, List<TypePathEntry> path) {
            visit(t.elemtype, path.append(TypePathEntry.ARRAY));
            return super.visitArrayType(t, path);
        }

        @Override
        public Void visitType(Type t, List<TypePathEntry> path) {
            ListBuffer<TypeCompound> attributes = attributesByPath.remove(path);
            if (attributes != null) {
                attributesByType.put(t, attributes.toList());
            }
            return null;
        }
    }

    /** A type mapping that rewrites the type to include type annotations. */
    private static class TypeAnnotationTypeMapping extends Type.StructuralTypeMapping<Void> {

        private final Map<Type, List<TypeCompound>> attributesByType;

        public TypeAnnotationTypeMapping(Map<Type, List<TypeCompound>> attributesByType) {
            this.attributesByType = attributesByType;
        }

        private <T extends Type> Type reannotate(T t, BiFunction<T, Void, Type> f) {
            // We're relying on object identify of Type instances to record where the annotations need to be added,
            // so we have to retrieve the annotations for each type before rewriting it, and then add them after
            // it's contained types have been rewritten.
            List<TypeCompound> attributes = attributesByType.remove(t);
            Type mapped = f.apply(t, null);
            if (attributes == null) {
                return mapped;
            }
            // Runtime-visible and -invisible annotations are completed separately, so if the same type has annotations
            // from both it will get annotated twice.
            TypeMetadata.Annotations existing = mapped.getMetadata(TypeMetadata.Annotations.class);
            if (existing != null) {
                existing.annotationBuffer().addAll(attributes);
                return mapped;
            }
            return mapped.addMetadata(new TypeMetadata.Annotations(attributes));
        }

        @Override
        public Type visitClassType(ClassType t, Void unused) {
            return reannotate(t, super::visitClassType);
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void unused) {
            return reannotate(t, super::visitWildcardType);
        }

        @Override
        public Type visitArrayType(ArrayType t, Void unused) {
            return reannotate(t, super::visitArrayType);
        }

        @Override
        public Type visitType(Type t, Void unused) {
            return reannotate(t, (x, u) -> x);
        }
    }
}
