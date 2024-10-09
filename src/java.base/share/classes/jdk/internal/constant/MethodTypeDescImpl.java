/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
package jdk.internal.constant;

import jdk.internal.vm.annotation.Stable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.util.Objects.requireNonNull;

import static jdk.internal.constant.ConstantUtils.badMethodDescriptor;
import static jdk.internal.constant.ConstantUtils.resolveClassDesc;
import static jdk.internal.constant.ConstantUtils.skipOverFieldSignature;
import static jdk.internal.constant.ConstantUtils.EMPTY_CLASSDESC;

/**
 * A <a href="package-summary.html#nominal">nominal descriptor</a> for a
 * {@link MethodType}.  A {@linkplain MethodTypeDescImpl} corresponds to a
 * {@code Constant_MethodType_info} entry in the constant pool of a classfile.
 */
public final class MethodTypeDescImpl implements MethodTypeDesc {
    private final ClassDesc returnType;
    private final @Stable ClassDesc[] argTypes;
    private @Stable String cachedDescriptorString;

    /**
     * Constructs a {@linkplain MethodTypeDesc} with the specified return type
     * and a trusted and already-validated parameter types array.
     *
     * @param returnType a {@link ClassDesc} describing the return type
     * @param validatedArgTypes {@link ClassDesc}s describing the trusted and validated parameter types
     */
    private MethodTypeDescImpl(ClassDesc returnType, ClassDesc[] validatedArgTypes) {
        this.returnType = returnType;
        this.argTypes = validatedArgTypes;
    }

    /**
     * Constructs a {@linkplain MethodTypeDesc} with the specified return type
     * and a trusted parameter types array, which will be validated.
     *
     * @param returnType a {@link ClassDesc} describing the return type
     * @param trustedArgTypes {@link ClassDesc}s describing the trusted parameter types
     */
    public static MethodTypeDescImpl ofTrusted(ClassDesc returnType, ClassDesc[] trustedArgTypes) {
        requireNonNull(returnType);
        // implicit null checks of trustedArgTypes and all elements
        for (ClassDesc cd : trustedArgTypes) {
            validateArgument(cd);
        }
        return ofValidated(returnType, trustedArgTypes);
    }

    private static ClassDesc validateArgument(ClassDesc arg) {
        if (requireNonNull(arg) == CD_void)
            throw new IllegalArgumentException("Void parameters not permitted");
        return arg;
    }

    /**
     * Constructs a {@linkplain MethodTypeDesc} with the specified pre-validated return type
     * and a pre-validated trusted parameter types array.
     *
     * @param returnType a {@link ClassDesc} describing the return type
     * @param trustedArgTypes {@link ClassDesc}s describing the trusted parameter types
     */
    public static MethodTypeDescImpl ofValidated(ClassDesc returnType, ClassDesc... trustedArgTypes) {
        if (trustedArgTypes.length == 0)
            return new MethodTypeDescImpl(returnType, EMPTY_CLASSDESC);
        return new MethodTypeDescImpl(returnType, trustedArgTypes);
    }

    /**
     * Creates a {@linkplain MethodTypeDescImpl} given a method descriptor string.
     *
     * @param descriptor the method descriptor string
     * @return a {@linkplain MethodTypeDescImpl} describing the desired method type
     * @throws IllegalArgumentException if the descriptor string is not a valid
     * method descriptor
     * @jvms 4.3.3 Method Descriptors
     */
    public static MethodTypeDescImpl ofDescriptor(String descriptor) {
        int length = descriptor.length();
        int rightBracket, retTypeLength;
        if (descriptor.charAt(0) != '('
                || (rightBracket = (descriptor.charAt(1) == ')' ? 1 : descriptor.lastIndexOf(')'))) <= 0
                || (retTypeLength = length - rightBracket - 1) == 0
                || (retTypeLength != 1 // if retTypeLength == 1, check correctness in resolveClassDesc
                    && retTypeLength != skipOverFieldSignature(descriptor, rightBracket + 1, length))
        ) {
            throw badMethodDescriptor(descriptor);
        }

        var returnType = resolveClassDesc(descriptor, rightBracket + 1, retTypeLength);
        if (length == 3 && returnType == CD_void) {
            return (MethodTypeDescImpl) ConstantDescs.MTD_void;
        }
        var paramTypes = paramTypes(descriptor, 1, rightBracket);
        var result = new MethodTypeDescImpl(returnType, paramTypes);
        result.cachedDescriptorString = descriptor;
        return result;
    }

    private static ClassDesc[] paramTypes(String descriptor, int start, int end) {
        if (start == end) {
            return EMPTY_CLASSDESC;
        }

        /*
         * If the length of the first 8 parameters is < 256, save them in lengths to avoid ArrayList allocation
         * Stop storing for the last parameter (we can compute length), or if too many parameters or too long.
         */
        // little endian storage - lowest byte is encoded length 0
        long packedLengths = 0;
        int packedCount = 0;
        int cur = start;
        while (cur < end) {
            int len = skipOverFieldSignature(descriptor, cur, end);
            if (len == 0) {
                throw badMethodDescriptor(descriptor);
            }
            cur += len;
            if (len > 0xFF || packedCount >= Long.SIZE / Byte.SIZE || cur == end) {
                // Cannot or do not have to pack this item, but is already scanned and valid
                break;
            }
            packedLengths = packedLengths | (((long) len) << (Byte.SIZE * packedCount++));
        }

        // Invariant: packedCount parameters encoded in packedLengths,
        // And another valid parameter pointed by cur

        // Recover encoded elements
        ClassDesc[]     paramTypes    = null;
        List<ClassDesc> paramTypeList = null;
        if (cur == end) {
            paramTypes = new ClassDesc[packedCount + 1];
        } else {
            paramTypeList = new ArrayList<>(32);
        }

        int last = start;
        for (int i = 0; i < packedCount; i++) {
            int len = Byte.toUnsignedInt((byte) (packedLengths >> (Byte.SIZE * i)));
            var cd = resolveClassDesc(descriptor, last, len);
            if (paramTypes != null) {
                paramTypes[i] = cd;
            } else {
                paramTypeList.add(cd);
            }
            last += len;
        }
        var lastCd = resolveClassDesc(descriptor, last, cur - last);

        if (paramTypes != null) {
            paramTypes[packedCount] = lastCd;
            return paramTypes;
        }
        paramTypeList.add(lastCd);
        return buildParamTypes(descriptor, cur, end, paramTypeList);
    }

    // slow path
    private static ClassDesc[] buildParamTypes(String descriptor, int cur, int end, List<ClassDesc> list) {
        while (cur < end) {
            int len = skipOverFieldSignature(descriptor, cur, end);
            if (len == 0)
                throw badMethodDescriptor(descriptor);
            list.add(resolveClassDesc(descriptor, cur, len));
            cur += len;
        }

        return list.toArray(EMPTY_CLASSDESC);
    }

    @Override
    public ClassDesc returnType() {
        return returnType;
    }

    @Override
    public int parameterCount() {
        return argTypes.length;
    }

    @Override
    public ClassDesc parameterType(int index) {
        return argTypes[index];
    }

    @Override
    public List<ClassDesc> parameterList() {
        return List.of(argTypes);
    }

    @Override
    public ClassDesc[] parameterArray() {
        return argTypes.clone();
    }

    @Override
    public MethodTypeDesc changeReturnType(ClassDesc returnType) {
        return ofValidated(requireNonNull(returnType), argTypes);
    }

    @Override
    public MethodTypeDesc changeParameterType(int index, ClassDesc paramType) {
        ClassDesc[] newArgs = argTypes.clone();
        newArgs[index] = validateArgument(paramType);
        return ofValidated(returnType, newArgs);
    }

    @Override
    public MethodTypeDesc dropParameterTypes(int start, int end) {
        Objects.checkIndex(start, argTypes.length);
        Objects.checkFromToIndex(start, end, argTypes.length);

        ClassDesc[] newArgs = new ClassDesc[argTypes.length - (end - start)];
        if (start > 0) {
            System.arraycopy(argTypes, 0, newArgs, 0, start);
        }
        if (end < argTypes.length) {
            System.arraycopy(argTypes, end, newArgs, start, argTypes.length - end);
        }
        return ofValidated(returnType, newArgs);
    }

    @Override
    public MethodTypeDesc insertParameterTypes(int pos, ClassDesc... paramTypes) {
        if (pos < 0 || pos > argTypes.length)
            throw new IndexOutOfBoundsException(pos);

        ClassDesc[] newArgs = new ClassDesc[argTypes.length + paramTypes.length];
        if (pos > 0) {
            System.arraycopy(argTypes, 0, newArgs, 0, pos);
        }
        System.arraycopy(paramTypes, 0, newArgs, pos, paramTypes.length);
        int destPos = pos + paramTypes.length;
        if (pos < argTypes.length) {
            System.arraycopy(argTypes, pos, newArgs, destPos, argTypes.length - pos);
        }
        // Validate after copying to avoid TOCTOU
        for (int i = pos; i < destPos; i++) {
            validateArgument(newArgs[i]);
        }

        return ofValidated(returnType, newArgs);
    }

    @Override
    public String descriptorString() {
        var desc = this.cachedDescriptorString;
        if (desc != null)
            return desc;

        return buildDescriptorString();
    }

    private String buildDescriptorString() {
        var returnType = this.returnType;
        var returnTypeDesc = returnType.descriptorString();
        var argTypes = this.argTypes;
        String desc;
        if (argTypes.length == 0) {
            // getter
            desc = "()".concat(returnTypeDesc);
        } else if (argTypes.length == 1 && returnType == ConstantDescs.CD_void) {
            // setter
            desc = ConstantUtils.concat("(", argTypes[0].descriptorString(), ")V");
        } else {
            int len = 2 + returnTypeDesc.length();
            for (ClassDesc argType : argTypes) {
                len += argType.descriptorString().length();
            }
            StringBuilder sb = new StringBuilder(len).append('(');
            for (ClassDesc argType : argTypes) {
                sb.append(argType.descriptorString());
            }
            desc = sb.append(')').append(returnTypeDesc).toString();
        }
        cachedDescriptorString = desc;
        return desc;
    }

    @Override
    public MethodType resolveConstantDesc(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
        MethodType mtype;
        try {
            @SuppressWarnings("removal")
            MethodType mt = AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override
                public MethodType run() {
                    return MethodType.fromMethodDescriptorString(descriptorString(),
                        lookup.lookupClass().getClassLoader());
                }
            });
            mtype = mt;
        } catch (TypeNotPresentException ex) {
            throw (ClassNotFoundException) ex.getCause();
        }

        // Some method types, like ones containing a package private class not accessible
        // to the overriding method, can be valid method descriptors and obtained from
        // MethodType.fromMethodDescriptor, but ldc instruction will fail to resolve such
        // MethodType constants due to access control (JVMS 5.4.3.1 and 5.4.3.5)
        lookup.accessClass(mtype.returnType());
        for (Class<?> paramType: mtype.parameterArray()) {
            lookup.accessClass(paramType);
        }
        return mtype;
    }

    /**
     * Returns {@code true} if this {@linkplain MethodTypeDescImpl} is
     * equal to another {@linkplain MethodTypeDescImpl}.  Equality is
     * determined by the two descriptors having equal return types and argument
     * types.
     *
     * @param o the {@code MethodTypeDescImpl} to compare to this
     *       {@code MethodTypeDescImpl}
     * @return {@code true} if the specified {@code MethodTypeDescImpl}
     *      is equal to this {@code MethodTypeDescImpl}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodTypeDescImpl constant = (MethodTypeDescImpl) o;

        return returnType.equals(constant.returnType)
               && Arrays.equals(argTypes, constant.argTypes);
    }

    @Override
    public int hashCode() {
        int result = returnType.hashCode();
        result = 31 * result + Arrays.hashCode(argTypes);
        return result;
    }

    @Override
    public String toString() {
        return String.format("MethodTypeDesc[%s]", displayDescriptor());
    }
}
