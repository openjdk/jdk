/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import jdk.internal.javac.PreviewFeature;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * @since 17
 */
@PreviewFeature(feature=PreviewFeature.Feature.SWITCH_PATTERN_MATCHING)
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle DO_ENUM_SWITCH;
    private static final MethodHandle INSTANCEOF_CHECK;
    private static final MethodHandle EQ_CHECK;
    private static final MethodHandle NULL_CHECK;

    static {
        try {
            DO_ENUM_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class, "doEnumSwitch",
                                           MethodType.methodType(int.class, Enum.class, int.class, Object[].class));
            INSTANCEOF_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "instanceofCheck",
                                           MethodType.methodType(boolean.class, Object.class, Class.class));
            EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "eqCheck",
                                           MethodType.methodType(boolean.class, Object.class, Object.class));
            NULL_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "nullCheck",
                                           MethodType.methodType(boolean.class, Object.class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a target of a reference type.  The static
     * arguments are an array of case labels which must be non-null and of type
     * {@code String} or {@code Integer} or {@code Class}.
     * <p>
     * The type of the returned {@code CallSite}'s method handle will have
     * a return type of {@code int}.   It has two parameters: the first argument
     * will be an {@code Object} instance ({@code target}) and the second
     * will be {@code int} ({@code restart}).
     * <p>
     * If the {@code target} is {@code null}, then the method of the call site
     * returns {@literal -1}.
     * <p>
     * If the {@code target} is not {@code null}, then the method of the call site
     * returns the index of the first element in the {@code labels} array starting from
     * the {@code restart} index matching one of the following conditions:
     * <ul>
     *   <li>the element is of type {@code Class} that is assignable
     *       from the target's class; or</li>
     *   <li>the element is of type {@code String} or {@code Integer} and
     *       equals to the target.</li>
     * </ul>
     * <p>
     * If no element in the {@code labels} array matches the target, then
     * the method of the call site return the length of the {@code labels} array.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName unused
     * @param invocationType The invocation type of the {@code CallSite} with two parameters,
     *                       a reference type, an {@code int}, and {@code int} as a return type.
     * @param labels case labels - {@code String} and {@code Integer} constants
     *               and {@code Class} instances, in any combination
     * @return a {@code CallSite} returning the first matching element as described above
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any element in the labels array is null, if the
     * invocation type is not not a method type of first parameter of a reference type,
     * second parameter of type {@code int} and with {@code int} as its return type,
     * or if {@code labels} contains an element that is not of type {@code String},
     * {@code Integer} or {@code Class}.
     * @jvms 4.4.6 The CONSTANT_NameAndType_info Structure
     * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Object... labels) {
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || invocationType.parameterType(0).isPrimitive()
            || !invocationType.parameterType(1).equals(int.class))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(labels);

        labels = labels.clone();
        Stream.of(labels).forEach(SwitchBootstraps::verifyLabel);

        MethodHandle target  = createMethodHandleSwitch(labels);

        return new ConstantCallSite(target);
    }

    private static void verifyLabel(Object label) {
        if (label == null) {
            throw new IllegalArgumentException("null label found");
        }
        Class<?> labelClass = label.getClass();
        if (labelClass != Class.class &&
            labelClass != String.class &&
            labelClass != Integer.class) {
            throw new IllegalArgumentException("label with illegal type found: " + label.getClass());
        }
    }

    private static MethodHandle createMethodHandleSwitch(Object[] labels) {
        MethodHandle mainTest;
        MethodHandle def = MethodHandles.dropArguments(MethodHandles.constant(int.class, labels.length), 0, Object.class);
        if (labels.length > 0) {
            MethodHandle[] testChains = new MethodHandle[labels.length];
            List<Object> labelsList = new ArrayList<>(Arrays.asList(labels));
            Collections.reverse(labelsList);
            for (int i = 0; i < labels.length; i++) {
                MethodHandle test = def;
                int idx = labels.length - 1;
                List<Object> currentLabels = labelsList.subList(0, labels.length - i);

                for (int j = 0; j < currentLabels.size(); j++, idx--) {
                    Object currentLabel = currentLabels.get(j);
                    if (j + 1 < currentLabels.size() && currentLabels.get(j + 1) == currentLabel) continue;
                    MethodHandle currentTest;
                    if (currentLabel instanceof Class<?>) {
                        currentTest = INSTANCEOF_CHECK;
                    } else {
                        currentTest = EQ_CHECK;
                    }
                    test = MethodHandles.guardWithTest(MethodHandles.insertArguments(currentTest, 1, currentLabel),
                                                       MethodHandles.dropArguments(MethodHandles.constant(int.class, idx), 0, Object.class),
                                                       test);
                }
                testChains[i] = MethodHandles.dropArguments(test, 0, int.class);
            }
            mainTest = MethodHandles.tableSwitch(MethodHandles.dropArguments(def, 0, int.class), testChains);
        } else {
            mainTest = MethodHandles.dropArguments(def, 0, int.class);
        }
        MethodHandle body =
                MethodHandles.guardWithTest(MethodHandles.dropArguments(NULL_CHECK, 0, int.class),
                                            MethodHandles.dropArguments(MethodHandles.constant(int.class, -1), 0, int.class, Object.class),
                                            mainTest);
        return MethodHandles.permuteArguments(body, MethodType.methodType(int.class, Object.class, int.class), 1, 0);
    }

    private static boolean instanceofCheck(Object value, Class<?> label) {
        return label.isAssignableFrom(value.getClass());
    }

    private static boolean eqCheck(Object value, Object label) {
        if (label instanceof Integer constant) {
            if (value instanceof Number input && constant.intValue() == input.intValue()) {
                return true;
            } else if (value instanceof Character input && constant.intValue() == input.charValue()) {
                return true;
            }
        } else if (label.equals(value)) {
            return true;
        }

        return false;
    }

    private static boolean nullCheck(Object value) {
        return value == null;
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a target of an enum type. The static
     * arguments are used to encode the case labels associated to the switch
     * construct, where each label can be encoded in two ways:
     * <ul>
     *   <li>as a {@code String} value, which represents the name of
     *       the enum constant associated with the label</li>
     *   <li>as a {@code Class} value, which represents the enum type
     *       associated with a type test pattern</li>
     * </ul>
     * <p>
     * The returned {@code CallSite}'s method handle will have
     * a return type of {@code int} and accepts two parameters: the first argument
     * will be an {@code Enum} instance ({@code target}) and the second
     * will be {@code int} ({@code restart}).
     * <p>
     * If the {@code target} is {@code null}, then the method of the call site
     * returns {@literal -1}.
     * <p>
     * If the {@code target} is not {@code null}, then the method of the call site
     * returns the index of the first element in the {@code labels} array starting from
     * the {@code restart} index matching one of the following conditions:
     * <ul>
     *   <li>the element is of type {@code Class} that is assignable
     *       from the target's class; or</li>
     *   <li>the element is of type {@code String} and equals to the target
     *       enum constant's {@link Enum#name()}.</li>
     * </ul>
     * <p>
     * If no element in the {@code labels} array matches the target, then
     * the method of the call site return the length of the {@code labels} array.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller. When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName unused
     * @param invocationType The invocation type of the {@code CallSite} with two parameters,
     *                       an enum type, an {@code int}, and {@code int} as a return type.
     * @param labels case labels - {@code String} constants and {@code Class} instances,
     *               in any combination
     * @return a {@code CallSite} returning the first matching element as described above
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any element in the labels array is null, if the
     * invocation type is not a method type whose first parameter type is an enum type,
     * second parameter of type {@code int} and whose return type is {@code int},
     * or if {@code labels} contains an element that is not of type {@code String} or
     * {@code Class} of the target enum type.
     * @jvms 4.4.6 The CONSTANT_NameAndType_info Structure
     * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
     */
    public static CallSite enumSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Object... labels) {
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || invocationType.parameterType(0).isPrimitive()
            || !invocationType.parameterType(0).isEnum()
            || !invocationType.parameterType(1).equals(int.class))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(labels);

        labels = labels.clone();

        Class<?> enumClass = invocationType.parameterType(0);
        labels = Stream.of(labels).map(l -> convertEnumConstants(lookup, enumClass, l)).toArray();

        MethodHandle target =
                MethodHandles.insertArguments(DO_ENUM_SWITCH, 2, (Object) labels);
        target = target.asType(invocationType);

        return new ConstantCallSite(target);
    }

    private static <E extends Enum<E>> Object convertEnumConstants(MethodHandles.Lookup lookup, Class<?> enumClassTemplate, Object label) {
        if (label == null) {
            throw new IllegalArgumentException("null label found");
        }
        Class<?> labelClass = label.getClass();
        if (labelClass == Class.class) {
            if (label != enumClassTemplate) {
                throw new IllegalArgumentException("the Class label: " + label +
                                                   ", expected the provided enum class: " + enumClassTemplate);
            }
            return label;
        } else if (labelClass == String.class) {
            @SuppressWarnings("unchecked")
            Class<E> enumClass = (Class<E>) enumClassTemplate;
            try {
                return ConstantBootstraps.enumConstant(lookup, (String) label, enumClass);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        } else {
            throw new IllegalArgumentException("label with illegal type found: " + labelClass +
                                               ", expected label of type either String or Class");
        }
    }

    private static int doEnumSwitch(Enum<?> target, int startIndex, Object[] labels) {
        if (target == null)
            return -1;

        // Dumbest possible strategy
        Class<?> targetClass = target.getClass();
        for (int i = startIndex; i < labels.length; i++) {
            Object label = labels[i];
            if (label instanceof Class<?> c) {
                if (c.isAssignableFrom(targetClass))
                    return i;
            } else if (label == target) {
                return i;
            }
        }

        return labels.length;
    }

}
