/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import jdk.internal.access.SharedSecrets;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;

import jdk.internal.classfile.impl.DirectCodeBuilder;
import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.misc.PreviewFeatures;
import jdk.internal.vm.annotation.Stable;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import static jdk.internal.constant.ConstantUtils.classDesc;
import static jdk.internal.constant.ConstantUtils.referenceClassDesc;

import sun.invoke.util.Wrapper;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * @since 21
 */
public final class SwitchBootstraps {

    private SwitchBootstraps() {}

    private static final Object SENTINEL = new Object();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final boolean previewEnabled = PreviewFeatures.isEnabled();

    private static final ClassDesc CD_BiPredicate = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/function/BiPredicate;");
    private static final ClassDesc CD_Objects = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/Objects;");

    private static final MethodTypeDesc CHECK_INDEX_DESCRIPTOR =
            MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_int);
    private static final MethodTypeDesc MTD_TYPE_SWITCH = MethodTypeDescImpl.ofValidated(CD_int,
            CD_Object,
            CD_int);
    private static final MethodTypeDesc MTD_TYPE_SWITCH_EXTRA = MethodTypeDescImpl.ofValidated(CD_int,
            CD_Object,
            CD_int,
            CD_BiPredicate,
            CD_List);
    private static final MethodType MT_TYPE_SWITCH_EXTRA = MethodType.methodType(int.class,
            Object.class,
            int.class,
            BiPredicate.class,
            List.class);
    private static final MethodType MT_TYPE_SWITCH = MethodType.methodType(int.class,
            Object.class,
            int.class);
    private static final List<StackMapFrameInfo.VerificationTypeInfo> TYPE_SWITCH_LOCALS = List.of(
            StackMapFrameInfo.ObjectVerificationTypeInfo.of(CD_Object), StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER
    );
    private static final List<StackMapFrameInfo.VerificationTypeInfo> TYPE_SWITCH_EXTRA_LOCALS = List.of(
            StackMapFrameInfo.ObjectVerificationTypeInfo.of(CD_Object), StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER,
            StackMapFrameInfo.ObjectVerificationTypeInfo.of(CD_BiPredicate), StackMapFrameInfo.ObjectVerificationTypeInfo.of(CD_List)
    );

    private static class StaticHolders {
        private static final MethodHandle MAPPED_ENUM_SWITCH;

        static {
            try {
                MAPPED_ENUM_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class, "mappedEnumSwitch",
                                                       MethodType.methodType(int.class, Enum.class, int.class, MethodHandles.Lookup.class,
                                                                             Class.class, EnumDesc[].class, MappedEnumCache.class));
            }
            catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
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
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if any element in the labels array is null
     * @throws IllegalArgumentException if the invocation type is not a method type of first parameter of a reference type,
     *                                  second parameter of type {@code int} and with {@code int} as its return type,
     * @throws IllegalArgumentException if {@code labels} contains an element that is not of type {@code String},
     *                                  {@code Integer}, {@code Long}, {@code Float}, {@code Double}, {@code Boolean},
     *                                  {@code Class} or {@code EnumDesc}.
     * @throws IllegalArgumentException if {@code labels} contains an element that is not of type {@code Boolean}
     *                                  when {@code target} is a {@code Boolean.class}.
     * @jvms 4.4.6 The CONSTANT_NameAndType_info Structure
     * @jvms 4.4.10 The CONSTANT_Dynamic_info and CONSTANT_InvokeDynamic_info Structures
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Object... labels) {
        Class<?> selectorType = invocationType.parameterType(0);
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || !invocationType.parameterType(1).equals(int.class))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);

        for (Object l : labels) { // implicit null-check
            verifyLabel(l, selectorType);
        }

        MethodHandle target = generateTypeSwitch(lookup, selectorType, labels);
        target = target.asType(invocationType);
        return new ConstantCallSite(target);
    }

    private static void verifyLabel(Object label, Class<?> selectorType) {
        if (label == null) {
            throw new IllegalArgumentException("null label found");
        }
        Class<?> labelClass = label.getClass();

        if (labelClass != Class.class &&
            labelClass != String.class &&
            labelClass != Integer.class &&

            ((labelClass != Float.class &&
              labelClass != Long.class &&
              labelClass != Double.class &&
              labelClass != Boolean.class) ||
              ((selectorType.equals(boolean.class) || selectorType.equals(Boolean.class)) && labelClass != Boolean.class && labelClass != Class.class) ||
             !previewEnabled) &&

            labelClass != EnumDesc.class) {
            throw new IllegalArgumentException("label with illegal type found: " + label.getClass());
        }
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

        labels = labels.clone(); // implicit null check

        Class<?> enumClass = invocationType.parameterType(0);
        boolean constantsOnly = true;
        int len = labels.length;

        for (int i = 0; i < len; i++) {
            Object convertedLabel =
                    convertEnumConstants(lookup, enumClass, labels[i]);
            labels[i] = convertedLabel;
            if (constantsOnly)
                constantsOnly = convertedLabel instanceof EnumDesc;
        }

        MethodHandle target;

        if (labels.length > 0 && constantsOnly) {
            //If all labels are enum constants, construct an optimized handle for repeat index 0:
            //if (selector == null) return -1
            //else if (idx == 0) return mappingArray[selector.ordinal()]; //mapping array created lazily
            //else return "typeSwitch(labels)"
            EnumDesc<?>[] enumDescLabels =
                    Arrays.copyOf(labels, labels.length, EnumDesc[].class);
            target = MethodHandles.insertArguments(StaticHolders.MAPPED_ENUM_SWITCH, 2, lookup, enumClass, enumDescLabels, new MappedEnumCache());
        } else {
            target = generateTypeSwitch(lookup, invocationType.parameterType(0), labels);
        }
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
            return EnumDesc.of(referenceClassDesc(enumClassTemplate), (String) label);
        } else {
            throw new IllegalArgumentException("label with illegal type found: " + labelClass +
                                               ", expected label of type either String or Class");
        }
    }

    private static <T extends Enum<T>> int mappedEnumSwitch(T value, int restartIndex, MethodHandles.Lookup lookup, Class<T> enumClass, EnumDesc<?>[] labels, MappedEnumCache enumCache) throws Throwable {
        if (value == null) {
            return -1;
        }

        if (restartIndex != 0) {
            MethodHandle generatedSwitch = enumCache.generatedSwitch;
            if (generatedSwitch == null) {
                synchronized (enumCache) {
                    generatedSwitch = enumCache.generatedSwitch;

                    if (generatedSwitch == null) {
                        generatedSwitch =
                                generateTypeSwitch(lookup, enumClass, labels)
                                        .asType(MethodType.methodType(int.class,
                                                                      Enum.class,
                                                                      int.class));
                        enumCache.generatedSwitch = generatedSwitch;
                    }
                }
            }

            return (int) generatedSwitch.invokeExact(value, restartIndex);
        }

        int[] constantsMap = enumCache.constantsMap;

        if (constantsMap == null) {
            synchronized (enumCache) {
                constantsMap = enumCache.constantsMap;

                if (constantsMap == null) {
                    T[] constants = SharedSecrets.getJavaLangAccess()
                                                 .getEnumConstantsShared(enumClass);
                    constantsMap = new int[constants.length];
                    int ordinal = 0;

                    for (T constant : constants) {
                        constantsMap[ordinal] = labels.length;

                        for (int i = 0; i < labels.length; i++) {
                            if (Objects.equals(labels[i].constantName(),
                                               constant.name())) {
                                constantsMap[ordinal] = i;
                                break;
                            }
                        }

                        ordinal++;
                    }

                    enumCache.constantsMap = constantsMap;
                }
            }
        }

        return constantsMap[value.ordinal()];
    }

    private static final class ResolvedEnumLabels implements BiPredicate<Integer, Object> {

        private final MethodHandles.Lookup lookup;
        private final EnumDesc<?>[] enumDescs;
        @Stable
        private final Object[] resolvedEnum;

        public ResolvedEnumLabels(MethodHandles.Lookup lookup, EnumDesc<?>[] enumDescs) {
            this.lookup = lookup;
            this.enumDescs = enumDescs;
            this.resolvedEnum = new Object[enumDescs.length];
        }

        @Override
        public boolean test(Integer labelIndex, Object value) {
            Object result = resolvedEnum[labelIndex];

            if (result == null) {
                try {
                    if (!(value instanceof Enum<?> enumValue)) {
                        return false;
                    }

                    EnumDesc<?> label = enumDescs[labelIndex];
                    Class<?> clazz = label.constantType().resolveConstantDesc(lookup);

                    if (enumValue.getDeclaringClass() != clazz) {
                        return false;
                    }

                    result = label.resolveConstantDesc(lookup);
                } catch (IllegalArgumentException | ReflectiveOperationException ex) {
                    result = SENTINEL;
                }

                resolvedEnum[labelIndex] = result;
            }

            return result == value;
        }
    }

    private static final class MappedEnumCache {
        @Stable
        public int[] constantsMap;
        @Stable
        public MethodHandle generatedSwitch;
    }

    /**
     * Check if the labelConstants can be converted statically to bytecode, or
     * whether we'll need to compute and pass in extra information at the call site.
     */
    private static boolean needsExtraInfo(Class<?> selectorType, Object[] labelConstants) {
        for (int idx = labelConstants.length - 1; idx >= 0; idx--) {
            Object currentLabel = labelConstants[idx];
            if (currentLabel instanceof Class<?> classLabel) {
                // No extra info needed for exact matches or primitives
                if (unconditionalExactnessCheck(selectorType, classLabel) || classLabel.isPrimitive()) {
                    continue;
                }
                // Hidden classes - or arrays thereof - can't be nominally
                // represented. Passed in as arguments.
                while (classLabel.isArray()) {
                    classLabel = classLabel.getComponentType();
                }
                if (classLabel.isHidden()) {
                    return true;
                }
            } else if (currentLabel instanceof EnumDesc<?>) {
                // EnumDescs labels needs late binding
                return true;
            }
        }
        return false;
    }
    /*
     * Construct test chains for labels inside switch, to handle switch repeats:
     * switch (idx) {
     *     case 0 -> if (selector matches label[0]) return 0;
     *     case 1 -> if (selector matches label[1]) return 1;
     *     ...
     * }
     */
    private static Consumer<CodeBuilder> generateTypeSwitchSkeleton(Class<?> selectorType, Object[] labelConstants, List<EnumDesc<?>> enumDescs, List<Class<?>> extraClassLabels) {
        int SELECTOR_OBJ        = 0;
        int RESTART_IDX         = 1;
        int ENUM_CACHE          = 2;
        int EXTRA_CLASS_LABELS  = 3;

        var locals = enumDescs == null && extraClassLabels == null ? TYPE_SWITCH_LOCALS : TYPE_SWITCH_EXTRA_LOCALS;

        return cb -> {
            // Objects.checkIndex(RESTART_IDX, labelConstants + 1)
            var stackMapFrames = new ArrayList<StackMapFrameInfo>(labelConstants.length * 2);
            cb.iload(RESTART_IDX)
              .loadConstant(labelConstants.length + 1)
              .invokestatic(CD_Objects, "checkIndex", CHECK_INDEX_DESCRIPTOR)
              .pop()
              .aload(SELECTOR_OBJ);
            Label nonNullLabel = cb.newLabel();
            cb.ifnonnull(nonNullLabel)
              .iconst_m1()
              .ireturn()
              .labelBinding(nonNullLabel);
            stackMapFrames.add(StackMapFrameInfo.of(nonNullLabel, locals, List.of()));
            if (labelConstants.length == 0) {
                cb.loadConstant(0)
                  .ireturn()
                  .with(StackMapTableAttribute.of(stackMapFrames));
                DirectCodeBuilder.withMaxs(cb, 2, locals.size()); // checkIndex uses 2
                return;
            }
            cb.iload(RESTART_IDX);
            Label dflt = cb.newLabel();
            Label[] caseTargets = new Label[labelConstants.length];
            Label[] caseNext = new Label[labelConstants.length];
            Object[] caseLabels = new Object[labelConstants.length];
            SwitchCase[] switchCases = new SwitchCase[labelConstants.length];
            Object lastLabel = null;
            for (int idx = labelConstants.length - 1; idx >= 0; idx--) {
                Object currentLabel = labelConstants[idx];
                Label target = cb.newLabel();
                stackMapFrames.add(StackMapFrameInfo.of(target, locals, List.of()));
                Label next;
                if (lastLabel == null) {
                    next = dflt;
                } else if (lastLabel.equals(currentLabel)) {
                    next = caseNext[idx + 1];
                } else {
                    next = caseTargets[idx + 1];
                }
                lastLabel = currentLabel;
                caseTargets[idx] = target;
                caseNext[idx] = next;
                caseLabels[idx] = currentLabel;
                switchCases[idx] = SwitchCase.of(idx, target);
            }
            cb.tableswitch(0, labelConstants.length - 1, dflt, Arrays.asList(switchCases));
            for (int idx = 0; idx < labelConstants.length; idx++) {
                Label next = caseNext[idx];
                Object caseLabel = caseLabels[idx];
                cb.labelBinding(caseTargets[idx]);
                if (caseLabel instanceof Class<?> classLabel) {
                    if (isNotValidPair(selectorType, caseLabel)){
                        cb.goto_(next);
                        continue;
                    }
                    else if (unconditionalExactnessCheck(selectorType, classLabel)) {
                        //nothing - unconditionally use this case
                    } else if (classLabel.isPrimitive()) {
                        if (!selectorType.isPrimitive() && !Wrapper.isWrapperNumericOrBooleanType(selectorType)) {
                            // Object o = ...
                            // o instanceof Wrapped(float)
                            cb.aload(SELECTOR_OBJ)
                              .instanceOf(Wrapper.forBasicType(classLabel).wrapperClassDescriptor())
                              .ifeq(next);
                        } else if (!unconditionalExactnessCheck(Wrapper.asPrimitiveType(selectorType), classLabel)) {
                            // Integer i = ... or int i = ...
                            // o instanceof float
                            Label notNumber = cb.newLabel(); // this label may end up unbound
                            cb.aload(SELECTOR_OBJ)
                              .instanceOf(CD_Number);
                            if (selectorType == long.class || selectorType == float.class || selectorType == double.class ||
                                selectorType == Long.class || selectorType == Float.class || selectorType == Double.class) {
                                cb.ifeq(next);
                            } else {
                                cb.ifeq(notNumber);
                            }
                            cb.aload(SELECTOR_OBJ)
                              .checkcast(CD_Number);
                            if (selectorType == long.class || selectorType == Long.class) {
                                cb.invokevirtual(CD_Number,
                                        "longValue",
                                        MethodTypeDesc.of(CD_long));
                            } else if (selectorType == float.class || selectorType == Float.class) {
                                cb.invokevirtual(CD_Number,
                                        "floatValue",
                                        MethodTypeDesc.of(CD_float));
                            } else if (selectorType == double.class || selectorType == Double.class) {
                                cb.invokevirtual(CD_Number,
                                        "doubleValue",
                                        MethodTypeDesc.of(CD_double));
                            } else {
                                Label compare = cb.newLabel();
                                cb.invokevirtual(CD_Number,
                                        "intValue",
                                        MethodTypeDesc.of(CD_int))
                                  .goto_(compare)
                                  .labelBinding(notNumber);
                                stackMapFrames.add(StackMapFrameInfo.of(notNumber, locals, List.of()));
                                cb.aload(SELECTOR_OBJ)
                                  .instanceOf(CD_Character)
                                  .ifeq(next)
                                  .aload(SELECTOR_OBJ)
                                  .checkcast(CD_Character)
                                  .invokevirtual(CD_Character,
                                        "charValue",
                                        MethodTypeDesc.of(CD_char))
                                  .labelBinding(compare);
                                stackMapFrames.add(StackMapFrameInfo.of(compare, locals, List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER)));
                            }

                            TypePairs typePair = TypePairs.of(Wrapper.asPrimitiveType(selectorType), classLabel);
                            String methodName = TypePairs.typePairToName.get(typePair);
                            cb.invokestatic(ConstantUtils.referenceClassDesc(ExactConversionsSupport.class),
                                    methodName,
                                    MethodTypeDesc.of(CD_boolean, classDesc(typePair.from)))
                              .ifeq(next);
                        }
                    } else {
                        Optional<ClassDesc> classLabelConstableOpt = classLabel.describeConstable();
                        if (classLabelConstableOpt.isPresent()) {
                            cb.aload(SELECTOR_OBJ)
                              .instanceOf(classLabelConstableOpt.orElseThrow())
                              .ifeq(next);
                        } else {
                            cb.aload(EXTRA_CLASS_LABELS)
                              .loadConstant(extraClassLabels.size())
                              .invokeinterface(CD_List,
                                    "get",
                                    MethodTypeDesc.of(CD_Object,
                                            CD_int))
                              .checkcast(CD_Class)
                              .aload(SELECTOR_OBJ)
                              .invokevirtual(CD_Class,
                                    "isInstance",
                                    MethodTypeDesc.of(CD_boolean,
                                            CD_Object))
                              .ifeq(next);
                            extraClassLabels.add(classLabel);
                        }
                    }
                } else if (caseLabel instanceof EnumDesc<?> enumLabel) {
                    int enumIdx = enumDescs.size();
                    enumDescs.add(enumLabel);
                    cb.aload(ENUM_CACHE)
                      .loadConstant(enumIdx)
                      .invokestatic(CD_Integer,
                            "valueOf",
                            MethodTypeDesc.of(CD_Integer,
                                    CD_int))
                      .aload(SELECTOR_OBJ)
                      .invokeinterface(CD_BiPredicate,
                            "test",
                            MethodTypeDesc.of(CD_boolean,
                                    CD_Object,
                                    CD_Object))
                      .ifeq(next);
                } else if (caseLabel instanceof String stringLabel) {
                    cb.ldc(stringLabel)
                      .aload(SELECTOR_OBJ)
                      .invokevirtual(CD_Object,
                            "equals",
                            MethodTypeDesc.of(CD_boolean,
                                    CD_Object))
                      .ifeq(next);
                } else if (caseLabel instanceof Integer integerLabel) {
                    Label compare = cb.newLabel();
                    Label notNumber = cb.newLabel();
                    cb.aload(SELECTOR_OBJ)
                      .instanceOf(CD_Number)
                      .ifeq(notNumber)
                      .aload(SELECTOR_OBJ)
                      .checkcast(CD_Number)
                      .invokevirtual(CD_Number,
                            "intValue",
                            MethodTypeDesc.of(CD_int))
                      .goto_(compare)
                      .labelBinding(notNumber);
                    stackMapFrames.add(StackMapFrameInfo.of(notNumber, locals, List.of()));
                    cb.aload(SELECTOR_OBJ)
                      .instanceOf(CD_Character)
                      .ifeq(next)
                      .aload(SELECTOR_OBJ)
                      .checkcast(CD_Character)
                      .invokevirtual(CD_Character,
                            "charValue",
                            MethodTypeDesc.of(CD_char))
                      .labelBinding(compare);
                    stackMapFrames.add(StackMapFrameInfo.of(compare, locals, List.of(StackMapFrameInfo.SimpleVerificationTypeInfo.INTEGER)));
                    cb.loadConstant(integerLabel)
                      .if_icmpne(next);
                } else if ((caseLabel instanceof Long ||
                        caseLabel instanceof Float ||
                        caseLabel instanceof Double ||
                        caseLabel instanceof Boolean)) {
                    if (caseLabel instanceof Boolean c) {
                        cb.loadConstant(c ? 1 : 0);
                    } else {
                        cb.loadConstant((ConstantDesc) caseLabel);
                    }
                    var caseLabelWrapper = Wrapper.forWrapperType(caseLabel.getClass());
                    cb.invokestatic(caseLabelWrapper.wrapperClassDescriptor(),
                            "valueOf",
                            MethodTypeDesc.of(caseLabelWrapper.wrapperClassDescriptor(),
                                    caseLabelWrapper.basicClassDescriptor()))
                      .aload(SELECTOR_OBJ)
                      .invokevirtual(CD_Object,
                            "equals",
                            MethodTypeDesc.of(CD_boolean,
                                    CD_Object))
                      .ifeq(next);
                } else {
                    throw new InternalError("Unsupported label type: " +
                            caseLabel.getClass());
                }
                cb.loadConstant(idx)
                  .ireturn();
            }
            stackMapFrames.add(StackMapFrameInfo.of(dflt, locals, List.of()));
            cb.labelBinding(dflt)
              .loadConstant(labelConstants.length)
              .ireturn()
              .with(StackMapTableAttribute.of(stackMapFrames));
            DirectCodeBuilder.withMaxs(cb, 3, locals.size()); // enum labels use 3 stack, others use 2
        };
    }

    private static boolean isNotValidPair(Class<?> selectorType, Object caseLabel) {
        return (selectorType == boolean.class && caseLabel != boolean.class && caseLabel != Boolean.class) ||
               (selectorType != boolean.class && selectorType.isPrimitive() && (caseLabel == boolean.class || caseLabel == Boolean.class));
    }

    /*
     * Construct the method handle that represents the method int typeSwitch(Object, int, BiPredicate, List)
     */
    private static MethodHandle generateTypeSwitch(MethodHandles.Lookup caller, Class<?> selectorType, Object[] labelConstants) {
        boolean addExtraInfo = needsExtraInfo(selectorType, labelConstants);
        List<EnumDesc<?>> enumDescs = addExtraInfo ? new ArrayList<>() : null;
        List<Class<?>> extraClassLabels = addExtraInfo ? new ArrayList<>() : null;

        byte[] classBytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(ConstantUtils.binaryNameToDesc(typeSwitchClassName(caller.lookupClass())),
                clb -> {
                    clb.withFlags(AccessFlag.FINAL, AccessFlag.SUPER, AccessFlag.SYNTHETIC)
                       .withMethodBody("typeSwitch",
                                       addExtraInfo ? MTD_TYPE_SWITCH_EXTRA : MTD_TYPE_SWITCH,
                                       ClassFile.ACC_FINAL | ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                       generateTypeSwitchSkeleton(selectorType, labelConstants, enumDescs, extraClassLabels));
        });

        try {
            // this class is linked at the indy callsite; so define a hidden nestmate
            MethodHandles.Lookup lookup;
            lookup = caller.defineHiddenClass(classBytes, true, NESTMATE, STRONG);
            MethodHandle typeSwitch = lookup.findStatic(lookup.lookupClass(),
                                                        "typeSwitch",
                                                        addExtraInfo ? MT_TYPE_SWITCH_EXTRA : MT_TYPE_SWITCH);
            if (addExtraInfo) {
                typeSwitch = MethodHandles.insertArguments(typeSwitch, 2, new ResolvedEnumLabels(caller, enumDescs.toArray(new EnumDesc<?>[0])),
                        List.copyOf(extraClassLabels));
            }
            return typeSwitch;
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    //based on src/java.base/share/classes/java/lang/invoke/InnerClassLambdaMetafactory.java:
    private static String typeSwitchClassName(Class<?> targetClass) {
        String name = targetClass.getName();
        if (targetClass.isHidden()) {
            // use the original class name
            name = name.replace('/', '_');
        }
        return name + "$$TypeSwitch";
    }

    // this method should be in sync with com.sun.tools.javac.code.Types.checkUnconditionallyExactPrimitives
    private static boolean unconditionalExactnessCheck(Class<?> selectorType, Class<?> targetType) {
        Wrapper selectorWrapper = Wrapper.forBasicType(selectorType);
        Wrapper targetWrapper   = Wrapper.forBasicType(targetType);
        if (selectorType.isPrimitive() && targetType.equals(selectorWrapper.wrapperType())) {
            return true;
        }
        else if (selectorType.equals(targetType) ||
                (targetType.isPrimitive() && selectorType.isPrimitive() &&
                    (selectorWrapper.isStrictSubRangeOf(targetWrapper) &&
                            !((selectorType.equals(byte.class) && targetType.equals(char.class)) ||
                              (selectorType.equals(int.class)  && targetType.equals(float.class)) ||
                              (selectorType.equals(long.class) && (targetType.equals(double.class) || targetType.equals(float.class))))))) return true;
        return false;
    }

    // TypePairs should be in sync with the corresponding record in Lower
    record TypePairs(Class<?> from, Class<?> to) {

        private static final Map<TypePairs, String> typePairToName = initialize();

        public static TypePairs of(Class<?> from,  Class<?> to) {
            if (from == byte.class || from == short.class || from == char.class) {
                from = int.class;
            }
            return new TypePairs(from, to);
        }

        public int hashCode() {
            return 31 * from.hashCode() + to.hashCode();
        }

        public boolean equals(Object other) {
            if (other instanceof TypePairs otherPair) {
                return otherPair.from == from && otherPair.to == to;
            }
            return false;
        }

        public static Map<TypePairs, String> initialize() {
            Map<TypePairs, String> typePairToName = new HashMap<>();
            typePairToName.put(new TypePairs(byte.class,   char.class),   "isIntToCharExact");      // redirected
            typePairToName.put(new TypePairs(short.class,  byte.class),   "isIntToByteExact");      // redirected
            typePairToName.put(new TypePairs(short.class,  char.class),   "isIntToCharExact");      // redirected
            typePairToName.put(new TypePairs(char.class,   byte.class),   "isIntToByteExact");      // redirected
            typePairToName.put(new TypePairs(char.class,   short.class),  "isIntToShortExact");     // redirected
            typePairToName.put(new TypePairs(int.class,    byte.class),   "isIntToByteExact");
            typePairToName.put(new TypePairs(int.class,    short.class),  "isIntToShortExact");
            typePairToName.put(new TypePairs(int.class,    char.class),   "isIntToCharExact");
            typePairToName.put(new TypePairs(int.class,    float.class),  "isIntToFloatExact");
            typePairToName.put(new TypePairs(long.class,   byte.class),   "isLongToByteExact");
            typePairToName.put(new TypePairs(long.class,   short.class),  "isLongToShortExact");
            typePairToName.put(new TypePairs(long.class,   char.class),   "isLongToCharExact");
            typePairToName.put(new TypePairs(long.class,   int.class),    "isLongToIntExact");
            typePairToName.put(new TypePairs(long.class,   float.class),  "isLongToFloatExact");
            typePairToName.put(new TypePairs(long.class,   double.class), "isLongToDoubleExact");
            typePairToName.put(new TypePairs(float.class,  byte.class),   "isFloatToByteExact");
            typePairToName.put(new TypePairs(float.class,  short.class),  "isFloatToShortExact");
            typePairToName.put(new TypePairs(float.class,  char.class),   "isFloatToCharExact");
            typePairToName.put(new TypePairs(float.class,  int.class),    "isFloatToIntExact");
            typePairToName.put(new TypePairs(float.class,  long.class),   "isFloatToLongExact");
            typePairToName.put(new TypePairs(double.class, byte.class),   "isDoubleToByteExact");
            typePairToName.put(new TypePairs(double.class, short.class),  "isDoubleToShortExact");
            typePairToName.put(new TypePairs(double.class, char.class),   "isDoubleToCharExact");
            typePairToName.put(new TypePairs(double.class, int.class),    "isDoubleToIntExact");
            typePairToName.put(new TypePairs(double.class, long.class),   "isDoubleToLongExact");
            typePairToName.put(new TypePairs(double.class, float.class),  "isDoubleToFloatExact");
            return typePairToName;
        }
    }
}
