/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.instruction;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.Util;

/**
 * Models a method invocation instruction in the {@code code} array of a {@code
 * Code} attribute, other than {@link InvokeDynamicInstruction invokedynamic}.
 * Corresponding opcodes have a {@linkplain Opcode#kind() kind} of {@link Opcode.Kind#INVOKE}.
 * Delivered as a {@link CodeElement} when traversing the elements of a {@link CodeModel}.
 * <p>
 * Conceptually, a method invocation instruction is a record:
 * {@snippet lang=text :
 * // @link region substring="InvokeInstruction" target="#of(Opcode, MemberRefEntry)"
 * // @link substring="Opcode" target="#opcode()" :
 * InvokeInstruction(Opcode, MethodRefEntry | InterfaceMethodRefEntry method) // @link substring="MethodRefEntry | InterfaceMethodRefEntry method" target="#method()"
 * // @end
 * // @link region=1a substring="MethodRefEntry" target="ConstantPoolBuilder#methodRefEntry(ClassDesc, String, MethodTypeDesc)"
 * // @link region=2a substring="ClassDesc owner" target="#owner()"
 * // @link substring="String name" target="#name()" :
 * MethodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) // @link substring="MethodTypeDesc type" target="#typeSymbol()"
 * // @end region=1a
 * // @end region=2a
 * // @link region=1b substring="InterfaceMethodRefEntry" target="ConstantPoolBuilder#interfaceMethodRefEntry(ClassDesc, String, MethodTypeDesc)"
 * // @link region=2b substring="ClassDesc owner" target="#owner()"
 * // @link substring="String name" target="#name()" :
 * InterfaceMethodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) // @link substring="MethodTypeDesc type" target="#typeSymbol()"
 * // @end region=1b
 * // @end region=2b
 * }
 * where the {@code opcode} is of the method invocation kind, the {@code owner}
 * is a class or interface, the {@code name} is a simple name or {@value
 * ConstantDescs#INIT_NAME}.
 * <p>
 * The {@code method} must be an {@code InterfaceMethodRefEntry} for {@link
 * Opcode#INVOKEINTERFACE invokeinterface} opcode, and must be a {@code
 * MethodRefEntry} for {@link Opcode#INVOKEVIRTUAL invokevirtual} opcode.
 * {@link Opcode#INVOKESTATIC invokestatic} and {@link Opcode#INVOKESPECIAL
 * invokespecial} can have either type of entry for {@code method}.
 * <p>
 * Physically, a method invocation instruction has the same structure for
 * {@code invokestatic}, {@code invokevirtual}, and {@code invokespecial}.
 * {@code invokeinterface} has another structure:
 * {@snippet lang=text :
 * // @link substring="InterfaceMethodRefEntry method" target="#method()" :
 * InvokeInstruction(Opcode.INVOKEINTERFACE, InterfaceMethodRefEntry, int count) // @link substring="int count" target="#count"
 * }
 * The {@code count}, an unsigned byte, must be set according to the {@link
 * #typeSymbol() type}.  There is another trailing 1-byte padding of value 0.
 *
 * @see CodeBuilder#invoke CodeBuilder::invoke
 * @since 24
 */
public sealed interface InvokeInstruction extends Instruction
        permits AbstractInstruction.BoundInvokeInterfaceInstruction, AbstractInstruction.BoundInvokeInstruction, AbstractInstruction.UnboundInvokeInstruction {
    /**
     * {@return the {@link MethodRefEntry} or {@link InterfaceMethodRefEntry}
     * constant described by this instruction}
     */
    MemberRefEntry method();

    /**
     * {@return whether the class or interface holding the method is an interface}
     */
    boolean isInterface();

    /**
     * {@return the {@code count} value of an {@link Opcode#INVOKEINTERFACE invokeinterface} instruction,
     * or {@code 0} for {@code invokespecial}, {@code invokestatic} and {@code invokevirtual} instructions}
     * <p>
     * For an {@code invokeinterface} instruction, this value must be equivalent
     * to the sum of {@linkplain TypeKind#slotSize() slot sizes} of all arguments
     * plus one, which is equal to the number of operand stack depth consumed by
     * this interface method invocation instruction.
     *
     * @jvms 6.5.invokeinterface <em>invokeinterface</em>
     */
    int count();

    /**
     * {@return the class or interface holding the method}
     *
     * @apiNote
     * A symbolic descriptor for the owner is available through {@link
     * ClassEntry#asSymbol() owner().asSymbol()}.
     */
    default ClassEntry owner() {
        return method().owner();
    }

    /**
     * {@return the name of the method}
     *
     * @apiNote
     * A string value for the name is available through {@link
     * Utf8Entry#stringValue() name().stringValue()}.
     */
    default Utf8Entry name() {
        return method().nameAndType().name();
    }

    /**
     * {@return the method descriptor string of the method}
     *
     * @apiNote
     * A symbolic descriptor for the type of the method is available through
     * {@link #typeSymbol() typeSymbol()}.
     */
    default Utf8Entry type() {
        return method().nameAndType().type();
    }

    /**
     * {@return a symbolic descriptor for the method type}
     */
    default MethodTypeDesc typeSymbol() {
        return Util.methodTypeSymbol(method().type());
    }

    /**
     * {@return an invocation instruction}
     *
     * @param op the opcode for the specific type of invocation instruction,
     *           which must be of kind {@link Opcode.Kind#INVOKE}
     * @param method a constant pool entry describing the method
     * @throws IllegalArgumentException if the opcode kind is not
     *         {@link Opcode.Kind#INVOKE}.
     */
    static InvokeInstruction of(Opcode op, MemberRefEntry method) {
        Util.checkKind(op, Opcode.Kind.INVOKE);
        return new AbstractInstruction.UnboundInvokeInstruction(op, method);
    }

    /**
     * {@return an invocation instruction}
     *
     * @param op the opcode for the specific type of invocation instruction,
     *           which must be of kind {@link Opcode.Kind#INVOKE}
     * @param owner the class holding the method
     * @param name the name of the method
     * @param type the method descriptor
     * @param isInterface whether the class holding the method is an interface
     */
    static InvokeInstruction of(Opcode op,
                                ClassEntry owner,
                                Utf8Entry name,
                                Utf8Entry type,
                                boolean isInterface) {
        return of(op, owner, TemporaryConstantPool.INSTANCE.nameAndTypeEntry(name, type), isInterface);
    }

    /**
     * {@return an invocation instruction}
     *
     * @param op the opcode for the specific type of invocation instruction,
     *           which must be of kind {@link Opcode.Kind#INVOKE}
     * @param owner the class holding the method
     * @param nameAndType the name and type of the method
     * @param isInterface whether the class holding the method is an interface
     */
    static InvokeInstruction of(Opcode op,
                                ClassEntry owner,
                                NameAndTypeEntry nameAndType,
                                boolean isInterface) {
        return of(op, isInterface
                      ? TemporaryConstantPool.INSTANCE.interfaceMethodRefEntry(owner, nameAndType)
                      : TemporaryConstantPool.INSTANCE.methodRefEntry(owner, nameAndType));
    }
}
