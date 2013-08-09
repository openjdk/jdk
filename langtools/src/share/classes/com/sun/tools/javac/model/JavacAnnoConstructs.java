/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.javac.model;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.sun.tools.javac.code.TargetType;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.AnnotatedType;
import com.sun.tools.javac.util.ListBuffer;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import com.sun.tools.javac.util.List;

/**
 * Utility methods for operating on annotated constructs.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class JavacAnnoConstructs {

    // <editor-fold defaultstate="collapsed" desc="Symbols">

    /**
     * An internal-use utility that creates a runtime view of an
     * annotation. This is the implementation of
     * Element.getAnnotation(Class).
     */
    public static <A extends Annotation> A getAnnotation(Symbol annotated,
                                                         Class<A> annoType) {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: "
                                               + annoType);
        Attribute.Compound c;
        if (annotated.kind == Kinds.TYP &&
                annotated instanceof ClassSymbol) {
            c = getAttributeOnClass((ClassSymbol)annotated, annoType);
        } else if (annotated.kind == Kinds.TYP &&
                   annotated instanceof TypeVariableSymbol) {
            c = getAttributeOnTypeVariable((TypeVariableSymbol)annotated, annoType);
        } else {
            c = getAttribute(annotated, annoType);
        }
        return c == null ? null : AnnotationProxyMaker.generateAnnotation(c, annoType);
    }

    // Helper to getAnnotation[s]
    private static <A extends Annotation> Attribute.Compound getAttribute(Symbol annotated,
                                                                          Class<A> annoType) {
        String name = annoType.getName();

        for (Attribute.Compound anno : annotated.getRawAttributes()) {
            if (name.equals(anno.type.tsym.flatName().toString()))
                return anno;
        }

        return null;
    }

    // Helper to getAnnotation[s]
    private static <A extends Annotation> Attribute.Compound
            getAttributeOnTypeVariable(TypeVariableSymbol annotated, Class<A> annoType) {
        String name = annoType.getName();

        // Declaration annotations on type variables are stored in type attributes
        // on the owner of the TypeVariableSymbol
        List<Attribute.Compound> res = List.nil();
        List<Attribute.TypeCompound> candidates = annotated.owner.getRawTypeAttributes();
        for (Attribute.TypeCompound anno : candidates)
            if (anno.position.type == TargetType.CLASS_TYPE_PARAMETER ||
                    anno.position.type == TargetType.METHOD_TYPE_PARAMETER)
                if (name.equals(anno.type.tsym.flatName().toString()))
                    return anno;

        return null;
    }

    // Helper to getAnnotation[s]
    private static <A extends Annotation> Attribute.Compound getAttributeOnClass(
            ClassSymbol annotated,
            final Class<A> annoType)
    {
        boolean inherited = annoType.isAnnotationPresent(Inherited.class);
        Attribute.Compound result = null;

        result = getAttribute(annotated, annoType);
        if (result != null || !inherited)
            return result;

        while ((annotated = nextSupertypeToSearch(annotated)) != null) {
            result = getAttribute(annotated, annoType);
            if (result != null)
                return result;
        }
        return null; // no more supertypes to search
    }

    /**
     * Returns the next type to search for inherited annotations or {@code null}
     * if the next type can't be found.
     */
    private static ClassSymbol nextSupertypeToSearch(ClassSymbol annotated) {
        if (annotated.name == annotated.name.table.names.java_lang_Object)
            return null;

        Type sup = annotated.getSuperclass();
        if (!sup.hasTag(CLASS) || sup.isErroneous())
            return null;

        return (ClassSymbol) sup.tsym;
    }

    /**
     * An internal-use utility that creates a runtime view of
     * annotations. This is the implementation of
     * Element.getAnnotations(Class).
     */
    public static <A extends Annotation> A[] getAnnotationsByType(Symbol annotated,
            Class<A> annoType)
    {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: "
                                               + annoType);
        // If annoType does not declare a container this is equivalent to wrapping
        // getAnnotation(...) in an array.
        Class <? extends Annotation> containerType = getContainer(annoType);
        if (containerType == null) {
            A res = getAnnotation(annotated, annoType);
            int size;
            if (res == null) {
                size = 0;
            } else {
                size = 1;
            }
            @SuppressWarnings("unchecked") // annoType is the Class for A
            A[] arr = (A[])java.lang.reflect.Array.newInstance(annoType, size);
            if (res != null)
                arr[0] = res;
            return arr;
        }

        // So we have a containing type
        String annoTypeName = annoType.getSimpleName();
        String containerTypeName = containerType.getSimpleName();
        int directIndex = -1, containerIndex = -1;
        Attribute.Compound direct = null, container = null;
        // Find directly (explicit or implicit) present annotations
        int index = -1;
        for (List<Attribute.Compound> list = annotated.getAnnotationMirrors();
                !list.isEmpty();
                list = list.tail) {
            Attribute.Compound attribute = list.head;
            index++;
            if (attribute.type.tsym.flatName().contentEquals(annoTypeName)) {
                directIndex = index;
                direct = attribute;
            } else if(containerTypeName != null &&
                      attribute.type.tsym.flatName().contentEquals(containerTypeName)) {
                containerIndex = index;
                container = attribute;
            }
        }

        // Deal with inherited annotations
        if (direct == null && container == null) {
            if (annotated.kind == Kinds.TYP &&
                    (annotated instanceof ClassSymbol)) {
                ClassSymbol s = nextSupertypeToSearch((ClassSymbol)annotated);
                if (s != null)
                    return getAnnotationsByType(s, annoType);
            }
        }

        // Pack them in an array
        Attribute[] contained0 = null;
        if (container != null)
            contained0 = unpackAttributes(container);
        ListBuffer<Attribute.Compound> compounds = ListBuffer.lb();
        if (contained0 != null) {
            for (Attribute a : contained0)
                if (a instanceof Attribute.Compound)
                    compounds = compounds.append((Attribute.Compound)a);
        }
        Attribute.Compound[] contained = compounds.toArray(new Attribute.Compound[compounds.size()]);

        int size = (direct == null ? 0 : 1) + contained.length;
        @SuppressWarnings("unchecked") // annoType is the Class for A
        A[] arr = (A[])java.lang.reflect.Array.newInstance(annoType, size);

        // if direct && container, which is first?
        int insert = -1;
        int length = arr.length;
        if (directIndex >= 0 && containerIndex >= 0) {
            if (directIndex < containerIndex) {
                arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 1;
            } else {
                arr[arr.length - 1] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 0;
                length--;
            }
        } else if (directIndex >= 0) {
            arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
            return arr;
        } else {
            // Only container
            insert = 0;
        }

        for (int i = 0; i + insert < length; i++)
            arr[insert + i] = AnnotationProxyMaker.generateAnnotation(contained[i], annoType);

        return arr;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Types">

    /**
     * An internal-use utility that creates a runtime view of an
     * annotation. This is the implementation of
     * TypeMirror.getAnnotation(Class).
     */
    public static <A extends Annotation> A getAnnotation(AnnotatedType annotated, Class<A> annoType) {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: "
                                               + annoType);
        Attribute.Compound c = getAttribute(annotated, annoType);
        return c == null ? null : AnnotationProxyMaker.generateAnnotation(c, annoType);
    }

    // Helper to getAnnotation[s]
    private static <A extends Annotation> Attribute.Compound getAttribute(Type annotated,
                                                                          Class<A> annoType) {
        String name = annoType.getName();

        for (Attribute.Compound anno : annotated.getAnnotationMirrors()) {
            if (name.equals(anno.type.tsym.flatName().toString()))
                return anno;
        }

        return null;
    }

    /**
     * An internal-use utility that creates a runtime view of
     * annotations. This is the implementation of
     * TypeMirror.getAnnotationsByType(Class).
     */
    public static <A extends Annotation> A[] getAnnotationsByType(AnnotatedType annotated, Class<A> annoType) {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: "
                                               + annoType);
        // If annoType does not declare a container this is equivalent to wrapping
        // getAnnotation(...) in an array.
        Class <? extends Annotation> containerType = getContainer(annoType);
        if (containerType == null) {
            A res = getAnnotation(annotated, annoType);
            int size;
            if (res == null) {
                size = 0;
            } else {
                size = 1;
            }
            @SuppressWarnings("unchecked") // annoType is the Class for A
            A[] arr = (A[])java.lang.reflect.Array.newInstance(annoType, size);
            if (res != null)
                arr[0] = res;
            return arr;
        }

        // So we have a containing type
        String annoTypeName = annoType.getSimpleName();
        String containerTypeName = containerType.getSimpleName();
        int directIndex = -1, containerIndex = -1;
        Attribute.Compound direct = null, container = null;
        // Find directly (explicit or implicit) present annotations
        int index = -1;
        for (List<? extends Attribute.Compound> list = annotated.getAnnotationMirrors();
                !list.isEmpty();
                list = list.tail) {
            Attribute.Compound attribute = list.head;
            index++;
            if (attribute.type.tsym.flatName().contentEquals(annoTypeName)) {
                directIndex = index;
                direct = attribute;
            } else if(containerTypeName != null &&
                      attribute.type.tsym.flatName().contentEquals(containerTypeName)) {
                containerIndex = index;
                container = attribute;
            }
        }

        // Pack them in an array
        Attribute[] contained0 = null;
        if (container != null)
            contained0 = unpackAttributes(container);
        ListBuffer<Attribute.Compound> compounds = ListBuffer.lb();
        if (contained0 != null) {
            for (Attribute a : contained0)
                if (a instanceof Attribute.Compound)
                    compounds = compounds.append((Attribute.Compound)a);
        }
        Attribute.Compound[] contained = compounds.toArray(new Attribute.Compound[compounds.size()]);

        int size = (direct == null ? 0 : 1) + contained.length;
        @SuppressWarnings("unchecked") // annoType is the Class for A
        A[] arr = (A[])java.lang.reflect.Array.newInstance(annoType, size);

        // if direct && container, which is first?
        int insert = -1;
        int length = arr.length;
        if (directIndex >= 0 && containerIndex >= 0) {
            if (directIndex < containerIndex) {
                arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 1;
            } else {
                arr[arr.length - 1] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 0;
                length--;
            }
        } else if (directIndex >= 0) {
            arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
            return arr;
        } else {
            // Only container
            insert = 0;
        }

        for (int i = 0; i + insert < length; i++)
            arr[insert + i] = AnnotationProxyMaker.generateAnnotation(contained[i], annoType);

        return arr;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Container support">

    // Needed to unpack the runtime view of containing annotations
    private static final Class<? extends Annotation> REPEATABLE_CLASS = initRepeatable();
    private static final Method VALUE_ELEMENT_METHOD = initValueElementMethod();

    private static Class<? extends Annotation> initRepeatable() {
        try {
            // Repeatable will not be available when bootstrapping on
            // JDK 7 so use a reflective lookup instead of a class
            // literal for Repeatable.class.
            return Class.forName("java.lang.annotation.Repeatable").asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static Method initValueElementMethod() {
        if (REPEATABLE_CLASS == null)
            return null;

        Method m = null;
        try {
            m = REPEATABLE_CLASS.getMethod("value");
            if (m != null)
                m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // Helper to getAnnotations
    private static Class<? extends Annotation> getContainer(Class<? extends Annotation> annoType) {
        // Since we can not refer to java.lang.annotation.Repeatable until we are
        // bootstrapping with java 8 we need to get the Repeatable annotation using
        // reflective invocations instead of just using its type and element method.
        if (REPEATABLE_CLASS != null &&
            VALUE_ELEMENT_METHOD != null) {
            // Get the Repeatable instance on the annotations declaration
            Annotation repeatable = (Annotation)annoType.getAnnotation(REPEATABLE_CLASS);
            if (repeatable != null) {
                try {
                    // Get the value element, it should be a class
                    // indicating the containing annotation type
                    @SuppressWarnings("unchecked")
                    Class<? extends Annotation> containerType = (Class)VALUE_ELEMENT_METHOD.invoke(repeatable);
                    if (containerType == null)
                        return null;

                    return containerType;
                } catch (ClassCastException e) {
                    return null;
                } catch (IllegalAccessException e) {
                    return null;
                } catch (InvocationTargetException e ) {
                    return null;
                }
            }
        }
        return null;
    }

    // Helper to getAnnotations
    private static Attribute[] unpackAttributes(Attribute.Compound container) {
        // We now have an instance of the container,
        // unpack it returning an instance of the
        // contained type or null
        return ((Attribute.Array)container.member(container.type.tsym.name.table.names.value)).values;
    }

    // </editor-fold>
}
