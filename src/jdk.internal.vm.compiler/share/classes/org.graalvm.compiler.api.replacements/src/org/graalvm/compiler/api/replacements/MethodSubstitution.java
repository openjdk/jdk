/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.api.replacements;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;

import jdk.vm.ci.meta.Signature;

/**
 * Denotes a method whose body is used by a compiler as the substitute (or intrinsification) of
 * another method. The exact mechanism used to do the substitution is compiler dependent but every
 * compiler should require substitute methods to be annotated with {@link MethodSubstitution}. In
 * addition, a compiler is recommended to implement {@link MethodSubstitutionRegistry} to advertise
 * the mechanism by which it supports registration of method substitutes.
 *
 * A compiler may support partial intrinsification where only a part of a method is implemented by
 * the compiler. The unsupported path is expressed by a call to either the original or substitute
 * method from within the substitute method. Such as call is a <i>partial intrinsic exit</i>.
 *
 * For example, here's a HotSpot specific intrinsic for {@link Array#newInstance(Class, int)} that
 * only handles the case where the VM representation of the array class to be instantiated already
 * exists:
 *
 * <pre>
 * &#64;MethodSubstitution
 * public static Object newInstance(Class<?> componentType, int length) {
 *     if (componentType == null || loadKlassFromObject(componentType, arrayKlassOffset(INJECTED_VMCONFIG), CLASS_ARRAY_KLASS_LOCATION).isNull()) {
 *         // Array class not yet created - exit the intrinsic and call the original method
 *         return newInstance(componentType, length);
 *     }
 *     return DynamicNewArrayNode.newArray(GraalDirectives.guardingNonNull(componentType), length, JavaKind.Object);
 * }
 * </pre>
 *
 * Here's the same intrinsification where the exit is expressed as a call to the original method:
 *
 * <pre>
 * &#64;MethodSubstitution
 * public static Object newInstance(Class<?> componentType, int length) {
 *     if (componentType == null || loadKlassFromObject(componentType, arrayKlassOffset(INJECTED_VMCONFIG), CLASS_ARRAY_KLASS_LOCATION).isNull()) {
 *         // Array class not yet created - exit the intrinsic and call the original method
 *         return java.lang.reflect.newInstance(componentType, length);
 *     }
 *     return DynamicNewArrayNode.newArray(GraalDirectives.guardingNonNull(componentType), length, JavaKind.Object);
 * }
 * </pre>
 *
 * A condition for a partial intrinsic exit is that it is uses the unmodified parameters of the
 * substitute as arguments to the partial intrinsic exit call. There must also be no side effecting
 * instruction between the start of the substitute method and the partial intrinsic exit.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodSubstitution {

    /**
     * Gets the name of the original method.
     * <p>
     * If the default value is specified for this element, then the name of the original method is
     * same as the substitute method.
     */
    String value() default "";

    /**
     * Determines if the original method is static.
     */
    boolean isStatic() default true;

    /**
     * Gets the {@linkplain Signature#toMethodDescriptor signature} of the original method.
     * <p>
     * If the default value is specified for this element, then the signature of the original method
     * is the same as the substitute method.
     */
    String signature() default "";

    /**
     * Determines if the substitution is for a method that may not be part of the runtime. For
     * example, a method introduced in a later JDK version. Substitutions for such methods are
     * omitted if the original method cannot be found.
     */
    boolean optional() default false;
}
