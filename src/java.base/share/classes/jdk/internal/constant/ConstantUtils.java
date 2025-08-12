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
package jdk.internal.constant;

import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodType;
import java.util.Set;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import static jdk.internal.constant.PrimitiveClassDescImpl.*;

/**
 * Helper methods for the implementation of {@code java.lang.constant}.
 */
@AOTSafeClassInitializer // initialization dependency of PrimitiveClassDescImpl
public final class ConstantUtils {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /** an empty constant descriptor */
    public static final ConstantDesc[] EMPTY_CONSTANTDESC = new ConstantDesc[0];
    public static final ClassDesc[] EMPTY_CLASSDESC = new ClassDesc[0];
    public static final int MAX_ARRAY_TYPE_DESC_DIMENSIONS = 255;
    public static final ClassDesc CD_module_info = binaryNameToDesc("module-info");
    public static @Stable ClassDesc CD_Object_array; // set from ConstantDescs, avoid circular initialization

    private static final Set<String> pointyNames = Set.of(ConstantDescs.INIT_NAME, ConstantDescs.CLASS_INIT_NAME);

    /** No instantiation */
    private ConstantUtils() {}

    // Note:
    // Non-JDK users should create their own utilities that wrap
    // {@code .describeConstable().orElseThrow()} calls;
    // these xxDesc methods has undefined and unsafe exceptional
    // behavior, so they are not suitable as public APIs.

    /**
     * Creates a {@linkplain ClassDesc} from a pre-validated binary name
     * for a class or interface type. Validated version of {@link
     * ClassDesc#of(String)}.
     *
     * @param binaryName a binary name
     */
    public static ClassDesc binaryNameToDesc(String binaryName) {
        return internalNameToDesc(binaryToInternal(binaryName));
    }

    /**
     * Creates a {@linkplain ClassDesc} from a pre-validated internal name
     * for a class or interface type. Validated version of {@link
     * ClassDesc#ofInternalName(String)}.
     *
     * @param internalName a binary name
     */
    public static ClassDesc internalNameToDesc(String internalName) {
        return ClassOrInterfaceDescImpl.ofValidated(concat("L", internalName, ";"));
    }

    /**
     * Creates a ClassDesc from a Class object, requires that this class
     * can always be described nominally, i.e. this class is not a
     * hidden class or interface or an array with a hidden component
     * type.
     */
    public static ClassDesc classDesc(Class<?> type) {
        if (type.isPrimitive()) {
            return Wrapper.forPrimitiveType(type).basicClassDescriptor();
        }
        return referenceClassDesc(type);
    }

    /**
     * Creates a ClassDesc from a Class object representing a non-hidden
     * class or interface or an array type with a non-hidden component type.
     */
    public static ClassDesc referenceClassDesc(Class<?> type) {
        return referenceClassDesc(type.descriptorString());
    }

    /**
     * Creates a {@linkplain ClassDesc} from a pre-validated descriptor string
     * for a class or interface type or an array type.
     *
     * @param descriptor a field descriptor string for a class or interface type
     * @jvms 4.3.2 Field Descriptors
     */
    public static ClassDesc referenceClassDesc(String descriptor) {
        if (descriptor.charAt(0) == '[') {
            return ArrayClassDescImpl.ofValidatedDescriptor(descriptor);
        }
        return ClassOrInterfaceDescImpl.ofValidated(descriptor);
    }

    /**
     * Creates a MethodTypeDesc from a MethodType object, requires that
     * the type can be described nominally, i.e. all of its return
     * type and parameter types can be described nominally.
     */
    public static MethodTypeDesc methodTypeDesc(MethodType type) {
        var returnDesc = classDesc(type.returnType());
        if (type.parameterCount() == 0) {
            return MethodTypeDescImpl.ofValidated(returnDesc, EMPTY_CLASSDESC);
        }
        var paramDescs = new ClassDesc[type.parameterCount()];
        for (int i = 0; i < type.parameterCount(); i++) {
            paramDescs[i] = classDesc(type.parameterType(i));
        }
        return MethodTypeDescImpl.ofValidated(returnDesc, paramDescs);
    }

    /**
     * Creates a MethodTypeDesc from return class and parameter
     * class objects, requires that all of them can be described nominally.
     * This version is mainly useful for working with Method objects.
     */
    public static MethodTypeDesc methodTypeDesc(Class<?> returnType, Class<?>[] parameterTypes) {
        var returnDesc = classDesc(returnType);
        if (parameterTypes.length == 0) {
            return MethodTypeDescImpl.ofValidated(returnDesc, EMPTY_CLASSDESC);
        }
        var paramDescs = new ClassDesc[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            paramDescs[i] = classDesc(parameterTypes[i]);
        }
        return MethodTypeDescImpl.ofValidated(returnDesc, paramDescs);
    }

    /**
     * Creates a {@linkplain ClassDesc} from a descriptor string for a class or
     * interface type or an array type.
     *
     * @param descriptor a field descriptor string for a class or interface type
     * @throws IllegalArgumentException if the descriptor string is not a valid
     * field descriptor string, or does not describe a class or interface type
     * @jvms 4.3.2 Field Descriptors
     */
    public static ClassDesc parseReferenceTypeDesc(String descriptor) {
        int dLen = descriptor.length();
        int len = ConstantUtils.skipOverFieldSignature(descriptor, 0, dLen);
        if (len <= 1 || len != dLen)
            throw new IllegalArgumentException(String.format("not a valid reference type descriptor: %s", descriptor));
        if (descriptor.charAt(0) == '[') {
            return ArrayClassDescImpl.ofValidatedDescriptor(descriptor);
        }
        return ClassOrInterfaceDescImpl.ofValidated(descriptor);
    }

    /**
     * Validates the correctness of a class or interface name or a package name.
     * In particular checks for the presence of invalid characters,
     * consecutive, leading, or trailing separator char, for both non-internal
     * and internal forms, and the empty string for class or interface names.
     *
     * @param name the name
     * @param slashSeparator {@code true} means {@code /} is the separator char
     *     (internal form); otherwise {@code .} is the separator char
     * @param allowEmpty {@code true} means the empty string is a valid name
     * @return the name passed if valid
     * @throws IllegalArgumentException if the name is invalid
     * @throws NullPointerException if name is {@code null}
     */
    private static String validateClassOrPackageName(String name, boolean slashSeparator, boolean allowEmpty) {
        int len = name.length();  // implicit null check
        // empty name special rule
        if (allowEmpty && len == 0)
            return name;
        // state variable for detection of illegal states of
        // empty name, consecutive, leading, or trailing separators
        int afterSeparator = 0;
        for (int i = 0; i < len; i++) {
            char ch = name.charAt(i);
            // reject ';' or '['
            if (ch == ';' || ch == '[')
                throw invalidClassName(name);
            // encounter a separator
            boolean foundSlash = ch == '/';
            if (foundSlash || ch == '.') {
                // reject the other separator char
                // reject consecutive or leading separators
                if (foundSlash != slashSeparator || i == afterSeparator)
                    throw invalidClassName(name);
                afterSeparator = i + 1;
            }
        }
        // reject empty name or trailing separators
        if (len == afterSeparator)
            throw invalidClassName(name);
        return name;
    }

    /**
     * Validates the correctness of a binary class name.
     * In particular checks for the presence of invalid characters, empty
     * name, consecutive, leading, or trailing {@code .}.
     *
     * @param name the class name
     * @return the class name passed if valid
     * @throws IllegalArgumentException if the class name is invalid
     * @throws NullPointerException if class name is {@code null}
     */
    public static String validateBinaryClassName(String name) {
        return validateClassOrPackageName(name, false, false);
    }

    /**
     * Validates the correctness of an internal class name.
     * In particular checks for the presence of invalid characters, empty
     * name, consecutive, leading, or trailing {@code /}.
     *
     * @param name the class name
     * @return the class name passed if valid
     * @throws IllegalArgumentException if the class name is invalid
     * @throws NullPointerException if class name is {@code null}
     */
    public static String validateInternalClassName(String name) {
        return validateClassOrPackageName(name, true, false);
    }

    /**
     * Validates the correctness of a binary package name.
     * In particular checks for the presence of invalid characters, consecutive,
     * leading, or trailing {@code .}.  Allows empty strings for the unnamed package.
     *
     * @param name the package name
     * @return the package name passed if valid
     * @throws IllegalArgumentException if the package name is invalid
     * @throws NullPointerException if the package name is {@code null}
     */
    public static String validateBinaryPackageName(String name) {
        return validateClassOrPackageName(name, false, true);
    }

    /**
     * Validates the correctness of an internal package name.
     * In particular checks for the presence of invalid characters, consecutive,
     * leading, or trailing {@code /}.  Allows empty strings for the unnamed package.
     *
     * @param name the package name
     * @return the package name passed if valid
     * @throws IllegalArgumentException if the package name is invalid
     * @throws NullPointerException if the package name is {@code null}
     */
    public static String validateInternalPackageName(String name) {
        return validateClassOrPackageName(name, true, true);
    }

    /**
     * Validates the correctness of a module name.
     * In particular checks for the presence of invalid characters in the name.
     * Empty module name is allowed.
     *
     * {@jvms 4.2.3} Module and Package Names
     *
     * @param name the module name
     * @return the module name passed if valid
     * @throws IllegalArgumentException if the module name is invalid
     * @throws NullPointerException if the module name is {@code null}
     */
    public static String validateModuleName(String name) {
        for (int i = name.length() - 1; i >= 0; i--) {
            char ch = name.charAt(i);
            if ((ch >= '\u0000' && ch <= '\u001F')
            || ((ch == '\\' || ch == ':' || ch =='@') && (i == 0 || name.charAt(--i) != '\\')))
                throw new IllegalArgumentException("Invalid module name: " + name);
        }
        return name;
    }

    /**
     * Validates a member name
     *
     * @param name the name of the member
     * @return the name passed if valid
     * @throws IllegalArgumentException if the member name is invalid
     * @throws NullPointerException if the member name is {@code null}
     */
    public static String validateMemberName(String name, boolean method) {
        int len = name.length();
        if (len == 0)
            throw new IllegalArgumentException("zero-length member name");
        for (int i = 0; i < len; i++) {
            char ch = name.charAt(i);
            // common case fast-path
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))
                continue;
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/')
                throw new IllegalArgumentException("Invalid member name: " + name);
            if (method && (ch == '<' || ch == '>')) {
                if (!pointyNames.contains(name))
                    throw new IllegalArgumentException("Invalid member name: " + name);
            }
        }
        return name;
    }

    public static void validateClassOrInterface(ClassDesc classDesc) {
        if (!classDesc.isClassOrInterface())
            throw new IllegalArgumentException("not a class or interface type: " + classDesc);
    }

    public static void validateArrayRank(int rank) {
        // array rank must be representable with u1 and nonzero
        if (rank == 0 || (rank & ~0xFF) != 0) {
            throw new IllegalArgumentException(invalidArrayRankMessage(rank));
        }
    }

    /**
     * Retrieves the array depth on a trusted descriptor.
     * Uses a simple loop with the assumption that most descriptors have
     * 0 or very low array depths.
     */
    public static int arrayDepth(String descriptorString, int off) {
        int depth = 0;
        while (descriptorString.charAt(off) == '[') {
            depth++;
            off++;
        }
        return depth;
    }

    public static String binaryToInternal(String name) {
        return name.replace('.', '/');
    }

    public static String internalToBinary(String name) {
        return name.replace('/', '.');
    }

    public static String dropFirstAndLastChar(String s) {
        return s.substring(1, s.length() - 1);
    }

    public static PrimitiveClassDescImpl forPrimitiveType(String descriptor, int offset) {
        return switch (descriptor.charAt(offset)) {
            case JVM_SIGNATURE_BYTE    -> CD_byte;
            case JVM_SIGNATURE_CHAR    -> CD_char;
            case JVM_SIGNATURE_FLOAT   -> CD_float;
            case JVM_SIGNATURE_DOUBLE  -> CD_double;
            case JVM_SIGNATURE_INT     -> CD_int;
            case JVM_SIGNATURE_LONG    -> CD_long;
            case JVM_SIGNATURE_SHORT   -> CD_short;
            case JVM_SIGNATURE_VOID    -> CD_void;
            case JVM_SIGNATURE_BOOLEAN -> CD_boolean;
            default -> throw badMethodDescriptor(descriptor);
        };
    }

    static ClassDesc resolveClassDesc(String descriptor, int start, int len) {
        if (len == 1) {
            return forPrimitiveType(descriptor, start);
        }

        // Pre-verified in MethodTypeDescImpl#ofDescriptor; avoid redundant verification
        int arrayDepth = arrayDepth(descriptor, start);
        if (arrayDepth == 0) {
            return ClassOrInterfaceDescImpl.ofValidated(descriptor.substring(start, start + len));
        } else if (arrayDepth + 1 == len) {
            return ArrayClassDescImpl.ofValidated(forPrimitiveType(descriptor, start + arrayDepth), arrayDepth);
        } else {
            return ArrayClassDescImpl.ofValidated(ClassOrInterfaceDescImpl.ofValidated(descriptor.substring(start + arrayDepth, start + len)), arrayDepth);
        }
    }

    static String invalidArrayRankMessage(int rank) {
        return "Array rank must be within [1, 255]: " + rank;
    }

    static IllegalArgumentException invalidClassName(String className) {
        return new IllegalArgumentException("Invalid class name: ".concat(className));
    }

    static IllegalArgumentException badMethodDescriptor(String descriptor) {
        return new IllegalArgumentException("Bad method descriptor: " + descriptor);
    }

    private static final char JVM_SIGNATURE_ARRAY = '[';
    private static final char JVM_SIGNATURE_BYTE = 'B';
    private static final char JVM_SIGNATURE_CHAR = 'C';
    private static final char JVM_SIGNATURE_CLASS = 'L';
    private static final char JVM_SIGNATURE_FLOAT = 'F';
    private static final char JVM_SIGNATURE_DOUBLE = 'D';
    private static final char JVM_SIGNATURE_INT = 'I';
    private static final char JVM_SIGNATURE_LONG = 'J';
    private static final char JVM_SIGNATURE_SHORT = 'S';
    private static final char JVM_SIGNATURE_VOID = 'V';
    private static final char JVM_SIGNATURE_BOOLEAN = 'Z';

    /**
     * Validates that the characters at [start, end) within the provided string
     * describe a valid field type descriptor.
     * @param descriptor the descriptor string
     * @param start the starting index into the string
     * @param end the ending index within the string
     * @return the length of the descriptor, or 0 if it is not a descriptor
     * @throws IllegalArgumentException if the descriptor string is not valid
     */
    static int skipOverFieldSignature(String descriptor, int start, int end) {
        int arrayDim = 0;
        int index = start;
        if (index < end) {
            char ch;
            while ((ch = descriptor.charAt(index++)) == JVM_SIGNATURE_ARRAY) {
                arrayDim++;
            }
            if (arrayDim > MAX_ARRAY_TYPE_DESC_DIMENSIONS) {
                throw maxArrayTypeDescDimensions();
            }

            switch (ch) {
                case JVM_SIGNATURE_BOOLEAN:
                case JVM_SIGNATURE_BYTE:
                case JVM_SIGNATURE_CHAR:
                case JVM_SIGNATURE_SHORT:
                case JVM_SIGNATURE_INT:
                case JVM_SIGNATURE_FLOAT:
                case JVM_SIGNATURE_LONG:
                case JVM_SIGNATURE_DOUBLE:
                    return index - start;
                case JVM_SIGNATURE_CLASS:
                    // state variable for detection of illegal states of
                    // empty name, '//', leading '/', or trailing '/'
                    int afterSeparator = index + 1; // start of internal name
                    while (index < end) {
                        ch = descriptor.charAt(index++);
                        if (ch == ';')
                            // reject empty name or trailing '/'
                            return index == afterSeparator ? 0 : index - start;
                        // reject '.' or '['
                        if (ch == '.' || ch == '[')
                            return 0;
                        if (ch == '/') {
                            // reject '//' or leading '/'
                            if (index == afterSeparator)
                                return 0;
                            afterSeparator = index + 1;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return 0;
    }

    private static IllegalArgumentException maxArrayTypeDescDimensions() {
        return new IllegalArgumentException(String.format(
                        "Cannot create an array type descriptor with more than %d dimensions",
                        ConstantUtils.MAX_ARRAY_TYPE_DESC_DIMENSIONS));
    }

    public static String concat(String prefix, Object value, String suffix) {
        return JLA.concat(prefix, value, suffix);
    }
}
