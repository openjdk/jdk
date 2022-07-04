/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Represents the runtime representation of the constant pool that is used by the compiler when
 * parsing bytecode. Provides methods to look up a constant pool entry without performing
 * resolution. They are used during compilation.
 */
public interface ConstantPool {

    /**
     * Returns the number of entries the constant pool.
     *
     * @return number of entries in the constant pool
     */
    int length();

    /**
     * Ensures that the type referenced by the specified constant pool entry is loaded and
     * initialized. This can be used to compile time resolve a type. It works for field, method, or
     * type constant pool entries.
     *
     * @param cpi the index of the constant pool entry that references the type
     * @param opcode the opcode of the instruction that references the type
     */
    void loadReferencedType(int cpi, int opcode);

    /**
     * Looks up the type referenced by the constant pool entry at {@code cpi} as referenced by the
     * {@code opcode} bytecode instruction.
     *
     * @param cpi the index of a constant pool entry that references a type
     * @param opcode the opcode of the instruction with {@code cpi} as an operand
     * @return a reference to the compiler interface type
     */
    JavaType lookupReferencedType(int cpi, int opcode);

    /**
     * Looks up a reference to a field. If {@code opcode} is non-negative, then resolution checks
     * specific to the bytecode it denotes are performed if the field is already resolved. Checks
     * for some bytecodes require the method that contains the bytecode to be specified. Should any
     * of these checks fail, an unresolved field reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}
     * @param method the method for which the lookup is being performed
     * @return a reference to the field at {@code cpi} in this pool
     * @throws ClassFormatError if the entry at {@code cpi} is not a field
     */
    JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode);

    /**
     * Looks up a reference to a method. If {@code opcode} is non-negative, then resolution checks
     * specific to the bytecode it denotes are performed if the method is already resolved. Should
     * any of these checks fail, an unresolved method reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}
     * @return a reference to the method at {@code cpi} in this pool
     * @throws ClassFormatError if the entry at {@code cpi} is not a method
     */
    JavaMethod lookupMethod(int cpi, int opcode);

    /**
     * The details for invoking a bootstrap method associated with a {@code CONSTANT_Dynamic_info}
     * or {@code CONSTANT_InvokeDynamic_info} pool entry .
     *
     * @jvms 4.4.10 The {@code CONSTANT_Dynamic_info} and {@code CONSTANT_InvokeDynamic_info}
     *       Structures
     * @jvms 4.7.23 The {@code BootstrapMethods} Attribute
     */
    interface BootstrapMethodInvocation {
        /**
         * Gets the bootstrap method that will be invoked.
         */
        ResolvedJavaMethod getMethod();

        /**
         * Returns {@code true} if this bootstrap method invocation is for a
         * {@code CONSTANT_InvokeDynamic_info} pool entry, {@code false} if it is for a
         * {@code CONSTANT_Dynamic_info} entry.
         */
        boolean isInvokeDynamic();

        /**
         * Gets the name of the pool entry.
         */
        String getName();

        /**
         * Returns a reference to the {@link MethodType} ({@code this.isInvokeDynamic() == true}) or
         * {@link Class} ({@code this.isInvokeDynamic() == false}) resolved for the descriptor of
         * the pool entry.
         */
        JavaConstant getType();

        /**
         * Gets the static arguments with which the bootstrap method will be invoked.
         *
         * @jvms 5.4.3.6
         */
        List<JavaConstant> getStaticArguments();
    }

    /**
     * Gets the details for invoking a bootstrap method associated with the
     * {@code CONSTANT_Dynamic_info} or {@code CONSTANT_InvokeDynamic_info} pool entry {@code cpi}
     * in the constant pool.
     *
     * @param cpi a constant pool index
     * @param opcode the opcode of the instruction that has {@code cpi} as an operand or -1 if
     *            {@code cpi} was not decoded from an instruction stream
     * @return the bootstrap method invocation details or {@code null} if the entry at {@code cpi}
     *         is not a {@code CONSTANT_Dynamic_info} or @{code CONSTANT_InvokeDynamic_info}
     * @throws IllegalArgumentException if the bootstrap method invocation makes use of
     *             {@code java.lang.invoke.BootstrapCallInfo}
     * @jvms 4.7.23 The {@code BootstrapMethods} Attribute
     */
    default BootstrapMethodInvocation lookupBootstrapMethodInvocation(int cpi, int opcode) {
        throw new UnsupportedOperationException();
    }

    /**
     * Looks up a reference to a type. If {@code opcode} is non-negative, then resolution checks
     * specific to the bytecode it denotes are performed if the type is already resolved. Should any
     * of these checks fail, an unresolved type reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}
     * @return a reference to the compiler interface type
     */
    JavaType lookupType(int cpi, int opcode);

    /**
     * Looks up an Utf8 string.
     *
     * @param cpi the constant pool index
     * @return the Utf8 string at index {@code cpi} in this constant pool
     */
    String lookupUtf8(int cpi);

    /**
     * Looks up a method signature.
     *
     * @param cpi the constant pool index
     * @return the method signature at index {@code cpi} in this constant pool
     */
    Signature lookupSignature(int cpi);

    /**
     * Looks up a constant at the specified index.
     *
     * @param cpi the constant pool index
     * @return the {@code Constant} or {@code JavaType} instance representing the constant pool
     *         entry
     */
    Object lookupConstant(int cpi);

    /**
     * Looks up the appendix at the specified index.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}
     * @return the appendix if it exists and is resolved or {@code null}
     */
    JavaConstant lookupAppendix(int cpi, int opcode);
}
