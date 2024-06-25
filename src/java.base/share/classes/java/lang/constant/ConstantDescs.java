/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.constant;

import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.constant.PrimitiveClassDescImpl;
import jdk.internal.constant.ReferenceClassDescImpl;

import java.lang.Enum.EnumDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.invoke.VarHandle.VarHandleDesc;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.constant.DirectMethodHandleDesc.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.STATIC;

/**
 * Predefined values of <a href="package-summary.html#nominal">nominal descriptor</a>
 * for common constants, including descriptors for primitive class types and
 * other common platform types, and descriptors for method handles for standard
 * bootstrap methods.
 *
 * @see ConstantDesc
 *
 * @since 12
 */
public final class ConstantDescs {
    // No instances
    private ConstantDescs() { }

    /** Invocation name to use when no name is needed, such as the name of a
     * constructor, or the invocation name of a dynamic constant or dynamic
     * callsite when the bootstrap is known to ignore the invocation name.
     */
    public static final String DEFAULT_NAME = "_";

    // Don't change the order of these declarations!

    /** {@link ClassDesc} representing {@link Object} */
    public static final ClassDesc CD_Object = ReferenceClassDescImpl.ofValidated("Ljava/lang/Object;");

    /** {@link ClassDesc} representing {@link String} */
    public static final ClassDesc CD_String = ReferenceClassDescImpl.ofValidated("Ljava/lang/String;");

    /** {@link ClassDesc} representing {@link Class} */
    public static final ClassDesc CD_Class = ReferenceClassDescImpl.ofValidated("Ljava/lang/Class;");

    /** {@link ClassDesc} representing {@link Number} */
    public static final ClassDesc CD_Number = ReferenceClassDescImpl.ofValidated("Ljava/lang/Number;");

    /** {@link ClassDesc} representing {@link Integer} */
    public static final ClassDesc CD_Integer = ReferenceClassDescImpl.ofValidated("Ljava/lang/Integer;");

    /** {@link ClassDesc} representing {@link Long} */
    public static final ClassDesc CD_Long = ReferenceClassDescImpl.ofValidated("Ljava/lang/Long;");

    /** {@link ClassDesc} representing {@link Float} */
    public static final ClassDesc CD_Float = ReferenceClassDescImpl.ofValidated("Ljava/lang/Float;");

    /** {@link ClassDesc} representing {@link Double} */
    public static final ClassDesc CD_Double = ReferenceClassDescImpl.ofValidated("Ljava/lang/Double;");

    /** {@link ClassDesc} representing {@link Short} */
    public static final ClassDesc CD_Short = ReferenceClassDescImpl.ofValidated("Ljava/lang/Short;");

    /** {@link ClassDesc} representing {@link Byte} */
    public static final ClassDesc CD_Byte = ReferenceClassDescImpl.ofValidated("Ljava/lang/Byte;");

    /** {@link ClassDesc} representing {@link Character} */
    public static final ClassDesc CD_Character = ReferenceClassDescImpl.ofValidated("Ljava/lang/Character;");

    /** {@link ClassDesc} representing {@link Boolean} */
    public static final ClassDesc CD_Boolean = ReferenceClassDescImpl.ofValidated("Ljava/lang/Boolean;");

    /** {@link ClassDesc} representing {@link Void} */
    public static final ClassDesc CD_Void = ReferenceClassDescImpl.ofValidated("Ljava/lang/Void;");

    /** {@link ClassDesc} representing {@link Throwable} */
    public static final ClassDesc CD_Throwable = ReferenceClassDescImpl.ofValidated("Ljava/lang/Throwable;");

    /** {@link ClassDesc} representing {@link Exception} */
    public static final ClassDesc CD_Exception = ReferenceClassDescImpl.ofValidated("Ljava/lang/Exception;");

    /** {@link ClassDesc} representing {@link Enum} */
    public static final ClassDesc CD_Enum = ReferenceClassDescImpl.ofValidated("Ljava/lang/Enum;");

    /** {@link ClassDesc} representing {@link VarHandle} */
    public static final ClassDesc CD_VarHandle = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/VarHandle;");

    /** {@link ClassDesc} representing {@link MethodHandles} */
    public static final ClassDesc CD_MethodHandles = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/MethodHandles;");

    /** {@link ClassDesc} representing {@link MethodHandles.Lookup} */
    public static final ClassDesc CD_MethodHandles_Lookup = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/MethodHandles$Lookup;");

    /** {@link ClassDesc} representing {@link MethodHandle} */
    public static final ClassDesc CD_MethodHandle = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/MethodHandle;");

    /** {@link ClassDesc} representing {@link MethodType} */
    public static final ClassDesc CD_MethodType = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/MethodType;");

    /** {@link ClassDesc} representing {@link CallSite} */
    public static final ClassDesc CD_CallSite = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/CallSite;");

    /** {@link ClassDesc} representing {@link Collection} */
    public static final ClassDesc CD_Collection = ReferenceClassDescImpl.ofValidated("Ljava/util/Collection;");

    /** {@link ClassDesc} representing {@link List} */
    public static final ClassDesc CD_List = ReferenceClassDescImpl.ofValidated("Ljava/util/List;");

    /** {@link ClassDesc} representing {@link Set} */
    public static final ClassDesc CD_Set = ReferenceClassDescImpl.ofValidated("Ljava/util/Set;");

    /** {@link ClassDesc} representing {@link Map} */
    public static final ClassDesc CD_Map = ReferenceClassDescImpl.ofValidated("Ljava/util/Map;");

    /** {@link ClassDesc} representing {@link ConstantDesc} */
    public static final ClassDesc CD_ConstantDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/ConstantDesc;");

    /** {@link ClassDesc} representing {@link ClassDesc} */
    public static final ClassDesc CD_ClassDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/ClassDesc;");

    /** {@link ClassDesc} representing {@link EnumDesc} */
    public static final ClassDesc CD_EnumDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/Enum$EnumDesc;");

    /** {@link ClassDesc} representing {@link MethodTypeDesc} */
    public static final ClassDesc CD_MethodTypeDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/MethodTypeDesc;");

    /** {@link ClassDesc} representing {@link MethodHandleDesc} */
    public static final ClassDesc CD_MethodHandleDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/MethodHandleDesc;");

    /** {@link ClassDesc} representing {@link DirectMethodHandleDesc} */
    public static final ClassDesc CD_DirectMethodHandleDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/DirectMethodHandleDesc;");

    /** {@link ClassDesc} representing {@link VarHandleDesc} */
    public static final ClassDesc CD_VarHandleDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/VarHandle$VarHandleDesc;");

    /** {@link ClassDesc} representing {@link DirectMethodHandleDesc.Kind} */
    public static final ClassDesc CD_MethodHandleDesc_Kind = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/DirectMethodHandleDesc$Kind;");

    /** {@link ClassDesc} representing {@link DynamicConstantDesc} */
    public static final ClassDesc CD_DynamicConstantDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/DynamicConstantDesc;");

    /** {@link ClassDesc} representing {@link DynamicCallSiteDesc} */
    public static final ClassDesc CD_DynamicCallSiteDesc = ReferenceClassDescImpl.ofValidated("Ljava/lang/constant/DynamicCallSiteDesc;");

    /** {@link ClassDesc} representing {@link ConstantBootstraps} */
    public static final ClassDesc CD_ConstantBootstraps = ReferenceClassDescImpl.ofValidated("Ljava/lang/invoke/ConstantBootstraps;");

    private static final ClassDesc[] INDY_BOOTSTRAP_ARGS = {
            CD_MethodHandles_Lookup,
            CD_String,
            CD_MethodType};

    private static final ClassDesc[] CONDY_BOOTSTRAP_ARGS = {
            CD_MethodHandles_Lookup,
            CD_String,
            CD_Class};

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#primitiveClass(Lookup, String, Class) ConstantBootstraps.primitiveClass} */
    public static final DirectMethodHandleDesc BSM_PRIMITIVE_CLASS
            = ofConstantBootstrap(CD_ConstantBootstraps, "primitiveClass",
            CD_Class);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#enumConstant(Lookup, String, Class) ConstantBootstraps.enumConstant} */
    public static final DirectMethodHandleDesc BSM_ENUM_CONSTANT
            = ofConstantBootstrap(CD_ConstantBootstraps, "enumConstant",
            CD_Enum);

    /**
     * {@link MethodHandleDesc} representing {@link ConstantBootstraps#getStaticFinal(Lookup, String, Class, Class) ConstantBootstraps.getStaticFinal}
     * @since 15
     */
    public static final DirectMethodHandleDesc BSM_GET_STATIC_FINAL
            = ofConstantBootstrap(CD_ConstantBootstraps, "getStaticFinal",
            CD_Object, CD_Class);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#nullConstant(Lookup, String, Class) ConstantBootstraps.nullConstant} */
    public static final DirectMethodHandleDesc BSM_NULL_CONSTANT
            = ofConstantBootstrap(CD_ConstantBootstraps, "nullConstant",
            CD_Object);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#fieldVarHandle(Lookup, String, Class, Class, Class) ConstantBootstraps.fieldVarHandle} */
    public static final DirectMethodHandleDesc BSM_VARHANDLE_FIELD
            = ofConstantBootstrap(CD_ConstantBootstraps, "fieldVarHandle",
            CD_VarHandle, CD_Class, CD_Class);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#staticFieldVarHandle(Lookup, String, Class, Class, Class) ConstantBootstraps.staticFieldVarHandle} */
    public static final DirectMethodHandleDesc BSM_VARHANDLE_STATIC_FIELD
            = ofConstantBootstrap(CD_ConstantBootstraps, "staticFieldVarHandle",
            CD_VarHandle, CD_Class, CD_Class);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#arrayVarHandle(Lookup, String, Class, Class) ConstantBootstraps.arrayVarHandle} */
    public static final DirectMethodHandleDesc BSM_VARHANDLE_ARRAY
            = ofConstantBootstrap(CD_ConstantBootstraps, "arrayVarHandle",
            CD_VarHandle, CD_Class);

    /** {@link MethodHandleDesc} representing {@link ConstantBootstraps#invoke(Lookup, String, Class, MethodHandle, Object...) ConstantBootstraps.invoke} */
    public static final DirectMethodHandleDesc BSM_INVOKE
            = ofConstantBootstrap(CD_ConstantBootstraps, "invoke",
            CD_Object, CD_MethodHandle, CD_Object.arrayType());

    /**
     * {@link MethodHandleDesc} representing {@link ConstantBootstraps#explicitCast(Lookup, String, Class, Object) ConstantBootstraps.explicitCast}
     * @since 15
     */
    public static final DirectMethodHandleDesc BSM_EXPLICIT_CAST
            = ofConstantBootstrap(CD_ConstantBootstraps, "explicitCast",
            CD_Object, CD_Object);

    /** {@link ClassDesc} representing the primitive type {@code int} */
    public static final ClassDesc CD_int = new PrimitiveClassDescImpl("I");

    /** {@link ClassDesc} representing the primitive type {@code long} */
    public static final ClassDesc CD_long = new PrimitiveClassDescImpl("J");

    /** {@link ClassDesc} representing the primitive type {@code float} */
    public static final ClassDesc CD_float = new PrimitiveClassDescImpl("F");

    /** {@link ClassDesc} representing the primitive type {@code double} */
    public static final ClassDesc CD_double = new PrimitiveClassDescImpl("D");

    /** {@link ClassDesc} representing the primitive type {@code short} */
    public static final ClassDesc CD_short = new PrimitiveClassDescImpl("S");

    /** {@link ClassDesc} representing the primitive type {@code byte} */
    public static final ClassDesc CD_byte = new PrimitiveClassDescImpl("B");

    /** {@link ClassDesc} representing the primitive type {@code char} */
    public static final ClassDesc CD_char = new PrimitiveClassDescImpl("C");

    /** {@link ClassDesc} representing the primitive type {@code boolean} */
    public static final ClassDesc CD_boolean = new PrimitiveClassDescImpl("Z");

    /** {@link ClassDesc} representing the primitive type {@code void} */
    public static final ClassDesc CD_void = new PrimitiveClassDescImpl("V");

    /**
     * {@link MethodHandleDesc} representing {@link MethodHandles#classData(Lookup, String, Class) MethodHandles.classData}
     * @since 21
     */
    public static final DirectMethodHandleDesc BSM_CLASS_DATA
            = ofConstantBootstrap(CD_MethodHandles, "classData",
            CD_Object);

    /**
     * {@link MethodHandleDesc} representing {@link MethodHandles#classDataAt(Lookup, String, Class, int) MethodHandles.classDataAt}
     * @since 21
     */
    public static final DirectMethodHandleDesc BSM_CLASS_DATA_AT
            = ofConstantBootstrap(CD_MethodHandles, "classDataAt",
            CD_Object, CD_int);

    /** Nominal descriptor representing the constant {@code null} */
    public static final ConstantDesc NULL
            = DynamicConstantDesc.ofNamed(ConstantDescs.BSM_NULL_CONSTANT,
                                          DEFAULT_NAME, ConstantDescs.CD_Object);

    /**
     * Nominal descriptor representing the constant {@linkplain Boolean#TRUE}
     * @since 15
     */
    public static final DynamicConstantDesc<Boolean> TRUE
            = DynamicConstantDesc.ofNamed(BSM_GET_STATIC_FINAL,
                                          "TRUE", CD_Boolean, CD_Boolean);

    /**
     * Nominal descriptor representing the constant {@linkplain Boolean#FALSE}
     * @since 15
     */
    public static final DynamicConstantDesc<Boolean> FALSE
            = DynamicConstantDesc.ofNamed(BSM_GET_STATIC_FINAL,
                                          "FALSE", CD_Boolean, CD_Boolean);

    /**
     * The special name of instance initialization methods, {@value}. An instance
     * initialization method has this special name and is {@code void}.
     *
     * @jvms 2.9.1 Instance Initialization Methods
     * @since 21
     */
    public static final String INIT_NAME = "<init>";

    /**
     * The special name of class initialization methods, {@value}. A class
     * initialization method has this special name, {@link java.lang.reflect.AccessFlag#STATIC
     * ACC_STATIC} flag set, is {@link #MTD_void void} and takes no arguments.
     *
     * @jvms 2.9.2 Class Initialization Methods
     * @since 21
     */
    public static final String CLASS_INIT_NAME = "<clinit>";

    /**
     * Nominal descriptor representing the method descriptor {@code ()V},
     * taking no argument and returning {@code void}.
     *
     * @since 21
     */
    public static final MethodTypeDesc MTD_void = MethodTypeDesc.of(CD_void);

    static final DirectMethodHandleDesc MHD_METHODHANDLE_ASTYPE
            = MethodHandleDesc.ofMethod(Kind.VIRTUAL, CD_MethodHandle, "asType",
                                        MethodTypeDesc.of(CD_MethodHandle, CD_MethodType));
    /**
     * Returns a {@link MethodHandleDesc} corresponding to a bootstrap method for
     * an {@code invokedynamic} callsite, which is a static method whose leading
     * parameter types are {@code Lookup}, {@code String}, and {@code MethodType}.
     *
     * @param owner the class declaring the method
     * @param name the unqualified name of the method
     * @param returnType the return type of the method
     * @param paramTypes the types of the static bootstrap arguments, if any
     * @return the {@link MethodHandleDesc}
     * @throws NullPointerException if any of the arguments are null
     * @jvms 4.2.2 Unqualified Names
     */
    public static DirectMethodHandleDesc ofCallsiteBootstrap(ClassDesc owner,
                                                             String name,
                                                             ClassDesc returnType,
                                                             ClassDesc... paramTypes) {
        int prefixLen = INDY_BOOTSTRAP_ARGS.length;
        ClassDesc[] fullParamTypes = new ClassDesc[paramTypes.length + prefixLen];
        System.arraycopy(INDY_BOOTSTRAP_ARGS, 0, fullParamTypes, 0, prefixLen);
        System.arraycopy(paramTypes, 0, fullParamTypes, prefixLen, paramTypes.length);
        return MethodHandleDesc.ofMethod(STATIC, owner, name, MethodTypeDescImpl.ofTrusted(returnType, fullParamTypes));
    }

    /**
     * Returns a {@link MethodHandleDesc} corresponding to a bootstrap method for a
     * dynamic constant, which is a static method whose leading arguments are
     * {@code Lookup}, {@code String}, and {@code Class}.
     *
     * @param owner the class declaring the method
     * @param name the unqualified name of the method
     * @param returnType the return type of the method
     * @param paramTypes the types of the static bootstrap arguments, if any
     * @return the {@link MethodHandleDesc}
     * @throws NullPointerException if any of the arguments are null
     * @jvms 4.2.2 Unqualified Names
     */
    public static DirectMethodHandleDesc ofConstantBootstrap(ClassDesc owner,
                                                             String name,
                                                             ClassDesc returnType,
                                                             ClassDesc... paramTypes) {
        int prefixLen = CONDY_BOOTSTRAP_ARGS.length;
        ClassDesc[] fullParamTypes = new ClassDesc[paramTypes.length + prefixLen];
        System.arraycopy(CONDY_BOOTSTRAP_ARGS, 0, fullParamTypes, 0, prefixLen);
        System.arraycopy(paramTypes, 0, fullParamTypes, prefixLen, paramTypes.length);
        return MethodHandleDesc.ofMethod(STATIC, owner, name, MethodTypeDescImpl.ofTrusted(returnType, fullParamTypes));
    }
}
