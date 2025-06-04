/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal;

import jdk.xml.internal.Utils;

/**
 * Exception constants.
 *
 * @since 6.0 (intended to replace the InstructionConstant interface)
 * @LastModified: Feb 2023
 */
public final class ExceptionConst {

    /**
     * Enum corresponding to the various Exception Class arrays, used by
     * {@link ExceptionConst#createExceptions(EXCS, Class...)}
     */
    public enum EXCS {
        EXCS_CLASS_AND_INTERFACE_RESOLUTION, EXCS_FIELD_AND_METHOD_RESOLUTION, EXCS_INTERFACE_METHOD_RESOLUTION, EXCS_STRING_RESOLUTION, EXCS_ARRAY_EXCEPTION,
    }

    /**
     * The mother of all exceptions
     */
    public static final Class<Throwable> THROWABLE = Throwable.class;

    /**
     * Super class of any run-time exception
     */
    public static final Class<RuntimeException> RUNTIME_EXCEPTION = RuntimeException.class;

    /**
     * Super class of any linking exception (aka Linkage Error)
     */
    public static final Class<LinkageError> LINKING_EXCEPTION = LinkageError.class;

    /**
     * Linking Exceptions
     */
    public static final Class<ClassCircularityError> CLASS_CIRCULARITY_ERROR = ClassCircularityError.class;
    public static final Class<ClassFormatError> CLASS_FORMAT_ERROR = ClassFormatError.class;
    public static final Class<ExceptionInInitializerError> EXCEPTION_IN_INITIALIZER_ERROR = ExceptionInInitializerError.class;
    public static final Class<IncompatibleClassChangeError> INCOMPATIBLE_CLASS_CHANGE_ERROR = IncompatibleClassChangeError.class;
    public static final Class<AbstractMethodError> ABSTRACT_METHOD_ERROR = AbstractMethodError.class;
    public static final Class<IllegalAccessError> ILLEGAL_ACCESS_ERROR = IllegalAccessError.class;
    public static final Class<InstantiationError> INSTANTIATION_ERROR = InstantiationError.class;
    public static final Class<NoSuchFieldError> NO_SUCH_FIELD_ERROR = NoSuchFieldError.class;
    public static final Class<NoSuchMethodError> NO_SUCH_METHOD_ERROR = NoSuchMethodError.class;
    public static final Class<NoClassDefFoundError> NO_CLASS_DEF_FOUND_ERROR = NoClassDefFoundError.class;
    public static final Class<UnsatisfiedLinkError> UNSATISFIED_LINK_ERROR = UnsatisfiedLinkError.class;
    public static final Class<VerifyError> VERIFY_ERROR = VerifyError.class;
    /* UnsupportedClassVersionError is new in JDK 1.2 */
//    public static final Class UnsupportedClassVersionError = UnsupportedClassVersionError.class;

    /**
     * Run-Time Exceptions
     */
    public static final Class<NullPointerException> NULL_POINTER_EXCEPTION = NullPointerException.class;
    public static final Class<ArrayIndexOutOfBoundsException> ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION = ArrayIndexOutOfBoundsException.class;
    public static final Class<ArithmeticException> ARITHMETIC_EXCEPTION = ArithmeticException.class;
    public static final Class<NegativeArraySizeException> NEGATIVE_ARRAY_SIZE_EXCEPTION = NegativeArraySizeException.class;
    public static final Class<ClassCastException> CLASS_CAST_EXCEPTION = ClassCastException.class;

    public static final Class<IllegalMonitorStateException> ILLEGAL_MONITOR_STATE = IllegalMonitorStateException.class;
    /**
     * Pre-defined exception arrays according to chapters 5.1-5.4 of the Java Virtual Machine Specification
     */
    private static final Class<?>[] EXCS_CLASS_AND_INTERFACE_RESOLUTION = {NO_CLASS_DEF_FOUND_ERROR, CLASS_FORMAT_ERROR, VERIFY_ERROR, ABSTRACT_METHOD_ERROR,
        EXCEPTION_IN_INITIALIZER_ERROR, ILLEGAL_ACCESS_ERROR}; // Chapter 5.1

    private static final Class<?>[] EXCS_FIELD_AND_METHOD_RESOLUTION = {NO_SUCH_FIELD_ERROR, ILLEGAL_ACCESS_ERROR, NO_SUCH_METHOD_ERROR}; // Chapter 5.2

    /**
     * Empty array.
     */
    private static final Class<?>[] EXCS_INTERFACE_METHOD_RESOLUTION = new Class<?>[0]; // Chapter 5.3 (as below)

    /**
     * Empty array.
     */
    private static final Class<?>[] EXCS_STRING_RESOLUTION = new Class<?>[0];

    // Chapter 5.4 (no errors but the ones that _always_ could happen! How stupid.)
    private static final Class<?>[] EXCS_ARRAY_EXCEPTION = {NULL_POINTER_EXCEPTION, ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION};

    /**
     * Creates a copy of the specified Exception Class array combined with any additional Exception classes.
     *
     * @param type the basic array type
     * @param extraClasses additional classes, if any
     * @return the merged array
     */
    public static Class<?>[] createExceptions(final EXCS type, final Class<?>... extraClasses) {
        switch (type) {
        case EXCS_CLASS_AND_INTERFACE_RESOLUTION:
            return mergeExceptions(EXCS_CLASS_AND_INTERFACE_RESOLUTION, extraClasses);
        case EXCS_ARRAY_EXCEPTION:
            return mergeExceptions(EXCS_ARRAY_EXCEPTION, extraClasses);
        case EXCS_FIELD_AND_METHOD_RESOLUTION:
            return mergeExceptions(EXCS_FIELD_AND_METHOD_RESOLUTION, extraClasses);
        case EXCS_INTERFACE_METHOD_RESOLUTION:
            return mergeExceptions(EXCS_INTERFACE_METHOD_RESOLUTION, extraClasses);
        case EXCS_STRING_RESOLUTION:
            return mergeExceptions(EXCS_STRING_RESOLUTION, extraClasses);
        default:
            throw new AssertionError("Cannot happen; unexpected enum value: " + type);
        }
    }

    // helper method to merge exception class arrays
    private static Class<?>[] mergeExceptions(final Class<?>[] input, final Class<?>... extraClasses) {
        return Utils.arraysAppend(input, extraClasses);
    }
}
