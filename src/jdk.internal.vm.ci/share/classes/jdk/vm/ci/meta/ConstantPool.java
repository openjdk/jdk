/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 * The following convention is used when accessing the ConstantPool with an index:
 * <ul>
 * <li>rawIndex - index in the bytecode stream after the opcode (could be rewritten for some opcodes)</li>
 * <li>cpi - the constant pool index (as specified in JVM Spec)</li>
 * </ul>
 *
 * Some of the methods are currently not using the convention correctly. That will be addressed in JDK-8314172.
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
     * @param rawIndex index in the bytecode stream after the {@code opcode} (could be rewritten for some opcodes)
     * @param opcode the opcode of the instruction that references the type
     */
    void loadReferencedType(int rawIndex, int opcode);

    /**
     * Ensures that the type referenced by the specified constant pool entry is loaded. This can be
     * used to compile time resolve a type. It works for field, method, or type constant pool
     * entries.
     *
     * @param rawIndex index in the bytecode stream after the {@code opcode} (could be rewritten for some opcodes)
     * @param opcode the opcode of the instruction that references the type
     * @param initialize if {@code true}, the referenced type is either guaranteed to be initialized
     *            upon return or an initialization exception is thrown
     */
    default void loadReferencedType(int rawIndex, int opcode, boolean initialize) {
        if (initialize) {
            loadReferencedType(rawIndex, opcode);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Looks up the type referenced by the {@code rawIndex}.
     *
     * @param rawIndex index in the bytecode stream after the {@code opcode} (could be rewritten for some opcodes)
     * @param opcode the opcode of the instruction with {@code rawIndex} as an operand
     * @return a reference to the compiler interface type
     */
    JavaType lookupReferencedType(int rawIndex, int opcode);

    /**
     * Looks up a reference to a field. Resolution checks
     * specific to the bytecode it denotes are performed if the field is already resolved. Checks
     * for some bytecodes require the method that contains the bytecode to be specified. Should any
     * of these checks fail, an unresolved field reference is returned.
     *
     * @param rawIndex rewritten index in the bytecode stream after the {@code opcode}
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @param method the method for which the lookup is being performed
     * @return a reference to the field at {@code rawIndex} in this pool
     */
    JavaField lookupField(int rawIndex, ResolvedJavaMethod method, int opcode);

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
    default JavaMethod lookupMethod(int cpi, int opcode) {
        return lookupMethod(cpi, opcode, null);
    }

    /**
     * Looks up a reference to a method. If {@code opcode} is non-negative, then resolution checks
     * specific to the bytecode it denotes are performed if the method is already resolved. Should
     * any of these checks fail, an unresolved method reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or
     *            {@code -1}
     * @param caller if non-null, do access checks in the context of {@code caller} calling the
     *            looked up method
     * @return a reference to the method at {@code cpi} in this pool
     * @throws ClassFormatError if the entry at {@code cpi} is not a method
     * @throws IllegalAccessError if {@code caller} is non-null and it cannot link against the
     *             looked up method
     */
    JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller);

    /**
     * The details for invoking a bootstrap method associated with a {@code CONSTANT_Dynamic_info}
     * or {@code CONSTANT_InvokeDynamic_info} pool entry.
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
         * The {@linkplain JavaConstant#getJavaKind kind} of each argument will be
         * {@link JavaKind#Object} or {@link JavaKind#Int}. The latter represents an
         * unresolved {@code CONSTANT_Dynamic_info} entry. To resolve this entry, the
         * corresponding bootstrap method has to be called first:
         *
         * <pre>
         * List<JavaConstant> args = bmi.getStaticArguments();
         * List<JavaConstant> resolvedArgs = new ArrayList<>(args.size());
         * for (JavaConstant c : args) {
         *     JavaConstant r = c;
         *     if (c.getJavaKind() == JavaKind.Int) {
         *         // If needed, access corresponding BootstrapMethodInvocation using
         *         // cp.lookupBootstrapMethodInvocation(pc.asInt(), -1)
         *         r = cp.lookupConstant(c.asInt(), true);
         *     } else {
         *         assert c.getJavaKind() == JavaKind.Object;
         *     }
         *     resolvedArgs.append(r);
         * }
         * </pre>
         *
         * The other types of entries are already resolved and can be used directly.
         *
         * @jvms 5.4.3.6
         */
        List<JavaConstant> getStaticArguments();

        /**
         * Resolves the element corresponding to this bootstrap. If
         * {@code isInvokeDynamic()}, then the corresponding invoke dynamic is resolved.
         * If {@code !isInvokeDynamic()}, then the dynamic constant pool entry will be
         * resolved.
         *
         * @jvms 5.4.3.6
         */
        void resolve();

        /**
         * If {@code isInvokeDynamic()}, then this method looks up the corresponding
         * invoke dynamic's appendix. If {@code !isInvokeDynamic()}, then this will
         * return the constant pool entry's value.
         */
        JavaConstant lookup();
    }

    /**
     * Gets the details for invoking a bootstrap method associated with the
     * {@code CONSTANT_Dynamic_info} or {@code CONSTANT_InvokeDynamic_info} pool entry
     * in the constant pool.
     *
     * @param index if {@code opcode} is -1,  {@code index} is a constant pool index. Otherwise {@code opcode}
     *              must be {@code Bytecodes.INVOKEDYNAMIC}, and {@code index} must be the operand of that
     *              opcode in the bytecode stream (i.e., a {@code rawIndex}).
     * @param opcode must be {@code Bytecodes.INVOKEDYNAMIC}, or -1 if
     *            {@code index} was not decoded from a bytecode stream
     * @return the bootstrap method invocation details or {@code null} if the entry specified by {@code index}
     *         is not a {@code CONSTANT_Dynamic_info} or {@code CONSTANT_InvokeDynamic_info}
     * @jvms 4.7.23 The {@code BootstrapMethods} Attribute
     */
    default BootstrapMethodInvocation lookupBootstrapMethodInvocation(int index, int opcode) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns either the BootstrapMethodInvocation instances for all invokedynamic
     * bytecodes which reference this constant pool, or all
     * {@code CONSTANT_Dynamic_info} BootstrapMethodInvocations within this constant
     * pool. The returned List is unmodifiable; calls to any mutator method will
     * always cause {@code UnsupportedOperationException} to be thrown.
     *
     * @param invokeDynamic when true, return all invokedynamic
     *                      BootstrapMethodInvocations; otherwise, return all
     *                      {@code CONSTANT_Dynamic_info}
     *                      BootstrapMethodInvocations.
     */
    List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic);

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
     * Looks up a constant at the specified index.
     *
     * If {@code resolve == false} and the denoted constant is of type
     * {@code JVM_CONSTANT_Dynamic}, {@code JVM_CONSTANT_MethodHandle} or
     * {@code JVM_CONSTANT_MethodType} and it's not yet resolved then
     * {@code null} is returned.
     *
     * @param cpi the constant pool index
     * @return the {@code Constant} or {@code JavaType} instance representing the constant pool
     *         entry
     */
    Object lookupConstant(int cpi, boolean resolve);

    /**
     * Looks up the appendix at the specified index.
     *
     * @param rawIndex index in the bytecode stream after the {@code opcode} (could be rewritten for some opcodes)
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @return the appendix if it exists and is resolved or {@code null}
     */
    JavaConstant lookupAppendix(int rawIndex, int opcode);
}
