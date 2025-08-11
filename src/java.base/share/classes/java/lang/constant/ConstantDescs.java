/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.constant.PrimitiveClassDescImpl;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;

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
@AOTSafeClassInitializer
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
    public static final ClassDesc CD_Object = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Object;");

    /** {@link ClassDesc} representing {@link String} */
    public static final ClassDesc CD_String = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/String;");

    /** {@link ClassDesc} representing {@link Class} */
    public static final ClassDesc CD_Class = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Class;");

    /** {@link ClassDesc} representing {@link Number} */
    public static final ClassDesc CD_Number = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Number;");

    /** {@link ClassDesc} representing {@link Integer} */
    public static final ClassDesc CD_Integer = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Integer;");

    /** {@link ClassDesc} representing {@link Long} */
    public static final ClassDesc CD_Long = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Long;");

    /** {@link ClassDesc} representing {@link Float} */
    public static final ClassDesc CD_Float = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Float;");

    /** {@link ClassDesc} representing {@link Double} */
    public static final ClassDesc CD_Double = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Double;");

    /** {@link ClassDesc} representing {@link Short} */
    public static final ClassDesc CD_Short = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Short;");

    /** {@link ClassDesc} representing {@link Byte} */
    public static final ClassDesc CD_Byte = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Byte;");

    /** {@link ClassDesc} representing {@link Character} */
    public static final ClassDesc CD_Character = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Character;");

    /** {@link ClassDesc} representing {@link Boolean} */
    public static final ClassDesc CD_Boolean = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Boolean;");

    /** {@link ClassDesc} representing {@link Void} */
    public static final ClassDesc CD_Void = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Void;");

    /** {@link ClassDesc} representing {@link Throwable} */
    public static final ClassDesc CD_Throwable = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Throwable;");

    /** {@link ClassDesc} representing {@link Exception} */
    public static final ClassDesc CD_Exception = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Exception;");

    /** {@link ClassDesc} representing {@link Enum} */
    public static final ClassDesc CD_Enum = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Enum;");

    /** {@link ClassDesc} representing {@link VarHandle} */
    public static final ClassDesc CD_VarHandle = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/VarHandle;");

    /** {@link ClassDesc} representing {@link MethodHandles} */
    public static final ClassDesc CD_MethodHandles = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandles;");

    /** {@link ClassDesc} representing {@link MethodHandles.Lookup} */
    public static final ClassDesc CD_MethodHandles_Lookup = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandles$Lookup;");

    /** {@link ClassDesc} representing {@link MethodHandle} */
    public static final ClassDesc CD_MethodHandle = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandle;");

    /** {@link ClassDesc} representing {@link MethodType} */
    public static final ClassDesc CD_MethodType = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodType;");

    /** {@link ClassDesc} representing {@link CallSite} */
    public static final ClassDesc CD_CallSite = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/CallSite;");

    /** {@link ClassDesc} representing {@link Collection} */
    public static final ClassDesc CD_Collection = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/Collection;");

    /** {@link ClassDesc} representing {@link List} */
    public static final ClassDesc CD_List = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/List;");

    /** {@link ClassDesc} representing {@link Set} */
    public static final ClassDesc CD_Set = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/Set;");

    /** {@link ClassDesc} representing {@link Map} */
    public static final ClassDesc CD_Map = ClassOrInterfaceDescImpl.ofValidated("Ljava/util/Map;");

    /** {@link ClassDesc} representing {@link ConstantDesc} */
    public static final ClassDesc CD_ConstantDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/ConstantDesc;");

    /** {@link ClassDesc} representing {@link ClassDesc} */
    public static final ClassDesc CD_ClassDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/ClassDesc;");

    /** {@link ClassDesc} representing {@link EnumDesc} */
    public static final ClassDesc CD_EnumDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/Enum$EnumDesc;");

    /** {@link ClassDesc} representing {@link MethodTypeDesc} */
    public static final ClassDesc CD_MethodTypeDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/MethodTypeDesc;");

    /** {@link ClassDesc} representing {@link MethodHandleDesc} */
    public static final ClassDesc CD_MethodHandleDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/MethodHandleDesc;");

    /** {@link ClassDesc} representing {@link DirectMethodHandleDesc} */
    public static final ClassDesc CD_DirectMethodHandleDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/DirectMethodHandleDesc;");

    /** {@link ClassDesc} representing {@link VarHandleDesc} */
    public static final ClassDesc CD_VarHandleDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/VarHandle$VarHandleDesc;");

    /** {@link ClassDesc} representing {@link DirectMethodHandleDesc.Kind} */
    public static final ClassDesc CD_MethodHandleDesc_Kind = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/DirectMethodHandleDesc$Kind;");

    /** {@link ClassDesc} representing {@link DynamicConstantDesc} */
    public static final ClassDesc CD_DynamicConstantDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/DynamicConstantDesc;");

    /** {@link ClassDesc} representing {@link DynamicCallSiteDesc} */
    public static final ClassDesc CD_DynamicCallSiteDesc = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/constant/DynamicCallSiteDesc;");

    /** {@link ClassDesc} representing {@link ConstantBootstraps} */
    public static final ClassDesc CD_ConstantBootstraps = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/ConstantBootstraps;");

    static {
        // avoid circular initialization
        ConstantUtils.CD_Object_array = CD_Object.arrayType();
    }

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
            CD_Object, CD_MethodHandle, ConstantUtils.CD_Object_array);

    /**
     * {@link MethodHandleDesc} representing {@link ConstantBootstraps#explicitCast(Lookup, String, Class, Object) ConstantBootstraps.explicitCast}
     * @since 15
     */
    public static final DirectMethodHandleDesc BSM_EXPLICIT_CAST
            = ofConstantBootstrap(CD_ConstantBootstraps, "explicitCast",
            CD_Object, CD_Object);

    /** {@link ClassDesc} representing the primitive type {@code int} */
    public static final ClassDesc CD_int = PrimitiveClassDescImpl.CD_int;

    /** {@link ClassDesc} representing the primitive type {@code long} */
    public static final ClassDesc CD_long = PrimitiveClassDescImpl.CD_long;

    /** {@link ClassDesc} representing the primitive type {@code float} */
    public static final ClassDesc CD_float = PrimitiveClassDescImpl.CD_float;

    /** {@link ClassDesc} representing the primitive type {@code double} */
    public static final ClassDesc CD_double = PrimitiveClassDescImpl.CD_double;

    /** {@link ClassDesc} representing the primitive type {@code short} */
    public static final ClassDesc CD_short = PrimitiveClassDescImpl.CD_short;

    /** {@link ClassDesc} representing the primitive type {@code byte} */
    public static final ClassDesc CD_byte = PrimitiveClassDescImpl.CD_byte;

    /** {@link ClassDesc} representing the primitive type {@code char} */
    public static final ClassDesc CD_char = PrimitiveClassDescImpl.CD_char;

    /** {@link ClassDesc} representing the primitive type {@code boolean} */
    public static final ClassDesc CD_boolean = PrimitiveClassDescImpl.CD_boolean;

    /** {@link ClassDesc} representing the primitive type {@code void} */
    public static final ClassDesc CD_void = PrimitiveClassDescImpl.CD_void;

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
