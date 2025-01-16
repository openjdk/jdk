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
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Function;

import jdk.internal.classfile.impl.AbstractInstruction;
import jdk.internal.classfile.impl.Util;

/**
 * Models a dynamically-computed call site invocation instruction in the
 * {@code code} array of a {@code Code} attribute.  The corresponding opcode is
 * {@link Opcode#INVOKEDYNAMIC invokedynamic}.  Delivered as a {@link
 * CodeElement} when traversing the elements of a {@link CodeModel}.
 * <p>
 * A dynamically-computed call site invocation instruction is composite:
 * {@snippet lang=text :
 * // @link substring="InvokeDynamicInstruction" target="#of" :
 * InvokeDynamicInstruction(InvokeDynamicEntry invokedynamic) // @link substring="invokedynamic" target="#invokedynamic()"
 * }
 *
 * @see Opcode.Kind#INVOKE_DYNAMIC
 * @see CodeBuilder#invokedynamic CodeBuilder::invokedynamic
 * @jvms 6.5.invokedynamic <em>invokedynamic</em>
 * @since 24
 */
public sealed interface InvokeDynamicInstruction extends Instruction
        permits AbstractInstruction.BoundInvokeDynamicInstruction, AbstractInstruction.UnboundInvokeDynamicInstruction {
    /**
     * {@return an {@link InvokeDynamicEntry} describing the call site}
     */
    InvokeDynamicEntry invokedynamic();

    /**
     * {@return the invocation name of the call site}
     */
    default Utf8Entry name() {
        return invokedynamic().name();
    }

    /**
     * {@return the invocation type of the call site}
     *
     * @apiNote
     * A symbolic descriptor for the invocation typeis available through {@link
     * #typeSymbol() typeSymbol()}.
     */
    default Utf8Entry type() {
        return invokedynamic().type();
    }

    /**
     * {@return the invocation type of the call site, as a symbolic descriptor}
     */
    default MethodTypeDesc typeSymbol() {
        return invokedynamic().typeSymbol();
    }

    /**
     * {@return the bootstrap method of the call site}
     */
    default DirectMethodHandleDesc bootstrapMethod() {
        return invokedynamic().bootstrap()
                              .bootstrapMethod()
                              .asSymbol();
    }

    /**
     * {@return the bootstrap arguments of the call site}
     */
    default List<ConstantDesc> bootstrapArgs() {
        return Util.mappedList(invokedynamic().bootstrap().arguments(), new Function<>() {
            @Override
            public ConstantDesc apply(LoadableConstantEntry loadableConstantEntry) {
                return loadableConstantEntry.constantValue();
            }
        });
    }

    /**
     * {@return an invokedynamic instruction}
     *
     * @param invokedynamic the constant pool entry describing the call site
     */
    static InvokeDynamicInstruction of(InvokeDynamicEntry invokedynamic) {
        return new AbstractInstruction.UnboundInvokeDynamicInstruction(invokedynamic);
    }
}
