/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Various static utility methods for testing type relationships.
 */
public class TypeUtilities {
    static final Class<Object> OBJECT_CLASS = Object.class;

    private TypeUtilities() {
    }

    /**
     * Given two types represented by c1 and c2, returns a type that is their most specific common supertype for
     * purposes of lossless conversions.
     *
     * @param c1 one type
     * @param c2 another type
     * @return their most common superclass or superinterface for purposes of lossless conversions. If they have several
     * unrelated superinterfaces as their most specific common type, or the types themselves are completely
     * unrelated interfaces, {@link java.lang.Object} is returned.
     */
    public static Class<?> getCommonLosslessConversionType(final Class<?> c1, final Class<?> c2) {
        if(c1 == c2) {
            return c1;
        } else if (c1 == void.class || c2 == void.class) {
            return Object.class;
        } else if(isConvertibleWithoutLoss(c2, c1)) {
            return c1;
        } else if(isConvertibleWithoutLoss(c1, c2)) {
            return c2;
        } else if(c1.isPrimitive() && c2.isPrimitive()) {
            if((c1 == byte.class && c2 == char.class) || (c1 == char.class && c2 == byte.class)) {
                // byte + char = int
                return int.class;
            } else if((c1 == short.class && c2 == char.class) || (c1 == char.class && c2 == short.class)) {
                // short + char = int
                return int.class;
            } else if((c1 == int.class && c2 == float.class) || (c1 == float.class && c2 == int.class)) {
                // int + float = double
                return double.class;
            }
        }
        // For all other cases. This will handle long + (float|double) = Number case as well as boolean + anything = Object case too.
        return getMostSpecificCommonTypeUnequalNonprimitives(c1, c2);
    }

    private static Class<?> getMostSpecificCommonTypeUnequalNonprimitives(final Class<?> c1, final Class<?> c2) {
        final Class<?> npc1 = c1.isPrimitive() ? getWrapperType(c1) : c1;
        final Class<?> npc2 = c2.isPrimitive() ? getWrapperType(c2) : c2;
        final Set<Class<?>> a1 = getAssignables(npc1, npc2);
        final Set<Class<?>> a2 = getAssignables(npc2, npc1);
        a1.retainAll(a2);
        if(a1.isEmpty()) {
            // Can happen when at least one of the arguments is an interface,
            // as they don't have Object at the root of their hierarchy.
            return Object.class;
        }
        // Gather maximally specific elements. Yes, there can be more than one
        // thank to interfaces. I.e., if you call this method for String.class
        // and Number.class, you'll have Comparable, Serializable, and Object
        // as maximal elements.
        final List<Class<?>> max = new ArrayList<>();
        outer: for(final Class<?> clazz: a1) {
            for(final Iterator<Class<?>> maxiter = max.iterator(); maxiter.hasNext();) {
                final Class<?> maxClazz = maxiter.next();
                if(isSubtype(maxClazz, clazz)) {
                    // It can't be maximal, if there's already a more specific
                    // maximal than it.
                    continue outer;
                }
                if(isSubtype(clazz, maxClazz)) {
                    // If it's more specific than a currently maximal element,
                    // that currently maximal is no longer a maximal.
                    maxiter.remove();
                }
            }
            // If we get here, no current maximal is more specific than the
            // current class, so it is considered maximal as well
            max.add(clazz);
        }
        if(max.size() > 1) {
            return Object.class;
        }
        return max.get(0);
    }

    private static Set<Class<?>> getAssignables(final Class<?> c1, final Class<?> c2) {
        final Set<Class<?>> s = new HashSet<>();
        collectAssignables(c1, c2, s);
        return s;
    }

    private static void collectAssignables(final Class<?> c1, final Class<?> c2, final Set<Class<?>> s) {
        if(c1.isAssignableFrom(c2)) {
            s.add(c1);
        }
        final Class<?> sc = c1.getSuperclass();
        if(sc != null) {
            collectAssignables(sc, c2, s);
        }
        final Class<?>[] itf = c1.getInterfaces();
        for(int i = 0; i < itf.length; ++i) {
            collectAssignables(itf[i], c2, s);
        }
    }

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES = createWrapperTypes();
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TYPES = invertMap(WRAPPER_TYPES);
    private static final Map<String, Class<?>> PRIMITIVE_TYPES_BY_NAME = createClassNameMapping(WRAPPER_TYPES.keySet());

    private static Map<Class<?>, Class<?>> createWrapperTypes() {
        final Map<Class<?>, Class<?>> wrapperTypes = new IdentityHashMap<>(8);
        wrapperTypes.put(Boolean.TYPE, Boolean.class);
        wrapperTypes.put(Byte.TYPE, Byte.class);
        wrapperTypes.put(Character.TYPE, Character.class);
        wrapperTypes.put(Short.TYPE, Short.class);
        wrapperTypes.put(Integer.TYPE, Integer.class);
        wrapperTypes.put(Long.TYPE, Long.class);
        wrapperTypes.put(Float.TYPE, Float.class);
        wrapperTypes.put(Double.TYPE, Double.class);
        return Collections.unmodifiableMap(wrapperTypes);
    }

    private static Map<String, Class<?>> createClassNameMapping(final Collection<Class<?>> classes) {
        final Map<String, Class<?>> map = new HashMap<>();
        for(final Class<?> clazz: classes) {
            map.put(clazz.getName(), clazz);
        }
        return map;
    }

    private static <K, V> Map<V, K> invertMap(final Map<K, V> map) {
        final Map<V, K> inverted = new IdentityHashMap<>(map.size());
        for(final Map.Entry<K, V> entry: map.entrySet()) {
            inverted.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(inverted);
    }

    /**
     * Determines whether one type can be converted to another type using a method invocation conversion, as per JLS 5.3
     * "Method Invocation Conversion". This is basically all conversions allowed by subtyping (see
     * {@link #isSubtype(Class, Class)}) as well as boxing conversion (JLS 5.1.7) optionally followed by widening
     * reference conversion and unboxing conversion (JLS 5.1.8) optionally followed by widening primitive conversion.
     *
     * @param sourceType the type being converted from (call site type for parameter types, method type for return types)
     * @param targetType the parameter type being converted to (method type for parameter types, call site type for return types)
     * @return true if source type is method invocation convertible to target type.
     */
    public static boolean isMethodInvocationConvertible(final Class<?> sourceType, final Class<?> targetType) {
        if(targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        if(sourceType.isPrimitive()) {
            if(targetType.isPrimitive()) {
                return isProperPrimitiveSubtype(sourceType, targetType);
            }
            // Boxing + widening reference conversion
            assert WRAPPER_TYPES.get(sourceType) != null : sourceType.getName();
            return targetType.isAssignableFrom(WRAPPER_TYPES.get(sourceType));
        }
        if(targetType.isPrimitive()) {
            final Class<?> unboxedCallSiteType = PRIMITIVE_TYPES.get(sourceType);
            return unboxedCallSiteType != null
                    && (unboxedCallSiteType == targetType || isProperPrimitiveSubtype(unboxedCallSiteType, targetType));
        }
        return false;
    }

    /**
     * Determines whether a type can be converted to another without losing any precision. As a special case,
     * void is considered convertible only to Object and void, while anything can be converted to void. This
     * is because a target type of void means we don't care about the value, so the conversion is always
     * permissible.
     *
     * @param sourceType the source type
     * @param targetType the target type
     * @return true if lossless conversion is possible
     */
    public static boolean isConvertibleWithoutLoss(final Class<?> sourceType, final Class<?> targetType) {
        if(targetType.isAssignableFrom(sourceType) || targetType == void.class) {
            return true;
        }
        if(sourceType.isPrimitive()) {
            if(sourceType == void.class) {
                // Void should be losslessly representable by Object, either as null or as a custom value that
                // can be set with DynamicLinkerFactory.setAutoConversionStrategy.
                return targetType == Object.class;
            }
            if(targetType.isPrimitive()) {
                return isProperPrimitiveLosslessSubtype(sourceType, targetType);
            }
            // Boxing + widening reference conversion
            assert WRAPPER_TYPES.get(sourceType) != null : sourceType.getName();
            return targetType.isAssignableFrom(WRAPPER_TYPES.get(sourceType));
        }
        // Can't convert from any non-primitive type to any primitive type without data loss because of null.
        // Also, can't convert non-assignable reference types.
        return false;
    }

    /**
     * Determines whether one type can be potentially converted to another type at runtime. Allows a conversion between
     * any subtype and supertype in either direction, and also allows a conversion between any two primitive types, as
     * well as between any primitive type and any reference type that can hold a boxed primitive.
     *
     * @param callSiteType the parameter type at the call site
     * @param methodType the parameter type in the method declaration
     * @return true if callSiteType is potentially convertible to the methodType.
     */
    public static boolean isPotentiallyConvertible(final Class<?> callSiteType, final Class<?> methodType) {
        // Widening or narrowing reference conversion
        if(areAssignable(callSiteType, methodType)) {
            return true;
        }
        if(callSiteType.isPrimitive()) {
            // Allow any conversion among primitives, as well as from any
            // primitive to any type that can receive a boxed primitive.
            // TODO: narrow this a bit, i.e. allow, say, boolean to Character?
            // MethodHandles.convertArguments() allows it, so we might need to
            // too.
            return methodType.isPrimitive() || isAssignableFromBoxedPrimitive(methodType);
        }
        if(methodType.isPrimitive()) {
            // Allow conversion from any reference type that can contain a
            // boxed primitive to any primitive.
            // TODO: narrow this a bit too?
            return isAssignableFromBoxedPrimitive(callSiteType);
        }
        return false;
    }

    /**
     * Returns true if either of the types is assignable from the other.
     * @param c1 one of the types
     * @param c2 another one of the types
     * @return true if either c1 is assignable from c2 or c2 is assignable from c1.
     */
    public static boolean areAssignable(final Class<?> c1, final Class<?> c2) {
        return c1.isAssignableFrom(c2) || c2.isAssignableFrom(c1);
    }

    /**
     * Determines whether one type is a subtype of another type, as per JLS 4.10 "Subtyping". Note: this is not strict
     * or proper subtype, therefore true is also returned for identical types; to be completely precise, it allows
     * identity conversion (JLS 5.1.1), widening primitive conversion (JLS 5.1.2) and widening reference conversion (JLS
     * 5.1.5).
     *
     * @param subType the supposed subtype
     * @param superType the supposed supertype of the subtype
     * @return true if subType can be converted by identity conversion, widening primitive conversion, or widening
     * reference conversion to superType.
     */
    public static boolean isSubtype(final Class<?> subType, final Class<?> superType) {
        // Covers both JLS 4.10.2 "Subtyping among Class and Interface Types"
        // and JLS 4.10.3 "Subtyping among Array Types", as well as primitive
        // type identity.
        if(superType.isAssignableFrom(subType)) {
            return true;
        }
        // JLS 4.10.1 "Subtyping among Primitive Types". Note we don't test for
        // identity, as identical types were taken care of in the
        // isAssignableFrom test. As per 4.10.1, the supertype relation is as
        // follows:
        // double > float
        // float > long
        // long > int
        // int > short
        // int > char
        // short > byte
        if(superType.isPrimitive() && subType.isPrimitive()) {
            return isProperPrimitiveSubtype(subType, superType);
        }
        return false;
    }

    /**
     * Returns true if a supposed primitive subtype is a proper subtype ( meaning, subtype and not identical) of the
     * supposed primitive supertype
     *
     * @param subType the supposed subtype
     * @param superType the supposed supertype
     * @return true if subType is a proper (not identical to) primitive subtype of the superType
     */
    private static boolean isProperPrimitiveSubtype(final Class<?> subType, final Class<?> superType) {
        if(superType == boolean.class || subType == boolean.class) {
            return false;
        }
        if(subType == byte.class) {
            return superType != char.class;
        }
        if(subType == char.class) {
            return superType != short.class && superType != byte.class;
        }
        if(subType == short.class) {
            return superType != char.class && superType != byte.class;
        }
        if(subType == int.class) {
            return superType == long.class || superType == float.class || superType == double.class;
        }
        if(subType == long.class) {
            return superType == float.class || superType == double.class;
        }
        if(subType == float.class) {
            return superType == double.class;
        }
        return false;
    }

    /**
     * Similar to {@link #isProperPrimitiveSubtype(Class, Class)}, except it disallows conversions from int and long to
     * float, and from long to double, as those can lose precision. It also disallows conversion from and to char and
     * anything else (similar to boolean) as char is not meant to be an arithmetic type.
     * @param subType the supposed subtype
     * @param superType the supposed supertype
     * @return true if subType is a proper (not identical to) primitive subtype of the superType that can be represented
     * by the supertype without no precision loss.
     */
    private static boolean isProperPrimitiveLosslessSubtype(final Class<?> subType, final Class<?> superType) {
        if(superType == boolean.class || subType == boolean.class) {
            return false;
        }
        if(superType == char.class || subType == char.class) {
            return false;
        }
        if(subType == byte.class) {
            return true;
        }
        if(subType == short.class) {
            return superType != byte.class;
        }
        if(subType == int.class) {
            return superType == long.class || superType == double.class;
        }
        if(subType == float.class) {
            return superType == double.class;
        }
        return false;
    }

    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE_TYPES = createWrapperToPrimitiveTypes();

    private static Map<Class<?>, Class<?>> createWrapperToPrimitiveTypes() {
        final Map<Class<?>, Class<?>> classes = new IdentityHashMap<>();
        classes.put(Void.class, Void.TYPE);
        classes.put(Boolean.class, Boolean.TYPE);
        classes.put(Byte.class, Byte.TYPE);
        classes.put(Character.class, Character.TYPE);
        classes.put(Short.class, Short.TYPE);
        classes.put(Integer.class, Integer.TYPE);
        classes.put(Long.class, Long.TYPE);
        classes.put(Float.class, Float.TYPE);
        classes.put(Double.class, Double.TYPE);
        return classes;
    }

    private static final Set<Class<?>> PRIMITIVE_WRAPPER_TYPES = createPrimitiveWrapperTypes();

    private static Set<Class<?>> createPrimitiveWrapperTypes() {
        final Map<Class<?>, Class<?>> classes = new IdentityHashMap<>();
        addClassHierarchy(classes, Boolean.class);
        addClassHierarchy(classes, Byte.class);
        addClassHierarchy(classes, Character.class);
        addClassHierarchy(classes, Short.class);
        addClassHierarchy(classes, Integer.class);
        addClassHierarchy(classes, Long.class);
        addClassHierarchy(classes, Float.class);
        addClassHierarchy(classes, Double.class);
        return classes.keySet();
    }

    private static void addClassHierarchy(final Map<Class<?>, Class<?>> map, final Class<?> clazz) {
        if(clazz == null) {
            return;
        }
        map.put(clazz, clazz);
        addClassHierarchy(map, clazz.getSuperclass());
        for(final Class<?> itf: clazz.getInterfaces()) {
            addClassHierarchy(map, itf);
        }
    }

    /**
     * Returns true if the class can be assigned from any boxed primitive.
     *
     * @param clazz the class
     * @return true if the class can be assigned from any boxed primitive. Basically, it is true if the class is any
     * primitive wrapper class, or a superclass or superinterface of any primitive wrapper class.
     */
    private static boolean isAssignableFromBoxedPrimitive(final Class<?> clazz) {
        return PRIMITIVE_WRAPPER_TYPES.contains(clazz);
    }

    /**
     * Given a name of a primitive type (except "void"), returns the class representing it. I.e. when invoked with
     * "int", returns {@link Integer#TYPE}.
     * @param name the name of the primitive type
     * @return the class representing the primitive type, or null if the name does not correspond to a primitive type
     * or is "void".
     */
    public static Class<?> getPrimitiveTypeByName(final String name) {
        return PRIMITIVE_TYPES_BY_NAME.get(name);
    }

    /**
     * When passed a class representing a wrapper for a primitive type, returns the class representing the corresponding
     * primitive type. I.e. calling it with {@code Integer.class} will return {@code Integer.TYPE}. If passed a class
     * that is not a wrapper for primitive type, returns null.
     * @param wrapperType the class object representing a wrapper for a primitive type
     * @return the class object representing the primitive type, or null if the passed class is not a primitive wrapper.
     */
    public static Class<?> getPrimitiveType(final Class<?> wrapperType) {
        return WRAPPER_TO_PRIMITIVE_TYPES.get(wrapperType);
    }


    /**
     * When passed a class representing a primitive type, returns the class representing the corresponding
     * wrapper type. I.e. calling it with {@code int.class} will return {@code Integer.class}. If passed a class
     * that is not a primitive type, returns null.
     * @param primitiveType the class object representing a primitive type
     * @return the class object representing the wrapper type, or null if the passed class is not a primitive.
     */
    public static Class<?> getWrapperType(final Class<?> primitiveType) {
        return WRAPPER_TYPES.get(primitiveType);
    }

    /**
     * Returns true if the passed type is a wrapper for a primitive type.
     * @param type the examined type
     * @return true if the passed type is a wrapper for a primitive type.
     */
    public static boolean isWrapperType(final Class<?> type) {
        return PRIMITIVE_TYPES.containsKey(type);
    }
}
