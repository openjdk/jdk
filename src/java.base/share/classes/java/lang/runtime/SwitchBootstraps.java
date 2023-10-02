/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.Enum.EnumDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * @since 21
 */
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    private static final Object SENTINEL = new Object();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INSTANCEOF_CHECK;
    private static final MethodHandle IS_ASSIGNABLE_FROM_CHECK;
    private static final MethodHandle INTEGER_EQ_CHECK;
    private static final MethodHandle FLOAT_EQ_CHECK;
    private static final MethodHandle DOUBLE_EQ_CHECK;
    private static final MethodHandle BOOLEAN_EQ_CHECK;
    private static final MethodHandle OBJECT_EQ_CHECK;
    private static final MethodHandle ENUM_EQ_CHECK;
    private static final MethodHandle NULL_CHECK;
    private static final MethodHandle IS_ZERO;
    private static final MethodHandle CHECK_INDEX;
    private static final MethodHandle MAPPED_ENUM_LOOKUP;
    private static final HashMap<TypePairs, String> typePairToName;

    static {
        typePairToName = TypePairs.initialize();
        try {
            INSTANCEOF_CHECK = MethodHandles.permuteArguments(LOOKUP.findVirtual(Class.class, "isInstance",
                                                                                 MethodType.methodType(boolean.class, Object.class)),
                                                              MethodType.methodType(boolean.class, Object.class, Class.class), 1, 0);
            IS_ASSIGNABLE_FROM_CHECK = MethodHandles.permuteArguments(LOOKUP.findVirtual(Class.class, "isAssignableFrom",
                                                                                 MethodType.methodType(boolean.class, Class.class)),
                                                              MethodType.methodType(boolean.class, Class.class, Class.class), 1, 0);
            INTEGER_EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "integerEqCheck",
                                           MethodType.methodType(boolean.class, Object.class, Integer.class));
            FLOAT_EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "floatEqCheck",
                    MethodType.methodType(boolean.class, float.class, Float.class));
            DOUBLE_EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "doubleEqCheck",
                    MethodType.methodType(boolean.class, double.class, Double.class));
            BOOLEAN_EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "booleanEqCheck",
                    MethodType.methodType(boolean.class, boolean.class, Boolean.class));
            OBJECT_EQ_CHECK = LOOKUP.findStatic(Objects.class, "equals",
                                           MethodType.methodType(boolean.class, Object.class, Object.class));
            ENUM_EQ_CHECK = LOOKUP.findStatic(SwitchBootstraps.class, "enumEqCheck",
                                           MethodType.methodType(boolean.class, Object.class, EnumDesc.class, MethodHandles.Lookup.class, ResolvedEnumLabel.class));
            NULL_CHECK = LOOKUP.findStatic(Objects.class, "isNull",
                                           MethodType.methodType(boolean.class, Object.class));
            IS_ZERO = LOOKUP.findStatic(SwitchBootstraps.class, "isZero",
                                           MethodType.methodType(boolean.class, int.class));
            CHECK_INDEX = LOOKUP.findStatic(Objects.class, "checkIndex",
                                           MethodType.methodType(int.class, int.class, int.class));
            MAPPED_ENUM_LOOKUP = LOOKUP.findStatic(SwitchBootstraps.class, "mappedEnumLookup",
                                                   MethodType.methodType(int.class, Enum.class, MethodHandles.Lookup.class,
                                                                         Class.class, EnumDesc[].class, EnumMap.class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a target of a reference type.  The static
     * arguments are an array of case labels which must be non-null and of type
     * {@code String} or {@code Integer} or {@code Class} or {@code EnumDesc}.
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
     *   <li>the element is of type {@code EnumDesc}, that describes a constant that is
     *       equals to the target.</li>
     * </ul>
     * <p>
     * If no element in the {@code labels} array matches the target, then
     * the method of the call site return the length of the {@code labels} array.
     * <p>
     * The value of the {@code restart} index must be between {@code 0} (inclusive) and
     * the length of the {@code labels} array (inclusive),
     * both  or an {@link IndexOutOfBoundsException} is thrown.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName unused
     * @param invocationType The invocation type of the {@code CallSite} with two parameters,
     *                       a reference type, an {@code int}, and {@code int} as a return type.
     * @param labels case labels - {@code String} and {@code Integer} constants
     *               and {@code Class} and {@code EnumDesc} instances, in any combination
     * @return a {@code CallSite} returning the first matching element as described above
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if any element in the labels array is null, if the
     * invocation type is not not a method type of first parameter of a reference type,
     * second parameter of type {@code int} and with {@code int} as its return type,
     * or if {@code labels} contains an element that is not of type {@code String},
     * {@code Integer}, {@code Class} or {@code EnumDesc}.
     * @jvms 4.4.6 The CONSTANT_NameAndType_info Structure
     * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Object... labels) {
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || !invocationType.parameterType(1).equals(int.class))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(labels);

        labels = labels.clone();
        Stream.of(labels).forEach(SwitchBootstraps::verifyLabel);

        MethodHandle target;
        if (invocationType.parameterType(0).isPrimitive()) {
            target = createMethodHandleSwitch(lookup, labels, invocationType.parameterType(0));
        }
        else {
            target = createMethodHandleSwitch(lookup, labels, Object.class);
        }

        return new ConstantCallSite(target);
    }

    private static void verifyLabel(Object label) {
        if (label == null) {
            throw new IllegalArgumentException("null label found");
        }
        Class<?> labelClass = label.getClass();
        if (labelClass != Class.class &&
            labelClass != String.class &&
            labelClass != Integer.class &&
            labelClass != Float.class &&
            labelClass != Double.class &&
            labelClass != Boolean.class &&
            labelClass != EnumDesc.class) {
            throw new IllegalArgumentException("label with illegal type found: " + label.getClass());
        }
    }

    /*
     * Construct test chains for labels inside switch, to handle switch repeats:
     * switch (idx) {
     *     case 0 -> if (selector matches label[0]) return 0; else if (selector matches label[1]) return 1; else ...
     *     case 1 -> if (selector matches label[1]) return 1; else ...
     *     ...
     * }
     */
    private static MethodHandle createRepeatIndexSwitch(MethodHandles.Lookup lookup, Object[] labels, Class<?> selectorType) {
        MethodHandle def = MethodHandles.dropArguments(MethodHandles.constant(int.class, labels.length), 0, selectorType);
        MethodHandle[] testChains = new MethodHandle[labels.length];
        List<Object> labelsList = List.of(labels).reversed();
        // unconditionally exact patterns always match
        MethodHandle trueDef = MethodHandles.dropArguments(MethodHandles.constant(boolean.class, true), 0, selectorType, Object.class);
        for (int i = 0; i < labels.length; i++) {
            MethodHandle test = def;
            int idx = labels.length - 1;
            List<Object> currentLabels = labelsList.subList(0, labels.length - i);
            for (int j = 0; j < currentLabels.size(); j++, idx--) {
                Object currentLabel = currentLabels.get(j);
                Object testLabel = currentLabel;
                if (j + 1 < currentLabels.size() && currentLabels.get(j + 1) == currentLabel) continue;
                MethodHandle currentTest;
                if (currentLabel instanceof Class<?> currentLabelClass) {
                    if (unconditionalExactnessCheck(selectorType, currentLabelClass)) {
                        currentTest = trueDef;
                    } else if (currentLabelClass.isPrimitive()) {
                         if (selectorType.isInstance(Object.class)) {
                            currentTest = INSTANCEOF_CHECK;
                            if (currentLabelClass.isAssignableFrom(byte.class)) { testLabel = Byte.class; }
                            else if (currentLabelClass.isAssignableFrom(short.class)) { testLabel = Short.class; }
                            else if (currentLabelClass.isAssignableFrom(char.class)) { testLabel = Character.class; }
                            else if (currentLabelClass.isAssignableFrom(int.class)) { testLabel = Integer.class; }
                            else if (currentLabelClass.isAssignableFrom(double.class)) { testLabel = Double.class; }
                            else if (currentLabelClass.isAssignableFrom(float.class)) { testLabel = Float.class; }
                            else { testLabel = Long.class; }
                        } else if (!selectorType.isPrimitive()) {
                            currentTest = INSTANCEOF_CHECK;
                            if (currentLabelClass.equals(byte.class)) { testLabel = Byte.class; }
                            else if (currentLabelClass.equals(short.class)) { testLabel = Short.class; }
                            else if (currentLabelClass.equals(char.class)) { testLabel = Character.class; }
                            else if (currentLabelClass.equals(int.class)) { testLabel = Integer.class; }
                            else if (currentLabelClass.equals(double.class)) { testLabel = Double.class; }
                            else if (currentLabelClass.equals(float.class)) { testLabel = Float.class; }
                            else { testLabel = Long.class; }
                        } else {
                            MethodHandle exactnessCheck;
                            try {
                                TypePairs typePair = TypePairs.of(selectorType, currentLabelClass);
                                String methodName = typePairToName.get(typePair);
                                MethodType methodType = MethodType.methodType(boolean.class, typePair.from);
                                exactnessCheck = lookup.findStatic(ExactnessMethods.class, methodName, methodType).asType(MethodType.methodType(boolean.class, selectorType));
                            }
                            catch (ReflectiveOperationException e) {
                                throw new ExceptionInInitializerError(e);
                            }
                            currentTest = MethodHandles.dropArguments(exactnessCheck, 1, Object.class);
                        }
                    } else if (selectorType.isPrimitive()) {
                        currentTest = IS_ASSIGNABLE_FROM_CHECK;
                        currentTest = MethodHandles.dropArguments(currentTest, 0, selectorType);
                        currentTest = MethodHandles.insertArguments(currentTest, 1, Class.class);
                        testLabel = currentLabelClass;
                    } else {
                        currentTest = INSTANCEOF_CHECK;
                    }
                } else if (currentLabel instanceof Integer ii) {
                    if (selectorType.equals(boolean.class)) {
                        testLabel = ii.intValue() == 1;
                        currentTest = BOOLEAN_EQ_CHECK;
                    } else {
                        currentTest = INTEGER_EQ_CHECK;
                    }
                }
                else if (selectorType.isPrimitive() && currentLabel instanceof Float) {
                    currentTest = FLOAT_EQ_CHECK;
                }
                else if (selectorType.isPrimitive() && currentLabel instanceof Double) {
                    currentTest = DOUBLE_EQ_CHECK;
                }
                else if (selectorType.isPrimitive() && currentLabel instanceof Boolean) {
                    currentTest = BOOLEAN_EQ_CHECK;
                }
                else if (currentLabel instanceof EnumDesc) {
                    currentTest = MethodHandles.insertArguments(ENUM_EQ_CHECK, 2, lookup, new ResolvedEnumLabel());
                    currentTest = MethodHandles.explicitCastArguments(currentTest,
                            MethodType.methodType(boolean.class, selectorType, EnumDesc.class));
                } else {
                    currentTest = OBJECT_EQ_CHECK;
                }
                test = MethodHandles.guardWithTest(MethodHandles.insertArguments(currentTest, 1, testLabel),
                                                   MethodHandles.dropArguments(MethodHandles.constant(int.class, idx), 0, selectorType),
                                                   test);
            }
            testChains[i] = MethodHandles.dropArguments(test, 0, int.class);
        }

        return MethodHandles.tableSwitch(MethodHandles.dropArguments(def, 0, int.class), testChains);
    }

    /*
     * Construct code that maps the given selector and repeat index to a case label number:
     * if (selector == null) return -1;
     * else return "createRepeatIndexSwitch(labels)"
     */
    private static MethodHandle createMethodHandleSwitch(MethodHandles.Lookup lookup, Object[] labels, Class<?> selectorType) {
        if (!selectorType.isPrimitive() && !selectorType.isEnum()) {
            selectorType = Object.class;
        }

        MethodHandle mainTest;
        MethodHandle def = MethodHandles.dropArguments(MethodHandles.constant(int.class, labels.length), 0,  selectorType);
        if (labels.length > 0) {
            mainTest = createRepeatIndexSwitch(lookup, labels, selectorType);
        } else {
            mainTest = MethodHandles.dropArguments(def, 0, int.class);
        }

        MethodHandle body;
        if (!selectorType.isPrimitive()) {
            var castedNullCheckMT = MethodHandles.explicitCastArguments(NULL_CHECK,
                    MethodType.methodType(boolean.class, selectorType));
            body = MethodHandles.guardWithTest(MethodHandles.dropArguments(castedNullCheckMT, 0, int.class),
                    MethodHandles.dropArguments(MethodHandles.constant(int.class, -1), 0, int.class, selectorType),
                    mainTest);
        } else {
            body = mainTest;
        }

        MethodHandle switchImpl =
                MethodHandles.permuteArguments(body, MethodType.methodType(int.class, selectorType, int.class), 1, 0);
        return withIndexCheck(switchImpl, labels.length);
    }

    private static boolean floatEqCheck(float value, Float constant) {
        if (Float.valueOf(value).equals(constant.floatValue())) {
            return true;
        }
        return false;
    }

    private static boolean doubleEqCheck(double value, Double constant) {
        if (Double.valueOf(value).equals(constant.doubleValue())) {
            return true;
        }
        return false;
    }

    private static boolean booleanEqCheck(boolean value, Boolean constant) {
        if (Boolean.valueOf(value).equals(constant.booleanValue())) {
            return true;
        }
        return false;
    }

    private static boolean integerEqCheck(Object value, Integer constant) {
        if (value instanceof Number input && constant.intValue() == input.intValue()) {
            return true;
        } else if (value instanceof Character input && constant.intValue() == input.charValue()) {
            return true;
        }
        else if (value instanceof Boolean input && constant.intValue() == (input.booleanValue() ? 1 : 0)) {
            return true;
        }
        return false;
    }

    private static boolean isZero(int value) {
        return value == 0;
    }

    private static boolean unconditionalExactnessCheck(Object selectorType, Class<?> targetType) {
        if ((selectorType.equals(byte.class) && targetType.equals(Byte.class)) ||
            (selectorType.equals(char.class) && targetType.equals(Character.class)) ||
            (selectorType.equals(long.class) && targetType.equals(Long.class)) ||
            (selectorType.equals(double.class) && targetType.equals(Double.class)) ||
            (selectorType.equals(float.class) && targetType.equals(Float.class)) ||
            (selectorType.equals(short.class) && targetType.equals(Short.class)) ||
            (selectorType.equals(int.class) && targetType.equals(Integer.class)))
            return true;
        else if (selectorType.equals(targetType) || (selectorType.equals(byte.class) && !targetType.equals(char.class)  ||
                (selectorType.equals(short.class) && (targetType.equals(int.class)   || targetType.equals(long.class) || targetType.equals(float.class) || targetType.equals(double.class))) ||
                (selectorType.equals(char.class)  && (targetType.equals(int.class)   || targetType.equals(long.class) || targetType.equals(float.class) || targetType.equals(double.class))) ||
                (selectorType.equals(long.class) && (targetType.equals(long.class))) ||
                (selectorType.equals(int.class) && (targetType.equals(double.class)  || targetType.equals(long.class))) ||
                (selectorType.equals(float.class) && (targetType.equals(double.class))))) return true;
        return false;
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
     * <p>
     * The value of the {@code restart} index must be between {@code 0} (inclusive) and
     * the length of the {@code labels} array (inclusive),
     * both  or an {@link IndexOutOfBoundsException} is thrown.
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

        MethodHandle target;
        boolean constantsOnly = Stream.of(labels).allMatch(l -> enumClass.isAssignableFrom(EnumDesc.class));

        if (labels.length > 0 && constantsOnly) {
            //If all labels are enum constants, construct an optimized handle for repeat index 0:
            //if (selector == null) return -1
            //else if (idx == 0) return mappingArray[selector.ordinal()]; //mapping array created lazily
            //else return "createRepeatIndexSwitch(labels)"
            MethodHandle body =
                    MethodHandles.guardWithTest(MethodHandles.dropArguments(NULL_CHECK, 0, int.class),
                                                MethodHandles.dropArguments(MethodHandles.constant(int.class, -1), 0, int.class, Object.class),
                                                MethodHandles.guardWithTest(MethodHandles.dropArguments(IS_ZERO, 1, Object.class),
                                                                            createRepeatIndexSwitch(lookup, labels, invocationType.parameterType(0)),
                                                                            MethodHandles.insertArguments(MAPPED_ENUM_LOOKUP, 1, lookup, enumClass, labels, new EnumMap())));
            target = MethodHandles.permuteArguments(body, MethodType.methodType(int.class, Object.class, int.class), 1, 0);
        } else {
            target = createMethodHandleSwitch(lookup, labels, invocationType.parameterType(0));
        }

        target = target.asType(invocationType);
        target = withIndexCheck(target, labels.length);

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
            return EnumDesc.of(enumClassTemplate.describeConstable().get(), (String) label);
        } else {
            throw new IllegalArgumentException("label with illegal type found: " + labelClass +
                                               ", expected label of type either String or Class");
        }
    }

    private static <T extends Enum<T>> int mappedEnumLookup(T value, MethodHandles.Lookup lookup, Class<T> enumClass, EnumDesc<?>[] labels, EnumMap enumMap) {
        if (enumMap.map == null) {
            T[] constants = SharedSecrets.getJavaLangAccess().getEnumConstantsShared(enumClass);
            int[] map = new int[constants.length];
            int ordinal = 0;

            for (T constant : constants) {
                map[ordinal] = labels.length;

                for (int i = 0; i < labels.length; i++) {
                    if (Objects.equals(labels[i].constantName(), constant.name())) {
                        map[ordinal] = i;
                        break;
                    }
                }

                ordinal++;
            }
        }
        return enumMap.map[value.ordinal()];
    }

    private static boolean enumEqCheck(Object value, EnumDesc<?> label, MethodHandles.Lookup lookup, ResolvedEnumLabel resolvedEnum) {
        if (resolvedEnum.resolvedEnum == null) {
            Object resolved;

            try {
                Class<?> clazz = label.constantType().resolveConstantDesc(lookup);

                if (value.getClass() != clazz) {
                    return false;
                }

                resolved = label.resolveConstantDesc(lookup);
            } catch (IllegalArgumentException | ReflectiveOperationException ex) {
                resolved = SENTINEL;
            }

            resolvedEnum.resolvedEnum = resolved;
        }

        return value == resolvedEnum.resolvedEnum;
    }

    private static MethodHandle withIndexCheck(MethodHandle target, int labelsCount) {
        MethodHandle checkIndex = MethodHandles.insertArguments(CHECK_INDEX, 1, labelsCount + 1);

        return MethodHandles.filterArguments(target, 1, checkIndex);
    }

    private static final class ResolvedEnumLabel {
        @Stable
        public Object resolvedEnum;
    }

    private static final class EnumMap {
        @Stable
        public int[] map;
    }

    static class TypePairs {
        public Class<?> from, to;

        public static TypePairs of(Class<?> from,  Class<?> to) {
            if (from == byte.class || from == short.class || from == char.class) {
                from = int.class;
            }
            return new TypePairs(from, to);
        }

        private TypePairs(Class<?> from,  Class<?> to) {
            this.from = from;
            this.to = to;
        }

        public static HashMap<TypePairs, String> initialize() {
            HashMap<TypePairs, String> typePairToName = new HashMap<>();
            typePairToName.put(new TypePairs(byte.class,   char.class),   "int_char");      // redirected
            typePairToName.put(new TypePairs(short.class,  byte.class),   "int_byte");      // redirected
            typePairToName.put(new TypePairs(short.class,  char.class),   "int_char");      // redirected
            typePairToName.put(new TypePairs(char.class,   byte.class),   "int_byte");      // redirected
            typePairToName.put(new TypePairs(char.class,   short.class),  "int_short");     // redirected
            typePairToName.put(new TypePairs(int.class,    byte.class),   "int_byte");
            typePairToName.put(new TypePairs(int.class,    short.class),  "int_short");
            typePairToName.put(new TypePairs(int.class,    char.class),   "int_char");
            typePairToName.put(new TypePairs(int.class,    float.class),  "int_float");
            typePairToName.put(new TypePairs(long.class,   byte.class),   "long_byte");
            typePairToName.put(new TypePairs(long.class,   short.class),  "long_short");
            typePairToName.put(new TypePairs(long.class,   char.class),   "long_char");
            typePairToName.put(new TypePairs(long.class,   int.class),    "long_int");
            typePairToName.put(new TypePairs(long.class,   float.class),  "long_float");
            typePairToName.put(new TypePairs(long.class,   double.class), "long_double");
            typePairToName.put(new TypePairs(float.class,  byte.class),   "float_byte");
            typePairToName.put(new TypePairs(float.class,  short.class),  "float_short");
            typePairToName.put(new TypePairs(float.class,  char.class),   "float_char");
            typePairToName.put(new TypePairs(float.class,  int.class),    "float_int");
            typePairToName.put(new TypePairs(float.class,  long.class),   "float_long");
            typePairToName.put(new TypePairs(double.class, byte.class),   "double_byte");
            typePairToName.put(new TypePairs(double.class, short.class),  "double_short");
            typePairToName.put(new TypePairs(double.class, char.class),   "double_char");
            typePairToName.put(new TypePairs(double.class, int.class),    "double_int");
            typePairToName.put(new TypePairs(double.class, long.class),   "double_long");
            typePairToName.put(new TypePairs(double.class, float.class),  "double_float");
            return typePairToName;
        }

        @Override
        public int hashCode() {
            int code = 0;
            code += from.hashCode();
            code += to.hashCode();
            return code;
        }

        @Override
        public boolean equals(Object testName) {
            if ((!(testName instanceof TypePairs testNameAsName))) return false;
            else {
                return this.from.equals(testNameAsName.from) &&
                        this.to.equals(testNameAsName.to);
            }
        }
    }
}
