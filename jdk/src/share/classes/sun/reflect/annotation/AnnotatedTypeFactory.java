/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.annotation;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static sun.reflect.annotation.TypeAnnotation.*;

public class AnnotatedTypeFactory {
    /**
     * Create an AnnotatedType.
     *
     * @param type the type this AnnotatedType corresponds to
     * @param currentLoc the location this AnnotatedType corresponds to
     * @param actualTypeAnnos the type annotations this AnnotatedType has
     * @param allOnSameTarget all type annotation on the same TypeAnnotationTarget
     *                          as the AnnotatedType being built
     * @param decl the declaration having the type use this AnnotatedType
     *                          corresponds to
     */
    public static AnnotatedType buildAnnotatedType(Type type,
                                                   LocationInfo currentLoc,
                                                   TypeAnnotation[] actualTypeAnnos,
                                                   TypeAnnotation[] allOnSameTarget,
                                                   AnnotatedElement decl) {
        if (type == null) {
            return EMPTY_ANNOTATED_TYPE;
        }
        if (isArray(type))
            return new AnnotatedArrayTypeImpl(type,
                    currentLoc,
                    actualTypeAnnos,
                    allOnSameTarget,
                    decl);
        if (type instanceof Class) {
            return new AnnotatedTypeBaseImpl(type,
                    addNesting(type, currentLoc),
                    actualTypeAnnos,
                    allOnSameTarget,
                    decl);
        } else if (type instanceof TypeVariable) {
            return new AnnotatedTypeVariableImpl((TypeVariable)type,
                    currentLoc,
                    actualTypeAnnos,
                    allOnSameTarget,
                    decl);
        } else if (type instanceof ParameterizedType) {
            return new AnnotatedParameterizedTypeImpl((ParameterizedType)type,
                    addNesting(type, currentLoc),
                    actualTypeAnnos,
                    allOnSameTarget,
                    decl);
        } else if (type instanceof WildcardType) {
            return new AnnotatedWildcardTypeImpl((WildcardType) type,
                    currentLoc,
                    actualTypeAnnos,
                    allOnSameTarget,
                    decl);
        }
        throw new AssertionError("Unknown instance of Type: " + type + "\nThis should not happen.");
    }

    private static LocationInfo addNesting(Type type, LocationInfo addTo) {
        if (isArray(type))
            return addTo;
        if (type instanceof Class) {
            Class<?> clz = (Class)type;
            if (clz.getEnclosingClass() == null)
                return addTo;
            return addNesting(clz.getEnclosingClass(), addTo.pushInner());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType)type;
            if (t.getOwnerType() == null)
                return addTo;
            return addNesting(t.getOwnerType(), addTo.pushInner());
        }
        return addTo;
    }

    private static boolean isArray(Type t) {
        if (t instanceof Class) {
            Class<?> c = (Class)t;
            if (c.isArray())
                return true;
        } else if (t instanceof GenericArrayType) {
            return true;
        }
        return false;
    }

    static final AnnotatedType EMPTY_ANNOTATED_TYPE = new AnnotatedTypeBaseImpl(null, LocationInfo.BASE_LOCATION,
                                                            new TypeAnnotation[0], new TypeAnnotation[0], null);
    static final AnnotatedType[] EMPTY_ANNOTATED_TYPE_ARRAY = new AnnotatedType[0];

    private static class AnnotatedTypeBaseImpl implements AnnotatedType {
        private final Type type;
        private final AnnotatedElement decl;
        private final LocationInfo location;
        private final TypeAnnotation[] allOnSameTargetTypeAnnotations;
        private final Map<Class <? extends Annotation>, Annotation> annotations;

        AnnotatedTypeBaseImpl(Type type, LocationInfo location,
                TypeAnnotation[] actualTypeAnnotations, TypeAnnotation[] allOnSameTargetTypeAnnotations,
                AnnotatedElement decl) {
            this.type = type;
            this.decl = decl;
            this.location = location;
            this.allOnSameTargetTypeAnnotations = allOnSameTargetTypeAnnotations;
            this.annotations = TypeAnnotationParser.mapTypeAnnotations(location.filter(actualTypeAnnotations));
        }

        // AnnotatedElement
        @Override
        public final Annotation[] getAnnotations() {
            return getDeclaredAnnotations();
        }

        @Override
        public final <T extends Annotation> T getAnnotation(Class<T> annotation) {
            return getDeclaredAnnotation(annotation);
        }

        @Override
        public final <T extends Annotation> T[] getAnnotationsByType(Class<T> annotation) {
            return getDeclaredAnnotationsByType(annotation);
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return annotations.values().toArray(new Annotation[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotation) {
            return (T)annotations.get(annotation);
        }

        @Override
        public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotation) {
            return AnnotationSupport.getDirectlyAndIndirectlyPresent(annotations, annotation);
        }

        // AnnotatedType
        @Override
        public Type getType() {
            return type;
        }

        // Implementation details
        LocationInfo getLocation() {
            return location;
        }
        TypeAnnotation[] getTypeAnnotations() {
            return allOnSameTargetTypeAnnotations;
        }
        AnnotatedElement getDecl() {
            return decl;
        }
    }

    private static class AnnotatedArrayTypeImpl extends AnnotatedTypeBaseImpl implements AnnotatedArrayType {
        AnnotatedArrayTypeImpl(Type type, LocationInfo location,
                TypeAnnotation[] actualTypeAnnotations, TypeAnnotation[] allOnSameTargetTypeAnnotations,
                AnnotatedElement decl) {
            super(type, location, actualTypeAnnotations, allOnSameTargetTypeAnnotations, decl);
        }

        @Override
        public AnnotatedType getAnnotatedGenericComponentType() {
            return AnnotatedTypeFactory.buildAnnotatedType(getComponentType(),
                                                           getLocation().pushArray(),
                                                           getTypeAnnotations(),
                                                           getTypeAnnotations(),
                                                           getDecl());
        }

        private Type getComponentType() {
            Type t = getType();
            if (t instanceof Class) {
                Class<?> c = (Class)t;
                return c.getComponentType();
            }
            return ((GenericArrayType)t).getGenericComponentType();
        }
    }

    private static class AnnotatedTypeVariableImpl extends AnnotatedTypeBaseImpl implements AnnotatedTypeVariable {
        AnnotatedTypeVariableImpl(TypeVariable<?> type, LocationInfo location,
                TypeAnnotation[] actualTypeAnnotations, TypeAnnotation[] allOnSameTargetTypeAnnotations,
                AnnotatedElement decl) {
            super(type, location, actualTypeAnnotations, allOnSameTargetTypeAnnotations, decl);
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return getTypeVariable().getAnnotatedBounds();
        }

        private TypeVariable<?> getTypeVariable() {
            return (TypeVariable)getType();
        }
    }

    private static class AnnotatedParameterizedTypeImpl extends AnnotatedTypeBaseImpl implements AnnotatedParameterizedType {
        AnnotatedParameterizedTypeImpl(ParameterizedType type, LocationInfo location,
                TypeAnnotation[] actualTypeAnnotations, TypeAnnotation[] allOnSameTargetTypeAnnotations,
                AnnotatedElement decl) {
            super(type, location, actualTypeAnnotations, allOnSameTargetTypeAnnotations, decl);
        }

        @Override
        public AnnotatedType[] getAnnotatedActualTypeArguments() {
            Type[] arguments = getParameterizedType().getActualTypeArguments();
            AnnotatedType[] res = new AnnotatedType[arguments.length];
            Arrays.fill(res, EMPTY_ANNOTATED_TYPE);
            int initialCapacity = getTypeAnnotations().length;
            for (int i = 0; i < res.length; i++) {
                List<TypeAnnotation> l = new ArrayList<>(initialCapacity);
                LocationInfo newLoc = getLocation().pushTypeArg((byte)i);
                for (TypeAnnotation t : getTypeAnnotations())
                    if (t.getLocationInfo().isSameLocationInfo(newLoc))
                        l.add(t);
                res[i] = buildAnnotatedType(arguments[i],
                                            newLoc,
                                            l.toArray(new TypeAnnotation[0]),
                                            getTypeAnnotations(),
                                            getDecl());
            }
            return res;
        }

        private ParameterizedType getParameterizedType() {
            return (ParameterizedType)getType();
        }
    }

    private static class AnnotatedWildcardTypeImpl extends AnnotatedTypeBaseImpl implements AnnotatedWildcardType {
        private final boolean hasUpperBounds;
        AnnotatedWildcardTypeImpl(WildcardType type, LocationInfo location,
                TypeAnnotation[] actualTypeAnnotations, TypeAnnotation[] allOnSameTargetTypeAnnotations,
                AnnotatedElement decl) {
            super(type, location, actualTypeAnnotations, allOnSameTargetTypeAnnotations, decl);
            hasUpperBounds = (type.getLowerBounds().length == 0);
        }

        @Override
        public AnnotatedType[] getAnnotatedUpperBounds() {
            if (!hasUpperBounds())
                return new AnnotatedType[0];
            return getAnnotatedBounds(getWildcardType().getUpperBounds());
        }

        @Override
        public AnnotatedType[] getAnnotatedLowerBounds() {
            if (hasUpperBounds)
                return new AnnotatedType[0];
            return getAnnotatedBounds(getWildcardType().getLowerBounds());
        }

        private AnnotatedType[] getAnnotatedBounds(Type[] bounds) {
            AnnotatedType[] res = new AnnotatedType[bounds.length];
            Arrays.fill(res, EMPTY_ANNOTATED_TYPE);
            LocationInfo newLoc = getLocation().pushWildcard();
            int initialCapacity = getTypeAnnotations().length;
            for (int i = 0; i < res.length; i++) {
                List<TypeAnnotation> l = new ArrayList<>(initialCapacity);
                for (TypeAnnotation t : getTypeAnnotations())
                    if (t.getLocationInfo().isSameLocationInfo(newLoc))
                        l.add(t);
                res[i] = buildAnnotatedType(bounds[i],
                                            newLoc,
                                            l.toArray(new TypeAnnotation[0]),
                                            getTypeAnnotations(),
                                            getDecl());
            }
            return res;
        }

        private WildcardType getWildcardType() {
            return (WildcardType)getType();
        }

        private boolean hasUpperBounds() {
            return hasUpperBounds;
        }
    }
}
